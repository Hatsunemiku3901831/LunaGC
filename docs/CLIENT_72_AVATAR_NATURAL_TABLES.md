# D02-R1：实际六表、角色资源引用与 Ornament 派生索引

本卡是离线原生研究，等待主线复验与审核。使用已审 [locator](CLIENT_72_RESOURCE_LOCATOR.md) 提取的真实 `MiHoYoBinData.m_bytes`，不是候选 JSON 重编码。六张 whole-table、row parser、原生字典插入/查询已跑到实际 EOF；这不表示自然客户端选择了该 revision，也不表示完整 Avatar、cold comparer、handler 或 g2 完成。没有修改资源、Java、Proto、配置、补丁、运行数据或现场进程。

主线已审真实代码、原始证明、typed存储/NativeConfig配置fixture和停止边界；第三 `main/avatar-natural-tables-third.json` 为同一15,112,922字节/SHA `1e9817cbea6da6db5ee564d5c3f3a64436fa3319e6d9b8ccf5aef0014cf85a86`，与双跑逐字节一致，14项检查通过，证明`main/avatar-natural-tables-main-proof.json`、日志`main/avatar-natural-tables-main-tests.log`。仅接收本页限定研究，特别是Weapon的空配置HashSet/absent List仍是fixture；自然NativeConfig选择/初始化、Java alias与完整g2继续未批准，不以EOF掩盖这些条件。后继由独立HandlerClosure继续，无生产部署。

## 小计划与边界

| 项目 | 本次执行 |
| --- | --- |
| 目标 | 同一来源字节闭合 10000005/10000007、501/701、100540/100550 与 Weapon11101；解释实际 Ornament 缺行、对象字段与默认分支，形成可审核 Java 接口规格。 |
| 已有证据 | 固定五客户端输入、locator 完整容器/索引链、旧 row/typed getter 合同、自然 resources5 的逆置换与 seed、当前 Java 消费者源码。 |
| 未知 | 自然活动 revision、运行时 NativeConfig 模式、英文 scalar/list alias、完整 cold/Avatar 后缀、能力/曲线/保存全链。 |
| 候选解释 | 文件头是各 whole loader 的编码 count；资源标量置换仅用于解析后的对象存储。实际字节解析与独立查询能够区分资源 namespace，同值本身不能建立 alias。 |
| 最小实验 | 六份原字节逐 SHA 校验；原生 whole→row→Dictionary；选定行原生 query→typed getter；完整 Group 派生 builder；高位 key/重复/截断/错误数组 class；两个全新进程逐字节比较。 |
| 范围 | 新工具、对应 test、本页；原始输出仅 `local/migration72/d02-active/avatar/natural-tables/`。旧卡及共享索引冻结，由主线管理。 |
| 失败出口 | 输入 hash 不符即拒绝；未建模调用/分配容量/输出跨度不符保留准确停止点。不会补零、替换表、跳构造器或调用现场函数。 |
| 验收 | 完整原生路径及模型清单、精确来源跨度与 enum 常量、独立断言、双跑、Java getter/省略与次序边界；主线审核前不批准生产。 |

## 固定输入和整个来源链

五份文件由 `tools/game_path.ps1` 解析的当前目录只读重读；完整 hash 同时记录在工具 `FIVE_INPUTS` 和报告 `inputSHA256`：

| 输入 | SHA-256 |
| --- | --- |
| YuanShen.exe | `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| global-metadata.dat | `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a` |
| startup-metadata.dat | `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105` |
| AccountPlatNative.dll | `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25` |
| Astrolabe.dll | `87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442` |

locator 报告 `locator-final-1.json` SHA `fc2c3aca3f472354f0533c9ac319387102fd6f8a1a7195b0400d06988a420fda`，主线已第三重跑并提交 `a1f3968`。它保存准确 literal、generic T/suffix、native hash→实际 index tag0/value178→`25539185.blk`→完整 546 块 LZ4/serialized→1210 index→1208 byte[] 的路径。完整 serialized 为 71,555,980 字节，SHA `0311cb19ace1cac5475f197043af14b33657f22c9915e93eb9bffa54cae5e8c7`。本卡校验报告与每份 payload，不重算或替代上游证据。

| 实际 payload | 字节 / 行数 | SHA-256 | whole / row / cache offset |
| --- | --- | --- | --- |
| Avatar | 64,384 /169 | `466525f21b9e3f6dbbc90df7d050bfc8faf06d1f595d6317872bc5a1d2ed71c9` | `14f33b1b0` / `14f9935d0` type58272 / S+30870 |
| AvatarSkillDepot | 26,024 /171 | `fda5ac5875581bf6c04a269333197d41decea728ecc9f075e4add7780745b14b` | `14d6d54a0` / `148328b60` type22284 / S+212b8 |
| AvatarSkill | 141,702 /1176 | `a5d2fdc6bbc32ea408c51e90c04777b771e9ab4fec4c35fc74186b6dc3c236fe` | `14eb02da0` / `149b56fe0` type88602 slot5 / S+16c70 |
| Weapon | 128,936 /288 | `cca19efbe032e5416d78c28fcb69c2ea2d9707187197aa4ed90d869c3a1ff918` | `154177330` / `154498500` type87097 slot12 / S+34900 |
| AvatarOrnamentGroup | 22 /1 | `bd3bd58de65c59b1f4bb9f87d1535725cf22e621f402965cc42fbb765b454f00` | `15437a970` / `152fe0bc0` type61286 / S+13b70 |
| AvatarOrnament | 346 /2 | `291895b576850960aa89965ae1ee7e4644f2c0fc31c01973cc6141e139201400` | `147366b80` / `14a6fe2f0` type44799 / S+1cd98 |

表内 S 仅为 root cell `14586b600` 指向的受控静态存储；不把离线 GLOBAL 地址当现场地址。原始 byte[] **不含 Unity 4B 长度**。这六个真实 payload 的前4B分别为 `80a0f236/c5b8c7bb/00334099/93377e16/e84fb65e/47fb0a10`。whole 真实计算依次是 XOR `36f2a029`、XOR `bbc7b86e`、加 `66bfd198` mod2^32、XOR `167e36b3`、XOR `5eb64fe9`、XOR `100afb45`。count 读取后 row 从 offset4 起，工具没有另剥一层头。

每行保存 `[begin,end)`、SHA、原生插入的完整 uint32 key、行对象 bytes。所有跨度从4起连续覆盖到 EOF；不是只寻找几个 ID。每个选定 ID 再通过准确 native getter 查询，返回指针必须等于实际插入的 row，缺项必须实际返回 null。

## 原生执行与明确模型

原生运行包括 whole 的计数/循环/构造调用、每个 row parser、字节/字符串变换、encoded scalar setter/getter、`1578b6a20 TryInsert` 和 `1578b66f0 FindEntry`。`nativeSources` 保存每个执行函数的固定 PE 范围/hash/RIP引用；`callOrder` 和 `trace.executedAddresses` 保留完整顺序/次数。没有 Python key→row 查询成功桩。

模型包括托管对象/数组存储分配、已初始化的类型/继承/vtable、MethodInfo/RGCTX、字典容量初始化、UTF8→托管字符串（在原生变换后）、memcpy/memset、严格同 class 指针的 array assignability。**下述Weapon额外配置cell的类型标签存在后来发现的来源错误，不在“精确类型”许可内。** Dictionary 闭包布局按真实代码使用到 +44，分配0x48；开放泛型 metadata 的0x10不等于该运行布局。comparer=null 的 uint32 分支实际执行，完整 key 比较与高位 hash 碰撞不被 Python 代替。此模型不执行 Dictionary cold comparer 初始化。

resources5 原样本 SHA `c5e6b05778f6326891065f120b966f4a8135b8601f097c30f6477724066a765d`；四段真实逆置换共1024B SHA `8717ded77f717cf44988dee2720b99bbd60c751426b130f6166a2ffc379a57f6`，seed `0x3b0a002e`。工具逐 span 检查长度/hash/array 地址链，并要求四段各为256值双射。正置换逐段取其数学逆，**明确是重建模型，不称自然读到了未采的前1024B**。原生 `14aefa090/14aef9db0` 与 float getter `14f905140` 使用这组存储，文件本身不经过所谓 cipher 解密。旧显式13/29配对置换的 probe 保留，正式数据使用该自然来源。

Weapon 有额外明确边界。首失败在 `.ctor15449a920→base1484451b0→14f587b40→14d5b9fa0` 的 S+60e8 空配置 HashSet。原模型提供count0空存储并给其标61440自有HashSet<uint32>类型，S+6100为absent List；后来HandlerClosure从准确metadata证明S+60e8实际是11325 GlobalVars._serializedCodeSwitches/HashSet<int32>，61440的自有uint32集合真实在S+1d4a8。**该旧类型标签错误，不能作为目标配置的typed身份。** 当前原生空路径只读+20=count0、未消费class，故只保留该受控空存储路径的执行事实，不将其提升为真实配置/自然模式。历史source/raw保持冻结，错误不通过刷hash掩盖。

在这一限定fixture下，托管模式`.ctor` slot11 `154498220`原生分配20600 backing，保留87097+10/+20同对象，随后`15043f0f0/150b6a9b0`解析实际字段。这不批准Weapon11101的自然构造上下文或unmanaged路径。GlobalVars.cctor虽写mode false，另一实际writer会在codeSwitches空时写true，不能只据cctor推自然模式；新HandlerClosure须以真实int32来源/相关writer和必要自然值关闭3521/6506/模式条件后再交生产规格。

长字符串使用明确 OS TLS 存储：GS+58→TLS array[0]→8192B TLS block；+1998 scratch 对象和4096B slab按真实布局提供。原生 `1405070c0` 分配、调用者释放真实执行；全表576次请求，最大160B，最终 used=0。超过既定 slab 的扩容没有被成功模型代替。该布局复用了 Scene 线独立成功实验，Weapon 的 `15043f0f0` 又独立定位相同 TLS-index cell `145eef080`。

## 实际角色、depot、技能与初始装备

下表的字段都是目标 metadata/原生 getter 所确定的逻辑值；右侧 Java 对照仍受下一节许可边界约束。

| 实际对象 | 精确字段和结果 |
| --- | --- |
| Avatar5 /7 | +18 key；+168 KJCFGLDDHMG=501/701；+154 ECGFIKIODEP=11101；+178 LDPPONPFHHD=12；+d8完整501..508/701..708列表；+c8为 UI_AvatarIcon_PlayerBoy/Girl。 |
| Avatar 基础 float | +64=911.791015625 bits4463f2a0；+9c=17.808000564575195 bits418e76c9；+5c=57.224998474121094 bits4264e666；+98 bits3d4ccccd；+a4 bits3f000000。没有用十进制候选重建浮点。 |
| Avatar +48 | type81112内联两 enum 字段；三项 `(FIGHT_PROP_BASE_HP,GROW_CURVE_HP_S4)`、`(FIGHT_PROP_BASE_ATTACK,GROW_CURVE_ATTACK_S4)`、`(FIGHT_PROP_BASE_DEFENSE,GROW_CURVE_HP_S4)`。名称来自独立原始 enum constant 表，不用候选数字推断。 |
| Avatar hash | +1c为1533656306/3816664530；真实多项uint64超2^63，报告保存十进制字符串和完整16位hex，例如5的+28=`adfb90ae5bdec0ac`，不经double或signed int截断。 |
| depot501/701 | +30 EBEDHBBEBIJ=`[100540/100550,0,0,0]`；+38 AFPKIMBPIOJ=`[10035,10101,10001,10102,5005010/5007010]`；+28六个0；+20三个空字符串；+48完整 Boy_Common/Girl_Common ability-group 字符串。 |
| depot 缺省 scalar | 原行头 mask为LE32加51fee905；+68 bit40、+6c bit10在501/701均未置位，实际原生解析写逻辑0。这次确认的是实际缺 flag，不再只是候选 JSON 缺键。704的+6c flag实际存在并解析10068。 |
| depot704 嵌套 | +10第一40302元素 group751、params[603201]、kind1；+18五个81127元素有非零 `(1,721)/(4,722)`。这验证非空实际列表，不批准 LHFO→specialProudSkillOpens。 |
| Skill100540/100550 | +10 IIBMNPFLNMJ=`Avatar_PlayerBoy_ExtraAttack_Common` / `Avatar_PlayerGirl_ExtraAttack_Common`；+60 ELMCBLCOEEK=730/731；+54 enum74813=None0；+68是各自key。 |
| Skill11101 | +10=`Avatar_Iansan_ExtraAttack`，+60=11031。它与 Weapon11101 是不同 map、不同 payload、不同原行来源；禁止因同值误接。 |
| Weapon11101 | 真实首行[4,400)，SHA `c4ef8b265f69900cf3330ea83666d039d7f5d753266994e6056039ca36202e9a`；20600 backing继承88968，+28=11101、+2c=50011101、+84=11101；+38原enum为ITEM_WEAPON4；+98为WEAPON_SWORD_ONE_HAND1；+40数组[0,0]。 |
| Weapon属性 | backing+68两87327元素：`GROW_CURVE_ATTACK_101 / float bits41b9f5c3 / FIGHT_PROP_BASE_ATTACK`；第二为`GROW_CURVE_CRITICAL_101 / 0 / FIGHT_PROP_NONE`。第二项确实存在，不能在目标导出时删去。 |

Avatar5原行[1153,1586) SHA `5782275e1170b9de4dfd49749b07745ccf0e923d6424f701cfa4b69c7496aa0c`；7为[1977,2412) SHA `5a68d9e9184be7a6427e3353afc3ddc56013bfbb5f06f3f560a8a844ade8b52f`。depot501为[426,577) SHA `943f15501042ae657b27bf424e8bae2c0fbd225a70e80622fa453dada40037cf`；701为[2196,2348) SHA `c8150bc63a7055b7a1aa466c8683ff1035c0aec58f68f1616d30f33f19103914`。其他选中行跨度/hash全部保留在报告。

`crossReferences` 是实际解析对象与实际按ID查询结果的合并：两角色的默认depot、+154引用及两个技能数组的全部正ID均在同批真实表中存在，0原样保留。它不是新一次完整 Avatar handler 执行。既有具名 `Miscs.GetAvatarElementType153217960` 已证明 Avatar+168→Depot+6c→Skill+54 的资源身份；本次补上真实 parser/实际值，没有把该方法冒称本次跨三个缓存连续重跑。

## Ornament 完整派生行为

真实Group唯一行key1、avatar10000136、default字段+1c=101、plain字段+14=3452747463。真实Ornament两行101/102、group1；完整58937条件数组也由原生 row parser 执行，原字符串和五个参数位置保持不变。

第一次 `15437ada0(10000005)` 在 primary非空/derived空时真实调用 `15437af90`。模型仅提供空 `Dictionary<uint32,List<61286>>` 和已构造的 primary `ValueCollection` 存储。之后枚举 `1593af830`、key+18的native解码、FindEntry、List对象分配、`1591151b0 AddWithResize→159111f60 set_Capacity`、typed array分配及 TryInsert 均实际运行。List RGCTX slot7/17/26对应10441/10379/10406，由固定 definition/MethodSpec表核对；Dictionary slot15对应10196。

结果derived count1：查询5返回null；查询7返回null且不再次构建；查询136返回只含真实Group1对象的List。独立两行同avatar样本又运行真实已有List追加，包含高位Group key `0x80000001`。**这里的null仅是该资源getter没有列表，不是Avatar tag38默认、当前选中Ornament或保存语义。** Java仍无已审核Ornament资源类/map；不以costume接口替代。

## Java 对照与允许实现范围

复用 [Java消费者合同](CLIENT_72_D02_JAVA_RESOURCE_CONTRACT.md) 的真实源码，具体目的接口如下。允许主线先审核中性类型/实际字段导出和单字段桥梁；本卡不批准批量给混合候选 JSON 增加 alias，不批准生产sender/g2，也没有运行 JVM loader。

| 目标证据 | Java精确目的接口 | 本次许可/剩余桥梁 |
| --- | --- | --- |
| 各whole字典key /实际row key | AvatarData.getId、AvatarSkillDepotData.getId、AvatarSkillData.getId、ItemData.getId | 资源类型、完整key与实际行一致；所列出生ID均适合现int范围。其他uint32高位记录需单独入口策略，不能静默截断。 |
| Avatar+168与具名资源链 | AvatarData.getSkillDepotId | 默认资源depot身份已有具名消费者；这与Avatar.getSkillDepotId的当前保存depot不同。 |
| Avatar+154=11101 | AvatarData.getInitialWeapon→AvatarStorage.addStartingWeapon→GameItem | 实际值/Weapon引用已闭；英文initialWeapon桥梁须结合原生装备消费另审，不因相同11101单独批准别名。 |
| Avatar +64/+9c/+5c/+98/+a4 与 +48原enum | AvatarData.getBaseHp(level)/getBaseAttack(level)/getBaseDefense(level)/getBaseCritical/getBaseCriticalHurt；PropGrowCurve(type,growCurve) | 真实float bits/曲线符号可审核；前三getter还乘对应level成长曲线，不能直接以base值覆盖最终属性。实际AvatarCurve输入仍需同源。 |
| depot+30/+38/+28/+20/+48 | getSkills/getSubSkills/getTalents/getExtraAbilities/getSkillDepotAbilityGroup | 值与类型已得；数组语义对应及全部英文alias仍需原生消费者桥梁。Java初始等级只使用skills/energy/attack正值去重，不把subSkills并入。 |
| depot+68/+6c及40302 | getAttackModeSkill/getEnergySkill/getSpecialProudSkillOpens | 实际缺省0和独立非零已证；+68/+6c英文角色和LHFO alias仍未批准。保留原字段，不能为Java缺键自动补0。 |
| Skill+54原enum74813 | AvatarSkillData.getCostElemType；ElementType.None/Fire/…/Wind | 原始constant名字/数值与具名元素消费者支持本字段的元素域；仍需以目的Java enum实际接受项逐个审核，未知项不得归None。 |
| Skill+10/+60、其它scalar wrappers | getAbilityName/getProudSkillGroupId/getMaxChargeNum等 | 中性字符串/值可导出，完整alias仍待消费者。44333使用原生getter14ef4b180保留32位，不推断signedness；其他float使用14f905140。 |
| Weapon backing+38 / +98 与属性enum | ItemData.getItemType/getWeaponProp；WeaponProperty.getPropType/getInitValue/getType | ITEM_WEAPON、FightProperty、GrowCurve具名常量是原始metadata独立证据；属性字段布局见上。Java对NONE/null属性的过滤是自己的onLoad行为，不能提前删目标第二项。 |
| Weapon backing+2c/+84/+40 | ItemData.getGadgetId/getWeaponPromoteId/getSkillAffix | 有真实值/类型，原生装备消费及语义对照未完成前仅供审查，不按同数字猜字段。 |

可继续实现的文件范围仍只有新离线工具/test/本页及忽略输出。生产接入应在主线对具体字段批准后另交其所有者，不能由本卡修改 `AvatarData.java`、`AvatarSkillDepotData.java`、`AvatarSkillData.java`、`ItemData.java` 或共享资源。没有获批的字段保留unknown；不创建“为了能load”而补空的转换。

必要顺序：同源 Skill→Depot→Curve/Item→Avatar，并完成AbilityEmbryo/ConfigAvatar/PlayerAbilityGroup引用。新号必须先使11101 ItemData可用；否则 `new GameItem(11101)` 先在 `data.getId()` 失败，早于AvatarStorage的后续非空判断。旧号恢复仍须 `Inventory.loadFromDatabase→AvatarStorage.postLoad→recalcStats→completeLoadingVitals→restoreLoadingVitals(true)`，最后才读取最终HP/能量；本卡没有执行该Java次序，也不因基础depot实际+6c=0覆写已有保存能量。

## 独立错误、边界与复现

新 whole 实验覆盖 key1与`0x80000001`的同hash碰撞（两条都可正确取回）、同avatar追加两Group、重复key、最后scalar截断、header截断、0行表，以及真实Ornament条件数组被注入错误61286 class的类型拒绝。高位值`0xffffffff/0xfffffffe`、实际超2^63、实际float位值、嵌套与列表由独立断言检查。

重复key到 `1578b6f54→1404bbd90` 异常构造入口，错误数组class到 `1404c2c70`。两者停止于入口，不声称完成异常分配/抛出。截断有重要区分：在受控零填充分配中，Group最后scalar少1B时原生仍返回，cursor22超过输入21；正式工具的精确EOF断言拒绝。仅3B header读到cursor4，请求capacity1577058304，明确容量模型拒绝，未进行大分配。**它们不是客户端已做bounds拒绝的证明，也不是自然进程行为/漏洞结论。** 第一次错误预期与后续修正保留在 `all-probe1.log`、`controls-probe1..4.log`，没有将模型拒绝混写为native成功。

```powershell
$taskGame = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/game_path.ps1
python tools/client72_avatar_natural_tables.py --client $taskGame --output local/migration72/d02-active/avatar/natural-tables/first.json
python tools/client72_avatar_natural_tables.py --client $taskGame --output local/migration72/d02-active/avatar/natural-tables/repeat.json
$env:CLIENT72_NATURAL_TABLES_REPORT = 'local/migration72/d02-active/avatar/natural-tables/first.json'
python -m unittest tools.test_client72_avatar_natural_tables -v
```

两个全新进程正式双跑逐字节一致，14项独立断言全部通过；日志 `first.log/repeat.log/tests-first.log`、证明 `determinism.json` 同目录保留。每次报告还固定依赖源码hash；不把旧研究测试数重复计入本卡。主线负责第三native复验、共享Project/Harness及本地提交。

| 冻结产物 | 大小 / SHA-256 |
| --- | --- |
| first.json / repeat.json | 15,112,922 B；`1e9817cbea6da6db5ee564d5c3f3a64436fa3319e6d9b8ccf5aef0014cf85a86` |
| tools/client72_avatar_natural_tables.py | 44,027 B；`920395dfd4817523951d2c766167ca481d8f6b2b64288980fee2cade2ae9ecad` |
| tools/test_client72_avatar_natural_tables.py | 11,562 B；`09940af11e6a87347abf4a60f93c8ef4a3fa0efc0c3df8e216cd11428a1b25f4` |

这批共6张完整表（1,807行）、25次真实选定ID查询、实际Group byAvatar 3次查询及6组新增whole/环境边界。它们不计作6个自然资源loader、完整Avatar或Java加载验收。旁路静态/新消费者实验将单独保存，冻结工具与这两份报告不再跟随研究反复改hash。

## 下一首阻塞

“没有真实Avatar/Depot/Skill/Weapon/Ornament字节或parser”已解除，不能重复采登录页空cache。剩余首要工作是实际资源字段→Java及完整Avatar装备/技能/池后缀的消费者桥梁；能力、成长/突破资源同源输入与保存恢复必须随后闭合。自然cold新样本的Array117/Type314/Type[]也已到达并被本线逐span核对、native cache-hit重放成功，不能继续列为旧缺输入；generic comparer工厂/default写回仍是独立未闭路径。本卡所有正式ready保持false。
