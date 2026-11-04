# TokenRsp / LoginRsp 受控读取与外层 wire

A-04d3b 的范围是响应外层字段定位与标量受控读取。输入固定为 [目标哈希](CLIENT_72_EXTRACTION.md)，工具 `trace_client72_responses.py`；离线环境同 [保护入口](CLIENT_72_PROTECTED.md)。**两个响应的嵌套内容、业务语义和真实登录仍未验收，A-04d3 整体仍在进行。**

| 候选业务名 | 原始类型 / 索引 | 静态 CmdID | 受保护 reader | 外层实例字段 |
| --- | --- | --- | --- | --- |
| GetPlayerTokenRsp | CLDBKDNJHFG / 37481 | 27808 | 148B7EA10→15A4DBA53 | 41 |
| PlayerLoginRsp | NDENMDAIAMC / 40503 | 20835 | 14B89EC90→15A4DC5A8 | 44 |

类型/CmdID 来自已有网络 getter 表；这次新增的是字段读取证据，不提升业务名称的 candidate 状态。[response-wire-observations.json](../protocol/cn-7.2/response-wire-observations.json) 记录每个字段的 tag、接受的 wire 类型、对象偏移、元数据类型与原生 store/helper 来源。字段业务语义全部保留 unresolved。

## 实际执行与模型边界

每个响应扫描 tag 1–2047 的 wire 0/1/2/5，64 tag 一组；共 256 组完整返回。这个范围是本次研究搜索区间，不是协议 tag 上限；完成条件另要求元数据中所有实例字段均有唯一 tag，类型与 wire 一致。未知调用、字段缺失/歧义、越界、非法系统指令或指令/时间预算耗尽均停止，不发布完整表。

实际执行对象引用计数检查 14A42BD90（合成对象引用计数为 1），没有将其替换为“认证成功”。ByteString 的 byref 包装器 147CF2D60 也实际执行，由 147CF2DA8 写回对象字段。其他标量与字符串的对象写入由保护函数原生控制流执行，用内存写入 hook 关联当前 tag。

明确模拟的 helper：

| 原生入口 | 受控模型及证据 |
| --- | --- |
| 1474D8100 / 1474D7BB0 | ReadTag / Skip；lastTag +20，position +44；原函数按 wire 分派 |
| 147520110 / 1475202D0 | varint32 / varint64；int32 保留低32位，负值输入十字节 |
| 1475213F0 | 逐字节拼装 little-endian 64位位型；Login 的 double 位型由保护代码写至 +264 |
| 147500E70 / 1475208D0 | 长度前缀 string / ByteString；仅 ASCII 字符串受控模型 |
| 1474D7E60 | 嵌套消息入口；仅接受空 payload，记录目标对象参数，不执行子消息 reader |
| 158DB8E10 | repeated uint32；原函数检查 wire 2 的 packed 分支，否则单值读取；模型支持两种输入 |
| 155F9BE70 / 158912430 / 156D35A40 | 三个 map 实例的 entry 读取入口；仅空 entry，内容尚未模拟 |
| 158DC41F0 | repeated message 分配→嵌套读取→追加；仅空消息 envelope |

静态 codec 来源为 TokenRsp .cctor 148B811A0 和 LoginRsp .cctor 14B8A1770。工具检查六处 key 初始化指令字节，并验证调用时的 codec 指针与目标 tag 一致。TypeInfo cell 1458C2398 / 1459108C8 分别与两个响应 Clone 自类型分配一致；145898960 是 ByteString。合成已初始化 TypeInfo、静态 codec 和预分配嵌套对象不代表运行时泛型 ABI/分配/释放已通过。

共享模拟器对保护区不逐指令进入 Python 回调以降低扫描开销，Unicorn 的全指令预算和时间上限仍有效。`observedNativeInstructions` 只统计保护区外观察到的原生指令，不当作完整执行指令数。

## 已验证与未恢复内容

TokenRsp GNHGKGCEFIO：tag 376、对象 +168、repeated uint32，packed key 3010，unpacked key 3008。KMLEGLIGNPH：tag 454、NNKFJMDCCMB 嵌套；A-04d3b仅空envelope，后续独立叶子读写结果见下。

LoginRsp 的 HIAJLCOHEFP 是 tag 470 / HMLAJHBHLOC（LoginReq 的研究 writer 已知，但响应 reader 尚未执行其非空内容）。另有 NNKFJMDCCMB、MDKGJAOEHIL、FAHEHGJDAME，消息列表 GHANJBIKPDH / LINNJHPIFAO，以及 map 值 DKFJJMGPCMD / ANDFHAFNBOJ；独立叶子字段现已恢复，父容器非空集成仍待验。三个 map 的泛型参数是 string→int32、uint64→DKFJJMGPCMD、uint32→ANDFHAFNBOJ；这些来自元数据，不能据此声称 entry 内容解析已通过。

实际 11 组受控验证：两个空输入；两类响应各两组全部字段（非零/零bool、负int32、uint32边界、大于2^53的uint64、非零double、ASCII/二进制字节、空嵌套envelope）；两条手写独立 wire；两组重复标量/错误wire分派；一组 packed/unpacked 混合列表。预期值直接与原生对象 store/helper 参数比较。

独立字节输入分别使用 Token 的 `38` / `70` 与 Login 的 `28` / `40`，确认 uint64 9007199254740993 与低32位 -1 写入对应偏移。这里只确认数值/字段表示，不将 LDLIEKOAFBP 等业务语义提升为 verified。重复标量实测最后值生效，错误 wire 进入未知字段跳过路径。

另外，受控输入模型拒绝截断、零 tag、过长 varint，以及非空但尚未模型化的嵌套内容；这些是工具模型的拒绝测试，**不宣称真实客户端采用完全相同的异常语义**。没有真实网络抓包、新旧账号登录或游戏内验收。

## 复现与下一步

```powershell
python tools/trace_client72_responses.py --client $env:GAME_PATH --output local/migration72/evidence/responses-trace.json --mapping-output protocol/cn-7.2/response-wire-observations.json
python -m unittest tools.test_client72_reader tools.test_client72_login tools.test_validate_protocol72 -v
python tools/validate_protocol72.py
python tools/validate_protocol72.py --ready
```

实际63项工具/映射回归通过；两次最终目标运行的完整报告和原始字段表逐字节一致。普通映射校验通过，ready 实际退出1，仍不能发布。证据位于 `local/migration72/evidence/responses-trace*.json`、`responses-determinism.json`；工具测试在 `logs/a04d3-reader-tests.log`。原始研究反汇编/扫描在 `analysis/reader-*`，不提交。

下一项 A-04d3c：将下述已恢复叶子接回父响应的非空嵌套路径，恢复map entry/列表内容与重复消息合并；HMLAJHBHLOC仍需reader/oneof非空路径。独立叶子通过不能替代父容器或客户端验收。


## A-04d3c1：七个叶子消息双向恢复

`trace_client72_response_leaves.py` 已对 NNKFJMDCCMB、MDKGJAOEHIL、FAHEHGJDAME、GHANJBIKPDH、LINNJHPIFAO、DKFJJMGPCMD、ANDFHAFNBOJ 恢复共30字段。输入为独立构造的非零/逐bool扰动/边界/空对象；先运行各自真实writer，再运行各自真实reader，将对象写入与原始输入比较。26组writer、27组reader（含一条额外手写wire）通过；protoc比较26次，另2条独立字节断言通过。第二次报告/字段表/Proto逐字节一致，最新工具回归64项通过。

重要差异：GHANJBIKPDH（44443）的两个元数据int32是 **sfixed32**：PGEMKKCNEMJ +28 / tag13，EMBPDEHFOJK +24 / tag15。writer 14824F800 直接写key 6D/7D，并调用151766CA0逐字节写32位；reader 14824FBB0比较6D/7D并调用147521000逐字节拼装32位。这两段固定宽度函数实际执行，只对reader的底层单字节读取14751FD30使用有界模型。独立输入 `6dffffffff7d2c010000` 写回 +28=-1、+24=300；不能从C# int32推定protobuf int32。已加入此差异的回归测试。

共享NNKFJMDCCMB（85772）的JJLJMLEJIIK（+40/tag1/uint32），EBMBKOOKKPD（+44/tag2/uint32），BFOFKNFHPJN（+32/tag3/string），HLFMBMBLHME（+24/tag4/string）。业务含义仍未知。其reader 1533D21A0与writer 1533D22B0均已执行非空输入。

交付：[叶子字段表](../protocol/cn-7.2/response-leaf-wire-observations.json)、[研究Proto](../protocol/cn-7.2/response-leaves.proto)。父响应表的contentStatus=not-traced继续表示父容器内部路径尚未执行，不因独立叶子通过而自动晋级。

```powershell
python tools/trace_client72_response_leaves.py --client $env:GAME_PATH --output local/migration72/evidence/response-leaves-trace.json --mapping-output protocol/cn-7.2/response-leaf-wire-observations.json --proto-output protocol/cn-7.2/response-leaves.proto
python tools/check_client72_response_leaves_proto.py --protoc $env:PROTOC72 --trace local/migration72/evidence/response-leaves-trace.json
```

本机证据为evidence/response-leaves-trace.json、response-leaves-protoc-check.json、response-leaves-determinism.json及logs/a04d3-leaf-tests.log。没有新增服务端协议或实际游戏验收。
