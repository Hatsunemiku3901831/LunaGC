# F-03d3f4d2：第二allocation与零参数HashSet的后续合采proposal

**本轮保留AB98读/2304B，不扩采、不执行replay。** 已有源足以冻结一个后续有界增量：实际分支可返回时，补第二次allocation的一块完整真实存储和Default的三个QWORD，可推进原`15183fea8`及`15183feb5`两个已知site。所有新读取、CPU、副本写权限仍为false；不因本规格出现就启用第五profile。它只验两次原allocation及零参数ctor返回，不验完整Avatar、自然caller或空集合全部语义。

## 原窗长度更正与固定源

HashSet原窗是 **`157d97690..157d97759`，end exclusive，201B/53条**，不是265B。原manifest没有bytes属性；原extractor按`end-entry`切片、反汇编和hash。旧`a-avatar-ctor-next-inputs/audit-source.private.py:157`误手填265，报告/doc及本次初始请求沿用了这个显示错误。这里更正，不改旧pin或原件。原请求SHA`2021093a9bd7f8f03bc6357510d10b9f528a0479a7d089015d59f295885e1f2e`保留；独立归因报告SHA`c8b0ec60cd101084ec386cbdbd142defca14c36665266bb8b5548ed98bc3d18d`。

主线从当前核定stock的真实exception目录独立确认同一边界，只取201B，未取额外64B。原12B pdata为`9076d9175977d9179c281505`，unwind RVA`515289c`，xdata未读。MethodSpec329040、table419029、pointer235873、原caller `15183feb5 e8d6775506`亦独立复核；**普通method15590的registeredAddress=0，不能套普通P表。** 本分析只消费封存原件，没有读当前PE。

| 固定输入 | 完整SHA-256 |
| --- | --- |
| 完整Ctor1661B | `1b6d394af556f8647e6ee911cd32de784078ab64e29fb9fa37d3368019cd0f5e` |
| 原allocator420B | `c6b7251e6025e0accc5fc41e2083852d59a67671875a338738bd3c922250f1a2` |
| 原pop312B | `8ce320df866d328376e990489907ab22f973e2d087ee7c1e0c415c8fb8ee3a7c` |
| 原postpop409B | `cd03c4e7f6d80a3f5f1d3f3744f0013dead6bdec82615559db73b25b8bd39e14` |
| HashSet真实201B | `ac214998df8fd99397a345192248ca9a43d4b22ec74c374c05fb5e4dc20d2550` |
| 该窗主线提取proof | `9171c2d45391837bc55c053133ca19459a5010e43f1cb79502875bc8190de037` |
| initializer原manifest | `1a0889afe94d0bd815a2ff99604fb4c6d48bbade5f49e0d5a8995071ad92acaf` |
| 固定metadata cache | `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f` |
| 旧warm报告，仅取代码/常量/规则 | `5ca9ac157ede168f299bceb65841e24a9accf89d8ef3bdb08c249a2afabdad1c` |

路径和其余全部输入SHA保存在独立`local/migration72/d02-resume-20261110/a-second-allocation/audit-first.private.json`的29项inputPins。首pop合同04b6、next-inputs e8f、Pool a6d及现有A四profile均冻结。复用既有TLS/phnt/GitHub固定成果，本次没有新增库或复用检索缺口。

## 两个原call之间没有额外分配

首allocator `15183fe84 → 1405336c0`返回后，原Ctor依次：`15183fe92`读`B+5b74628/8=E`，`fe99`写`H1+10/8=E`，`fe9d`写`AvatarThis+70/8=H1`；`fea1`读`B+5897cf0/8=K2`，`fea8`第二次call allocator。这个区间没有其他call。E只按原QWORD复制，E=0也不补数组，不读pointee，不晋级Array身份。

现有A4已取K2的208B头、GenericClass32B、classInst16B、argument8B、descriptor16B及ordinary2147 cell8B。固定metadata/AOT闭合HashSet definition2147、classInstance11、UInt32；它不固定新epoch的class指针、flag、U16尺寸、record或block。actual classInst与List相等才可复用已有三span40B，不因静态instance11相同就复用指针。

## S2、bin、L2和第二块的最小来源

只支持实际CA bit20置位、ordinary2147非零且不等comparison、原代码保留K2的分支。CA bit02决定原tagged/untagged路径：`140533731 0fb786b4000000`/`738`或`793`/`79a`读取**K2+b4的U16=S2**；tagged路径由K2+8低3位是否为1得tag，untagged路径tag=0。两个实际callsite是`14053376c`和`1405337d8`，均将RCX=C、RDX=S2、R8=原class参数交给同一312B。不能强制S2=56或tag=1。

按原bsr/shift，令`z=64-bit_length(S2|80h)`，`bin=(((S2-1)>>((60-z)&63))-8*z+1c1h) mod 2^32`；**`C+28h+10h*bin`是8B record指针cell**。size32得到bin2，历史size56得到bin4，仅是公式例证。

独立R2分支新增以下五个首读，所有指针均来自同epoch原bytes：

| 地址/宽度 | 直接源与用途 |
| --- | --- |
| `C+28h+10h*bin / 8` | `140563d34`取得R2 |
| `R2+18h / 8` | `140563d6f`的实际U64 L2 |
| `R2+20h / 8` | `140563d39`取得H2 |
| `R2+10h / 2` | `140563d46`原lock-inc的初始word |
| `H2 / L2` | 一次完整真实block，提供next/oldFlags及原生写存储 |

有限读取政策为 **`0<S2≤208`且`max(S2,38h)≤L2≤208`**。38h来自本窗最后一次store的exclusive end，不把S2充L2；208只是拒绝阈值，不是native L上界。请求长度恰为实读L2，不补齐、截断、分块或重试。S2在209..8000仍可能属于native small分支，本proposal政策拒绝；S2>8000走原slow。R2/H2非零且第二完整span与H1不重叠；unsupported alias保留原值并停。完整block替代head+0/8与head+8/1两次读，不额外读取next节点。

**同R、同bin2时可以只为第二pop取一个N块。** 首pop原`140563d4b`读H1内真实N，`d4e 48894120`写R1+20=N；上述Ctor区间没有call，且首pop/outer实际fast边能返回，所以第二`fea8`选择同C+48/R1时，其离线顺序中的head确为N。此时复用R1的L/count/cell原span，要求`L1≥max(S2,38h)`，只新增一次`read(N,L1)`；N是特定第二pop的候选，不是已观测到第二次自然调用。不跟N块内部N2。N=0则第二次走slow，不取零页；N自指/与H1重叠拒绝。same-R/different-bin观察到时本有界profile保留为unsupported，不递进猜布局。bin2的正请求仅17..32；这不是允许强改HashSet尺寸。

两次pop共用M+19cc、C+8、global header byte、M+19e0及notify原输入，不重复首读。Pool的M+1990与Avatar的M+19d0仍不同。首读成功的同地址同宽span才可显式去重；原宽度不等的重叠须逐byte一致，不用宽span覆盖矛盾。首失败不扩展，所有成功unique span在同一A事务末尾按原地址/宽度统一final一次，含两块完整bytes。B仍保留自身final，不能跨A/B偷减账。两遍一致仅是非原子稳定候选，不证明ABA不存在或GC所有权。

## 零参数HashSet正常分支需要的Default

固定metadata owner2147 method15590明确`parameterCount=0`。`15183feb3 xor edx,edx`没有被本callee正常路径消费，不能称capacity=0重载。唯一concrete绑定是MethodSpec329040/table419029/pointer235873，generic pointer来源`CodeRegistration+30`，不是普通方法表。

| 正常路径首读 | 原site | 结果与停止 |
| --- | --- | --- |
| `B+594fd78 / 8` | `157d97698` | U=0时止下一条`9769f`解引用 |
| `U+38 / 8` | `157d9769f` | P=0时止`976a3`解引用 |
| `P+0 / 8` | `157d976a3` | D非零走原`976ac→976e6`；D=0止首次cold新读`976ae` |

该global的固定metadata usageRow732150/sourceIndex5214为EqualityComparer definition1234、GenericInst4864、instance11、UInt32；这个静态身份不证明实际U/D的运行时header。正常D非零分支没有MethodInfo、R8/RDX、泛型上下文或D对象解引用，**原指令只复制这个opaque非零QWORD**，所以不必为此再采comparer对象/Hash/Equals图。D=0后的反射/初始化/helper/cache-publication均未闭合；不host Default成功，也不多取B+5bc8ee0碰运气。

`157d9773e`写`H2+10/8=D`；`97742`读固定16B常量`1421e9d70`，`97749`写`H2+28/16`，`97752 ret`。常量原bytes为`0000000000000000ffffffff00000000`，SHA`11a9a2fb3cb66146fa6e703de533c3f386cda2eb4120c8faf8a19a36555e8247`，已在固定warm报告保存，是stock文件数据，**不需新PE读，不是旧heap复用**。

allocator原返回RAX=H2；这个ctor热返RAX仍是D。原Caller的RDI保存H2，在`15183feba`写AvatarThis+a0，不以ctor返回RAX充对象。ctor没有清零其余payload；未写的真实dirty bytes必须全部保留。即使将来原ctor返回，也仅证明零声明参数原函数返回，**不据此批准空集合payload、自然对象生命周期或GCownership**，更不能用旧零数组/owned零对象补齐。

## 新增账与本轮边界

现A4首遍36读/(718+L1)B，含final72/(1436+2L1)；B另首遍13/226，total26/452。下表是假设原受支持分支完整通过的**后续proposal**上限，失败按实际前缀记账且不重试：

| 分支 | 新增首遍 | A首遍；含final上界 | AB首遍；含final上界 |
| --- | --- | --- | --- |
| R2独立，五storage项+三Default | 8/(50+L2) | 44/(768+L1+L2)；88/2368 | 57/(994+L1+L2)；**114/2820** |
| 同R同bin2，只加N整块+三Default | 4/(24+L1) | 40/(742+2L1)；80/2316 | 53/(968+2L1)；**106/2768** |

独立L1=32/L2=56时AB首57/1082、total114/2164；独立最坏L1=L2=208时首57/1410、total114/2820。单读仍208。总字节较旧cap小的个例，也不能复用旧读数授权。**本轮AB98/2304和A四profile保持原实现，后续要新scope/profile及主线批准。**

## 停止、零值与拟议精确写域

TLS index0合法但不是成功；state byte/count/global header byte为0均合法，word FFFF原inc后wrap0、C+8按U64 wrap。L=0保存后拒绝，S2=0原`14053380b`，C=0仍是原M+19d0初始化分支，未知record不能host构造。R=0没有native null检查，止`d39`解引用，不能当空record；H=0走`140563df5→140563e10`。第一个N=0允许首pop返回，但同record第二次必须slow；第二块N2=0允许最后一个link被pop，不解引用N2。notify=0只在E真实非零且原byte已取得时可解释bit0清除。

oldHead bit08置位在原`140563d85`调用141fdf9c0前停，保留此前副本写/state5；notify bit0置位在`140563e01`调用14056c050前停，state已恢复1；CA bit0置位在outer`1405337f3→14055ecf0`前停；C7=0在`140525e2a`读B+5bc90f0前停，其后IAT`140525e31`亦未知。C7非零只走已审fast-return，不强设flag。CA20缺失、ordinary0/比较相等、Default U/P/D0分别保留对应首缺，不再追运行时树。

只读replay即使输入完整，仍止首次captured写 **`140563d2d` M+19cc/1**。未来另批captured-copy-write时，捕获原件保持只读，副本每pop仅允许：M+19cc/1在d2d写5、da4写1；R+10/2原inc；R+20/8写真实next；H+0/8写原taggedClass&~7；H+8/1写原lowtag OR实际global byte；C+8/8加**该record实际L**。两次共十四个非栈写事件，实际地址相同的span合并权限，事件不合并。两次不同R时Ccounter依次加L1、L2；同R时word计数原样连续inc两次、head依次N/N2。

这些六类写域之外，仅可另外提案原`fe99`的H1+10/8、`9773e`的H2+10/8和`97749`的H2+28/16；已有来源AvatarThis的+70/+a0各8B仍须独立授权，不能造this。null context额外M+19d0/8不并入本合同。所有未写block字节保留；真实非零脏bytes才能证明dirty覆盖，样本全零则明确false，不注入脏数据、不换head求通过。

未来CPU验收须核原件SHA不变、每个原PC/地址/宽度/old/new、实际pop链、计数、RAX和class/header；原slow/call边不得被stub成功。目标最多到零参ctor `97752 ret`，在原Avatar+a0 store已独立获权时可再到`15183feba`，然后**止第三个class读`15183fec1`**。primary只称有来源受控离线上下文，不是自然Avatar/Pool allocator线程；自然constructor的this/caller/线程tuple及完整生命周期仍未取。

后续collector正反验收只围绕此图：独立R2与同R/bin2各一个完整块成功前缀；原U16尺寸和不同真实L；N2=0不多读；R/H/N缺失、L小于所需/大于208/完整U64 max、同R不同bin、块重叠及U/P/D0首失败不扩；final漂移或重叠矛盾返回partial；实际classInst相等才省三span。人工输入仅验读取/拒绝规则，不能代替实采block、原CPU返回或自然线程验收。

## 实际离线验证与交付

独立静态审计核29个完整pin、420B/98条、312B/74条、409B/108条、201B/53条连续原bytes，201B与旧manifest逐指令相同；核1661B原三call、metadata零参数和Default typed usage、固定16B常量、27081组独立L预算及153个同record L预算。没有新CPU、Windows API、live、PE/GAME_PATH、JVM/DB、网络、Git、构建。主线提供201B窗单独记源，不冒称本代理提取。原合同、Atools、AB实现、资源和共享文档均未写。

两次报告均20429B且逐byte相同，SHA **`17d81c40a4c674e201d24af7f1880325a316f408c8f97ee564b266dabe42e646`**。私有目录保存最小中文计划、长度归因/更正请求、逐opcode审计、确定性报告和`implementation-proposal.private.json`（9309B，SHA`bb64561caef7d7e250924a39b2bb72b9782e8f970bdb5911321e10ee2c8392d9`）；早期`budget-proposal.private.json`中的S2≥56限制由本最终proposal取代，旧轨迹保留。主线可独立复核：

```powershell
python -X utf8 -B local/migration72/d02-resume-20261110/a-second-allocation/audit-source.private.py --output local/migration72/d02-resume-20261110/a-second-allocation/audit-main-third.private.json
```

实现方可在未来主线另批后，复用A4 ledger及首失败/统一final规则增加上述有限storage/Default读取；不需要新code窗或通用allocator。当前真实输入、copy-write与CPU仍未取得/未批，完整Avatar/Pool/g2/D-02均不晋级。本卡到此交回。
