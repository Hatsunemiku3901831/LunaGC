# E-01c：生产Lua序列化与scene3索引输入离线审计

2026-11-06。延续[固定既有脚本迁移](CLIENT_72_SCENE_SCRIPT_MIGRATION.md)，本批只新增离线工具，不修改运行源码、Proto、配置、资源或缓存。**本限定审计已执行；E-01c、E-01、D-02及实机验收未完成。** 继承Lua仍为7.1实现参考，不能称为7.2原始导出。

## 入口、固定输入与安全边界

新增`tools/audit_scene_serialization72.py`、`tools/Client72SceneSerializeAudit.java`及对应测试。Python复用既有资源审计的完整tree/blob/大小/SHA-256守卫，固定候选提交d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1、resources提交dac2a9d43332ef51879522cf39da2831b0ca0543，拒绝Scene/3和Common已跟踪脚本漂移。3张目标实体表重新核对稳定ID与Git blob；没有混入当前基线行。

现有JAR只作离线类依赖，重新核验SHA-256为`3a05ca88115f93a46bbb0ca334f54241f560a291fbb5b05fa2d42bb0f87aa522`；工具拒绝JAR变化。序列化器、数据类、索引算法、位置类、GadgetData、Grasscutter初始化和既有求值工具均有固定规范换行源码hash守卫，变化后需重新阅读/复核，不能自动刷新hash后冒称已验。

Java通过一个明确的反射适配器复用既有工具的私有inline LuaJ环境与求值方法，不改变父工具公共行为。实际调用生产`LuaSerializer.toObject/toList`、`SceneSuite.init`、`Position`、`GridPosition`和`SceneIndexManager.buildIndex`；直接序列化SceneConfig、SceneBlock、SceneGroup声明，以及SceneMonster/NPC/Gadget/Region/Trigger/Suite/Var/InitConfig与嵌套对象。按SceneGroup.load装配实体映射及group反向引用，保留生产“重复key取首项”的结果并另行报告重复。

未调用`SceneMeta.load`、`SceneBlock.load`、`SceneGroup.load`、`ScriptLoader.init`或`SceneScriptManager`：这些入口涉及配置初始化、事件/业务环境，manager构造还会创建初始化线程，超出本离线范围。生产LuaSerializer正常路径无需Grasscutter；其外围错误分支会触达Grasscutter日志，故逐次保留JVM class-init日志并拒绝Grasscutter、ScriptLoader、SceneScriptManager或database类初始化。两次正式运行均未初始化这些基础设施。没有启动服务器、数据库、客户端，没有调用trigger/action/ScriptLib业务，也没有读取、删除或写入scene3_grid缓存。

## 实际结果与未伪造的失败

64个块、17,806个声明组均经过真实序列化，实际构造1个二维块矩形索引、64个三维组位置索引。按生产getGroupGrids过滤，只执行13,616个非dynamic组，4,190个dynamic组仅声明/位置被检查，未执行其组体。

| 层次 | 结果 |
| --- | --- |
| inline顶层求值 | 13,616 / 13,616成功 |
| 生产序列化、套件初始化全部走完 | 13,611 / 13,616成功；5组抛ClassCastException |
| 已解析套件 / 完成初始化 | 20,871 / 20,862；失败组后续套件没有冒称已完成 |
| 套件实际绑定 | monster 17,475；gadget 64,927；region 4,659；trigger 29,661 |
| 生产消费集合行数 | monster 19,233；gadget 62,239；NPC 478；region 4,330；garbage gadget 11,410 |
| 显式问题 | 1,283条，涉及470组；另记录未知字段及两个声明字段未绑定计数 |

这里“序列化走完”不是内容完全兼容；静默截断仍属于问题。当前生产SceneGroup.load只捕获ScriptException/LuaError，以下ClassCastException不会被该catch处理；真实getGroupGrids调用也不能因审计器继续其他组而被宣称成功。

### 数值字符串被错误装进List<String>

5组为**133212052、133212135、133212142、133212146、133212233**，均在`suites[1].init`失败。共同依赖`Common/BlackBoxPlay/EnergyAmplifier.lua`：顶层LF_Initialize_Group把`tostring(defs.repeater[i])`作为timer trigger名字插入套件。

这些Lua值本来是字符串。LuaJ对数值字符串的`isint()`返回true，而生产LuaSerializer.serializeList在`isstring()`前检查`isint()`，忽略List<String>声明，把30个字符串变成Integer。SceneTrigger.name按String字段保留字符串；SceneSuite.triggers实际含Integer，随后SceneSuite.init的triggers流筛选触发运行时String强转异常。审计分别记录30条list-element-type、30条numeric-coercion与5条group-failure，不将它们算成65个独立根因。

可复现的合成最小Lua：

```lua
triggers = {{name = "52001"}}
suites = {{triggers = {tostring(52001)}}}
```

完整异常栈保留在本机报告；共同业务帧为：

```text
java.lang.ClassCastException: class java.lang.Integer cannot be cast to class java.lang.String
java.util.stream.ReferencePipeline$2$1.accept(ReferencePipeline.java:178)
...
emu.grasscutter.scripts.data.SceneSuite.init(SceneSuite.java:48)
Client72SceneSerializeAudit.load(...)
```

未修生产类型转换，也未把触发器名字改成非数值来让审计变绿。

### boolean字段存在但setter未被匹配

生产LuaSerializer按字段名拼`set`+首字母大写。Lombok实际生成的boolean setter对`is`前缀有不同命名。实际返回对象观察到：

| 字段 | 出现数 | serializer寻找 / 实际setter | 首个实际观察 |
| --- | --- | --- | --- |
| SceneMonster.isElite | 144 | setIsElite / setElite | group133001303，Lua true→对象false |
| SceneGadget.is_use_point_array | 868 | setIs_use_point_array / set_use_point_array | group133004460，Lua true→对象false |

工具将这两类单列为unboundDeclaredSourceFields，并保留首个source/actual/expectedSetter；不是仅凭“未知字段”推测。两字段的合成最小输入为：

```lua
monsters = {{config_id=1, monster_id=7, pos={x=1,y=0,z=1}, isElite=true}}
gadgets = {{config_id=2, gadget_id=7, pos={x=1,y=0,z=1}, is_use_point_array=true}}
```

### region半径被截断

1,210个非整数radius实际写入`SceneRegion.radius`的int字段时截断；例如group133001090的19.1→19。该数据类的SPHERE/CYLINDER范围判断都直接消费此int radius并计算radius*radius，因此不是只影响报告显示。工具保留每条变更，不修类型、不更改Lua；本批没有执行region.contains或判断目标7.2业务应采用的精度。

### 目标引用、未知字段与vision

8条缺Gadget ID仍全部位于garbages.gadgets，ID70200012至70200019，组及config_id与前批一致，不删除、不伪造。生产SceneGroup.load只装配garbages.gadgets；其余garbage monster/NPC/region/trigger保留为未消费字段计数，不混入已生成实体计数。普通未知字段也逐类型/名字计数，没有补别名或推断含义。

直接以真实Gson→GadgetData读取固定目标表：6,770行有visionLevel，24,912行没有。已经走到网格模型的62,193个普通gadget中，52,533个采用生产getGadgetVisionLevel的“字段缺失返回0”路径，然后与Lua vision_level取max。没有把任何混淆列猜成visionLevel；该回退是已观察的既有行为，不是已经恢复目标语义的证据。garbage gadget按生产源码直接使用其Lua vision_level，不套普通gadget表提升规则。

## 索引输入模型的覆盖与限制

网格公式逐行复现固定getGroupGrids/addGridPositionToMap/getGadgetVisionLevel源码：monsters、普通gadgets、NPC、region、garbage gadget与组位置分别进入6层；region固定0层，组位置只依据monster/普通gadget出现层的visionRange选最大层。坐标除gridWidth后使用Java向零截断，负数没有改成floor。visionOptions只读当前config.json中6层name/range/width，未改配置。

成功组贡献网格输入：monster19,227、gadget62,193、NPC478、region4,328、garbage gadget11,410、组位置13,611。6层实际构造的二维RTree共34,947格、60,846个去重组成员；每层有独立规范化格子/组集合SHA-256。**这只是13,611组的部分模型，5个失败组未贡献网格，不能作为完整scene3缓存安装。** 生产getGroupGrids本身、GameData加载器、事件/插件、邻块可见性查询、实体生成、网络和实机均未执行。当前4类region shape计数来自序列化后模型输入，没有额外恢复7.2枚举。

## 测试与复现

新增7项真实Java/LuaJ测试覆盖：inline局部变量、稀疏键/嵌套对象/套件绑定、dynamic过滤、负坐标网格、类型截断/boolean转换、未知字段、层越界/缺坐标/未知shape、普通与garbage不同vision路径、缺ID、真实顶层失败、源码漂移，以及数值字符串套件故障和两个setter缺口。连同既有脚本6项/资源14项，**27项聚焦回归通过**。不依赖尚未接入共享矩阵的摘要。

```powershell
$env:GAME_PATH = & ./tools/game_path.ps1
$sceneJdk = (Get-ChildItem local/runtime -Directory -Filter 'jdk-17*' | Select-Object -First 1).FullName
python tools/audit_scene_serialization72.py --source local/migration72/sources/AnimeGameData/d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1 --baseline resources --client $env:GAME_PATH --java-home $sceneJdk --runtime-jar LunaGC-7.1.0.jar --output local/migration72/evidence/scene-serialization-audit.json --summary-output local/migration72/evidence/scene-serialization-summary-draft.json
python -m unittest tools.test_audit_scene_serialization72 tools.test_audit_scene_scripts72 tools.test_audit_scene_resource72 tools.test_audit_resource72 -v
```

正式报告和脱敏摘要各运行两次逐字节比较，保留`scene-serialization-{audit,repeat,summary-draft,summary-repeat,determinism}.json`与同前缀日志；每轮JVM stdout/stderr及class-init日志也保留。为保证真实异常栈完整可重复，明确关闭JVM OmitStackTraceInFastThrow优化，没有过滤或伪造异常内容。探索报告`scene-serialization-probe-before-stack-detail.json`和`scene-serialization-probe-full-stack.json`保留最早生产失败；聚焦测试首轮因工具只保留8帧而没含SceneSuite帧失败，原日志`scene-serialization-focused-tests-first-failure.log`保留，修正为完整栈后27项通过。

所有原始证据和摘要草稿仅在忽略的local/migration72；本批不新增protocol/cn-7.2观察文件、不更新共享矩阵，不提交原Lua/目标表/本机配置。无运行代码修改，不构建部署或重启。下一步由主任务按独立验收项修复真实序列化类型与setter绑定，再按运行前置决定构建/便携部署范围；不能将该离线审计当作资源或实机完成。
