# E-01c3：固定scene3脚本API缺口与实现边界

2026-11-06。本项是[继承Lua迁移](CLIENT_72_SCENE_SCRIPT_MIGRATION.md)的限定离线来源审计；没有执行trigger/action、实例化ScriptLib、调用业务或启动服务器/客户端。脚本仍标为旧实现参考，**E-01c、E-01和D-02均未完成，resourceReady=false。** 本批不改生产代码、资源、配置、既有审计或共享观察/门禁。

## 固定输入和实际核对

工具为`tools/audit_scene_script_api72.py`、独立JDK解析器`tools/Client72SceneApiSurface.java`及对应测试。每次核对固定resources提交dac2a9d43332ef51879522cf39da2831b0ca0543、Scene/3与Common已跟踪差异、父inline报告和共享摘要hash，并逐文件核对原始大小/SHA-256。父文件清单固定**13,785文件/104 Common**，规范化manifest SHA-256为`47b783755a7c8f714a868db551f76a503092e0fd7895cc7a787e6060e651c02d`；不借当前fast路径漏掉的文件缩小范围。

ScriptLib及18份读取/注册/相关业务依赖源码具有固定规范换行hash。JDK compiler仅parse声明，不做类型归属推断、类加载或Java执行；源内静态初始化即使会抛异常也不会运行。实际读取**206个public ScriptLib方法声明**，构造器及非public方法不算公开接口；相同名字的重载各保留签名、方法hash和行号。近名项只按拼写列出，不能把它们当成参数/业务一致的别名。

Lua使用有位置的词法扫描，行注释、带任意等号深度的长注释、短字符串和长括号字符串均单独识别，排除其中的假ScriptLib文本。分别记录点/冒号/常量方括号访问、调用语法、成员取值和函数声明。动态key、ScriptLib整体取值/局部遮蔽、动态require只登记开放边界；不试图构建通用Lua解释器或解析别名作用域。

从每个已求值非dynamic组的静态require图定位Common，再与父报告该组真实编译的直接成员名集合相交：**13,616个静态组逐组的词法名称集合与父compiled集合完全一致，差异0、未找到的静态require目标0；合计171个成员名。** 这是名称级来源对照，不是每个位置的bytecode归属或调用可达性证明，函数体即使存在也没有被执行。

62个历史缺成员对应**1,390处物理源码位置、1,503个静态组**。这些位置全部为调用语法；实际固定输入未观察到本扫描器所识别的动态key/整体取值/动态require边界，但rawget、_G、metatable、eval、其他别名等间接路径仍未闭合，不能用“0个已观察边界”宣称完整运行调用图。

本机完整报告的每个位置包含文件、行/列、展示行、原始行字节base64/SHA-256和相关静态组ID。本固定集合实际UTF-8解码失败数为0；工具仍保留原始行字节，并在遇到无效序列时按父Java读取器的替换规则展示，避免把展示文本等同于原始字节。start/end为解码文本的字符位置，不是文件字节偏移。原始行和完整引用列表仅存忽略目录，不提交原Lua。

## 62个历史缺成员：两项实际上有userdata入口

父工具仅检查`ScriptLib.class.getMethods()`，该统计仍原样保留。真实加载器使用ScriptLibUserdata，它会在Java反射delegate前检查包装表。其中：

- `GroupLuaCall.functions()`以字面量Map注册`ExecuteGroupLua`和`ExecuteActiveGroupLua`；ScriptLibUserdata.get通过groupFunctions.get优先返回它们。
- 两项分别在90组/12处、1组/2处被引用。源码可见目标组加载/已加载过滤、参数校验、上下文保存恢复与实际目标Lua函数调用路径；本工具只读源码，**没有调用这些函数**，也不据注册存在声称符合7.2契约。
- 当前固定public与显式userdata注册表合并后，历史62项中剩**60项未在本次复核入口找到实现，涉及1,469组**。这是纠正公开接口盘点边界，不是删除缺口或已完成兼容的结论。

对userdata get switch及GroupLuaCall/GroupTimerLua/MonsterTideLua/ScenePlayBattleLua的已阅读字面量注册形式分别取证。注册表之外的`"Get"`等判断字符串不会被误认成API key；没有实例化这些类来读取Map。公开接口中还存在日志明确标为unimplemented的近名方法，例如ClearPlayerEyePoint、AddPlayerGroupVisionType，它们不能用来伪补缺成员。

下表给出每项的组数、物理位置数和首个来源位置；完整逐行原文和全部静态组在本机`scene-script-api-audit.json`。表内“相关机制”只代表有下面所述具体源码机制，不代表API别名或可直接实现。

| 成员 | 静态组 | 物理位置 | 首个固定来源 | 当前边界 |
| --- | --- | --- | --- | --- |
| `AddChallengeDuration` | 12 | 1 | `Common/V3_0/Dreamland.lua:544` | 仅有相关机制；契约unknown |
| `AddEntityGlobalFloatValueByConfigId` | 12 | 22 | `Scene/3/scene3_group133008048.lua:578` | unknown |
| `AddExhibitionAccumulableData` | 1 | 2 | `Common/V3_0/Activity_WorldBoss.lua:87` | unknown |
| `AddExhibitionReplaceableData` | 12 | 1 | `Common/V3_0/SumeruOneLineDraw.lua:1303` | unknown |
| `AddExtraFlowSuite` | 79 | 139 | `Scene/3/scene3_group133103009.lua:174` | 仅有相关机制；契约unknown |
| `BeginCameraSceneLookWithTemplate` | 1 | 1 | `Common/V3_4/Activity_SeaLampParkour.lua:382` | unknown |
| `ChangeDeathZone` | 35 | 6 | `Common/V3_0/Boss_Marana.lua:718` | unknown |
| `ChangeGroupTempValue` | 31 | 9 | `Common/V3_0/Boss_MaranaSub.lua:147` | 仅有相关机制；契约unknown |
| `ClearExhibitionReplaceableData` | 12 | 1 | `Common/V3_0/SumeruOneLineDraw.lua:713` | unknown |
| `ContinueTimeAxis` | 5 | 1 | `Common/V3_4/SandwormChallengeControl.lua:91` | unknown |
| `CreateGadgetByConfigIdByPos` | 100 | 14 | `Common/V2_0/TemariChallenge.lua:314` | 仅有相关机制；契约unknown |
| `CreateGadgetByParamTable` | 14 | 3 | `Common/V3_4/SandwormToolkit.lua:94` | 仅有相关机制；契约unknown |
| `CreateGroupVariable` | 1 | 1 | `Scene/3/scene3_group133002363.lua:169` | 仅有相关机制；契约unknown |
| `ExecuteActiveGroupLua` | 1 | 2 | `Common/V3_0/VarunaWaterspout.lua:274` | userdata包装已注册；契约未验 |
| `ExecuteGroupLua` | 90 | 12 | `Common/V3_0/DeathZoneObservation.lua:406` | userdata包装已注册；契约未验 |
| `ForbidPlayerRegionVision` | 20 | 5 | `Common/V2_0/SetPerformanceOptimizationWithRegion_V2.0.lua:52` | unknown |
| `GetAranaraCollectableCountByTypeAndState` | 1 | 1 | `Common/V3_0/AranaraCollection.lua:89` | unknown |
| `GetChainLevel` | 182 | 14 | `Common/V3_1/ChiWangShooter.lua:429` | unknown |
| `GetCurrentLevelTagVec` | 4 | 2 | `Common/V3_0/DreamStone.lua:157` | unknown |
| `GetDeathZoneStatus` | 307 | 131 | `Common/V3_0/Boss_Marana.lua:903` | unknown |
| `GetExhibitionReplaceableData` | 13 | 3 | `Common/V3_0/SumeruFarm.lua:346` | unknown |
| `GetGadgetAbilityFloatValue` | 21 | 9 | `Common/V3_1/Desert_HourGlass.lua:246` | unknown |
| `GetGadgetConfigId` | 413 | 54 | `Common/BlackBoxPlay/DesertEnergySpark.lua:60` | 仅有相关机制；契约unknown |
| `GetGameTimePassed` | 1 | 1 | `Common/V3_0/VarunaWaterspout.lua:196` | unknown |
| `GetGroupAliveMonsterList` | 35 | 2 | `Common/V3_0/Boss_Marana.lua:675` | unknown |
| `GetGroupLogicStateValue` | 8 | 8 | `Scene/3/scene3_group133301278.lua:321` | unknown |
| `GetLevelTagNameById` | 3 | 1 | `Common/V3_0/DreamStone.lua:162` | unknown |
| `GetOfferingLevel` | 1 | 1 | `Scene/3/scene3_group133008191.lua:161` | unknown |
| `GetPlatformArrayInfoByPointId` | 89 | 3 | `Common/V3_1/ChiWangShooter.lua:83` | unknown |
| `GetPlatformPointArray` | 16 | 1 | `Common/V3_0/VisualizationPlantOne.lua:92` | unknown |
| `GetSceneOwnerUid` | 317 | 42 | `Common/V3_0/HookLook.lua:143` | 仅有相关机制；契约unknown |
| `GoToFlowSuite` | 149 | 247 | `Scene/3/scene3_group133210002.lua:264` | 仅有相关机制；契约unknown |
| `IsChallengeStartedByChallengeId` | 6 | 4 | `Common/V3_4/Activity_SeaLampParkour.lua:87` | unknown |
| `IsInRegion` | 11 | 6 | `Common/V3_0/Activity_WorldBoss.lua:84` | 仅有相关机制；契约unknown |
| `IsPlayerTransmittable` | 12 | 1 | `Common/V3_0/Dreamland.lua:175` | unknown |
| `LockMonsterHp` | 2 | 3 | `Scene/3/scene3_group133003504.lua:178` | unknown |
| `MarkGroupLuaAction` | 138 | 6 | `Common/BlackBoxPlay/DesertEnergySpark.lua:60` | unknown |
| `PauseTimeAxis` | 5 | 1 | `Common/V3_4/SandwormChallengeControl.lua:103` | unknown |
| `PrintGroupWarning` | 126 | 19 | `Common/V3_1/PermissonOperator.lua:61` | 仅有相关机制；契约unknown |
| `RecieveAllAranaraCollectionByType` | 1 | 1 | `Common/V3_0/AranaraCollection.lua:113` | unknown |
| `RemoveExtraFlowSuite` | 20 | 20 | `Scene/3/scene3_group133210208.lua:266` | 仅有相关机制；契约unknown |
| `RevertPlayerRegionVision` | 20 | 5 | `Common/V2_0/SetPerformanceOptimizationWithRegion_V2.0.lua:71` | unknown |
| `SetChainLevel` | 8 | 6 | `Common/V3_1/SetChainLevel.lua:58` | unknown |
| `SetChallengeDuration` | 12 | 1 | `Common/V3_0/Dreamland.lua:374` | 仅有相关机制；契约unknown |
| `SetFlowSuite` | 126 | 175 | `Scene/3/scene3_group133103009.lua:177` | 仅有相关机制；契约unknown |
| `SetGadgetHp` | 4 | 7 | `Scene/3/scene3_group133002264.lua:136` | unknown |
| `SetGadgetTalkByConfigId` | 2 | 5 | `Scene/3/scene3_group133307259.lua:83` | unknown |
| `SetGroupDead` | 242 | 242 | `Scene/3/scene3_group133001194.lua:109` | unknown |
| `SetGroupLogicStateValue` | 314 | 82 | `Common/V3_0/DeathFieldStandard.lua:136` | unknown |
| `SetLimitOptimization` | 7 | 4 | `Common/V2_0/SetPerformanceOptimizationWithRegion_V2.0.lua:44` | unknown |
| `SetMonsterHp` | 3 | 8 | `Scene/3/scene3_group133002265.lua:156` | unknown |
| `SetPlayerEyePoint` | 16 | 6 | `Common/TD_Lib.lua:10` | unknown |
| `SetPlayerEyePointStream` | 7 | 4 | `Common/V2_0/SetPerformanceOptimizationWithRegion_V2.0.lua:40` | unknown |
| `SetPlayerGroupVisionType` | 31 | 13 | `Common/TD_Lib.lua:12` | unknown |
| `SetPlayerStartGallery` | 9 | 2 | `Common/V2_0/TemariChallenge.lua:399` | unknown |
| `ShowReminderByUid` | 111 | 4 | `Common/V3_1/Desert_HourGlass.lua:146` | 仅有相关机制；契约unknown |
| `SkipTeyvatTime` | 13 | 2 | `Common/V3_4/ShelterWorktopControl.lua:79` | unknown |
| `StartChallenge` | 1 | 1 | `Scene/3/scene3_group133210421.lua:359` | unknown |
| `SwitchSceneEnvAnimal` | 8 | 6 | `Common/V2_0/SetPerformanceOptimizationWithRegion_V2.0.lua:46` | unknown |
| `TrySetPlayerEyePoint` | 10 | 10 | `Scene/3/scene3_group133001313.lua:177` | unknown |
| `UnlockMonsterHp` | 1 | 2 | `Scene/3/scene3_group133003504.lua:206` | unknown |
| `UnlockScenePoint` | 3 | 4 | `Scene/3/scene3_group133001112.lua:124` | 仅有相关机制；契约unknown |

## 可证的复用机制与下一项

逐项报告保留当前public精确匹配、最多3个近名声明、userdata注册证据，以及有明确源码的业务机制。所有missing项的7.2业务语义和入场必需状态仍为unknown。本次只为16项找到可具体引用的相关机制，其余不凭名称补候选：

- **GetGadgetConfigId**：413组/54处。固定Lua使用`gadget_eid`表字段，Scene.getEntityById有实体ID查找，GameEntity的configId有独立字段和Lombok getter。现有GetGadgetIdByEntityId返回资源gadget ID，**不能直接当作config ID别名**。这项覆盖最高，适合下一批先做非零实体ID→不同config/resource ID的最小独立回归；HookLook:148-151对gadgets[configId]==nil有拒绝分支，但Dreamland:352-355直接索引；调用处未决定无效ID应返0/-1/nil。gadget类型、无效/跨组ID、返回错误和ABI仍要明确。
- **GetSceneOwnerUid**：317组/42处。Scene.getHost明确转到World host，存在owner player来源；当前Lua只取返回UID，HookLook:143-145继续传给GetAvatarEntityIdByUid，Player.getUid有明确返回源码。适合与上项组成小的只读查询适配批，但上下文/多人权威、空场景和7.2语义仍未由本审计执行确认。
- UnlockScenePoint可对照PlayerProgressManager.unlockTransPoint，但该路径涉及保存、奖励、解锁锁状态、任务和通知，不能仅看返回bool就包成默认成功。ShowReminderByUid也不能把已有全场景broadcast当作定向发送。
- 4个FlowSuite接口有现成GroupSuite添加/移除/跳转机制，2个临时/创建变量接口有现成变量写入机制；Flow身份、生命周期与持久化差异未知，不把近名方法直接接线。
- Add/SetChallengeDuration可对照真实挑战timeLimit消费及KillMonsterTimeIncTrigger的限额增时/通知，但死亡触发增时不是缺Lua接口的契约。IsInRegion可对照SceneRegion.contains的几何机制；缺接口如何选择实体/玩家/区域仍未知。
- gadget创建相关2项只找到已有table/manager创建机制，PrintGroupWarning只找到上下文日志机制；均保留参数、返回值和业务差异。

GetChainLevel/DeathZone/Exhibition/Offering/Aranara、血量锁定、EyePoint、逻辑状态等剩余接口没有在本次复核范围形成可证契约，不给空实现或猜字段。业务服务复核限于报告列明的固定源码，不把“这里没找到”推论成全仓、插件或目标客户端完全不存在。

## 测试、复现与交付范围

9项测试通过：注释/短字符串/长括号假命中；等号深度/CRLF/行列位置；调用/取值/声明及冒号/常量下标；动态key/转义key/别名/遮蔽边界；静态require循环及缺目标/动态require分离；未结束文本拒绝；JDK解析public/private且不加载抛异常的静态初始化；固定来源漂移拒绝；注册key排除方法内无关字符串。测试不运行Gradle、不使用JAR或生产服务。

```powershell
$sceneApiJdk = (Get-ChildItem local/runtime -Directory -Filter 'jdk-17*' | Select-Object -First 1).FullName
python tools/audit_scene_script_api72.py --baseline resources --java-home $sceneApiJdk --output local/migration72/evidence/scene-script-api-audit.json --summary-output local/migration72/evidence/scene-script-api-summary-draft.json
python -m unittest tools.test_audit_scene_script_api72 -v
```

真实固定输入运行两次，报告与摘要均逐字节一致；证据`local/migration72/evidence/scene-script-api-{audit,repeat,summary-draft,summary-repeat,determinism}.json`，日志同前缀及`scene-script-api-tests.log`。摘要草稿不含原始行字节/机器路径，供主任务统一接入，本批不新增共享observations或改Project/Harness。

仅离线工具/测试/文档，无运行代码构建部署或重启要求。没有调用缺API，没有改变既有脚本、缓存、8条缺Gadget ID、visionLevel未知、fastRequire配置或实机前置；研究API名称及源码候选不能提升resourceReady或替代真实入场。

主线接入：固定来源、19份源码及9测试经复核；原draft SHA-256 143aac4cddfff82a26911b1a45d1018ef839d665ad71a2a29a995afc8e977c8f原样保存为[迁移前基线](../protocol/cn-7.2/scene-script-api-baseline-observations.json)。它是第60份研究输入，原矩阵/源码/三层不变、recover两次一致；当前工作树358研究选集通过（含并行列表测试12项）。E-01c3限定完成；E-01c4将单独对GetSceneOwnerUid做真实Lua桥接红绿验证，届时必须保留本历史基线并复核源码/构建变化，不能把本报告称为修改后当前状态。
