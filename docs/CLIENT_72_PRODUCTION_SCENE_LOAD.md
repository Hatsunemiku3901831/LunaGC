# E-01c5：真实生产Lua加载、require吞错与绑定差异

2026-11-06。本项执行真实`ScriptLoader.init/getScript/createBindings/eval`及`SceneGroup.load`，没有以既有求值模型替代生产加载器。**这仍是限定离线审计，不是原环境兼容、资源ready或实机入场验收；E-01c/E-01/D-02均未完成。** 按用户本轮要求，完成本批限定审计后交主任务整合交接并暂停；没有继续修复生产加载器或改运行配置。

## 固定输入与隔离

新增`tools/Client72ProductionSceneLoadAudit.java`、`tools/audit_production_scene_load72.py`和对应测试。JAR重新核验固定为`2566f46604df80ddaad05801fb182a7217b9d057a2c8619e21fc54e49db083fe`；19份生产加载/init/配置/序列化/包装入口源码逐次核对规范换行hash。没有打包新JAR或更新既有审计守卫。

继承脚本固定resources提交dac2a9d43332ef51879522cf39da2831b0ca0543，拒绝Scene/3与Common工作区漂移。父报告固定SHA-256为`b99d5deeb475e2dae3269e7946f574d001230e98d9ae18c212b39ad4eada3a7d`，包含17,806组声明及13,616个已求值非dynamic组。逐文件重验13,785文件/104 Common的大小与SHA-256；manifest仍为`47b783755a7c8f714a868db551f76a503092e0fd7895cc7a787e6060e651c02d`。

每模式使用一个独立JVM，workingDir、user.dir、config/data/cache/plugins/packets路径全部指向本任务临时忽略目录，资源与Scripts只读指向当前checkout。helper启动时实际核验config文件及data归属；配置version14与固定源码一致，不读取根config替换生产设置。`Configuration.FAST_REQUIRE`是静态缓存，因此不在同一JVM中强切模式。每进程空白内存缓存启动，再用生产readScript预读固定manifest，核对实际生效源码与原文件；这不是借用旧磁盘cache。

JVM class-init日志实际拒绝GameData、ResourceLoader、database、GameServer/HttpServer/DispatchServer及SceneScriptManager初始化。本次最小与整场均未观察到这些初始化，ScriptLoader.init到结束新增Java线程名均为空。没有加载GameData业务表、创建manager/服务器/数据库或操作客户端；没有读写原mongodata、local/data或scene3_grid缓存。只读ID存在性不是本工具的验收范围，原8条缺Gadget ID仍由前批报告保留。

## 两种明确分开的执行口径

最小合成及真实代表组使用**原生产ScriptLibUserdata**。代表组133106026的local defs由Common/V3_0/CameraLook.lua读取：fast有真实nil错误被require吞掉，inline正常。两者仍均loaded=true，4 gadget/2 suite/4 trigger数量及typed对象hash相同，但fast缺少绑定函数LF_PointLook。这直接证明“数量相同”“load返回”“loaded=true”不能代替Common初始化完整。

整场使用已约定的**带业务调用边界的生产加载审计**：ScriptLoader初始化真实userdata后，只给真实函数调用加一个观察哨。成员读取与函数定义保持可用；真正invoke时先记录member/group并抛错，不调用后端、不返回假值。不存在的成员仍是原nil，未补空API。由于RequireFunction会吞异常，helper在外侧保留独立attempt记录，把该组标为范围外并停止后续组，不能用eval返回或loaded=true把它算通过。合成测试实际覆盖了此吞错路径。本次整场两模式attempt均为0；仍不能把带边界执行称为完整原环境兼容。

每组保存实际集合数量、套件绑定数量、缺引用/命名函数、binding类型/标量和递归表内容hash、生产对象hash及真实错误。function/userdata仅记录类型及绑定名，不把函数地址、upvalue或metatable行为冒充已执行验证。对象按生产字段序列化，排除transient反向引用；hash递归排序键，不删除真实标量/表值。

## 实际整场结果

结果针对**固定父报告的组声明顺序，每模式单JVM并复用该进程内生产缓存/engine状态**。engine默认bindings可能跨组留下状态；不能外推任意真实加载顺序都具有同样数量，本轮没有新增重排审计。

| 实际观察 | fastRequire=true | 现有inline路径，隔离配置false |
| --- | --- | --- |
| 已处理组 / loaded=true / 集合物化 | 13,616 / 13,616 / 13,616 | 13,616 / 13,616 / 13,616 |
| 有生产求值错误的组 / 错误事件 | **437 / 481** | **0 / 0** |
| 未见求值错误组 | 13,179 | 13,616 |
| monster / gadget / NPC / garbage gadget | 19,233 / 62,239 / 478 / 11,410 | 相同 |
| region / suite / trigger / variable | 4,328 / 20,871 / 25,838 / 4,357 | 4,330 / 20,871 / 27,133 / 5,008 |
| 套件绑定monster / gadget | 17,481 / 61,410 | 17,481 / 64,974 |
| 套件绑定region / trigger | 4,659 / 26,741 | 4,661 / 29,734 |
| 业务调用哨 / missingScript计数 / 生效源码差异 | 0 / 0 / 0 | 0 / 0 / 0 |

两模式共**540组binding结构hash不同、453组typed对象hash不同**；351组集合数量不同、353组套件绑定数量不同、268组binding类型/名字集合不同。437个错误组均有binding差异，另有103个没有错误日志的组仍有binding差异。“未见求值错误”不等于与inline等价，更不等于玩法通过。

### 为什么不是旧模型的407

旧[纯顶层求值比较](CLIENT_72_SCENE_SCRIPT_MIGRATION.md)记录407组fast错误，仍是其明确模型下的真实结果。生产437组包含全部旧407组，另有30组；它们全部有多个Common require（28组2个、1组3个、1组4个）。原始错误与每组require行保存在`scene-production-load-extra-fast-errors.json`，不把旧数改写为生产数。

源码中ScriptLoader.eval先设置currentBindings，成功后直接置null；内层Common成功同样置null，未恢复调用者。RequireFunction下一次看见null，就通过script.eval()使用engine默认bindings，而非原群组bindings。异常路径又不会执行清除，随后require的环境还会受到前次成功/失败影响。生产catch吞错并继续，错误事件也可能多于出错组数。

代表133301039先require Boss_MaranaSub成功，再require DeathFieldStandard；后者在table.insert(triggers,...)看到nil。新增纯声明双require测试独立复现：第一模块把first_marker=7写入群组binding，第二模块在fast无法读到它、抛错后群组仍继续；inline能读到并完成。这是对当前实际生产路径的复现，本批没有修复currentBindings或异常清理。

## 测试、首次失败与可重复性

**8项测试通过**，实际使用JAR与独立Java进程：local defs fast吞错/inline完整及实体/套件差异；成功首require后第二require丢失调用者bindings；哨异常被require吞掉后外侧仍拒绝/停止；单纯函数取值/定义不误报业务；缺Common虽loaded=true仍不通过；真实顶层异常留下loaded=true/null集合；两独立模式中相同纯声明binding hash相同；JAR漂移在初始化前拒绝。

保留了三个研究工具层面的首次失败，没有删生产错误来过关：

- 初次沿用前卡父报告hash，被当前b99d5dee报告守卫拒绝；人工核对新JAR/owner源码、17,806/13,616及相同manifest后，只固定本新工具的当前输入，见`scene-production-load-parent-baseline-first-rejection.log`。
- 测试曾把生产missingScripts按文件名断言；源码实际按文件夹Common计数，原失败日志保留为`scene-production-load-tests-first-failure.log`，纠正测试预期，没有改生产计数。
- 首两次整场报告不一致，仅inline的bindingsSHA变化，typed对象、错误、binding名字/标量等实际观察相同。原因是工具把scalar的Map.of(type,value)直接交Gson生成hash输入，JDK map迭代顺序受JVM种子影响。独立探针用语义相同、插入顺序相反的map稳定复现旧hash不同；仅修成递归键排序后，两map hash相同，而真实value从1变2仍改变hash。没有过滤地址/业务值或修改Lua求值。原两份报告/摘要及首次determinism分别保留在`scene-production-load-before-hash-fix-*`和`scene-production-load-first-determinism-failure.json`，探针前后结果为`scene-production-load-hash-order-{before,after}.txt`。

修正工具hash后，以同一份工具源码重新跑两轮完整fast/inline，正式报告与摘要均逐字节一致，证据`scene-production-load-determinism.json`。437/481和typed集合等真实结果在修正前后保持相同。

## 复现、建议与暂停交接范围

```powershell
$sceneProductionJdk = (Get-ChildItem local/runtime -Directory -Filter 'jdk-17*' | Select-Object -First 1).FullName
python -m unittest tools.test_audit_production_scene_load72 -v
python tools/audit_production_scene_load72.py --baseline resources --java-home $sceneProductionJdk --runtime-jar LunaGC-7.1.0.jar --scope probe --output local/migration72/evidence/scene-production-load-probe.json --summary-output local/migration72/evidence/scene-production-load-probe-summary.json
python tools/audit_production_scene_load72.py --baseline resources --java-home $sceneProductionJdk --runtime-jar LunaGC-7.1.0.jar --scope all-static --output local/migration72/evidence/scene-production-load-audit.json --summary-output local/migration72/evidence/scene-production-load-summary-draft.json
```

当前根config的fastRequire仍为**true**，未改写。离线证据支持后续隔离运行考虑已有inline路径，**这只是配置建议**；没有据此变更部署，也没有修生产RequireFunction。后续恢复工作需由主任务核对P0前置和资源/部署条件，不能把inline声明加载零错误当作可连接构建。

本机完整报告、草稿及重复文件为`local/migration72/evidence/scene-production-load-{audit,repeat,summary-draft,summary-repeat}.json`，测试/运行日志在同前缀logs；每模式另存raw JSON、stdout/stderr和class-init日志。摘要草稿不含机器路径/原始脚本行，交主任务统一接入共享证据hash，本批未改Project/Harness/observations或既有守卫。仅离线工具、测试、文档，无运行构建部署要求；原数据/缓存、8条缺ID、其余API与完整实机前置均保持原状态。
