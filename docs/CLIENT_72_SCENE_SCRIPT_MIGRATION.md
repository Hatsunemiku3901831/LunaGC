# E-01c：参考既有实现迁移scene3脚本

2026-11-06（Australia/Sydney）。用户确认没有7.2 Scene/Block/Group/Common Lua来源，允许参考LunaGC7.1实现尝试迁移。本项据此核对固定既有脚本与目标7.2表；**没有把原脚本冒称7.2原始导出，E-01c/E-01/D-02均未完成。** 尚未改写或安装脚本，也没有以空返回/默认成功补齐缺失接口。

## 已完成的实际检查

入口为`tools/audit_scene_scripts72.py`和`tools/Client72SceneScriptAudit.java`。每次先复现[E-01b](CLIENT_72_SCENE_RESOURCES.md)的固定tree/blob/读取源码守卫，再核对3张实体表的Git blob/大小/SHA-256及稳定ID。原始Monster/Npc表新增到固定候选忽略目录，Gadget表继承前批；不使用当前混合表填目标缺失ID。

既有脚本来自resources锁定提交dac2a9d43332ef51879522cf39da2831b0ca0543，并拒绝Scripts/Scene/3或Common的工作区漂移。该检查不会覆盖或提交原有ScriptSceneData修改。现有编译JAR仅作LuaJ、既有枚举绑定和公开ScriptLib成员名的离线依赖，记录其hash；不启动Grasscutter.main、服务、数据库或生产资源加载器。

独立Java进程用真实LuaJ编译/执行顶层声明，每个群组独立Globals。默认读取当前config.json的server.fastRequire；另提供inline/fast显式比较，不改写配置。fast时require在同一Globals另编译Common，inline时按现有ScriptLoader.getScript把Common文本放进同一个chunk，保留群组局部变量的词法作用域。枚举及EventType/GadgetState/RegionShape参照当前ScriptLoader.init，不新造目标枚举值。触发器/条件/action函数只编译，不调用；没有ScriptLib成功模型。

结果：

- 继承脚本的出生位置位于**3003块**。该出生位置本身仍来自既有实现，不是独立恢复的7.2出生点。
- 686个群组全部顶层求值成功；共读取698个脚本，其中10个Common。块声明分403个非dynamic群组、283个dynamic群组，未删除任何一类来使结果通过。
- 检查装置4,033、怪物1,454、NPC 5条正ID引用，合计**5,492条全部存在于固定7.2表**。覆盖稀疏整数键的集合、garbages集合、套件成员及trigger命名函数存在性；没有检查全部实体技能、掉落、任务、挑战、group链接或实际生成行为。
- 从LuaJ编译的原型指令中恢复88个直接ScriptLib成员引用；注释/字符串内的伪调用不计。当前编译API缺25个成员，别名/动态下标/非相邻字节码等调用仍未闭合，不把此统计当完整调用图或实际调用次数。
- 25个缺成员涉及10个非dynamic群组和26个dynamic群组。非dynamic组内涉及GetGadgetConfigId、SetGroupDead、SetPlayerEyePoint、SetPlayerGroupVisionType、TrySetPlayerEyePoint、LockMonsterHp、UnlockMonsterHp。它们是否在最小入场操作触发仍须继续跟踪，不能一律当可忽略历史活动，也不能因静态出现就全部当D-02必需。

## 全scene3索引输入与实际迁移条件

当前`SceneScriptManager.getGroupGrids()`在缺缓存时遍历**全部scene3块的非dynamic群组**以建立索引。因此已扩展实际检查到64块声明的17,806组，其中13,616组非dynamic、4,190组dynamic。all-static模式保留全部声明，按该生产索引过滤条件只求值非dynamic群组；这不是删除dynamic资源，后续动态加载仍未覆盖。结果如下：

| 路径 | 顶层求值成功 | 实际读取文件 / Common | 正实体ID引用 | Lua错误 / 缺ID |
| --- | --- | --- | --- | --- |
| 当前配置fastRequire=true | 13,209 / 13,616 | 13,784 / 103 | 90,727 | 407 / 8 |
| 现有inline路径，离线显式选择 | 13,616 / 13,616 | 13,785 / 104 | 95,600 | 0 / 8 |

比较定位到分块编译的词法作用域差异，典型失败是Common读取群组的`local defs`，分开编译Common无法访问局部变量。原日志保留，涉及ChiWangShooter、DesertEnergySpark、CameraLook、DeathFieldPlay等；没有改Lua、强置返回或吞异常。inline比较复用已有实现路径即能消除全部407处顶层错误，是后续隔离迁移配置的具体依据；**当前配置仍未改，生产加载未验证**。

inline正引用细分装置73,649、怪物21,451、NPC 500。62个缺ScriptLib成员涉及1,503个非dynamic群组；当前fast配置下仅看到1,501组，不能把因顶层失败未执行完的组误当作没有引用。成员完整清单保存在共享摘要，不给缺方法补空实现。编译成员名相符也不代表参数签名、业务效果或网络枚举已核实。

8条缺ID全部位于`garbages.gadgets`，且在当前基线Gadget表也不存在，未用旧行或伪造7.2行补齐：

| group | config_id | gadget_id |
| --- | --- | --- |
| 133301045 | 45002 | 70200015 |
| 133301046 | 46002 | 70200016 |
| 133302007 | 7002 | 70200012 |
| 133302008 | 8002 | 70200013 |
| 133302009 | 9002 | 70200014 |
| 133303035 | 35002 | 70200018 |
| 133303036 | 36002 | 70200019 |
| 133304018 | 18002 | 70200017 |

内建源码中，SceneGroup.load反射读取该集合并绑定group，getGroupGrids使用它们的位置/vision_level；常规getGadgetsInGroupSuite→createGadget使用suite.sceneGadgets。仓库内没有getGarbageGadgets调用方，Server目录未见GadgetExcel替代表。这限定了当前查到的消费路径，不能据此断言任何插件/动态逻辑永远不用这些引用，也不从问题报告删除它们。

`Scene.loadBlock`与可见性路径仍依赖邻块、vision配置和实际实体。新隔离运行不得借用旧scene3_grid缓存来掩盖未检查脚本，也不删除现有cache。下一步核对生产SceneMeta/SceneGroup序列化及索引行为，再依据真实调用安排必需接口适配和回归；保留来源/服务版本，协议和补丁前置就绪后仍需入场、重登验收。

## 测试、复现与边界

```powershell
$env:GAME_PATH = & ./tools/game_path.ps1
$sceneJdk = (Get-ChildItem local/runtime -Directory -Filter 'jdk-17*' | Select-Object -First 1).FullName
python tools/audit_scene_scripts72.py --source local/migration72/sources/AnimeGameData/d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1 --baseline resources --client $env:GAME_PATH --java-home $sceneJdk --runtime-jar LunaGC-7.1.0.jar --output local/migration72/evidence/scene-script-audit.json --summary-output protocol/cn-7.2/scene-script-observations.json
python tools/audit_scene_scripts72.py --source local/migration72/sources/AnimeGameData/d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1 --baseline resources --client $env:GAME_PATH --java-home $sceneJdk --runtime-jar LunaGC-7.1.0.jar --all-static --output local/migration72/evidence/scene-grid-script-audit.json --summary-output protocol/cn-7.2/scene-grid-script-observations.json
python tools/audit_scene_scripts72.py --source local/migration72/sources/AnimeGameData/d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1 --baseline resources --client $env:GAME_PATH --java-home $sceneJdk --runtime-jar LunaGC-7.1.0.jar --all-static --require-mode inline --output local/migration72/evidence/scene-grid-inline-audit.json --summary-output protocol/cn-7.2/scene-grid-inline-observations.json
python -m unittest tools.test_audit_scene_scripts72 -v
```

6项测试全部通过，实际编译Java并执行LuaJ：require/稀疏集合/枚举、顶层函数不被误执行、缺实体及garbage来源/套件成员/命名函数、编译API引用忽略注释字符串、缺Common与真实Lua报错不伪造成功、全块声明与dynamic过滤、局部defs在inline/fast两路径的真实差异。加原资源14项共20项聚焦回归通过。三份真实报告和摘要均二次运行逐字节一致；Java Map无序字段由Python最终规范排序，避免不同JVM的顺序伪差异。

完整研究选集294项通过（scene-script-research-regression.log）；研究结构校验通过，ready仍拒绝204缺口。人工复核矩阵仅接入三份新Lua摘要的hash，来源源码、Proto依赖、候选、三层状态与开放边界均未变，53份研究输入受约束；两次恢复矩阵相同。此次仅离线工具/测试/文档，不触发运行构建或重启。

初次探索仅绑定三个常量对象，4个群组因缺VisionLevelType出现nil；正式工具复用完整既有枚举绑定后686组通过。此为探索环境缺口，不是客户端或原脚本故障；原失败日志保留，未改脚本绕过。

本机证据位于忽略local/migration72：evidence/scene-script、scene-grid-script、scene-grid-inline各自的{audit,repeat,summary-repeat,determinism}.json，logs同名前缀及scene-script-focused-tests.log，探索日志scene-script-probe和probe-complete-enums.log。共享[出生块摘要](../protocol/cn-7.2/scene-script-observations.json)、[当前配置全索引摘要](../protocol/cn-7.2/scene-grid-script-observations.json)、[inline比较摘要](../protocol/cn-7.2/scene-grid-inline-observations.json)只含来源/工具hash、清单hash、计数和明确缺口，不提交原始Lua或表数据。生产SceneMeta/SceneGroup反射序列化、实际ScriptLib参数/效果、实体生成、存档、网络枚举、完整资源及实机均未验；通过顶层求值不提升resourceReady。
