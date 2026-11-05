# E-01c2：已复现Lua序列化问题的最小修复

2026-11-06。本批承接[生产序列化审计](CLIENT_72_SCENE_SERIALIZATION.md)的真实失败，只修改LuaSerializer、SceneRegion、SceneMonster、SceneGadget及新增生产JUnit。没有修改旧Lua、运行配置、资源、Proto、旧审计结果或其源码/JAR守卫；E-01c、E-01及D-02仍未完成。

## 失败复现与修复

先新增`src/test/java/emu/grasscutter/scripts/LuaSceneSerializationTest.java`并在未改生产源码时实际运行：7项测试中6项失败，普通typed标量列表测试通过。失败包括数值字符串类型、真实SceneSuite.init强转异常、两个boolean true未绑定，以及球/柱小数半径与包含边界。原始JUnit XML和Gradle失败日志保留在忽略的local/migration72。随后才应用以下最小修改：

| 实证问题 | 修复及保留范围 |
| --- | --- |
| EnergyAmplifier的数值字符串进入List<String>后变Integer；原5组套件崩溃 | 仅在LuaSerializer.serializeList分支中，先用真实`type()==TSTRING`识别字符串，再沿用原int/float/boolean路径。数值字符串保留原文，不按目标泛型强制改写数字；serializeMap与无关catch均不改 |
| SceneRegion.radius的1,210处小数被int截断 | 字段改为float，与Position坐标、height和既有serializer float路径一致。SPHERE三维距离与CYLINDER平面距离/高度判断继续直接消费该半径，不增加容差或修改边界比较 |
| SceneMonster.isElite、SceneGadget.is_use_point_array漏绑 | 参照已有setIsOneoff，为两字段各增加serializer实际寻找的显式setter；不重写Bean绑定框架、不猜新增别名 |

生产改动总计12行增加、3行删除。数值字符串在LuaJ中可以同时满足isint/isnumber，因此只把旧isstring分支提前而不检查真实type仍不够；本修复明确检查TSTRING。非整数数值仍输出Float，整数仍输出Integer，true/false仍输出Boolean。

全仓检查SceneRegion/radius读写和setRadius调用：生产半径直接消费点是SceneRegion.contains；EntityRegion持有SceneRegion并调用包含逻辑，没有读取int半径的独立协议字段。既有RegionMembershipTickTest的int常量赋值可正常拓宽为float。本次完整生产/测试源码编译均通过；没有为其他同名radius数据类改类型。

## 实际测试

修复后同一7项生产JUnit全部通过：

- 数值字符串`"52001"`、`"3.75"`、`"-1"`与普通/中文字符串保留String，整数、小数、boolean维持原运行类型。
- `tostring(52001)`与普通trigger名字通过真实LuaSerializer→SceneSuite.init绑定到实际SceneTrigger对象。
- 两个boolean字段分别检查true与false的实际序列化结果。
- 半径2.75的球检查三维内部、轴向边界、三维对角内部与外点；柱检查平面半径内/边界/外点以及正负高度边界/外点。

这些测试只直接使用LuaJ、LuaSerializer和数据类，不调用ScriptLoader、SceneGroup.load、manager、事件、服务器或数据库。没有执行trigger/action业务。其他测试源码由Gradle编译，但没有执行完整Java测试集，不能据此消除历史完整集失败。

复现命令：

```powershell
$env:LUNAGC_RUNTIME = 'portable'
$env:JAVA_HOME = (Get-ChildItem local/runtime -Directory -Filter 'jdk-17*' | Select-Object -First 1).FullName
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:GRADLE_USER_HOME = Join-Path $PWD 'local/gradle'
./gradlew.bat --no-daemon test --tests emu.grasscutter.scripts.LuaSceneSerializationTest -PskipHandbook=1 --console=plain
```

本机证据：`logs/scene-serialization-fix-before-portable.log`、`evidence/scene-serialization-fix-before-junit.xml`为7项/6失败；`logs/scene-serialization-fix-after.log`、`evidence/scene-serialization-fix-after-junit.xml`为修复后7项通过。测试格式整理后又用相同命令执行7项通过，见`scene-serialization-fix-final.log`及`scene-serialization-fix-final-junit.xml`。`evidence/scene-serialization-fix-verification.json`记录三轮测试数量/失败数/XML hash、5份源码规范换行hash及旧JAR未变hash。首次命令未设置便携GRADLE_USER_HOME而尝试下载Gradle，已核对并停止本次wrapper，原日志`scene-serialization-fix-before.log`保留；实际红/绿测试均使用local/gradle缓存。

## 构建、部署与下一验收

本批Gradle已重新编译生产与测试源码；没有生成或替换运行JAR，没有便携部署、服务器/数据库/客户端启动，没有操作旧数据或scene3_grid缓存。完整7.2协议ready前置尚未满足，不启动猜测协议服务；根代理负责后续JAR构建、运行前置核对和未部署记录。

旧审计固定的生产源码hash现应拒绝这4处变化，不能自动刷新常量把拒绝隐藏。本批保持旧JAR与历史审计原样，根代理需人工复核这4类语义及新JAR，再正式更新守卫，重跑13,616组生产序列化/索引审计和重复性验证。原5组是否全部恢复、1,210条截断及1,012条setter缺口是否全部消失，需要该次真实整场重跑确认，本7项JUnit不替代它。

8条缺Gadget ID、未知visionLevel、62个缺ScriptLib成员、当前fastRequire词法差异及实机协议/资源前置仍保留；本修复不补空业务实现、不删组、不改Lua或降低校验。
