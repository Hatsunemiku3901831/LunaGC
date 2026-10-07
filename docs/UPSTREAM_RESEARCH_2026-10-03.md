# 外部实现调研：2026-10-03

## 范围与结论

对照基线：本项目 `1b363fac36d218ecfce3452a3c1893fe0fac6b31`，以及当前使用的本地 7.1 资源。
本轮检查公开仓库、提交差异、实际实现和非公开服务的公开说明。下文的「本地缺口」来自静态代码与资源核对；客户端行为仍需专项验证。

收获最明确的是 **7.1 特殊能量与夜魂动作、联机战斗消息序号、卡池标题字段和个人场景入口协议**。
千星奇域还找到了存档格式和节点编辑器资料。本轮未找到已经验证适用于本项目客户端的空月祝福 SDK 加载修复，也未找到能直接补齐全部历史活动的实现。

## 来源与适用范围

| 来源 | 本轮核对的版本 | 可参考内容 | 适用边界 |
| --- | --- | --- | --- |
| [AstaPS](https://github.com/MeChen618/AstaPS/tree/e28d5c8ebb21fca27e1fe7f8969eb1b179d0d403) | `e28d5c8ebb`，2026-10-03；声明支持 7.1 | 特殊能量、夜魂动作、联机序号、数据库任务池、千星纪行报文 | 千星纪行共用普通纪行状态；仍保留按名称注入动态技能的逻辑 |
| [LunaGC_7.1.0](https://github.com/capyb2222/LunaGC_7.1.0/tree/811b224db150982be37c0619e1506d980443cb9b) | `811b224db1`，2026-10-01 | 卡池标题字段、个人场景跳转 | 协议候选需核对本地客户端；上游提交不等于本项目实机验证 |
| [HunkyMeow](https://github.com/AzureXuanVerse/HunkyMeow/tree/029f0a63d1b9eb786825110fc4fd14debf83e70a) | `029f0a63d1`，2025-04-05；源码目标 5.5 | 按账户、物品、场景组拆分数据库队列；普通纪行触发结构 | 项目说明部分代码不公开，协议需自行提供；公开商城仍是普通 Shop/ShopChest 加载 |
| [genshin-miliastra-file-format](https://github.com/script-1024/genshin-miliastra-file-format/tree/48ffc9c79ca0b62039a89686c579482b55dff14b) | `48ffc9c79c`，2025-12-12 | GIA/GIL/GIP/GIR 容器结构及部分 protobuf 定义 | 文档部分依据 6.1；文件格式研究尚不能补齐大厅、任务结算和网络服务 |
| [WebMiliastraNodesEditor](https://github.com/Columbina-Dev/WebMiliastraNodesEditor) | 本轮读取公开 README | 服务端/客户端节点图编辑、JSON/GIA 导出、教程入口 | 作者说明部分节点定义根据截图推断，需逐个核对实际语义 |
| [genshin-db](https://github.com/theBowja/genshin-db/tree/49a6544a6c6ae36089cb42fa591fc46f01de8bcf) | `49a6544a6c`，2026-09-21，7.1 数据 | 多语言名称、技能说明、角色资料 | 可用于名称与说明交叉核对；不能替代资源数值或客户端协议 |
| [mavuika-rs](https://git.xeondev.com/mavuika-rs/mavuika-rs) | 公开页面最后提交为 2024-11-29，支持 CNCBWin5.2.51 | 分离 SDK/dispatch/gate/game 服务，跨版本协议转换和二进制资源解析方向 | 技术栈为 Rust/Bevy/PostgreSQL；本轮只核对公开架构说明，未验证解析器或 7.1 适配 |
| [YuukiPS 的 GC/GIO 服务](https://ps-new.yuuki.me/downloads/genshin-impact) | 页面列出 GC 6.6、GIO 3.2 | 非公开引擎方向的版本与公开功能说明 | 运营方说明自己负责托管；没有取得可核验的完整 7.1 引擎源码 |

日期使用源码提交时间；网页更新时间、仓库活跃时间不能代表代码已经升级。
源码移植应逐项保留来源及适用的许可说明：AstaPS 为 GPL-3.0，HunkyMeow 为 AGPL-3.0，节点编辑器为 GPL-3.0，genshin-db 为 MIT。格式仓库的许可证需另外核实。

## 1. 特殊能量与夜魂：已确认本地数据映射缺口

[AstaPS 的专项提交](https://github.com/MeChen618/AstaPS/commit/0c00f4d07c1ebbbf2b1aec4876f5851e846d86d4) 新增了资源字段别名与夜魂动作处理。
本地 `AvatarSkillData` 只识别旧字段 `specialEnergyMin/Max` 和旧混淆名称，当前资源使用 `specialEnergyCostMax/Start`：

| 技能 ID | 当前资源中的字段值 | 本地状态 |
| --- | --- | --- |
| 11065 | `specialEnergyCostMax=200`、`specialEnergyCostStart=100` | 新字段未映射 |
| 11145 | `specialEnergyCostMax=100`、`specialEnergyCostStart=50` | 新字段未映射 |
| 11147 | `specialEnergyCostMax=100` | 新字段未映射 |

本地 `EntityAvatar.GetEnergyProp()` 根据解析后的特殊能量字段是否大于零选择能量类型，因此这些字段漏读会影响能量类型判断。
这与玛薇卡、丝柯克专项提交的修复方向一致；是否同时影响全部技能表现需要客户端验证。

只统计当前资源的 `ConfigAbility_Avatar_Mavuika.json` 与 `ConfigAbility_Avatar_SkirkNew.json`，还发现以下 **43 处配置节点**：

| 资源动作/混入类型 | 节点数量 | 本地缺口 |
| --- | ---: | --- |
| `ReviveSpecialEnergy` | 10 | 动作枚举缺少此名称映射及对应动作处理 |
| `AddNyxValue` | 18 | 未映射到 `NyxAdd`，未注册该动作处理器 |
| `SetNyxValue` | 8 | 未映射到 `NyxSet`，未注册该动作处理器 |
| `ChangeNyxValueMixin` | 7 | 未映射到 `NyxCostMixin`，未注册该混入处理器 |

建议适配顺序：资源字段与动作名称 → 动态表达式求值 → 能量增减与上下界 → 属性和全局值同步。
验收应覆盖积累、施放消耗、切人、重登、联机和普通元素能量角色。不能仅加枚举别名就标记角色已修复。

## 2. 联机战斗广播：协议已有序号，发送端没有填写

[AstaPS 联机提交](https://github.com/MeChen618/AstaPS/commit/bbe0fe4ee3) 为 `CombatInvocationsNotify` 添加递增的 `client_sequence_id`，治疗广播也使用同一序号来源。
上游解释这是避免客户端丢弃移动消息的处理；这一客户端行为尚未在本项目复现验证。

本地协议已经定义 `client_sequence_id = 12`，但 `PacketCombatInvocationsNotify` 两个构造方法都只填 `invoke_list`，序号恒为默认零。
这使它成为优先验证的联机修复候选。应覆盖普通战斗广播、治疗等所有直接包装该消息的位置，并验证双人移动、攻击、重连及消息顺序。
上游使用进程级计数；本项目实现前需决定计数范围，处理并发发送与回绕，避免只复制一个全局计数器。

## 3. 卡池标题和个人场景跳转：上游有新协议候选

本地与上游的定义差异如下：

| 字段/包号 | 本地当前定义 | 上游新定义 | 来源 |
| --- | --- | --- | --- |
| `GachaInfo.title_textmap` | 7 | 2032 | [卡池标题提交](https://github.com/capyb2222/LunaGC_7.1.0/commit/5f750b1b90b4235f71708a6d8053080fdcff6189) |
| `GachaInfo.gacha_record_url` | 600 | 7 | 同上 |
| `GachaInfo.gacha_record_url_oversea` | 2032 | 600 | 同上 |
| `PersonalSceneJumpReq` 包号 | -7，未绑定有效包号 | 13 | [至冬宫殿入口提交](https://github.com/capyb2222/LunaGC_7.1.0/commit/811b224db150982be37c0619e1506d980443cb9b) |
| `PersonalSceneJumpReq.point_id` | 5 | 11 | 同上 |

入口提交还加入传送朝向 `tranRot` 和个人场景的进入原因/方式。下一步应核对本地 7.1 客户端消息描述器和处理函数，再修改 `.proto` 并重新生成；随后检查卡池标题、记录链接和入口实际行为。

AstaPS 的 [传送点通知修复](https://github.com/MeChen618/AstaPS/commit/e28d5c8ebb21fca27e1fe7f8969eb1b179d0d403) 也值得参考。
不过本项目普通解锁通知已经仅发送 `scene_id + point_list`，这部分没有相同的重复锁定写入。
本地 `ScenePointUnlockNotify` 的 `unhide/locked` 仍有 50000/50001 占位字段；完善锁定/取消隐藏功能时应重新核验，不能直接采用上游生成类的字段名称。

## 4. 数据库与世界更新：有可复用的设计，但要保留本地持久化语义

[HunkyMeow 的数据库实现](https://github.com/AzureXuanVerse/HunkyMeow/blob/029f0a63d1b9eb786825110fc4fd14debf83e70a/src/main/java/emu/grasscutter/database/DatabaseHelper.java) 与
[AstaPS 的数据库实现](https://github.com/MeChen618/AstaPS/blob/e28d5c8ebb21fca27e1fe7f8969eb1b179d0d403/src/main/java/emu/grasscutter/database/DatabaseHelper.java)
都拆分任务池，使用有界队列和队列满时由提交方执行的策略。AstaPS 另有任务池观测。

本项目目前使用一个六线程、无界队列的异步数据库任务池，负载积压可以持续占用内存。
可借鉴队列上限、压力指标和关闭时等待落盘的设计；应保留当前同步经济写入、物品删除检查和重试逻辑。
验证重点是同一玩家/物品的写入顺序、队列满时延迟、保存异常和服务停止时的未完成任务。

AstaPS 的 [家园登录死锁提交](https://github.com/MeChen618/AstaPS/commit/e1912db0bc) 调整了 home-world 缓存与 world 集合的锁顺序。
本项目当前 tick 没有上游该版本的家园缓存删除分支，尚不能据此断言存在相同死锁；以后补缓存回收时应一并检查锁顺序。

## 5. 千星奇域：报文展示、存档工具与完整玩法需要分别验收

[AstaPS 的 BeyondBattlePassWireEncoder](https://github.com/MeChen618/AstaPS/blob/e28d5c8ebb21fca27e1fe7f8969eb1b179d0d403/src/main/java/emu/grasscutter/game/battlepass/BeyondBattlePassWireEncoder.java)
接收普通 `BattlePassManager`，直接复用其等级、经验、付费标记与任务；排期固定为 6700，商品 ID 固定为 10201/10203。
可以作为报文结构参考，完整千星纪行仍需独立排期、玩家状态、任务触发、奖励与领取记录。

[文件格式说明](https://github.com/script-1024/genshin-miliastra-file-format/blob/48ffc9c79ca0b62039a89686c579482b55dff14b/docs/en/Overview.md)
给出了项目、关卡、素材、运行时文件的共同容器结构。可先做文件头校验与样本解析，再扩展导入。
[节点编辑器说明](https://github.com/Columbina-Dev/WebMiliastraNodesEditor#readme) 提供节点图编辑和导出方向。
后续服务端仍需执行节点、保存玩法状态、结算及发放奖励；工具能编辑或导出文件，不代表网络玩法已可运行。

## 6. 名称和技能说明交叉核对

[genshin-db 的中文薇斯纳条目](https://github.com/theBowja/genshin-db/blob/49a6544a6c6ae36089cb42fa591fc46f01de8bcf/src/data/ChineseSimplified/characters/vesna.json)
已包含角色 ID `10000143`、中文名称与基础资料，可用于核对 GM 搜索和名称缺失。
[中文纳西妲技能条目](https://github.com/theBowja/genshin-db/blob/49a6544a6c6ae36089cb42fa591fc46f01de8bcf/src/data/ChineseSimplified/talents/nahida.json)
可以帮助列出点按/长按、标记、关联伤害等行为验收项目；文字说明本身不能解决技能状态机故障。

## 移植时需检查的已知差异

AstaPS 的 [ResourceLoader](https://github.com/MeChen618/AstaPS/blob/e28d5c8ebb21fca27e1fe7f8969eb1b179d0d403/src/main/java/emu/grasscutter/data/ResourceLoader.java)
仍按角色名前缀合并动态技能，虽然过滤了名字含 `Quest` 的条目，仍不能排除 `BossRush` 活动技能。
本项目刚修复的纳西妲故障与活动技能误入基础目录有关，移植其他改进时应保留基础技能目录与条件授予技能的边界。

本轮没有在 AstaPS/HunkyMeow 的完整仓库树中发现可直接使用的 `.proto` 源文件；两者的协议来源/构建说明也需要外部材料。
不能据此把它们当作完整新协议包。GitLab 协议源页面本轮无法取得可核验文件清单，未对其内容完整性作结论。

## 后续适配清单

| 顺序 | 工作 | 完成依据 |
| --- | --- | --- |
| 1 | 特殊能量字段、夜魂与特殊能量动作 | 真实资源解析和动作结算测试；玛薇卡/丝柯克及普通角色实机回归 |
| 2 | 联机战斗消息序号 | 各广播构造路径覆盖；两名玩家移动、治疗、战斗、重连测试 |
| 3 | 卡池标题与个人场景跳转协议 | 本地客户端描述器核验、生成代码、真实入口和卡池界面验证 |
| 4 | 有界保存队列及压力观测 | 保持经济与物品写入顺序；积压、异常和停机落盘测试 |
| 5 | 独立千星纪行及 UGC 文件解析 | 排期/进度独立，奖励可领取且不重复；真实存档样本往返验证 |

本次新增调研文档，未移植外部运行代码，也未替换游戏补丁或资源。资料缓存与核对脚本位于被忽略的 `local/upstream-research/2026-10-03/`。
