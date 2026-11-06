# F-03d3e4：signed-list 与同形子字段

限定 19829、21103、89623 三类，复用已验 68918；40 个 metadata 存储槽经 384 次独立 tag1..32/wire0/1/2/5 原生扫描确认 40 wire。三个 owner 的每个新增字段均有全部非零独立向量；多个同形 68918 字段与列表元素分别给不同值，不按字段顺序或业务名配对。完整复杂图仍未验，51050 非空保持拒绝，`fullAvatarRawClosure=false`、`fullComplexRawClosure=false`、身份/业务语义 `unresolved`、`targetProtocolReady=false`。

每个正式全新进程重新读取并核验 `extract_client72.HASHES` 的三输入，SHA-256 与[AvatarInfo 核心](CLIENT_72_AVATAR_CORE.md)相同；重新提取 metadata、usage cells、选定 AOT 和三 owner reader/cctor 的有界全部类型引用。唯一外部消息依赖为 68918，无 Object。库存复用摘要继续严格核验 `ca45b0d9b038cd46fc35943924ca77ae46c8fbae82c06e131245e03f43d95b18`；不采用 Asta 或 7.1 名称证明 raw。

## int32-list 的独立来源

直接解码 12 条 AOT 记录，含 35584/classInstance6 的 ctor/cctor、两个 Add、reader、setter，45077/6 helper 的 cctor 与三个 ctor，以及 25060/6112→68918 的 ctor/reader。记录不等于所有方法都已执行。signed reader `0x1568b8d60`、scalar Add `0x1568b8b10` 与 message-list reader `0x158dc41f0` 独立绑定，不能用 u32/float 实例替代。

实际 signed reader 从 codec+0x20 读 delegate，通过 EAX→EDX 将低 32 位交给 Add；`0x1568b8bc4` 真正写 `array+0x20+index*4` 的四字节槽。helper usage cell `0x14592a138`、list cell `0x145897788`、int32[] cell `0x145886168`、boxing primitive cell `0x145886118` 均有独立 metadata 来源。模型提供容量 64 的四字节数组，不直接塞最终列表值；snapshot 按 signed int32 解释已写位。

helper cctor `0x155b36190` 实际执行到 `0x155b361c0` 前，默认 int32 boxing 为明确模型；原生 `0x155b361bd` 依据非 null 结果把 static+0 的 packed flag 设为 1。该位置不同于 float 的 static+4，未套用此前值。反射余段、完整 list cctor 与真实托管 null-policy 仍未执行，不宣称完整 runtime ABI。

signed codec factory `0x14e5dedf0` 与实际具体 ctor `0x155b36420` 已独立静态核查：reader delegate 在 +0x20，tag 在 +0x2c，属于明确 primitive codec 模型；没有声称 factory/ctor 整体原生执行。19829 与 89623 两个消息 cctor 实际执行到返回，89623 分别传 u32 参数 50、68918 message 参数 58、int32 参数 74，即 tag6/7/9 的 wire2。

## 独立样本与边界

19 个正向图包括三 owner 全字段非零、signed packed/unpacked 混用追加、`-1/min-int32/max-int32/0/128`、非 canonical 五字节低 32 位解码为 -1、空 packed 保留旧元素、错误 fixed32 wire 跳过、三份 uint64 使用 `2^53+1/2^63/max` 并换位再次独立验证。19829 的两份 68918 singular 分别合并，89623 的两份 singular 与 68918 列表各元素均不串用；空列表元素与显式空 singular 对象保留。三类 empty、标量重复清零和 bool 规范化已验。

signed packed 长度 `ffffffff0f` 经原语返回低 32 位 -1 后，具体 reader `test eax/eax; jle` 原生路径直接返回，不追加元素；此观察是有界 raw 行为，不把它伪装成客户端拒绝。64 元素样本执行 64 次真实 Add，store 偏移 0..252，末值 -63。第 65 元素进入未建模 allocator `0x140514ee0`，明确是 fixture 扩容边界，不是客户端列表上限。

10 个拒绝分为：1 个 51050 非空范围拒绝；4 个 WireInput 模型拒绝（非法 tag、signed varint overflow、signed packed 截断、uint64 overflow）；4 个真正 ReadMessage 错误入口（负消息长度、截断、singular 与 list 递归限额），到入口即停而非完整 throw；1 个上述容量模型边界。托管分配、class/generic metadata、boxing、原语输入和 codec/delegate 均明确为模型。具体三 owner/68918 reader、signed/u32/message-list reader 和 append、嵌套 limits 执行原生代码。没有 IFix、Handler、实机、部署或生产集成通过声明。

8 项新增与合计 122 项关联研究回归通过。两次正式全新进程各核三 hash、提取、扫描及独立图，完整证据和脱敏摘要逐字节一致；本机证据 `local/migration72/evidence/avatar-signed-trees-{native,native2,summary,summary2,determinism}.json`，日志 `local/migration72/logs/avatar-signed-trees-*`。首次测试命令误写不存在的 `test_client72_aot`，保留失败日志；改用实际 `test_client72_map_aot` 与关联集合后全部通过，未放宽测试或 runtime。

```powershell
python tools/trace_client72_avatar_signed_trees.py --client $env:GAME_PATH --output local/migration72/evidence/avatar-signed-trees-native.json --observations local/migration72/evidence/avatar-signed-trees-summary.json
python -m unittest tools.test_client72_avatar_signed_trees -v
```

本卡只新增离线研究工具、测试、脱敏摘要和本文，无生产、Proto、配置或资源修改，无客户端/服务/数据库/调试器操作，不需重启。共享 Project/Harness/矩阵与本地提交由主线审核维护。下一可执行 e5 的五类中层 list/int64，或 e6 的两类大标量节点；e8 仍依赖 e1–e7 全部完成。
