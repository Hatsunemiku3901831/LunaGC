# 国服 7.2 上游成果调研（UP-72-20261114）

## 结论与状态

本轮发现的最直接新候选是 [capybr2222/LunaGC_7.2.0](https://github.com/capybr2222/LunaGC_7.2.0/tree/acc7a0b48f44f3d51666c91f1125e8e1ee923ca4)：它提供基于 LunaGC 的 7.2 登录、角色、库存和场景发送路径，可为 D02-R1/R4 的依赖闭包提供逐调用点对照。它**不是可直接替换的国服协议**：本轮实际读到的 `PlayerLoginReq` 字段与本项目固定目标证据冲突，包号表含大量负占位，场景发送器还对值做变换，目标有效性均须单独证明。

已有 [AstaPS 固定协议审计](CLIENT_72_ASTAPS_AUDIT.md)仍是最有用的已验来源对照，不重复算作新验证。本轮另发现其 Fork 的 [quest-content-fixes-7.2.0](https://github.com/962666231a-max/AstaPS/tree/2ed264ae4594c1c566297b63828b041ccbe4dbcc) 非默认分支：可为任务条件和场景/库存事件提供后续 E-02 对照；[开放 PR #244](https://github.com/MeChen618/AstaPS/pull/244) 另有任务技能处理，但当前项目已有技能事件发送，PR 的宽泛匹配规则缺独立语义依据。两者都不是登录入场的已验修复。

最值得先做的是 **UP-72-N1：离线逐层对照 LunaGC 7.2 与既有固定国服证据**，先聚焦 LoginReq、AvatarData、PlayerStore、EnterScene 和 SceneInitFinish 的消息、嵌套和调用顺序，输出冲突表与独立 wire 断言设计，保持未知，不修改运行代码。资源侧先核对同源 ID 和消费者；本轮对 303 的一个对照已发现 LunaGC 资源与现有本地数据的 16 个子任务行完全相同，不能把它算作新增目标资源。

研究覆盖、限制和继续位置见[检索账本](CLIENT_72_UPSTREAM_LEDGER_20261114.md)；每个已纳入检查的分支、完整 tip、根 tree、README blob 和去重见[分支表](CLIENT_72_UPSTREAM_BRANCHES_20261114.md)；关键文件 blob、SHA-256 和固定提交链接见[来源清单](CLIENT_72_UPSTREAM_SOURCES_20261114.md)。**本批为阶段结果，尚未完成全部搜索拆分和全部候选深审，不宣称穷尽全网。**

## 计划、项目需求与已有证据

本次由当前智能体独立执行，用户明确禁止派子智能体；因此没有执行 AGENTS 中建议的跨模型派工。先查当前 checkout、现有研究及差异，再按 README→分支/Fork/PR→固定源码→本地对照推进。只研究公开材料；仓外 clone 使用 blob 过滤、无工作树检出、保留所有远端分支，不递归取子模块/LFS，不运行上游脚本、构建或安装。初始主仓库 HEAD 为 `2cfddb655665d7eb8da90904761f8aa9fe5b217d`；AGENTS、patch、resources 的既有差异保留，不归本批。

| 需求 / 任务 | 已有证据，按限定范围复用 | 剩余缺口 | 检索词示例 |
| --- | --- | --- | --- |
| SDK、dispatch、认证和连接 D02-R2 | 已有真实 HTTP 往返、部分 key 桥接与调用点的局部证据，见 [Project](../Project.md) | 自然 SDK 初始化、route、认证、完整校验和实机链路 | `Genshin hk4e dispatch SDK region session authentication login` |
| 角色、库存、队伍、实体、场景 D02-R1/R4 | 部分目标原生结构、Java 投影、场景握手与既有调用图；AstaPS 对照已存在 | 完整 Avatar/PlayerData/Store、Scene/Team/Entity 与实际消费顺序 | `AvatarDataNotify PlayerStore PlayerData EnterScene SceneInitFinish team entity` |
| 必要资源及保存 D02-R3 | 局部出生资源/装备和 303 的真实加载、保存与重绑已验，见 Project | 完整行、ID 引用、目标 revision、配置、ability 和全路径恢复 | `ExcelBin BinOutput ProudSkill resource parser ability Lua save persistence` |
| 协议三层与独立证据 | 目标固定输入、部分三层与 wire 已有记录；AstaPS 的高编号、缺号和语义冲突已审 | 仍有大量身份、语义、字段及递归嵌套未验；不能用同形晋级 | `protobuf proto opcode CmdID packet descriptor protocol` |
| 后续玩法 E-02/E-03/E-04 | 已有业务框架及历史上游研究；本地免费充值、普通商城和活动账本分属不同路径 | 7.2 资源、协议及真实效果；不将全部玩法塞入 D-02 | `combat quest shop abyss battlepass Miliastra UGC` |

官方国服公告将 7.2 称为[「坏灭肇始之时」](https://ys.mihoyo.com/main/news/detail/167205)，英文官方公告称 [Hour of Ruin](https://genshin.hoyoverse.com/en/news/detail/167187)；两个名称仅用于发现资料，不表示不同渠道协议相同。交叉搜索包括 Grasscutter/Grasscutters、LunaGC、AstaPS、HunkyMeow、Genshin/Genshin Impact、原神、YuanShen、hk4e、anime game、private server/server emulator/服务端/私服，以及上表能力词；搜索执行与空白范围以账本为准。

## 候选与谱系

| 来源 | 作者声明与已读取源码 | 用途和边界 | 许可及状态 |
| --- | --- | --- | --- |
| [capybr2222/LunaGC_7.2.0](https://github.com/capybr2222/LunaGC_7.2.0/tree/acc7a0b48f44f3d51666c91f1125e8e1ee923ca4) `7.2.0` | README 声明以 girluh 6.7 为基线、REL 7.2.0、WIP；更新提交称改了 proto/opcode、登录和场景。实际存在上述源码和 Proto，README 未提供目标国服输入哈希 | D02-R1/R4 调用图与字段差异候选；不可直接替换协议或声称兼容 | 根树无独立 LICENSE，README 要求致谢且引用其他来源；代码、依赖与资源授权待核。原 `capyb2222/LunaGC_7.1.0` 在本次 GitHub 查询返回 404，不能假定已迁移或可访问；新地址为搜索发现，不证明账号更名 |
| [MeChen618/AstaPS](https://github.com/MeChen618/AstaPS/tree/a49f51aa6f2787ffbc25cee46801b70dee860d23) `main` | README 自称 7.2 且明确尚有大量问题；PR #243 已合并。已有固定提交审计及限定融合，源输入及歧义见原专题 | P0 差异和旧业务候选；先复用已验，不把同一来源的 Fork 当独立样本 | 根 LICENSE 为 GPL-3.0；其他代码/依赖分别核对 |
| [962666231a-max/AstaPS](https://github.com/962666231a-max/AstaPS/tree/2ed264ae4594c1c566297b63828b041ccbe4dbcc) `quest-content-fixes-7.2.0` | 从 AstaPS `a49f51…` 分出；新增任务条件、库存/场景事件与数据补丁 | 独立任务后续分析；未验证目标行、事件频率和保存顺序，不运行数据补丁 | 根 LICENSE 同 AstaPS；数据来源与权属另核；未发现针对该分支的公开 PR，不等于不存在外部合并 |
| [capybr2222/LunaGC-Resources](https://github.com/capybr2222/LunaGC-Resources/tree/11d971bec4ab8fbe4598c6d8f63bbdfdb49b7010) `master` | README 声称 7.2、明确不完整，列多个来源；目录含 ExcelBinOutput、BinOutput 和 Scripts | 做有键资源比对和加载路径审计；单行相同不证明 revision 或目标解析 | 根树无独立 LICENSE；多来源数据权利未知，不建议直接复制 |
| [AstaPS-Resource](https://github.com/MeChen618/AstaPS-Resource/tree/6cfecfb55f889f8e3b7156adb12809608db4f74b) `main` | 既有[定向 D-02 对照](CLIENT_72_D02_UPSTREAM_REVIEW.md)已检查出生资源、303、scene3；`mechen/resources-7.2-runtime` 与 main 根 tree 相同 | 复用先前限定比较；不能凭发布说明证明国服来源或完整资源 | 根 LICENSE MIT；游戏数据及第三方内容权利另核 |
| [girluh/LunaGC](https://github.com/girluh/LunaGC/tree/c2e541dab83296a8292b1e4191031fcb33f8fbc5)、[Grasscutter](https://github.com/Grasscutters/Grasscutter/tree/f373827a83d3e688fd3c6afb1e1c3759ca1d61c9) | 均为当前谱系背景；已枚举分支、Fork、PR | 用于理解业务和共同来源；旧代码不能证明 7.2 | Grasscutter 根许可 AGPL-3.0；LunaGC 根项目许可须按该树具体文件另核 |
| [HunkyMeow](https://github.com/AzureXuanVerse/HunkyMeow/tree/029f0a63d1b9eb786825110fc4fd14debf83e70a) | 描述称 5.5，部分代码不公开、Proto 需自行提供 | 账号保存队列等旧业务参考；现有项目已有相关专题和保护，不能认作 7.2 协议 | 根 LICENSE AGPL-3.0；不作为完整 7.2 服务端 |

来源链接中的 Codeberg `CapyTeam/genshin-protocol` 和 `CapyTeam/animegamepatch` 在本次公开网页读取时返回 **restricted URL**；不将访问失败当作项目不存在，也未改用其他入口重试该地址。GitLab 的 [genshin-protocol](https://gitlab.com/CarolBicsi/genshin-protocol) 页面可见 6.x 资料但本轮未完成分支清单；[mavuika-rs](https://git.xeondev.com/mavuika-rs/mavuika-rs) 是公开 Rust 模拟器，README 的旧版本/模块架构只能作方法线索。Gitee 相关入口通过网页工具不可访问。`hk4e-go`、Irminsul、ProtoDumper、Miliastra 格式工具及公开论坛目前仅为发现线索，未纳入本批已完成分支深审，见账本；不把旧版数据或宣传当作目标证据。

[ProtoDumper](https://github.com/66hh/ProtoDumper) README 明确要求非混淆程序集，不提供对当前国服输入的适用证据。[Irminsul](https://github.com/konkers/irminsul) 是玩家数据导出工具，其公开 [7.1 问题 #163](https://github.com/konkers/irminsul/issues/163) 自述包处理未完成；本次只读说明与问题，没有运行、捕获或验证 7.2。两者均不能直接解除目标协议缺口。

## 分支、Fork 与 PR 的独有增量

| 来源 | 固定身份与关系 | 本轮结论 |
| --- | --- | --- |
| LunaGC 7.2 `7.1.0` → `7.2.0` | 共同祖先 `a0b32dba9d7d07721c160ea37ff3d2d02d2854ee`；7.2 tip `acc7a0b48f44f3d51666c91f1125e8e1ee923ca4`；根 tree `5964c9d0099effd55ea8a891cc2b3dc6d1fb0512` | Git diff 显示 2549 文件变化；数目不代表均为独有逻辑或已审。7.2 更新提交为 `dad1735e3356837d81378ed545ce3d68b6b6fb43` |
| [Neona-Xi `Hard-7.2.0`](https://github.com/Neona-Xi/LunaGC_7.2.0/tree/9d14f34ab4e57cdf3241761ad224d041405871c8) | head `9d14f34ab4e57cdf3241761ad224d041405871c8`；共同祖先为上游 `acc7a0…` | 相对上游仅一个提交、只修改 README；其困难玩法声明没有对应源码增量，不能计为独立实现 |
| zhujiangheng64-alt/LunaGC_7.2.0 | 默认 tip `4062d6353dc6c9d2fa546a5e18ee78850f3446f8` | 它是上游 tip 的较早祖先，落后两个提交，不是独立 7.2 证据 |
| [AstaPS PR #243](https://github.com/MeChen618/AstaPS/pull/243) | head `14cc19881f29842d7277a518054d7f38f84bb23c`，原分支 `update-7.2-test`，已合并到 `a49f51aa6f2787ffbc25cee46801b70dee860d23` | 公开描述明确引用旧版 dump 和 NT；不能把 PR、main 和 Fork 计成独立来源 |
| [AstaPS PR #244](https://github.com/MeChen618/AstaPS/pull/244) | head 仓库 `962666231a-max/AstaPS`、分支 `feat/quest-skill-fixes`、commit `930aadd0e8cade73380f54b54fa775fd924cebf8`；开放、未合并到所查 main | 作者声称在 7.2 国服推进了序章相关任务；本轮只读源码。差异为语言别名、技能事件和宽匹配，未执行其测试、配置或客户端。本项目已有 `ActionAvatarSkillStart` 的技能事件，不能重复触发或直接接受宽匹配 |
| [AstaPS PR #241](https://github.com/MeChen618/AstaPS/pull/241) | head `Ruiwang66/AstaPS:fix/stygian-onslaught` / `f3c5dfd639c2bb3b388015dd1bc35d81cc8e9140`；开放草稿 | 作者描述为 7.1 幽境危战初始流程，作者称做过部分测试但该隔离补丁没有客户端验证。归 E-02 后续线索，不是 7.2 协议依据 |
| AstaPS-Resource [PR #21](https://github.com/MeChen618/AstaPS-Resource/pull/21) | head `mechen/resources-7.2-runtime` / `9f1ae21ea6b4610fca4fde8484ae4316cb3860b1`；已合并 | 该 tip 与 main `6cfecf…` 的根 tree 同为 `3cf83488a94b47a018934e5f91c1bf7e93357aae`，不是独立增量 |
| [AstaPS-Resource `update-quest-excel`](https://github.com/MeChen618/AstaPS-Resource/tree/9fe14a67667e3bca4db026d9c98e452342e2b989) | 相对 main 的共同祖先，main 独有 79 个提交、本支独有 1 个；本支相对共同祖先只改 `ExcelBinOutput/QuestExcelConfigData.json` | 主题为把 BinOutput 子任务并入 Excel；它不是 7.2 同源证明，也未对全部行或本地加载做比较 |

另有 `zornicza/AstaPS:development` 和 `fix/protocol-generated-out-of-src` 等不同源码树；前者 tip 主题涉及静态数据后端，非默认分支已固定并读 README，但未做完整实现或目标资源证明。`HertaA11/AstaPS` 有多个历史功能分支，部分 tip 对应 AstaPS 已合并 PR；同树相同者按分支表去重，其余仅作初筛，不能因 README 相同推断源码相同。详细列表和差异提交数见分支表。

## 具体静态对照与验证边界

### 协议、登录、初始化

1. **CmdID→类型**：LunaGC 7.2 源码中的 Token、Login、Ping、EnterScene、AvatarData、PlayerStore 和出生响应几个示例编号，与 AstaPS 对应常量相同；对固定表作一次静态数字提取，4084 个整数常量中 2356 正数、1 个零、1727 负数。数字一致只是对照，README 的 kitkat/NT 致谢与 AstaPS 已知来源存在关联，尚未证实这些是互相独立的提取；未核对所有正数的目标类型身份。
2. **类型→语义**：上游 `PacketAvatarDataNotify`、`PacketPlayerStoreNotify` 枚举角色/队伍和库存；`HandlerSceneInitFinishReq` 发送世界、队伍、实体等一系列通知。它们可供逐调用点比较和识别遗漏，但顺序不是已验证的国服必需闭包。上游 `HandlerPlayerLoginReq.handle` 接收 payload 而未解析它，并有可配置的默认旅行者 fallback；这些行为不能替代本项目真实认证、显式选人或目标客户端测试。
3. **字段→wire**：上游 `PlayerLoginReq` 将 `token` 写为 tag 3、`system_version` 为 50001；当前本地[固定目标合同](../src/main/proto/PlayerLoginReq.proto)分别为 tag 1、2。这是具体冲突，不接受高编号为目标事实。上游 `PlayerEnterSceneNotify` 的部分低 tag 与本地固定 raw 外形相近，但还有 20000+ 迁移字段，且其业务名、嵌套结构、数值变换和省略/存在性没有因此验证。上游 `AvatarDataNotify` 的多个字段号与当前本地 Proto 不同；本地该文件也不能仅凭存在就视为已验目标 schema。`PlayerStoreNotify` 外层列表/枚举/整数形状相近，但本地 tag 8 明确保持未知，不能按上游名当作容量语义。

后续对比必须按固定目标输入分别证明三层，覆盖非零、64 位 GUID/时间、嵌套、列表、错误、边界和存在性，并用独立样本或独立 wire 断言；自编自解、同号、同形、编译通过均不够。现有账号、经济、保存回归继续保留。完整目标客户端验收仍是 D02-R6 的六项，不因本报告变化。

### 资源与任务

实际读取 LunaGC 资源 `BinOutput/Quest/303.json` 与本 checkout 的对应文件：两者 JSON 整体不相等、顶层键集合也不同；按 `subId` 对齐后集合相同，16/16 子任务对象相等，包括 30302。字节 SHA 不同；这是本次纯离线对象比较，不证明其他表、目标客户端 revision 或所有消费者相等。与 AstaPS-Resource 固定文件的 61 条子任务及既有目标候选 63 条须分别保留来源，不能拼接成目标真值。

新的 AstaPS Fork 任务分支中，`ContentItemNumGreater` 用实际库存数量与阈值比较；`HandlerPostEnterSceneReq` 排队指定场景事件；`QuestManager` 排队离开场景范围事件，`ContentLeaveSceneRange` 从 guide 字符串推导中心和半径。后者源码注释称其数据支持，但“guide 点就是区域中心”仍是作者推断，须以固定资源和实际消费者独立核对；不得运行其 `tools/data-patch-7.2.0` 或直接改资源。本项目已有 303 局部正向恢复，研究新增条件前先确认它们是否真属入场依赖。

### 成本与后续验证

| 项 | 价值 / 相对成本 | 必要验证与停止条件 |
| --- | --- | --- |
| LunaGC 7.2 登录、Avatar/Store、Scene 逐字段对照 | 高优先；只读比较成本中等，直接融合成本高 | 固定目标输入与参考 blob；按三层逐字段、递归、非零/64 位/错误断言。来源冲突、语义未证或依赖 A/B 冻结边时停止对应项 |
| 303/出生资源的文件与 ID 比较 | 中；已做一个具体对照，继续按真实加载路径筛选 | 版本来源、字段语义、ID 引用、实际 Java 加载和目标消费者均需证据；遇到未知 revision 不覆盖现有资源 |
| 任务条件分支 | 中低；E-02，先静态审计再定是否移植 | 正反阈值、不同场景/距离、重复事件、存档和重登；guide 中心及许可未明前不导入 |
| 历史业务、深渊/商城/纪行/UGC | 后续按单项需要比较；旧代码仅业务参考 | 保留普通商城、活动币与熄星账本及现有账号经济保存回归；独立验证 7.2 协议和资源 |

## 后续最小任务

| ID | 固定输入 | 产物与验收 | 停止条件 |
| --- | --- | --- | --- |
| UP-72-N1 | 本报告两份固定服务端树、现有 `protocol/cn-7.2` 映射与目标输入哈希、上述五个 P0 消息 | 离线冲突/相同来源表、字段/递归依赖及独立 wire 用例；不改三层状态，保持已有账号经济保存回归 | 缺固定输入、原生证据不足或触及冻结的 A/B 动作时，对相应字段标未知并停止 |
| UP-72-N2 | 两份固定资源的 303、真实 `ResourceLoader`/Quest 消费路径与已有 303 证据 | 按 ID、字段和来源审计必要差异；测试正反/缺键、嵌套和版本，保持原资源不变 | 资源权属、目标 revision 或语义未明时不复制、不建立 alias |
| UP-72-N3 | 本账本中的未完成搜索分片、Fork 及其 README | 按官方分页规则补齐并固定新增候选分支/PR；更新去重表 | 返回限制或权限错误保留页码和原错误；不得把错误记为零结果 |

已有安全审查拒绝项 A-SECOND-RETURN-CONTRACT 及其依赖、B 组合 loader 继续冻结；本轮不改变原停止点，不自动派 Daybreak。全部新结论只属公开资料及离线静态范围：没有启动服务、数据库或客户端，没有运行上游构建/安装，也没有本轮目标客户端实机验证。
