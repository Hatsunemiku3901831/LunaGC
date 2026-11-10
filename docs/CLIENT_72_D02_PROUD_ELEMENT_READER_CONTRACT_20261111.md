# E-01 / R3-PROUD-ELEMENT-READER-CONTRACT（2026-11-11）

## 结论与可交付出口

**已闭合52802.method439383元素的普通文件读取合同；同key22101的两个元素分别只消费2B，使条件base返回cursor及63831本体起点确定为142。** 这是明确普通文件条件下的文件语法，不是观察到的原生cursor或成功typed对象。元素wire为2/6/10B，caller每次清零的8B局部只是receiver存储，不能作固定8B wire。

float叶14f905250的存储编码仍未闭。原体给它局部54724 receiver和float参数，没有声明reader参数；其缺体不阻断本窗的inline wire/推进规格。缺省float输入是+0.0，原call后实际encoded DWORD保持unknown，不能把输入bits或call前清零值当成存储结果。

已交主线可立即实现的最小reference规格：**base文件前缀＋52802数组header/元素token，返回next63831InputCursor，暂不展开derived全部字段。** 私有`terra-reference-spec.zh.md`8283B，SHA-256 `311bf6644f362d9aa602406b8dfe5b1e72485590924a37eb88437525a47e8a8d`，已冻结且不再修改。该子出口不需要新source；准确下一工作是从142审核既存7674B derived原体的后继消费，不重采元素，不先扩float/helper树。

本卡接续[base合同](CLIENT_72_D02_PROUD_BASE_PARSER_CONTRACT_20261111.md)、[数组合同](CLIENT_72_D02_PROUD_NATIVE_STRUCT_ARRAY_CONTRACT_20261111.md)、[元素机械窗口](CLIENT_72_D02_PROUD_ELEMENT_READER_WINDOW_20261111.md)和[同key输入](CLIENT_72_D02_PROUD_ACTUAL_ROW_CONTRACT_20261111.md)。已先读AGENTS、README、Project、Harness、迁移/部署手册；同链小续项直接复用固定GitHub成果，无新检索或招募。中文计划私有2267B / SHA-256 `7042a5380ab8921d7f3bc2fea1b763f52ae3c0bf53e9e96bd33a3759c46954ea`。

## 固定原件与准确参数

主线第三元素机械报告13768B / SHA-256 `5a9632b259cfc673d9b7457ae9af1b54e9b53f276a059fd3fe4573fd7ffc81fe`、两fresh八原件及原工具/测试身份保持；没有再读当前PE、AOT或异常目录。唯一元素原body为`[14ee1ea00,14ee1eb1d)`，285B/80条，SHA-256 `8f64b2b9c0020c75eff11786e33d7e257d9925a08b83b8b1975de0b9524c5247`。

新独立脚本从已存code.bin重新解码元素285B、数组329B、base2097B和derived7674B，共10385B/2429条，逐byte拼回，核两fresh八原件、原pdata边界及ordinaryAOT算术。derived只核准确调用/返回后首读取锚点，不声称新审其全部1778条字段语义。元素全部80条、9call、12branch、25显式memory及18隐式栈指令连同81个跨窗准确锚点存于私有报告。

原数组caller在`14ee1ebe0`清零局部QWORD，`ebe9/ebec`以RCX=&局部receiver、RDX=同reader，`ebef e80cfeffff`调元素。元素`ea12/ea15`保存RDI=同reader、R14=同receiver，除epilogue恢复没有第二次赋值。唯一typed声明是owner52802/method439383、flags0x83、一个class42005参数；return type仍unknown，不把返回RAX当元素值。调用后原数组重新加载输出对象，再将局部QWORD交155fb6be0；其存储实现/所有权未批准。

metadata52802是ValueType，field261827在metadata+10声明enum32419，field261828在metadata+14声明54724。原体分别直接写unboxed receiver+0/+4，与8B局部吻合；没有据此把member命名成Proud业务字段。metadata/signature在反序列化前完整核SHA，不借旧typed对象。

## presence、默认、codec输入与推进

以下只解释明确inline分支的文件读取，不执行reader、allocator、对象或存储callee。

| 阶段 / 原PC与bytes | 精确读取、变换与默认 | 文件推进/输出边界 |
| --- | --- | --- |
| header `ea46 0fb75c0820`；`ea4b 83c102`；`ea4e 894f1c`；`ea51 81c3606f0000` | 读LE16到EBX，再执行**32位**加6f60。保存`headerSumU32`及其low16；mask40用BL，mask80最终用BX转出的DL。 | cursor低32位加2。header高位carry不能丢失；它不增加wire宽度。 |
| member0 mask40 `ea57 31c9`、`ea59 f6c340`；存在时`ea8b b954a6cdc4`、`ea90 334c1020` | 缺省ECX=0，不读wire；存在读LE32并xor c4cda654，保留全部32位。 | 存在时`ea88/ea94` cursor+4；`ea9a 41890e`直接写receiver+0。缺省是直接写0，不是伪造4B零wire。 |
| member1 mask80 `ea97 0fb7d3`、`ea9d 660fefc9`、`eaa1 f6c280`；存在时`ead4 335c0820` | 缺省XMM1全0，float输入低32位00000000/+0.0；存在读LE32 xor64f1d829，再`eadb 660f6ecb`以MOVD传原bits。 | 存在时`eacc/ead8` cursor+4。IEEE754解释与bits分开，保留-0/inf/NaN；缺省不消费wire。 |
| 两路汇合 `eadf c744242000000000`；`eae7 488d4c2420`；`eaec e85f67ae00`；`eaf1/eaf5` | 局部DWORD先清0，RCX=&局部，XMM1是上述float输入；**无论mask80是否置位都调用14f905250**。声明为54724实例方法454807、一个float参数。 | 正常返回后才将局部DWORD复制receiver+4。存储编码/返回值未审，因此实际copied DWORD未知；call前0不是call后0的证据。 |

元素显式reader存储只有上述三处+1c，没有inline替换+10数组或+20指针。其wire消耗精确为`2 + (mask40?4:0) + (mask80?4:0)`，可能2/6/10B；没有64位wire读取。receiver未见null guard；本体不能证明任意receiver地址有效或赋予其所有权。

## 原native/refill与错误边界

初始header前及两个可选member前分别检查reader+20；非零在`ea27/ea6d/eab5`以同reader、EDX2/4/4调14f614760，然后重读数组/游标。该helper行为未知，不能从一次入口+20为0推出全部后继自然状态。普通文件reference必须明确每次相关检查均走inline；不建typed42005，也不强置未知对象字段。

| 原条件 | 原入口与目标 | 当前状态 |
| --- | --- | --- |
| reader参数null | eb08→1404c2be0 | 原检查已证，未执行helper。 |
| header前array null | eb0d→1404c2be0 | 同上。 |
| mask40 refill后array null | eb12→1404c2be0 | 只在该分支重新检查；普通inline沿用此前array。 |
| mask80 refill后array null | eb17→1404c2be0 | 同上。 |
| header/member0/member1起始下标unsigned越界 | ea40/ea86/eaca→eb03→1404c2650 | 三个原start检查共享入口，未执行helper。 |

三次原load都没有全宽`cursor+width<=length`检查；cursor先MOVSXD用于地址，起始下标比较仍用低32位unsigned。host文件reference的全宽可读条件、外部候选334对照和原错误分支必须独立记录。若原start检查通过、物理剩余不足2/4B，应报告物理全宽不可读及原check结果，不能声称原越界helper已经触发。错误前已有的header/member0推进及receiver+0先写事实保留，不作事务回滚；未审错误callee不合并成统一EOF或默认成功。

## 同key两个元素与63831准确起点

完整payload2132778B / SHA-256 `82a8017c6c2868e41e3ce8fc02f8bc04437cb641ec653ed3383c5cc109e25835`；候选`[4,334)`330B / SHA-256 `cfe85822a6606b824d731b5bb7ba442c8d0d4113187294de5457e3a1e04c8a6b`。缓存backend不消费B，物理end仍2132778，不能预置334为reader长度/终点。

本卡沿已审明确条件：derived+40=null与base+20=null分别成立，reader相关+20检查均inline，所有此前对象/allocator/storage/cleanup原call正常返回；纯文件抽象不赋予opaque存储call额外文件消费。这些不是自然mode/backend观察，也不是由INativeAllocator0推出。既有prefix31、25个4B项和22B块使cursor134，数组DWORD xor后count2，使首元素输入138。

| 元素 | 实际必要header span/raw | 32位相加 / low16 | member0 / member1 | 条件消费 |
| --- | --- | --- | --- | --- |
| 0 | [138,140) / a090 | 90a0+6f60=10000 / 0000 | 均未置位 | 2B→140 |
| 1 | [140,142) / a090 | 90a0+6f60=10000 / 0000 | 均未置位 | 2B→142 |

两次member0直接默认写0；两次float输入+0.0，但receiver+4的actual encoded DWORD都unknown。两个header均在物理范围及候选范围内，没有执行错误helper。

原base在数组call正常返回后的`14d5e2ce8..2d20`只有refcount/清理与ret，没有进一步inline reader消费。故**该文件抽象的base返回cursor为142**。63831在`149f0c243`交base，返回后仍有header分配/初始化等原生前置，再到`149f0c2eb`首DWORD读取；这一步的条件起点现在确定为142。必要对照`[142,146)`原e2fe8a1b / LE321b8afee2已保存在报告，未在本卡展开本体字段或宣称消费330B/终点334。

## 旧Promote范围与float首缺

三个既存Promote报告各3297024B、逐byte相同，SHA-256 `546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1`。其元素source记录的end/hash与本轮285B原bytes一致，可以明确绑定同一元素本体；旧报告本身仍没有可重解码元素bytes。

历史whole-table trace里，WeaponPromote元素entry/header/member0/float-call/ret各9105 hook visits，member1读取1558；AvatarPromote分别3556与2875，相关五个错误入口均0。它们是历史fixture的PC覆盖，不能替代本轮Proud两个缺省元素、自然reader、原行或错误验收；未重跑旧CPU，也没有使用其typed输出。

float14f905250旧摘要仅保留`[14f905250,14f9052f3)`163B / SHA-256 `bac1a635359180adac5ac415d084cd45d36d9f7a01c59eda1ea9b1d1eb79833c`及两个RIP引用。在准确文本库存和选定旧报告中未得到可重解码raw；主线也没有额外路径。该有界负结果不声称全部未审二进制都不存在原体。当前只交decoded float输入，不批准54724存储编码/对象成功；不为这个局部输出另采source或阻断wire/cursor出口。

## 实际审核与交接

指定私有根`local/migration72/d02-resume-20261110b/c-proud-element-reader-contract/`：冻结清单21202B / SHA-256 `df5a5476a0d2047cc07cb731027d0e443c19d68c109d3095ac50bd85ca56db85`，86源/216964461B和11份只读上下文快照。独立脚本26189B / SHA-256 `c7781cf233e39e5a2dab80c42c584fec638cd2ed51f9fc81b6bf04243120a95b`。

两个新host Python进程实际退出0：

```powershell
python -B local/migration72/d02-resume-20261110b/c-proud-element-reader-contract/verify-contract.private.py audit fresh1.private.json
python -B local/migration72/d02-resume-20261110b/c-proud-element-reader-contract/verify-contract.private.py audit fresh2.private.json
```

两报告各54095B，逐byte相同，SHA-256 `16f23d450d86b56dfeeba5721db57c2ec8f4f3b7397b20478349df248a9fc68c`。除完整原件/声明/指令与同key必要算术外，保留4个presence组合、5个start-vs-width/高位边界、5个IEEE754位模式和一个高位U32纯host算术见证；这不是原代码执行或新单元测试套数。上游5项机械测试直接复用，未冒称本卡重跑。

只读准备时一处漏PROUD文档名、一处错误asm后缀及两次过量显示已纠正，记于read-notes；没有修改原件、失败审计或目标重试，无安全/Daybreak事件。输出排他创建，复现须用此前不存在的新basename。shared上下文快照固定一次，不以其后并行doc更新放宽source pin。

状态保持`targetCPU=0`、`nativeParserExecuted=false`、`actualCursorObserved=false`、`typedReceiverOutputApproved=false`、`naturalBackendProven=false`、`naturalModeObserved=false`、`actualIndexFilled=false`、`actualRowSpanApproved=false`、`completeProudReference=false`、`resourceReady=false`。group/level、81127门槛、Java公式、完整Avatar/保存与D-02未批准；客户端验收增量0。无新PE/code/AOT/pdata/xdata/literal/CPU/native/emulator/live/REA、进程/网络/Git/构建/DB或部署。仅本文和独有私有目录写入，无需重启；Project/Harness、主线第三、本地提交与交接由root统一处理。本max交回成熟规格、审计及全部写权，不等待其它包。
