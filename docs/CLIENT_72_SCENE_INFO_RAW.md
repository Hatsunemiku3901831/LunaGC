# F-03d3s1：SceneInfo 外层与有限非空 raw 合同

2026-11-07。D02-R1，**有限子项已双跑并通过主线第三次独立审核；完整 SceneInfo 闭包、业务准入与 D-02 实机仍未完成**。新工具只恢复 9 类的 36 个外层字段，复用既有 e8 的 34 类复杂 raw 图；没有把此前静态 44 类/289 存储槽候选、11 个新增类型或空 Object 模型当作完整闭包。

## 目标、证据缺口与影响

前置是 [场景入场身份](CLIENT_72_SCENE_ENTRY_IDENTITY.md) 对 Cmd21761/type34208 的独立身份与 tag12 本地角色实体 ID 消费合同，以及 e8 已验的 `AvatarCompleteRaw72`。本卡补充此前仅空 envelope 的 18295/44512 非空图，及 49723 的六个外字段与已闭简单后代。三层中仅字段→wire 和具体 raw 读取范围新增；tag7 等未知字段继续用 raw 名，未借旧 AbilitySync/Team/AbilityEmbryo 命名作 7.2 语义证明。

实际开始前的缺口包括 83320 的真实 wire、各 cctor 的具体列表类型与 tag、以及 72829/54267 的 Object 擦除依赖。最小实验为新提取 metadata/参数/usage 后，9 类逐个执行 tag1..32 × wire0/1/2/5，要求全 36 槽独立绑定；再执行手工 literal、非空组合、列表、合并及错误边界。候选扫描范围不是合法 tag 上限，覆盖结论来自本卡全部 metadata 外字段命中。

仅新增以下四文件，公共原生模型、既有 e8 工具/摘要、Proto、Java、sender、全局 mapping/review、ready、配置与资源均未修改：

- [trace_client72_scene_info_raw.py](../tools/trace_client72_scene_info_raw.py)
- [test_client72_scene_info_raw.py](../tools/test_client72_scene_info_raw.py)
- [scene-info-raw-observations.json](../protocol/cn-7.2/scene-info-raw-observations.json)
- 本说明。

## 固定输入与独立来源

每次正式新进程都由 `tools/game_path.ps1` 重新解析 `GAME_PATH`，实际读取并校验 `extract_client72.HASHES` 的 EXE、global、startup 三输入，再重提取完整 metadata、方法参数、具体泛型和 usage。工具不读取旧研究脚本中的路径，也不依赖忽略目录的候选索引或反汇编输入文件。

EXE/global/startup SHA-256 分别为 `8b014f64…21dbd6b5` / `45dc7a44…60f6ef2a` / `b3148108…4d5105`；完整值在共享摘要的 `inputSHA256`。既有 e8 摘要固定 SHA `37c90e4dea9fccc4ac20ce68616612250b5a57169e564081c33dc69a25def837`，同时核其十份上游 raw 摘要，不改原文件。其 34 类/237 wire、Avatar 65 类/372 wire 成果原样复用，不重复宣告为本卡新增。

9 个 reader 的唯一参数均从当前 metadata 独立恢复为 type52310/GEPIHFBDOKD，非 by-ref。共享摘要保存 owner、方法定义、参数索引、注册地址、当前方法字节 SHA、直接 call 目标及 reader/cctor class usage。34208 的保护跳转 `1541B67D0 → 15A4E691C` 保留为实际入口；其虚拟化原生执行不构成静态依赖闭包。

具体 message-list AOT 五实例为 `12523→18295`、`10766→73976`、`6486→53824`、`8979→80154`、`12390→83320`，当前 `FNNADLCNGFO` 均为 `158DC41F0`。u32 实例11独立连至 `158DB8E10`，两个 Add 重载为 `158DB87C0/158DB8BC0`。4 个新 cctor 全部实际执行；5 个 message codec 的 tag/+10 与 class/+18 从原生 cctor 存储后读回验证，18295 的 u32 tag6 另经实际 cctor 调用明确的 primitive codec 模型验证。

## 有限字段合同

偏移均为十六进制。`→` 是 singular message，`[]→` 是 message list；uint32 list 接受 unpacked0/packed2。message 只接受 wire2，float/fixed32 只接受 wire5，普通整数/枚举/bool 只接受 wire0。业务意义除前卡已验的34208/tag12外保持未知。

| type / reader | tag → raw 字段 / 偏移 / raw 类型 |
| --- | --- |
| 34208 / `1541B67D0` | 2→DIGAMAKGCPA/+18/[]→18295；7→MCCMGLDOECH/+34/u32；12→OLCJCLBMMCF/+30/u32；14→LFLOLJADPBI/+28/→44512；15→AEKMONHBHAA/+20/→49723 |
| 18295 / `148B78700` | 4→FDDDFIAINCE/+40/u32；5→CCODNLIKNGM/+20/→34045；6→CEKDDNOPKMK/+18/u32[]；7→IEDJNHILJBF/+44/u32；8→LBJMODOPOCP/+38/→34045；10→NPKKCHABEJH/+50/u64；11→CDOAFDGJGDF/+30/[]→73976；12→JOBBNEKLCOF/+28/[]→53824；13→OKMEHFHNFCG/+48/u64 |
| 44512 / `14A89C2D0` | 1→HKLILOKPKBJ/+24/u32；2→KDKAEHEEMMJ/+18/→34045；5→LLHEBIHGDAE/+20/u32 |
| 49723 / `14C57CB20` | 1→EOFFDMLNFIM/+20/→89710；3→KNFDPJCMNOE/+18/[]→80154；4→GLHJPPFOGMB/+28/→34045；7→JNBCPNHJCNI/+38/u32；9→AGJIEAHJPJC/+3C/float；12→PKJBKFHECBE/+30/→72829，仅空 envelope |
| 73976 / `151475120` | 1→JPCOIFLAAED/+1C/u32；2→CIIBGHCBCGK/+24/u32；3→FOKGPCODGMF/+18/u32；4→IJKEMAINHOD/+28/u32；5→GAPJBCJONCA/+20/bool |
| 80154 / `149C79570` | 1→ANKEOLKBECN/+18/u32；2→GOKKMOKPCEE/+1C/float |
| 83320 / `152465040` | 1→DOBGMPBDLKF/+1C/u32；2→EMBPDEHFOJK/+20/**fixed32**；3→OPKFBNFODJM/+18/**fixed32** |
| 87468 / `14D233EA0` | 1→EMNNKLLNMBG/+1C/int32 enum86697；2→JFBIDBBFBPG/+18/u32 |
| 89710 / `152EF8E70` | 1→GFDEDOJEICE/+18/[]→83320 |

83320 是本卡关键差异：metadata 三槽都是 uint32，但 tag2/tag3 实际调用原生 `147521000`，分别在 `1524650C1/1524650E1` store；只有 tag1 调用 varint reader。手工 literal `08818080800815efcdab891dffffffff` 分别得到 `0x80000001 / 0x89abcdef / 0xffffffff`。交换成错误 varint 或给 tag1 fixed32 均由真实 dispatch 跳过，不能把三字段一律按旧 helper 或 u32 推定。

## 非空与原生执行验收

实际 1152 个扫描样本、40 个成功夹具通过。18295 的九字段夹具含非零 u32、`2^53+1/uint64 max`、packed/unpacked u32 列表、两个同形 singular 子对象、两条互不混淆的 message list，以及 e8 的完整 34 类非空复杂图；它实际进入 36 个 reader。34208 综合夹具包含两个18295实例、非空44512、49723五个已闭分支，实际进入42个 reader，其中复用复杂类34个。87468 作为独立叶子验证；没有借尚未闭合的72829声称它已从SceneInfo非空可达。

独立样本覆盖两个 u64 高位换位、同形对象分别合并、singular 重复合并、list 追加与空元素、重复显式零、wrong wire、负 int32 枚举、float 非零/负零/最小 subnormal/最大有限值。真实 `158DC4236` 对 message 数组写入两个不同83320元素，实际索引0/1；真实 `158DB8BC0` 逐个 Add `[0,1,0xffffffff,128]`。9个reader、nested limit、fixed32 reader、两个 concrete list reader 与 u32 Add 均不在成功报告的 modeledAddresses 中，未把 reader/store/Add 替换成模型成功。

托管 allocation、class/interface 初始化、primitive input/codec/float、boxing、固定容量64数组和底层 Array.Copy 仍是显式离线模型，不等于 live ABI。21 项拒绝分开记录：

- 12 项 primitive 输入模型错误：9类 invalid tag、u64 overflow、截断 packed u32、截断 fixed32。
- 5 项真实 native error entry：负长度、截断子消息，以及3种递归限制。
- 2 项范围拒绝：49723或34208包装下的非空72829，在实际72829 reader入口显式停止。
- 2 项夹具增长边界：64元素成功；第65个message元素在真实 `158DC42CA` 读取缺失增长 RGCTX 的 `+30`，u32增长到未建模的 allocator `140514EE0`。这些不是客户端64元素上限或服务端输入拒绝合同。

## 未闭合边界与失败记录

72829/+28 Object 和54267/+18 Object 擦除大量具体后代；本卡没有为二者安装获准 schema。49723/tag12空输入仅执行72829真正的空 reader 路径，结果显式标记 `untracedType=72829/content=empty-envelope-only`；所有非空输入拒绝。该空 envelope 仅证明外层字段，不批准其内容。54267及隐藏oneof后代仍未知，不能因旧静态11新增类型而全部推入P0或宣告完整SceneInfo。

首轮工具探针曾把复用e8摘要hash及stream类型写成未核预设，分别在 source/metadata guard 即拒绝，已从本轮真实文件修正；日志为 `local/migration72/logs/scene-info-raw-probe-{1,2}.log`。随后40夹具前身的38成功夹具已值比对，错误集第65个message list元素因增长rgctx缺失触发原始 Unicorn unmapped exception；补的是本工具的异常归属与明确fixture边界，没有补猜测增长ABI、替换原生指令或将它当客户端错误。原始 `scene-info-raw-run{1,2}.log` 留在忽略目录，后续原样重现该边界。完整原始报告及输入反汇编均不提交。

## 复现、双跑与出口

两个全新进程重新校验三输入并执行全流程，完整报告757,992字节、摘要589,469字节均逐字节一致：

- 完整 SHA-256：`74cd8618d3f502661a1dfd0df30abe3eb8bdc14dcb0945db4678a03f53e6a7ab`。
- 脱敏摘要 SHA-256：`f52f41a817241646d63d692b120d5c134c1b30dfe8b2da5d440de5d9557f7c75`，与新共享observations同字节。
- 完整/摘要分别在 `local/migration72/evidence/scene-info-raw-{native,summary}-final-{a,b}.json`，正式日志在 `logs/scene-info-raw-final-{a,b}.log`。
- 12项新增守卫 + 6项f2b场景身份 + 10项e8完整raw关联，**实际28项通过，失败/错误/跳过0**；它们不与原生fixture数量相加。

```powershell
$game = & tools/game_path.ps1
python tools/trace_client72_scene_info_raw.py --client $game --output local/migration72/evidence/scene-info-raw-native-replay.json --observations local/migration72/evidence/scene-info-raw-summary-replay.json
python -m unittest tools.test_client72_scene_info_raw tools.test_client72_scene_entry_identity tools.test_client72_avatar_complete_raw -v
```

主线已审核四新文件、显式模型/范围拒绝，独立28项及第三全新原生回放均通过，完整/摘要与上表双跑逐字节相同，四份source pin保持。证明为 `local/migration72/evidence/scene-info-raw-main-review.json`，独立输出 `evidence/scene-info-raw-main-{native,summary}.json`，日志 `logs/scene-info-raw-main-{tests,native}.log`。未改运行文件，故无需构建、重启或部署；没有客户端验收。`completeSceneInfoRawClosure/targetProtocolReady/fullNetworkHandler/fullSceneEntityEffects` 均保持false。

下一项先沿场景实际消费者确定必要分支、实体/GUID关联、初始化顺序和省略等待条件，再据证据限定72829/54267 Object后继的具体AOT、判别与非空wire任务；83320等业务名称也须独立来源。不把未闭合战斗后代全部当作入场必需，不用空外壳绕过未知内容。本卡不自动生成Proto、批准ready或接入生产。
