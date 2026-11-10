# E-01/R3-PROUD-LAZY-INDEX-CONTRACT（2026-11-11）

## 本轮中文有界计划

| 项目 | 本卡合同 |
| --- | --- |
| 目标 | 只解读已验157ffc4c0完整236B/71条，回答400190壳、U32 key/byref75077、初始化/替换/零错/返回边界，定位第一个有原来源的装载前置或准确下一source。 |
| 已有 | lookup final3/4八原件与第三15186B/SHA `9e48e1dfb3bc27073e04934572dc21dc57b124ba7d7bed74cbf00701c6376f6c`；AOT final3/4及第三12430B/SHA `ab9f56b6d782430261d195a2b21cf245340e86b01663483cb9e676c483b06b5f`；两literal final1/2及第三2429B/SHA `d6e1160cc1eccd1564df174798a7571a6feb41f048a3a105b161dcc07c3d9727`，旧factory/cctor/mode/row合同与两next-spec只读。 |
| 准确缺口 | 本lookup怎样使用+10/+18/+20、怎样填8B out与返回AL；首call157ffc650是否真正惰性装载尚未知，不能仅凭literal名称或相邻generic方法地址猜loader。 |
| 方法 | 固定全部既有source/full SHA，逐原PC/hex核参数保存、分支、source type、地址计算、out与返回；metadata已有缓存仅定向解准确cells/字段/声明。只复用既有fixed/partial外部来源，小续项不新检索。 |
| 范围 | 新PE/code/tuple pointer/literal/CPU/live/REA/网络/目标进程/构建/DB/Git/招募均0。新必要body或纯数据须先给root独立有界计划；不拓helper树、不碰Native trace或旧B Daybreak。 |
| 产物/验收 | 本页、必要离线审核工具/test及独立`c-proud-lazy-index-contract/`；已证/推断/未知分列、两fresh只读审核、准确下一输入。旧八原件/AOT/literal source与原call continuity全SHA，不能把完整payload当row或原AL当RAX==0。 |
| 停止/恢复 | source漂移、身份/extent歧义留诊断交root确认epoch，不放宽pin；安全/Daybreak记录停止不重试/换模型。普通工程未知在已批窗内继续，成熟source/最小样本规格即交root/Terra并释放max。 |

## 当前结论

本有界静态出口完成。**157ffc4c0先无条件call157ffc650，再看同壳+18；本236B不读取壳+20，所以真正的loader/path/type边仍未闭合。** 壳+18为空时，本地请求`Dictionary<U32,75077>`，把壳+10与typed Default候选比较，必要时写入新dictionary+20，然后缓存到壳+18。不能把这个fallback创建候选当已填index或装载成功。

后半的正常合同已经准确：原索引helper返回EAX为负时，**out恰8B清零、AL=0**；非负且array/bounds通过时，从entry读取8B到同out、**AL=1**。全零8B仍可伴AL=1，key0没有本地特判。**只保证AL，不保证整个RAX/EAX为0或1。** 真实调用/索引值、row与异常unwind仍未执行或观察。

不新增公共wrapper/test，不重复六出口已有36项门禁。本卡只增加一个私有纯静态来源/指令核对脚本；两fresh报告各12550B、34个原PC断言、逐byte同SHA `2c35406ac5f3ac296fc55e7975c882fb375e22bba2f0c9ef10c2f76641f24224`。全部写权交root，下一最小source规格交Terra/Sol xhigh；本max不取新pointer/body。

## 完整固定来源

私有根`local/migration72/d02-resume-20261110b/c-proud-lazy-index-contract/`；新manifest固定138项实际来源，28054B/SHA `bc1b0342094786ed7cb79e3f5af71261fb3c248a6f97c41e6f79c2bae3269b1a`。逐项核旧factory/index合同98源、lookup final3/4八原件与第三、AOT final3/4与第三、两literal final1/2及group原件与第三，及三包各自全部source pins。公共可维护专题只作context，不用追加接受文字覆盖旧pin。

| 来源 | 完整范围/身份 | SHA-256 |
| --- | --- | --- |
| lookup code | 157ffc4c0..157ffc5ac，236B/71条 | `479e010ba8e0b7fbea184c024408f2a1ce266aa519b92445bf03279672f2fb4e` |
| lookup pdata | 唯一exact/containing，RVA1b0dec10，12B `c0c4ff17acc5ff17ec6c1905` | `bc51db5ccd1ae65bc99967caf12530ef5d35c35feea86d3b86f03aa4c6050fd7` |
| lookup第三/最终双audit | 各15186B，同byte | `9e48e1dfb3bc27073e04934572dc21dc57b124ba7d7bed74cbf00701c6376f6c` |
| 5071 AOT三报告 | 各12430B，同byte，method138527/slot142515e78 | `ab9f56b6d782430261d195a2b21cf245340e86b01663483cb9e676c483b06b5f` |
| literal final1/2 | 各7507B，同byte | `ab5e41ca8b95a4d8ff610d5d96140583c9c5f8e8ad34399c93259817dc447c68` |
| literal第三/双audit | 各2429B，同byte | `d6e1160cc1eccd1564df174798a7571a6feb41f048a3a105b161dcc07c3d9727` |
| 前factory/index合同 | 各41969B，同byte | `5c95fea6265f1386e5e849a565fea6e91e8ef4464b5251106cba3c35a30ad838` |

五文件身份在已接受取窗报告内完整保留并逐项一致：EXE `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`；global metadata `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`；startup metadata `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`；SDK `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25`；stock `87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442`。本卡没有重解GAME_PATH或读当前PE，不冒称新客户端采样；旧raw/full hash是本次唯一输入。

原606B caller为80063.method663546，SHA `c090061f8eaef3bce27411310d52b2811eab410fe73f772dbe76424d22fd570d`；14d7ad926取global+21f58，14d7ad938起14B `4c8d45d089fa4531c9e87aeb840a`传key与8B out，E8 site14d7ad941→157ffc4c0，返回14d7ad946后检查AL。field400190声明18192/5071 `<U32,75077,47647,25030>`；AOT原table/spec为`394a0100f5ab0200ffff89660000` / `1f1d0200cf130000ffffffff`，pointerBase142470cb0、pointerIndex84537、slot142515e78原8B `c0c4ff5701000000`。仅此method138527绑定唯一；跨不同pointerIndex同code仍partial，不抹掉旧边界。

## 参数、首call与普通/替换边界

`157ffc4cb..4d0`依次R14=R8(out)、EDI=EDX(key)、RSI=RCX(壳)。key按32位完整位值保存，后`55b`再送EDX；没有符号扩展或零key分支。caller使this非null才调用，out是原栈8B槽；本body未另外校验this/out，不给错误对象或地址补成功模型。

首个call是`157ffc4d3 e878010000`→**157ffc650**。此时RCX仍为同壳；RDX/R8/R9没有新setup，不能从残留寄存器推断声明参数。保存key/out可让之后继续使用；此callee的RAX返回值未被直接使用，`4d8`首先重读`[RSI+18]`。所以它是查询前的**唯一首个必经准备边**，可能改变壳状态或抛出异常，但本卡不能先叫Load/EnsureLoaded、普通成功或替换成功。

本236B没有local dispatch/usage guard、没有this+28检查或this+20读取。这里的“普通本地流程”只指首call正常返回后到本地RET的指令；**不能据没有local flag便断言整个调用没有IFix/替换/初始化路径**。首callee、allocator、cold Default、索引helper都保持各自未审语义，不枚举或扩其树。尤其首call是否读路径、填index、一次加载还是每次刷新，目前未知；已缓存+18也仍先经过首call，不能假设它完全不被改动。

## 壳+10/+18/+20及本地初始化

| 原site | 实际数据关系 | 已证及未闭 |
| --- | --- | --- |
| `4d8/4dc/4df` | RBX=准备call返回后的壳+18；非null直接转558 | 此对象用于后续字典索引；尚无自然内容或调用成功样本。 |
| `4e1` | R15=同壳+10，仅在+18为空时读 | 值参与后续Default比较和新dictionary+20条件store；不是path，也未调用该对象作为delegate。 |
| `4e5/4ec/4f4` | RCX来自cell145935a60，请求allocator；返回null转5a6 | 冻结usageRow731403/sourceIndex41311声明1222/5072 `Dictionary<U32,75077>`。请求类型不证明实际创建成功。 |
| `4fd..511` | typed cell14594fd78→+38→[0]取候选，MFENCE后判非null | usageRow732150/sourceIndex5214声明1234/11 `EqualityComparer<U32>`；static storage/对象实际值未读，不把它当自然Default实例。 |
| `513..548` | 候选null才走cold链；原MFENCE后写回上述storage[0] | 原`145bc8ee0`根、偏移7c0、140517680、140538230仍按原地址保留。最后159b6e720的唯一metadata是1328.method10494 `CreateDefaultEqualityComparer`，名称/声明支持Default职责，但不证明helper成功或对象身份。 |
| `54b/54e/550` | 比较候选RAX与壳+10的R15；不相等才`[RBX+20]=R15` | 已闭合比较/条件store用途。IEqualityComparer声明支持比较器解释；不是实体引用替换或文件装载。 |
| `554` | `[RSI+18]=RBX`，随后与非null路径汇合 | 只在上述本地初始化路径写回壳+18；没有本地entry插入或真实75077值生成。 |

原18192 field86033/86034/86035/86036分别声明String/IEqualityComparer/Dictionary/bool，通用runtime offsets仍未独立恢复。本窗让“+10参与比较器选择、+18持有typed Dictionary”有直接值流支持；与原声明兼容，但不据此把所有generic字段offset一次性填齐。旧cctor给5071壳+10=0、+20=literal来源，**本lookup全程无壳+20读**；不要把这里新dictionary+20 store错认成壳path写入。

本地fallback没有显式closed .ctor调用，没有可见entry数组填充；MFENCE/Default初始化属于比较器链，不能改叫index资源初始化。即使一个新dictionary被缓存，也未证明文件存在、已填字典、空字典默认布局、loaded标志或loadAll完成。没有实际执行、异常/并发样本，不添加推测竞态处理或去掉原fence。

## 索引结果、8B out、AL及错误

`558/55b/55d`将同RBX和原U32 key交**1553c0640**；这个callee体/闭合方法身份未取，本卡不预称FindEntry算法已验。但其返回EAX在本wrapper的用途完全可见：

| 本地条件 | 原指令 | 正常结果 |
| --- | --- | --- |
| EAX signed<0 | `562/564`到584，`49c70600000000` | 向原R14 out写恰8个零byte；58b test原EAX、58d setns AL→0。 |
| EAX signed≥0 | `566`读字典+10数组；空转59c；`56f/572`作unsigned index与array+18比较，越界转5a1 | 574将EAX零扩展到EDX，576令RDX=5*index，57a读取`array+20*index+2c`处8B；57f原QWORD写同out；58d令AL=1。 |

75077原field372773/+10、372774/+14均声明U32；这里的unboxed out是完整8B，正好携带两原U32位值，不给它们offset/size或group/level别名。可从直接地址计算证明20B entry stride和8B copy；“数组data32B+value offset12B”的结构解释与之相符，但不是独立恢复完整Dictionary Entry schema的新增证明。

正常RET只在59b。最终`test eax,eax; setns al`只改低8位：例如纯静态位算中EAX=ffffffff的低32位会变ffffff00；EAX=00000100会变00000101。**这两个是编码含义示例，不是实际索引样本。** 上层原caller检查AL，因此不能让driver用整个RAX/EAX是否0判失败，也不能要求成功时RAX==1；高位未建立业务返回合同。

key0与key80000000/ffffffff都没有本地特判，原位值送callee；后面的signed测试针对**返回索引**而非key，不把高位key当负索引。非负索引若对应8B全零，仍走AL1，不能把out全零当miss或空row。负索引仅说明本callee结果分支；它不解释究竟缺key、未装载或其他原因。

allocator返回null→5a6/1404c2be0；非负索引时array null→59c/1404c2be0，unsigned bounds失败→5a1/1404c2650。没有把这些边转换为out0/AL0，末5ab是INT3；真实异常/unwind未知。out没有在首call前本地清零；若前置callee异常，不能保证out是零或不受callee影响。错误函数未正常返回到59b时，不报告本地正常false/true。

## 已解literal与最小下一source

现已接受两完整literal：cell145b29758为`Data/_ExcelBinOutput/ProudSkillExcelConfigDataPrimaryIndex`，cell145b29760为`Data/_ExcelBinOutput/ProudSkillExcelConfigDataIndexGroupId`；它们分别由旧cctor写400190/400189关联壳+20。sourceIndex/slot各自独立解码，本例同数不一般化。此处没有第三literal解码或任何新payload/hash/index查找。

旧MiHoYoBinData suffix的准确type来源来自14d6e1570/145983438，不在本236B。本卡不能用两个literal名字拼suffix后查固定index求命中；它们没有证明loader接受何种typed资源或已生成75077两数。真实row仍需index内容与同key原8B值、所选reader backend的有效bytes/范围，或独立whole reader的确切row span。rowreader+1c写A、B容量、整个2132778B payload都不代这些输入；factory/mode/+20→+40与child许可独立，不随本lookup关闭。

私有`next-input-spec.zh.md`只建议**157ffc650**一个下一完整窗。准确caller就是本236B的4d3/E8；不追Default冷链或字典helper。若root需先闭合其managed身份，可复用已验5071全部16条table/spec，只读16个准确slot QWORD（fresh≤128B，新增≤15QWORD/120B），按实际指针等于157ffc650筛；不凭名字/邻地址/另一tuple距离猜一个slot。零或多命中均留诊断，不扩pointer表；root可另决定按无metadata身份的原native helper研究。身份经root接受后，才可另批此唯一body≤4096B/1024条，先新五fullhash、原caller/slot与唯一pdata；首个真正loader/path/type边出现即交回，不扩callee树。

以上D/W均只是计划，本卡没有读新QWORD、pdata/body或字符串。已形成成熟最小source规格，不让max等待机械执行。

## 实际核验、复现与交回

```powershell
python -B local/migration72/d02-resume-20261110b/c-proud-lazy-index-contract/verify-contract.private.py <新basename>.json
```

脚本只读138项冻结来源、两fresh八件和三包已验第三，核完整236B/71条连续bytes、34个关键PC/operand、首E8 rel32、typed Dictionary/Comparer、原literal、AOT唯一身份与partial限制。没有调用旧collector、重跑旧6/5/6项测试、导入emulator/REA、解析当前PE或执行原指令。两新host Python进程退出0，报告各12550B/同SHA `2c35406ac5f3ac296fc55e7975c882fb375e22bba2f0c9ef10c2f76641f24224`；实际argv/UTC/exit/stdout/stderr留`executions.private.json`。私有脚本是本卡来源复核，不是新runtime wrapper或lookup实现。

没有source drift、平台安全/Daybreak门禁或取窗失败。只新增本页与指定私有根；未改旧工具/专题/Project/Harness/HANDOFF、Native trace、Proto/patch/resources/config，未Git/构建/客户端/进程/live/CPU/网络/DB。仅文档/离线材料无需重启或部署，客户端验收增量0。全部本卡写权交root，第三复核与共享状态/提交由root负责；loader/index内容、真实row、alias、threshold/Java公式、resourceReady/Avatar/D-02仍未验。
