# E-01/R3-AVATAR-ITEM-ENTRY：Depot talents 的直接消费者合同

typed Depot22284 的 field105357 `DEGJNDCIFLE/+28:encoded58209[]` 已由一个完整原生函数的实际读写链连接到 `AvatarEnhanceHelper.EnhanceDetail` 的 **Talent** 分支。可以实现 `AvatarSkillDepotData.talents` 的有界片段投影；本卡没有实现投影或执行 Java 构造，也不批准完整 Avatar、talent 资源行、能力、活动 revision、保存或 D-02。

本次先写中文计划，再审既有 metadata/AOT 和主线新封存的唯一完整窗。子任务没有读取当前 PE/GAME_PATH，没有 Windows API、native CPU、JVM、DB、网络、Git、构建或现场。没有第二 code 窗请求。旧工具、文档、资源和原件保持只读。

## 固定来源与实际审计

独立目录为 `local/migration72/d02-resume-20261110/c-depot-talents-successor/`。原计划 `plan.zh.md` SHA `7278aa466ddcf5204d77a5c1c64274bef480949440ba482975c3a6df627d3ae9`；准确 AOT 锚点 `priority-anchor.private.json` SHA `c34af1c75648f3daf391e1bba17dee160e01b7d3717c834f510cd4119e843d98`。

| 固定输入 | 完整 SHA-256 |
| --- | --- |
| `d02-resume-20261108/r3-equipment/metadata.private.pkl`，117201003B | `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f` |
| `d02-resume-20261108/r3-mapping/main-third.json`，10370640B | `d990c2c469e5a9d3ee40351e4dfe5223ae49afaa855abe5d912427139c66307e` |
| `d02-active/resource-locator-astra/locator-final-1/AvatarSkillDepot.native-m_bytes.bin`，26024B | `fda5ac5875581bf6c04a269333197d41decea728ecc9f075e4add7780745b14b` |
| `c-depot-enhance-window/code.bin`，5901B | `a5677fc634752a48abf9486c8c3302654296e267bb272c67ecbf3224f1acb14f` |
| 同目录 `code.asm` | `d2d2d50973b8f993f82588bb7dd8a878c85664baa91124aaf6486055297d0282` |
| 同目录 `pdata.bin`，12B | `e125544734310753bbbdeeb094b2eabef28c8687be7582cc1338a49b9d5dc347` |
| 同目录 `extraction.private.json` | `a34acb366db32b02a53a1abc6fd633044b65f5c2a4b432cc7dca1b68881607fa` |
| `c-depot-enhance-enum-main/selected-enum.private.json` | `7194c2c23130ff9dd1817888d051525e62e0de980f85ede04fb819a70ae3282b` |
| 同目录 `main-review.private.json` | `0f65d5f9324d51a25775ea3e39c0a829e75892925a992dd1213a336c36d371cb` |
| `tools/extract_client72.py` | `7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d` |
| `tools/extract_client72_enums.py` | `ee00b4a92ec81a97e3e54a56e8a38f0d0f556e69d7ec5b7203497e307b18cc93` |

以上前九项相对 `local/migration72/`；`c-*` 均在本轮 `d02-resume-20261110/`。完整16输入 pin、字节数与逐条 opcode 在新报告内，不依赖本页短写。

原输入五文件为：EXE `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`；global metadata `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`；startup `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`；SDK `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25`；Astrolabe `87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442`。主线在提取及独立枚举复核时实际重核；本子任务只读封存材料，不冒领重读当前五文件。

新审计 `audit_static.py` SHA `15dcfbf2db2fe997b96923fbf419cdc1ab04f17fd92a21f4ffe6bf971f98cf12` 在两个独立 Python 进程实际运行。`first.private.json`、`repeat.private.json` 各18165B，均 SHA `de0c23d2acb6565f6c413af183fd1e5772972e0764e462cde7f96a5d77846ad3`，原字节相同。实际核验16输入、1382条/5901B完整反汇编逐字节连续、32个准确 opcode、9个 typed 字段、唯一 metadata entry、旧135个常量、与主线一致的9个目标常量及3行实际 payload 源跨度。这是静态审计，不是原生函数执行或 Java 验收。

## 唯一 entry、边界与 typed 身份

metadata owner13300 `MAHLFGBPELA` 的方法范围是97808..97815；method97810 `MoveNext` 唯一注册地址为 `150b29720`。遍历冻结全部 metadata，没有第二个方法使用这个 entry。其 `IEnumerator<MoleMole.AvatarEnhanceHelper.EnhanceDetail>.get_Current` 的具名接口与 field62193/+28 的 type13298 相接，不能只凭方法名建立字段语义。

AOT 地址索引由既有 extractor 的 `P=QWORD(1422f98e0+70)`、`QWORD(P+97810*8)` 得出。主线原件确认 P=`1428a54d0`、slot=`142964560`，槽原 bytes=`2097b25001000000`。真实 exception 记录为 `2097b2102daeb210983f7704`，范围 **150b29720..150b2ae2d exclusive**；unwind RVA4773f98，未读取 xdata。不能把后一个 method 的 entry 当作本函数 end。

| metadata 链 | 准确字段 |
| --- | --- |
| iterator13300→Depot | field62192/+20，class22284 |
| Depot22284→待解数组 | field105357/+28，SZARRAY，element58209，rank1 |
| iterator13300→候选资源 | field62190/+10，class40280 |
| resource40280→候选数组 | field198529/+10，同 encoded58209[] |
| iterator13300→遍历数组 | field62194/+30，同 encoded58209[] |
| iterator13300→current | field62193/+28，class13298 EnhanceDetail |
| EnhanceDetail13298→职责/ID | field62178/+30，enum13299；field62177/+2c，uint32 |
| iterator13300→Avatar | field62191/+18，class18163 |

原 `150b2ac80` 的类型槽 RIP 目标是 `145907b00`，冻结 usage cell 明确 type13298；所以随后的 allocator 返回用于这个 typed EnhanceDetail，而非根据写入数字猜对象类型。

## Depot+28 到 Talent 的逐指令链

入口 `150b2972e 4989cf` 固定 r15=iterator。`150b2986d` 取 S+212b8，按默认 depot ID 查表；实际值在 `150b298c6 49894720` 写入 typed iterator+20。后段读该 typed 字段，不把调用方任意 RCX 当作 Depot。

候选来源另行固定：`150b29e3b` 取 iterator+10 的 class40280，`150b29e48 488b4110` 取 resource+10 数组，`150b29e57 49894730` 写 iterator+30。它不是 Depot 数组；后段明确执行两数组的成员匹配。

| 原 site / bytes | 实际职责 |
| --- | --- |
| `150b2adf8 498b4f20` | rcx=typed iterator+20→Depot22284；null只推进外层，不补对象。 |
| `150b2abb0 4883792800` | 检查该 Depot+28 指针；null跳 `150b2adda`，不产生本次 Talent 项。 |
| `150b2abbe 8b6c9020` | ebp=当前40280候选数组的 encoded58209元素。 |
| `150b2abd0 488b4128` | rax=同一typed Depot+28。 |
| `150b2abdd 8b4818`、`150b2abf9 8b44b020` | 实读该数组长度，按rsi取原DWORD；signed结束及unsigned边界检查都保留。 |
| `150b2ac04 e8a7f13cfa`、`150b2ac12 e899f13cfa` | 同一已审58209 getter `14aef9db0` 分别解码 Depot元素与候选。 |
| `150b2ac17 39c3`、`150b2ac19 7411` | 解码ID相等才进入 `150b2ac2c`；耗尽则推进外层，不产生Talent项。 |
| `150b2ac35 e876f13cfa`、`150b2ac41 488bb9d0a00200` | 同一候选解码ID作为 S+2a0d0资源字典查询键。 |
| `150b2ac98 c7403008000000` | 在typed EnhanceDetail13298的enum字段+30明确写8。 |
| `150b2aca8 e803f13cfa`、`150b2acad 89472c` | 同一候选经58209 getter解码后，写EnhanceDetail+2c的uint32 ID。 |
| `150b2acc1 ffc6` | 命中位置rsi加1，用于本项后续展示字符串；本卡不恢复该字符串的内容/规则。 |
| `150b2ad57 4d8b4718`、`150b2ad74 e887ffd100` | 候选解码ID与同iterator持有的Avatar18163进入直接callee15184ad00；其语义不另行猜测。 |
| `150b2ad8c 49897f28`、`150b2ad90 41c7474c08000000` | 发布typed current及迭代state8，随后返回AL=1。state8与enum8分别是不同字段，不能互作语义。 |

S+2a0d0命中行还进入 `1515cd700`（metadata owner51041/method424401），返回值写展示字段+18；Avatar直接callee为owner18163/method138175。二者混淆名保持原样。**本次闭合依赖的是原生成员匹配→typed enum Talent写入→current发布**，并不需要将这两个混淆方法强译成英文，亦未证明S+2a0d0整行对应Java AvatarTalentData。

## Talent=8 的原常量，不按字段顺序推断

本子任务使用既有忽略目录的离线 metadata 副本：`d02-parallel-20261109/b-sdk-prefix/adapter-main/installed-layout-owned/YuanShen_Data/Managed/Metadata/global-metadata.dat`。虽目录名含 owned，它的83644188B完整SHA等于上述固定原 metadata；没有读取当前安装，也不表示任何 heap/GC ownership。

既有 `astra-resources/enums.json` SHA `89a4f3cb069c97df544982055b67c491d1fbba30a5fb6fc7489d33de156b874c` 给出的 field-default table是 metadata+916620/915840B，data base2791de4。对旧135个已取常量逐个复核后，再按**字段索引**取13299的九个literal；未使用literal排列位置。

- Talent field62188：defaultRow15481、storage typeIndex70、dataIndex338614。
- 原12B row在 metadata+943bcc：`46000000b62a0500ecf20000`；三个LE32分别为70/338614/62188。
- 原4B value在 metadata+27e489a：`08000000`，signed int32值8；enum13299的value__本身也为int32。
- 新私有 `enum13299-default-rows.bin` 保存九个原12B row，共108B，SHA `debf617ca170d8374164edc2394039ba4f516cf3b2635aaadde8e2bd33d8b418`；`enum13299-default-values.bin`保存对应九个4B值，共36B，SHA `921c803abfa6ac88f44f7ab19198e5c137d1c7183e8e6912757a6263e8dee0a5`。逐项offset/原hex在first报告，不把拼接副本当原文件连续任意区间。
- 主线独立重核实际五stock/cache，并以既有`recover(...,indices=(13299,))`验证底层typeRefs/storage种类与9常量；上述row/value完全一致，证明见固定 `c-depot-enhance-enum-main/main-review.private.json`。没有第二code窗或CPU。

因而 `150b2ac98` 的8有准确的同版本 **Talent** 来源，并且写入消费 Depot+28 命中的实际分支。这里既有具名常量，也有直接数据流；不是单独metadata符号或UI候选命名。

## 交Terra/Sol xhigh的最小投影规格

另开新独立投影器/测试，冻结旧bridge/source/报告；本卡不写运行实现。只从完整SHA固定的 `r3-mapping/main-third.json` 中按唯一 `(name=AvatarSkillDepot,key)` 选择501/701，704仅作独立非零对照，不改出生默认depot。

1. 先核同一原bytes的SHA再解JSON。选中表须是171行、cursor=payloadBytes=26024、payload SHA `fda5ac5875581bf6c04a269333197d41decea728ecc9f075e4add7780745b14b`。选中行/source须唯一；源跨度实际bytes SHA与canonical行一致。
2. 按字段身份选择owner22284/field105357/offset28、rank1 encoded58209[]，不能只按偏移或数值。只读取既有原parser已解码的`value`列表；不再次套codec、不读对象hex或生产JSON。
3. 生成新片段 `{table:"AvatarSkillDepot",key,source,javaFields:{id:key,talents:<原列表>},fragmentOnly:true}`。数组长度、次序、重复及显式0全部保留。真实空数组可保留空；缺字段/null不是空，不造`[]`。整数只接受0..7fffffff；负数、高位uint32、bool或非整型拒绝，不绕回Java int。
4. 与旧获批`{id,skills,energySkill}`合并时，须同key且source逐项相同；唯一重叠id须同类型和值。旧片段/输入原件不能覆盖。不加入attackMode、subSkills、proud、ability、talentStarName等未知字段。
5. 输出及manifest保留`partial=true`、`completeAvatar=false`、`resourceReady=false`、`d02Complete=false`，绑定本次静态报告与主线enum证明SHA。String、AstaPS英文键、候选JSON、六零或连续数列都不是此许可的前提。

已有真实三行可作准确期望：

| key | 原source begin/end/SHA | talents，原序保留 |
| --- | --- | --- |
| 501 | 426/577；`943f15501042ae657b27bf424e8bae2c0fbd225a70e80622fa453dada40037cf` | `[0,0,0,0,0,0]` |
| 701 | 2196/2348；`c8150bc63a7055b7a1aa466c8683ff1035c0aec58f68f1616d30f33f19103914` | `[0,0,0,0,0,0]` |
| 704 | 2770/2988；`6e6aeaac927901eec5b35009d2538bdaabff9d52179cc35d0e1ca31d667d05f6` | `[71,72,73,74,75,76]` |

这些值只用于转换结果核对；语义由前述原生链证明。704的存在不批准默认出生改成704、不证明全部普通Skill引用齐全、也不提供71..76的AvatarTalentData/OpenConfig行。

## Java目的、验收与精确停止

Java原源固定：`Avatar.java` SHA `9176d33373ee5e663f1cc8f3a9137b65c3def1908fc13035dc5569e4bd4cc31d`；`AvatarSkillDepotData.java` SHA `2aa9dd97dd2d375d430af7053d3e7adab58e91cf3b997fadf5852d33c24d4f52`。目的为后者31行的`List<Integer> talents`，无需新增混淆alias。其onLoad98..103的Optional只处理cost item；它不会给talents补空。

当前出生构造在Avatar138行先建立空unlocked talent set，然后147行选择实际501/701，`setSkillDepotData`301行进入`recalcConstellations`，958行无条件调用`getTalentIdList`；529行直接以`getTalents()`构造IntOpenHashSet。因此源六零列表可消除这一null输入，实际unlocked集合为空会将其交集滤空。这个特定出生路径不需要伪造0号talent行。已有非零解锁、旧号恢复或后续玩法必须另验真实Talent/OpenConfig引用，不能拿出生空unlocked推广。

后继最小正反验收：

- 投影两次fresh同bytes；501/701六个零原样，704六个非零及次序精确。hash漂移、wrong owner/field/element、缺字段/null、重复行、source冲突、错key、高位/负/非整型均拒绝。列表中的重复0不能被当“重复输入”拒绝或去重。
- 新Java测试先走真实Gson/ResourceLoader/onLoad→Depot map，验证两个出生对象的talents非null、长度6、原六零；704非零列表另作有界加载对照。复用现有Skill→Depot顺序和同源出生join，不重做已验HP/critical/Promote算法。
- 主线在实际已具备的同源出生输入中尝试真实`new Avatar(data)`，记录是否越过529及首个后继失败。受控仅删talents后，应保留当前Java的529处null失败；如果更早前置未齐，记录真实更早位置，不补字段去强达该断言。不能通过反射注入空talents或host成功代替构造。
- constructor即使返回，也只证明本次给定片段的Java行为；proud输入被当前onLoad规范化、缺Ability等仍须源语义审计。完整Avatar/自然modifier、活动revision、Manager保存及D-02不得随本字段晋级。若随后首缺是其他必读字段，交该准确Java consumer与已有同源字段来源，先用本地证据，不再次开泛化索引或SDK页。

本卡已排除：53763+290周围RSP+28、EnhanceDetail自己的+28 uint32、iterator自己的+28 current，以及本窗 `150b2a09a` 的+28；最后一项来自 `150b2a08a` 的S+2bbf8分派carrier，不是Depot。无需重查Pool/Hash/String、资源解包、默认/world模板或26348第二候选。此前GitHub固定branch/source与partial已读，没有发现能替代此直接链的新上游目标，本次没有新增检索/clone。

规格已明确，后继交Terra/Sol xhigh做新投影和受控Java准备，真实运行、共享文档、提交与现场均由主线串行。父项保持未完成。
