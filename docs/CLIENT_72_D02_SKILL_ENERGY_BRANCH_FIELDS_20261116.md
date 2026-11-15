# Skill 普通与特殊能量字段来源

`R3-SKILL-ENERGY-RESOURCE-BRANCH-SOURCES-20261116` 已取得两个准确完整静态源。它们承接[已验 energy 分流](CLIENT_72_D02_SKILL_ENERGY_CONSUMER_SOURCES_20261116.md)，不执行目标代码或解资源值。两个分支均重新核当前 GAME_PATH、固定五 stock hash、fresh AOT、完整 exception 的唯一 exact-begin/containing/overlap；真实边界和派生 delta 在任何 body 读取前落盘。

| 分支 | 原方法与完整范围 | 实际 body / strict 来源 |
| --- | --- | --- |
| 普通 | `53763.m446828/HAJGOBMLPIL`，`[15345d0f0,15345d2b9)` | 457B；SHA-256 `898a1554704afc6601e167803f56cc21e21890d517c946104d0e9b4eb530e8cc`；100指令/37引用 |
| 特殊 | `53763.m446822/HMHJLKCIIDK`，`[15345d2c0,15345d486)` | 454B；SHA-256 `453bf5de985e2d426328c1b9758bbc48dd4f17ab4e4a09451b3443518f542c1b`；100指令/37引用 |

每个 body 只读一次，共911B；直接冻结site/target均为0，仅说明两窗直接索引。source证据共959B（24B AOT、24B选中pdata、911B body），header两读4616B、完整目录14344080B和五 stock hash550661784B另账；没有读callee/global/xdata/资源payload，OS/Python loader额外IO不冒作全量已计。原件为 `debug/d02-skill-energy-search-20261116/energy-resource-branch-sources/`：body前 delta SHA-256 `b0809eccf25e851f475ab1f562dadbce0a33aa96a3fae8de207bca9f9e54ed58`，result SHA-256 `634b97d1259c8dadaa75734f78a30e045136f6d9ab55cb101c92918f770784a9`，manifest SHA-256 `5d65b0656f22b68343ae3476d6ce73a8c1b0a9396f3552f25ec542c7765cf0e3`；driver只在该忽略目录，不增加第二个跟踪工具。

两窗都从实际 energy 槽4的53767对象 `+10` 取得其声明为88602的 Skill资源。普通分支 `15345d1b5` 读取该 row `+98`，特殊分支 `15345d385` 读取 `+74`；各自复制4B到局部cell后交已验 Float getter `14f905140`。这是资源字段的独立消费路径，不用旧 whole 的对象内存值作新数值 oracle。

比较池由原[critical installer 的 typed dispatcher](CLIENT_72_D02_AVATAR_CRITICAL_INSTALLER.md)及其既存完整2240B源/312B signed表定位：目标enum32419的70..76（各元素 MAX_ENERGY）均读同24887的 `+6a0`，1000..1006（各元素 CUR_ENERGY）均读 `+770`；1007 CUR_SPECIAL_ENERGY读 `+248`，78 START_SPECIAL_ENERGY读 `+458`，77 MAX_SPECIAL_ENERGY读 `+6d0`。输入声明及原常量保留在 `r3-critical-installer/enum-declaration.private.json`，SHA-256 `74a48121b7e6d3e8598d40ebcbbb773c6f247275fd2cce9be1bf53be77c6e9fe`；准确signed表文件SHA-256 `dc8025f7a569b6233466938a604524a179c8befd5dd9de7f81595ee11a3a4daa`。只复用既验源，不重取该窗或执行dispatcher。

普通分支在配置分支上比较 `当前元素能量 ≥ Skill+98`；fallback比较同一当前能量与最大元素能量。故 `440599/+98` 是普通能量使用阈值，可进入 `costElemVal` 限定字段合同。特殊分支比较 `当前特殊能量 ≥ Skill+74`；fallback是 START_SPECIAL，**不是 MAX_SPECIAL**。因此 `440585/+74` 对应起始需求；按当前 Java 的既有反直觉命名，后继目的为 `specialEnergyMax`，不能写到表示上限的 `specialEnergyMin`。这些分支还受skill对象 `+3a` 和原IFix开关条件约束，未证明自然状态或实际按钮可用。

普通cost的 raw 后缀位置为 `end − 4×bit11 − 4`（bit53存在时），原word XOR `8a4ce70f` 直作float bits；10019限定pilot只需新增4B。特殊起始需求在首String1前P9：只累加已验P1..P8宽度，bit30存在时word加 `70326507`（mod2^32）直作float bits；11065/11145的两位置分别52408、58229，只需新增8B。两套 host 规格分别固定在忽略目录 `local-analysis/ordinary-cost-host-pilot-spec.private.json`（SHA-256 `7a4a793c8b9eba546efbfad3d897be6abb50a1374c9ad8cee1e02c9383bc3bd9`）与 `special-start-host-pilot-spec.private.json`（SHA-256 `c67b46bc5b31ab3eac256ad24280a3840ac8c0ac2e172b1f7ff7daa92e0b3f4d`）。实际值、canonical转换及Java加载由后继单独验收；不把机器spec当作已产值。

数值由固定target raw与原parser位式决定；固定AstaPS-Resource的80、100/50只作独立候选对照，不强制target相等、不证明目标revision，也不从200/100候选倒推上限字段。`specialEnergyMin` 的资源上限字段仍未定位；MAX_SPECIAL已定位为24887的独立属性，但其资源来源未定位。不能把“Skill→上限直接赋值”假定为必需或宣称只来自协议。完整Skill、资源ready、旧Player恢复与D-02六项不因这些source通过而完成。

已继续完成 `R3-SKILL-ENERGY-INIT-CONSUMER-SOURCE-20261116`：从既验Depot初始化的准确调用取得 `53763.m446713/CAGPNODJNOO`、`[153451e10,15345201d)` 的完整525B静态源，SHA-256 `6c6ba8b64ff5ea2dc74c58305aab4fbdb30f82407ec9cee51e7c9661fddde7a5`，137指令/40引用，直接冻结site/target为0。该次仍先核五stock、唯一fresh AOT与完整pdata，并在body前保存delta（SHA-256 `dd53f16aca0c68fa1f7e69fa1b687a95a256d7b681b171750334903c21f2582d`）；实际body读取一次，无目标执行/callee/global/xdata/payload。证据目录为 `debug/d02-skill-energy-search-20261116/energy-init-consumer-source/`，capture result SHA-256 `7f6331127f7ed145708d14ffde19b224551c6ae78e72d07cc499174c61c78b2c`、manifest SHA-256 `9d8b638dcfcd299cd84ef1a21cb3eddac8c01aacf89ba3db93027feb5c0ae261`；driver仅在忽略目录。

该窗的正常路径把组件 `+270` 中延迟的53767技能对象合入 `+210` 字典，以对象 `+50` 为key，向两个通用后继传对象及 `+54/+d4/+a8`，最后清空原列表。窗内没有同Skill资源 `+10` 读、24887 `+6d0` 写或上限职责绑定；故这条准确分支收口为对象注册/延迟合并，不继续两个通用后继，也不据此否定别处存在资源上限来源。

随后仅做一次有限既存来源查询 `R3-SKILL-SPECIAL-CAP-RETAINED-NAME-QUERY-20261116`。固定 `analysis/current-structures.json`（SHA-256 `d5d7f671a622439b2f9a3ed0018ce551aa5c573cd9ca68bed3b006712b8a98aa`）、`business-methods.json`（`6e574e15c822cd3572e1f64f82d53653aec67d6daeb82ae3a0468eecf75c9b3b`）、`avatar-skill-depot-resources.json`（`fe4ffe0e005194606b711dcbebf02cf6cc6a84bc61e31549e63915fc3aa2dd06`）及上述property声明。规范词为 `maxspecialenergy / specialenergymax / specialenergycostmax / specialenergycap / specialenergylimit / specialenergyupperbound / maxfightingspirit / fightingspiritmax`，另限SpecialEnergy/FightingSpirit具名owner的Max/Cap/Limit/Upper方法、24887与88602的具名成员。相关方法/具名owner方法/边界具名成员/business索引均0命中；字段仅32419的 `FIGHT_PROP_MAX_SPECIAL_ENERGY` 和另一GCG枚举52276的同类literal。保留源码的准确 `+ 0x6d0]` 命中只有既验属性dispatcher；`analysis/store-consume-0x153caf190.txt` 的同串是RBP栈临时，不能绑定24887。实际词、范围和固定输入保留于 `debug/d02-skill-energy-search-20261116/local-analysis/special-cap-retained-name-query.private.json`，SHA-256 `5088307eb58a738831c8701c37dcfec072a81f89c37292382dfadcc9834c9d0b`。未作全域call扫描或泛对象树。

最小剩余输入是：同固定目标版本中，将一个准确88602字段或其具名getter绑定为特殊能量上限的独立来源；可为准确完整consumer源中同Skill字段流向已验MAX_SPECIAL属性，或具有版本/字段身份依据的目标schema映射。不要求额外运行目标getter或独立raw-id解码。取得这条职责桥后才按既验parser证明该字段的准确token位置/解码和presence，再对必要行做单字段host合同。现有raw/parser能证明若干Float字段的位式，却不能选择哪一个叫上限；候选200/100、两个JSON互证、Float同形或位置相邻均不能补此职责。因此本轮仅将**特殊上限的目标字段职责桥**列普通来源缺口；普通cost、特殊START、selector枚举的独立工作继续，未将未定位方法宣称为安全拒绝或整族外阻塞。遇到新准确来源再提单窗范围；不扩此轮搜索，任何固定hash/边界冲突或安全拒绝即停相关准确动作及依赖。
