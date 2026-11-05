# 7.2 网络调用和登录协议研究

2026-11-05；输入 SHA-256 见 [提取记录](CLIENT_72_EXTRACTION.md)。所有 VA 只对应该输入。以下区分静态代码证据、业务命名和客户端验收；没有执行目标客户端登录。

## 静态包号调用链

`NCKOJEOGIIC`（类型67861）的零参虚方法 `AMPEGOHOBJM` 是槽22，基类返回0。派生类型的严格 `mov ax,imm16; ret` 返回值现可确认是静态网络 command 值，依据如下：

| 环节 | 目标指令位置和作用 |
| --- | --- |
| 虚方法槽 | 0x14051E626–0x14051E667解码flags、slot、参数数。flags=`u16((method[10] XOR DC05)-key)`；slot=`u16(method[12]-key+58B5)`。 |
| 发送入口一 | AKFCDHJBCHD.CBENCLHEKEH，0x1472CE538读取消息，0x1472CE55D调用`[class+180]`，0x1472CE56C写入包对象`+22`的word。 |
| 发送入口二 | BHBBNHFEPPB.CBENCLHEKEH，0x147E04023同样调用`[class+180]`，0x147E04032写包对象`+22`。 |
| 封包 | FBNMCCIAMBP（22292）方法0x14A447E00：0x14A447EB9写4567前导，0x14A447EF2从`+22`读command，经0x1483D30D0转换后写下一word。转换函数0x1483D311F/312B依次写高/低字节，即网络序。 |
| 解包 | 0x14A446CC0检查至少12字节；0x14A446E14–E25验证前导，0x14A446E4D–E56读取下一word到`+22`；0x14A446FAE–FC1将其传入消息工厂0x151A853D0。 |

运行类的虚表**代码指针每槽8字节**，起点+D0，因此槽22在+180；MethodInfo数组另按vtableCount接续，不能套用代码/MethodInfo交错16字节的旧假设。此布局由目标调用指令验证，仅适用于本哈希。

工具 `extract_client72_commands.py` 重新读取三份固定输入、恢复结构、校验基类/父链/虚槽/非static/零参数与严格常量代码后输出 [commands.json](../protocol/cn-7.2/commands.json)。方法flags/slot测试使用4组实际目标记录字，不通过逆向编码器自编自解。

结果：4,012个类型，4,007个不同command值。1004、1017、1027、1028、2002各有两种类型，保留 `ambiguous-domain-or-alias`，未自动合并。整个表的业务名称、网络域及运行时热修复覆盖仍未核实；**不能直接生成服务端 PacketOpcodes**。静态确认类型绑定不等于确认业务映射。

## 登录候选与实际调用点

| 业务候选 | 目标类型 / 索引 | 静态 CmdID | 证据与状态 |
| --- | --- | --- | --- |
| GetPlayerTokenReq | NJAALDCMHEP / 82491 | 24604 | 目标OnPlayerTokenReq阶段、声明Clone自类型单元1458C5500/同类型CopyFrom、151220013分配与原生getter闭合；静态verified，完整发送/实机另验。 |
| GetPlayerTokenRsp | CLDBKDNJHFG / 37481 | 27808 | 已由目标自身OnGetPlayerTokenRsp标记、参数、注册/原生分派闭合；静态verified，见登录语义。 |
| PlayerLoginReq | KOHBOMDDFCB / 76410 | 23172 | 目标OnLoginReq阶段、声明Clone自类型单元1458C3DF0/同类型CopyFrom、1512137BE分配与原生getter闭合；静态verified，完整发送/实机另验。 |
| PlayerLoginRsp | NDENMDAIAMC / 40503 | 20835 | 已由目标自身OnPlayerLoginRsp标记、参数、注册/原生分派闭合；静态verified，见登录语义。 |
| PingReq | LMINJCCKNDD / 64405 | 26605 | 自类型单元1458C39F0；15120CAC0和15120D8A0构造，15121EC30及15120F4CB周期调用；结合下述响应回调/共享字段确认静态心跳请求角色，实机待验。 |
| PingRsp | MJGOAHFNCGC / 68111 | 25763 | 方法参数、注册25763、分派25763至15120DEA0与getter一致；与请求共享两个原始字段，静态确认为心跳响应。3个wire字段已恢复，业务字段名仍candidate。 |
| PacketHead | LOPNMMJPFAJ / 27182 | 无（embedded） | 包对象+18所持有类型，封包写入header区；直接结构与读写证据，非独立command。 |

旧7.1的名称/类型对应仅用于排列研究优先级，未作为7.2证据。候选结构比较报告、类型单元交叉引用、反汇编在 `local/migration72/analysis/`。元数据不会保留可直接信任的业务名称。

## 保护入口的线性分析边界

TokenReq.WriteTo（1500C2660→15A4DCF40）、TokenRsp.Merge（148B7EA10→15A4DBA53）、LoginReq.WriteTo（152EEB470→15A4E48B5）、LoginRsp.Merge（14B89EC90→15A4DC5A8）入口均跳转到保护代码；线性反汇编无法恢复有效读写流程。未把后续填充字节当作指令或猜测tag。解除需恢复保护分派后的真实数据流，或在独立客户端运行中取得受控样本/读写观测。

本批已继续完成未保护的PacketHead、PingReq静态wire（见下节）。下一步追踪业务构造/回调类型关系、嵌套泛型参数及登录P0依赖闭包。运行时IFix分支、保护入口和真实网络序列仍需验证。

后续A-04d已通过离线CPU模型恢复TokenReq的28个原始字段，并取得LoginReq局部输出和两个响应入口checkpoint，见 [保护入口研究](CLIENT_72_PROTECTED.md)。上段线性反汇编失败不再等于TokenReq完全无可执行研究路径；完整业务和运行时验收仍未完成。

## 包头和心跳 wire

`extract_client72_wire.py` 将人工复核的14个tag写入点，与紧接的字段加载、直接writer调用和目标元数据布局关联；拒绝未知指令、helper、字段缺失、重复tag、类型/偏移不一致。它只处理本节两个已审查的未保护函数，不是通用反汇编器。

输出 [wire-observations.json](../protocol/cn-7.2/wire-observations.json) 保留每字段VA、原始名字和三层状态；[research.proto](../protocol/cn-7.2/research.proto) 使用原始混淆名字，仅供离线研究，没有接入src/main/proto或Java。全部字段是singular/implicit、非packed；其序列化条件和size计算也已人工复查。多维数组、嵌套容器不在这两个消息内，不能据此宣称P0容器验证完成。

| 类型 | 对象偏移 | 原始字段 | tag / wire类型 | 业务状态 |
| --- | --- | --- | --- | --- |
| LOPNMMJPFAJ | 30 | MIBNHKIPDIM | 3 / uint32 | client_sequence_id静态确认：1472CE594递增且跳过0，1472CE5D0写入此字段 |
| 同上 | 18 | BCNJAEDGBCK | 6 / uint64 | sent_ms静态确认：1472CE5EF–68C取时间差乘0.0001后写入；14E697558–592构造1970-01-01并转本地epoch |
| 同上 | 20 | POAOKGCEJPF | 7 / uint32 | 未知 |
| 同上 | 28 | KNKMLHHGPPE | 8 / uint32 | 未知 |
| 同上 | 24 | BNHLJEGKGEM | 9 / uint32 | 未知 |
| 同上 | 2C | FKKOHHCLCPH | 10 / uint32 | 未知 |
| LMINJCCKNDD | 30 | LBEAJJBNBIF | 1 / uint64 | 构造处调用GetMemoryUsageForProcessKB后左移10；process_memory_bytes候选名称 |
| 同上 | 40 | BFNKMJBHCKJ | 2 / uint32 | _cur_fps候选，来自浮点统计值转整数；统计对象语义待闭合 |
| 同上 | 18 | IMEDCDCPICN | 3 / bytes | 业务未知；真实tag为3，不是旧schema的50000占位 |
| 同上 | 38 | MHCBMOHFBCK | 5 / double | total_tick_time候选，来自145873360；累加来源待追踪 |
| 同上 | 20 | BAHKGFLONLL | 9 / bytes | 业务未知；真实tag为9，不是旧schema的50001占位 |
| 同上 | 44 | HDLNEABIBJF | 10 / float | ue_time候选，来自原生时间对象；具体时钟语义待闭合 |
| 同上 | 2C | OILKPHNHCHB | 13 / uint32 | client_time候选，14E65CCD0从epoch计算秒数；时钟修正语义待闭合 |
| 同上 | 28 | AMLHAGPHMHN | 14 / uint32 | seq候选，15120D8A0的第一个业务参数；现见调用传0，不能据此确认为递增序号 |

偏移为十六进制。PacketHead的读函数14CD81610独立按18/30/38/40/48/50键分派至同一对象偏移，交叉确认全部6个字段。PingReq写函数15182BFE0已覆盖8个实例字段；其CalculateSize在15182BA30独立给出浮点固定宽度和其他字段的长度条件。

writer语义：1517662A0为32位varint、151766960为64位varint（7bit移位循环）；151765EF0按低字节起写8字节；151748C40存float位并写4字节；151767530写长度后经147CF1DC0复制字节容器（含池化分支），不是嵌套消息writer。原始指令报告留analysis/，提取使用标准库，不依赖研究时的Capstone安装。

本批实际测试：37项提取/结构/包号/wire/映射测试通过；`check_client72_wire_proto.py` 使用libprotoc 3.18.1编译研究Proto，9项独立wire断言通过。包括所有字段非零、超过32位与uint64最大值、二进制bytes、float/double、无符号越界、重复singular最后值、未知tag及截断拒绝。断言是由上述目标指令推导的**合成样本**，不是抓包，不是客户端运行结果；尚未覆盖登录错误返回、P0嵌套/列表或真实IFix路径。

业务映射记录仅将PacketHead类型及2个业务字段标为verified；5个业务消息名仍candidate，其他语义未知。校验器同时比对commands/wire原始观察，避免候选记录混入旧号/旧tag。普通校验通过；`--ready`实际退出1，保持生成闸门关闭。未修改服务端协议、配置、补丁或资源，因此本批无需重启部署。

## A-04d：PingRsp静态确认

上述“五个业务名仍candidate”为前一检查点。当前PingReq/PingRsp静态消息角色及命令绑定已提升，Token/Login四个业务名仍candidate。依据 [参数恢复](CLIENT_72_SIGNATURES.md)，80个三标量候选中只有MJGOAHFNCGC直接作为网络管理器DHABIHPMOAH回调15120DEA0的参数。

独立指令链：HBIJFOJIFAH在1507C2A99以word **25763** 注册，1507CC8F6比较同值并跳至1507CDA87；1507CDA9E读取包对象+10的消息，1507CDAA2调用上述强类型回调。getter15251F2E0也返回25763。回调15120DEB1标记网络状态，并撤销/清空待处理网络操作。与已确认的周期发送、消息中相同的AMLHAGPHMHN/OILKPHNHCHB字段共同确认心跳应答角色；没有仅靠旧版形状命名。

注意151FB1A30也出现数值25763，但传给14052D0F0作元数据初始化索引，是已排除的假线索。不要将任意相同字面量都计为网络证据。

| 原始字段 | 对象偏移 | tag | 目标类型 | 业务名称状态 |
| --- | --- | --- | --- | --- |
| AMLHAGPHMHN | +18 | 1 | uint32 | seq候选，和请求同名字段对应；回调不读取字段，消费语义仍需补证 |
| OILKPHNHCHB | +1C | 6 | uint32 | client_time候选，和请求同名字段对应 |
| LDLIEKOAFBP | +20 | 11 | int32 | retcode候选；签名/布局确认有符号，未观察回调使用该错误值 |

读函数15251F300按08/30/58分派，分别在15251F376/391/35A写入对应偏移；32位reader都是147520110，不能只凭同一个helper把int32误写为uint32。类型由元数据独立确认，CalculateSize在15251F0A7检查符号，负值于15251F0ED取10字节，排除sint32/zigzag。

原始观察和研究Proto现有3消息17字段，仍不进入服务端源集。本批44项工具/映射测试与11项独立wire检查通过，新增响应非零字段、负错误值10字节varint和signed上界测试；普通校验通过、ready保持失败。没有客户端运行验收。

## 当前登录业务检查点

最新检查点见[会话研究](CLIENT_72_SESSION.md)：四个登录身份/CmdID静态闭合，22字段来源、8枚举102符号。下段“当前只有两个请求配对仍candidate”为上一检查点，已由目标阶段命名证据取代；完整构造/发送与实机仍未完成。

[登录语义](CLIENT_72_LOGIN_SEMANTICS.md)已确认GetPlayerTokenRsp/27808、PlayerLoginRsp/20835以及20个字段来源。上文“Token/Login四个业务名仍candidate”是旧检查点；当前只有两个请求配对仍candidate。网络管理器151221D70/1512160D0只转发共同配置，上游Handler为1507BE0E0/1507B7BE0。7枚举86原始符号已有目标常量证据；其余业务字段和真实客户端序列仍待验收，不生成正式运行协议。


## A-04d5r 独立封包函数与整帧XOR

用户2026-11-05已恢复推进。`trace_client72_frame`从14A447E00入口到真实ret后的合成返回哨兵，执行包头CalculateSize/WriteTo及其varint writer、网络端序转换、MemoryStream.SetLength/Write/GetBuffer、整帧循环XOR和writer清理/归池。声明核对MemoryStream类型597槽29→159BCDA20、所选传输类型32016槽5→1472CE3B0，后者经实际接口getter返回+90池对象。

独立Python字节表达式与11组成功输出逐字节相等：`4567 | command:u16be | headerLength:u16be | bodyLength:u32be | header | body | 89AB`。无key/空key保留明文；否则从帧首第0字节开始，以`key[i % key.length]`异或整个帧（包含前后标记）。native selector145878FC0=0读取GLOBAL+19580，=1读取+19588；两槽放入不同显式数组，验证选择和4096字节回绕，不声称本项验证了真实启动密钥来源。所选槽为空时不会尝试另一槽。这是原生观察，不改变运行安全策略。

15组样本：零帧、7/8/9字节payload边界、命令32767/32768/65535、uint32/uint64上界包头、65536字节body验证32位长度、两槽4103字节body循环、所选key缺失/空数组，以及缺message/stream/header和只读stream。缺message/stream原生返回false；缺header停在14A448572错误分支入口；只读stream停159B46B50抛错helper，不模拟异常后行为。原生XOR实际循环次数等于整帧长度，密钥数组未改变；清理后writer buffer/position已清空。

明确模型边界：payload的CalculateSize/WriteTo是指定字节夹具，不是实际Token/Login writer；Array.Copy底层存储复制与对象池分配/归还为受检模型，容量预置，不覆盖stream扩容/非零origin。没有改动目标指令、替换包头序列化/端序/XOR结果；没有执行socket、实际客户端、收帧或与上项Handler/seed安装串联。数值命令边界不新增业务CmdID映射，tag965等业务名保持未知。

复现：

```powershell
$env:GAME_PATH = & ./tools/game_path.ps1
python tools/trace_client72_frame.py --client $env:GAME_PATH --output local/migration72/evidence/frame-trace.json --summary-output protocol/cn-7.2/frame-observations.json
python -m unittest tools.test_client72_frame
python tools/validate_protocol72.py
python tools/validate_protocol72.py --ready
```

共享摘要`protocol/cn-7.2/frame-observations.json`固定三输入SHA-256；15原生样本两次trace/摘要一致，210项研究回归通过。6项新增回归覆盖独立帧字节、32位长度和摘要错误/边界降级拒绝；研究校验0，ready仍1，业务字段不晋级。报告与日志见Harness。仅离线工具/测试/文档变更，无运行部署，D-02未完成。

下一步14A446CC0入口已有静态核对：同一selector选槽，原地XOR后读前导、command/两长度，再读包头和消息工厂；尚未原生执行。不能由发送函数通过推断接收错误/截断/尾标记行为已验。


## A-04d5s 独立收帧解密包头及错误边界

`trace_client72_frame_reader`从14A446CC0执行到ret或声明的原生抛错入口。复用Frame72池/存储模型，另明确factory151A853D0返回虚构消息、body Merge消费指定窗口，不能称真实消息工厂/反序列化执行。PacketHead的14CD81610及CodedInputStream tag、uint32/uint64、边界helper都直接执行，未替换为Python wire解析器。基类67861槽11→1495C9D60为空原生ret，配合1495AA480实际增加生命周期计数；这是虚构基类消息，不代表目标业务消息的子对象处理。

声明类型173 BitConverter的.cctor159B548E0真实写14586B610=1。早期探索未初始化此静态值，导致offset6的非对齐32位读按另一端序解释，出现假长度不足；正式工具每个样本先执行.cctor并记录原生trace。没有修改目标读取代码或手写解密结果。

18样本含命令0/32767/32768/65535、uint32/uint64上界、重复singular最后值、65536字节体、两槽4096字节key回绕、明确参数指定的明文分支、坏帧/截断/坏tag/错key及尾随字节。收帧原地XOR逐字节回到独立输入，次数匹配输入长度；包头值、body窗口、factory收到的命令、生命周期计数和消费长度都有断言。

| 输入 | 原生结果/边界 | body消费 |
| --- | --- | --- |
| 正常/高位/重复字段/尾随字节 | 返回1 | 是；窗口精确对应body |
| null byte[] | 返回4 | 否 |
| 不足12字节 | 返回5 | 否 |
| 前导错误/错key | 返回6 | 否 |
| 帧长度少于声明长度 | 返回2 | 否 |
| 尾标记错误 | 返回7 | 是；reader未在此路径归池 |
| header截断varint | 1509CB360原生异常构造入口 | 否 |
| header零tag | 1509CB150原生异常构造入口 | 否 |

尾标记在body reader之后校验；正常尾随三字节仍返回1，packetLength只包含首帧，XOR则覆盖传入的全部长度。本项只记录目标行为，不修改服务端边界策略，也不推断真实业务reader对坏帧是否有副作用。压缩/替换delegate、真实工厂与负载schema、实际密钥来源、socket/Handler和D-02仍未验。

```powershell
$env:GAME_PATH = & ./tools/game_path.ps1
python tools/trace_client72_frame_reader.py --client $env:GAME_PATH --output local/migration72/evidence/frame-reader-trace.json --summary-output protocol/cn-7.2/frame-reader-observations.json
python -m unittest tools.test_client72_frame_reader
```

18原生样本二次trace/摘要逐字节一致，216研究回归通过，研究校验0/ready1；首次独立测试把12字节header手算成11，核对明确字节向量后修正测试预期，没有改变原生结果。共享摘要固定输入hash；日志及确定性报告见Harness。没有运行代码/Proto/配置/补丁/资源改动，不部署。下一步真实payload/密钥来源串联或P0闭包。


## A-04d5t 连续Token构造与真实序列化封包

`trace_client72_token_frame`在同次CPU运行中从15121FF70执行原生seed helper验签/加密/Base64、Token字段构造、151214D20队列/选定transport，再进入14A447E00调用Token真实槽13 CalculateSize1500C16E0及槽17保护writer1500C2660，最终完整帧返回到原调用者。仅在首次send入口安装显式池/虚表/stream存储，**不重置CPU/寄存器、不复制request字段、不回填seed到request**。Frame72遗留opaque body回调在本类明确拒绝调用。

原始request指针经envelope/packet不变，seed helper返回指针仍是request+90/tag623；整个request对象在封包前后逐字节不变。按已独立恢复的全部28字段表，对实际内存值和解密后的wire逐字段比较，拒绝额外/重复字段。完整加密帧再与独立帧表达式比较；header与body长度由真实size/writer生成，不传入预计算payload。

编码来源：Encoding类型443的命名utf8Encoding静态槽raw040024B8，先放入明确缓存实例，再执行1517676B0.cctor实际写GLOBAL+10770。虚槽11 GetByteCount(string)、19 GetBytes(string,int,int,byte[],int)声明已核对，编码操作以Python严格UTF-8作为明确模型；实际string writer15171FE80的ASCII快速分支/非ASCII输出分支及size1517204C0均执行原生代码。不覆盖无效UTF-16代理对/真实编码器初始化；不可将它与版本hash的ASCIIEncoding混淆。

6组：seed零值/最大Random输入、中文+补充字符+NUL、四个1000字符来源令帧长4486越过4096字节周期、未初始化、坏签名。4成功seed结果344字节Base64；两错误样本原生返回空seed但外层仍完成发送（分别184字节帧），没有强迫密码学成功。两槽4096字节frame key是明确数组，构造器自身把selector写1，确认选中GLOBAL+19588；**真实初始frame key来源仍未验**。

```powershell
$env:GAME_PATH = & ./tools/game_path.ps1
python tools/trace_client72_token_frame.py --client $env:GAME_PATH --output local/migration72/evidence/token-frame-trace.json --summary-output protocol/cn-7.2/token-frame-observations.json
python -m unittest tools.test_client72_token_frame
```

6原生样本重复trace/摘要一致，222研究回归通过；研究校验0/ready1。没有运行Java/Proto/配置/补丁/资源修改，无部署。SDK/OS/old Shell、所选transport、缓存/池和初始frame key仍为明确夹具；尚未覆盖socket、真实Login/响应串联或D-02。首轮组合错误源于独立.cctor把初始栈返回地址留为RETURN，后续SessionRuntime初始化误跳；现仅恢复研究harness END哨兵，未改变目标代码。

用户2026-11-05补充允许按实际情况动态调试。本机已准备[x64dbg官方固定发布2026.05.27](https://github.com/x64dbg/x64dbg/releases/tag/2026.05.27)，ZIP按官方GitHub资产SHA-256 d41966dfc5b435a372798245300ca0ab7bb8e48bdbf48512c6fb20fcca427697核验；放在忽略local/runtime，不注册系统调试器。来源与下载报告在local/migration72/evidence/debugger-{release,install}.json。下一步受控启动观察仍需记录真实PID/模块/状态，工具准备不构成动态或实机验收。
