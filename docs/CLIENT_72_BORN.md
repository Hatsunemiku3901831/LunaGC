# B-02d 选人三件组：固定7.2证据与Sol实现规格

2026-11-06（Australia/Sydney）。**仅三件组的研究映射闭合，可供后续实现与回归；本轮到此停止，不开展B-03/C-01/D-02。** 没有运行服务端、启动客户端或使用动态调试/computer-use。这里的原生执行指Offline72在离线CPU中执行固定EXE的实际机器指令，不是Windows实机验收。

## 输入与三层结果

每次执行先由`read_verified`核对以下SHA-256，重新提取目标结构/参数，不以缓存元数据替代输入检查：

| 输入 | SHA-256 |
| --- | --- |
| YuanShen.exe | 8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5 |
| global-metadata.dat | 45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a |
| startup-metadata.dat | b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105 |

| 语义/方向 | L1 CmdID→类型 | L2 类型→业务 | L3 字段→wire |
| --- | --- | --- | --- |
| DoSetPlayerBornDataNotify / S2C | **5008 → PMNMDAIKALO / 69319**；getter1528AEBE0、注册1507C2B20、分派1507CC240→1507D25D0与Handler参数一致 | Handler门控→14A1EFA30→JDDACGEHGGI选人页面14E457800；选择回调调用目标命名SelectMale/SelectFemale | **无声明字段**；自身无字段且无slot13/17/19覆盖，继承67861的零size、空writer、跳过未知字段reader，三者实际执行 |
| SetPlayerBornDataReq / C2S | **8541 → JNJOMANHPCD / 29956**；getter14CC44780，自身clone14CC44460取类槽1458C5458，构造151223B80同槽分配后进入15120D730发送入口 | 选人回调14E4569D0把角色值写入命名页面+248；提交15245A2F0取输入文本并调用真实validator15245A920，其成功分支在15245A3BF/C8把+248和文本传给151223B80 | **nick_name: string tag4**，FPOLBCIBIOB / +18；**avatar_id: uint32 tag11**，NLNJDCDLHHE / +20；实际构造→writer14CC44560，size14CC44380，手写wire与protoc一致 |
| SetPlayerBornDataRsp / S2C | **414 → EODFLMDBPOG / 34385**；getter15374AA50、注册1507C2B2F、分派1507CC240→1507D2820与参数一致 | 原生Handler读取retcode；0→14CD68860成功继续入口，非0→14CD2B920错误入口；132另发事件850，目标枚举分别命名RetNicknameWordIllegal与NicknameWordIllegal，命名页OnNotify15245B460消费该事件 | **retcode: int32 tag15**，LDLIEKOAFBP / +18；reader15374AC20比较key0x78、写入+18；size15374AB50；独立protoc编码与原生读出一致 |

三层均`verified`只表示上述固定输入和限定证据；三条`runtimeStatus=not-tested`。其他消息、P0闭包、传输域、5组静态重号、tag965等未解字段保持原状态。不能据此宣称7.2登录或可进入世界。

`DoSetPlayerBornDataNotify`空schema来自目标声明、父类及实际执行，**不是因为旧src/main/proto缺文件**。原缺根仍保留在A-03c依赖矩阵；仅在研究目录新增[born-research.proto](../protocol/cn-7.2/born-research.proto)。响应类型仅有接收reader和size，自身没有writer；不能误用继承的空writer发送非零retcode，服务器应由确认的tag15研究规格生成正确响应。

## 实际验证与模型边界

[trace_client72_born.py](../tools/trace_client72_born.py)执行39组：选人默认值1、选择到命名页3、文本到validator入口1、通知到选人页面入口1、分派4、请求构造/writer/size7、null拒绝1、响应reader/size/Handler7、重复/未知字段reader3、错误输入8、通知门控3；此外实际执行三getter和继承空writer/size。全部完成，二次trace/摘要逐字节一致。

- 请求样本含0/空、两默认旅行者10000005与10000007、127/128、uint32高位/最大、中文/补充字符/NUL、130字节文本。独立protoc全负载逐字节比较；固定十六进制向量再由protoc解码。长文本/uint32极值仅验证构造和wire表示，不称其通过UI或服务器业务校验。
- null字符串实际到构造的异常分配入口1404C2770，没有强制成功；整个异常对象创建未执行。空字符串为非null对象，可产生默认省略，不等于合法游戏昵称。
- retcode 0、1、116、132、-1、int32最小/最大；负值为10字节varint，不是sint32/zigzag。目标RetRepeatSetPlayerBornData=116仅确认枚举符号，客户端不会把116当成功；132的错误与850事件分支实际执行。
- 重复retcode最后一个生效；同tag错误wire类型和未知字段被跳过。三件组无声明64位、嵌套、列表、map或oneof字段，所以这些字段类型验收不适用；额外注入64位高值、嵌套及重复未知字段，确认reader跳过，不把它们晋级为字段。
- 截断varint、长度越界、tag0、超过10字节varint均由显式`WireInput`模型拒绝；protoc独立拒绝同组输入。这是原生消息reader调用的输入模型边界，**不是已验证客户端CodedInputStream实际异常分类**。
- 6项聚焦回归验证固定向量、Unicode往返、负数、重复/错误wire、空通知、错误输入、映射旧tag/包号漂移拒绝与未ready约束。研究全选集结果见Harness本批记录；Java业务/经济/保存代码及回归不改，本轮未重跑数据库测试。

明确夹具：已初始化托管类/全局存储、无IFix实例的离线环境、消息分配、UTF-8转换/字符串大小、tag/scalar/skip helper、UI基类存储/渲染与事件/error/success sink、platform=0。请求writer、分支与字段存取、size、响应reader、分派和Handler执行原指令。没有替换业务校验为true、没有跳过客户端校验或修改客户端文件。

语义源分段证据而非完整点击链：提交前缀运行至真实昵称validator入口即停止；validator自身、UI显示、网络等待及完整成功继续路径未执行。成功分支到请求的调用边来自固定目标机器码复核；请求构造另从函数入口以显式参数执行。默认旅行者值由14E456280原生构造读出（10000007/10000005），SetupView另有配置行3覆盖逻辑，不能把默认值当作穷尽客户端所有配置。

## 给Sol的实现与回归规格（下一批，未执行）

1. 只使用上表目标号/类型/字段。正式生产Proto/PacketOpcodes及handler改动属于另行授权的实现批；当前没有生成或接入运行代码。不要保留旧tag2 avatar_id、旧tag6 retcode或配置中的旧/负回包号作为回退。
2. 请求只含nick_name=4、avatar_id=11，均proto3隐式presence；默认值省略，无嵌套依赖。语义名可保持现有Java调用约定。零长度消息合法wire与合法业务输入分开；入口按实际业务规范校验昵称/角色，不凭wire样本放宽。
3. 通知有效payload是零字节，成功响应retcode=0也为零字节，但两者**包号和语义不同**。失败响应必须真实编码tag15，不能继续`new BasePacket`伪造所有响应为空。132触发命名错误路径，116为重复设置错误枚举；不要机械把内部错误都映射成132或116。
4. 固定回归向量：Req(10000005,Aether)=`22064165746865725885ade204`；Req(uint32.max,x)=`22017858ffffffff0f`；Rsp132=`788401`；Rsp116=`7874`；Rsp-1=`78ffffffffffffffffff01`。研究工具还保留边界/Unicode和未知字段组，可作生产生成类的独立对照。
5. 保留账号/存档/经济行为与原Guard回归。新号选人门控、重复创建幂等、错误不进onLogin、成功后持久化/继续、旧号不重选应单独写业务回归；不得以当前研究样本代替业务实现或客户端验收。默认角色/超时兜底不能作为选人成功证据。
6. 若随后实际修改运行代码，按AGENTS完成对应构建和便携部署核验；当前7.2完整连接/资源/补丁前置仍未满足，不能只因本三件组闭合启动猜测协议。实机D-02仍待后续授权和前置就绪。

## 复现与证据位置

从本机已核对配置设置`GAME_PATH`；不要复制旧路径或PID。

```powershell
$protocBorn = (Get-ChildItem local/gradle/caches/modules-2/files-2.1/com.google.protobuf/protoc/3.18.1 -Recurse -Filter '*.exe' | Select-Object -First 1).FullName
python tools/trace_client72_born.py --client $env:GAME_PATH --protoc $protocBorn --output local/migration72/evidence/born-trace.json --summary-output protocol/cn-7.2/born-observations.json
python -m unittest tools.test_client72_born -v
python tools/validate_protocol72.py
python tools/validate_protocol72.py --ready
```

最后一条仍预期退出1。共享：[born-observations.json](../protocol/cn-7.2/born-observations.json)、研究Proto、mappings、更新后的login-proto-gap-matrix/review。本机：`evidence/born-{trace,trace-repeat,summary-repeat,determinism,mapping-check,mapping-ready}.json`与`logs/born-{focused,repeat,regression}.log`；探索反汇编与候选排除保留忽略analysis，未提交客户端二进制或原始数据。

避坑：最初全局literal搜索没有三件组名称；后从目标选人/命名调用链闭合，不能以字符串未命中认定不存在。形状筛选9个string+uint32只用于找候选，不能直接命名。请求clone内联CopyFrom，不能套用先前Token的固定clone调用位置；正式工具检查本类型自己的类槽。无生产改动，因此不重启/部署；不清库、不操作任何客户端。
