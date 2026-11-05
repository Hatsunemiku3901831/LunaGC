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
