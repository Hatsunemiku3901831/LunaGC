# D02-OLD-STORE-SEMANTIC-LOCATE：旧档仓库剩余语义

## 中文有界计划

只读当前Item/Equip/Weapon/Relic raw协议、`PlayerStore.toClient72Item`、旧号完整快照契约及已固定Asta/Luna/Resource候选，固定同CN7.2 EXE/global/startup输入。本项不重新全网检索、全量协议研究或修改运行/Proto/DB/Git/进程，旧场景专题保持稳定。

1. 列已验Cmd→类型、类型→用途、tag/wire及旧档Furniture2331件/TPS8件/Weapon精炼/Relic的真实剩余缺口，区分数据缺省与协议身份未知。
2. 复用既有metadata、typed消费者/xref和固定候选，寻找Furniture/TPS目标oneof及Weapon精炼的affix key/level桥；不按Item7/8/9/10单u32形状补语义。
3. 优先一个成熟可编码出口；最多选择一条独立canonical typed source核准确AOT/pdata/边界后完整取源分析。准确依赖A返回/构造则停该动作，保留原停止点并转独立项，不加模型或换工具重试拒绝。
4. 直接事实、业务推断、未知分列；成熟规格即时交集成者，否则给唯一最小必要新输入与解除条件。原始证据只写ignored `local/migration72/d02-old-store-semantics/`，唯一tracked产物为本文，最后冻结交主线。

## 第一阶段：精炼投影合同（冻结）

**已闭合一个可独立编码的出口：旧档Weapon的正affix组原值作target map key，保存的零基refinement原值作value。无需减1、clamp或新增native取源。** Furniture/TPS的目标分支身份仍未闭；本阶段不等全族后再交付精炼合同。主线为并发轮转要求先冻结本阶段，后续家具/TPS只追加章节，不改本节已验内容。

### 三层来源与实际桥

Cmd/type/wire复用固定 [库存raw](CLIENT_72_INVENTORY_GRAPH.md)、[材料合同](CLIENT_72_INVENTORY_CONTRACT.md) 和 [Equip合同](CLIENT_72_EQUIP_CONTRACT.md)：`20892→68537`，根tag2的Item65333，Item tag6→Equip44169，Equip tag2→Weapon64947，Weapon tag4 `JMGKMMFNABP` 是 `map<uint32,uint32>`。独立目标consumer `150758E60→152B8EF00` 已证map key选择EquipAffix组、value选择组内level key，枚举/角色存储保留原值，没有value减1。

本轮补的是**本服保存状态到该已验业务角色的桥**，不用旧Proto当oracle：

- `GameItem.refinement` 声明初值为0，实际保存字段就是该值。
- `InventorySystem` 的正常精炼上限为4，升级计算 `min(oldRefine + feedRefine + 1, 4)`；这证明当前业务为零基等级，不从显示星数推断。
- `Avatar` 的实际战斗属性和 `AbilityTalentResolver` 的能力效果都以 `savedAffix * 10 + weapon.getRefinement()` 查GameData的EquipAffix行。查到行的 `getMainId()` 是组、`getLevel()` 是等级；`EquipAffixData.getId()` 返回flat `affixId`，flat lookup不是wire key。
- 固定旧号全部1663武器逐行核同一保存affixes/refinement、实际所选Weapon行和EquipAffix行，结果如下；原字段和数量未改。

因此可接入现有 `PlayerStore.toClient72Item` 的规格为：

```text
for each saved positive affixGroup in item.getAffixes():
    Weapon64947.JMGKMMFNABP[affixGroup] = item.getRefinement()
```

取已加载同一GameItem的原值；不乘10作wire key，不减1，不从当前候选配置重新生成/覆盖保存affixes。保存空affix列表仍为真正空map，不添加零key占位，不修改存档。target原生样本已分别验证空map和 `{group:0}` 具有不同含义，map中零value不能因为是0而删除整条entry。

### 全1663行结果与例外分类

| 检查 | 实际结果 |
| --- | ---: |
| 固定保存武器输入 / 逐行输出 | 1663 / 1663 |
| refinement分布 | 0：1139；3：1；4：523 |
| 保存affix列表 | 1542行各1个正group；121行为空；无重复/非正group |
| target map记录数 / 不同(group,level)组合 | 1542 / 264 |
| 正group属于该物品实际所选Weapon.skillAffix | 1542 / 1542 |
| flat lookup唯一、行group等于保存group、实际Java level等于保存refinement | 1542 / 1542；错误0 |
| 空map对应的实际所选Weapon正skillAffix组数 | 121行均为0，分布于5种物品；不会以空map删除本服已有正组 |
| JSON省略level、实际Java level=0的保存记录 | 1018 |
| 已批准server-provisional词条效果 | 7行，影响7把武器；key/level一致，效果不冒target原版 |

**1018项省略level的来源类别是服务端真实加载行为，绝非恢复target缺省值。** 当前 `EquipAffixData.level` 为plain int，缺JSON属性时Gson保留Java零值；`onLoad()` 只处理属性列表，不改id/level。`ResourceLoader.loadFromResource` 用真实typed `JsonUtils.decode` 后调用 `onLoad`，再按 `getId()` 安装。已有 `D02OldSnapshotResourceLoadTest` 确实加载342条EquipAffix；已有完整旧号恢复/冷载测试保留全部保存affixes/refinement。本轮只读复用这些证据，未启动JVM、反射造GameData或加native默认模型。

本服资源身份/效果的级别仍独立：Weapon/EquipAffix具名候选来自固定AstaPS-Resource commit `6cfecfb55f889f8e3b7156adb12809608db4f74b`、root `3cf83488a94b47a018934e5f91c1bf7e93357aae`；七条效果是既已批准的服务器策略。此合同证明同一保存状态、当前服务器实际业务lookup与目标map用途对应，不证明这些候选效果或全target原始资源表一致。无需追原版公式或重做全资源。

### 固定输入与证据

目标三输入SHA继续固定，来源沿既验raw/consumer合同，不换版本：EXE `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`；global metadata `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`；startup metadata `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`。本轮没有新增native body或重跑原native fixture。

| 原件 | 字节数 / SHA-256 | 类别 |
| --- | --- | --- |
| `protocol/cn-7.2/equip-contract-observations.json` | 329127 / `77e1df9d53d93380362f30ec0ad604b447a1123f4840963fa9f91a1da41b5011` | 已验target用途/wire与原生消费 |
| `protocol/cn-7.2/inventory-graph-observations.json` | 105661 / `ca45b0d9b038cd46fc35943924ca77ae46c8fbae82c06e131245e03f43d95b18` | 已验完整raw图；未命名分支仍raw |
| `local/instances/d02-old-restore/evidence/weapon-saved-affix-refinement.private.json` | 724418 / `a2f9637b36b26ba2f76bb92ef55b2afffbd34883afa076da97b078df1cdc9bed` | 原BSON全EOF只读投影；1663行，不重导出 |
| `local/instances/d02-old-restore/resources/revision-8/D02-OLD-RESOURCE-MANIFEST.json` | 3829658 / `941c5f30e1d66d0208f8a4618bca30e439b82527130b06a1168aa9cf2574e0b9` | 已验本服实际资源包与来源分类 |
| 同revision的 `ExcelBinOutput/WeaponExcelConfigData.json` | 319695 / `0de5230b5adf6c19abb7bf93604afc4c01c27b2017f0eb50e81d7f13d5e2e45a` | 当前实际所选239种Weapon |
| 同revision的 `ExcelBinOutput/EquipAffixExcelConfigData.json` | 128033 / `bf5e07b08c793943a562c354572c3bed433e7f7d45fda9d323b3ee761edbaa71` | 当前实际所选342行，含明示策略 |
| 固定原始候选EquipAffix blob `355996dedf261ec03586e1639a99bc8295e96d6e` | SHA `397c2870fa34a8e6173b53d0edb4a947c1285b1eb0e709734089028fefc0828a` | upstream-candidate，不是target数据恢复 |
| `local/migration72/d02-old-store-semantics/affix-run3/result.private.json` | 1547242 / `c81c73989a941a1544a1bc4a6cf2d50f565dbdf4765bb11d0d2bcab3a5299d06` | 全1663逐行映射、原source引用、全部例外与源码pin；含私有信息，不提交 |
| 同目录 `loader-and-empty-proof.private.json` | 2748 / `dd908846ce6d698644aa0aba4734f9ce648cec0d9b4e03121bb11b041a667f41` | 121空map补核、真实Loader来源及旧验收引用 |

当前源码逐字节pin，业务桥以实际实现为准：

| 源码 | SHA-256 | 直接事实 |
| --- | --- | --- |
| `src/main/java/emu/grasscutter/game/inventory/GameItem.java` | `e9de522816d9400c1cd3a3ba3ae9ca9ff6691365ceee1ecb8d534c4a03cef132` | refinement零初值、保存状态；旧Proto不作target oracle |
| `src/main/java/emu/grasscutter/game/avatar/Avatar.java` | `9176d33373ee5e663f1cc8f3a9137b65c3def1908fc13035dc5569e4bd4cc31d` | 实际战斗词条lookup |
| `src/main/java/emu/grasscutter/game/systems/InventorySystem.java` | `c5a39c6358180191403f3a35ff76bfb11c2c8ae737d974b1beb4159360870b45` | 实际零基精炼升级/上限 |
| `src/main/java/emu/grasscutter/game/ability/AbilityTalentResolver.java` | `3809064764b44c9d97ba0fe928a8b7755d92317e8cf62b666722736e7e8e5cce` | 同组同level能力lookup |
| `src/main/java/emu/grasscutter/data/excels/EquipAffixData.java` | `cbbcb2bc4a833f0f2b1cc481a6d73c517da833b823fcaa96437e938ac5c6735e` | flat getId、group getMainId、plain int level、onLoad范围 |
| `src/main/java/emu/grasscutter/data/ResourceLoader.java` | `4d39af8d8ecfbcaa1a5c5dd43d55f01ad59860e064e5da919a96b4da4e67692b` | typed decode/onLoad/install真实路径 |

独立实际Loader/恢复证据沿[已有Java验收](CLIENT_72_D02_OLD_SNAPSHOT_JAVA_20261116.md)：`D02OldSnapshotResourceLoadTest` 源SHA `90e3b5b9249e79886f5fc36cefa320c956d5c5730a73126efb85795ce7227f16`；旧loader日志1099B/SHA `272c56dc3c3b213695ed3d03036db550f50a6a7475038208f7eb3cabcc2c5341`；旧 `old-full-restore-final.xml` 3005B/SHA `8b8b5dcbc96d375d5466b27fe92e482561fe78ed658e62a3222d17fabf7fab60`，1项、失败/错误/跳过均0。后两件在 `local/migration72/d02-m0-integration-20261116/`。这是已存在的真实Java运行证据，不称本轮重跑。

### Item形状纠正、Relic分类与未完成边

现有raw图完整覆盖六个Item oneof；7/8/9/10并非全为单u32，形状更不能充当语义：

| Item分支 | 实际raw子类型与字段 | 本阶段语义状态 |
| --- | --- | --- |
| tag7 | 51247 `JOPADICJMPJ`：tag1/u32 | 未绑定Furniture，不因与count同名批准 |
| tag8 | 63899 `MKFCDFEEAAL`：tag1/u32、tag2→61085；61085/tag1为u32→u32 map | 未绑定Furniture/TPS |
| tag9 | 40274 `MIEKLLOFKOJ`：tag1/u32 | 未绑定Furniture，不按单值外形选中 |
| tag10 | 14095 `JLAGHMMLDCA`：tag2 repeated u32（packed/unpacked）、tag3 u32→u32 map | 未绑定TPS；配件保存列表存在不等于该list角色已证 |

旧号全10727活动Item仍保留，当前有限Store仅覆盖6367 Material、1663 Weapon、358 Relic，共8388行；2331 Furniture和8 TPS在当前sender默认分支被略过，尚不能称完整Store。121 Avatar、所有原12254 Item及1527非活动行/原快照证据均不删减。精炼补map不解决这些分支，也不自动恢复其他sender。

Relic缺口分层保留：358行已有level/star/lock target合同；74063其余tag2/3/4/5/7/8的业务职责未闭，不从与Weapon字段同名补经验/突破或主副词条。保存主词条28种、副词条22种和所选候选资源已齐是本服资源事实，不等于其wire映射已验。目标资源rank/set职责仍存在普通consumer缺桥；既有raw+88链的准确A依赖另列于[Relic来源](CLIENT_72_D02_RELIC_CODEX_SOURCE_20261116.md)，不因此泛封整个Relic族。

Furniture/TPS本轮**未取新native source，未选择或执行A/构造动作**。现成最小后继typed候选是已验转换链中的 `owner68895/m569928 ELMGDFDHGJN@148FED420`：public static非构造、唯一非by-ref参数Item65333；先核普通AOT/唯一完整pdata，再只审其分支到存储/资源类型的直接关系。现仅精确metadata声明，不把旧零碎执行升级为全文本消费证明；若实际遇准确A返回，停该依赖，不模拟后继续。对照工厂 `owner51337/m426915@150ED35E0` 带Item65333及模型51337参数，不是本轮已选第二源。

声明原件 `next-typed-candidates.private.json` 1888B/SHA `bb2105d13a5a435c70366a12e761d039015ab6d8b917bb6af4a9886bae02982e`，只精确解引用这两个已知地址，未做无参方法泛扫。metadata/signatures仍固定 `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f` / `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe`。

### 实际执行、停止与交付

本轮完成全1663保存武器只读交叉核对、真实Java业务/资源类型审阅、既有target map合同复用、121空map后继补核及两条精确typed声明。没有新native、JVM、DB、构建、Proto/Java、进程或客户端操作，也没有Git操作；成熟map规格已即时交集成者，由其串行实现并以非零/零level、多组/空map及原保存值不变的独立wire断言验收。

两个本地输入结构误读及一份raw JSON缺level的初判均保留在ignored原件：ItemId为直接int而非value wrapper；metadata ancestry的parent可为null；raw缺level不能写成实际Java level未知。后续只修正只读分析的来源解释，不更改输入pin或目标模型，完整失败/1018条原初判与最终0错误并存。它们不是安全拒绝、目标源漂移或放宽协议校验。

当前停止原因是主线并发轮转，非安全拒绝；Furniture/TPS普通来源定位作为下一独立followup继续。本阶段没有触达新的准确A/B/preview依赖。refinement合同可独立验收/提交，Furniture/TPS和Relic映射及全量旧号网络入场、Ready/entry-ready仍未完成。
