# 国服7.2详细接续记录

更新：2026-11-05。目标active，用户要求一直推进到D-02完成实机验收；没有暂停指令。D-02未完成，不得用离线研究通过替代实际客户端结果。

## 当前状态与已完成

本批起点c44009f，研究提交`4200ea6`完成A-04d5n4限定验收。第一个分区入口151210540在同次CPU执行中经过JSON、原生RSA/SHA/验签、实际30677/Region reader、两次配置、SDK更新包装15275F080、提示分支，到151212983。15121297C原生把解析对象+20指针写入session+B0；前三个成功样本继而复用同一seed运行时完成真实Token构造/getter24604/writer，独立wire AA 3C确认进入tag965。没有从后处理片段重开或强写会话值。

6原生样本、182研究回归通过，trace/摘要二次逐字节一致。原Token5组重跑报告与摘要和改动前逐字节相同。研究校验0、ready1。第一入口原始来源关联已确认；tag14/tag965业务名仍unknown，业务字段仍35。第二协程未验，A-04d5n/B-02c9及父任务仍进行中；B-03/C-01、D-01、E-01完整前置和D-02实机仍未完成。

工作区既有AGENTS.md与resources/ScriptSceneData/flat.luas.scenes.full_globals.lua.json保留未提交。patch f2874103ab70afc56260ee12d1eaca96247933b2，resources dac2a9d43332ef51879522cf39da2831b0ca0543均未变。仅离线工具/测试/摘要/文档修改，不涉及运行Java/主Proto/配置/补丁/资源，不需部署重启。未操作数据库或客户端，后续启动须重新按portable核对配置/进程/端口。

## 工具与实际证据

- `tools/trace_client72_dispatch_session.py`：新连续链正式工具。
- `tools/client72_sdk_update.py`：MiHoYo.SDK JSONObject/StringBuilder/字典池和存储、装箱/字符串桥接、OS数据源夹具；SDK业务函数真实执行。
- `tools/client72_dispatch_notice.py`：Object[]/String/System.Object元数据、主线程1夹具；原生类型检查和提示分支执行。
- `construct_native_token`新增可选seed复用参数；原调用路径不变。复用防止重复映射NativeCrypto的OS内存，两个精确RSA明文/padding观测点补足response工具的block追踪。
- 共享摘要protocol/cn-7.2/dispatch-session-observations.json。
- 本机证据local/migration72/evidence/dispatch-session-{trace,trace-repeat,summary-repeat,determinism,mapping-check,mapping-ready}.json；logs/dispatch-session-{run,repeat,regression}.log。
- 原Token回归evidence/token-complete-after-dispatch-session.json、-summary.json、-check.json；logs/token-complete-after-dispatch-session.log。

6组：高数字字符串9007199254740993、空源、不同源/语言、缺session、缺singleton、坏配置JSON。前三组真实store和Token；空值会话保留源指针而Token取默认空对象并省略tag965。缺session真实跳过store，缺singleton在151212D8E原生错误调用前停止，坏JSON由已声明helper拒绝，三者不构造Token。

SDK第一份JSON包含app_id/app_version/area/compile_type/use_device_name，第二份CPU/GPU/Language；这些值来源于明确虚构app/version/设备/channel/language输入。cn/os、None/Sc/It均有独立预期。原生字典非空枚举、清空和归还均执行。

## 必须保留的模型边界

- 密码学初始化标志与原生SDK导出表是独立声明输入；导出表14572BC18为0，原生140AC4160/140AC4180按自身条件返回。不能称实际SDK已接收或初始化成功。
- GLOBAL+F2E0为presence-only夹具，选定非IFix路径不解引用；不声称singleton初始化已恢复。
- 提示路径未靠添加feature flag跳过。usage698601实际为Object[]，不是String[]。原生assignability检查、GetCurrentThreadId=1/主线程1比较和1471623F0→147161FA0执行；日志sink缺省为0，实际按缺sink返回，不代表真实UI回调。
- 提示literal34435为`SDKServer - OnConnectRegionDispatch: GameBiz==Null`、code5000。这组合是离线研究夹具，不是可运行客户端初始化状态。
- 第一入口到B0闭合并不等于整个dispatch完成：151212983后尾段/真实HTTP/SDK回调/IFix/实机仍未验。

## 下一项可执行工作：第二协程来源

目前没有需要等待外部的整体硬阻塞。继续类型23192/NCIDAOJKFEI的MoveNext151226CD0，核实其实际解析对象到第二个B0 store151229560。

本批已核对元数据和真实jump table：

| state(+50) | 原生分支 |
| --- | --- |
| 0 | 151226DEA |
| 1 | 151226E77 |
| 2/3 | 151226D20 |
| 4 | 151226DCD |

实例+20是23177/DHABIHPMOAH接收者，+18是23181/FCLFPIMPBAF闭包，+38是Action<IENBACJFBGI>。闭包字段+10/+18/+20为string、+28为int64、+30为enum1787。状态2/3分支读取闭包+18/+20字符串及+30枚举，随后走保护解析；业务名未知，不应先命名为content/sign。可从明确“HTTP完成/恢复状态”输入开始追踪，但必须记录未执行初始网络等待，不能冒充整个协程调度完成。

进一步反汇编已留analysis/disasm-151226cd0.txt，既有dispatch-b0-coroutine.txt混入前一方法/大量填充，不要把其第一行当entry。该方法到15122A210前结束；+50范围外原生跳151229E5B。先真实执行恢复入口，看实际JSON/crypto/reader调用，不把第一入口证据自动扩展过去。

## 弯路与避坑

1. `SeedString72`不能在已有NativeCrypto的emulator中重复初始化，会UC_ERR_MAP。复用现有runtime，不重置会话/原生内存。
2. response runtime对140580000..14077FFFF按block采样，导致seed oracle最初plain=None（不是加密值错误）。新增1406595F0/140659AC0精确观测后通过。
3. 空tag14让Token选择默认空字符串对象，未必在Constructor72.strings表；按实际对象读取并先断言内容后登记观察，不写回期望值。
4. Object[]的klass+AC是相对type cache偏移；145BC8AD0还需间接读取base。误少一层间接会把rcx变8而非对象类地址。当前工具按真实检查路径建模。
5. SDK JSON与SimpleJSON不同；native string bridge初始化40字节描述符，借用存储的capacity=0；不能留旧栈数据触发不存在的释放。
6. 语言77368已有目标符号；原Constructor填充高位哨兵不是合法SDK语言，本批独立输入0/2/15，不将其当协议字段变更。
7. 所有未覆盖业务语义保持未知，不以编译/静态地址/工具通过代替D-02。

## 文档与交接

README、Project、Harness、CLIENT_72_SESSION及三个ACCOUNT_AND_SAVE专题已同步。旧HANDOFF2先备份到local/migration72/handoffs/HANDOFF2.before-dispatch-session-4200ea6.md并核对SHA-256一致，HANDOFF.md仅追加。研发未暂停，下一轮不需要再次申请授权。全部本地提交，未推送。
