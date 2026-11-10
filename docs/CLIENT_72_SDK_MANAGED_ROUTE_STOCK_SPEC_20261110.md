# R2：managed env / comboLogin 的最小 stock 输入规格

**下一步选择 stock 只读当前值图，与 A 的必要新 stock epoch 串行合采；不新增 SetEnv/GetURL hook。** 它可以补齐当前 `ConfigModel.env` 与 `URLConfig.Instance.comboLogin` 的有界输入候选，区分未就绪、类型不符、空值、未知值和已审 CN base route；它不能证明 SetEnv/GetURL 曾执行、实际选择/返回、40108/PostRequest 采用或认证。函数采用仍按[准确消费者合同](CLIENT_72_SDK_ROUTE_CONSUMER_SUCCESSOR_20261110.md)另验。

本页与独立 `local/migration72/d02-resume-20261110/b-managed-route-stock/` 只交可实施规格及冻结来源审核。没有新 collector/backend/hook、目标 CPU、Windows API、GAME_PATH/当前 PE、网络、Git、构建/DB/现场操作。旧 consumer/observer/spec/report、A helper及共享索引全部只读。明确实现交 Sol xhigh/Terra，max 到此释放；规格通过不构成新实时 ready 或现场许可。

## 已验起点与为什么选这个后继

主线新五点自然轮最终16项原件审核、同次 raw replay、stock恢复和保护/进程端口末盘全部通过。proof `b-route-implementation/helper-preparation/main-live1-acceptance.private.json` SHA `ff65adf58a0dfea4ece85fa6a468748b89f4812aaecfd1bd2fd14b01e592b09f`，post库存 SHA `45a94c3d2111ce0528a7b6f0b5249c3aa44a2ea9a9ab9fdf4304e30855783fd1`。所选原 writer 锁内 stored-map 类别为 `cn-route-env-0`；proof仍明确 `naturalComboRouteUseObserved=false`、`naturalInitOrRoutePassed=false`。本页只复核这份冻结接受文件，没有重新采 raw或现场。

固定328B的 `RequestComboLogin(40108)` 已证明真正的这一条条件成功账号路径读取 **managed** `URLConfig.Instance+E0`，加编译期单个 `?` 后交 managed `NetworkManager.PostRequest(33107)`。SDK native map不在该路径中。40108需要原成功 AccountModel/票据分支，R4/R5及可信本服 route尚未齐，不用官方凭据、伪 ret0/空账号换取它的自然命中。

SetEnv/GetURL已有准确代码和字段边，但没有已证零凭据可控动作、当前自然 caller与新 hook 的完整寿命/异常合同。为取得当前输入而再装 hook会加入这些未闭前置。只读图复用已验原 stock 创建身份、明确的静态根和字段，所有准备与拒绝验证均可离线完成；没有必要先扩大 SDK 页观察预算或 UI 注册链。旧 GitHub 定向分支覆盖直接复用，本小后继没有新库/分支目标。

## 准确源图、类型与字段来源

令 B 为新 epoch 的实际 EXE base，`S = QWORD[B+586B600]`。S是原代码读取的共同静态存储 pointer，不按 managed object或 class 解释它的首QWORD。唯一图为：

```text
B+586B600 ──> S ── +C18 ──> C:ConfigModel ── +58/int32 ──> 当前env
               └─ +B80 ──> U:URL ── +E0/QWORD ──> T:String ── +10/int32、+14/UTF16
```

对象身份使用**当轮准确 type-usage cell 的原值**作独立参照。不得借旧 String object、旧 heap、ordinary1243、SDK Config或 std::string 头；不读任意 class+10/GenericClass/父链来猜这个新对象的类型。

| 类型参照（均读8B） | 固定 metadata usage join |
| --- | --- |
| `KC = QWORD[B+5887680]` | row699031 / sourceIndex457342，definition4128 `MiHoYo.SDK.ConfigModel` |
| `KU = QWORD[B+5893D50]` | row701475 / sourceIndex457870，definition4313 `URL`，空namespace；父definition4137 `MiHoYo.SDK.ConfigUrlModel` |
| `KT = QWORD[B+5886040]` | row698582 / sourceIndex33，String typeReference，definition293 `System.String`；kind/string声明不是一份已观察 runtime class header |

这三项来自已有117201003B、完整 pin 的 `d02-resume-20261108/r3-equipment/metadata.private.pkl`（89748 records/117430 type cells）。本审计先核 hash，并确认无 GLOBAL/REDUCE/BUILD/NEWOBJ 等执行型pickle opcode，才读其纯数据。sourceIndex、usage row和runtime pointer各自分列，不把前两者当地址。

ConfigModel type-usage 还由原 `SetEnv:159777075` 的 RIP load独立落到 `145887680`；URL type-usage由原 `GetURL:1599AE7BE` 的 allocation参数load独立落到 `145893D50`。原 String Dictionary消费者 `158B48CBC:488B01` 从 String参数首QWORD直接取class，继而 `158B48CCE:FF90E0000000` 经其虚表调用；这里没有 XOR、减基址或 tag mask。新图只要求 `QWORD[C]==KC`、`QWORD[U]==KU`、`QWORD[T]==KT`，原64位值精确相等；参照为0、对象为0或不同即停，不调用 resolver、不去掉低位、不补类。

字段的实际读取根据固定代码和独立声明共同限定：

| 字段 | 来源与可读上限 |
| --- | --- |
| `S+C18` | field19422 `ConfigModel.Instance` 的 raw offset为 `4000C18`；原 LocalConfigSync等直接读该 static槽。不能靠屏蔽高位发明运行偏移 |
| `C+58` | field19433 `env`，类型4316 `EnvType`；其value__声明为int32。原 `SetEnv:197770A0` 直接store DWORD；`LocalConfigSync:19807756` 直接load DWORD，无字段解码 |
| `S+B80` | field20504 `URLConfig.Instance`，原 `GetURL:199AE7EF` store；失败分支 `199AE80F` 返回此前实例。因此当前值不等于本次刷新已成功 |
| `U+E0` | URL自身无实例字段；继承的 field19544 `comboLogin:string` 在 ConfigUrlModel实例偏移E0；新原40108 `19801282` 直接load，未读native `+860` |
| `T+10` | String293 field1211 `m_stringLength:int32` instance offset10；原 EqualsHelper2908 `159AAC336:448B4910` 直接读取长度 |
| `T+14` | String293 field1212 `m_firstChar:char` offset14；原 helper的 `159AAC33A/33E` 分别加14，原 String hash叶直接以2B读取字符。按准确UTF-16 LE code unit比较，无字符解密 |

String布局来自这个固定客户端的声明及原指令，不是标准 .NET 布局假设。旧 String owned case**未提供对象头/class**，所以不能拿它们直接证明新T的身份；本图另取fresh KT并核T首QWORD，才按上述已证字段读内容。旧 helper奇数尾部曾读显式owned NUL，但自然分配器终止存储仍未知；本图不需要也不读取尾NUL、monitor字段或页填充。

## 唯一首遍与精确 final

每个入口一次。所有地址加法在单一读取入口检查完整uint64范围及本次width；读取失败计已请求width，不截低32位、不重试。类型和字段只在将要进入下一层时验证一次，不套额外 class warm flag或未知 descriptor kind/data。

| 序号 | 读数 | bytes / 成功后动作 |
| --- | --- | --- |
| 1 | `B+586B600` | 8，S；0则not-ready停止 |
| 2 | `B+5887680` | 8，KC；0停止 |
| 3 | `S+C18` | 8，C；0停止 |
| 4 | `C+0` | 8，须等KC |
| 5 | `C+58` | 4，实际int32 env；不是零初始化推断 |
| 6 | `B+5893D50` | 8，KU；0停止 |
| 7 | `S+B80` | 8，U；0停止 |
| 8 | `U+0` | 8，须等KU |
| 9 | `U+E0` | 8，T；0为absent终态，不追String |
| 10 | `B+5886040` | 8，KT；0停止 |
| 11 | `T+0` | 8，须等KT |
| 12 | `T+10` | 4，带符号长度L；负数invalid，0为empty，正数不在64..69为unknown、不读字符 |
| 13 | `T+14` | 只在L属于64..69时读准确 `2*L`；最多138B。精确白名单不匹配为unknown，不追加读取 |

前12项合88B，第13项最多138B：**首遍13读/226B，final至多13读/226B，整轮26读/452B，单读138B**。null/unknown/error按实际前缀提前结束，不为命中而换对象、换线程、读新root或放大长度。

final按成功首读的原序号，复读**原地址、原width**，不按新pointer重新走图。完整但为0、类型不符或未知长度的首读也属于原已成功span；发生short/error的那一次不进入final，避免把final做成失败读取的重试。首遍第一次失败后不扩图；final自己的第一次变化/错误也立即停止剩余final。最多一次final，不跨阶段reset限额。

first/final相同只是同两次读取所得副本一致，**不是原子快照、GC pin、writer排除或无地址复用证明**。本工具不在目标线程中执行，也不要求GC不移动；变化/回收可能导致RPM失败或不一致，按原失败处理。即使两遍相同，ABA/期间对象变化未被排除，因此只发布 `currentStateCandidateOnly=true/nonAtomic=true`，不能给target CPU复放或写域补充对象所有权许可。

## 分类与隐私、能独立复验什么

env只将实际读出的已审 CN 数字 `0,1,4,6,8,9,22,19` 投影到资源行类别；其他int32保持unknown，不用尚未恢复的enum literal数值给其套PROD等符号。String只与已审八个 `https://固定CN host/hk4e_cn/combo/granter/login/v2/login` 的**完整UTF-16原字节**比较，其长度集合恰为64..69 units。无需 UTF-16 decoder、URL parser、大小写/Unicode/percent规范化。内嵌NUL、额外slash/query/`?`、不同大小写或任一非法/未知unit只是不匹配。

这里读的是存储的base字段。40108以后原 `String.Concat` 才加单个 `?`；collector既不加它，也不剥已有的`?`来制造匹配。`currentEnvAndBaseRouteAgree`只比较这次两个已知类别，允许真实不一致，不要求二者一致来放宽结构门禁；一致也不证明同一次SetEnv/LoadLocalConfig选择或PostRequest采用。

String相关第9/11/12/13项的原pointer、地址、长度、width、bytesRead、raw bytes及累计敏感byte数**先仅留内存**，不流式写JSONL/异常repr，不hash自然内容。输出分两种合同：

- **精确命中公开CN base且B整次first/final全部成功不变：** 可在忽略的raw原件保留这些实际完整已白名单化读数，summary仍只类别。主线从原读bytes独立复核全部指针/类型、长度、精确字符匹配、顺序/预算及末尾相等；不能从reference生成“raw”补齐证据。identity/source末核失败仍整体partial，不称自然候选。
- **unknown/empty/absent、任意变化、short/error、类型或身份失败：** 上述敏感项只保存固定ordinal/name/phase/终态或固定错误/Win32码，不导出地址/指针值、width/bytesRead、原长度、字符、内容hash或敏感byte总量；报告硬上限、实际请求次数及非敏感结构byte数。保留其他非敏感原结构读取供结构重放。这是有效partial，**不能声称未知字符可作完整原字节重放、route ready或已采用**。

特别是“first已知，final改为未知/失败”也必须走后一条，不能先把暂定已知原件写盘再补partial。若implementation不容易保证这一点，应整次B在内存结束后一次封存，不把既有A全raw Ledger直接套到String输入。

本节partial指B自己的图/身份/source状态；A仅graph partial不导致一个完整有效的B结果丢失其独立白名单原件。adapter必须在B身份/source末核之后才封存，失败时将尚在内存的敏感项统一脱敏，不提前写盘。

未知结果的独立准入证据是：严格source/冻结表门禁、离线人工读回调的真实序列/失败/脱敏测试、主线独立实现审核、自然结构原读及redaction receipt/固定负终态；**它不是未知URL内容的独立复原证据**。已知结果才有实际白名单原字节完整重放。这与旧SDK inner不导出map bytes、外collector只重放code/DATA的范围不同；旧observer和其输出白名单不改。

## 与 A stock epoch 合采的明确边界

与当前正常stock launch-primary物理兼容，但必须使用一个**新合并计划和新私有 coordinator**，A现有helper/adapter/四profile输出不改，不把B回调偷偷塞回旧冻结monitor。顺序固定：

1. 主线先新核profile、五stock/carrier、13保护、portable/config与缺席alias、source闭包及进程端口；新CheckOnly全部离线门禁通过，再为这一次合采取得新实时ready。旧SDK ready/原件不复用。
2. 正常 CreateProcessW一次、flags0/无额外参数；原ModuleBackend仅45次/90秒metadata，保持不枚举/query线程的范围。先执行A第四profile一次、A自己的final和线程后验；A adapter关闭原primary一次。
3. A `endIdentityVerified`和`primaryCloseVerified`均真，且原process handle仍在外层finally前保持打开，才进入B独立identity-before、一次首遍、B自己的final、identity-after。B不需要primary线程已存活，不把它认作SDK执行线程；B不额外OpenProcess/OpenThread/NT query。
4. 原process PID/image/创建FILETIME/唯一EXE path/base/SizeOfImage、相同runId/五stock pins在B前后完全对应；最后alive及输入末核按新helper整体收尾。只有外层关闭原process一次。B自己不关闭process或primary，异常也不能越权cleanup。

A仅采集图partial、但其身份后验/primary关闭成功时，可以独立采B一次；保留两个子结果，overall仍partial。**A身份后验不成立、primary cleanup失败、B新identity-before不符，或stock/source/ready gate不符时，B必须0 RPM**。不能把A graph partial都当作epoch失效，也不能把cleanup失败忽略掉继续B。

A_epoch的 `implementationPins` 必须保持原五tool精确映射，A入口对此作等值校验；不要往里面追加B source来强改guard。B_epoch单独绑定新collector/adapter闭包，二者共享实际创建身份及runId。新外层计划可绑定两个闭包的并集。A和B各自final、各自结果，不跨ledger去重或把A较早final称成B之后的统一final，也不称整个AB原子快照。

| 独立账 | 首遍最大 | 含各自final最大 | 单读 |
| --- | --- | --- | --- |
| A现有第四profile | 36/926B | 72/1852B | 208B |
| B本规格 | 13/226B | 26/452B | 138B |
| 同一次stock创建合计 | **49/1152B** | **98/2304B** | **208B** |

这些是RPM请求账，包括失败已请求width；不是全部磁盘/模块/Windows API访问数。B pure collector为0 API；新B adapter只新增前后两个既有process identity callback，无新增thread API、OpenProcess、process/primary CloseHandle。每个identity callback按现有源码有process WaitForSingleObject、GetExitCodeProcess、QueryFullProcessImageNameW、GetProcessTimes各1，以及模块snapshot的Create/First/Close各1和实际M次Module32NextW。B合计为上述七类各2及 `M_before+M_after` 次Next；即成功路径 `14+M_before+M_after` 次native API，不将“2 callback”冒充2 API。

原metadata monitor/A身份回调/launch/primary查询/末盘及cleanup继续分别记实际次数。现有ModuleBackend枚举到OS结束，没有固定M硬界；本规格准确继承这一范围，**不声称所有native API总数已有固定硬上限**，也不私改旧backend增加猜测复杂度。若主线另要求全native API硬界，应独立审核metadata backend，不能拿本RPM上限代替它。

本轮没有DLL或文件安装。采集完只由主线请用户正常退出，核退出、五stock/carrier/13保护/配置及端口；关闭本次owned句柄，不强杀、不用备用thread、不操作其他客户端/原库/数据。若A集成/source预算不满足，则分开准备B，不通过临时修改A的source gate/ready/required来合并。

## 明确实施清单与验收出口

可立即交 Sol xhigh：新增 `tools/client72_sdk_managed_route_inputs.py`、`tools/observe_client72_sdk_managed_route_inputs.py` 和各自test；新的合并私有coordinator/计划由主线分配。只复用已知一般Win64/RPM/创建身份合同，不需要新 target ABI/GC成功模型或native owned线程实验。已有固定String字段和type-usage足以做**外部非原子当前输入候选**；要做原SetEnv/GetURL返回或PostRequest采用观察、target代码执行/GC pin，则是另一个明确任务，不能从本图升级。

完整机器可读步骤、隐私、预算、停止和A组合谓词在本私有根 `implementation-spec.private.json`。实施至少验证：

- 人工稀疏读回调使用高64位独立地址；所有8个CN base、env0非默认推断、当前env/URL类别不同、null/未知/负数长度、63/64/69/70边界、内嵌NUL/多余问号/未知UTF16均按上述出口；它们只是collector人工数据，不是目标托管对象或native执行。
- 每个首/final读取ordinal分别short/error，首错不扩图、失败不在final重试；final root/class/env/pointer/length/字符变化停止，不跟新pointer。unknown/partial完整输出不含敏感原件/长度/hash，尤其先known后final未知。
- 已知完整raw必须来自实际读回调，主线独立纯重放全部字段和分类；未知只做结构重放/固定负例，不伪造丢失内容。重复读变更、no-read前置拒绝、uint64溢出及预算边界确实拒绝。
- adapter和新coordinator人工正常/异常生命周期核：A身份/primaryClose失败→B0 RPM；A graph partial且身份有效→允许一次B但overall partial；B身份/退出失败、source漂移、最终alive、外层process/primary cleanup准确一次；旧A输出/预算保持，两个implementationPins不串位。
- 所有source、静态输入、实际测试和独立第三、真实stock文件门禁/CheckOnly封SHA后，才允许主线提出合采新ready。当前没有上述实现/人工collector测试/第三/现场准入结果。

## 本工位实际静态审核与输入身份

只运行 stdlib `audit_source.private.py`。两个fresh进程各68项退出0，`audit-first.private.json` 与 `audit-repeat.private.json` 逐字节相同，均为13884B / SHA `5cc9a55d521174813f21b9c8e21655d9b36e36ee3a0c917f323af4bf47f7c532`。核21个完整pin、纯数据metadata/type-cell精确join、字段/继承、原328B/177B及String class读取原opcode、env/raw字段、13项图和公用长度集合、隐私分支、A实际profile/cleanup/门禁接口与合计预算。只在内存将首个预期pin改为全0的负例确实拒绝；原输入未改。第二进程实际命令、退出码及stdout/stderr在 `repeat.execution.private.json`，SHA `ee6cc4e1a611487c290d21a24e1240bfb312acfaa84f808724a5843fb6843db6`；一致性与负例在 `determinism.private.json` 和 `negative.private.json`。没有import tools/ctypes/backend、重开PE、运行collector或目标。这个68是源规格静态断言数，不是新collector测试数，也不是独立第三或现场准入。

完整输入清单 `frozen-inputs.private.json` SHA `cb5e66330892e6a081f94545df82ed5697f2966c06d69bb7a0bf4d9f83808b5c`，含21冻结输入/20读取上下文；Project/Harness等正在由主线更新，只记录当时context SHA，不拿旧SHA充当前gate。核心固定身份如下，其余每份源的完整SHA在清单：

| 输入 | SHA-256 |
| --- | --- |
| 旧consumer专题，保持原件 | `15a04a74a0af17dafdaaf4b34bbc3eaac57cf5e644d49b8016658611b24be66b` |
| EXE（既有固定输入身份；本审计未重开） | `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| global metadata（同上） | `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a` |
| startup metadata（同上） | `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105` |
| SDK（同上） | `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25` |
| stock Astrolabe（同上） | `87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442` |
| metadata.private.pkl | `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f` |
| 原route/report，含GetURL窗口 | `bae776a1a43c678b2953e69d71b6f198f0418cb966f275b0f8a9b8200bee1471` |
| 原SetEnv/explore窗口 | `8871dd865a9be44f06a2950a8bce9e23ba150d60b9900a731599ace7a280a247` |
| 原LocalConfigSync/sync窗口 | `4722fea419f51cb985648ccf0ff5f318c3a32b489c49c25ad8d07c2b25c1a9d1` |
| 原40108 code328B | `2fdf917e9fa54a1bf1db172fa32fec4c8c212a404ee57d79e5d8023884bc05ac` |
| 原String helper manifest | `a6b2d2073afea9835f328d45f24ba697da3f29b90a78c3225b6fd69c4049057a` |
| 原String helper code177B | `435647e6a8b42388fe9f94a375c6822b6c2958859b0586b2a91cf851eea5b5fd` |
| 原String object-header caller报告 | `10085ff137a6d918a43adaa2cf27754e4b5c638c9de12c3119705852e65e5abd` |

本页完成明确stock输入规格，客户端验收增量0、无本地Git提交。主线已有SDK map及旧consumer独立验收直接复用；下一可执行项就是上述小collector/adapter和新私有协调器的离线实现与门禁。current-state与自然采用的缺口持续保留，完整R2/D-02不晋级。
