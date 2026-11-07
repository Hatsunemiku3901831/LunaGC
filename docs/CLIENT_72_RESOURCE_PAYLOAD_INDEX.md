# D02-R3：真实 `0000006f` byte[] 的索引消费后继

2026-11-08，接续已冻结 [payload 合同](CLIENT_72_RESOURCE_PAYLOAD.md)。本限定子合同已由主线审代码/模型、第三回放及接收，旧 payload/blob/provider/native/index 源文件保持冻结。

主线独立`main/payload-index-third.json` SHA`e8a02de4a4b24c58be647e8f1cb2638b5e5a06f1b30427a8ae5a627291d54ab9`、两定位产物与双跑逐字节相同，8项独立检查通过，证明`main/followups-main-proof.json`、日志`payload-index-main-tests.log`。两实际byte[]全量独立解析至EOF；原生仅4个真实记录对照、3 selector边界与5 EOF截断，primitive/void sink模型均明确，不称全量native DeserializeBinary/ctable/运行资源已通过。仅研究工具/测试/文档，无需服务部署。

## 重大问题计划

| 项目 | 约定 |
| --- | --- |
| 目标 | 用实际 `31049740/31049741` 的 `0000006f` byte[] 对照准确 LoadBinDataZipFile/ResourceIndex/DeserializeBinary 消费，恢复可验证索引格式、资源/表定位及来源关系。 |
| 固定证据 | payload source `b35ab488fbab3f54528598ebf5f81df14e755f66c92e9348ca63b6ea1e4e00aa`，双报告 `efd152bc8e7337ab5af6aa685a1b16567e63c10a84a31398781d2711521deced`；两 m_bytes 分别48,614,214B/SHA `216c791b536ce8e4f1f0128c200a791015768491bf23f2a8a9bc618720b6eb7d`、3,621,204B/SHA `782aa1208ab3070a567f8907e7f15b8f3ad046b0d661cdd4774ef9af8f81dffc`；固定五 profile 输入。 |
| 未知与候选 | 字段/计数/嵌套布局、两文件各自角色、全局配置及类型恢复、与 ctable/每表/候选 exporter/revision 的实际对应。首个u32或字符串可见不单独证明语义。 |
| 最小实验 | 新读窄 metadata owner/method/field，沿实际 native consumer 边界核有序读取；先以真实 byte[] 的长度/EOF/位置合同执行可控原指令，再独立解析并检查全量计数/64位/范围/尾部。 |
| 范围 | 新 payload-index 工具/test/本页及忽略 `resource-payload-index-astra/`。无 live、启动、断点、生产资源、Git、Gradle、数据库或部署。 |
| 失败出口 | 真实缺失类型/runtime helper 定点记录，不猜 return/字段映射/密钥，不将两个名字相同推为同一种业务表。 |
| 验收 | 固定来源、原生与模型边界、独立边界/错误样本、全量消费或准确余量、新进程双跑及主线第三复验；未证 ctable/native reader/exporter 保持 false。 |

## 本轮实际结果

两份实际 byte[] 按固定 PE 的 `AssetIndexDeserializer.DeserializeBinary 146f00000` 调用顺序完整独立解析，均精确 EOF。不是解密一个新假定头，也不是根据文件名认定格式；七段读取的字段宽度、嵌套循环及可选尾段来自各准确 `.pdata` 函数。原生 `ReadAssetInfoMap_B` 的原指令再对真实首记录及真实双变体记录作有界对照。

| 段 | native consumer | 31049740 `[start,end)` / 数量 | 31049741 `[start,end)` / 数量 |
| --- | --- | --- | --- |
| 类型字符串对 | `146f00480` | `[0,19661)` / 357 | `[0,4)` / 0 |
| u64 资源键和变体 | `146f00580` | `[19661,12973844)` / 925261 | `[4,3607108)` / 257650 |
| bundle 依赖 | `146f00880` | `[12973844,31280280)` / 768956 行、3038694 依赖 | `[3607108,3607120)` / 0 行 |
| 预加载块 | `146f01f20` | `[31280280,31280804)` / 130 | `[3607120,3607124)` / 0 |
| shader 预加载块 | `146f020d0` | `[31280804,31280884)` / 19 | `[3607124,3607128)` / 0 |
| 块分组/两个 byte | `146f02280` 前半 | `[31280884,31289974)` / 16 组、1493 块 | `[3607128,3610476)` / 16 组、536 块 |
| 块与 bundle 定位 | 同函数后半 | `[31289974,48608238)` / 1493 块、1442193 记录 | `[3610476,3621200)` / 536 块、536 记录 |
| 可选排序列表 | `146f02a60` | `[48608238,48614214)` / 1493 | `[3621200,3621204)` / 0 |

真实两份资源键均无重复。31049740 的 925156 个记录有一个变体，105 个记录有两个；tag 共 925261 个 0、105 个 1。31049741 全为单变体 tag0。类型字符串全部 ASCII；`ReadStringWithLength 146d8cad0` 实际读取字符数组并构造 UTF16 字符串，故本合同明确拒绝未经证明的非 ASCII 字符编码扩展。

字段合同：

- 数值均小端；section count、bundle/dependency/location 字段为 i32。资源 key 为 **u64**；记录随后 u8 变体数，每个变体为 `(u8 tag,i32 value)`。不把 value 的高位剥离为猜测 flag，也不直接强制它小于 bundle capacity。
- native `146f00717..146f00722` 的选择是：当前值为零时接纳候选，或候选 tag 等于 `14586d100` 的 u32 时接纳候选。这是“首个非零值/匹配 tag”的控制行为，不能简化成“总是第一项”。该全局自然值未采样；原始所有变体保留。
- 依赖头为 `(i32 行数,i32 总依赖数,i32 capacity)`，随后每行 `(i32 bundleID,i32 n,n*i32)`。两文件 capacity 分别 1449476、537；原生以该 capacity 初始化后续结构。实际总依赖计数全量一致。
- 块类别组为 `(i32 group,i32 n,n*(i32 block,u8 a,u8 b))`。native 存 `2*a | b`；随后查 group 时使用其低 byte。本报告保留原始 group，而不命名它为语言或渠道。
- 定位组为 `(i32 block,i32 n,n*(i32 bundle,i32 A,i32 B))`。native 传 `{block,bundle,A,B}` 给 `147236520`。31049741 全部 A/B=0/0，**未解释为零字节实际文件**。
- `146f02a60` 首先比较 stream Position/Length；仅未到 EOF 时读取最后一个 count 和 i32 列表。

## 独立真实文件桥梁

31049740 定位首行是 `blockID=9302948,bundleID=29475,A=0,B=3978`。此前由同一自然 manager 真实文件名取得的 `09302948.blk` 固定 SHA `f307fff7f1b3bb3005ae9b15ca7c869726c2c1f75ffd73ddbff9bf4780f8dd6f`，其第一段原生 blob 头给出的 `logicalBlobBytes=3978`。二者独立一致。该块 221 条定位的 A/B 区间全都位于实际 9659857 B 文件内；只第一条已经过 native blob 头校验，未声称其余 220 条都已解码。第一条 tag9 payload 尚未解码。

因此现在有真实 **index 记录→已自然观测文件→首 native blob** 的局部桥梁。尚无 `Data/_ExcelBinOutput/Avatar...` 的准确资源键、每表读取或候选 JSON/revision 证明，不能晋级为 ctable 已解码。

## 原生执行与模型边界

执行 `146f00580` 完整准确 `.pdata` 范围内的原指令，保留 PE 中的 IFix 分支状态；不修改客户端文件，不调用 native reader 成功桩。两次实际输入运行保留整个真实 asset section 和原始 count，在首条输出后的 `146f00816` 主动停止。另以一条未经改动的实际双变体记录构造明确的 count=1 fixture，分别用 selector0/1 完整运行函数。共四个真实记录对照，不能称全量 native DeserializeBinary。

模型仅包括：

1. 显式 synthetic managed reader/vtable：VT+120/+148/+160 按原 `ReadByte/ReadInt32/ReadULong` 包装函数宽度从固定 byte[] 顺序读取；真实 EOF 报错停机。它不是 System.IO.BinaryReader/Stream 的完整原生实现。
2. `147234d00`、`147234e10` 仅记录被调用的 count 和输出 struct；两处后继均忽略 RAX。未执行 managed Dictionary 分配/写入，也没有凭空返回“插入成功”。
3. synthetic 对象及 selector0/1/7 为离线输入；没有宣称来自存活目标。`LoadBinDataZipFile`、完整 index 构造和每表 reader 未执行。

八个 native 边界分为三种selector对照（各覆盖u64最大值/高位、零变体、三重变体、零值后继、signed i32）及五个截断长度；截断使reader在实际请求处EOF停止。八项独立单测覆盖完整嵌套字段、所有逐byte截断、计数负数/不可能值、依赖计数不符、location capacity、可选尾段、非ASCII明确拒绝，以及u64/有符号i32不混淆。

## 冻结与复现

```powershell
python -m unittest tools.test_client72_resource_payload_index -v
python tools/client72_resource_payload_index.py --client $env:GAME_PATH --payload-report local/migration72/d02-active/resource-payload-astra/payload-final-1.json --output local/migration72/d02-active/main/payload-index-third.json
```

执行前按当前机器设置`GAME_PATH`；工具逐个校验五个profile文件、冻结payload source/report和两实际byte[] SHA。输出仅允许忽略local且不能位于客户端目录；每次显式选新报告/产物目录，不覆盖旧raw，尤其不要复用另一项已有的resource-index-third.json。

新进程双跑 `resource-payload-index-astra/index-final-{1,2}.json` 各 92384 B，SHA `e8a02de4a4b24c58be647e8f1cb2638b5e5a06f1b30427a8ae5a627291d54ab9`，报告及两个 artifact 逐 byte 相等，证明在同目录 `determinism.json`。

每份报告旁生成 `.locations-le-i32.bin`，每行六个小端 i32，列为 blockID、bundleID、A、B、groupID、categoryBits；它是从原 index 全量读取的派生产物，原 byte[] 仍是来源。31049740 artifact SHA `c013043753bdb2679bb9a59d4aab4d6db62d87febd37bb27e69adb00ee8093f2`；31049741 artifact SHA `29b5f4215a2c5caf2d1b57cf967f0d8e1bdb96f00af57d229e63be2a27c4ebb0`。

下一任务应从已知准确资源 literal 的真实 `GetResourceIdentifier`/lookup 原指令求 u64 key，连接这些保留的完整变体与定位。未证明的 tag9 codec、ctable、revision 和 exporter 仍 false。主线拥有第三复验、共享文档与 Git 提交；本代理未操作 Git、实机或部署。
