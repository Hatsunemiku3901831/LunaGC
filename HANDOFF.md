# 简略交接

## 2026-11-05：完成当前子任务后按用户要求暂停

当前研发提交`c7556a0`，分支`7.2-dev`。A-04d5k/B-02c8已完成：认证回调中的账号ID、账号token、渠道、账号类型、guest五字段，经原生会话写入、真实seed构造和Token writer验证；四个登录消息累计35字段有业务来源。9组原生样本、160项研究回归通过，重复产物一致，原Token5组重构回归不变；研究校验退出0，ready仍退出1。

**研发已暂停，D-02尚未完成/验收。未经用户恢复，不开始下一项。** 本批无运行代码/Proto/补丁/配置/资源改动或重启。AGENTS.md及resources既有修改已保留，未提交；未推送。

详细状态、证据/复现入口、缺口、弯路和下一步请读[HANDOFF2.md](HANDOFF2.md)，再读[Project](Project.md)、[Harness](Harness.md)及[会话研究](docs/CLIENT_72_SESSION.md)。本次原HANDOFF2.md不存在，首次创建，无旧文件可备份；下次覆盖前须先备份。恢复后优先追踪回调尾段/session+B0→Token tag965，或封包体/Handler后段，再按B-03/C-01/D-01/E-01前置推进D-02实机验收。

## 2026-11-05：用户已恢复推进，认证尾段/B0/分区解析前缀新增验收

用户已恢复原目标，并强调Project.md按任务列表逐项维护。当前没有暂停指令；D-02仍未完成/未验收。新增A-04d5l（46d422c）、A-04d5m（5dcf882）、A-04d5n1（c9f6870）按限定范围已完成，A-04d5n/B-02c9及父任务仍进行中。最新169项研究回归、6组分区前缀原生样本与重复产物通过；业务字段仍35，tag965语义未知，ready仍退出1。

Project已补齐提交号和验收/下一步（74ac0f0、54dfac1）。下一项是151211BA1之后到B0的连续处理、第二协程及业务语义；未改运行文件或部署。AGENTS/resources既有修改未提交，未推送。

详见[HANDOFF2.md](HANDOFF2.md)、[Project](Project.md)、[Harness](Harness.md)、[会话研究](docs/CLIENT_72_SESSION.md)。本次覆盖前已将原HANDOFF2备份至忽略目录`local/migration72/handoffs/HANDOFF2.before-dispatch-prefix-c9f6870.md`并核对SHA-256一致。旧暂停段落仅为历史，不需要用户再次授权恢复。


## 2026-11-05：A-04d5n2完成，继续配置函数内部

本地提交`c34ab42`：分区JSON/原生密码学连续到Region嵌套读取及151204AC0配置入口。8原生样本、173研究回归，trace/摘要二次一致；研究校验0、ready1。A-04d5n/B-02c9仍进行中；D-02未完成，无客户端验收，目标active且未暂停。

下一步151204AC0配置内部→151211DD5→B0；{}探索当前遇到容器初始化缺口，详见[HANDOFF2.md](HANDOFF2.md)。本次覆盖前原HANDOFF2已备份至忽略目录`local/migration72/handoffs/HANDOFF2.before-dispatch-post-c34ab42.md`并核对哈希。未改运行内容、不重启；AGENTS/resources既有修改保留未提交，未推送。


## 2026-11-05：A-04d5n3完成，继续SDK更新至B0

研究提交`c158e0e`：两次配置原生消费/事件返回和Region全局写入，终点1512124B9。6原生样本、177回归、重复trace/摘要一致，研究校验0/ready1。下一项GLOBAL+F2E0前置与SDK函数15275F080，随后才能继续B0；tag965语义未知，D-02未完成，目标active且未暂停。

[HANDOFF2.md](HANDOFF2.md)含SDK类型绑定探索和返回寄存器避坑。覆盖前原详细交接已备份local/migration72/handoffs/HANDOFF2.before-dispatch-config-c158e0e.md并核对哈希。无运行部署，AGENTS/resources差异保留未提交，未推送。


## 2026-11-05：A-04d5n4完成，第一分区入口到B0/Token原始链闭合

研究提交`4200ea6`：同次分区JSON/密码学/配置/SDK包装/提示→B0原指针，再复用seed运行时到Token tag965。6原生样本、182回归、两次产物一致；原Token5组产物不变，研究校验0/ready1。第一入口原始关联已确认，字段业务名、第二协程和D-02仍未完成。

下一项23192.MoveNext151226CD0的恢复状态及真实解析源；[HANDOFF2.md](HANDOFF2.md)含状态jump table和模型边界。原详细交接已备份local/migration72/handoffs/HANDOFF2.before-dispatch-session-4200ea6.md并核对哈希。目标active、无暂停，无运行部署；AGENTS/resources既有差异未提交，未推送。


## 2026-11-05：A-04d5n5完成，继续必要登录字段

研究提交`2a71ddf`：第二协程真实state2/3恢复入口→密码学/reader→第二B0 store→Token tag965。8原生样本、187回归、两次产物一致，研究校验0/ready1；两条分区原始关联均已验证，业务名/完整HTTP调度/实机仍未验。

下一步优先核查Token准入必需字段（key_id尚未确认）、Handler后段/封包体；tag965继续未知。两类公钥不能混同，详见[HANDOFF2.md](HANDOFF2.md)。原详细交接备份local/migration72/handoffs/HANDOFF2.before-dispatch-coroutine-2a71ddf.md已核对哈希。目标active、无暂停，无运行部署；AGENTS/resources既有差异保留未提交，未推送。

## 2026-11-05：A-04d5o/B-02c10完成，继续Handler尾段

研究提交`b89d855`：原生tag1184、命名dispatch key_id参数及密钥选择对照确认key_id；3请求/6响应，192项研究回归，两次产物一致。研究校验0/ready1（213项），四登录消息36业务字段，tag965仍未知，D-02未完成。

Handler尾段已单次探索到1531D03D0，实际执行response tag3 bytes→robot setter→GLOBAL+11C18；尚未正式验收或命名。下一步版本来源/CalcVersionHash，再继续Handler或封包体，见[HANDOFF2.md](HANDOFF2.md)。原详细交接已备份local/migration72/handoffs/HANDOFF2.before-key-selection-b89d855.md并核对哈希。目标active、无暂停；无运行部署，AGENTS/resources差异保留未提交，未推送。

## 2026-11-05：A-04d5p完成，Handler→Login连接已有单组探索

研究提交`e1a0194`：连续Handler入口→robot输入setter→版本拼接→原生ASCII/SHA1/Base64返回；7原生样本、198研究回归、两次产物一致，研究校验0/ready1（213项）。更正编码为ASCII；两响应字段仍未知，D-02未完成，无运行部署。

下一项q已单次连续跑到封包入口14A447E00，token/hash原指针进入Login，阶段[9,6]。仍待正式化与边界回归；WorldType.Login=0/Level=2、PackageType.Full=5/PlayGo=3不可混淆，详见[HANDOFF2.md](HANDOFF2.md)。原详细交接已备份local/migration72/handoffs/HANDOFF2.before-handler-tail-e1a0194.md并核对哈希。目标active；AGENTS/resources差异保留未提交，未推送。

## 2026-11-05：A-04d5q完成，按用户要求暂停

当前子任务A-04d5q已正式完成并提交`dc41c99`：连续Token Handler→真实Login/Full状态/Action→Login构造→发送封包入口。9原生样本、204研究回归、二次产物一致；8组已安装密钥与Java逐字节相等，原默认枚举输出不变。研究校验0/ready1（213项）。没有运行部署或实机验收，D-02仍未完成，未知字段继续未知。

用户要求本项完成后先暂停再交接，目标工具已返回**paused**，研发已停止；用户明确恢复前不启动下一项。[HANDOFF2.md](HANDOFF2.md)记录完整现状、完成/未完成、边界、弯路和恢复计划。覆盖前旧详细交接已备份local/migration72/handoffs/HANDOFF2.before-pause-dc41c99.md，SHA256一致，核验报告在local/migration72/evidence/handoff-pause-backup-check.json。

恢复首选14A447E00封包体/密钥使用或P0依赖闭包；当前停在嵌套发送函数中，不能宣称整个Handler返回。AGENTS/resources既有差异保留未提交，patch/resources引用不变，全部提交仅本地未推送。


## 2026-11-05：已恢复，独立封/收帧验收完成

用户明确恢复推进至D-02，目标active，没有暂停。A-04d5r（ae23ee7）完成独立原生封包/XOR，A-04d5s（b698f75）完成独立原生收帧/包头/错误边界；分别15/18原生样本，两次产物一致，最新216研究回归通过，研究校验0/ready1。factory/负载与key来源仍明确夹具，尚未串联真实消息/seed/Handler，不代表D-02或客户端通过。

下一项真实payload/密钥串联或P0闭包，详见[HANDOFF2.md](HANDOFF2.md)。BitConverter.cctor漏初始化造成的探索假长度错误已修正；不要把模型问题当客户端故障。覆盖详细交接前已备份至忽略目录local/migration72/handoffs/HANDOFF2.before-frame-b698f75.md并核对哈希。无运行文件修改/部署；本轮Java及客户端未运行，只有本工作区MongoDB。AGENTS/resources原差异保留未提交，未推送。


## 2026-11-05：Token完整帧及动态启动基线，继续active

`389fe4a`完成A-04d5t，6原生样本、222研究回归、重复产物一致。用户允许动态调试并手动完成提权/Automate加载，随后明确禁止computer-use；以后只用脚本API。D-01b1真实启动5处代码及RNG状态交叉已验，目标当前暂停1411267B0；会话详情在忽略dynamic报告，先重新核对，不重启新客户端。

Ec2b单组探索已验证原生初始化后输出与仓库dispatchKey相同，下一步正式化并串联分区来源；D-02未完成，目标active。详见[HANDOFF2](HANDOFF2.md)与[动态调试](docs/CLIENT_72_DYNAMIC_DEBUG.md)。覆盖前已备份旧HANDOFF2至local/migration72/handoffs/HANDOFF2.before-token-frame-389fe4a.md并核对哈希。无运行源码/配置/补丁改动或部署，AGENTS/resources差异保留，未推送。

## 2026-11-06：分区密钥到Token帧完成，继续active

A-04d5u已提交0153d00；A-04d5v完成第一分区9块RSA→三次Ec2b/两配置XOR→最终key原指针→真实Token完整帧。4原生样本、234研究回归、二次产物一致，旧三工具各6样本产物不变，校验0/ready1（213项）。调度器/SDK/实机不在本项范围，D-02未完成，目标active。

真实调试已将首次异常交给应用处理，当前停mhypbase.dll TLS，Global仍0；勿沿用旧注册入口暂停点，只用Automate，禁止computer-use。下一项真实SDK/HTTP入口、Login/响应串联或P0闭包。详见[HANDOFF2](HANDOFF2.md)。旧详细交接已备份local/migration72/handoffs/HANDOFF2.before-dispatch-key-0153d00.md并核对hash。无运行文件改动或部署，AGENTS/resources他人差异保留，未推送。

## 2026-11-06：真实缓存ABI与退出复现，继续离线Login

A-04d5v已提交2523513。D-01b2完成两次真实UTF-8缓存同对象/3槽匹配及4处代码核对；SDK相关全局已出现但key槽仍空，完整SDK/登录未验。**最后目标已退出，原管理员调试器保留**；最新active-session中的target也已结束，勿沿用任何旧暂停点。

erun是全部首次异常传递，已改con 1+run；本机hardware watch表删除后寄存器残留已实证并清理本研究观察点。清理后运行更远，恢复软件观察仍80000003退出，原因unknown，不跳过校验。下一项离线Handler→Login实际size/writer/完整帧。研究回归仍234，D-02未完成，目标active。详细恢复/避坑见[HANDOFF2](HANDOFF2.md)；覆盖前备份local/migration72/handoffs/HANDOFF2.before-startup-runtime-2523513.md且hash一致。无运行部署，他人差异保留，未推送。

## 2026-11-06：Handler到Login完整帧完成，继续P0闭包

A-04d5w已限定验收：10原生样本、240研究回归、二次产物一致；4成功完整帧含4560字节Unicode/NUL跨key周期，6非发送分支；全负载protoc一致，9组安装key与Java一致，原Token6组产物不变。未知语义不晋级，未执行整个Handler返回/真实响应收帧/实机，D-02未完成，目标active。

下一项A-03调用闭包或响应收帧。D-01b2提交cf09603；当前无运行游戏目标，调试退出原因仍unknown。详见[HANDOFF2](HANDOFF2.md)；覆盖前旧文已备份local/migration72/handoffs/HANDOFF2.before-login-frame-cf09603.md并核验。无运行部署，他人差异保留，未推送。


## 2026-11-06 A-03a完成后暂停交接

按用户最新要求，完成当前A-03a后已暂停研发（目标paused），不开始下一子任务。研发提交00e0bee：方法级登录/场景源码索引，12源码/9根/80方法，13人工边与83候选边，1012未关联调用保留；61直接构造参数类型名/62总构造名不是7.2完整或最小消息集。8新增/248研究回归、二次产物一致，研究校验0/ready1（213缺口）；Java历史217失败未消除。

详细状态、前期6db47fe的Login完整帧证据、真实调试退出状态、弯路、复现和下一步见[HANDOFF2.md](HANDOFF2.md)。覆盖前已备份并验证旧HANDOFF2，备份local/migration72/handoffs/HANDOFF2.before-login-source-6db47fe.md，报告evidence/handoff-login-source-backup-check.json，SHA256 e9b0812da232ecadd20d842ea1b17167b9569146472d78be4c33e288613c2f75。

本批仅离线工具/测试/摘要/文档，无部署、数据库变更或新增实机验收。交接复查Java/游戏均未运行，HTTP/游戏端口无监听，MongoDB及调试器保留；不沿用历史PID/暂停地址。禁止computer-use仍有效。AGENTS.md及resources既有差异不提交，不推送。Project、Harness、README、协议盘点、登录源码说明和账号专题已同步。D-02未完成；恢复后下一项是按A-03a的未关联位置继续Player.onLogin管理器/事件/脚本调用及Proto依赖，不自动恢复执行。


## 2026-11-06：A-03c限定验收提交后停止，交接Astra

按本次限定顺序完成A-03a收尾（801834d）、V-01b家具真实保存夹具（03f2a99/b53fda2）、V-01c加工手册夹具（8dd87a3）、A-03b管理器/事件源码复核（a250354）、A-03c候选消息/Proto与三层缺口矩阵（411d522），现已停止研发。221项Java经济/保存回归及260项研究回归通过；ready仍拒绝213缺口，完整Java集未重跑，历史217失败不据此扣减。

A-03b固定48源码/293方法，2868未关联和开放插件/脚本/异步边界保留；A-03c记录97包构造/148引用种子/1139描述符文件，缺DoSetPlayerBornDataNotify.proto如实保留。未知三层不晋级，A-03整体仍待完整闭包及实机复核。未开展B-03/C-01/D-02或实机调试，无运行文件变更/部署。独立测试Mongo27027已正常关闭，原数据库/运行数据/他人修改保留，未推送。

Astra下一研究为现有B-02下DoSetPlayerBornDataNotify/SetPlayerBornDataReq/Rsp三件组的独立7.2三层证据；如拆子ID须先建验收卡，尚未启动。详见[HANDOFF2.md](HANDOFF2.md)和[缺口矩阵说明](docs/CLIENT_72_LOGIN_PROTO_MATRIX.md)。覆盖前旧HANDOFF2已备份`local/migration72/handoffs/HANDOFF2.before-login-proto-411d522.md`并逐字节/hash核验，SHA256 ae0144399f616f4a6264e94712770990addaa156ae3c1942ec781f25110169cf，报告evidence/handoff-login-proto-backup-check.json。本文仅追加，旧active/暂停段落均为历史，以本次停止点为准。

## 2026-11-06 B-02d选人三件组闭合后停止

先建子卡提交3fc7e80，限定研究验收提交36eb486，均未推送。目标三层：DoSetPlayerBornDataNotify=5008/PMNMDAIKALO（目标原生确认空schema）；SetPlayerBornDataReq=8541/JNJOMANHPCD（nick_name4、avatar_id11）；SetPlayerBornDataRsp=414/EODFLMDBPOG（retcode15）。39组限定原生样本、独立wire/protoc、6新增/266研究回归通过，二次trace/摘要逐字节相同，校验0/ready1（204缺口）。其他unknown、P0及实机状态不晋级。

已达到“Sol能按明确规格实现回归即停止”的停止点，未进入B-03/C-01/D-02，没有运行集成/部署/客户端或动态调试。昵称validator仅到入口，未替换成功；wire错误模型、UI/SDK/完整成功继续等未验范围详见[研究与Sol规格](docs/CLIENT_72_BORN.md)。正式后续实现须用户另给Sol范围。

原HANDOFF2先备份到local/migration72/handoffs/HANDOFF2.before-born-36eb486.md并核对字节/hash，SHA-256 7ba3b0d7d41c0423b489e878eeb0dec65ecde648b41fbd5dd5812e4f5bb664c9；详细状态/提交/测试/阻塞/避坑/复现与下一项见[HANDOFF2.md](HANDOFF2.md)。Java/游戏未运行，Mongo27017及两个既有调试器未操作；AGENTS/resources他人差异保留未提交。本HANDOFF仅追加，旧内容保留。

## 2026-11-06 B-03a/C-01a限定实现完成，按取证停止条件交接

本次指令已覆盖前批停止点并完成可独立的实现、回归和构建，现已停止新增开发/取证。建卡d2cfb14，B-03a生产生成/独立wire c6bf035，V-01d路径失败建卡87cda4c/修复9fe3bb2，C-01a业务/保存及源码矩阵8bd338f；均仅本地、未推送。三件组固定5008/8541/414及nick_name4/avatar_id11/retcode15，删除配置包号/超时/默认角色兜底，错误真实编码、重复幂等、确认保存后继续，半成品冷读拒绝误登录。

最终495项Java（含19业务、15wire、原33准入及221经济保存选集）和272研究回归通过，失败/错误/跳过均0；ready仍拒绝204缺口，完整Java集未重跑。便携build退出0，JAR10类与编译产物/代码提交8bd338f匹配；完整成功继续/P0/SDK/补丁/资源前置未齐，未部署或启动服务/游戏。无Java/游戏与HTTP/游戏/测试监听；独立测试Mongo正常关闭，原Mongo/存档/运行数据/他人修改保留。onLogin/欢迎仅继续入口观察点，不是实机或实际邮件落库验收；父B-03/C-01仍进行中，D-02未验。

下一项B-02e为Astra最小取证：固定目标Rsp0→14CD68860完整继续与新旧号首批必需初始化/场景序列，新增必需消息逐层证据，不猜号/绕检查。范围、复现、模型/保存边界、测试/构建证据、未完成与避坑详见[HANDOFF2.md](HANDOFF2.md)及[CLIENT_72_BORN](docs/CLIENT_72_BORN.md)；本批未派发/执行该研究。

覆盖HANDOFF2前已先备份至local/migration72/handoffs/HANDOFF2.before-born-implementation-8bd338f.md，字节/hash核验一致，SHA-256 8254eae519d5610454c670e00c4b6d49e1f1c0f2836ec95d12002769c1dfc086；报告evidence/handoff-born-implementation-backup-check.json。本文件只追加，历史停止/active记录保留，以本段和详细交接为当前状态。

2026-11-06持续工作检查点：已按约定先逐字节备份原HANDOFF2至忽略local/migration72/handoffs并核对SHA-256，再覆盖详版。目标继续至D-02，未暂停/完成。d2969b5整合列表24样本、六类15字段非空嵌套与owner UID正常迁移；366研究通过，当前JAR2566f466…83fe（69c3672）、63输入、ready228。实际SDK/入场未验、服务未部署，原数据及他人差异保留。cb74432已委派B-02e7消费者及E-01c5真实生产Lua加载路径；当前边界、证据、未验构造/EOF、避坑与下一步详见[HANDOFF2](HANDOFF2.md)。本条仅追加接续信息，不表示停止持续任务。


## 2026-11-06 当前两个子任务完成后按用户要求暂停

B-02e7限定原生部分消费51f7cd2、E-01c5实际生产Lua加载审计9aa8ca7均已完成并停止，整合4d78fa3，未推送。最终384研究回归0失败/错误/跳过；两项正式产物各自双跑字节一致，原失败保留。65输入仅新增两摘要hash，三层/原63输入/源码/候选/开放边界不变，ready仍228。真实fast437组部分求值/481错误，inline13,616组零错误；未知helper/25866后缀、完整SDK/资源与D-02实机仍未完成。根配置true、JAR2566f466…83fe未改，未部署，原数据/调试对象/他人差异保留。

已先备份原HANDOFF2至`local/migration72/handoffs/HANDOFF2-before-scene-pause-20261106-105909.md`，字节/hash核验一致，SHA-256 `306e9c0c41e14db7c7bf83aa831c6bf04a801de1bd0367bcf2c7b732e93e52cf`；证据scene-pause-handoff-backup.json。随后覆盖详版，本文件只追加。当前状态、完成/未完成、阻塞、弯路/避坑、复现证据及恢复后计划见[HANDOFF2.md](HANDOFF2.md)。**本次明确暂停，用户恢复前不启动下一任务；D-02没有完成。**


## 2026-11-06：AstaPS限定融合验收后按最新要求暂停

最新用户范围为“先进行融合和维护文档，融合完成通过验收后暂停”。F-01固定来源/协议及实现差异（45e22ce）、F-02a四schema45字段/3包号（4324cd7）已完成限定验收；73dd759为计划提交，均未推送。399研究、43协议及关联、独立Mongo29套589账号/经济/保存/Lua分别全过；生成/结构通过、ready仍228。portable构建23秒，JAR a8c2d893…53a8f8内嵌4324cd7，163个Guard/Player/脚本class保持；完整P0/SDK/补丁/资源前置不齐，未部署/新实机，D-02仍未验。

现按要求暂停，不开始下一项。F-02b响应/完整Login和F-03场景语义仍阻塞；Asta仅候选，不采用0/旧号/20000+或7.1选人启发式。原数据、调试目标进程树、AGENTS/resources差异及子模块引用保持。详细完成/缺口/避坑/下一步/复现见[HANDOFF2](HANDOFF2.md)。覆盖前旧HANDOFF2备份到`local/migration72/handoffs/HANDOFF2-before-astaps-fusion-c9aafa6-20261106.md`，SHA-256 4e5740c0…bff2，逐字节/hash核验；本文件原字节前缀保持。当前两个scene离线审计CLI仍固定旧JAR，复现用已校验归档，不刷新历史守卫。


## 2026-11-06 F-02b/F-03限定融合验收后暂停

运行接入4bbf9fd，本批native分项def036e/a5fe305/d0cddb0/fbe99d5，未推送。468研究/43套761 Java/五生成检查通过，portable构建24秒；JAR 570d64f4f1680ab023c7eda7f2e930ebf5eba79791030e8499100ca8f2799bf5。74证据/1267源、150根/矩阵，结构0/ready205；完整初始化/P0/SDK/场景落地和D-02未验。原H2已先备份/hash核验再覆盖，详见[HANDOFF2](HANDOFF2.md)。前置不足未部署，无项目服务或测试库监听；原客户端目标当前未见、原因unknown，本轮未操作调试器，不按旧PID直接恢复。AGENTS/resources原差异、配置/子模块/原数据保护。按用户要求暂停，恢复后从完整初始化/Notify未知目的地和连续keyselector继续。

## 2026-11-06：持续D-02中途检查点，未暂停

用户已恢复持续推进，旧暂停不再生效。F-02c（75bafc7）、F-03d1a（0cd95ef）、d2a1（a81f84b）、d2a2（eec23d2）、F-04a/b（540a077/6b72887）及E-01c6（b74cd16/8a91d41）限定验收完成。80模块496研究实际全过，32 Lua/8实际加载通过；统一review74→79，仅加5摘要且原三层/1267源/Proto/开放边界不变，ready仍205。新a2尚待下一次统一review/研究复跑，不计入496。

当前portable JAR7ca18e10…e8f5f（b74cd16），整场两轮fast437→407错误组、inline仍0；未部署/未新增实机，D-02未完成。F-03d1b具名链、d2a3组合图及F-04c正式保留验签补丁正在三子代理推进；不得误还原patch在制差异。AGENTS原改动由外部f4354ef提交，resources原差异保留。只用脚本/API，API无target，不沿旧PID。

详见[HANDOFF2](HANDOFF2.md)的在途归属/证据/失败/后续。覆盖前旧详细交接已备份至local/migration72/handoffs/HANDOFF2.before-resume-checkpoint-2ae4ca3.md并逐字节/hash一致，证明evidence/resume-d02-handoff-checkpoint-backup.json；本简交接仅追加。此为中途检查点，持续active，不是新的暂停指令。


2026-11-06 活动检查点（未暂停）：持续D-02授权有效。PlayerData有限接入9a9853e、独立库存raw适配器b47a490及库存/Avatar/Marshal研究分项提交；600研究、778隔离Mongo Java、库存36 wire Java分别通过。当前JAR5db5d3b2…28fd73未部署，完整P0/SDK/资源/实机未齐。88输入统一review已a2f56ce双跑，d3d新摘要8a03338待下一次受审；三层结构0/ready208。旧HANDOFF2已逐字节备份核hash后覆盖，详见HANDOFF2.md及local证据post-adapter-handoff-backup.json。当前继续Weapon/Equip、53824拆卡及独立调试通道复现；不要沿旧PID或把原生存储模型当实机通过。
