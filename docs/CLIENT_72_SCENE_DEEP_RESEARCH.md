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

## 后继：具体 CopyFrom 上下文与非零能力哈希注册表

独立工具 [client72_scene_deep_followup.py](../tools/client72_scene_deep_followup.py) 固定首项摘要 SHA `bed41fc74af5ef39b951c6d57834c10a56b0577da1166957d5221f19ada4acd5`，并核首项全部source hash。新摘要 `scene-deep-followup-observations.json` 不改首项受审产物。原始尝试和新报告仍在 `local/migration72/astra-scene/`。

最小计划是先解当前clear/copy的具体泛型usage，在真实非空bool、两同形子list和旧值上比较；再用明确非零注册表执行真正ContainsKey/get_Item/FindEntry，跨过首项缺表错误，到下一实际消费者。没有以缺live内存为由跳过这两项，也没有为新对象填默认成功。

### CopyFrom 已推进的实际范围

owner61203两方法的当前usage重新确定：507471/HAPHBJOOKLA→clear、507465/JBBEIEPPIGB→copy；方法实例5883/27561/27562/27563分别绑定77771/70808/51162/51050。必要MethodInfo/rgctx存储仍是显式托管模型，函数本体、计数guard、清理、复制和写入执行原生指令。

最初非空bool已能连续经 `1507E4900→14AD08270→147CCFC70→147CCF650→154C79510/154C79640→158DC4600/158DC2140` 返回；两条77771子list则真实止于resolver `140524010`，返回地址 `158DC239E`，准确缺少class context+58。原失败 `copy-probe2/3.log` 保留。

与 Avatar rgctx 线协调后，本工具独立从固定文件重读 class range/definition，按5883实参展开：owner25060 definitionIndex6347的 slot9/+58/typeRef686353 是 **type89084 `KPLNPGCDMDK<77771>`**，slot3/+28/typeRef686352是 **`List<77771>.Enumerator/type1256`**。前者的唯一方法为零参 `GFJDJODGIDB`，与77771具体Clone声明关联；不是凭猜测的System.ICloneable名称绑定。接口表、实例化metadata和共享Enumerator.Dispose仍为已声明的模型。

现在7个原生场景均到预期返回：非空bool、显式零、重复bool后零、错wire跳过、两条不同非空77771子list、复用旧目标后清理，以及省略附属子对象。两条子对象确实新分配，源/副本地址不同、1.25/-2.5分别保持，原输入不被改动。复用场景又向同一已非空目标CopyFrom另一份真实reader生成的零/空内容，bool和两list清空；实际清理范围各1个8字节元素，Array.Clear字节操作单独标为模型。

本后继批准的是 **34045非空bool及两份77771 list的限定复制合同**，`coveredNonemptyListTypes=[77771]`。70808/51162/51050只恢复当前方法实参，不声称已执行其全部非空Clone。完整能力业务字段、对象池现场、正式生产者发送集合仍未批准；也不因此接生产SceneInfo。

### 能力哈希表的非零真实查询

精确 `Dictionary<uint32,string>/instance4742` 的 ContainsKey=`1578B63C0`、get_Item=`1578B5FD0` 与独立共享 `Dictionary<uint32,Object>/instance1541` 的 FindEntry=`1578B66F0` 重新从AOT表取得。两个wrapper实际直接调用同一FindEntry；不能误称4742自己另有已登记FindEntry。

8个场景从完整typed Handler入口连续执行，真实uint32掩码、取模、桶链和完整key比较都在目标代码内，**没有Python字典查询回调**。注册表bucket/entry及UTF-16字符串对象是明确输入夹具，既不是实际资源数据，也不证明加载已完成。覆盖不同非零名、1与0x80000001相同masked hash、1与8同bucket链、重复与逆序、Unicode原指针、零值、缺key和空名。

查到两个有效名称后，实际tuple为 `(tag3名称指针, tag2名称指针, tag1 u32)`，保持max uint32与列表顺序，随后到 `1507E44F0`。未执行该后继浮点列表消费者的完整效果。

独立边界修正了“缺key必然报错”的过度概括：函数先把 `GLOBAL+C698` 默认字符串作为候选；缺key且该默认值非空时，真实返回该原指针。没有默认值，或命中一个长度为0的字符串时，才进入 `1500D872C` 错误日志准备分支。本卡在准备分支停止，没有把它称为异常抛出、断线或完整日志派发。零hash直接采用受控默认值；本机自然默认值和生产可否采用它仍未知。

初轮后继把共用检查 `1500D871C` 误选作仅miss边界；真实命中/get_Item执行已反驳它，保留 `followup-probe3/4.log` 与 `registry-boundary-failure.json`。随后依据实际test/字符串长度分支修正停止位置并补上述正反例，没有放宽成功断言。

### 后继实现规格与剩余界限

Sol/Terra可以把本卡15个场景作为独立适配器的合同回归：两同形34045子对象必须保持原来字段用途，附属分支copy不得误实现为Avatar的retain；两个83320 hash仍是fixed32；不使用int32符号截断、double或map遍历顺序替代uint32键/有序列表。Java值依然必须来自已核实际getter和资源，不能据本卡人工注册表生成正式哈希值或默认名。

首项的 `154C795C4` 和“C6D0完全无法继续”的宽阻塞已缩小。当前精确未闭合范围是：其它复杂非空list的Clone及业务含义、自然AddAbilityString注册和默认字符串来源、`1507E44F0` 非零列表的状态消费、完整stage/44512实体创建及旧实体teardown。C6D0受控有表的成功不替代这些条件，原来的构造/入场/运行状态均不晋级。

```powershell
$game = & tools/game_path.ps1
python tools/client72_scene_deep_followup.py --client $game --output local/migration72/astra-scene/followup-replay-native.json --observations local/migration72/astra-scene/followup-replay-summary.json
python -m unittest tools.test_client72_scene_deep_followup tools.test_client72_scene_deep tools.test_client72_scene_info_contract tools.test_client72_scene_info_raw tools.test_client72_scene_entry_identity -v
```

实际9个新增守卫加既有39个关联守卫共48项通过，失败/错误/跳过0；不与15个原生场景或首项36个场景相加为一次测试量。最终双跑hash和主线独立复验由本节收尾继续记录。没有生产变更、构建、部署、客户端/调试器操作或自行Git提交。

正式双新进程 `followup-final-{a,b}-native.json` 均418,551 bytes，SHA `9fdbe3e0d1c76925c7bdaf0d60196a2a64d1cabdf621aadc0034b1e9834ef6f6`；summary均101,415 bytes，SHA `755ff3ad04ec69684b608de36667619746ec7421b463a5728b32f292b613e974`，与共享后继摘要相同。末次25.380秒；守卫日志 `followup-tests.log`。主线独立第三复验另记。

后继主线48项关联及第三原生报告/摘要同双跑；已审核rgctx类型展开、Clone名称/入口、真实字典调用与默认/错误分支。证明`local/migration72/astra-main/scene-followup-review.json`，日志同前缀；仅批准所列77771复制和受控表查询范围，不批准全部非空图或自然注册。

### 最终必要性退出：80154非零列表后置

完成后继后又实际核对了下一入口 `1507E44F0`，没有因为“小卡完成”直接停止。固定三输入重新提取表明：目标49723/tag3的80154元素为u32/+18与float/+1C；网络对象type56238的+110为原字段 `DDIPEPCDALN`、具体 `Dictionary<uint32,float>/instance5971`。typed Handler会调用该方法，但其内部还检查现有本地实体状态；这证明调用边，**不能单独证明D-02必须发送非空内容或给出该u32的业务身份**。

当前生产全部 `TeamEnterSceneInfo.newBuilder` 只命中 `PacketPlayerEnterSceneInfoNotify.java`，仅设置teamEntityId、teamAbilityInfo和abilityControlBlock。旧Proto虽另有同形 `JFAHOBMFLFE/IDAKPKNODGC`（id/cur_value），当前Java/测试没有相关add/set使用；同形不能证明它与目标80154同义，更不能制造生产getter或默认一条非零值。已审7.2证据仍只批准80154的raw字段，没有独立业务名、账号/保存来源或实际非空D02样本。

同owner原生范围另进行了有界+110引用搜索，得到17个方法候选，其中包含大量栈偏移及其它receiver的误命中；没有把这些候选称为真实消费者或全程序无引用证明。可复核原始 `necessity-result.json`、`necessity-final.log` 固定三输入及五份生产/旧Proto来源hash，留在本卡忽略目录。这里仅作必要性结论，不新增已批准业务映射。

因此本轮不扩这份未知浮点状态为战斗研究。具体解除输入是：新号与旧测试号自然21761处理时，在 `1507D16D0/1507E44F0` 记录实际列表count及每项u32/float、SceneScriptData.stageType、本地实体+426、+110字典处理前后；同时找与这些key对应的服务端真实保存/资源来源。若有非空样本，下一项只沿 `1507E4773/1507E4776→1553C8AF0` 核值和语义；若均为空，也不自动批准删通知或省略其它字段，仍需完成整个handler与入场。当前无该独立输入，非空必要性保持未知；原客户端/调试器本轮未操作。

Scene线本轮退出范围：首项及CopyFrom/hash后继已经实际执行、独立守卫、双跑和主线第三复验；完整构造/复杂图、自然能力注册、stage/44512实体生命周期、完整handler和D-02实机仍开放。后续实机应观察上述精确入口及生命周期，不重复已验raw扫描，也不为保持工作量扩展未证明的战斗分支。
