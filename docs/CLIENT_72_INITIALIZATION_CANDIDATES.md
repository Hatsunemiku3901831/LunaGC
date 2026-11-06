# CN7.2初始化三通知候选与PlayerData原始对象图

2026-11-06，F-03d1a限定离线取证。**PlayerData候选的2类型9个wire字段已独立执行；消息身份和字段业务名仍未验证。** F-03d1b、d2、d3及完整初始化/P0/D-02没有因此完成。本文不修改生产Proto、包号、三层映射或客户端。

## 固定输入与来源层次

正式工具[trace_client72_player_data.py](../tools/trace_client72_player_data.py)每次实际读取并校验`extract_client72.HASHES`三文件，再重新提取结构、Cmd绑定及选定AOT记录：

| 输入（相对GAME_PATH） | SHA-256 |
| --- | --- |
| YuanShen.exe | `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| YuanShen_Data/Managed/Metadata/global-metadata.dat | `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a` |
| YuanShen_Data/Managed/Metadata/startup-metadata.dat | `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105` |

AstaPS只作为候选命名出处：固定`a49f51aa6f2787ffbc25cee46801b70dee860d23`，本轮实际复核HEAD及干净状态。其三个Proto的SHA依次为PlayerData `f6974d5ac3235ad7b6a83e9abfc8f74c95fb1ebef6b69e1c485acd48900493aa`、PlayerStore `b316df1634b00fe2521e29aadf47653d93b4249e8758bf3c0a73ebdea0c96fa2`、AvatarData `874451d794506dd92c28639ad8ac416eee80d82420327244c00b9200c2b59ea4`。原始参考与解码/反汇编留在忽略目录，不复制其Proto或实现。

| 用途候选（Asta名） | 目标静态Cmd→类型 | getter / reader | 独立目标业务身份 |
| --- | --- | --- | --- |
| PlayerDataNotify | 7193→79263/BIMKHOFCKHB | `0x14e420720` / `0x14e420460` | unresolved；5外字段和map子图已验raw，尚无具名消费证明 |
| PlayerStoreNotify | 20892→68537/CAIHCNHAMHB | `0x149420040` / `0x1494200e0` | unresolved；未执行此reader/Item闭包 |
| AvatarDataNotify | 20143→30509/ILEPMBEJKIA | `0x152de0cb0` / `0x152de03c0` | unresolved；未执行此reader/Avatar闭包 |

“目标静态Cmd→类型”来自固定客户端getter调用链，候选业务名来自独立的Asta文件；二者不能合并为已验三层身份。未沿用本仓库旧3272/22160/27799。

## 当前发送序列与最小取证卡

本仓库`Player.onLogin`先执行活动/图鉴/恢复、队伍修复、World/场景接纳和每日/任务初始化，再直接发送PlayerData → 已迁入容量通知 → PlayerStore → ItemCdGroupTime → 队伍技能刷新 → AvatarData → AvatarWeaponSkinData。后续还有商城、进度、任务、纪行等管理器和包；该顺序不是已验证的客户端最小必需集合。旧测试号的LoginReq处理先onLogin后LoginRsp，新号走显式选人返回；需要分开验收。

| 子卡 | 精确最小证据与待验证位置 | 可复用部分 |
| --- | --- | --- |
| F-03d1a raw对象图 | type79263五外字段；map<uint32,type78350>；子值uint32、独立int64和boxed int64/float oneof；非空map/覆盖/64位/错误边界 | Reader72原语、GraphReader72的ReadMessage/limits、MapRuntime72的uint32/message adapter/setter。本文新增专用子类，不改公共工具 |
| F-03d1b 身份与字段业务 | typed handler `HBIJFOJIFAH.GKEKNELDHOK/0x1507bc0e0`；string+20→type58013模型+70，bool+28→同模型+15c，u32+2c→+178，map+18→`0x152f7f670`；u64+30与包头字段经`0x14e697940/0x14e699670`应用。须找独立具名目的地并执行值编码/缺省消费，不能按形状命名 | 已验raw图；签名参数和本机`init-three-consumers.txt`。本轮全局literal有界搜索未找到三消息名/ServerTime/FirstLoginToday；NICKNAME命中尚未与上述模型+70闭合 |
| F-03d2 PlayerStore | 外层list→type65333/NDGHFPMJFIM，reader `0x14b8a55e0`。Item有Object+18及判别enum65334+2c，guid候选u64+20、id候选u32+28；必须恢复保护reader中各oneof动态子类型，不能把Object误当叶子。typed消费`0x153caf190/0x14e3dc3d0`，另有`0x150809180/0x14e43be70` | GraphReader message-list与独立u64；库存enum55345及既有容量消费仅能作仓库用途旁证，不能自动给本包身份/weight语义 |
| F-03d3 AvatarData | map→26081/CFCDAEBPLOG(reader `0x1502ce400`)，list→48178/ONGOHFDHHNI(`0x14f676d80`)、14786/PPJCCBIDNNH(`0x149778220`)；另有uint32及uint64 packed lists和两个标量。唯一typed消费`0x1507cee50`。48178直接消息/容器边包括78350、66100、27246、13438、40807、53824、16352、27196、66308，须先按实际builder所需字段定位，不能整棵旧AvatarInfo迁入 | GraphReader/map/list基础；d1a子值raw。元数据直接class/generic边递归得到51类型331存储字段的研究下界，**含未发送扩展且未展开Object/oneof，既非最小builder闭包也非完整raw schema** |

Store builder实际需要Item的Material/Furniture/Equip/Weapon/Reliquary及TPS分支；Avatar builder还经Avatar.toProto、AvatarTeam、属性/技能/好感和TPS SceneWeapon等依赖。以上名称目前只描述服务器业务下界，不能作为目标类型或tag证明。d2/d3本轮只审计，不启动实施。

## F-03d1a实际raw结果

正式脱敏摘要为[player-data-wire-observations.json](../protocol/cn-7.2/player-data-wire-observations.json)，所有字段保留目标原名。oneof两分支用`BNJKCHONGGE_int64/float`作研究区分，后缀不是客户端原字段名，也不是业务别名。

| 类型 | tag/wire | 目标字段/存储 | 已验wire内容 |
| --- | --- | --- | --- |
| 79263 | 5/0 | PLMNNCGEMIN/+2c | uint32 |
| 79263 | 6/2 | IKIFIBAEDGD/+18 | map<uint32,78350> |
| 79263 | 7/0 | OMFPDOKCLNL/+28 | bool；非零2归一true |
| 79263 | 8/2 | FPOLBCIBIOB/+20 | string；当前原语限制ASCII |
| 79263 | 13/0 | KFICMDLCEHE/+30 | uint64；>2^53与上界 |
| 78350 | 1/0 | NOEEBKJAFAG/+24 | uint32 |
| 78350 | 2/0 | BNJKCHONGGE/Object+18，GDAIBAOLJGK/+20=2 | boxed int64 |
| 78350 | 3/5 | 同Object+18，判别+20=3 | boxed float32 |
| 78350 | 4/0 | AEJAFPNBBHO/+28 | 独立int64，不属于oneof |

目标元数据与AOT表独立确认`classInstance9505=<uint32,type78350>`的`FNNADLCNGFO`为`0x156d35a40`，与shared1541同体；原生adapter为`0x157b22e00`，setter为`0x156d350e0`。实际非空图进入两个具体reader、ReadMessage/limits、map adapter/setter；重复key把旧子值整体替换，旧子值的字段不会并入新子值。`32110807120d082a10ffffffffffffffffff01`独立手写wire得到key7、子tag1=42、boxed int64=-1。外层独立wire `280138014203416263688180808080808010`得到u32=1、bool=true、string=Abc、u64=9007199254740993。

256次原生有界扫描覆盖两类tag1..32及wire0/1/2/5，并对照全部实例存储布局，不把扫描范围当全tag域假设；选中9个字段分支均保留native stores。另14次独立/边界原生图含u32/u64上界、int64正负边界、float=-1.25、oneof来回/显式零、map非空/双key/重复替换，以及重复标量、错wire和空输入。4个坏输入由显式WireInput模型拒绝，不能称客户端原生错误处理通过。

本轮发现旧PropValue三标量假设不完整：目标oneof还有float分支，tag4独立int64与tag2 boxed int64不是同一槽。故不能把服务器现有PropValue直接套上Asta tag后称完整初始化。

## 复现与边界

先从本机配置核对GAME_PATH，输出放忽略目录：

```powershell
python tools/trace_client72_player_data.py --client $env:GAME_PATH --output local/migration72/evidence/player-data-native.json --observations local/migration72/evidence/player-data-summary.json
python -m unittest tools.test_client72_player_data -v
```

两次原生运行的完整报告与摘要均逐字节相同，摘要SHA-256为`455a353974185c885622759247a19b94c905c037545232b29e3cede189bb8cf6`；本机核对见`local/migration72/evidence/player-data-determinism.json`。8项新增回归、包含graph/initialization/reader/literal的42项关联研究回归通过。首关联运行的旧literal测试因缺PYTHONPATH导入失败，设置`PYTHONPATH=tools`后原测试未修改即全42通过；未把该首失败隐去。

模型边界为托管分配、ASCII字符串、boxing、primitive input、generic runtime context、固定容量64的map lookup/insertion及linked-node allocation；原生setter执行重复key覆盖。没有IFix/live ABI、网络分发、handler完整返回、UI或客户端验收，也没有该图不存在的repeated-list覆盖。只改离线工具/测试/摘要/文档，无需重启服务。下一可执行项为F-03d1b独立具名链，d1身份仍unresolved。

## F-03d2a1：库存依赖全清单、外层与两标量叶子

本子卡单独完成root68537、Item65333外层/oneof绑定和51247/40274两个单uint32叶子的raw图，**完整库存raw闭包仍未完成**。工具[trace_client72_player_store.py](../tools/trace_client72_player_store.py)每次重读上述三哈希，重新提取metadata/Cmd，并独立解码全部有界usage组的source与destination。正式摘要[player-store-outer-observations.json](../protocol/cn-7.2/player-store-outer-observations.json)保留15类型、42实例存储字段的完整依赖审计，明确区分四个已恢复schema与11个未恢复schema。

| 目标类型 | reader | 递归边与剩余工作 |
| --- | --- | --- |
| 68537/CAIHCNHAMHB | `0x1494200e0` | tag2消息列表→65333；tag8/+20 uint32；tag14/+24为enum55345的32位raw存储，不恢复额外枚举语义 |
| 65333/NDGHFPMJFIM | `0x14b8a55e0` | tag1/+28 uint32；tag2/+20 uint64；Object+18与判别+2c承载tag5..10六个message oneof分支 |
| 63442/KKOPHGDBCEO | `0x152ee3080` | Item tag5→本类；nested→28471及一个uint32；内容留a3 |
| 44169/BNDIKNLDLOJ | `0x15408aee0` | Item tag6→本类；bool及oneof→74063/64947；内容留a3 |
| 51247/JOPADICJMPJ | `0x14c06c450` | Item tag7；tag1/+18 uint32，已验 |
| 63899/MKFCDFEEAAL | `0x153de6130` | Item tag8；nested→61085及uint32；内容留a3 |
| 40274/MIEKLLOFKOJ | `0x1518c3a80` | Item tag9；tag1/+18 uint32，已验 |
| 14095/JLAGHMMLDCA | `0x149c5c150` | Item tag10；map<uint32,uint32>及packed uint32 list，留a2 |
| 28471/JDDDHIGLMGJ | `0x148fc60e0` | bool及oneof→28473/28474/28475，留a3 |
| 28473/PEMOFGMDGHP | `0x14e457f70` | map<uint32,uint32>及uint32，留a2 |
| 28474/KNNPHAOJMAH | `0x14a870410` | uint32，留a3 |
| 28475/DFPLPBLIEEK | `0x152274ca0` | map<uint32,uint32>及两个uint32，留a2 |
| 64947/FNECPDFLEHN | `0x1504d7b30` | map<uint32,uint32>、packed uint32 list、三个uint32及bool，留a2 |
| 74063/JBBEOMJDLFM | `0x147744650` | 三个packed uint32 list、四个uint32及bool，留a2 |
| 61085/MCBNNKGKJCI | `0x14b6fab30` | map<uint32,uint32>，留a2 |

最初只查global usage组会漏掉28473/28474/28475。实际reader `0x148fc60e0`还从非global组初始化的class cells加载这三类；正式审计遍历从首usage到global组结尾的全部有界记录，逐层结合实例field与reader/.cctor RIP class引用，最终固定15类型集合。42只是metadata存储槽数量，不能当作完整wire字段数；三个oneof的共享Object/判别会展开更多wire字段。

Item六分支的tag与判别均为5/6/7/8/9/10，分别动态分配上表63442/44169/51247/63899/40274/14095，并进入实际ReadMessage和相应reader返回。未恢复的四个直接子分支只运行空envelope，结果显式写`empty-envelope-only`；传入非空63442会被本工具拒绝，没有把空壳当完整nested schema。后续a2/a3必须提供非空内容和组合图后才可关闭完整父项。

本卡512次有界扫描覆盖四个所选类型的tag1..32/wire0/1/2/5，共13个wire字段分支；另21次原生图覆盖u32/u64上界、>2^53、双元素消息列表、空列表元素、六个oneof分支外壳、重复/错wire、两个标量叶子、同分支遗漏保留与显式零覆盖、分支切换后旧值清除。独立wire `082a108180808080808010`直接验证Item u32=42、u64=9007199254740993；`120408011002120608034a020804`验证root消息列表的两个独立Item。原生具体reader、list append与ReadMessage/limits执行；托管分配、metadata-usage初始化、primitive输入及列表runtime context为明确fixture。

五个拒绝分别记录zero-tag、截断varint、u64溢出、nested列表截断及未恢复非空子图。前四在显式WireInput原语模型拒绝，nested列表已走原生ReadMessage但最终拒绝来自原语模型；最后是明确scope拒绝，不称客户端安全边界或完整错误处理验收。

```powershell
python tools/trace_client72_player_store.py --client $env:GAME_PATH --output local/migration72/evidence/player-store-outer-native.json --observations local/migration72/evidence/player-store-outer-summary.json
python -m unittest tools.test_client72_player_store -v
```

新8项与包含它们的38项关联研究回归通过；两轮完整报告和摘要均逐字节一致，摘要SHA-256为`2c14b416e5e18d868bde7d645939f6ea9c2a936761967e555e92538c2d11e20f`，见本机`player-store-outer-determinism.json`。Cmd20892→68537是固定目标静态绑定，PlayerStore/Item/Material/Furniture等业务名仍只作候选，没有晋级身份、生产Proto或运行发送合同。无需Gradle/服务/数据库/客户端操作。
