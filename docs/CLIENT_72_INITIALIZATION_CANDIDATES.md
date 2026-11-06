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
