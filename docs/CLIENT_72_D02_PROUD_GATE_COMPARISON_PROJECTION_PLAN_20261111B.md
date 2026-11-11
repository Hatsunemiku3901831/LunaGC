# E-01d1：81127 已观察对象状态的最小 Java 投影计划

2026-11-11B，Sol max 规格交接。可独立实施：将已验 Depot22284.field105355 / `+18` 数组中，同81127元素的 field404854 / `+14` group 与 field404853 / `+10` required promote phase，复制到现有 `AvatarSkillDepotData.inherentProudSkillOpens` 两个 canonical 字段，并通过真实 ResourceLoader 验证。批准范围仅为**已观察对象状态的两叶投影**；未批准当前实际采用公式或目标序列化 alias。

[比较合同](CLIENT_72_D02_PROUD_GATE_COMPARISON_PROMOTE_SEMANTICS_20261111B.md)已由主线第三重算：47421 B，SHA `ecdbace6ebda2667182dc5d2cdfa092759b17a71919d352d23d7a9e99732df8d`。它直接证明同元素 group非零检查、required phase 与同 Avatar property1002加一的相等比较及预览列表采用／跳过。这里足以命名两叶的对象职责；不把相等比较替换成 Java `<=current`，不生成 Proud 行 ID。

## 输入已齐

| 固定已验输入 | 大小 / SHA-256 | 本包用途 |
| --- | --- | --- |
| `local/migration72/d02-resume-20261110b/c-all-depot-refs/main-third.private.json` | 9014403 B / `b6c806dfd5634f20501a9e57a70d88bf4d41b63b47740cf68dff817b7d79527a` | 全121个保存角色的121个唯一当前 Depot 快照；直接复用实际 query/getter 返回值。 |
| `local/migration72/d02-resume-20261110b/c-depot-fields/main-third.private.json` | 1121330 B / `8c06c130d7f2efa6a31a5848eda471810834e019c1a1153158576d2c15f3dfd5` | 四个活动样本；按 Depot key、原行 source 和两叶原序与上项交叉比较。 |
| `local/migration72/d02-resume-20261111b/proud-gate/promote-preview-contract.private.json` | 16614 B / `8c55511884462ee76cdb75a22aba296d83526a50cb7eb5d7cfaacdb3771c30e2` | 两叶、同对象及比较职责的已冻结边界。 |

全121份快照各有5槽，共605个81127元素、1210个 U32 叶。required phase 为0／1／4，次数363／121／121；group有390个非零槽及215个零槽，所有叶值范围0..15025。四份活动报告可直接使用，但不能单独代替全121。以上来自固定报告的纯 JSON 定向读取，没有重跑旧 parser、runtime、query 或 getter。

私有机器规格 `local/migration72/d02-resume-20261111b/proud-gate/projection-input-contract.private.json` 为13035 B / SHA `81e71c2f8cc2e0b16f88ef03418abe9ef1a660bdf2779bcae8103ed41ba10fd4`，固定上述输入、准确 Java 源哈希、121键原序和四样本 source交集。它是实施输入规格，尚非生成的 Java 资源或测试通过报告。

## 精确映射与保留规则

| 观察字段 | 现有 canonical Java 字段 | 限定 |
| --- | --- | --- |
| 同元素81127.field404854 / `+14` 的原 getter U32 返回 | `InherentProudSkillOpens.proudSkillGroupId` | 原值复制，不查 Proud 表，不乘100，不筛除0。 |
| 同元素81127.field404853 / `+10` 的原 getter U32 返回 | `InherentProudSkillOpens.needAvatarPromoteLevel` | 原值复制，不按角色 phase 过滤，不把 threshold0 解释为默认解锁。 |

输出每行 `javaFields` 只含 `id`、`inherentProudSkillOpens`；每个元素只含上述两键。按原报告行序、原数组索引复制，保留重复、所有零槽和原5项长度，不排序、去重、裁剪或填充。两字段使用**已有 canonical 名称**，不新增 BOIOJNENKHP 等目标 obfuscated alias，不修改生产注解。

旁账保留原 `fieldIndex`、offset、元素索引、原 U32 数值、原行 source/span/hash、原 query/getter 报告位置及 inputPresence。全121报告的叶 presence 是 unknown；四样本未单列叶 presence 的地方也只能记录“该报告未给出，unknown”，不能把 metadata 属性缺失当 wire 缺字段。输出显式数值零只表达对象中观察到0，不能提升为原输入显式存在0。

当前固定实际值均可由 Java `int` 同值承载，直接复制数值即可。读取原报告用 U32 范围，不先经有符号转换；不截断、取绝对值、clamp 或 wrap。若未来实际输入超出 Java 正 `int` 范围，本规格停止该投影并在失败证据中保留原 U32，不擅改负值或类型。本包没有实现通用高位编码方案的需要。

固定121份实际数组都非null，元素与两叶均完整。如果来源漂移到 null／缺元素／缺 value，停止并报告，不能用 `[]`、空对象或 primitive默认0替代。presence 标记未知本身不阻止已观察对象值复制。没有 ability、skills、talents、special/quest 或其它资源补值；未触及的 Java 既有默认不获得目标业务许可。

## 当前 Java 与真实 loader 链

`AvatarSkillDepotData.java` 为5169 B / SHA `2aa9dd97dd2d375d430af7053d3e7adab58e91cf3b997fadf5852d33c24d4f52`。外层已有 `List<InherentProudSkillOpens>`，两叶为 `int`；`onLoad()` 对已存在列表不排序、不筛选、不改叶值，仅缺/null列表规范化为空。

`ResourceLoader.java` 为56651 B / SHA `fa7b7d26ef234e92f6d7eac5790922d36dc76fb113b5a31cc80c7bad40d2a015`。准确入口是同 package 可见的 `loadFromResource(AvatarSkillDepotData.class, Path, Int2ObjectMap)`：读 JSON array，逐行 `JsonUtils.decode`，`installResource` 执行真实 `onLoad()`，再按 `getId()` 安装 map。该入口逐行拒绝错误，因此测试必须核全部121键与605槽，不能只以命令成功验收。

JUnit 将投影片段写入 `@TempDir` 的实际 JSON 文件，并用局部 `Int2ObjectOpenHashMap` 调此入口；不安装到生产路径、不调用 `loadAll`、不创建 Avatar、不重算属性、不执行通知。无需改 `AvatarSkillDepotData`、`Avatar.java` 或 ResourceLoader，也无需全局表清空、数据库、服务或部署。

## 给 Terra 的最小实施包

允许新增以下文件，其余只读：

- `tools/migration72/proud_gate_comparison_projection.py` 与 `test_proud_gate_comparison_projection.py`。
- `src/test/java/emu/grasscutter/data/D02OldDepotInherentProudProjectionResourceLoadTest.java`。
- 结果专题 `docs/CLIENT_72_D02_PROUD_GATE_COMPARISON_PROJECTION_JAVA_20261111B.md`。
- 私有 `local/migration72/d02-resume-20261111b/proud-gate/projection/`，包括两fresh、新basename失败记录及主线使用的最小 Gradle init script。本文、机器规格及旧专题冻结。

Python 工具仅固定报告与上述 Java 源为输入，不能导入/启动旧 native parser、A 草稿或 runtime。单份输出包含 `sources`、`scope`、原序 `rows[{source, rawSlots, javaFields}]`、四样本 join 和明确未通过项；真实 loader 用的裸 JSON array由 JUnit 从这些 `javaFields` 写到临时目录。产物仅私有，不写 `resources/`。

Python 验收至少覆盖：605槽逐元素两叶复制与确定性；四样本与121按 key/source匹配；0和重复保留及presence未知；缺字段/缺value、错误fieldIndex/typed owner、超U32或超现有Java同值范围停止；固定源漂移拒绝。验证在输入边界完成，不在内部重复防御。

独立 JUnit 至少两项：全121真实 `loadFromResource` 后逐 key／索引，直接从**原 native 报告**取期望两叶核对 getter，并明确核363个零门槛、215个零group和原605槽；四活动报告的原序值及 source独立交叉核对并单独真实加载。期望不能从投影的 `javaFields` 自证，不能只调用 Gson、手工 `onLoad` 或 mock loader。所有 JSON 必须只含本规格允许字段；空 ability 不得出现。

Terra 可运行本包纯 Python 测试及两次新进程投影，交完整 SHA、输出及实际测试计数；JVM／Gradle与Git由主线执行。主线使用既有 portable环境及 Gradle `test --tests emu.grasscutter.data.D02OldDepotInherentProudProjectionResourceLoadTest`。现有 `build.gradle` 不转发任意 `-D`，可用本包私有 init script 仅转发 `d02.proud.opens.*` 三项路径到测试：`projection`、`allDepots`、`activeDepots`；不修改 build.gradle。主线再做第三 Python 新进程，要求与两fresh逐byte相同，并核真实 JUnit XML及完整121键、605槽。

通过仅设置 `observed81127LeafProjectionVerified=true`、`realResourceLoaderRoundTripVerified=true`。旧全量/业务批准标记不借此晋级；`currentProudAdoptionVerified`、`javaLessOrEqualProven`、`groupTimes100Plus1Proven`、`actualProudRowConstructed`、`actualPairMapHit`、`resourceReady`、`completeAvatar`、D02和ready均保持false。

## Proud 剩余项的准确独立性

| 剩余出口 | 已验来源／准确前置 | 与拒绝项的边界 |
| --- | --- | --- |
| 本两叶对象投影＋loader | 本计划三个固定数据输入、现有Java两字段及真实Path loader | 删除A第二返回和B组合loader的动作/产物后仍完整成立。 |
| 真正 pair lookup hit／碰撞对照 | 已验 hot insert、3 miss；原 FindEntry 在hash相等时需要热Default对象、派生klass、`[klass+110]` 函数及伴随MI的准确同次输入 | 主线等待用户new ready取得已约定热输入。不新做coldDefault，也不改为host Equals。 |
| 固定22101条件文件语法 | [完整单key reference](CLIENT_72_D02_PROUD_SINGLE_KEY_REFERENCE_20261111.md)已验81 span、4→334；保持其普通文件/inline/正常返回条件 | 此已验出口无A/B依赖，直接复用；不重跑、不扩表，不当actual typed row。 |
| native packed slot→当前管理字段／目标行实际构造 | [derived合同](CLIENT_72_D02_PROUD_DERIVED_FILE_CONTRACT_20261111.md)明确 nativeData 是 QueryWrapper结果，不是41105；packed slot尚无精确getter/layout桥到203373/+84、203376/+90。实际还须相应表示、reader、allocator/对象和原parser条件 | 这是准确数据桥与实际构造前置，当前所读来源未给出其到A第二allocator的确定CALL/data边，故不把整条row/parser研究宣告A阻塞。任何实现若必须接被拒第二返回或热Default后继归属，则只停该准确依赖，不能实施或拆包重试。 |
| Proud addProps／OpenConfig等必要配置及当前采用公式 | 必须先由已证明实际行与实际消费选择所需字段，再给相应typed/配置内容来源；Java算式和旧保存ID不能代目标关系 | 目前未取得将这些配置整体连到A/B的准确source边，不作泛封。配置缺来源保持缺来源，不能空ability、空配置或默认Proud填补；本包也不新启配置研究。 |

本计划是已有研究的小后继，复用已固定 GitHub/分支成果，不另起检索。没有实际执行 JVM、Gradle、native、现场或Git；Project/Harness、状态集成、第三验收和本地提交均由主线负责。规格交回即释放 max，后续实现由主线派 Terra。
