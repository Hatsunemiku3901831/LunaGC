# E-01-bridge-equipment：初始武器与属性资源桥梁

## 有界计划（2026-11-08）

任务 ID 为 `E-01-bridge-equipment`。目标是沿真实 Avatar/Weapon 表、准确原生装备消费者，到 Java `initialWeapon → ItemData.itemType/weaponProp → 同源 WeaponCurve` 的必要字段规格；只覆盖 starting weapon 与 `recalcStats` 的装备前置，不扩展地图、UGC、完整角色能力或玩法。

现证：R3 主线第三报告 `r3-mapping/main-third.json` SHA-256 为 `d990c2c469e5a9d3ee40351e4dfe5223ae49afaa855abe5d912427139c66307e`；固定恢复清单包含准确五输入，旧六表、parser/cache/source 已冻结。Avatar5/7 的 `+154` 实际为11101，真实 Weapon11101 backing 有 item enum、两项属性及 curve enum；这些值和相同 ID 尚不足批准英文职责。准确装备入口为 `151843490/15184A5F0`，weapon 分支 `15184A679..683 → factory51522`。Java `AvatarStorage.addStartingWeapon → GameItem` 及 `ItemData.onLoad` 的装备分类/属性过滤，和 `Avatar.recalcStats` 的 WeaponCurve/weaponProp 是当前必读来源。

未知：Avatar `+154` 的职责消费者；Weapon 原始字段的准确业务目的和原始缺省/省略；同源 WeaponCurve 的已定位缓存/消费者及完整必要输入；完整 Java 行、实际 loader/装备/保存恢复与自然 selectedRevision。原 NativeConfig 模式、3521/6506 仍按旧合同未知，不以旧 empty HashSet fixture 证明自然构造。

依赖：先读取三份既有合同，复用 R3 第三报告、完整六表报告、已有 typed runtime/parser、locator 已冻结原字节与缓存、准确 metadata 和文件窗口；不重复解包、索引定位、七字段 energy 桥梁或名称扫描。主线 proxy 实验期间若当前 Astrolabe 身份变化，先报告并只使用已冻结库存/明确 orig 身份，不改 profile 或跳过 hash。

修改范围仅新增本专题、`tools/client72_d02_equipment_resource.py`、对应测试，raw 仅在 `local/migration72/d02-resume-20261108/r3-equipment/`。不改旧 R3/source/raw、Java、Proto、资源、补丁、配置、Project/Harness；不操作 Git、构建、DB、端口、客户端、调试器、安装或 live，不使用 Astra 或委派。

预算：先做固定来源和 Java 精确窗口审核；最多 10 个直接相关原生函数窗口、3 组装备 caller/消费者原生实验、1 个已知缓存来源的曲线表后继，不开整 EXE 分析。单次模拟最多 2,000,000 指令；复验允许重放相同 case。若曲线需要重新解包/大范围搜索，立即交准确缺口，先完成独立装备字段。遇身份漂移、未建模间接边、类型/跨度/枚举或引用失败时保留首次失败，不能加默认、alias、枚举回退或成功桩。

产物/验收：明确“真实表/行跨度/原始字段 → 准确 native consumer → Java 目的字段”，保留来源、存在/显式0/省略、顺序和原枚举；独立非零、64位、缺引用和未知枚举拒绝检查；双新进程逐字节确定性，交第三复验命令。行片段始终标记 fragmentOnly，不等同完整 Java 行、加载或 resourceReady。证据足够的最小规格立即交主线；缺运行输入则交首缺的准确类型/位置和解除条件，selectedRevision 保持 unknown。

## 已闭合的装备字段与范围

本卡新增的准确消费者已经把 **Avatar 初始武器、Weapon 两项属性、同源 WeaponCurve 和 weaponPromoteId 输入** 接起来。实际重新解析 Avatar169行/64,384B、Weapon288行/128,936B、WeaponCurve100行/26,204B，三者均完整 EOF、逐行连续跨度与 SHA 保留；原生 whole 指令数分别为658,889、859,406、277,854，每次都低于2,000,000预算。没有重跑 Depot/Skill energy 桥梁。

新工具的 `javaProjectionFragments` 只给3个 Avatar 的 `id/initialWeapon`、Weapon11101的 `id/itemType/weaponProp/weaponPromoteId` 与100个 Curve 字段行，共104片段。**全部保持 `fragmentOnly=true`**；没有把这些片段当完整 Avatar/Item 行、实际 Java 加载或 resourceReady。曲线全部字段虽然已解析，也没有执行 JVM onLoad。自然 selectedRevision、NativeConfig mode/3521/6506、全装备赋值/生命周期及旧号保存恢复仍未知。

## 固定来源与同源 Curve 身份

原五输入逐文件在原生执行前后核 SHA，仍是恢复清单中的 stock 身份。主线透明 proxy 期间本卡没有放宽 gate；之前仅在确认 stock 时保存了一份私有 orig 库存，正式运行等主线恢复 stock 后才开始，**不使用 orig 别名替代当前五磁盘校验**。EXE、SDK、metadata、资源、profile均未改变。

旧六表、R3主线第三报告、源码依赖、当前11份 Java 源码及其私有快照均有固定 pin。`metadata.private.pkl` 是用冻结 `extract/type_cells` 从同一固定 EXE/两 metadata 生成的纯 metadata 缓存，不是自然运行状态；每轮先核完整 hash 再读取。旧 parser/source/raw 一律未改。

| 来源 | 身份及界限 |
| --- | --- |
| Avatar、Weapon | 复用已定位原始 `Avatar.native-m_bytes.bin` SHA `466525f2…2ed71c9` 和 `Weapon.native-m_bytes.bin` SHA `cca19efb…a1ff918`，本轮重跑原始 whole/row/UInt32 Dictionary，未重新定位或解包 |
| Curve 具名入口 | 复用已冻结 `equip-contract-observations.json`：owner67884、字段335039 `FNLANEFCGPE`，原布局 `040123A0`，Dictionary<uint32,12924>；准确 literal27754/cell `145B00170` 为 `Data/_ExcelBinOutput/WeaponCurveExcelConfigData`，loader `1502C1BF0`，path getter `1502C1CE0` |
| Curve 缓存条目 | 沿已审 `.MiHoYoBinData` hash规格计算 key `8c4c9ae08c392eb2`、entry `e08c392e`，在冻结同一 `25539185.serialized.bin` 的既有 class1210 index中唯一命中；fileID0/pathID2114/class1208，原 bytes 26,204B |
| Curve 原字节 | SHA-256 `14c5a563a352e01947cdea3b067a0cbfd0c25acbe9d58b6b21207f6f1a462f9f`。冻结 carrier SHA 为 `0311cb19ace1cac5475f197043af14b33657f22c9915e93eb9bffa54cae5e8c7`；本卡每轮直接按既证 offset/length 比较精确原字节 |
| 新 native parser | whole `1502C1960` → row type12924 `14A3A9750`，cache `S+123A0`；头 count 为 `(LE32 XOR 956381B7) + F4C7DE0B mod2^32`，真实为100，不剥额外4B |
| 新旧界限 | 没有重复 archive/块解码、建立新全局 index 或自然资源加载；`globalIndexBindingNewlyExecuted=false`。这是已冻结 serialized carrier 的准确条目投影和实际 parser/cache 消费，不声称当次 live loader 或活动 revision 选中了它 |

本卡将旧 `NaturalRuntime` 构造前的配置存储在任何原生执行前重新绑定到准确 `GlobalVars11325._serializedCodeSwitches:HashSet<Int32>`，明确 buckets=null、次级 List absent。原 `.ctor` 和 Contains 早退仍执行，typed来源不再沿用无关61440的 UInt32 集合。该 fixture 不能证明自然空集合、配置初始化或 native backend 正确；没有把模式设为自然事实。

## 初始武器职责：真实 Avatar 行到装备输入

新的准确 caller 是 owner35156/`14E9B15A0`。它从实际 Avatar cache `+30870` 查行；`14E9B1776` 读真实 Avatar `+154`，原始 `14AEF9DB0` 解码后，在 `14E9B17A1` 写到 Equip proto65333 `+28` 的 item ID。`14E9B17C9` 对 Weapon proto64947 写 level1；随后包入44169的 weapon分支，并在 `14E9B182B` 对65333置 equip case6，`14E9B183B` 把它交给 `1518545B0`。后者的武器分支与已知 `15184A5F0` 一样到 typed factory51522；没有按11101相等或 UGC 名称猜职责。

本轮真实 cache lookup 跑到 `14E9B1684`，返回指针等于刚解析的同一行。producer分别执行三个准确片段 `1776..17A5`、`17B4..17D0`、`17DF..183B`；中间 pool/完整构造不伪造成功，而由明确 mutable typed proto存储提供每段 live-in。`14A42BD90` 原始可变性检查仍执行。三个 Avatar 结果为5/7→11101，独立136→14101；再用真实 Weapon getter `154177600` 查到同一实际表中的对应行。

三个样本均 level1、equip case6；另以合成 GUID `8000000100000001` 验证完整64位保持，不能把它当自然 GUID 生成已执行。Avatar资源本身的 `+28` 等uint64 hash也保留完整十进制字符串/16位hex，不转double或signed int。这些是原生片段，不是完整 caller返回、完整装备赋值或 Avatar构造。

## 字段到 Java 的最小规格

下列 Java 路径相对于 `src/main/java/emu/grasscutter/`。新输入只写 canonical 键，不添加混淆 alias。

| 原始字段 | 准确消费者 | Java 目的与严格转换 |
| --- | --- | --- |
| Avatar58272 field289481 `ECGFIKIODEP/+154`，58209 | 上述真实 default equip producer；真实 Weapon查询 | `AvatarData.initialWeapon:int`。5/7为11101，136为14101；正ID须存在同批真实 Weapon表且适合正Java int，不能截断高位或补0 |
| Weapon继承backing88968 field442106 `KDLEOKGKEAN/+28` | 原始whole字典key、真实getter | `ItemData.id:int`，必须与Weapon命名空间key一致 |
| backing88968 field442110 `DOCGJOECODI/+38`，enum34819 | 原具名 ITEM_WEAPON 域和冻结 typed equip factory合同；当前实际row解析为4 | `ItemData.itemType:ItemType`。仅原名字和值双重一致的项；11101为 `ITEM_WEAPON`。Java `onLoad`据此设 `EQUIP_WEAPON`，不能用ITEM_NONE默认代替。旧factory使用受控资源fixture，本卡不重复计为实际row factory重执行 |
| backing20600 field97502 `MFDMGHFDIJE/+68`，87327[] | `150758430` 原始类型检查后取数组，以 caller ordinal索引 | `ItemData.weaponProp:WeaponProperty[]`。保留两项、原顺序、零和NONE；不能提前按候选缺键过滤 |
| 87327 field435122 `IELLPMKLAIO/+10`，enum54063 | `15075858E`读取，送 `150757A00` 在当前level曲线数组按type精确查找 | `WeaponProperty.type:String`，原枚举唯一符号名；未知/歧义拒绝，不按数值编造 `GROW_CURVE_*` |
| 87327 field435123 `CKFGINHAJBM/+14`，float wrapper54724 | `1507584C8..4D4` 原始 float getter，作为成长计算初值 | `WeaponProperty.initValue:float`，保留原float32位值。第一项 `41b9f5c3`，第二项 `00000000`；不用候选十进制重建 |
| 87327 field435124 `DOIMOBAEJPA/+18`，enum32419 | `1507585C9`读取，交promote属性查询 | `WeaponProperty.propType:FightProperty`，原名字和值双重一致。第一项 `FIGHT_PROP_BASE_ATTACK4`，第二项 `FIGHT_PROP_NONE0`，后者在Java是非null合法枚举 |
| backing20600 field97506 `CBCBDADNCNH/+84`，58209 | 完整原生 `1507587B0`前缀从实际Weapon读取，解码后在 `147BF60C0`入口停下 | `ItemData.weaponPromoteId:int`。实际11101，准确caller参数为group11101/phase0；**未提供Promote row，不批准空addProps或正引用已满足** |
| Curve12924 `KEBIFAHBIBK/+18`，58209 | 原始whole解码作Dictionary key；属性函数按Weapon level查询 | `WeaponCurveData.level:int`。真实1..100，key和字段一致；缺对应level拒绝，不用Java“没有curve跳过”的分支充通过 |
| Curve12924 `OMEAIDPHHPM/+10`，66952[] | `150757A00`原顺序 first-match遍历 | `WeaponCurveData.curveInfos:CurveInfo[]`，保留原数组18项；同type重复必须拒绝，因为native first-match与Java HashMap last-write不同 |
| 66952 field330814 `IELLPMKLAIO`，inline+0，enum54063 | 与Weapon属性type比较 | `CurveInfo.type:String`，要求准确原枚举符号，无未知回退 |
| 66952 field330815 `FCKNPAJIBIL`，inline+4，enum58570 | `150757A00`switch：ADD1/MULTI2/SUB3/DIVIDE4；NONE/未知走原生保留初值 | `CurveInfo.arith:String`保留原符号。Java当前getter只取value，因此本转换只接受 `ARITH_MULTI2`；其他已知arith也拒绝，不能把它默认为倍率 |
| 66952 field330816 `PIMPEPNGCNL`，inline+8，54724 | 原始float getter与实际算术 | `CurveInfo.value:float`，保留原float32位值。实际100×18=1800项全部 `ARITH_MULTI` |

Weapon11101严格输出两项：`FIGHT_PROP_BASE_ATTACK / bits41b9f5c3 / GROW_CURVE_ATTACK_101`；`FIGHT_PROP_NONE / bits00000000 / GROW_CURVE_CRITICAL_101`。Java `ItemData.onLoad`只滤 `propType==null`，所以第二项保留；旧候选省略propType后被滤掉的结果不能替代真实资源。

逐属性的二进制 optional flag 尚未单独恢复。本卡报告的是**原始parser实际产生、准确consumer实际读取的逻辑零**；不声称第二项源码编码中必有显式零 token。转换不靠Java省略默认；必需字段在中性输入缺席时直接拒绝。原数组两项存在、顺序与逻辑零均已在实际表和consumer中验证。

## 原生算术、负例与明确模型

`150758430` 对实际Weapon11101的两个ordinal在level1和20执行至准确 promote call `1507585F0`前；使用新解析的同一Curve cache，原始Dictionary查level、数组查type、float getter与 `150757A00` 均执行。没有Python key→row返回桩或曲线成功桩。

| ordinal / level | init bits | 实际curve value bits | promote前原生值 bits |
| --- | --- | --- | --- |
| 0 /1 | `41b9f5c3` | `3f800000` | `41b9f5c3` |
| 1 /1 | `00000000` | `3f800000` | `00000000` |
| 0 /20 | `41b9f5c3` | `401ae148` | `426102f9` |
| 1 /20 | `00000000` | `3fe22d0e` | `00000000` |

独立检查用解出的两个float32做乘法并舍入回float32，与原始SSE结果逐bits一致。Promote getter另执行至 `147BF60C0`入口，核传入11101/0后停止；没有将缺Promote当0加成成功，也不宣称完整 `recalcStats`。

真实Weapon getter查询 `ffffffff` 原生返回null。独立转换负例再删除正引用11101、删除所需curve、增加同type重复curve、注入未知item/fight/grow/arith枚举、将所需arith改为已知ADD、移除零值属性字段、输入高位uint32，均拒绝；这些是转换入口/原始getter边界检查，不是客户端异常或Java加载验收。

模型仍含typed class/RGCTX和已初始化class状态、准确大小的托管分配、UTF8转换、memcpy/memset、泛型Dictionary容量、单class数组相容；资源存储置换/seed复用冻结实际snapshot，正向置换为已审数学逆重建，不称新自然采集。新Weapon model29353、Weapon proto64947及default equip proto均是明确caller存储；原生whole/parser、UInt32插入/查询、scalar变换、选行、字段读写和算术未被替换。

## 停止边界、下一项与复验

10个准确函数窗口已用完，没有新REA会话或整EXE分析。`151843490`及51522的两个方法确认属于affix/对象相关路径，未把它们误记为weaponProp；原始属性消费改由已知装备合同的 `150758430/150757A00/1507587B0`闭合。首次probe在 `14E9B1631`因未绑定所属函数窗口而拒绝，`probe01.failure.json`保留；后续只绑定准确 `14E9B15A0`范围，不跳检查。其后EOF、字段、算术和拒绝检查才作为正式验收来源。

**下一准确资源缺口是同源 WeaponPromote group11101/phase0。** 旧合同已有 literal81103/cell `145B68498`、`Data/_ExcelBinOutput/WeaponPromoteExcelConfigData`、owner22605/cache `+34E28`；准确reader `147BF60C0`本卡已采到参数11101/0。后继应复用现有carrier/缓存，取得真实行和addProps，不能补空。完整Avatar入场仍需同源AvatarCurve/AvatarPromote12及基础属性/能力引用、完整Item其它必要字段与实际Java加载；不把这组片段拼成伪完整资源行。

自然NativeConfig的准确Int32 set/List/mode输入和活动selectedRevision仍由主线独立观察；本卡不收集live、不读账号/请求，也不扩大现场窗口。只改离线工具/测试/本专题，无构建部署或客户端验收增量。

```powershell
$equipmentGame = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/game_path.ps1
python tools/client72_d02_equipment_resource.py --client $equipmentGame --output local/migration72/d02-resume-20261108/r3-equipment/final-first.json
python tools/client72_d02_equipment_resource.py --client $equipmentGame --output local/migration72/d02-resume-20261108/r3-equipment/final-repeat.json
$env:CLIENT72_D02_EQUIPMENT_REPORT = 'local/migration72/d02-resume-20261108/r3-equipment/final-first.json'
python -m unittest tools.test_client72_d02_equipment_resource -v
Remove-Item Env:CLIENT72_D02_EQUIPMENT_REPORT
# 主线第三个新进程；输出必须是新文件，然后逐字节比较。
python tools/client72_d02_equipment_resource.py --client $equipmentGame --output local/migration72/d02-resume-20261108/r3-equipment/main-third.json
```

正式双进程报告、16项独立测试、来源/代码身份与逐字节比较记录仅存本卡raw；主线负责第三复验、本地提交和统一Project/Harness。没有修改前卡或生产文件，没有将原生片段、完整表解析、Java源码合同和自然客户端验收混为同一层。

实际最终 `final-first.json/final-repeat.json` 各 **4,164,206B**，逐字节相同，SHA-256 均为 `50aaea9406152e1ec9e0c423ec532618b8fe635e3d0cf758d24fccc6403eb5bc`；16项测试全过，日志 `tests-final.log`。另一个不导入本工具的标准库检查器重核62份pin、当前五stock文件、10个PE窗口、literal hash/唯一cached条目/原字节、全部row跨度hash、原生无查询/算术成功模型、float32结果及未晋级状态，共20项全过。检查器不把报告审核当作新原生执行。

最终三文件身份与检查结果在 `independent-final.private.json`。主线第三报告完成后可独立比较：

```powershell
python local/migration72/d02-resume-20261108/r3-equipment/independent-review.py --repeat local/migration72/d02-resume-20261108/r3-equipment/main-third.json --output local/migration72/d02-resume-20261108/r3-equipment/main-third-review.private.json
```

开发预览和首次失败均保留。测试名称最后明确为property **prefix**，随后重新双跑并核新test hash；此前报告改名为 `pre-test-name-{first,repeat}.json`，不混入最终身份。

## 主线验收

主线独立第三新进程报告为 **4,164,206B / SHA-256 `50aaea9406152e1ec9e0c423ec532618b8fe635e3d0cf758d24fccc6403eb5bc`**，与冻结双跑逐字节一致；16项相关测试及20项独立标准库复核通过。原始入口为 `r3-equipment/main-third.json`、`tests-main-third.log` 与 `main-third-review.private.json`，均在本轮忽略目录。主线另检查字段转换、实际消费者停止点、原始逻辑零/NONE保留及模型边界，不将子代理完成声明直接作为结论。

`E-01-bridge-equipment` 在上述限定范围已完成。后继是同源真实 WeaponPromote11101/phase0 与完整 Avatar/Item 必要行，不重复曲线解包或已验属性研究。未修改生产Java、Proto、资源或配置，本项无需重启；没有新增客户端验收，R3及D-02保持未完成。
