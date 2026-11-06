# E-01d1n2：30302 启动、保存与未就绪门禁

本合同在独立 Mongo 和测试 JVM 使用真实 `QuestManager`、`QuestSystem`、`World`、未启动的 `GameServer` 与数据库写入。它不是完整 `Player.onLogin`、真实账号登录、任务完成、目标协议或客户端验收；目标 303 恢复和未知字段见 [n1](CLIENT_72_D02_QUEST_303_RECOVERY.md)，入口与目标字段差异见 [入场审计](CLIENT_72_D02_QUEST_ENTRY.md)。没有修改生产、Proto、资源、配置或映射。

## 实际结果

最终独立复现驱动退出 0，定向 **2 项测试通过，失败/错误/跳过均为 0**。两项使用同一唯一测试库内不同的合成 UID、目标 chapter 303 全部 63 个 BIN fallback 子项，真实 `QuestData`/`QuestManager` 与旧真实 GlobalCombat。目标 30302 Excel 原始行再次由真实 `ResourceLoader` 拒绝，拒绝日志保留；没有用别名、手改 JSON 或关闭任务。

- 直接入口：对等级 1 的新玩家调用真实 `QuestManager.addQuest(30302)`，构造章 303 和 63 个子项；30302 为 UNFINISHED，62 项为 UNSTARTED，30302 的 finish progress 为 `[0]`，章的 questGroupSuites 为空。真实 `PacketFinishedParentQuestUpdateNotify` 和 `PacketQuestListUpdateNotify` 按此顺序到达测试包边界，未连接网络。等待已提交状态事件及数据库保存后，从 Mongo 原始 BSON 和 `DatabaseHelper.getAllQuests` 用同 UID 新玩家冷读，得到同一章节/子项状态与进度。
- 进度入口：另一个新 UID 反射调用真实 `PlayerProgressManager.addStatueQuestsOnLogin` 私有入口，实际启动 63 个子项；冷读得到 63 个 UNFINISHED。测试核对构造了 1 个 parent 更新包和 63 个 quest 更新包。此直接调用不运行 `PlayerProgressManager.onPlayerLogin` 的其他 open-state 步骤，也不是完整玩家登录。

`ResourceLoader.loadedAll` 从头到尾保持 **false**，测试没有设置或反射修改它。冷读玩家调用真实 `QuestManager.loadFromDatabase` 后，因资源未就绪保持 manager 为空；直接入口测试对比该调用前后 BSON 完全相等，验证门禁未删改存档。**正向 QuestManager 重绑定尚未验证**：它要求真实完整资源就绪，不能以本次目标章节子集或改写 ready 标志替代。

目标 chapter 303 中这些任务的具名 finish condition 均为 COMPLETE_TALK，beginExec 均缺失；本实验没有发送完成对话或执行 finishExec 中的解锁点/区域。测试的单线程 quest executor barrier 只用于已提交事件：此受控章节的自动接受索引为空、开始任务无 beginExec，所有完成条件为 COMPLETE_TALK；它不代表任意递归异步任务的全局闭包。随后 `DatabaseHelper.awaitIdle` 成功，再读取真实 BSON，不把内存状态当作保存确认。场景 Lua、任务完成及其后的 unlock effects 未执行。

测试只在独立配置中使用 questing=true、enableScriptInBigWorld=true，隔离场景未加载目标 `ChapterExcelConfigData`；fixture 明确检查当前 `ChapterData` 的 beginQuest map 中无 30302。因此本轮没有 `PacketChapterStateNotify`，这不能证明真实目标资源下该包不触发。包捕获仅表示 Java 对象构造并交到边界，不表示网络发送，当前 opcode/字段也未作 7.2 wire 认证。随机存档、旧号恢复、真实资源就绪、完整生产队列/插件、运行 revision 和客户端均未覆盖。

## 输入、复现和证据

目标 QuestExcel SHA-256 `c8561299f543456573b0fa16404e210bcd2ecf84ce5006eb63dd4b2845137a87`，目标 303 BIN `aaf6901da4c30528047c1210bc636f810b368d5fb2f19aad6369bbb956dd3cca`；旧 QuestExcel `aacb6edba8b97e3dd39fef80444bb180756fe1d6090c341cd0545ec08f0951a3`，旧 303 BIN `44b646a0f29a3a1a1b780d58902ee28e7d5749ba13a45e6b9b2a6d48d12ad8cc`，旧 GlobalCombat `df4b1d9dce5756251195995a4c30ae40f66c792735bb0977d166dd988f9965c0`。测试直接核对这些文件 hash。固定三客户端输入已重读且与 n1 一致。测试使用当前源码 classes；根 JAR SHA-256 `dfdebbda0352055540c44a54e2eebfd4ffdfe679db74270044d3d55aeb006ec3` 仅为独立 Mongo shutdown 辅助程序提供依赖，不用于覆盖测试类。

最终测试源码 `src/test/java/emu/grasscutter/game/quest/D02Quest303StartMongoTest.java` SHA-256 `45f50ea989ae8df00ab5a4d56067330e6d3754d8ed07b1c5563ba937b7b60067`，class `1c207b84ffee3b95a5d721658a2bca9d05b540e9e774369717416eb85adc2974`，最终复现 XML `e42d6f36f936d5d4033fb20b43ff015f7606653db022c178cb8b7c3e8965c3e5`，驱动 SHA-256 `1344ff0fbc601c0e4c6f0778bd0899926678a4625674749beb3056322bb07984`。复现日志、原始 BSON、独立库和 `input-sha256.json` 在 `local/migration72/d02-r3-quest303-replay-86bad2d8e647416e9adbe5de79b4d750/`；该输入清单 SHA-256 `d72b2236b784f095e61c17400d40448ad0ee95e73dbe9d5975e81e66087b6f46`，13 个关键生产源码与对应 class 的 26 项指纹清单 `critical-code-sha256.json` 的 SHA-256 为 `f53c2dbf797b3110dbf03bc072b3086a5030f1f25a45aa1f062becb3efdfc7c3`。class-init 未见 HTTP、DispatchServer 或 ScriptLoader 初始化；GameServer 是未启动容器。

主线已逐项审核测试/驱动及等待/门禁边界，核11输入及26生产source/class指纹，并按同驱动新建另一独立库实际复现2项全过，Mongo正常shutdown exit0、27027释放，根配置/resources原差异保持。主线目录 `local/migration72/d02-r3-quest303-replay-873511c485744e10b8dad1d75e481df8/`，证明 `local/migration72/evidence/d02-r3-quest303-start-main-review.json`；BSON门禁前后原样相同，未设置loadedAll。各批次两项不相加成新验收范围。

首轮直接入口测试通过，进度入口因测试反射时把 Player 错作方法接收者而失败；该生产方法尚未被调用。更正为真实 ProgressManager 接收者后，两项通过；再增加直接入口其余62项和进度/组套件冷读断言，并用新独立库通过。首轮 XML/日志和前两次库保存在 `local/migration72/d02-r3-quest303-n2-446975c8af8b452698180f682a209343/`，不将多次运行相加为更多验收测试。

原手工测试 Mongo 与最终驱动的新 Mongo 均以 admin shutdown 正常关闭，日志记录 exitCode 0；最终复核 27027 释放，原 27017 的既有进程保持。数据库和运行目录保留，未 drop 任何库。运行前必须协调独占 Gradle 和 27027，并准备上述候选文件、便携 JDK17/Mongo 和固定 JAR；脚本检查端口、创建唯一目录/数据库，核对监听进程和 dbpath 归属后才发 shutdown，并验证退出与端口释放：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/audit_d02_quest303_start.ps1
```

下一个独立问题是目标资源实际完成加载后，真实 `QuestManager.loadFromDatabase` 对保存章/子项的重绑定及后续真正的 Player 登录与任务/场景包；这需要前置资源、异步和目标 wire 证据，不能通过修改 `loadedAll` 或清理旧账号数据绕过。R3/resourceReady 和 D-02 均未完成。
