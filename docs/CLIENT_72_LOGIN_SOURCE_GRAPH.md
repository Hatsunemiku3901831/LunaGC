# A-03a 登录与场景的方法级源码索引

2026-11-06，限定验收完成。此项整理当前遗留服务端的源码调用关系，为 A-03 后续追踪提供入口；没有修改运行代码、协议或配置，没有运行游戏、访问数据库或进行部署。**A-03 完整调用闭包和 D-02 实机验收仍未完成。**

## 输入、工具与结果

`protocol/cn-7.2/login-source-review.json` 固定 12 份源码的规范换行 SHA-256、9 个根方法和 13 条人工复核边。9 个根分别是 GetPlayerToken、PlayerLogin、SetPlayerBornData、EnterSceneReady、SceneInitFinish、EnterSceneDone、PostEnterScene、Ping、UnionCmd 的 Handler.handle；另纳入 Player、World、Scene 的具体方法。

`Client72JavaSurface.java` 使用 JDK 17 编译器 API，仅执行语法解析，不解析依赖/符号、不处理注解、不运行项目。它记录方法、构造函数、字段/初始化块、调用/构造/方法引用和参数源跨度；局部/匿名类拥有独立作用域，lambda 记录语法深度。源坐标为原文件 UTF-16 偏移，哈希将 CRLF 规范为 LF；二者不能混算。

`client72_java_surface.py` 和 `review_client72_login_surface.py` 从根方法遍历人工边及同 owner 唯一名称/参数数量候选边，重载歧义和未关联调用保留。源码摘要或人工调用出现次数变化时拒绝旧复核表。

| 项目 | 结果与限制 |
| --- | --- |
| 源文件 / 根方法 / 选中方法 | 12 / 9 / 80；不再把整个 Player.java 当作登录方法 |
| 调用边 | 96：人工复核 13，其余 83 仅为同 owner 唯一名称/参数数量候选 |
| 未关联调用位置 | 1012，含库、getter、管理器、服务等；不是 1012 个协议消息 |
| 直接构造参数类型名 | 61，包含动态 BasePacket；仅指 send/sendPacket/broadcastPacket 的直接 new 参数 |
| 所有包形构造类型名 | 62；额外的 PacketWindSeedUID 来自工厂返回，不算直接发送参数 |
| Player.onLogin 直接构造参数 | 20；不包含管理器或服务内部发送，也不证明实际执行 |
| 完整性 / 目标协议 / 运行状态 | closureComplete=false、targetProtocolReady=false、runtimeStatus=not-tested |

这组数字与 A-02 的 114 名称/471 Proto 文件不是同一口径，不能相减得出“缺少的包”。A-02 保守文件集合包含非登录代码，而本索引还有未展开的管理器等边界。条件分支和 lambda 以源码并集呈现，既不是线上顺序，也不是 7.2 最小必需消息集；Packet 前缀和发送方法名只是语法筛选，不证明继承类型或运行发送。

## 已复核的业务路径

`HandlerPlayerLoginReq.handle` 经 AccountLoginAdmission 回调进入 handleAdmitted。资源未就绪时关闭会话；无角色且 intro.enabled 时进入 PICKING_CHARACTER，按配置可发送选人通知，发送 LoginRsp，安排可选超时回调后返回，尚不调用 Player.onLogin。已有角色则先 onLogin 再 LoginRsp；无角色但未启用选人流程时会创建默认旅行者。

超时回调仅在 fallbackSeconds>0 时安排，内部复查会话状态、玩家身份及角色数量，再创建默认旅行者和调用 onLogin。索引保留嵌套 lambda 深度，但不据此推断异步时序。D-02 的新号验收仍须真实选人请求/响应，不能靠默认角色兜底宣称通过。

`HandlerSetPlayerBornDataReq` 同样经过准入回调，校验男女主角 ID 及资源存在，空角色情况下创建角色、配置队伍并保存，再调用 onLogin，按配置或遗留常量构造响应，最后发送欢迎邮件。此处记录的是源码行为；配置包号和旧常量未因此成为 7.2 映射，邮件和保存服务仍需继续追踪。

13 条人工边覆盖三个登录分支到 Player.onLogin、onLogin 到 World 构造及 tryAddPlayer、World 构造链、getSceneById 到 Scene 构造、世界加入流程到 Scene.addPlayer，以及场景初始化/入场 Handler 到 playerSceneInitialized、spawnPlayer、showOtherEntities、loadNpcForPlayerEnter、loadGroupForQuest。完整端点、匹配表达式及出现次数在复核表中，具体方法/行号在共享摘要中。

Player.onLogin 的 20 个直接构造参数为 PlayerDataNotify、StoreWeightLimitNotify、PlayerStoreNotify、ItemCdGroupTimeNotify、AvatarDataNotify、AvatarSelectionDataNotify、AvatarWeaponSkinDataNotify、FinishedParentQuestNotify、BattlePassAllDataNotify、WorldWatcherAllDataNotify、BeyondCosmeticDataNotify、QuestListNotify、QuestGlobalVarNotify、CodexDataFullNotify、AllWidgetDataNotify、WidgetGadgetAllDataNotify、CombineDataNotify、GetChatEmojiCollectionRsp、PlayerEnterSceneNotify、PlayerLevelRewardUpdateNotify（源码类名均带 Packet 前缀）。这些名字只来自当前服务端，不是新增客户端消息身份确认。

## 尚未闭合的边界

- 管理器/服务：任务、活动、商城价格、队伍、成就、家园、树脂、锻造、合成、烹饪、纪行、邮件和保存等方法及构造函数；需按实际调用入口继续复核，不能把整个文件直接算入必需集。
- 场景：Scene 构造中的控制器与 SceneScriptManager，场景/任务/日常脚本、资源条件和后续回调；场景初始化和入场不只包含 Handler 的直接发送。
- 动态行为：AccountLoginAdmission、调度器、PlayerJoinEvent/插件、UnionCmd 内层分派、事件替换与 fanout；A-02 已有表面审计，但未证明运行闭包。
- Java 语义：继承/虚调用、Lombok 生成访问器和重载绑定未执行符号归属。解析器记录字段/静态初始化器，但图遍历不自动展开隐式初始化执行。
- 目标客户端：实际新旧号、选人及入场序列和三层映射仍缺证据；未关联的重复登录 onLogout 不扩张成整个退出流程。

下一项可从 Player.onLogin 的管理器与服务调用逐条补充源码及人工边，再将消息构造追到 Proto 引用；最终仍须结合真实 CN 7.2 序列收敛。A-03a 不批准任何 CmdID、字段业务语义或正式协议生成。

## 复现与实际验证

在 checkout 根目录运行，JDK 路径以本机已核对的便携安装为准：

```powershell
python tools/review_client72_login_surface.py --java-home local/runtime/jdk-17.0.20.1+1 --output local/migration72/evidence/login-source-surface.json --summary-output protocol/cn-7.2/login-source-observations.json
python -m unittest tools.test_client72_java_surface -v
python tools/validate_protocol72.py
python tools/validate_protocol72.py --ready
```

8 项新增测试实际编译并执行 JDK 解析器，覆盖注释/字符串排除、Unicode/原始 UTF-16/CRLF、record/switch、lambda/匿名类/字段作用域、间接构造与直接参数区别、重载歧义、人工出现次数漂移、源码/语法错误以及禁止晋级完整性。248 项研究回归全部通过；没有运行 Java 项目完整测试，历史 217 项失败保持未解决。

完整报告与摘要分别连续生成两次，逐字节一致。报告 SHA-256 为 `64194613a1e53d45369a2b06d3deffcd74bed4d69b280fe3f3b8be6a9b92ccdc`，摘要为 `8920466702325db2ba27837ab9d7588e2432049b858a3694e7f0350940055bf3`。研究映射校验退出 0；ready 退出 1，原 213 项缺口保留。

本机证据位于忽略的 `local/migration72/`：`evidence/login-source-surface{,-repeat,-determinism}.json`、`evidence/login-source-summary-repeat.json`、`evidence/login-source-mapping-{check,ready}.json`、`logs/login-source-surface-{run,repeat,focused-tests,regression}.log`。完整报告含全部未关联位置，共享 `protocol/cn-7.2/login-source-observations.json` 保留计数、方法、候选边和边界，不含机器绝对路径或运行数据。

## 2026-11-06 收尾复核

固定源码重新解析及8项聚焦测试通过；共享摘要逐字节不变，研究校验通过，ready仍拒绝213缺口。用户恢复限定离线后续工作，新增验收卡已先写入Project；A-03整体仍待完整依赖闭包和实机复核。工作区AGENTS及resources既有差异保留。未改运行代码/Proto/配置，无部署或数据库操作。
