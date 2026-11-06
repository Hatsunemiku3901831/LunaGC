# LoginReq 嵌套与布尔字段离线证据

## F-02b/F-03当前生产接入

2026-11-06，生产登录闭包已包含PlayerLoginReq的55个外层字段、GetPlayerTokenRsp/PlayerLoginRsp的41/44个外层字段，以及18个raw嵌套类型58字段，共21类198字段。只有既有verified语义使用业务名，其余保留目标raw名；不是198个字段的业务语义全部恢复。历史A-04d3a的12类83字段是LoginReq单独研究闭包，不改写为本轮union数量。

生成入口为`tools/generate_login72_contract.py --client $env:GAME_PATH`，只生成三个根Proto、Client72LoginNested及已验规范包号；加`--check`只检查漂移。Java继续由项目Gradle/protoc生成。固定三输入hash、独立wire摘要与已审语义边界均由生成器检查。`tools/check_login72_contract.py`已用生产Proto通过50组独立native writer逐字节比较、34组production→response native graph及6组错误拒绝；这不是同schema自编自解。

嵌套标量使用proto3 optional是Java存在性适配：使重复消息的显式零、false、空字符串覆盖旧值，同时保留后次未提供的字段；不宣称目标原Proto写了optional。4项独立原生wire Java回归已通过，普通消息/map/oneof各自合并边界见[响应对象图](CLIENT_72_GRAPH_READERS.md)。最小Token发送、LoginRsp资源tag61/1,3,4,5投影及MD5本地语义见[响应合同](CLIENT_72_RESPONSE_CONTRACT.md)。

F-03四组场景握手及请求token关联另见[场景合同](CLIENT_72_SCENE_HANDSHAKE.md)、[省略边界](CLIENT_72_SCENE_REQUEST_CONTRACT.md)，StoreWeight见[独立合同](CLIENT_72_STORE_WEIGHT_CONTRACT.md)。本轮38套Java中间选集通过；初始化Notify仍在逐项核实，不代表完整初始化或D-02。LoginRsp连续收帧key selector也仍未证明。以下为A-04d3a原始研究记录，保留当批模型边界与验证数字。

固定输入同 [目标提取](CLIENT_72_EXTRACTION.md)，入口为类型 76410 / KOHBOMDDFCB / CmdID 23172 的 WriteTo 152EEB470。三层业务映射仍受 [保护入口模型](CLIENT_72_PROTECTED.md) 的限制；本页仅验收 A-04d3a 的原始 wire 恢复，不宣称客户端登录成功。

`trace_client72_login.py` 已覆盖外层全部 55 个实例字段。三条嵌套边分别为 HDGALALKMCK→NBFMIBMFJIO（tag 769）、HIAJLCOHEFP→HMLAJHBHLOC（962）、PJFPOHEKNEF→PBBDOCEDNIE（1295）。PPGGBLPHADP 是 ByteString，不当作嵌套消息。

六个 bool 每次只置位一个，要求原有非 bool 输出完全不变且恰好新增一个值为 1 的 varint；因此已消除共享值带来的字段歧义：

| 原始字段 | 对象偏移 | tag | 业务语义 |
| --- | --- | --- | --- |
| EJHPCJMKBOM | 321 | 6 | 未知 |
| EGDHLKIDOID | 320 | 14 | 未知 |
| COAMILMLPIC | 351 | 473 | 未知 |
| GJKKCNFPPGA | 350 | 1168 | 未知 |
| AGPMKEEOBGD | 348 | 1312 | 未知 |
| JILJKLNFMMK | 349 | 1640 | 未知 |

嵌套依赖展开为 12 个消息、83 个 wire 字段，详见 [原始观察](../protocol/cn-7.2/login-wire-observations.json) 和 [研究 Proto](../protocol/cn-7.2/login-research.proto)。NBFMIBMFJIO 包含 packed uint32；KHDFHMAMMBI 包含 packed uint64。PBBDOCEDNIE 两字段是 int32，负值使用十字节 varint。HMLAJHBHLOC 又引用两组 oneof：KEDIEGLJIMC 的 case 11/12/13/14，KCIAPDNPNHE 的 case 11/12；所有分支分别执行，子类型由 Clone 自分配与类型检查使用的 TypeInfo cell 交叉确认。`case_N` 是研究占位名，不是恢复出的业务名称。

## 模型边界

共用 `client72_emulator.py` 装入固定 PE，只执行允许的方法与保护区，未知调用/未映射访问/系统指令/超预算均失败。`Writer72` 明确模型化 ASCII 字符串、ByteString、嵌套长度前缀、两个 packed 容器及枚举 int32 写入。消息选择、tag、缺省条件和 oneof 类型判断仍执行目标原生代码。

嵌套 helper 151719B20 的长度和内容来自子消息自己的原生 writer 输出。容器 helper 158DB8540 / 1571DD480 的 codec tag 分别由 .cctor 1487CE4F4 的 `mov ecx,10` 和 14782F544 的 `mov ecx,18` 固定，工具实际检查指令字节。模型使用合成已初始化 TypeInfo 和静态 codec，不代表完整运行时泛型 ABI 已恢复。枚举仅恢复存储类型和原始类型名，符号值仍未知，研究 Proto 暂用 int32；IFix、非 ASCII 和客户端实际对象生命周期未验收。

## 复现与验证

```powershell
python tools/trace_client72_login.py --client $env:GAME_PATH --output local/migration72/evidence/login-trace.json --mapping-output protocol/cn-7.2/login-wire-observations.json --proto-output protocol/cn-7.2/login-research.proto
python tools/check_client72_login_proto.py --protoc $env:PROTOC72 --trace local/migration72/evidence/login-trace.json
python -m unittest tools.test_client72_login tools.test_client72_protected -v
```

实际 50 组夹具涵盖唯一非零值、逐 bool 扰动、uint32/uint64 边界、负 int32、空对象、packed 列表、全部 oneof 分支。protoc 输入由原始合成对象构造，不先按生成 schema 解码原生输出；50 次逐字节比较及 3 条独立手写 wire 断言通过。第二次目标运行的报告、映射、Proto 均逐字节一致。工具/结构/映射回归共 52 项通过。

本机证据：`evidence/login-trace.json`、`login-protoc-check.json`、`login-determinism.json`、`logs/a04d3-login-tests.log`（均在 local/migration72）。这些只是离线受控输入，未获得真实响应样本，也未修改 src/main/proto 或服务端运行内容。
