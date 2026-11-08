# E-01 / R3-ITEM-ENTRY：出生武器 Item 必读字段与引用

## 有界计划（2026-11-09，先计划再原生执行）

本卡只处理出生武器11101尚缺的必要Item职责。已验initialWeapon、itemType、weaponProp、WeaponCurve、weaponPromote及真实Java武器片段加载直接复用；不重开HP/grow/Curve/Promote，critical没有新锚点继续unknown。目标是在确实足够时立即交可实现canonical片段与加载规格；不足则交准确最小输入，不补0、空列表、alias或旧资源。

先按当前Java调用明确必要性：`AvatarStorage.addStartingWeapon → new GameItem(11101)`在构造器先读id/itemType，Weapon分支随后迭代`skillAffix`正ID；`Avatar.equipItem`在有World的武器分支用`ItemData.gadgetId`创建EntityWeapon。EntityWeapon查GadgetData，再按jsonName查ConfigEntityGadget、fight props/能力/controller；SceneTeamUpdate实际必须有Weapon对象，weapon entity的空分支仅是当前Java行为，不据此许可省略gadget/能力资源。其余Item字段只按当前D-02实际必读源纳入，不扩全物品玩法。

| 精确来源/候选 | 已有锚点与许可状态 | 本卡最小下一验证 |
| --- | --- | --- |
| Weapon87097 typed wrapper → managed20600 backing（继承88968） | 原始Weapon whole288/EOF及真实11101已审；原字节SHA `cca19efb…a1ff918`；第三equipment SHA `50aaea94…eb5bc` | 只复用已审跨度重新执行选中原row parser及原getter，不重跑整表。 |
| 88968 field442107 `BOEHLFMHNBC/+2c`，encoded58209，11101 logical50011101 | 真实值/字段类型已有；**gadgetId职责尚未批准** | 先从同一typed Item/Weapon资源访问器及其直接caller确认该值进入具名Gadget资源getter/创建入口；找到准确getter后只审其直接消费者。若无新准确caller，不按50011101的数值形状批准。 |
| 20600 field97497 `BAOFKNMGAEM/+40`，encoded58209[]，11101为[0,0] | 真实数组已得；旧完整武器affix consumer `150758e60 → 152b8ef00` 有独立受控group/level证明，资源存储当时为fixture | 从已知consumer核真实managed数组/NativeClassArray接口来源，保留零槽/顺序；以实际11101和一个真实非零Weapon选中行验证group参数与必要引用，不能把旧fixture列表当真实数组自然ABI。 |
| EquipAffix具名表 | 旧合同owner45983/cache`S+dfd8`，getter`152b8ef00`，loader`152b8efd0`，literal21444/cell`145af3c40` | 只在正引用确为本卡必要时沿现有冻结carrier/index唯一记录取精确所需输入；未得真实row时停准确getter入口，不能用空cache/假row演成功。 |
| Gadget正引用及配置 | Java精确目的为GadgetData.id/type/jsonName→ConfigEntityGadget与必要能力；本卡尚无批准target Gadget caller/原row锚点 | 先补上述field职责；仅在找到准确具名getter/entry后复用已有index定向投影该表/选中Gadget。需要新解包/全局名称搜索或无准确source时停止并交准确缺口。 |

固定源为旧equipment工具/第三报告、同源Weapon原字节、typed metadata/cache、既有locator carrier/index、当前Java消费者及固定五stock输入；原生前后用既有hash守卫复核。已完成的旧原生/Java测试/报告全部冻结，不改pin掩盖漂移，不下载两外部仓库或全资源。

修改范围只新增本页、`tools/probe72_item_entry_resource_fields.py`、对应test；raw只在`local/migration72/d02-resume-20261109/r3-item/`。禁止Project/Harness/HANDOFF、生产Java/Proto/resources/patch/config、旧测试/工具/报告、Git提交、Gradle/Cargo/DB、进程/客户端/调试器或共享REA session。先向主线回报本计划的准确field/caller锚点，再执行新native。

预算最多12个新直接函数窗口、3组原生实验、每调用最多2,000,000指令。按顺序为（1）两个field的精确producer/accessor/caller与真实选中row；（2）11101及独立非零/缺引用对照；（3）必要时同源Gadget/配置定向引用。继承的parser/primitive/typed存储直接复用，所有原getter、查询和readout开销计入，区分新执行、复用和fixture，不将大量小调用藏成whole。

验收保存原source hash/跨度、encoded原bits与logical值、32/64位、float32、原枚举、有序列表/0槽、缺席/null/empty/显式零与未知。对已可转换字段写准确Javacanonical键、引用、onLoad过滤与加载次序，并有独立正/反例、双fresh报告和摘要及主线第三命令。完整Item/gadget/能力/自然revision/loader/入场状态保持各自未完成。

停止条件：固定输入/hash/类型/跨度不符立即拒绝；早期准确caller不存在时只做有界否定并返回，不无限扫描。未建模间接边或真实Gadget/能力定义缺失时保留第一次失败，给准确类型/调用位置/最小解除输入，可独立字段先收口；不能补0/空row、套alias、将缺引用自然分支或健康/编译冒称实机许可。

## 实际字段结论与停止边界

**只批准11101的新增Java片段 `{"id":11101,"skillAffix":[0,0]}`。** 来源是同一原生Weapon87097的managed20600 backing、field97497 `BAOFKNMGAEM/+40`，其真实类型为encoded58209数组。两个零槽为实际原parser结果，不能折成缺键、null或空源列表。它们经当前Java `GameItem` Weapon构造分支的`>0`过滤后，运行对象affixes为空；这是该构造器的实际行为，不是源列表为空。ItemData.onLoad不改skillAffix。

独立Weapon11501实得`[111501,0]`，原消费者以此顺序把111501和0交给具名EquipAffix group getter。它证明该字段的职责和零槽保留，**不导出11501运行行**：其正group的真实level行与Java `(group*10)+refinement`编号关系尚未闭合。出生11101没有正affix引用，本卡没有为它虚构空EquipAffix表或0号成功行。

`88968 field442107 BOEHLFMHNBC/+2c`仍仅为原值。实际getter `148446d40..148446d7b`（owner34113/method285988）从managed backing+2c取encoded58209，再调用原uint解码器；11101为50011101，11501为50011501。**不批准gadgetId alias**，数值与真实Gadget同ID不是字段职责证据。精确getter的直接E8 caller为0；对已知Gadget getter `152e110c0/152e10fc0`的41个直接site与7个inline cache候选做定向metadata/字段过滤，未取得Item→Gadget桥，遂停止。过滤不是全程序“不存在”的证明；间接、内联或整块复制仍未知。

另核的`150758750/1507581a0`读的是Weapon model29353+40所持proto64947的+2c（已知promoteLevel），并非resource88968+2c，明确排除，不能因同偏移转成gadgetId。

## 原生消费者与精确数组ABI

复用已审完整affix消费者`150758e60..1507596ce`，本卡只执行其明确default-affix分支中的`15075934f..152b8ef00`。输入为实际Weapon pointer、typed29353 model+30、显式slot index，及原代码`R14=RSP+20`。它先作87097类型检查，再从非null managed+20选择backing+40，按真实数组Count检查index、get_Item、原`14aef9db0`解码，最终在具名`EquipAffixExcelConfigData` group getter`152b8ef00`入口停下。没有伪造group lookup结果、phase/level或Affix row，也未自然执行整个Weapon model方法。

实际消费接口由typed metadata固定：cell`1458978f0`为`ICollection1244<58209>`，cell`1458a2e78`为`IList1250<58209>`，不是最初检查的IReadOnly候选。Array方法如下：

| 用途 | 精确来源 | 执行范围 |
| --- | --- | --- |
| Count | System.Array117/method991 `InternalArray__ICollection_get_Count` → `159c6ca60` | 原函数返回数组长度。 |
| get_Item | System.Array117/method1004 `InternalArray__get_Item`，AOT methodSpec167023/table544099/pointer76706，method argument instance3826=`58209` → `15480d060` | 原AOT调用`1404c2610`检查边界并按原stride复制encoded值。 |
| 非采用候选 | method999 → `1547ecd00`及method1000 → `159c6cbc0` | 只审窗口；未把IReadOnly实现塞进实际IList接口。 |

数组仍是原parser分配/填充的真实typed58209[]。CPU harness只为该精确类补rank1/stride4及两个接口slot，slot指向上述实际AOT/native函数；无Python Count/index/result回调。87097对象使用准确metadata cell`1458aa7e8`的class身份。它们都是明确的托管存储/dispatch fixture，**不是自然CLR class构造、NativeConfig mode或revision证明**。

11101四个encoded字段值（little-endian32位显示）为：+2c=`f294755f`→50011101，affix两槽均`403d5515`→0；11501为+2c=`f2943567`→50011501，affix=`40151809,403d5515`→`111501,0`。完整原字段保留在私有selectedRows，包括float32、enum、空列表和64位：11501的20600+88原bits`febb59b80e2e5dc0`，无符号十进制`18355363353325362624`，不转JS浮点/Java负数alias；11101为`484162849b1b2b37`/`5206550965894458167`。已验Weapon property的float32`41b9f5c3`及原ITEM_WEAPON4仍保留，仅复核数据未重复算作旧字段新完成。每个标量的可选token省略位未另恢复，不能把逻辑0声称为原字节显式零token。

三组实验为：（1）两个选中原row constructor/parser和所有原getter readout；（2）实际数组四个slot的消费者到具名group入口；（3）六个受控负例。负例为index==Count自然退出、直接get_Item越界抵原错误入口、backing数组受控null抵原分支，及typed空EquipAffix Dictionary对0/111501/`ffffffff`返回null。空cache为明确fixture；缺引用返回null不许可省略真实正引用，越界/空分支仅到精确停点，不声称执行异常或后续生命周期。

## 固定输入、来源与预算

只复用旧equipment main-third报告SHA `50aaea9406152e1ec9e0c423ec532618b8fe635e3d0cf758d24fccc6403eb5bc`及同源Weapon payload128936B/SHA `cca19efbe032e5416d78c28fcb69c2ea2d9707187197aa4ed90d869c3a1ff918`。实际选中行：11101 `[4,400)`、396B/SHA `c4ef8b265f69900cf3330ea83666d039d7f5d753266994e6056039ca36202e9a`；11501 `[20031,20463)`、432B/SHA `cce091376d371895ccc7be0b0f137a3eb8f9885e7ffe0d5768b3ad2258fae6a3`。未新跑288行whole parser、解包或重建资源索引；固定五stock输入和全部继承来源仍由旧守卫执行，正式report记录精确pin。

已有Gadget资料经本卡发现后直接复用，而非“尚未取得”：`scene-runtime-context-third.json`3870401B/SHA `bf9efd6225cd2836286b3a301bb2ea2f33dab6c6ddfb103d53aeed8f10db81d4`已取得Gadget50011101 `[432396,432488)`92B，SHA `eaf903171c14cfd27367b0bc7012ef7036671d4b7bb0d6c3723d237e6a17b9ad`；Gadget payload2944342B/SHA `6355185856a095aea5d523a8465029b2c40c61d5de055e53fc0412a6472dcf2c`与PrimaryIndex163245B/SHA `1e95ae771d6597160b2438d5a1ead40f04f58a84a26be466c0226fab6923138f`亦固定。该原row含id50011101和字符串`Lv1_Weapon`/`Weapon_Sword_Metal`，尚不能直接把字符串位置翻译为Java jsonName/type或能力定义。

新直接窗口8/12，共1123B：`148446d40`59B、`150758750`81B、`1507581a0`371B、`1547ecd00`23B、`15480d060`23B、`159c6cbc0`256B、`1404c2610`54B、`159c6ca60`256B。各窗原SHA/精确反汇编在staticEvidence中，不扩大Gadget搜索。预算为3组实验，每次call最多2000000；报告逐call及按原函数entry记账，包含所有原getter/readout及停点hook，库/托管存储模型另列。26个绑定源码条目包含只绑定的whole/完整consumer与未执行停点，不能称26个完整函数均被执行。

两次早期host断言拒绝保留，不从开销中隐藏：

- `probe01.failure.json`SHA `1d1490a39f7c3907e6e0c1edaa5c86da7fec2e33e5a2e433956f3a86b97ea2d5`：先选14101，实际未满足非零affix对照，原生88次call/8753 hook后拒绝；当次failure未另存该行逻辑字段，只保留来源与调用账，故不以它导出列表。
- `probe02.failure.json`SHA `dc8cae86337dddccf2f2619639eb249c8da5b737edbbfc0668ed4ce95bc115d2`：两个实际行已保存；首次consumer的简化87097 class身份不匹配，抵原null错误入口`1404c2be0`。97次call/9441 hook；没有绕过原检查，改用准确metadata类并将R14还原为RSP+20后再验。
- `probe03.json`SHA `24e72acd915753c12894d6a4c36dca8d87a9b68609e66ebe01017ea0a5f66f01`：完成3组、106次call/10361 hook，旧16项检查通过。此后仅补逐call账本、失败来源和第17项64位/float/enum检查，不能把此报告冒作冻结验收。

三个当时源码的原件均在私有目录，并逐一与报告sourceSHA匹配：`probe01-source.private.py`=`8323c664…b4a92f`、`probe02-source.private.py`=`9abedebb…86e1e`、`probe03-source.private.py`=`4888b2e4…473c2`。前置探索总计291次原生call/28555 hook；两次失败是完成原生后host断言，failure里的末次current-call hook已在completed总量中，不重复相加。没有未记的额外原生失败指令。

## 最小真实Java后继与尚缺输入

主线可独立在隔离ResourceLoader测试中，将本卡批准片段与已审equipment的11101 `id/itemType/weaponProp/weaponPromoteId`片段合并，文件名沿真实WeaponExcelConfigData加载路径。使用实际JsonUtils/Gson→ItemData.onLoad→GameData Item map，核skillAffix原数组正好`[0,0]`；再实际`new GameItem(11101)`核ItemData身份、level1、count1、`affixes=[]`。只构造GameItem，不调用World/Avatar.equipItem/EntityWeapon或SceneTeam，不设置loadedAll。GameData Item map按ResourceLoaderIsolation惯例备份、独立清空、finally恢复；缺报告/pin/片段立即fail，不用候选JSON。未安装生产资源、未运行Java/Gradle/DB，本卡仅交加载规格。

受控Java负例可分别删skillAffix键、改null/[]或插入正111501，观察当前构造器的过滤/保序行为；这些均为Java当前行为，**不能称为source省略许可或11501正引用闭包**。源合同只接受实际11101 `[0,0]`，不把正ID控制写成可运行canonical行。Java加载通过也不补gadgetId职责。

Gadget侧准确最小解除输入是：同一客户端的typed Weapon/Item consumer，把field442107/+2c或准确getter148446d40的结果送进具名Gadget54132 getter`152e110c0`/wrapper`152e10fc0`或等价创建入口，保留调用位置、类型、raw值与正/缺引用对照。已有真实Gadget50011101原行可直接续用，不须再取整表。此桥补齐后还需明确该行字段如何选择Java GadgetData.type/jsonName，并取得它引用的真实ConfigEntityGadget及必要ability/controller定义；无证不补0/空/alias。另一独立后继可继续Avatar icon→AbilityEmbryo、Depot/Skill必要列表，不依赖本卡gadget未知。

## 冻结与复验状态

本卡公开源码冻结：`probe72_item_entry_resource_fields.py`26135B/SHA `6581362650dbbbc3ddb85b059ad22b1e659c3b372add636bab7a72d0788d016d`，对应test9036B/SHA `db268b2054e201101210b19de886d5798ae5e6c75e7456c02e7cb2187ff5f631`。AST解析通过。收到主线实际恢复stock及五输入复核许可后执行最终双fresh；期间未改这两个源码。

`first.json == second.json`，447517B/SHA `9007d08f91848c9aa2c63d8690b3f5f9c6e8cc7165a3fa07a0dc78b8e55a53c2`；两个summary亦字节一致，26825B/SHA `de157830e819835dfa64252c0ca6f4454bd76aa806a06beff493eb4fc6bea95c`，均位于本卡私有目录。每轮91个source pin、106次call/10361 hook，最长call2637；三组分别9431/704/226，逐call账与entry聚合相等。连同全部前置探索，主线第三前实际共503次call/49277 hook，未漏早期拒绝或readout开销。

17项检查在first与second各全过，0失败/错误/跳过，日志`tests-first.log/tests-second.log`。覆盖实际有序零/非零列表、ICollection/IList实际AOT与解码路径、gadget alias拒绝、null/缺/empty区分、正引用未闭合拒绝、uint32高位与bool拒绝、精确跨度/hash、超过2^63的uint64、enum/float32位、六个native反例、所有逐call/分组/前置成本及完整Item/entry保持false。没有把它们当Java/DB/客户端验收。

主线实际第三 `main-third.json`447517B、同SHA，与两formal逐字节相同，明确指定第三的17项检查通过；另核源/测试SHA、91个pin（当前GAME_PATH单独解析）、同源Weapon payload与两原行跨度、四槽实际消费者/有序group、106调用10361hooks及false边界，共11项，见 `main-review.private.json`。首次只读审核曾在sourcePins里误找直接Weapon路径；实际原件由冻结equipment报告的payloadSHA绑定，核对准确locator文件后通过，未改producer或raw。限定 `E-01/R3-ITEM-AFFIX-FRAGMENT` 完成；完整Item父项仍缺gadget职责/配置/能力。批准片段进入[真实Java affix加载](CLIENT_72_D02_ITEM_AFFIX_JAVA_LOAD.md)。

主线恢复固定五输入后，第三复现命令为（已有报告冻结，重跑须另取新文件名）：

```powershell
$itemClientPath = & tools/game_path.ps1
python tools/probe72_item_entry_resource_fields.py --client $itemClientPath --output local/migration72/d02-resume-20261109/r3-item/main-third.json
$env:D02_ITEM_ENTRY_REPORT = 'local/migration72/d02-resume-20261109/r3-item/main-third.json'
python -m unittest tools.test_probe72_item_entry_resource_fields -v
```

修改仅为本专题与两个新增离线文件；旧原生/Java工具、报告、Project/Harness/HANDOFF、生产Java/Proto/resources/patch/config全部冻结，本卡不Git提交、不构建、不部署或操作客户端/共享REA。完整Item、Gadget/能力、自然Java loadAll/revision与D-02仍未完成。

## 后继gadgetId限定闭合

上述材料和旧报告的field2c未知标记保持原范围。后续[Weapon→named Gadget桥](CLIENT_72_D02_WEAPON_GADGET_BRIDGE.md#主线第三验收)已通过主线第三/15检查/12审核，批准出生`{id:11101,gadgetId:50011101}`；经原字段、准确call/prologue和独立原named-query片段，真实Gadget指针命中及交换/缺引用反例成立，不按同ID批准。当前剩余是Gadget具名字段→配置/能力闭包和EntityWeapon自然生命周期，不能继续把gadgetId职责列为缺输入，也不晋级完整Item或资源父项。
