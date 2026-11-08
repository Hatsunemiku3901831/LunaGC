# E-01/R3-GADGET-CONFIG-KEY

## 实际结果与许可

实际闭合的是 **Gadget40472.field199649/+58 → 原配置key reader → 第一级Dictionary查找/hash**。真实50011101与90000004及原字符串pointer交换均经过原指令，值分别为 `Weapon_Sword_Metal`、`MPLevel`，并实际执行System.String.GetHashCode。配置命中在未审EqualityComparer前保留首缺；**本卡没有批准Java jsonName片段**，没有伪造17591/25270配置值或返回成功模型。+50、完整Gadget/config/ability、自然factory/EntityWeapon生命周期、resourceReady/revision/D02仍未完成。

新 [工具](../tools/probe72_gadget_config_key.py) / [检查](../tools/test_probe72_gadget_config_key.py) 正式双fresh各 **7case/21 native call/3425 instruction hooks**，最长call965；18项检查各全过。双报告各389999B，逐byte相同，SHA `10085ff137a6d918a43adaa2cf27754e4b5c638c9de12c3119705852e65e5abd`。开发首轮加双fresh累计10275 hooks，无失败native call；静态两类提取假设失败均零native CPU，原件保留。

独立标准库 `r3-gadget-config-key/close.private.py` 未导入producer、没有CPU回放，实际复核113来源pin/105去重文件、13个已绑定原范围（12 PE exception+1 metadata叶）、3新窗2330B及5条真实AOT注册/实参records。13是已绑定完整范围，包含未执行的旧loader；5是注册条数，不是5个consumer成功执行。双摘要各5102B/SHA `8bb6a5d0c76751cf2f201ef468906bcca74687404fa5316a956f31ccb4d9dc35`。本卡材料均在忽略目录 `local/migration72/d02-resume-20261109/r3-gadget-config-key/`；`remaining-input.private.json` 是准确下一合同。

## 任务与当前证据

只核固定国服7.2的Gadget40472/+58是否是配置查找key，从而能映射Java `GadgetData.jsonName`；不按值 `Weapon_Sword_Metal` 命名字段，不自动批准+50或Gadget配置/ability完整资源。旧Gadget桥和Java gadgetId片段已由主线验收，本卡不重做或修改。

先读并复用 [Scene runtime](CLIENT_72_SCENE_RUNTIME_CONTEXT.md)、冻结 `tools/client72_scene_runtime_context.py` 和 `local/migration72/d02-active/main/scene-runtime-context-third.json`。真实50011101原92B span的+50为 `Lv1_Weapon`、+58为 `Weapon_Sword_Metal`；独立90000004原54B span的+50为 `Default_Item`、+58为 `MPLevel`。旧原factory在 `15154bfd7` 取row+58交 `14aa97730`，在 `15154bff9` 再取row+58交 `14aa9c630`。后者以string入参调用 `14aa9b8a0`，并直接tail `1524e1f80`；旧报告包含前者完整原窗，但尚无这两个callee的完整字节窗。本卡不执行allocator或伪造连续factory。

Java实际链为 `GadgetData.getJsonName → GameData.gadgetConfigData → EntityWeapon.configGadget/initAbilities`。`ResourceLoader` 从 `BinOutput/Gadget` 聚合JSON按entry key加载 `ConfigEntityGadget`。本地 `ConfigGadget_Avatar_Equip.json` 已有精确key `Weapon_Sword_Metal`，`ConfigGadget_Drop.json` 已有精确key `Lv1_Weapon`，仅登记候选；它们与旧Gadget行同值不足以证明target职责或能力来源。

## 本轮有界计划

1. 固定当前 `tools/game_path.ps1` 解析的五stock、签名缓存、metadata原件、旧Scene源码/报告及Gadget payload/span，记录长度/SHA。先复核旧完整factory/config consumer窗、真实typed父字段及配置缓存声明。
2. 优先零新窗复用旧原consumer；确有直接边才开窗。首批仅完整准确 `14aa9b8a0`（旧 `14aa9c630` 的直接call）与 `1524e1f80`（其直接tail），分别核PE exception extent/原bytes/metadata owner、参数和返回类型。不扫全EXE/泛型图；若这两窗暴露仍未审的必需callee，只能在总≤3完整新窗内选择一个有直接原边者，并先记具体理由。预算不够即返回精确剩余输入，不加第四窗。
3. CPU前须明确已有typed owned配置fixture的原件及边界。现有资源runtime的NativeConfig mode存储不同于ConfigEntityGadget对象，不能混为自然配置。只复用有来源的typed storage/metadata/RGCTX/codec/内存函数模型；没有可复用的typed职责或真实pointee就停原入口，不新造成功consumer/默认配置。
4. 静态前提充分后，新独占工具复用既有bounded调用入口：每call≤2M，所有初始化/parser/getter/readout/失败及正反累计≤500000 hooks，记录每call/累计/stop/model。只parse50011101和90000004准确source span，不重跑Gadget整表或PrimaryIndex。field→consumer为明确独立slice，实际从两个已parse对象读取+58指针，不由host按相同值造成功返回。
5. 最多7 case：两个真实row原字符串pointer；两者+58原pointer交换；50011101的+50真实pointer作为替代key对照；同+58原pointer在明确缺该key的owned缓存；null或空key的原拒绝边界。case只在typed职责和fixture充分时执行；缓存受控、非自然JSON/配置的边界必须逐项保留。+50不会因此自动映射Java字段。
6. 本地资源仅检查两个精确key所在聚合文件、所需ability精确名称及当前Java字段消费；固定上游仅查已有 `upstream-review-20261108/AstaPS-Resource` 缓存/tree和commit `6cfecfb55f889f8e3b7156adb12809608db4f74b`。若本地候选足够，本卡零下载。若确需外部候选，至多两个对应小文件进入本卡private candidate，验证Git blob/content SHA/许可/本地重复，不切换commit、不下载全表或运行上游脚本，不写生产资源。外部版本声明不算兼容证据。

## 验收与停止

允许仅新增本页、`tools/probe72_gadget_config_key.py`/对应test及忽略 `local/migration72/d02-resume-20261109/r3-gadget-config-key/`。独立typed源桥、真实source/readout、原native正反均充分后才批准最小 `{id:50011101,jsonName:"Weapon_Sword_Metal"}` 字段片段，并交主线在实际Java ResourceLoader/GadgetData fixture验证。双fresh完整bytes相同，保留工具/test/输入pin/准确原PE范围及第三命令。

若缺现有typed owned fixture、具名目标类型、真实配置源或必需ability，先保存首缺的准确入口/参数/key/bundle/类型合同并返回主线；不把fixture称为真实JSON/自然配置，不把未知pointee变成功model。配置完整性、ability注册/执行、自然factory/EntityWeapon生命周期、resourceReady/revision/D02分别保留未知。禁止旧doc/source/raw修改、Project/Harness、Git/构建/DB/客户端/REA/现场和生产resources/patch/config操作。

## CPU前准确边界（静态复核后的收窄）

旧扩展原件 `scene-runtime-context-astra/config-producer-static.json` 实际已有 `14aa9b8a0/1524e1f80/1524e2050` 完整extent/asm/SHA；它们未收入旧main报告。本轮前两个callee重提后与旧SHA相同，按旧范围复用，不能算新增成果。主线已核此纠正，确认此前没有type17591配置consumer成功fixture；旧资源runtime的NativeConfig mode也不是配置JSON。

新完整窗只取两个准确直接FindEntry：`14aa9b902→158b48be0..158b49025`、`1524e1fd1→15787cdc0..15787d205`，均1093B；再取由两者原string klass+e0调用、固定metadata明确的System.String293.GetHashCode(method2922/slot2)叶 `159a8da20..159a8dab0` 144B，合3窗2330B。第三为metadata-next-registered-method叶范围，非PE exception；一次错误地只找PE exception的静态KeyError已保存，零native CPU，不用猜end。纯UTF16双5381/33xor/乘0x5d588b65原hash叶没有Random/TLS/global输入。

两层配置cache的完整typed职责现已明确：owner17586/+213e8为Dictionary<string,uint64>/3744，+213f0为Dictionary<uint64,17591>/11691；17591/+30为Dictionary<string,25270>/11687。原 `14aa9c630→14aa9b8a0` 先取前两级，再由 `1524e1f80` 查最后一级。仍不能由此把空配置视为真实ConfigEntityGadget。

3窗已满，String FindEntry在comparer=null的非空bucket路径，实际hash后会从MethodInfo→klass+30→rgctx+20取DefaultComparer类型carrier，调用 `15746c870`，继而用comparer虚槽+110比较。其真实carrier/default及后续原方法未审。本卡不填未知pointee或默认Comparer，不执行第四新窗。

因此CPU只验7个有明确停点的输入：两个真实row+58、两真实+58指针互换、将50011101的+50真实指针换入+58、原50011101在受控无bucket缓存、null+58。前5项执行独立原factory片段 `15154bff9..15154c002`，从实际row+58取key进入完整 `14aa9c630→14aa9b8a0→158b48be0→String.GetHashCode`，在 `158b48cf6` 首次加载未审DefaultComparer carrier之前停止；只称原key路由/hash，不称配置命中。后2项沿原查询/注册控制流返回null，不造row、17591或25270值。实际String类头仅绑定已pin metadata293 vtable，UTF16字符来自真实parser；这是沿用typed managed storage的明确fixture，非自然CLR初始化。

本轮复用冻结bounded resource runtime，不执行Weapon constructor、整表/PrimaryIndex、17591构造/状态写入、配置反序列化或实体分配。只有Gadget两个精确span及既有逻辑readout。唯一native调用入口完整累计初始化、parser/readout和7case，硬限500000 hooks、每call2M。若出现其它原callee/IFix或pointee，保存首失败后停止，不扩窗。即使上述7项全过，也只批准路由证据，Java jsonName许可暂保留false，直到具名配置lookup的必要typed输入/正反闭合。

## 固定来源及原native正反

真实50011101 `[432396,432488)` 92B/SHA `eaf903171c14cfd27367b0bc7012ef7036671d4b7bb0d6c3723d237e6a17b9ad`、90000004 `[1451356,1451410)` 54B/SHA `3288b3a44679bcb4ff775fc52fcb5274e20818c9b58c3ce9bc5d33892198283a`，均用原40472/27264 parser至精确EOF，再执行原uint readout；每行29项已知值与冻结Scene第三一致，完整u64保留。没有Gadget constructor或整表重跑。

| 本轮完整新范围 | 范围来源 | SHA-256 |
| --- | --- | --- |
| `158b48be0..158b49025` 1093B | 原 `14aa9b902` direct call / PE exception | `cfe5db886ab9aeed283b61c4b91736a3177960faef816c492b60d47c157adfc9` |
| `15787cdc0..15787d205` 1093B | 原 `1524e1fd1` direct call / PE exception | `8c079fcdb87ef972aa7337c9740c160eea022393dbd9196692551120f43d4b33` |
| `159a8da20..159a8dab0` 144B | String293.method2922/slot2，metadata下个注册方法边界 | `3f0da425909eb1ae65e34d69ec879c612bfd6355a306b7b1394c066579566e97` |

三级cache的concrete类型来自原字段/调用者rgctx：3744=string→u64、11691=u64→17591、11687=string→25270。准确原AOT表实际只给共享FindEntry注册：地址158b48be0/classInstance3742(object,u64)、156f6f510/59(u64,object)、15787cdc0/16(object,object)，methodDefinition均10194；不把它们改名为concrete直接注册。后继15746c870实际为owner1234.method10288 `EqualityComparer<T>.get_Default`，classInstance4/string及0/object共享一个pointer。它的完整code窗、真实default实例均未读取或执行。

| 原生case | 真实key/hash | 实际停止/含义 |
| --- | --- | --- |
| 原50011101/+58 | Weapon_Sword_Metal /3705035964 | `158b48cf6`，首未审comparer carrier加载前 |
| 原90000004/+58 | MPLevel /1870589383 | 同一准确停点；没有MPLevel配置成功声明 |
| 50011101换入90000004原+58 pointer | MPLevel /1870589383 | 原key跟随真实指针变化 |
| 90000004换入50011101原+58 pointer | Weapon_Sword_Metal /3705035964 | 同一职责不随row ID伪定 |
| 50011101将自身+50 pointer写到+58 | Lv1_Weapon /867824404 | 替代输入只证明+58所供key变化，未判定配置存在/缺失，不批准+50 |
| 原50011101/+58、受控无bucket cache | Weapon_Sword_Metal /未hash | 原 `14aa9c630→14aa9b8a0→14aa97730→14aa9af10` 最终返回null |
| null +58 | null /未hash | 原reader拒绝key并返回null，未到FindEntry |

上述hash是本次System.String的原函数，保留完整u32；它与旧Scene能力名字base131 hash分别留源。后两项null结果在受控缓存/输入中成立，不宣称目标自然配置缺失。全部mutation在离线对象中恢复；没有prefill成功entry、Dictionary成功插入、17591状态/引用计数写入或25270 opaque配置。每条query trace的成功model集合均为空。

## 本地候选与固定上游分类

本轮没有下载外部文件，没有把候选写进运行资源。

| 精确本地来源 | bytes / SHA-256 | 选中内容及边界 |
| --- | --- | --- |
| `resources/BinOutput/Gadget/ConfigGadget_Avatar_Equip.json` | 391235 / `3beef16104a81fabd4c0bd14c8339d6f8eb790cdff65d4fbca55019944ef5993` | 已有Weapon_Sword_Metal键，abilities含Avatar_Weapon_Metal；仅候选 |
| `resources/BinOutput/Gadget/ConfigGadget_Drop.json` | 306710 / `c722c5b701081b41dac866cbdbae92e4c24ce6f00711ad0c70a3c4bb87fbb6f2` | 已有Lv1_Weapon键；只登记，不命名+50 |
| `resources/BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Weapon.json` | 1518 / `dc0f61c3a52dc8cbc34b76dab152cfe9c9e5b9b35edfe19b844758e450e7032a` | 已有唯一Default.abilityName=Avatar_Weapon_Metal；target配置引用/注册/执行未验 |

固定Asta commit `6cfecfb55f889f8e3b7156adb12809608db4f74b`、非截断tree `3cf83488a94b47a018934e5f91c1bf7e93357aae` 中两个聚合文件对应Git blob `ce2b119da8d74e43eeffc93308bfe91919aeee5b`（361168B）和 `86d17d77daca84604bb98ab37f7ed05297b02661`（272493B）。两个本地完整blob均与其不同；未取远端正文，不声称选中entry新增或相同。精确key/ability已在本地，取得外部候选不能解除本卡comparer/真实配置来源缺口。

固定tree列有LICENSE blob `337856c369eb467b1bbf5810ca61cbb8975c6c1f`，正文未在该cache中，本卡没有猜许可文本或发布许可结论。`resource-candidates.private.json` 保留完整本地hash/Git blob/canonical选中entry hash、固定上游tree记录及许可unknown；canonical hash是派生JSON选择项的hash，不是target原native span。

## 首缺与下一最小合同

第一停点是 `158b48cf6`：当前原FindEntry已完成真实字符串hash和bucket索引，下一步将从传入MethodInfo→klass+30→rgctx+20取 `EqualityComparer<string>/1234,instance4` carrier，调用共享 `15746c870/get_Default`，随后使用比较实例klass的slot8/+110。真实carrier/default实例、原工厂及比较职责未验；本轮3窗已满，不填默认实例或返回成功callback。后续可只审这个具名直接callee及其实际首缺，不重做Gadget行或本轮hash。

命中后仍需同一key的实际配置注册链：

1. `GLOBAL+213e8/Dictionary<string,uint64>/3744` 中 `Weapon_Sword_Metal` 的原64位asset key，来自旧 `14aa99dc0→GetAssetPathHash<MiHoYoBinData/type8026>` 注册。当前未恢复该asset key对应的准确target bundle/bytes；本地JSON文件名不给这个值。
2. `GLOBAL+213f0/Dictionary<uint64,17591>/11691` 的真实owner；17591继承41430，其+28为state41431、+2c引用计数、+40原u64源key，+30为Dictionary<string,25270>/11687。需原producer/解析结果，不把状态2、空row或opaque返回值当自然加载。
3. 最后一级在同一真实key下得到具名配置对象/输入来源后，才能审Java `jsonName` 的字段片段；配置/ability内容兼容性继续逐项验证。必要ability候选Avatar_Weapon_Metal的target来源/注册、AbilityManager输入和执行仍独立待验。

当前 `javaProjectionFragments=[]`。本卡不向Java提供完整Gadget/ConfigEntityGadget行。取得上述最小语义/正反许可后，由主线在原独立fixture中用真实ResourceLoader/GadgetData加载最小jsonName片段，断言 `getJsonName()` 和同key的实际配置map来源；完整EntityWeapon构造/abilities/客户端验收仍按真实后继执行，不以null配置通过代验。

## 源码与第三复现

- 工具23483B/SHA `ff4875dc723c00a5d902ceb95ad680ddb034dabf254cb6f0839676f96c814665`。
- 检查11854B/SHA `67f8f653aaea95d5b812d8d8fc0d71b2201815079bb20cdb3663398affc36e81`。
- `probe01` 的原工具/test完整bytes分别归档，SHA与开发报告相符；开发至正式仅增加AOT注册来源/明确false状态及测试断言，没有改变native路径或模型。静态的exception-only/具体实例AOT假设失败原件保留，未忽略或改成功。

主线第三用新输出，不覆盖已有raw；工具每次核当前五stock及固定来源，不启动客户端/服务或附加现场：

```powershell
$gadgetConfigClient = & ./tools/game_path.ps1
python tools/probe72_gadget_config_key.py --client $gadgetConfigClient --output local/migration72/d02-resume-20261109/r3-gadget-config-key/main-third.json
$env:D02_GADGET_CONFIG_KEY_REPORT = 'local/migration72/d02-resume-20261109/r3-gadget-config-key/main-third.json'
python -m unittest tools.test_probe72_gadget_config_key -v
```

主线已按上述命令实际完成第三，389,999B/SHA `10085ff137a6d918a43adaa2cf27754e4b5c638c9de12c3119705852e65e5abd`与双fresh逐byte相同，指定第三的18项检查通过。另审实际工具源码、113源路径、五固定输入和13原范围：12范围由准确PE异常边界独立核对，String hash的144B leaf由固定metadata下一个registered method核对；三个新窗2330B、两个完整原行、原字符串hash及交换、真实null分支、21调用3425hooks和全部未验边界等15项均通过。证明`main-review.private.json` SHA `87d0bf098055b0ebdfcd57ede2b165799dd1263fabdea56994fb0b6acd957252`；独立审核脚本先后误写metadata缓存日期及list访问方式，两个失败版本保留，修正后才写通过证明，未改正式工具/原报告。

本卡验收只关闭准确配置键前缀，未将unknown标完成。仅新工具/test/本页/private，无运行代码或资源变化，无需GS重启；完整Gadget/config/ability、自然revision/loadAll及D-02仍进行中。主线继续核String comparer的最小真实输入，不能据本页先加入Java jsonName片段。
