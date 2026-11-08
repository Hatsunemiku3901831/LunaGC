# E-01 / R3-AVATAR-ENTRY：出生角色必要属性后继

## 有界计划（2026-11-09）

本卡按主线恢复 D-02 的授权继续，历史暂停段落不覆盖本次任务。只沿固定国服 7.2 客户端与已审 locator/Promote 原始产物，闭合出生 10000005/10000007 的剩余必要基础属性和成长职责；达到可实现条件即交准确 Java 行转换与真实加载规格，不扩大到全玩法。

已验 energy、initialWeapon、Weapon/WeaponCurve、两个出生用途 Promote 和 AvatarCurve 100 行直接复用，不重做全资源解包/索引/整表解析。准确首缺为 Avatar `+64` 到 `BASE_HP/hpBase` 的来源、`+98/+a4` 的 critical/criticalHurt consumer，以及 `+48` grow binding 的实际 Avatar caller。MAX_HP/CUR_HP 用途、常见数值、7.1 同名字段和 Java 默认均不能代替证据。

范围仅本页、新 `tools/probe72_avatar_entry_resource_fields.py`、对应 test；原始输出仅 `local/migration72/d02-resume-20261109/r3-avatar/`。既有研究 source/raw 冻结。不修改 Java、Proto、resources、patch、配置或 Project/Harness/HANDOFF，不操作 Git 提交、Gradle、DB、客户端、调试器、现场或共享 REA session。

先核五 stock 输入、冻结 source/report/payload/metadata 与 Java 消费来源。复用既有 CPU harness 和已冻结真实 row/storage，仅执行准确属性/growth caller 片段或完整函数。最多新审 12 个直接函数窗口、3 组有界原生实验，每次最多 2,000,000 指令；记录复用/新执行、所有 getter 与设置夹具的计数和原始开销，不把多次调用藏成一次 whole。允许相同 case 复验，不重跑已闭合完整表。

正反独立样本覆盖实际非零、float32 位值/舍入、有序列表、缺省/缺引用、未知 enum 与高位 ID/hash；原始字段身份、来源跨度/hash、受控 live-in、准确停止点与模型限制全部保留。正式双 fresh 进程报告须逐字节一致，并给主线独立第三复验命令。

任一输入/hash/类型/跨度不符立即拒绝并保留首次失败；遇未建模间接边、缺自然输入或达到窗口/指令预算时，交首缺准确位置、最小解除输入和可独立后继后停止，不补空、不加无证 alias/default/成功桩。未执行的 Java loader、active revision、完整 Avatar/Item/能力引用、保存恢复与 D-02 均保持未完成。

## 实际闭合与准确边界

本卡新闭合 `AvatarData.hpBase` 和 `AvatarData.propGrowCurves`，连同旧已验 attackBase/defenseBase，能形成出生5/7的基础属性和成长**片段**。真实 private static helper `153a7f940` 的 metadata 声明恰为 `(Avatar58272, AvatarCurve68066, FightProperty32419)`，不是按相同偏移或常见数值猜测。原始函数完整返回与实际 caller 的缓存取行片段均已执行。

`critical/criticalHurt`、完整 Avatar/Item/Depot/Skill/能力必要输入、自然 Java 加载、活动 revision 与 D-02 仍未完成。新工具不输出可安装 Avatar 行，不在缺字段时补 Java 默认。本卡也不重新计算已验 energy、Weapon、Promote、完整曲线表的完成数量。

| 真实字段 | 原始消费者 | Java canonical 目的及转换 |
| --- | --- | --- |
| Avatar58272 继承63287，field313953 `OMDHANNLDKO/+64`，54724 | `153a7f996..9a4` 将 FightProperty1 唯一选到 `+64`；`9be` 原 float getter，随后实际成长运算 | `AvatarData.hpBase:float`；5/7均 logical bits `4463f2a0`，值911.791015625。必须保留float32，不据MAX_HP/CUR_HP旧片段推出本字段。 |
| field313964 `MOINKAPELGF/+9c`、field313951 `DBEMBHPGGLN/+5c` | 同 helper 的4/7分支明确选择上述两字段 | 复核旧 `attackBase/defenseBase` 许可，bits `418e76c9/4264e666`，值17.808000564575195/57.224998474121094；不是新的字段组完成数。 |
| field313947 `IAFAOJNGADJ/+48`，inline81112[] | `153a7f9c3..faa4` 从真实 Avatar 行取数组；每项低32位 FightProperty32419 与请求比较，高32位 GrowCurve54063 被实际用来查 Curve 数组 | `propGrowCurves:List<PropGrowCurve>`，每项 `type:String,growCurve:String`。保留原顺序，两个enum各按原唯一符号与Java目标值核对；出生转换恰须各有一次BASE_HP1、BASE_ATTACK4、BASE_DEFENSE7，未知/缺项/重复不回退。 |
| inline81112 `IELLPMKLAIO/+0` 与 `DOMLLPADPHC/+4` | 上述真实匹配选择，继而 `153a7fadd` 与真实 Curve inline66952 type比较 | 5/7有序三项：`FIGHT_PROP_BASE_HP→GROW_CURVE_HP_S4(22)`、`FIGHT_PROP_BASE_ATTACK→GROW_CURVE_ATTACK_S4(32)`、`FIGHT_PROP_BASE_DEFENSE→GROW_CURVE_HP_S4(22)`。不能按位置默认匹配，也不能删去列表后给倍率1。 |
| field313963 `FNBIHFNPHBE/+98`、field313966 `HDJFAACKBON/+a4`，54724 | 实际原parser/getter已保留 bits `3d4ccccd/3f000000`；本卡未取得 Avatar→CRITICAL20/CRITICAL_HURT22 的职责消费者 | **不批准** `critical/criticalHurt` alias，不以0.05/0.5、Java字段名、候选 JSON、7.1或相似基类用法填入。 |

两个出生片段仅输出 `id/hpBase/attackBase/defenseBase/propGrowCurves`，`fragmentOnly=true`、`fullDeclaredJavaInputRow=false`、`actualJavaLoaded=false`。它们可与既有已审 id/defaultDepot/initialWeapon/avatarPromoteId 片段逐来源审核合并，但仍不能冒称完整 Avatar 行。

## 实际原生路径与独立样本

准确 caller 是 owner66972/method554618 `153a7f560`，参数为 `(uint64,FightProperty,uint32,uint32,bool)`，用于同一 actor 的两个level属性比较。原路径在 `153a7f621..65e` 从 Avatar cache `S+30870` 查真实角色行，`684/6a5..6db` 从 AvatarCurve cache `S+34a80` 按请求level查真实曲线行，`6eb..6f1` 把两行及同一 property 交完整 helper `153a7f940`。

本卡 caller 从编码后入口 `153a7f621` 执行到 `153a7f6f6`，live-in 为 `RAX=明确Avatar ID`、`EBP=请求level`、`ESI=FightProperty`、`RBX=typed18163 actor存储`。两个 Dictionary 是明确 selected-row typed 存储fixture，**TryInsert/FindEntry、实际行指针选择和 helper 都是原始机器码**；没有 Python key→row返回桩。返回的R15必须等于同一次真实row parser生成的Avatar指针。前段actor GUID选择/ID decode及后段比较、UI和生命周期没有执行。

helper 对1/4/7先原生解码对应base，再遍历原binding列表；匹配的grow type用于真实Curve inline66952[] first-match查找。原arith58570分支实际支持ADD1/MULTI2/SUB3/DIVIDE4；真实100×4曲线均MULTI的已验合同直接复用，本次实际level1/20/100三行亦逐项原parser/getter复核。乘法为SSE float32，不能把host double中间结果直接作为输出。

新执行仅六个已审source span：Avatar5/7/136，AvatarCurve1/20/100。每行先执行原constructor与原row parser，再执行原scalar readout，完整逻辑字段与冻结主线第三报告逐项相等；没有重跑whole、解包、全局资源index或重新下载资源。Avatar行来自64,384B/SHA `466525f21b9e3f6dbbc90df7d050bfc8faf06d1f595d6317872bc5a1d2ed71c9`，Curve来自6,604B/SHA `4b7eb712589496626b69bae4fe9a55c592f18c894877c698a183158bdf487aa3`，旧carrier/source binding继续逐字节守卫。

独立正对照136实际base bits `446269db/41a3db23/42767fe6`，binding改用HP_S5(21)/ATTACK_S5(31)，与5/7值和曲线都不同。3角色×3属性×3level共27个完整 helper 返回，另27个实际cache caller片段；独立oracle从冻结真实行另取base、binding和factor，仅在最后舍入为float32，逐bits匹配。

| 出生5/7 level | BASE_HP bits | BASE_ATTACK bits | BASE_DEFENSE bits |
| --- | --- | --- | --- |
| 1 | `4463f2a0` | `418e76c9` | `4264e666` |
| 20 | `45126642` | `4236feb9` | `431302d2` |
| 100 | `4602b315` | `434ade66` | `44033edb` |

12个原生负例区分null Avatar/null Curve、NONE/未知/非base property、null/empty grow数组、缺binding、缺curve type、未知arith，以及actual caller的缺Avatar/缺level缓存查询。null grow数组实际抵达 `1404c2be0`，没有执行完整异常分配/抛出；其他上述缺项均观察到helper逻辑零，不把这个零转换为Java合法输入。fixture mutation只发生在离线typed存储，调用后恢复真实行。原uint64源hash `adfb90ae5bdec0ac` 仍完整保留，高位资源ID拒绝静默窄化；没有新增GUID生成许可。

## 搜索、首次失败和模型限制

新审准确函数窗口实际7个，未达到12个上限：精确共享Weapon curve-helper的3个新caller、两个精确AvatarCurve cache消费者、上述Avatar helper，以及一个最终排除的Avatar-cache候选。共享Weapon原函数只复用。定向静态检索以既知精确call target或cache displacement/field-load+float-getter为键，只对选中函数解码；没有打开客户端/调试器或共享REA。

critical定向查询保留原件：field `+a4` load后40B内直接call原float getter得到16个site（14个函数），tail-jump查询0个；metadata/字段来源未提供Avatar职责桥梁。另一个同时包含两个字节位移和Avatar cache的候选 `148bde5a0` 经准确窗口解码，没有确认Avatar `+98/+a4`读取，已排除。**这些过滤不是穷尽性“不存在消费者”证明**；可能的间接、整块复制或其他浮点路径仍未知，不继续无界开窗。已取得的HP/growth因此独立收口。

最早CPU `native-probe01.failure.json` 已完成六row/27 helper后，尝试执行actor ID编码 `14b1046f0`，准确止 `149a75169` 对空运行对象的读取；没有加Random/运行时成功模型或跳过该操作。这个前段到此停止。独立后继只执行上节明确声明的**编码后caller片段**，不称恢复了完整caller。早期失败snapshot未保存失败调用的精确hook计数，该单次原上限仍2M，精确值保持unknown；不拿正式成功计数覆盖失败开销。

`native-probe02.private.json` 为首次后继成功，6row/27完整helper/27片段为37,960 hook。正式工具另计全部getter、原生索引回查和负例；247次显式原生调用都列在`nativeCallTotals`，正式最终精确数量见下表，不重置或藏掉原生readout预算。实际native每调用均受冻结harness的2M上限约束；三个实验组为选中行与完整helper、实际caller、负例。同case开发与正式复验各自有原件，不相加为单一whole调用。

首次定向测试在setUpClass因Windows相对路径反斜杠与固定forward-slash pin键不符而失败，0 tests，原`tests-probe03.log`保留；只改该测试为`as_posix()`，没有改任何原生producer/parser或旧pin。修后probe04的18项全过；正式冻结后又补上actual missing-Avatar caller负例，正式结果另列，不把旧probe冒称最终双跑。

沿用模型包括精确typed class/RGCTX、托管存储/分配、UTF8、memcpy/memset、已审资源scalar codec的实际逆置换snapshot/seed与数学重建forward。NativeConfig仍是准确Int32 HashSet且buckets=null的fixture，不能证明自然mode或全局初始化。selected Dictionary容量3是显式fixture，不证明自然全表发布、增长时序或cold comparer。函数按固定PE/offline全局初值执行普通路径，IFix/自然热修上下文未验；`completeNativeFunction`只表示这次普通路径从函数入口到原始return。原字段可选token位未逐项恢复；本卡只陈述原parser实际逻辑值、原列表和消费者运算。

## 可执行后继与真实 Java 加载合同

现可实现的单一入口转换：校验本卡/旧主线来源hash与行跨度；解出原float32和有序grow binding；以唯一原enum符号/数值写上述canonical字段；核全部100个连续level的必需grow引用和MULTI；只输出带来源的fragment。缺席/null/empty/零与未知保持分离。无需增加任何混淆alias，也不能通过填0或空数组把片段变成整行。

**下一最小独立研究输入**是同一固定客户端/资源的 `Avatar+98/+a4 → FIGHT_PROP_CRITICAL20/FIGHT_PROP_CRITICAL_HURT22` 具名producer/consumer：应提供准确typed来源、函数/调用位置或可核权威同源字段定义，再作真实非零/缺项对照。不要重复已经排除的14c9e8400（其它资源）或148bde5a0；不要重做已闭的AvatarCurve/Promote/Weapon整解析。这是缺准确职责锚点，当前没有新live采样合同；不把“需要更多研究”写成必须启动实机。

可独立继续另一项是当前Java出生必读的完整Item11101 `gadgetId/skillAffix`及同源引用，或Avatar icon→AbilityEmbryo、Depot/Skill必要列表/AbilityGroup闭包；它们不依赖critical结论。已有实际数据偏移/值仅供定位，仍须消费者许可。完整Avatar、Item与必要Depot/Skill行及能力输入均齐后，再由主线执行真实Java loader；本卡没有运行JVM/Gradle/DB。

真实Java完整入场验证的最小合同沿用已审源码：Ability/Config/PlayerAbilityGroup先就绪，Skill HIGHEST→Depot HIGH→Curve/Item/Promote NORMAL→Avatar LOW；同NORMAL内部无固定顺序。使用隔离来源目录及独立新进程，在实际ResourceLoader路径让行`onLoad`后进入map，不手调loadedAll。检查拒绝日志/每行map身份与来源、100个连续level的growth数组、五基础属性（critical两项先补证）、11101实际Weapon/NONE两项属性和零阶Promote原列表的onLoad行为。再实际创建出生Avatar/初始GameItem并核level1及非零level float32结果、完整武器/ability引用与各必需负例。**缺critical等输入不阻止下面的批准字段片段测试，但仍阻止完整入场许可**；Gson接受缺字段或JVM没有抛错不是target默认许可。

主线可独立新增 `src/test/java/emu/grasscutter/data/D02BirthAvatarResourceContractTest.java`，只验批准字段的真实Java加载，准确规格如下（本卡不执行、不新增该Java文件）：

1. 固定本卡最终原生报告SHA和旧Promote第三SHA `546b95ce…d70fef1`，从本卡`javaProjectionFragments`取两份带`fragmentOnly=true`的`javaFields`，从旧`javaProjection.completeAvatarCurveInputRows`取100个完整Curve输入。原报告、field片段与源字段不得混入候选JSON；输入必须没有`critical/criticalHurt`键，并明确这些缺键不是target默认许可。
2. 在新JVM和临时目录生成这两张JSON。测试包与ResourceLoader同为`emu.grasscutter.data`，可直接调用准确protected overload `ResourceLoader.loadFromResource(AvatarCurveData.class, curvePath, GameData.getAvatarCurveDataMap())`，再 `ResourceLoader.loadFromResource(AvatarData.class, avatarPath, GameData.getAvatarDataMap())`。它们实际执行JsonUtils/Gson→onLoad→getId→map.put；不必调用loadAll，也不读取生产resources。测试前备份/清空两个内存map，测试后restore；不启DB、不触及存档。
3. 第一项核100个level、每行4个type/value及完整原有序`curveInfos`数组，原值与raw float bits逐项一致；第二项核恰5/7两行实际进入map，`getBaseHp/getBaseAttack/getBaseDefense`在level1/20/100逐bits等于本卡27个完整helper中对应的18例，`getGrowthCurveById(level, FightProperty)`逐bits等于相应factor。`skillDepot`缺行/null、未给iconName时的空能力仅是这个字段片段测试的范围，不当成完整默认或正向能力验收；不创建完整Avatar、Player或GameItem。
4. 独立负项把一份Avatar片段删去`propGrowCurves`，通过同一真实loader确认该行被拒绝而独立正常行仍安装；另一负项使用99个level的Curve输入（明确移除中间level，保留level100），确认当前Avatar onLoad不会凭getBaseHp fallback把不完整曲线当成功行。两项均从fresh空map执行并核实际拒绝日志与map，没有手动`ready`、旧缓存或预填结果。

这四项可以在缺critical时独立验证本卡批准的HP/growth→真实Java行为。未知字段进入Java后的自然零值不是资源来源事实，测试名称/说明必须维持fragment范围；未来完整运行行仍须补足关键职责和引用。主线已独立完成的出生Weapon四项真实Java片段加载为另一项成果，本卡没有重跑或重复计数。

资源加载之后的Inventory/AvatarStorage恢复、recalcStats/completeLoadingVitals/restoreLoadingVitals、active revision、完整Manager正向冷重绑及客户端实际入场各自另验。本卡没有触及原数据、配置、resources、生产Java、Proto、补丁或服务，故无需构建/重启/便携部署；本地提交与共享索引由主线管理。

## 冻结产物、实际测试与主线第三复验

两个全新Python进程的五stock前/后hash守卫均通过，完整报告和摘要分别逐字节相同。全部99来源pin又在收尾按磁盘原字节核对；22个实际native函数范围与PE SHA保留于报告。247个显式native调用合计 **39,508 hook visits**，含原constructor、六个selected row parser、全部字段读出、两个selected index的真实插入/回查、27个完整helper、27个caller片段及12个negative。Python typed存储准备、宿主hash/metadata和报告写入成本没有算作native指令；没有将开发失败当作这份正式成功统计。

| 冻结产物 | 大小 / SHA-256 |
| --- | --- |
| `first.json` / `second.json` | 各949,711B；`32cf0c85dd4df7976a78a33fca92464b6b6bdb01f1fd7e29dace9852de1c222e` |
| `first-summary.json` / `second-summary.json` | 各15,433B；`db730ef7795b42caebae304fee8516fc9539f74a902c74c6c0a9c80b8ff5d857` |
| `tools/probe72_avatar_entry_resource_fields.py` | 23,435B；`d2a00bc4228a7b531b9804840b84fd6b75552046058b562692a96f10e09e5297` |
| `tools/test_probe72_avatar_entry_resource_fields.py` | 12,036B；`bbc07594db0478db7735c7da60759b3501b8832c111e77c7f43449b9734fdbbd` |

18项定向检查在first与second各全过，0失败/错误/跳过，日志`tests-first.log/tests-second.log`。覆盖两出生位值/顺序、独立136/S5、raw critical保留且禁止alias、缺席/null/empty、重复/未知/错配enum、缺curve/非MULTI/重复curve、nonfinite与bits错配、高位ID拒绝及真实超2^63 hash、六行来源/原始字段、54正向原生路径、12原生negative、全调用预算与全部未完成状态。没有把这18项当Java/DB或客户端验收。

字节/摘要/pin收尾证明为raw内`determinism.private.json`，标准库工具`close.private.py`不导入本卡producer，也不作新的native执行。开发probe、首次失败、查询命中/排除记录与正式报告均保留；本页最后SHA由`delivery-manifest.private.json`保存。

```powershell
$avatarEntryGame = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/game_path.ps1
python tools/probe72_avatar_entry_resource_fields.py --client "$avatarEntryGame" --output local/migration72/d02-resume-20261109/r3-avatar/main-third.json
$env:CLIENT72_AVATAR_ENTRY_REPORT = 'local/migration72/d02-resume-20261109/r3-avatar/main-third.json'
python -m unittest tools.test_probe72_avatar_entry_resource_fields -v
Remove-Item Env:CLIENT72_AVATAR_ENTRY_REPORT
```

主线第三必须使用fresh输出文件，完整bytes应与first/second的949,711B/SHA一致；不能只比JSON对象或口头复用本卡18项。第三验收后可按上一节新增真实Java**片段**测试；没有任何本卡runtime/complete/ready晋级。

主线已按上述命令实际第三执行，949,711B/SHA与first/second逐字节相同，第三18项定向检查通过。主线另以独立标准库脚本核99原来源pin、实际source/test、6行/两出生片段、float32/有序绑定及未知/未完成状态共11项，证明 `local/migration72/d02-resume-20261109/r3-avatar/main-review.private.json`；原报告和日志已保留。两次主线私有审核初稿分别用了错误的consumers键名、未解析GAME_PATH前缀，失败均属审核脚本，修正后重读原件，没有修改producer或原证明。

批准字段现进入[真实Java片段加载](CLIENT_72_D02_AVATAR_JAVA_LOAD.md)；完整critical/能力/Item、loadAll、revision及保存/客户端父项不晋级。没有修改运行材料或部署GS。

后续单独完成[基础critical两字段许可](CLIENT_72_D02_AVATAR_CRITICAL_INSTALLER.md#主线第三验收)，不得反写本卡冻结报告的`criticalAliasesApproved=false`。HP/grow与新字段以真实行source相等后才合并，完整Avatar/能力与父项仍未验。
