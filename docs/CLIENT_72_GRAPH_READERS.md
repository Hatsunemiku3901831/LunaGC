# A-04d3c：响应非空嵌套、容器与 oneof

## F-02b/F-03当前生产对照

2026-11-06，本文历史响应闭包18类137字段已与LoginReq研究闭包合并接入生产：三个根类型的55+41+44字段，加18个raw嵌套类型58字段，共21类198字段。这里的18个nested不是下文包含两个响应根的18类；保留原研究数字及原始摘要。生成入口与固定hash/语义边界守卫见[登录wire](CLIENT_72_LOGIN_WIRE.md)。生产Proto经50组独立native writer、34组production→native response graph及下文6组错误拒绝对照通过；5组原生异常分支/1组有界helper拒绝的范围不扩大为完整运行时错误语义。

Client72LoginNested的普通singular原始标量使用proto3 optional，目的是保留Java parse的存在性并让重复消息的显式默认值覆盖旧值；不是恢复了目标Proto的optional声明。`Protocol72ResponseMergeTest`的4项独立原生wire回归分别覆盖Token nested非零→显式零、后次未提供字段保留、同oneof消息bool true→false且保留大于2^53的uint64、Login MDKG非零→零/false/空字符串。预期来自原生reader对象结果和独立hex，不由生产schema往返生成；map value最后替换、普通消息合并及oneof切换仍各按下文已证边界处理。

50组canonical writer比较只设置非默认值以对齐目标writer的默认省略；Java显式setter(0/false/空串)可能因presence编码该字段，不能把canonical比较通过称作这些显式设置的逐字节一致。重复显式默认值的接收行为由上述独立回归单独覆盖，详见[响应合同](CLIENT_72_RESPONSE_CONTRACT.md)。Token仅四字段加密成功/retcode-only失败、Login资源61/1,3,4,5最小投影已接入；LoginRsp连续收帧key selector未证。

F-03的场景scalar存在性同样是Java适配，请求缺字段则必须按raw0逆变换，不能套用响应构造默认，见[场景握手](CLIENT_72_SCENE_HANDSHAKE.md)及[请求边界](CLIENT_72_SCENE_REQUEST_CONTRACT.md)。38套Java中间选集通过，账号准入/确认保存/经济Guard/Lua不变；初始化Notify仍在核实，完整客户端初始化与D-02未验。以下为A-04d3c当批离线记录，未修改生产等描述只针对该历史研究批次。

固定输入同 [目标提取](CLIENT_72_EXTRACTION.md)。本批验收范围是**离线、受控初始化状态下的原生读取控制流和 wire 行为**。未启动/注入真实客户端，未修改服务端主Proto、包号、版本准入或客户端文件；SDK、认证、新旧账号登录和入场仍未验收。字段业务名称与枚举符号继续未知。

`trace_client72_graph.py` 已把此前7个叶子接回 TokenRsp/LoginRsp 父reader，补齐 HMLAJHBHLOC 的reader、两组oneof及其6个子类型。研究闭包为18个消息、137字段。原始观察见 [response-graph-observations.json](../protocol/cn-7.2/response-graph-observations.json)，研究Proto见 [response-graph.proto](../protocol/cn-7.2/response-graph.proto)。旧response-wire-observations是外层扫描检查点，其not-traced状态由新表的contentBindings补充，不篡改历史扫描工具的支持范围。

## 执行链与显式模型

父响应的受保护分派、原生 ReadMessage（1474D7E60）、PushLimit（14751FFB0）、PopLimit（1475210F0）、各子类型reader和oneof分支均实际执行。ReadMessage通过合成接口表分派到目标原生方法，按长度压入/恢复输入边界，并检查递归深度和结束状态。没有用Python按研究schema解析嵌套payload。

| 实际执行的目标代码 | 仍明确模型化的运行环境 |
| --- | --- |
| Token/LoginRsp分派、子消息字段存储、普通消息合并、oneof复制/切换 | 已初始化TypeInfo/接口表/RGCTX；消息分配和缺省状态；构造器反射枚举以已构造对象模型代替，不声称测过真实GC/池化生命周期 |
| 三个map的adapter构造/reader、set_Item及重复节点覆盖 | 托管Dictionary查找/插入、链表节点分配；字符串按内容比较，整数key保留完整位宽；新节点存储由模型提供，重复节点更新由原生setter实际写内存 |
| uint32/uint64 packed/unpacked循环、原生Add/AddRange、消息列表分配/解析/追加 | 原始varint/string/ByteString读取helper、委托绑定、数组复制；固定数组容量64；超出模型范围停止，不宣称客户端有64项限制 |
| 固定32位reader、父子长度/递归检查、TryConsumeTag的lookahead | 底层有界单字节读取；ASCII字符串范围；未支持IFix、非ASCII或真实进程ABI |

map adapter不是按形状猜选，声明、共享实参、构造/reader/setter来自 [7.2 AOT注册表](CLIENT_72_MAP_AOT.md)。执行前重新提取AOT记录并与公共表逐项比较。具体map与共享实例的读取入口同址得到独立校验。原生函数允许范围由目标PE异常目录中的精确起止地址约束；合成库回调使用明确的模拟地址，不伪装为客户端VA。

CodedInputStream的+20保存lastTag，+25/+3C保存lookahead，+38为当前limit，+40为当前可见buffer大小，+44为position。工具把边界同时应用于标量/bytes读取；子消息不能借父消息后续字节补齐截断varint。缺失map message value会走原生“默认空reader→分配默认value”路径，已覆盖多reader状态切换。

## 已观察到的内容与合并行为

- TokenRsp的NNKFJMDCCMB及LoginRsp的NNKFJMDCCMB、两个独立MDKGJAOEHIL、FAHEHGJDAME均读取非空内容。重复普通消息保留前次未覆盖字段；后续显式零值覆盖对应标量，父消息后续字段仍正确读取。
- uint32列表与HML中的uint64列表同时支持本目标的packed/unpacked分派；多段追加，覆盖非零、UINT32_MAX、大于2^53的值、UINT64_MAX及64元素模型边界。
- Login的GHANJBIKPDH、LINNJHPIFAO消息列表追加多个独立对象；前者两个sfixed32字段通过实际固定宽度reader处理负数。
- 三个map覆盖非零key/value、负int32、64位大key、UINT64_MAX key、bytes、bool和缺失key/value。相同key的后一个entry替换旧value；原生set_Item覆盖节点的写入地址记录在trace的mapStores中。
- 同一个map entry内重复出现message value时，本目标每次分配新对象，最后一个value生效，包含最后一个为空消息的情况。此行为与本目标普通singular嵌套消息的合并不同；**本批没有据此宣称所有protobuf运行库的解码行为相同或不同**。
- HMLAJHBHLOC reader为150887560；KEDIEGLJIMC reader为1516B1370，case 11/12/13/14；KCIAPDNPNHE reader为153BF9A40，case 11/12。8个组合分别执行，两组oneof的所有分支都有非零输入。同一分支合并旧字段/追加列表，换分支丢弃旧内容，切回旧分支不会恢复旧对象字段；空消息仍保留oneof presence。
- HML与GGAHLPJMAHH的枚举仅按原始int32位型验证，包括非零/负值。DLEEHOLOJKM、KBOEJJBMALM的符号值未恢复，不能将这些数值测试称作业务枚举确认。

## 实际测试

34组独立构造的wire输入均运行父响应reader并与预期对象图比较。另将同一批独立预期对象交给protoc编码，34组生成wire再次由目标原生reader读取，结果一致。这不是同一schema自编自解；协议实现来自目标二进制。未知字段/错误wire原始样本另验证目标跳过行为，protoc只编码预期的已知字段。

6组拒绝样本：父消息截断、负长度、子varint跨越limit、递归超限、map entry长度超限、map value越过entry边界。其中5组到达原生异常构造分支，1组由显式有界primitive helper拒绝。它们不代表完整CLR异常类型或所有错误输入行为已恢复。

工具回归含lookahead不重复消费、nested limit、map key位宽/字符串内容比较、AOT歧义和越界、map Proto文本及原始手写wire。普通三层校验继续通过，ready仍失败：wire与内容观察不会自动提升unknown_field的业务语义。

## 可复现与接手

```powershell
python tools/extract_client72_map_aot.py --client $env:GAME_PATH --output protocol/cn-7.2/map-aot-observations.json
# 第一次生成研究Proto；以后使用下面带protoc的命令即可。
python tools/trace_client72_graph.py --client $env:GAME_PATH --output local/migration72/evidence/response-graph-trace.json --proto-output protocol/cn-7.2/response-graph.proto --summary-output protocol/cn-7.2/response-graph-observations.json
python tools/trace_client72_graph.py --client $env:GAME_PATH --output local/migration72/evidence/response-graph-trace.json --proto-output protocol/cn-7.2/response-graph.proto --summary-output protocol/cn-7.2/response-graph-observations.json --protoc $env:PROTOC72
python -m unittest tools.test_client72_map_aot tools.test_client72_graph tools.test_client72_reader tools.test_validate_protocol72 -v
python tools/validate_protocol72.py
python tools/validate_protocol72.py --ready
```

工具：client72_graph执行对象图；client72_map_runtime只提供显式托管存储模型；client72_graph_fixtures保留独立输入及预期；trace_client72_graph完成输入固定、注册复核、原生/protoc验证和产物生成。输入或模型改变后重新执行，不能复用本页通过数当作新结果。

本机证据：local/migration72/evidence/response-graph-trace.json、response-graph-determinism.json、map-aot-determinism.json；logs/a04d3c-graph-tests.log。原始反汇编在analysis/a04d3c-*及map-entry-*，不提交。

后续从A-04d/B-02继续：闭合Token/Login请求与响应回调的业务身份、字段来源和枚举符号；再开展正式协议生成与运行接入。完整wire研究闭包不代替登录依赖闭包、客户端补丁ABI、资源或实机验收。
