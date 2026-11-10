# E-01 / R3-PROUD-BASE-PARSER-CONTRACT（2026-11-11）

## 结论与范围

**已完成 base59500.method493918 的有界静态文件消费合同，仍不能交完整Proud纯文件reference。** 原base先读16位prefix，决定额外数组、字节块和一个静态数组parser；同key22101候选的prefix实际为`0x31`，在明确普通文件条件下，前缀消费使cursor由4到134，随后原`14d5e2ce3`必调尚无完整原体的52802.method439380 / `14ee1eb20`。它直接接收同reader，返回前的后续消费未知，不能在134解63831本体presence，更不能宣称正常终点334或消费330B。

唯一下一source规格已交root，未在本卡采集。原管理child`14f6f13c0`继续未批。group/level、81127+10门槛、Java公式、自然backend/mode、实际map填充、allocator/对象成功及父项均未批准。

本卡只接续已审[base原窗](CLIENT_72_D02_PROUD_BASE_PARSER_WINDOW_20261111.md)与[同key真实索引合同](CLIENT_72_D02_PROUD_ACTUAL_ROW_CONTRACT_20261111.md)，不重开mode/factory/index。先读AGENTS、README、Project、Harness、迁移/部署手册及相关旧合同，中文计划为私有`plan.zh.md`，2152B / SHA-256 `1aaba2db5336a2833c50cfa5b163b1184ea8fbca10a58364539a90b50ee497bc`。既有fixed GitHub成果直接复用，此单链小续项不新增检索或招募。

## 固定输入与逐原件审核

本卡没有打开当前PE、客户端五文件或新code/AOT/pdata/xdata/literal。五输入身份沿已审base窗口的完整哈希记录引用，不冒充本卡新哈希客户端。base两fresh八原件及主线第三逐byte复核：完整原body`[14d5e2680,14d5e2eb1)`、2097B/483条、SHA-256 `bfdb81e46bcd151507ccc2ae8d2c58e71c1922551be67b19f67a73b785958785`；主线第三71756B / SHA-256 `8bb6e07bd43a266e9d8ecd6e320d624d549120fa93eb3c41d49a2d313ffe9ba8`。cell/slot/pdata只读已存原件，不把旧P当新证据。

准确63831 caller仍为7674B原parser，SHA-256 `949868da171fd252ad9f8486d1a20bf5c211dbdc71065106db7775b6b18e682b`。它在`149f0c243 e838646d03`将同this、同42005及incoming allocator交base。此前同key合同主线第三6382B / SHA-256 `c49582289209087627a9af3b7ca23097c81aa1f8d89e20cddd162471e18b31a1`继续作为已验输入，不放宽旧pin。

私有`frozen-inputs.private.json`23492B / SHA-256 `188b2457d18849b3ed6bd27046b92cb66f16e72ee817d4bd5f97a54a7b6fbed9`，固定95项/213880276B及10份只读上下文快照。它记录每份输入的完整尺寸/SHA；metadata cache SHA-256 `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f`、signature原件SHA-256 `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe`在反序列化前核对。

两个新host Python进程分别重解码五个已存完整窗：base2097B、derived parser7674B、derived ctor418B、reader1438B、producer606B，共12233B/2811条；各窗两fresh八原件相同、连续机器码完整拼回、pdata边界与ordinaryAOT算术相符。base全部483条PC/bytes/operands、48个call、84个branch、20条显式reader内存引用、R8/R13使用清单及71个准确锚点均保留于新报告。没有重新研究已验ctor模式、factory或reader backend。

## 先区分表示、allocator与reader

| 原PC / bytes | 已证关系 | 保留边界 |
| --- | --- | --- |
| `14d5e26a3 4989d5`、`26a6 4989cc` | 保存R13=同reader、R12=同this；本body内R13没有第二次赋值。 | 未构造任何typed对象。 |
| `26a9 488b4920`至`26d5 48ffe0` | base+20非null时取该对象klass+f0、RDX=同reader、R8D=0，恢复栈后尾跳。26072声明slot4为单reader参数的method202237。 | 实际vtable目标未知，不能把metadata候选当实际callee；这里R8=0属于该单参数尾调用的第三寄存器，不命名为allocator成功。 |
| `26d8..274d` | base+20为null才继续；若this+8≤ff，先经klass+120取size，再以global+7510的原allocator调用148ba77b0，保留this+8低字节并原子更新高位。 | size/allocator返回、handle与wrapper有效性仍未验；不因incoming allocator0跳过。 |
| `2758`及后续同类分支 | reader+20非零时，分别以2、4、列表项4、长度2或解出的块长调用42005.method350685 /14f614760。 | refill/native分支行为未知，不能强置reader+20=0或宣称它不改变array/cursor。 |
| `27c2 e8290a82f9` | 将屏蔽低字节后的handle交11707.method83889 `QueryWrapperInternal`，局部接收两个QWORD。 | 没有reader实参，但返回和所有权未批准；未用host对象补成功。 |

derived+40=null只让63831进入base调用，**不蕴含base+20=null**：已验derived ctor对null或不兼容类型都会将+40写0。普通文件语法必须把base inline分支作为单独明确条件，不能由+40、INativeAllocator0或默认flags推出自然模式。

493918的incoming R8没有被本体保存或作为allocator读取。native-header分配使用global+7510；泛型数组构造也从同global取allocator。末尾52802静态parser的R8D0是本body另一次明确置零。它们与producer原`14d7ad9f3`清R8相互独立，不建立任何分配返回/对象成功。

## base的文件语法与第一未闭边

以下只解释本body inline路径的读取；它不是原生执行、实际typed reader或自然分支观察。prefix高位没有在本body另获业务名称；三个位只按原mask表达。

| 阶段 | 原PC、bytes和操作数 | 文件与cursor关系 |
| --- | --- | --- |
| prefix | `2789 0fb77c1020`读word；`278e 83c102`；`2791 41894d1c`；`2795 81f7276c0000` | 从当前cursor取LE16，cursor加2，`h = raw xor 6c27`。这是base prefix，不是63831本体presence。 |
| mask20 | `27da 40f6c720`；`2832 41beff273ab6`、`2838 4403741020`；`283d 41894d1c` | 置位才读LE32，cursor加4，`n=(raw+b63a27ff) mod 2^32`。零走原空/默认对象来源；非零走原分配。任意高位count的完整行为没有由当前样本批准。 |
| n项 | `28e4 8b441020`；`28e8 4431f8`，R15D=`f4a5719b`；`28ef 660f6ec8`；`28fd` call14f905250；`290e` call159241080 | 每项读4B、cursor加4；xor后的32位放XMM1，typed声明是float，再交原存储setter。field295066/+18声明`NativeClassArray_NativeStruct<54724>`，不命名业务字段。原float叶历史报告仅复用其163B代码身份hash，未伪称本卡取得叶原bytes或执行存储编码。 |
| mask01 | `2922 f6450001`；`29ce 440fb77c1020`；`29d7 41894d1c`；`29db 4981f715790000` | 置位才读LE16并加2，块长`L=raw xor 7915`；L=0走原空字符串来源，非零消费后续L字节。 |
| L字节块 | `2b70`常量`9a168e269ee37915`；`2ba3 4801f0`及展开循环；`2c28` memcpy原边；`2c8a 45017d1c` | 每个完整8B小端组独立加该64位常量、mod2^64；余数先复制到零QWORD再相加。原NativeCString Builder/Intern处理后cursor加L。这里只说明精确byte变换/推进，不批准返回String/NativeCString、编码内容或所有权。 |
| mask10 | `2cc6 f6450010`、`2cca 0f84e2fdffff`；`2cd0 4983c410`；`2cda 4c89e9`、`2cdd 4c89e2`、`2ce0 4531c0`、`2ce3 e838be8301` | 置位将同reader、byref同this+10、INativeAllocator0交52802.method439380 /14ee1eb20。未置位才把原默认来源写field295065/+10。**该call是当前文件消费首未闭边。** |

没有inline指令替换R13的reader指针或直接写reader+10数组；本体显式reader存储只有+1c的推进。但reader+20分支的未审helper和末尾数组parser均接收同reader，不能据本窗“没有inline替换”断言它们不替换array/范围/cursor。

完整原native运行的首未闭条件与这个文件消费首缺分开：base+20非空时可能最先停未知virtual目标；inline路径又可能先停virtual size、allocator或QueryWrapper。文件reference不执行这些调用，也不把它们模型成成功。14ee1eb20是下一应取得的**文件语法来源**，并不意味着更早运行依赖已经闭合。

## 同key候选的有界前缀核对

完整主表2132778B / SHA-256 `82a8017c6c2868e41e3ce8fc02f8bc04437cb641ec653ed3383c5cc109e25835`；既有真实索引同key22101的A=4、B=330，候选`[4,334)`330B / SHA-256 `cfe85822a6606b824d731b5bb7ba442c8d0d4113187294de5457e3a1e04c8a6b`再次核对。缓存42005 backend不消费B，沿用完整bytes+cursor4的文件抽象，物理end始终2132778；334仅是外部候选对照。

本轮只按刚闭合的base读取规格核必要头、长度和span，没有猜解字段：

| 绝对span | 原bytes / 已证运算 | 条件结果 |
| --- | --- | --- |
| `[4,6)` | `166c`，LE16 xor6c27 | h=31，mask20/01/10均置位；没有选默认prefix0。 |
| `[6,10)` | `1ad8c549`，LE32+b63a27ff，mod2^32 | n=25；25项的原4B span为`[10,110)`，没有发布其业务名/值。 |
| `[110,112)` | `0379`，LE16 xor7915 | L=22；块span为`[112,134)`。 |
| `134` | 原mask10进入上述E8 | 只是在普通文件条件及前置call正常返回下的nested输入cursor，未观察实际cursor；候选余量200B、物理余量2132644B。 |

在未知数组parser返回前，不能确定base最终cursor，也不能在134读derived header。derived后续首inline DWORD仍是`149f0c2eb`，其cursor只能来自base真实返回状态。已验63831其余body和旧codec/资源资料继续保留，尚不触发完整reference实现、typed对象或helper扩树。

原错误边也不合并为文件reference的通用EOF：各inline读取只先比较起始cursor与array长度，然后发出2/4/8B原load；本窗没有统一的`cursor+width<=length`或`A+B`检查。reader/array null与起始下标越界分别到原1404c2be0/1404c2650；清理还有1404c2bd0等未执行原边。物理全宽越界、外部候选334越界和原错误分支须分别记录，不将host严格切片或异常当原执行结论。本次必要头均在两种边界内，没有执行任何错误helper。

## 唯一下一source与实际交付

私有`next-native-struct-array-source-request.zh.md`5528B / SHA-256 `23420dfa373c10d4f4a9998cf7511bd3e0dfdf3bc7fa8e7f37938330b804188f`给出原E8、七条setup/branch、typed三参数、完整caller SHA和门禁。metadata唯一声明为owner52802/method439380、flags0x93；第二参数是byref `NativeClassArray_NativeStruct<52802>`，与base.field295065/+10同实例，返回类型未知。ordinaryAOT建议只新取cell1422f9950与所得P+439380×8；旧P下142bff770仅提示。建议完整body≤8192B、连续≤2048条，先唯一exact/containing pdata再code，两fresh与主线第三；xdata/邻body/执行/扩树均0，须root另批。本卡精确地址查询只发现当前caller和声明，没有找到可复用完整callee原体，不主张其它未审载体不存在。

私有工具`verify-contract.private.py`21248B / SHA-256 `521dad9b1be836cfeef51f4aa026829f714df8d1af51c39bdac1e4f99a450f37`。实际两个新进程分别退出0：

```powershell
python -B local/migration72/d02-resume-20261110b/c-proud-base-parser-contract/verify-contract.private.py audit fresh1.private.json
python -B local/migration72/d02-resume-20261110b/c-proud-base-parser-contract/verify-contract.private.py audit fresh2.private.json
```

两报告各100731B、逐byte相同，SHA-256 `2814615c93d8ebfafdd20c64832012c5f623dadcaafcdadb37aa2c2060b552f2`。复现须用此前不存在的新basename，工具以排他创建拒绝覆盖。这是完整原bytes/边界/声明和前缀算术静态审核，不是原parser、边界执行测试或全部旧资源审计重跑。一次metadata查询误用不存在的typeIndex键、一次旧单行报告输出未限制列宽，以及一次PowerShell显示组合未给出hash，均只读纠正，没有修改原件或补目标成功；无安全/Daybreak事件。

状态保持`targetCPU=0`、`nativeParserExecuted=false`、`naturalBackendProven=false`、`naturalModeObserved=false`、`actualIndexFilled=false`、`actualRowSpanApproved=false`，本体presence起点仍未知。客户端验收增量0，无新PE/code/pdata/AOT/literal/CPU/emulator/live/REA、进程/网络、构建、DB或Git；只有本文与指定私有目录写入，无需部署/重启。Project/Harness、交接和本地提交由root统一处理。本max交回成熟单source规格与全部写权，不等待未来现场或自行取下一窗。
