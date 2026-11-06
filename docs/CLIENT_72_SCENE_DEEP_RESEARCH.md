# F-03d3s2 后继：SceneInfo 引用生命周期与 World 条件路径

2026-11-07，纯离线 D02-R1 深度研究。新增工具、守卫和脱敏摘要；不改公共原生模型、生产 Java、Proto、配置、补丁、资源或客户端。D-02 及完整 SceneInfo/网络 Handler/世界效果仍未完成。

## 计划、必经性与本卡出口

复用 [s1 九类36字段](CLIENT_72_SCENE_INFO_RAW.md)、[s2a 四身份字段](CLIENT_72_SCENE_INFO_CONTRACT.md)、[本地实体](CLIENT_72_SCENE_ENTRY_IDENTITY.md) 及 e8。每个新进程重读 `GAME_PATH` 的 EXE/global/startup，核 `extract_client72.HASHES`，重新提 metadata、原生范围、virtual slots、具体 usage/AOT；没有重扫全部 wire 或重做 Java adapter。

当前生产源码 `Player.onLogin` 先发送 Store/Avatar；`HandlerSceneInitFinishReq.handle` 在 SceneInfo 后发送 SceneTeam/SyncTeam/SceneInitFinishRsp。目标 `1507D1620` 是已验 Cmd21761/type34208 typed handler，s2a 已实际证明首个 Avatar GUID 不存在/类型错误会结束整个列表。故 **Avatar 注册先于 SceneInfo 的依赖已成立**，但服务端顺序不等于已观察的客户端自然消息顺序。

本卡首先越过 `151846096`，验证非空 retain/旧值/重复；随后区分复杂 Object 是否 World 必经、旧实体替换边界和 Handler 后缀。成功出口是可复核的有限语义及直接可执行的实现规格；不能闭合的出口必须保留真实首个停止地址、缺失状态和实机解除条件。不会以空 Object、未知调用成功桩或删除必要通知收口。

## 已实际执行的新结论

### 非空能力保留与释放

`type34045` 的 metadata slot11 是 `147CCF320`，slot10 是 `147CCF3B0`。前者逐个调用五份 message-list 的实际共享 AOT `158DC1870`；后者调用 `158DC2860`。具体 MethodInfo usage 和泛型参数从当前文件重新解出，不能只以共享地址证明具体类型。

真实 `151846000` 在 `15184607D` 增加输入 +14 引用计数，再执行 slot11，最后 `15184609C` 保存到 Avatar 模型 +90。现在已实际越过旧停止点并返回。独立 wire 含 bool=true、两份不同 list、两条77771的 1.25/-2.5，以及重复同形字段合并；64位角色/附属 GUID 使用 `2^53+1` 与 uint64 max，实体ID为高位 uint32。

| 受控输入 | 原生结果 |
| --- | --- |
| 首次非空输入 | 根34045及两条非空77771计数均1→2，五个列表均执行retain；模型保存原输入指针 |
| 重复同一指针 | 无第二次增加；保存同一指针 |
| 用 tag5 独立解码的同形对象替换 | 旧根/子项释放一次，新根/子项保留一次；不是 Python 克隆或按字段名混用两个子对象 |
| 传 null | +90清0，旧引用计数保持；不能把null等同于“释放旧值” |
| 最后一个根引用释放、pool禁用 | 原生释放到0并进入回收helper后返回；不宣称实际对象池回收已执行 |
| 最后一个根引用释放、pool启用 | 真实停 `140514EE0` 数组分配入口；新指针尚未保存 |
| 初始计数 int32 max | 原生 inc 包装为 int32 min；这是内部计数边界观察，不批准网络/生产发此值 |
| 某必需子列表指针为null | 真实到 `1404C2BE0`；先前根计数已增加而 +90尚未保存 |
| 输入lazy标志1 | 在实际 `14A440C40` materialize入口停止，不替换成成功 |

这里的类/vtable/MethodInfo/RGCTX及分配仍是显式托管夹具。列表枚举销毁接口绑定当前目标真实 `List<Object>.Enumerator.Dispose/154FB0700` 的单指令ret共享AOT：该机器码实际执行，**具体现场泛型context并未观察**。presence项是明确不透明metadata token；没有把它们宣称为live ABI。原生 message reader、retain/release、列表遍历/计数和store没有模型替代。

### Avatar 与附属对象能力不能共用一次赋值实现

真实列表已在同一非空输入中完成 Avatar 能力保留、四身份关联，并进入附属对象 `14AD08270`。该方法重新分配34045，写51522模型+90，调用具名 `CopyFrom/147CCFC70`；不是保存收到的原指针。原生 `14A42BD90` 检查目标引用计数，再进入 `147CCF650` 和 `154C79510`，本卡在 **`154C795C4` 读取尚未绑定的泛型clear context之前** 停止。该失败之前的分配是已有s1模型，不是已执行的池构造。

与并行 Avatar 卡交叉：typed Weapon 参数分支 `15184A679` 的 MethodInfo cell `145994388`、usage738059/spec23017/method294224与 s2a 附属工厂完全相同，具体参数为51522；typed Reliquary 分支的独立工厂为87122。可据此采用 **typed Weapon 工厂分支中的 weapon-role 关联**。不把全部51522用途、slot7或所有附属分支都命名为Weapon；旧s2a摘要原样保留。详见 [Avatar 深度研究](CLIENT_72_AVATAR_DEEP_RESEARCH.md) 的正式工厂来源。

### 复杂72829 Object是状态条件消费

重新恢复具名 `MoleMole.SceneScriptData/type45183` 的 `stageType/+70` 及目标 enum66233：World=0、Dungeon=1、Room=2、HomeworldExternal=3、HomeworldInternal=4、BeyondGameplay=5、BeyondGameplayPrepare=6、BeyondSocialHall=7、BeyondGameplaySettle=8。

实际 `1482879C0` 对World/Room/Home及6/7/8返回false，对BeyondGameplay5返回true；Dungeon1实际到 `14CD5EDC0` 当前Dungeon查询。静态续段还要求独立Dungeon资源字段值105。`1507D16DC` 开始的真实 Handler 中段确认：只有谓词true且 `GLOBAL+ABE0` 管理器非null，才进入 `1496A7750` 消费49723/tag12→72829；World条件直接进入角色列表。后者静态先要求72829 discriminator=12、+28精确class37467，然后才retain和后续创建。

因此本卡 **不扩 BeyondGameplay 的37467/72829/54267后代来替代World入场研究**。这不是允许无条件省略72829：候选SceneExcel的 `SCENE_WORLD` 不能替代真实加载后的 `SceneScriptData.stageType`；旧号可能处于Dungeon或其他场景。必须在实机中记录实际stageType/当前Dungeon资源及该分支是否进入，才能批准对应入场场景的必要集合。

### 非空 Handler 前缀的新资源依赖

完整 typed Handler 从 `1507D1620` 连续进入 `1507E41C0`：49723/tag1→89710/tag1→83320。83320两个已验fixed32值（tag2/+20与tag3/+18）依次调用 `1500D85A0`，查询 `GLOBAL+C6D0` 的 uint32→string 表。该目标函数的独立 literal64772/cell `145B48640` 为 `[ABILITY HASH] can't find str from hash = `；资源线的 `AddAbilityString/1500D5480` 和 `.cctor/1500D6270` 同槽写入进一步确认它是能力哈希字符串注册表，不能当普通TextMap。

两个非零/高位hash样本在表为null时实际到 `1404C2BE0`；第一hash为0、第二非零仍失败。双hash为0的**控制**样本执行默认指针分支，向network+108写一条tuple `(tag3字符串指针, tag2字符串指针, tag1 u32)`，原始u32为max，再到 `1507E44F0` 下一消费者入口。默认字符串指针为null是受控夹具，不是正式允许空字符串或省略注册表。

所选输出旧列表清理的 Array.Clear 字节操作是明确模型；选择清理范围、版本/数量更新和tuple写入都是目标原生指令。必要表的自然填充/匹配hash、资源加载顺序和字符串业务字段名仍未闭合。

### 创建、替换与后缀

使用真实 `1517AB3A0` 查询旧实体91并分开设置entity+80是否可用。旧实体不可用时两setter均返回且写92；可用时 Avatar 在 `15184AF37` 组件解析前停止，仍保留91；51522在 `14EAAF0E3` 同类解析前停止，但已先写92。**两条路径的写入时机不同，不能假设替换失败时都保持旧ID。** 查询底层字典和实体是显式夹具，未执行真实teardown。

后缀 `1507D1802`（显式live-in，位于尚未闭合manager返回后）已实际清除两个18295元素、count2→0、version+1，并经明确Array.Clear模型清掉backing array指针后返回。这不拼接成完整Handler返回。

静态manager后继仍有必经内容：`14CD68680→14828FEA0` 接root/tag7、49723/tag7及tag4能力；`14CD6868C→14828BA00` 接root/tag14/type44512。后者读取44512 +20/+24/+18，查旧实体或走 `15154BF20` 创建，后续能力Clone/组件/回调。故World排除了上述Beyond条件，也没有排除Team/44512、普通能力或世界实体创建。新工具保留这些函数当前字节hash与调用边，未把静态边提升为完成创建。

## Sol/Terra 接管规格与禁止范围

本卡允许制作有限独立适配/测试规格；**还不允许接管正式PacketPlayerEnterSceneInfoNotify或放行ready**。

| 目标字段/状态 | Java实际候选来源 | 允许范围与缺口 |
| --- | --- | --- |
| 34208/tag12 u32 | `player.getTeamManager().getCurrentAvatarEntity().getId()` | 复用f2b/s2a本地实体合同；必须对应已注册实体，零ID不是批准默认值 |
| 18295/tag13 u64、tag4 u32 | `avatarEntity.getAvatar().getGuid()`、`avatarEntity.getId()` | 复用s2a完整64位/实体身份；Avatar注册必须更早完成 |
| 18295/tag10 u64、tag7 u32 | typed Weapon 分支的 `GameItem.getGuid()`、`EntityAvatar.getWeaponEntityId()` | 仅有真实武器及匹配工厂/归属时采用weapon-role；零/无武器、其它51522用途及保存恢复实体ID生成策略尚无批准 |
| 18295/tag8 vs tag5 →34045 | 旧 `buildAvatarAbilityVars`/`getAbilityRoots()` 只是候选 | 前者保留原指针，后者独立CopyFrom；raw后代字段业务语义、复制context/池和实际资源值未齐，不从旧AbilitySyncStateInfo直接重命名迁移 |
| 49723/tag1→89710→83320 | 旧 `TeamManager.getAbilityControlBlock()` 只是候选 | 非零hash要求客户端能力字符串注册表就绪；tag2/3必须fixed32，不能改varint；没有批准字符串名字到hash的正式生成合同 |
| 34208/tag7、tag14及其它49723字段 | 旧token/World/Team getter只是候选 | 新静态消费者明确存在，尚不足批准旧英文语义或省略 |

下一次有证据的实现应只改新的独立Scene raw Proto/生成器及适配器、对应包构造和测试；Proto经Gradle/protoc生成，不能手改Java。现有 `PacketPlayerEnterSceneInfoNotify.java`、`HandlerSceneInitFinishReq.java`、`Avatar/EntityAvatar/TeamManager/GameItem` 是待核来源，不在本卡修改。不得直接复用旧完整Proto、默认空复杂对象、空能力名表、0 GUID或省略尚未证明的消息。

最低验收：实际Java getter→独立wire→目标reader/本卡消费者，覆盖两个高位GUID、同形tag5/tag8互换、fixed32错wire、重复/零/省略、旧值清理、未注册Avatar和场景状态分支；再核Store→Avatar→SceneInfo→实体顺序。完整构造、复制、实体与资源闭合前，不把限定测试当成D02-R4完成。

## 仍需的输入、真实停止点与解除条件

| 问题 | 已尝试/真实边界 | 下一条可执行动作与实机解除条件 |
| --- | --- | --- |
| 附属34045 CopyFrom | 已执行真实分配交付、CopyFrom、count guard和clear前缀，止154C795C4 | 提取该具体泛型 MethodInfo 的实际rgctx绑定，执行Clear/Copy非空子项；live中观察池返回对象、引用flag和context，不能以任意AOT或成功stub填洞 |
| 最后引用回收/lazy | pool启用止140514EE0；lazy止14A440C40 | 需实际对象池配置/生命周期输入及托管allocator合同；保留原引用和新store的顺序断言 |
| 普通非空能力前缀 | 缺能力hash注册表时真实null-error | 自然资源加载后记录C6D0来源、目标hash存在性与字符串；用匹配非零输入继续1507E44F0，不能置空求通过 |
| 旧实体teardown/世界创建 | 真查询后分别止15184AF37/14EAAF0E3；15154BF20仅静态 | 固定实际entity class/组件泛型context及创建依赖；实机核旧实体移除、新实体生成、owner/ID一致，而非只看字段store |
| 完整manager及handler | 前缀、中段、后缀分开完成，未连续跨过153A20F30/14828FEA0/14828BA00 | 取得自然SceneScriptData、组件/Unity场景与资源前置后连续观察21761 handler入口至返回和本地实体；不跳过中段拼接 |
| World与其他场景必要集合 | stage enum/真实分支已验，实际加载的stage未观察 | 新号/旧测试号分别记录实际stageType和Dungeon资源105门槛；Beyond条件命中时再研究72829/37467必要后代 |

本轮明确禁止原客户端/调试器操作，因此以上自然状态未执行。研究输入齐全不等于已具备Unity/托管现场和客户端实机样本。未运行服务器、数据库、构建或部署；离线工具与文档不要求重启。

## 复现与证据边界

```powershell
$game = & tools/game_path.ps1
python tools/client72_scene_deep.py --client $game --output local/migration72/astra-scene/replay-native.json --observations local/migration72/astra-scene/replay-summary.json
python -m unittest tools.test_client72_scene_deep tools.test_client72_scene_info_contract tools.test_client72_scene_info_raw tools.test_client72_scene_entry_identity -v
```

正式脱敏摘要为 `protocol/cn-7.2/scene-deep-observations.json`，原始报告/日志在 `local/migration72/astra-scene/`。12个引用/错误边界、11个stage、5个Handler中段、3个完整非空前缀、1个后缀及4个实体替换夹具，共36个原生受控场景；另3个wire拒绝分开标明primitive输入模型与native error entry，不将它们算成全部客户端异常派发。

REA的target-free IL2CPP检查实际返回encrypted/unrecognized-magic，保留证据 `ev_b7d41e408b33201e2cf8ae690418f233cad5dd4498e18c328068ba685c54dda4`。没有重复冷开全程序分析；目标解码仍用固定三输入的既有专用解码器和Capstone/Unicorn。早期失败日志保留：只读探针父类null、共享Dispose无unwind、刻意null列表的观察器误读，以及未allow的真实CopyFrom/clear函数均在本地；修正限定观察器或增加已核真实函数，未改客户端指令或公共模型。

最终双跑与守卫的准确文件hash/数量由本卡收尾写入；主线独立第三次复验、Git和共享索引由主线协调。本卡没有自行提交Git。

主线已审核具体usage/虚槽、显式managed模型和pool flag修正，独立39项聚焦及第三新进程均通过；完整/摘要同最终双跑，证明`local/migration72/astra-main/scene-review.json`。不批准完整handler或生产SceneInfo；独立CopyFrom/hash后继继续。
