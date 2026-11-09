# E-01/R3-GADGET-CONFIG/ABILITY：Metal 未知对象与准确取窗入口

2026-11-10收尾：本页三个有界静态步骤已由主线验收，包含原候选未知对象审查、196497字典parser及196498值工厂分派。**字段语义仍未闭；514570 factory只提出准确入口，未批准、提取或执行。** 用户要求当前在途工作收尾后暂停，本页后继申请只供恢复后评估，不启动新任务。真实Java局部生命周期另见[必要性与执行验收](CLIENT_72_D02_GADGET_NECESSITY.md)。

本项承接 [完整候选审计](CLIENT_72_D02_GADGET_ABILITY_CANDIDATE.md) 和 [Gadget lookup-key 合同](CLIENT_72_D02_GADGET_LOOKUP_KEY_JAVA_LOAD.md)，只调查 `Weapon_Sword_Metal → Avatar_Weapon_Metal` 的内容缺口。**现有固定资料不能把 `Default.LKLPDIMKLKI` 及四叶绑定到目标字段；不能批准 `abilitySpecials`、default modifier 或其他 alias。`defense/common` 也没有取得目标字段职责。** 本项给出一个有原 caller 和精确签名来源的最小新窗申请，不修改 Java、资源、配置、共同 String 合同或任何旧报告。

已能独立保留的增量是：目标类型86828/76549确实出现在原能力名称注册路径；未知对象并非只有“猜名字”一个后继。该路径的已有原窗足够复用，不需重新扫描或重提。它仍没有建立候选 JSON 名称到这些类型字段的关系。

## 固定输入及实际范围

源路径均相对当前 checkout。原始材料、分析脚本与报告位于忽略目录 `local/migration72/d02-parallel-20261109/r3-gadget-ability-semantics/`；没有读取未验外部 clone。Q05/Q06仍是已固定 commit `6cfecfb55f889f8e3b7156adb12809608db4f74b` 的候选正文，不将其称为目标客户端原 bytes。

| 输入 | bytes / SHA-256 |
| --- | --- |
| 定向检索 `requests/Q05/body.bin` | 361168 / `700d2c4cb1ea11d5b3036002159326250b3817c127de087b697515321eda75a8` |
| 定向检索 `requests/Q06/body.bin` | 3745 / `b3f7f417c47ff4f2d726728450ca2092e07bc060458d874f7b919c57c2c564d2` |
| `local/migration72/evidence/client72-structures.json` | 241228521 / `fd82e0a55baa69301e87c422465e3797b0f1279778e6b82d938c28446f1b56c5` |
| `local/migration72/evidence/client72-signatures.json` | 78649549 / `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe` |
| `d02-resume-20261108/r3-equipment/metadata.private.pkl` | 117201003 / `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f` |
| `d02-active/scene-runtime-context-astra/config-owner-static.json` | 309852 / `dab9cfcc1a713475b1102fb53ea7cfdfeb7a43c83e8480a358aab286462e0e1d` |
| 同目录 `ability-source-discovery.json` | 10870 / `0aa9fc6efb1319e63daf9ee6078266f0cdf46b554a72a58e493afc0476676004` |
| 同目录 `producer-static.json` | 11691328 / `251ebcdfe7d72027c7d39377cca68ad32061384f2e2880b6d3bdaac59de987f5` |

Q05/Q06的检索根见候选专题；表内省略的 `d02-*` 前缀均在 `local/migration72/` 下。19个完整 source pin还包含旧提取脚本及9个实际 Java 声明/消费者。固定 structures/signatures 的 provenance 为 EXE SHA `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`、global metadata `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`、startup `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`。本项没有检查当前 GAME_PATH 磁盘身份，没有读取 PE 或再次证明旧 raw 与当前磁盘相同；后续新窗前由主线串行做 stock 门禁。

## 未知对象及四叶：分别留源

选中 Q06 `/0/Default` 的完整内容是 `abilityName="Avatar_Weapon_Metal"` 与以下对象；Q05必要 entry 的唯一 ability 引用为同名、ID `Metal`。名称连接和 Java 注册已经可以独立验收，但不能为剩余对象提供表示或省略许可。

| JSON pointer，省略 `/0/Default` | 原值 / JSON 类型 | 当前 Java 可观察事实 | 目标与实施决定 |
| --- | --- | --- | --- |
| `/LKLPDIMKLKI` | 对象，恰有4叶 | `AbilityData` 没有此字段或 alias | owner/type、producer、consumer均未闭；不变成空能力 |
| `/LKLPDIMKLKI/elementDurability` | 100 / number | `AbilityModifier.elementDurability` 是另一条 owner/path 的 `DynamicFloat` | 同名不能把根对象子字段移进修饰器；目标字段未知 |
| `/LKLPDIMKLKI/minus_one` | -1 / number | 无相应 `AbilityData` 目的声明 | 不解释为 duration、thinkInterval、sentinel 或默认值 |
| `/LKLPDIMKLKI/IAKPBCLBNKF` | -1 / number | 无相应目的声明 | 不按与上一叶相等的数值合并字段 |
| `/LKLPDIMKLKI/isLimitedProperties` | true / boolean | `AbilitySpecialsAdapter` 能把布尔转1/0 | 该适配器不是目标语义来源；此处布尔转float不批准 |

实际扫描固定 structures 的89748类型、445462字段，以上5个名称及 `defense/defence/common/configCommon/abilitySpecials` 的**字段精确命中均为0**。`abilityName`只有无关 owner21173/`ConfigBossAbility.field100104/+10` 一个命中。不能将该命中移到武器能力。该结果只说明直接字段名对照不足：目标成员大量混淆，不能据0命中宣布客户端没有这些职责。

实际 Java 有两条不同的消费路径：

- [AbilityData](../src/main/java/emu/grasscutter/data/binout/AbilityData.java) 的 `Map<String,Float> abilitySpecials` 经 `Ability` 构造器271–273行复制到实例参数 map；[JsonAdapters](../src/main/java/emu/grasscutter/utils/JsonAdapters.java) 的适配器将布尔变1/0，忽略null或非标量。把未知对象强接在此会改变类型和后续参数可见性，加载不报错并不证明这个转换正确。
- [AbilityModifier](../src/main/java/emu/grasscutter/data/binout/AbilityModifier.java) 的 `DynamicFloat elementDurability` 经 [AbilityDurabilityState](../src/main/java/emu/grasscutter/game/ability/AbilityDurabilityState.java) 的 `materialZero`184行读取，求值后正数才触发材料移除 callback。这里是**已登记修饰器**的字段；当前没有根对象→该修饰器的绑定。因此不能因同名就把100接给这条行为。

Q06的另外两个Default也有相同4叶对象，Wood修饰器又含若干相同叶。这些都来自同一候选文档，属于形状旁证，不是独立目标 producer/consumer。旧7.1/本机同名空Metal表示不参与填补本项未知。

## 有源能力类型图：确认注册职责，未确认 JSON 字段

固定签名与已有原窗能给出以下准确关系。这里的字段名、类型、offset来自 metadata；指令来自已经保存的旧原窗，未执行或新提取。

| 目标声明 | 已有来源和实际静态关系 | 未批准的对应 |
| --- | --- | --- |
| 41992.method350587 `OIHJKNGHDFM@1500e6670` | 参数为 `Dictionary<string,Dictionary<string,86828>>`，instance32986；原`1500e675f`注册内层key，再`1500e6769`将value传350583/`1500e3590` | 不据双层字典称某一层就是 JSON `Default` |
| 86828.field432849 `IIBMNPFLNMJ/+20 : string` | `1500e35eb`取此string，`1500e35ef→AddAbilityString/1500d5480`；另718692/`14b7541f0`接收 string+76549，`14b754224`写同一+20、`14b75422b`注册 | 该职责确在能力名称路径；未取得同源Metal实例值或Q06 bytes关联 |
| 86828.field432847 `GPPBBDEICKP/+10 : Dictionary<string,76549>` | 718692读76549.field382839/+d0字符串，在`14b7542bb`交原字典插入；350583在`1500e3679`读取此map | 不从字段形状直接批准 `modifiers` alias |
| 86828.field432870 `OMAIGDMCJBO/+c8 : 76549` | 独立 metadata 表明，它与上项map value为同一type；单对象与map不是一种表示 | 不能将 `LKLPDIMKLKI` 叫 default modifier；所复用原窗未把它接到四叶 |
| 86828.field432868 `LOEHLJGBGAC/+b8 : Dictionary<string,24329>` | `1500e35f4`取map，遍历key，`1500e3667`调用AddAbilityString；24329.field117888/+10是System.Object | 不能将它直接叫 `abilitySpecials`，也不能先把其值约成float |

旧 `producer-static.py` 先以PE exception范围取完整code再保存asm/hash，但 `producer-static.json` 的caller记录没有另存end字段。本项保留旧code hash，不从末条反汇编猜PE end，不宣称再次核过原bytes。准确复用摘要：`14b7541f0`/`d35eea88e78947fe3719073dffb55304570c26376e2301edb25bddc2320bb12b`，`1500e6670`/`0eaa4b661f921e139679f88ac3c1530cd576d216451c778ffc0f6f4f20430f7f`，`1500e3590`/`145ce1fceeac91530059a5cce199072d4865dfa5e768a14ad3f30436e185cb16`。后一个有3个caller记录，asm与hash一致，去重为1窗。

这张图建立了 target 类型和注册 consumer 的独立来源，尚未建立 `LKLPDIMKLKI → 某field → 四叶 → 各自consumer`。当前可以列出准确后继，不能给生产 Java 转换规格。

## defense 与 common

Q05 `/Weapon_Sword_Metal/combat/property/defense=999999` 是已知原值；Java声明为 `ConfigCombatProperty.defence : float`，没有alias。[EntityBaseGadget](../src/main/java/emu/grasscutter/game/entity/EntityBaseGadget.java) 的 `fillFightProps`99–101行实际取 `getDefence()` 写基础和当前防御。故这是**有Java消费目的但缺目标职责和字段对应**的损失，不能靠英美拼写相似批准alias。

Q05 `/common` 有标量和嵌套 `effectAttachShape`；Java为 `ConfigEntityBase.configCommon`，而 `ConfigCommon` 当前是无字段的空类。即使只补 `common` alias，也只是将null变成空对象，内容仍会丢失；不能称配置迁移完成，更不能由没有 Java getter消费推断目标无需这些数据。配置基类37853的18个真实成员仍未按职责映射，本项不从形状给它们命名 common/combat/abilities。

完整选中配置仍是候选专题的255叶/7精确Java声明/248未映射；本项没有减少这248，也没有把未知变成optional。

## 主线最小新窗申请：本批仅1窗

**立即请求的唯一新完整原窗为 owner25270.method196497，`CNLIBMNOALB@14b1321a0`，RVA `b1321a0`。** 这是准确 producer 调用边，不是结构猜测或未知虚槽。

| 合同项 | 准确值及边界 |
| --- | --- |
| 原直接 caller | owner17586.method133173 `FOIHJKNECJI@14aa9b160`；旧完整范围`14aa9b160..14aa9b3aa`/586B，原code SHA `7464bfb3466a30ff539c7e4c4af5b3763e0700ba0e01507aa0ad2443c1ed8566` |
| 直接site/实参传递 | `14aa9b27b→14d6f9210`得到reader；`14aa9b280 mov rcx,rax`；`14aa9b283 lea rdx,[rbp-20]`；`14aa9b287 call 14b1321a0`。后面取该slot交另一个原callee，不预先命名其合并语义 |
| 目标声明 | 25270/`AIBBPDMJMHB`，method196497，flags`0x93`，静态，2个显式参数，virtualSlot65535；继承37853 |
| 参数一 | parameter165423/typeReference86325 → reader type42005/`OFMKHIMCGKE`。该类型含byte[]/+10、int32/+18和+1c、u64/+20、int32/+28；不预先给这些状态字段命名，不补reader内容或flag |
| 参数二 | parameter165424/typeReference522739 → **byref** `Dictionary<string,25270>`，concrete instance11687。这是输出slot地址，不能用map对象指针替换byref；返回类型未恢复，不猜返回值 |
| 独立缓存旁证 | 17586.field83178，GLOBAL+21400，是同一instance11687；此前具名查询owner17591.field83188/+30也是该类型。不能据同类型声称该静态分支已经供应Metal |
| 需要的交付 | 主线在stock互斥允许时核固定五输入，取得目标entry的**准确PE exception extent**、完整原bytes/SHA、反汇编和metadata owner/index/flags/签名；只恢复这一窗实际使用的type/usage/generic实参和直接call边 |
| 本窗回答的问题 | 字典value如何产生，实际调用哪一个对象parser，reader输入与输出slot如何传递；将后续内容研究接到真实producer |
| 硬停止 | 本批只1新窗。首个未审callee、未知虚槽、必要reader内容/asset bytes缺失就记准确site/input并返回；不自动取第二窗，不执行native，不造字典成功项，不设置初始化flag，不给字段业务名 |

该窗本身不保证解决所有语义。仍需同一 `Weapon_Sword_Metal` 的原asset u64、原bundle/配置bytes、真正17591 producer/状态、具名25270值，以及配置内的实际能力引用；这些和 [String共同合同](CLIENT_72_GADGET_STRING_COMPARER_INPUT.md) 分开验收，不用本项报告代替String后继。

能力专线的准确 metadata 声明已可定位：86828.method718684 `CNLIBMNOALB@14b7528d0`，reader42005参数；字典parser718697/`14b754c10`有同reader与byref `Dictionary<string,86828>`/instance5893。**本批不申请提取这两窗**，也不预先认为它们必由25270调用。待主线另发能力分项且同源Metal bytes/producer入口明确后，才能选择真实必要parser和四叶consumer；上述3个既有注册caller应直接复用。

## 实际执行、复现和状态

本项没有新增仓库工具或测试；没有为报告新增一套功能测试。只新增本专题及私有标准库分析脚本，脚本只读取表列冻结来源，输出来源/声明/签名/旧窗和准确缺口。没有Git、网络、clone、构建、JVM、DB、进程端口、GAME_PATH身份、REA、新PE提取、native执行或客户端操作。无需服务重启，提交及共享索引由主线审核后统一处理。

```powershell
python local/migration72/d02-parallel-20261109/r3-gadget-ability-semantics/analyze.private.py --output local/migration72/d02-parallel-20261109/r3-gadget-ability-semantics/analysis-first.private.json
python local/migration72/d02-parallel-20261109/r3-gadget-ability-semantics/analyze.private.py --output local/migration72/d02-parallel-20261109/r3-gadget-ability-semantics/analysis-second.private.json
```

已实际执行上述双fresh，19来源pin、445462字段精确搜索、10方法声明/签名、4叶原值、旧caller直接边与3旧能力窗的去重/准确site断言通过。两报告各62295B，逐byte相同，SHA `70ed885f21b9c8e979d17b115ef95718addcd1e19dfbb113f87c6904796d50c8`；私有脚本SHA `103deaea09f959903920d576aaa4ea15a9e5e7613d763a0d4f251610546165a5`。这是静态分析复现，不计作Java、协议、目标行为或实机测试。

调查中一次临时只读打印误将 `ability-paths.json` 的顶层list当dict，AttributeError后改为按实际结构读取；输入未变，也未调用它的producer。另有最初尝试读取不存在的 `tools/extract_client72_structures.py`，随后直接使用现成固定structures，未创建/运行替代提取器。

本项交付为**有界结论与单窗申请**：未知对象、四叶、defense/common保持unknown，生产转换/alias和空能力许可均false。候选loader的主线实际Java验收记录由候选专题维护；本项不复制其测试数或升级其范围。配置真实注册/命中、完整Ability执行、resourceReady、R3与D-02客户端六项验收均未由本项完成。下一项可执行任务是主线按上表取得196497这一窗并回报首缺。

## 196497 单窗后继：主线已提取，value 首缺定位到196498

上方单窗申请及其历史结论保持。2026-11-09 UTC15:22，主线在五stock串行门禁后实际取得唯一批准窗；本后继只读取该保存结果、固定metadata/签名和已有报告，没有再次读取PE、检查GAME_PATH或执行目标。主线固定EXE仍为 `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`。

| 新固定输入，均在本卡 `main-parser-window1/` | bytes / SHA-256 |
| --- | --- |
| `manifest.private.json` | 47478 / `d3685968bd816e86d3c53234e8d13d9edd42c0a03a87f74eb0619d6876e084e0` |
| `parser.bin`，`14b1321a0..14b1326df` | 1343 / `cd472e403f50e6f777b17333f38348330ef2fe6d37ebf140c0d4c23062d774b4` |
| `pdata.bin`，唯一原记录 | 12 / `bebc70748f2dd39263f2afdd1ffe1d668c8506476385c7dad5d8bd2c2fed4a67` |

标准库复核了manifest、保存bin、manifest bytesHex与323条指令bytes逐byte相等，VA连续覆盖准确1343B；pdata三元组为begin RVA `b1321a0`、end `b1326df`、unwind `4199f1c`，与同一窗相符。所有外部direct call/尾跳转的rel32目标另由保存原bytes重算；共13个不同外部target。这里检查的是已提取原窗的内部一致性，不能据此宣称执行过13个callee。

### 字典、reader与byref输出的准确静态传递

| 阶段 | 原site及已闭事实 | 保留边界 |
| --- | --- | --- |
| 入口保存 | `14b1321c3`把输入RDX存`[RBP]`，`14b1321d0`把RCX reader保存R14；与196497的42005 reader及byref instance11687签名相符 | 没有为任一地址供应运行时对象或内容 |
| reader状态 | 原`+20 != 0`时，`14b1321e2/29f/2f1`分别以4、2、解出的长度调用42005.method350685 `MBCIMMBMGHJ@14f614760(int32)` | 未审该helper行为，不将+20强设0，也不假定它不改变offset/byte[] |
| 计数 | `14b13220c`从byte[] payload、实际reader+1c位置取u32，XOR `f21f1e02`；`213`将原位置加4写回。解出0时`217→663`走独立尾路径 | 这是该窗静态表达式，没有独立真实配置payload样本；不推广为其他表codec |
| 非零map产生/输出 | 原`21d`的RIP cell `14589ea40`，由metadata usageRow703477/source95469独立解为 `Dictionary<string,25270>`/instance11687；`224`分配后，经`254`的实际间接initializer，`25a`将map指针写到原`*outSlot` | initializer来自真实context的`[owner+30]+10`、函数取伴随`+8`，实际指针/代码未读取；“写出map”只指正常返回分支 |
| key | 当前窗解u16长度、搬运并逐8B XOR临时buffer；`4b7..4bf`把**同R14 reader**、解出的uint32长度、临时byte*交50379.method418862 `OEJADFIGFAC@14f0de820`，返回值保存RSI | 从后续typed插入可知其结果用作key；该helper的返回类型未由签名恢复、字符编码/reader副作用未审。不套旧host string模型 |
| value的准确来源 | `14b132500 mov rcx,r14`；`503 call14b135cc0`；`508 mov rbx,rax`。metadata独立给该callee为**25270.method196498 `ILGFIEGBNOF`**，flags`0x96`、静态、仅1个显式42005 reader参数 | 没有此callee原窗，不能断言它内部直接new对象、调用虚parser、查cache或返回哪一具体子型 |
| key/value提交 | `50b/50f`经原outSlot重新读取map；`53e RCX=map`、`541 RDX=key(RSI)`、`544 R8=value(RBX)`、`547 R9b=1`，`54a→15787d2a0`；第五参为真实context槽，`54f/556`按原计数继续 | 本项仅闭寄存器/typed目的关系；未重新提取或执行该共享插入例程，不从selector1猜重复键/错误语义 |
| 零计数 | `663`取cell `145990820`，`66a RCX=原outSlot`，恢复栈后`681`尾跳`154cd6650` | 此callee未审，不能把结果预写成null、空map、共享默认map或可省略许可 |

因此当前可以准确说：**这一窗逐条取得key，再把196498返回的value交给string/type25270字典；具体配置对象字段没有在这一窗内直接恢复。** 它不是已证明的完整Metal对象构造，也不是从host造一个25270成功返回。

key长度的局部静态表达式为 `((raw_u16 + d388) mod 65536) XOR 4357`；内联块XOR值为 `5c8e4357221cd388`。长度0时在`14b132370`加载cell `145acae50`后直接到value入口，没有该cell的当前字符串值。长key涉及原TLS/临时分配与释放路径；本项不读取TLS、不跳这些路径、不实施解码器，以上静态表达式没有被当作独立样本验证或生产转换规格。

### 真正剩余缺口与下一单窗合同

**对象内容研究的下一准确未审入口是196498，而不是先猜基类虚槽或JSON字段。** 原生可执行性的首缺仍更早包含实际reader/资产输入、条件350685、间接initializer/context及key helper；本项没有声称原生环境已经能自然到达`14b132503`。独立列清这两层，不能用静态call存在消除运行输入缺口。

供主线另审的下一最小取窗合同如下，本后继没有执行该申请：

- 唯一目标：owner25270/`AIBBPDMJMHB`，method196498/`ILGFIEGBNOF`，entry `14b135cc0`、RVA `b135cc0`；直接来源为当前固定原窗`14b132503`。parameter165425/typeReference86325确为type42005 reader，flags`0x96`、virtualSlot65535；返回类型仍未恢复，当前只根据RAX后继用途确定其value职责。
- 只取得该entry准确PE exception extent、完整原bytes/SHA、12B pdata、该方法metadata声明/参数与必要usage。建议继续沿用单函数8192B上限；若超限，只记真实范围返回。不扫描周边、不追callee/虚槽、不执行native。
- 本窗只回答“reader如何变成当前typed字典value，是否分配/派发/读取、下一对象parser究竟是哪一个”。首个未审直接callee、未知虚槽或必要payload立即报告并停，不预授权25270.method196496或37853.method316016。

即使取得196498代码，仍需同源 `Weapon_Sword_Metal` asset u64/bundle/原配置bytes、真正reader的byte[]/起始+1c/+20状态与producer，以及JSON成员→目标字段→独立consumer对应。Q05/Q06不是这个缺失reader的替代输入。`LKLPDIMKLKI`四叶、`defense/common`、完整目标配置/ability匹配没有新增通过；不批准alias、空能力、optional、resourceReady或D-02。

### 后继实际复现

只在新独立子目录 `parser-window1-analysis/` 保存私有标准库脚本和报告。原19pin分析脚本、双fresh `70ed885f…50c8`报告及13审核原件均冻结，没有覆盖。

```powershell
python local/migration72/d02-parallel-20261109/r3-gadget-ability-semantics/parser-window1-analysis/analyze.private.py --output local/migration72/d02-parallel-20261109/r3-gadget-ability-semantics/parser-window1-analysis/analysis-first.private.json
python local/migration72/d02-parallel-20261109/r3-gadget-ability-semantics/parser-window1-analysis/analyze.private.py --output local/migration72/d02-parallel-20261109/r3-gadget-ability-semantics/parser-window1-analysis/analysis-second.private.json
```

上述双fresh已实际执行。10个完整来源pin、1343B/12B原件、323指令覆盖、rel32目标、typed map usage、准确关键site及196498声明/42005参数核对通过。两报告各20633B，逐byte相同，SHA `55eb43ebd5f5f4c960229ab66e29d4dcc05bffcfa7a0aa9a6a031a671f1afb9b`；私有脚本SHA `761ad195090fca78bd6acd2a521709826f786568b900a078f63bf26a1596d593`。没有新增公共工具/测试，不计Java、native、协议行为或实机测试；本后继Git/网络/构建/JVM/DB/端口/现场/新PE提取/原生调用均为0。主线另审该value入口后再发布准确后继。

## 196498 单窗后继：选择码工厂与运行时parser派发

上一后继的主线第三报告同为20633B/SHA `55eb43eb…afb9b`，10项主线审核摘要SHA `f90e5c2b534102604e72fc48430506bc38ff2a3ca4542d20a29d6f0293a1f3cd`，仅作为本后继固定输入。主线随后在固定五stock和13保护核对后提取196498唯一完整窗；本后继没有再次执行这些现场门禁或新取窗。

本轮接手专题原文21965B/SHA `ea426d7f2be001a93b5269e49818288fadccb311f1a3fd99a7294120cbc4d4ef` 已先保存到私有 `value196498-analysis/document-before.private.md`，此前各节和冻结脚本/报告全部保留。本节只增加167B的限定事实。

| 固定新输入，相对 `local/migration72/d02-parallel-20261109/native-successor-windows-main1/config-value196498/` | bytes / SHA-256 |
| --- | --- |
| `manifest.private.json` | 10193 / `1678832dff0d4934ae11ad5bf4fd9c646a1925b724183f5fb60f526c69a5af6d` |
| `code.bin`，原范围`14b135cc0..14b135d67` | 167 / `9e635fb07bdbe93ddb1bb4b33c573d5d6eaa58d238dfbb2c2df9dc1f11379575` |
| `pdata.bin` | 12 / `73aabdb1feacbd7841e8639be17a0588b1cfa190230f691a2c012ff4777756a9` |

原pdata为begin `b135cc0`、end `b135d67`、unwind `419a2ac`。保存的code、manifest bytesHex及49条指令bytes连续覆盖全部167B；13条相对call/跳转的目标均从保存原bytes独立重算。本项只分析这些已存bytes，没有重新读取EXE、执行原指令或跟随任何callee。

### 实际流程和new/构造边界

**196498是“读取一个选择码 → 工厂取对象 → 验兼容类型 → 调实际虚parser → 返回同一对象”的包装入口。** 167B本身没有给出具体对象new、构造器或字段parser的直接绑定。

| 原site | 准确静态事实 | 尚缺 |
| --- | --- | --- |
| `14b135ccf` | 输入RCX reader保存RSI；仍为签名中的type42005 | 实际reader、byte[]与起始位置没有取得 |
| `cd2..ce1` | 实际reader+20非零时，以同reader及整数1调用原42005.method350685/`14f614760` | 不设+20为0，不推断此helper无副作用 |
| `cef..cff` | 从reader+1c位置读取byte，随后将该位置加1写回 | 原始选择byte未知；不是4字节字段 |
| `d02/d04/d07` | AL加`35`发生8位回绕，再零扩展ECX交`14e8a84a0`。固定metadata与签名独立给 **owner62017 `NPJMDHONOMP`、method514570 `PCKPBPDHLNB(uint32)`**，flags`93`、静态 | 该工厂内部new/构造、缓存或注册表选择均未审；不按名字猜其实现 |
| `d0c..d37` | 工厂非null返回保存RDI；cell `14589b1b8`由usage702867/source95470绑定expected type25270；按下述真实类层级规则检查 | 返回对象的具体klass与该cell当前指针未取得 |
| `d45..d4e` | 通过检查才以RCX=RDI对象、RDX=同RSI reader、R8D=原指令0，调用`[actual klass+100]` | 真正对象parser是这一运行时目标，尚无实际函数指针/代码绑定 |
| `d55→d3b` | 虚调用后把RDI放回RAX，返回原对象；该虚调用的RAX结果未被采用 | 对象字段和reader的实际变化未知 |
| `d0f/d2c/d37→d39` | 工厂null或类型不兼容走`xor edi,edi`，返回0 | 仅此分支静态返回零；不是配置为空或能力可省略的许可。选择byte此前已消耗，本窗没有reader回退写入 |

类型检查没有要求返回对象klass与25270 klass直接相等。它先比较 `actual klass+c4` 与 `expected klass+c4` 的无符号byte；实际值较小时拒绝。随后取 `actual klass+48` 数组，要求其 `[expectedByte-1]` 指针等于expected klass。故兼容的子类型也可能通过；不许把“能通过25270检查”改写成“实际必是精确25270”。本卡未读取任一自然klass头或层级数组。

固定metadata中，25270.method196496 `CNLIBMNOALB@14b132c20` 确有reader42005参数、virtualSlot6、flags`2c3`。这是**静态声明**，不是上述实际klass的`+100`函数值。196498没有直接调用`14b132c20`，本项也没有据slot或类型形状把它选成已绑定parser，更没有跳过派发去执行其body。

### 下一准确单窗：先恢复选择码工厂

当前真正parser目标仍未知；要解决该派发的必要上游，下一最小源码入口已经精确到**62017.method514570@14e8a84a0**。本节只交合同，未取窗：

- 直接来源：本新窗 `14b135d07 call 14e8a84a0`；唯一显式参数为零扩展uint32选择码，值域来自 `(原byte+35) mod256`，真实值未知。parameter410581/typeReference124为uint32；返回类型未由metadata恢复，只能按本caller用途确认返回值被当作对象候选。
- 只请求该entry/RVA `e8a84a0` 的准确PE exception extent、完整原bytes/SHA、12B pdata及本方法必要metadata/usage。建议继续单函数≤8192B；若实际extent超限，先只报告范围，不截断、不自动提高预算。
- 此窗回答选择码如何对应实际class/对象、是否直接new/构造、是否还依赖注册表，以及由此能否确定parser派发。首个缺callee、未知注册/selector或虚目标即返回；不追第二窗，不预授权196496或基类316016。

若工厂能从源码固定具体class，再据真实override/实际slot来源决定下一parser；若仍依赖自然对象或注册状态，应提出那个准确最小输入，不能填一个25270对象或借旧vtable求成功。执行层仍先缺同源Metal原asset bytes、实际reader+10/+1c/+20与选择byte，以及更早的reader helper、map initializer/key helper/context条件；本项不声称这些已足。

共同String输入的主线批准范围仍仅图和getter返回，既不含Equals/FindEntry，也不含配置/能力通过；它不能替代本节工厂、自然reader和实际parser输入。本项没有将共同String wrapper图插入此reader或改其许可范围。

### 本轮检查与状态

私有新增仅在 `r3-gadget-ability-semantics/value196498-analysis/`，没有新增公共tool/test。实际执行：

```powershell
python local/migration72/d02-parallel-20261109/r3-gadget-ability-semantics/value196498-analysis/analyze.private.py --output local/migration72/d02-parallel-20261109/r3-gadget-ability-semantics/value196498-analysis/analysis-first.private.json
python local/migration72/d02-parallel-20261109/r3-gadget-ability-semantics/value196498-analysis/analyze.private.py --output local/migration72/d02-parallel-20261109/r3-gadget-ability-semantics/value196498-analysis/analysis-second.private.json
```

两次fresh实际核8完整source pin、167B/12B/49条指令覆盖、13条相对转移、准确选择/兼容/派发/返回site及514570的uint32参数。报告各12975B，逐byte相同，SHA `3fb9165f741a1831bd328c4183ad20f4f79a6fa24a6a3c7bbf4d99f8d4a8b7dd`；私有脚本SHA `9e24f34dfdba7c884529f34c67f9ecc9eb773fba47d8bd5958193adc63c7e35b`。这是保存原窗的静态复现，不计native、Java、协议或实机测试。

实际新PE提取/native/JVM/客户端/现场/网络/Git/构建/DB/进程操作均0。LKLP四叶、defense/common及完整target匹配继续未知，alias/空能力/配置能力就绪/R3及D-02均未由本项通过。上述514570单窗是研究返回的候选，主线已因D-02必要性未充分而暂缓，没有自动批准或继续提取。

## 主线静态验收

初始19pin/双报告、字典parser10pin/三报告、值工厂分派8pin/三报告均保持原字节；分别只覆盖列明的静态表达式，不称原生行为测试。字典parser主线10项审核 SHA `f90e5c2b534102604e72fc48430506bc38ff2a3ca4542d20a29d6f0293a1f3cd`，值工厂分派主线8项审核 SHA `039eaacb688e1c87007da939b3b3e4785456112f840284e21c3ddf78981a0518`；原始JSON在本专题对应 `parser-window1-analysis/` 与 `value196498-analysis/`。

最终收尾复核10项通过，证明 `local/migration72/d02-parallel-20261109/closeout-20261109T164643Z/static-main-review.private.json` SHA `21fa138b4f1cde17bff5967c36c30761a512c1e91792e0a67af4c684c7518a1a`，另核必要性审查31个源pin。该复核没有新增PE、native或JVM执行。原文 SHA `7e39b2895b296a8b0c963ba310b0cd46895febf19a07a3ba43eab54b3831a008` 已备份到同一closeout目录 `before/docs/CLIENT_72_GADGET_ABILITY_SEMANTICS.md`；旧报告涉及历史文档的pin用该快照追溯，不改写原证据。

196498只证明选择码来源、兼容性检查及动态klass+100调用边，不能固定到196496 parser。reader实际状态、同源原asset bytes、selector/实际类和字段职责仍缺。没有批准alias、空ability或省略未知对象，也没有把全部248个未映射叶扩大为D-02必需。
