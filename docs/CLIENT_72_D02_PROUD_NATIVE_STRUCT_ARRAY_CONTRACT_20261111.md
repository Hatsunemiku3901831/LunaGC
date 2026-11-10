# E-01 / R3-PROUD-NATIVE-STRUCT-ARRAY-CONTRACT（2026-11-11）

## 结论与范围

**已闭合329B数组parser本体的有界静态文件合同；同key22101的新count为2，首元素输入cursor138，元素语法和base最终cursor仍未闭。** 原 `52802.method439380 / 14ee1eb20` 先读DWORD并xor `7aef1999`，零值写原Empty来源，非零分配/构造数组并循环调用同reader的实例方法 `52802.method439383 / 14ee1ea00`。不能把局部QWORD当成8B wire、直接跳到63831 presence，或宣布候选end334已批准。

本卡只接续已审[base合同](CLIENT_72_D02_PROUD_BASE_PARSER_CONTRACT_20261111.md)、[同key真实输入合同](CLIENT_72_D02_PROUD_ACTUAL_ROW_CONTRACT_20261111.md)和[唯一数组原窗](CLIENT_72_D02_PROUD_NATIVE_STRUCT_ARRAY_WINDOW_20261111.md)。AGENTS/README/Project/Harness、迁移/部署手册及三份合同已先读；既有固定GitHub来源直接复用，这是同一必经消费链的小续项，不新增检索或招募。中文计划私有 `plan.zh.md` 为2511B，SHA-256 `7f15f01a76e8578fe26d84645d42fa1faae1eb8525f586fd2810826b9255b4cc`。

## 原件与准确对象关系

主线已接受的工具SHA-256 `8b76dfb4496eb4857e425e8133353d65c6f68ae6c8723eafbb9e3a7ea3f9e65f`、两fresh八原件及第三16984B / SHA-256 `16b255d425747d86cffdf941472e04c48b32ff9edec15fea5b63e8f90b88ecaf`保持。完整数组原body `[14ee1eb20,14ee1ec69)` 为329B/88条，SHA-256 `de050d512bbd5446892a4b68e226f17af504607d6617a18ee2f1675699b89573`。本卡只读已存cell/slot/pdata/code，不把已存ordinaryAOT的P=`1428a54d0`当成新读。

原base2097B/483条、SHA-256 `bfdb81e46bcd151507ccc2ae8d2c58e71c1922551be67b19f67a73b785958785`继续独立重解码；原 `14d5e2ce3 e838be8301` 与七条setup确认：RCX=同reader、RDX=同this+10的byref输出、R8D=0。新body的 `eb2d/eb30/eb33` 分别保存R14=allocator、R15=输出地址、RDI=reader。除epilogue恢复外RDI未被另赋值；本body显式reader内存操作只涉及+20/+10/+1c，只有+1c写入，没有inline替换+10数组。

固定metadata/signature在反序列化前核SHA；数组静态声明仍为owner52802/method439380、flags0x93、三参数：class42005、byref `NativeClassArray_NativeStruct<52802>`（11506/instance11802）、class11476 INativeAllocator。输出是数组容器，不能混成元素值或管理表示41105。

## header、allocator、默认与循环合同

下列均是原指令的条件合同，没有执行原代码或构造typed对象。完整原运行更早仍可能停在未验global初始化、allocator、分配或构造；文件消费首缺则是元素reader，两种边界分开。

| 阶段 / 原PC | 原操作与已证合同 | 未闭边界 |
| --- | --- | --- |
| allocator `eb36..eb56` | incoming R8非零直接保留；为0则检查原NativeDataUtils类初始化，经global `14586b600` 的+7510取R14。必要初始化call为140525e00。 | 回退发生在header及零count分支之前；R8=0不表示跳过初始化/分配或成功。global实际值与allocator返回均未验。 |
| native/refill `eb66..eb75` | reader+20非零时，以同reader、EDX4 call14f614760，再重读+10/+1c。 | helper可影响同reader；本卡不置+20、不模拟其返回。普通inline语法须明确+20为0的条件。 |
| header `eb7a..eb9e` | +10数组非null，MOVSXD取+1c；原unsigned起始下标比较通过后，`eb94 8b5c0820`读LE32，`eb98/eb9b`将cursor加4写回；`eb9e 4881f39919ef7a`得 `n = rawU32 xor 7aef1999`。MOV EBX先零扩展，故RBX为U32范围。 | cursor推进是EAX低32位运算；本体没有`cursor+4<=length`、候选A+B或溢出守卫。 |
| 零count `ebac→ec17..ec27` | XOR的ZF跨MOV保持，n=0才走此分支；若需先初始化数组类，再取`klass+38`静态域块的第0槽写`[R15]`，随后返回，不进入元素循环。 | 固定usage映射为11506实例11802 `<52802>`；该类型唯一static field55097/offset0声明名为Empty。只闭合原Empty来源，实际值/对象/初始化成功未知。它与base mask10未置位的默认路线不是同一次文件读取。 |
| 非零 `ebae..ebd1` | 原1405336c0返回RAX须非null；保留到R12，以RCX=该对象、EDX=EBX、R8=原allocator、R9D0 call155fb6570；正常返回后将R12写`[R15]`。 | 不执行或推定两个callee成功；输出在循环前写入，不是完整数组处理成功的提交标记。 |
| 每元素 `ebd4..ebef` | R14改成`&[rsp+20]`；每次先清零该QWORD，RCX=该局部receiver、RDX=同reader，原E8 `e80cfeffff`到14ee1ea00。 | 临时量8B不是wire宽度证据。callee是实例声明439383/flags0x83、一个class42005参数，返回类型unknown；没有声明allocator参数。成员wire、默认和消费仍未知。 |
| 存储及回环 `ebf4..ec13` | callee正常返回后重新取`[R15]`，非null才以RCX=输出数组、EDX=ESI、R8=局部QWORD、R9D0 call155fb6be0；ESI加1、MOVSXD到RAX，再signed `JL`比较RBX。 | 只闭合调用关系，不批准setter实现、编码/所有权或数组内容。n在1..7fffffff且各call正常返回时，循环从i=0运行n次；高位count还受未审构造callee约束。 |

对高位count不能把signed循环改写成通用U32有限循环：ESI增至80000000后MOVSXD成为负数，而RBX仍是正的U32。八项host纯算术边界核对覆盖1、2、7fffffff、80000000、ffffffff及回绕；未执行巨量循环，更未证明原构造会接受高位count。本卡不新增防御实现或将未知输入强制成功。

本body原错误入口分别为：reader null→`ec3d`、array null→`ec42`、起始下标unsigned越界→`ec47`（1404c2650）、分配结果null→`ec4c`、元素call后当前输出数组null→`ec38`；其余四条转1404c2be0。输出byref地址自身没有inline null guard。原初始化helper与错误helper均未执行，不合并成统一EOF，也不以host异常代原分支。

## 同key的必要header与唯一文件首缺

完整主表仍为2132778B，SHA-256 `82a8017c6c2868e41e3ce8fc02f8bc04437cb641ec653ed3383c5cc109e25835`；候选 `[4,334)` 330B / SHA-256 `cfe85822a6606b824d731b5bb7ba442c8d0d4113187294de5457e3a1e04c8a6b`只作外部对照。缓存backend不消费B，文件物理end不是334。

在base+20=null、同reader+20=0的明确普通inline前提及先前调用正常返回下，已审prefix31、25项4B及22B块使输入cursor134；本卡只新增必要的数组DWORD观察：

| 绝对span | 原值与变换 | 条件结果 |
| --- | --- | --- |
| `[134,138)` | `9b19ef7a`，LE32=2062490011，xor7aef1999 | count2，cursor写138，走非零分支。 |
| 首元素call前 | 同42005 reader；RCX=&每次清零局部，RDX=同reader | 首次14ee1ea00输入cursor138。候选余量196B、物理余量2132640B。 |
| 元素call返回后 | 尚无可重解码原体 | 每元素跨度、第二元素输入、base返回cursor和63831 presence起点均unknown。 |

这些条件不由derived+40=null、INativeAllocator0或候选索引值推出自然模式。本轮没有在134/138读derived header、解成员值或宣布count2意味着后续固定16B。

## 既有source复用与下一项

准确本地检索找到了旧Promote first/second/main-third三个相同3297024B报告，SHA-256 `546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1`。它们对14ee1ea00只保留`[14ee1ea00,14ee1eb1d)` / 285B / SHA-256 `8f64b2b9c0020c75eff11786e33d7e257d9925a08b83b8b1975de0b9524c5247`及历史PC计数，没有原bytes或逐指令文本。旧bind_function写端也只存end/hash/RIP references。该摘要可以约束下一源身份，不能重解码成当前wire合同；没有重跑旧CPU或借用旧typed对象。

在已检索准确地址文本载体和命名原窗库存中没有找到可重解码完整原体；不主张所有未审二进制都不含它。唯一下一source规格已写私有 `next-element-reader-source-request.zh.md`：原E8/caller/setup、owner52802/method439383/单reader实例声明、ordinaryAOT cell1422f9950→新P+439383×8、旧slot142bff788仅提示；先唯一exact/containing pdata，再按旧完整285B/SHA门禁取单体，连续≤128条，两fresh及root第三。xdata/邻body/helper树/执行均0，任何边界或身份不符停止交root；本卡没有采集。管理child14f6f13c0、loader/index/backend、经营字段不重开。

## 实际审核、失败留档与交付

最终冻结清单 `frozen-inputs.v2.private.json` 为14446B，SHA-256 `4ad57103646f5f532dfd8f3c1da8edff619cd0ceb72ab81b54ecaed2cd5df58c`，固定53源/222108018B及9份只读上下文快照。私有独立脚本 `verify-contract.private.py` 为20360B，SHA-256 `85517f6d70f8defdeb8d0d2e3af656deb592fbc9457b18f7e5d0d674346c4d1c`。两个新host Python进程实际退出0：

```powershell
python -B local/migration72/d02-resume-20261110b/c-proud-native-struct-array-contract/verify-contract.private.py audit fresh1.private.json
python -B local/migration72/d02-resume-20261110b/c-proud-native-struct-array-contract/verify-contract.private.py audit fresh2.private.json
```

两报告各207092B、逐byte相同，SHA-256 `ef4347336c719c56b9546edb4ae0e86ae05b4e007e0d1b9f5243b0e1f92ff6b1`。重新解码329B数组窗与2097B原base，共2426B/571条；数组全部88条、12call/14branch/4RIP/23显式memory及52准确锚点与原件核对，保存typed声明、usage来源、完整原指令、同keyheader和循环算术边界。既有机械工具的5测试直接复用，本卡没有重跑或冒称新原生测试。复现使用此前不存在的新basename；脚本排他创建拒绝覆盖。

一次准备审核因独立RIP行多输出`access`键而与原机械报告schema比较失败，发生在成功报告写入前；原bytes/完整反汇编未漂移。已保留原脚本、首清单/context和971B失败记录，改为原RIP schema后另建v2冻结，再完成上述双fresh。一次PowerShell显式通配路径的只读rg查询报路径语法错误，已改用`-g`；没有修改原件、目标重试或安全/Daybreak事件。

状态保持 `targetCPU=0`、`nativeParserExecuted=false`、`typedAllocatorReturnProven=false`、`typedObjectSuccessProven=false`、`naturalBackendProven=false`、`naturalModeObserved=false`、`actualIndexFilled=false`、`actualRowSpanApproved=false`、`resourceReady=false`。group/level、81127+10门槛、Java公式、完整Proud字段/Avatar/保存和D-02父项不晋级，客户端验收增量0。无新PE/code/AOT/pdata/xdata/literal、target CPU/native/emulator/live/REA、进程/网络/Git/构建/DB或部署；只有本文与独有私有目录写入，无需重启。本max将成熟单source规格、报告与写权交回root；Project/Harness、第三审核、本地提交及交接由root统一处理。
