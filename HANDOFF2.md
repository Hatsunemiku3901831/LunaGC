# 国服7.2详细交接：当前子任务完成，研发已暂停

更新：2026-11-05。用户最新指令是“完成当前子任务之后先暂停，然后按照要求完成交接”。A-04d5q已完成并本地提交`dc41c99`，随后目标工具已返回**paused**。现在只做交接收尾，**用户明确恢复前不启动新研究、开发、测试、部署或客户端验收**。整体目标仍为完成D-02并实机验收；D-02尚未完成，不能缩小目标或标记complete。

## 当前状态

- 正式研究提交：`dc41c99`（A-04d5q连续Handler→Login→发送封包入口）；前项`e1a0194`为原生robot输入/ASCII-SHA1版本哈希，`b89d855`为key_id交叉证据。
- A-04d5q已按限定范围验收；A-04d/B-02整体与A-04d5n/B-02c9仍进行中，B-03/C-01、D-02尚未满足启动/实机前提。任务详情以Project为准。
- 四个登录消息36业务字段、另2个PacketHead字段；默认枚举表8类102符号，本项另核查WorldType/PackageType两类20符号。响应tag1/3/658与Token请求tag965仍unknown，没有从新数据流硬套旧字段名。
- 研究校验通过；ready校验仍失败，213项未满足。无国服7.2真实登录/选人/入场验收。已有Java全量集217失败未消除，不能用204项Python研究回归覆盖该事实。
- 本批只改离线研究工具、摘要、测试及文档；没有修改运行Java、主Proto、配置、补丁或资源，没有启动服务/客户端或访问数据库，不需部署重启。

## 本项已完成内容

正式工具`tools/trace_client72_handler_login.py`；测试`tools/test_client72_handler_login.py`；共享摘要`protocol/cn-7.2/handler-login-observations.json`。复用前项HandlerTail72/VersionHash72、Constructor72、SendFlow72，不修改这些已验运行模型的实现。

同一个CPU执行从1507BE0E0经warmup/原生RSA/SHA/验签/seed安装、UID/token保存、robot输入与原生版本哈希、实际WorldType.Login/PackageType.Full分支、原生Action尾调用、1507C0BB0/1507C21B0、151213730完整Login构造、151214D20队列/transport/getter23172，停止14A447E00封包体入口。真实阶段[9,6]；没有重置CPU/寄存器衔接或回填token/hash到请求。

三成功样本核对：

- response tag6/+68→session+98→request+D8/tag1保持原指针；原生GLOBAL+11B90版本hash→request+60/tag1996保持原指针；实际版本拼接进入tag9。
- frame-builder输入保留原request指针、CmdID23172、header sequence及sent_ms；发送前后request对象逐字节不变。
- wire独立断言包含非零、UInt32全1、64位sent_ms=0x200000044、高数字token文本、空token/空版本、零包头。零值按原生writer省略，不硬造空字段。
- 本项固定非零client/server seed，8组安装结果均与同一独立Java MT64参考的4096字节逐字节相等；seed的其他边界由既有专题测试覆盖。

九组实际样本及边界：

| 样本 | 真实终点 | Login构造/发送 |
| --- | --- | --- |
| login-full | 14A447E00 | 是 |
| high-fields | 14A447E00 | 是 |
| empty-token-version | 14A447E00 | 是 |
| clock-equal | 1507B9A50延迟处理入口 | 否 |
| clock-below | 1507B9A50延迟处理入口 | 否 |
| missing-game-manager | 15A4E1B99空对象错误边界 | 否 |
| missing-gate-state | 1507C0CBD空对象错误边界 | 否 |
| medium-package | 14BB767E3下载分支入口 | 否 |
| invalid-response-signature | 15A4E0645验签拒绝 | 否 |

五个状态/时钟/包条件样本已安装密钥后才停止，不能说它们都拒绝了密码学；仅坏签名没有安装密钥。延迟重试、下载UI、错误抛出之后的处理都未执行。停止点仍在Handler的嵌套发送函数里，**整个Handler尚未返回**；封包体、加密和socket也未执行。

## 实际测试与证据

204项研究回归通过，新增6项聚焦测试；两次9组原生trace/摘要逐字节一致。默认enum提取器增加可选indices以复用完整校验，原默认8枚举输出重跑后与旧产物逐字节相同。未知语义/只执行前缀/真实SDK未验等边界在摘要与测试中保留。

本机忽略目录：

- evidence/handler-login-{trace,trace-repeat,summary-repeat,determinism,mapping-check,mapping-ready}.json
- logs/handler-login-{run,repeat,focused-tests,regression}.log
- evidence/enum-after-handler-login.json、enum-after-handler-login-check.json；logs/enum-after-handler-login.log

以上路径都相对local/migration72/。trace SHA256=`32c50e4236e5fd501272687c17e5a1655491c1a42a51e6e12ae63219bd84eb69`；summary SHA256=`f71bcf6effa80d014aaee8bfc9a29d5b7ab22ccb5873cad3f05299a5a3a1c457`；默认enum SHA256=`e7c421a860a03046afff3f0470ea4f3b3837b496eecb46b8dcfb10f44d03b2db`。

恢复后复现（当前暂停期间不要执行）：

```powershell
python tools/trace_client72_handler_login.py --client $env:GAME_PATH --java-home $env:JAVA_HOME --output local/migration72/evidence/handler-login-trace.json --summary-output protocol/cn-7.2/handler-login-observations.json
python -m unittest tools.test_client72_handler_login
python tools/validate_protocol72.py
python tools/validate_protocol72.py --ready
```

全研究回归选集为tools/test_client72*.py，加test_extract_client72、test_validate_protocol72、test_audit_resource72、test_inventory_protocol、test_review_protocol72_surface，共204。正式工具替代早先analysis/probe_handler_login.py单组探索，不再以探索日志作验收依据。

## 声明证据与模型边界

1. GameManager50103.Instance raw0x400B6D0→实例+20类型59570→+18 WorldType11326。默认表确认Login=0、Level=2；不能用Level或null world绕过普通登录分支。
2. GlobalVars.packageType raw0x2001904/14586CF14是PackageType11334，Full=5、Medium=2、PlayGo=3。不是操作系统平台编号。本机真实启动的package值未测，Full为明确离线输入。
3. MethodInfo cell145A00A40来自usage749199/kind3/method467563，owner56238，目标1507C0BB0；14BB76A31真实Action尾调用核对目标、receiver、MethodInfo。类型usage698995/702787/708398分别为Action142、82313、闭包63102。
4. 辅助对象+30缓存配置（+76=false）、GLOBAL+111B0 presence/+3B=false为显式输入。raw gate具体业务名仍未知，不把填充对象视作真实初始化成功。
5. engine selector7存储经141447780→141705750→1419D9F00实际读取，+A0 double经原生float转换与manager+208比较，时钟相等/小于分支均验证；不猜时钟业务名或声称真实计时器初始化。
6. SDK/设备/old Shell输入、旧warmup及传输选择仍为既有夹具。5714/3628是固定7.2客户端内部两条分支的输入，不是维护7.1运行环境。
7. 版本源六byref组件与exact二字符串format模型沿用p项；ASCII/SHA1/Base64原生执行。信任替换仍明确为私钥签名、服务器公钥及其签名、根PEM输入，验签结果未替换。
8. SendFlow72在实际首次进入151214D20时安装对象池/传输前提，状态明确为已连接AKFCDHJBCHD；并非实机SDK启动后选择的证明。

## 走过的弯路与避坑

- 原先凭形状把编码称为UTF8，p项已通过注册类型纠正为ASCIIEncoding420；两个非ASCII代理单元各变问号，NUL保留。不要改回UTF8或靠样例ASCII文本掩盖差异。
- Constructor72必须先于Handler安装，避免覆盖刚写出的GLOBAL+11B90。保留Ctor初始account+50给Handler会话；遗漏会在151213836走真实null边界151214817/int3。已修正夹具组合，没有绕过目标检查，也没有给该字段猜名。
- 两模型需共享NumberFormatInfo（ctor.provider=runtime.provider），否则原模型入口会因provider身份不一致拒绝。单一literal hook避免双重字符串分配；实际UTF16读取才能消费原生Concat/Handler产生的字符串。
- 移除Ctor对1531D02B0/GetUniversalGameVersion与159A8D050/双字符串Concat的旧模型，确保连续链中的版本拼接仍实际执行。
- 包类型、世界类型、命令号、配置ID是不同域；特别避免Login=2或Windows=3这种错误推断。仅getter23172是本项实际Login发送命令号。
- 请求seed使用服务器公钥，响应解密对应客户端4_Pub方向；不能混淆。本地信任夹具不等于D-01实际补丁已经提供信任。
- 只改Proto源再生成Java是后续B-03/C-01要求；当前不能把尚未确认的字段塞进运行协议，也不能用健康检查或编译代替D-02。

## 未完成、当前阻塞与解除条件

没有已确认的全局外部硬阻塞。本次停止原因是用户明确要求暂停，不是任务完成或无法继续。D-02仍缺：

- A-03/P0登录、选人、初始化、场景握手完整依赖闭包；其余业务名/tag/枚举/嵌套关联。
- B-03确定性正式协议生成、C-01双向接入及对应运行回归/构建部署。
- D-01真实补丁ABI/信任/路由/加载与SDK初始化、E-01资源revision/场景/Lua和完整最小资源。
- D-02新旧测试号、选人、初始化/场景握手、心跳、退出重登全部实测。
- 原Java217项失败逐项修复/归因与完整回归；账号/经济/保存不变量继续保留。

解除本次暂停只需用户明确恢复。恢复后首选14A447E00实际封包体及key使用/双向帧验证；也可推进依赖满足的P0闭包或D-01/E-01。若要证明整个Handler返回，还须继续嵌套发送返回后的1507C0D30/1507C1080及其他SDK/UI段，不得把当前封包入口误写成Handler完成。

## 工作区与部署约束

当前checkout仍为7.2-dev；本批未新建分支/工作树，全部提交仅本地未推送。既有AGENTS.md编码规范差异和resources/ScriptSceneData/flat.luas.scenes.full_globals.lua.json差异保留，不属本批、不提交。已核对patch HEAD f2874103ab70afc56260ee12d1eaca96247933b2，resources HEAD dac2a9d43332ef51879522cf39da2831b0ca0543，引用均未变。

下一次启动前重新核对当前.env/.env.local/config.json、GAME_PATH、LUNAGC_RUNTIME=portable、子模块/进程/端口归属，不沿用旧路径/PID。保留mongodata/和local/data/及隔离测试库；不清库、不强杀未知进程、不操作无关客户端。只有离线研究/测试/文档变化，本次不部署；恢复后若改运行内容，按AGENTS完成构建、便携部署、日志/健康/TCP/UDP/PID/JAR SHA核验。

## 本次交接流程

已先备份旧HANDOFF2到`local/migration72/handoffs/HANDOFF2.before-pause-dc41c99.md`，并核对SHA256相等；本机核验记录`local/migration72/evidence/handoff-pause-backup-check.json`。然后覆盖本详细交接，HANDOFF.md只追加简要暂停记录及指引。README/Project/Harness/CLIENT_72_SESSION与三个账号专题已同步本项正式范围。目标工具已返回paused；交接后不再执行研发。
