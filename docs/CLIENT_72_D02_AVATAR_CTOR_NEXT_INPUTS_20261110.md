# F-03d3f4d2：首pop后两个必经输入的最小合同

**两个来源可以一次明确准备：原items-shaped值8B，以及HashSet<UInt32>类图和ordinary2147槽。** 最坏新增首遍8读304B、含统一final16读608B；没有增加数组body、第二block或N链采集。本卡只冻结源级proposal，不修改Sol xhigh独占的core/adapter/blockreader，不批准live、CPU或新副本写。

`B+5b74628`只能证明原QWORD被写入首List的+10，实际数组身份/长度/producer未闭。`B+5897cf0`的**静态身份已闭合为definition2147、GenericInst11、UInt32**；实际class pointer、208B头、原flag、U16请求及record长度仍必须同epoch实采。旧实采HashSet请求56/bin4只是历史例证，不能把它或其heap带进新轮。

## 来源与原指令

前卡block合同保持SHA `04b6cf78d9df9bc8d7c02362deac342fd3566f87896b4b6e4b1adf2f59a46bc7`。本卡复用1661B完整Ctor、420B allocator、312B callee及主线409B post-pop窗，没有新提取或CPU。

| 固定来源 | 完整SHA-256 |
| --- | --- |
| Ctor1661B bytes `15183fe60..1518404dd` | `1b6d394af556f8647e6ee911cd32de784078ab64e29fb9fa37d3368019cd0f5e` |
| Ctor manifest | `4acdd657015a45c7ae8982271406333b3caf88325610b088a5627a89e0bac19d` |
| initializer metadata/AOT/原指令声明 | `1a0889afe94d0bd815a2ff99604fb4c6d48bbade5f49e0d5a8995071ad92acaf` |
| 既有冻结metadata/type-cell缓存 | `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f` |
| 旧warm自然报告，仅提供规则/历史观测 | `cfea3e67d6795e791c541e2c140db8b3f19cc70d6c571f47e7ce84c3eb5a4204` |
| 420B allocator bytes | `c6b7251e6025e0accc5fc41e2083852d59a67671875a338738bd3c922250f1a2` |
| 已有ordinary lookup2833B bytes | `2ae927cf60f8d3e5a4a1ba9595f41e61bc9a6d325d84bfc1bace8c6c27d2fff1` |

metadata缓存为既有 `local/migration72/d02-resume-20261108/r3-equipment/metadata.private.pkl`，89748个已提取record及type-cell映射。只解析这个完整pin的本地数据，不读取当前metadata/PE或GAME_PATH。其输入来源仍绑定CN7.2 EXE/global/startup三个固定SHA；本卡不重做原native映射。

Ctor原bytes给出连续序列：

| site | 原指令 | 本卡作用 |
| --- | --- | --- |
| `15183fe92` | `mov rcx,qword [rip-bccb871]` | RIP指向 **B+5b74628/8**，完整64位值E |
| `15183fe99` | `mov qword [rax+10],rcx` | 将E写首个实际pop对象H+10/8 |
| `15183fe9d` | `mov qword [r14+70],rax` | 将H写当前Avatar this+70 |
| `15183fea1` | `mov rcx,qword [rip-bfa81b8]` | RIP指向 **B+5897cf0/8**，第二类K2 |
| `15183fea8` | `call 1405336c0` | return15183fead，RCX=原K2 |
| `15183feb0 / feb3 / feb5` | `mov rcx,rax / xor edx,edx / call 157d97690` | 第二allocation若真实返回，随后直接进UInt32 HashSet参数0构造；不借enum comparer重载 |

这段没有读取E的pointee或length，也没有在K2为0时调用一个usage补槽producer。

## B+5b74628：最小可证上限是值搬运

冻结metadata中List`1/1255的字段5850 `_items` 类型为SZARRAY、rank1、element为VAR，实例11的实际参数为UInt32，提供“UInt32[]-shaped items引用”的预期语义。**字段offset在该开放泛型metadata中是runtime、没有数值**；原store+10来自实际Ctor指令，不能由metadata字段顺序重造对象或数组布局。

已排除的伪producer来源：

- `declarations.emptyArrayCells`只是旧研究对被读cell的标注，不是Array分配/写入producer证明。
- 冻结type-cell映射包含List及HashSet两类usage，但**不含145b74628**；不能给E套一个泛型type usage身份。
- 旧initializer的 `empty_array()` 是32B自有存储并写length0的fixture，旧scene/born工具也有类似模型；它们不作为本卡自然对象输入。
- metadata只列List的 `_items/_size/_version` 三字段；`.cctor` method10377只有开放泛型声明、registeredAddress0，当前initializer提取中没有List实例11的concrete AOT。它不是已经定位的145b74628 writer，不能猜其地址或把它请求为“已知数组producer”。

因此当前最小采集只读 **`B+5b74628/8`**，保存原bytes、来源reader、地址、错误及统一final。E非零也不解引用；E=0是原始可搬运值，原指令会照写0，但必须标 `itemsNonNull=false`、`arrayIdentityProved=false`，不能称正常空List已初始化。short/error与真实0严格分开。

这8B足以解除原 `15183fe92` 数据缺口，若另获CPU/副本写授权，原 `15183fe99` 可以用完整block已捕获的 `[H+10,H+18)` 初始bytes承受原8B写。**H+10不在前卡六个写域内，须独立新增这8B native权限**；Avatar this+70的原store也只能按既有明确controlled this范围处理，不生成新的Avatar对象。当前没有这些写许可。

它仍不验证E的klass、rank、length0、element type、allocation或生命周期。要验证数组身份，应先定位实际writer/初始化入口及其对象/类型来源；本卡消费的固定方法没有提供该writer，不能直接指定猜测的array header跨度或发送SDK页重复采样。

## 第二类的静态身份和最小运行图

冻结type-cell提取将 `145897cf0` 绑定到 **usageRow702299、sourceIndex31750**；其type为genericinst，genericIndex29811（descriptor data7473）、definition2147 `System.Collections.Generic.HashSet`1`、instanceIndex11，唯一参数UInt32/byRef=false。这里的三个index分别是usage源、GenericClass注册项和GenericInst注册项，不当作runtime pointer或class缓存槽号。

同一initializer AOT提取明确：owner2147/classInstance11的参数0 `.ctor` 为**methodDefinition15590、MethodSpec329040、tableIndex419029、pointerIndex235873、entry157d97690**。它不是15591 comparer重载，不是enum5917实例，也不借String Default。

新epoch从原class usage取K2，最坏六项图如下：

| 来源 | bytes | 验证谓词/保留事实 |
| --- | --- | --- |
| `B+5897cf0` | 8 | 原完整K2；0保存后停止，不补class |
| `K2` class header | 208 | kind21/byRef0；保留+8低type bits、C7/C8/CA全部原byte及+b4原U16；不强制warm或size56 |
| `header+68` GenericClass | 32 | 实际definition2147、methodInst0、backref=原K2、classInst非零 |
| classInst | 16 | count1，实际argument-array非零 |
| argument-array[0] | 8 | 实际descriptor pointer非零 |
| descriptor | 16 | kind9/byRef0；opaque data原样记录，不把13f强当definition |

合计**6读288B**。若真实classInst pointer恰等于本轮已验证List的classInst，可在同一ledger复用其16+8+16共三span；仅metadata instanceIndex都为11不足以复用，也不强制canonical pointer相同。重叠只能复用本事务已成功、原bytes一致且最终同批final的exact span，不从旧报告取得数据。

还必须新增 **已有ordinary root O + `2147*8 = 4318h` 的8B槽**。O=`B+5bc8f18`及comparison cell=`B+5bc96f8`已经在当前R1九项图中，勿重复读。不能用closed K2 backref替ordinary2147，也不能复用1255槽。

原 `14053371c`以definition2147调用5269f0 trampoline及已有VM lookup；非零slot经原hit分支返回，未要求读取ordinary class头。原 `140533721`随后与comparison cell比较。若两值相等，会转 `140533773` 的实际argument/resolver分支；当前不得预设不等或声称仍请求原HashSet大小。若不同则保留原K2后继续原CA/size/tag选择。

## request、tag、bin与真实记录分开

开放泛型HashSet metadata的rawSizeAndFlags首值为10h；它**不是**closed runtime对象大小。旧warm实际K2头的U16+b4为38h=56、CA62、lowTypeBits3。按原420B指令，旧例tag1，按312B公式得到bin4，record pointer cell为 **C+28+4*10h=C+68h**。这些只是历史例证，不是新轮固定输入。

新轮S取**实际class+b4的U16**；CA bit02决定原tagged或untagged路径，tagged分支从class+8 DWORD低3位是否等于1得到0/1，再OR真实class pointer。CA bit20、C7及comparison实际分支均不可改写。只有原代码保留K2并选择这条size/tag路径时，才可将该S称HashSet请求；不能仅按静态名字写S=56。

原small请求的bin可按已审指令解释：`z=clz64(S|80h)`，`shift=(60-z)&63`，`bin=((S-1)>>shift)-8*z+1c1h`（原整数宽度保持）。解释结果不自动授权读取任意bin。如果新S实际仍为56，已知候选source是**C+68这个cell里的实际record pointer**。cell地址与List的C+48不同；pointer值及record是否相同仍须实采，不能用List record或首head里的N替代这个cell读取。

第二record的 `+18`实际L2、`+20`实际head及完整block仍未采；不能用S=56充L2，不能用首block/L1或N充第二block。若新S为0、超原small阈值或原flag选择其他分支，保存真实值并停相应原边；本卡不泛化第二allocator存储模型。

## 零槽与准确producer前沿

两种零槽必须区分：

1. **B+5897cf0=0**：本轮只取得原usage0。固定metadata绑定告诉它最终应对应哪个类型，不给动态usage写入时刻/函数。`15183fea1`到`fea8`没有lazy fallback；后面的HashSet constructor和525e00都需要已有class，不能反向当此slot的producer。当前准确未取的是runtime usage初始化writer/触发及非零class图，不以类型身份补槽。
2. **ordinary2147 slot=0**：已有VM的 `15a4d6bf1`读取槽，`15a4d6bfd`未走hit；首次新源是 **`15a4d6c03 → B+5bc95a8/8`**，随后 **`15a4d6c0f`经B+3bac1f0未知IAT call**。这就是原allocator2147查询触发的lazy producer前沿，但锁/发布及必要动态输入未闭。当前图在首缺前停，不猜锁API或调用host类生产成功。VM原2833B已冻结，不需要新PE窗。

即使六项身份图有效，CA bit20清除、ordinary比较选中resolver、C7=0或其他真实分支仍可能先到已声明未知边；候选图与“可按旧fast分支回放”分开。不要为了采到非零槽再启动相同SDK页。

## 可合并proposal与不包含项

| 本卡新增 | 首遍最坏 |
| --- | --- |
| 原items cell | 1读8B |
| HashSet class六项图 | 6读288B |
| ordinary2147槽 | 1读8B |
| 总新增；含统一final总上界 | **8读304B；16读608B** |

若实际classInst等于本轮List context且三span可复用，新增为5读264B；总含final10读528B。这只是实测地址相等后的减账，不降低最坏cap。class图、ordinary槽、items值与原block/Pool全图必须在一次首遍后统一final，失败/short计账、不重试、不沿变化值扩链；任何首读失败停止后续扩展，真实0保留对应分类。多次RPM仍非原子，不能证明writer不存在。

与前卡R1+Pool+单block组合（L1为首record真实长度）合并的**新proposal**是：

| 情况 | 首遍 | 含统一final总计 |
| --- | --- | --- |
| 不预设context共享，L1=32 | 36读750B | 72读1500B |
| 不预设context共享，L1最大208 | 36读926B | 72读1852B |
| 实际context共享且可复用，L1=32 | 33读710B | 66读1420B |
| 实际context共享且可复用，L1最大208 | 33读886B | 66读1772B |

最大单读仍208B，API另账。此表不包含第二record/head/block、array body、Default图、额外class、N链或新CPU；不自动改变当前实现的固定profile或任何审批。

第二allocation若真实返回，下一直接方法 **157d97690..157d97759，265B，已存边界/指令文本/代码SHA `ac214998df8fd99397a345192248ca9a43d4b22ec74c374c05fb5e4dc20d2550`**。其第一运行输入 `157d97698 → B+594fd78/8` 是UInt32 comparer owner，后读+38/static Default；本卡新增图没有提供它，不能拿String Default或旧warm值续跑。若下一卡真要执行此方法，先复用已有原bytes封存；若缺bytes，唯一合理raw请求就是这一个已固定265B源码方法及有效exception记录，不取新相邻窗/另一个重载。本卡没有发起该提取，也不将文本/hash当作新机器码原件。

当前两个问题无需新库、GitHub检索、ABI或新PE来给实现规格。数组producer本身未定位，不能因存在List.cctor10377这个符号就给出猜测的新PE边界。

## 实际审计与交回

独立目录 `local/migration72/d02-resume-20261110/a-avatar-ctor-next-inputs/` 已先写计划。两次新标准库静态进程均exit0，核20个完整pin、Ctor bytes/RIP、固定metadata/type-cell/AOT、原U16/tag/bin、VM miss原bytes、六项图及去重/统一final账。两报告各10281B、逐byte相同，SHA **`8612bd1ed5f8446a554415512e09f0cf3d289f824baaab4cdcae1db4dd5d0703`**；保留argv/UTC/exit/stdout/stderr及determinism。

```powershell
python local/migration72/d02-resume-20261110/a-avatar-ctor-next-inputs/audit-source.private.py --output local/migration72/d02-resume-20261110/a-avatar-ctor-next-inputs/audit-main-third.private.json
```

本卡无新PE/CPU/Windows API/live/GAME_PATH/网络/Git/构建；未调用旧collector/verify或运行实现。仅写新专题与独立输出。规格明确，可交root/Sol xhigh决定是否与必要新epoch合并。数组身份/producer、第二pop完整存储、自然caller、GC ownership及完整构造未证，父项不晋级，本卡立即返回释放max。
