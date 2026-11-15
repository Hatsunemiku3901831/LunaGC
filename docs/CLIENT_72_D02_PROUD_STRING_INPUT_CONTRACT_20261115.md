# D02-R3：Proud 中性 String 输入合同准备

任务 `R3-PROUD-STRING-INPUT-CONTRACT`，父项 `E-01d1 / D02-R3`。本页复用 [31A 三列来源](CLIENT_72_D02_PROUD_MANAGED_ROW_REMAINING_20261112.md)的已验中性字段及既有合同，补充未来输入记录中必须区分的状态、表示、来源身份和尚未确定的限额；不重新采集，也不把合同准备标成实际 String 已验。31B 与 [Float 来源及断言](CLIENT_72_D02_PROUD_FLOAT_CONSUMER_SOURCES_20261115.md)只确认另一列的来源和条件位样，不能授权或代替 String 读取。

## 1. 已验基础与必要性

31A 已明确合法既存标准 cache `P` 的限定前提下，`M=QWORD[P+40h]` 为非零且确属继承 26072 的 41105；26072.field127898 位于 `M+20h`，声明 `System.String`。type293 的 field1211 是 `m_stringLength`，`S+10h`、Int32；field1212 是 `m_firstChar`，`S+14h`、Char。`S` 是 field127898 的 String 指针。本包复核这些已保存声明，不证明当前存在或持有合格 `M`、`S`。

31A 已列出中性 String 指针／长度／原 UTF-16／文本和条件 catalog 对照，也已规定 null、失败和未读不能替换为空串。这里补的是各层的状态与来源要求：空对象和零长度如何分开、原始 code unit 与可显示文本如何分开、长度必须受实际授权范围约束。原合同并未给出已批准的具体对象、采集 API、最大长度或运行预算；本页同样不能提供它们。

## 2. 未来输入与记录边界

| 层次 | 将来所需输入或结果 | 可判定／不能推断 |
| --- | --- | --- |
| 对象与来源 | 独立合法来源提供的 `P`、非零 `M`、其 41105/26072 身份和同一观察的来源记录；`M+20h` 指针需在授权范围内 | 现有 M 未取得。若身份或所有权依赖被拒分配返回、热 Default 或其产物，则该来源不可用于此合同；不能自行构造 cache。 |
| 指针状态 | 记录未读取、读取失败、明确 null 或非 null；非 null 才有后续对象内容 | null 是观察到的空引用，没有 length 或文本；未读和失败也都不是空串。 |
| 长度 | 对合法非 null `S`，按固定 type293 声明记录 `S+10h` 的有符号 Int32，单位是 UTF-16 code unit | `0` 且对象/读取有效时表示零 code unit；负值视为不满足此合同。非负并不证明对象有效或可读。具体最大值必须由另行获准的来源、范围及预算确定，不能凭 Int32 可表示范围设实际合法上限。 |
| 内容 | 在长度和读取范围获准并完整有效时，记录 `S+14h` 起恰好 `2 × length` 个原字节及 code unit，关联同一对象、长度和观察来源 | 长度为零时无内容字节；短读、越界、超过授权上限或对象身份无法维持均为不完整，不能补零或声称文本完整。字符数不是 Unicode 标量数，也不以遇 NUL 作为长度。 |
| 文本 | 明确字节序与解码结果，保留原字节和 code unit；可解码时单独记录文本 | 当前固定声明支持 Char 布局，目标为小端 x64 的候选解释，但本包没有 String 字节样本来独立验证；未来输入须带可验证的字节序/来源。若含不配对 surrogate，保留 code unit 并标明严格 Unicode 文本无法完整解码，不以替换字符冒充原值。不做大小写折叠或 Unicode 归一化来强制匹配。 |
| 业务解释 | 实际文本与 host catalog 可作明确标注的候选比较 | 需要额外证明该客户端 field 到具体业务消费者的桥，以及目标配置与所对照配置的同源身份，才可讨论 `OpenConfig` alias；只匹配文本、Java 名称或形状不够。 |

后续[限定String查询与双字段判定](CLIENT_72_D02_PROUD_STRING_LOOKUP_AND_GATE_SEMANTICS_20261115.md)已连接该字段到条件容器查询及572232的低字节匹配；这不是OpenConfig业务或配置同源证明。原输入合同与14项检查不变、不重跑；actual M/pointer/length/UTF16仍未知，旧52 RPM/298B范围不自动扩展。

以上对未来证据的要求不指定新的目标读取方式、现场预算或实际字符串。为关联字段、长度和字节所需的一致对象来源目前未知；不能把不同观察的内容拼成同一实际值。对目录比较，必须记录比较对象的版本与哈希，而不把当前 checkout 自动等同客户端资源。

## 3. 条件文件与 host 对照各能证明什么

31A 已固定的条件 base-prefix 报告给出 22B 块的 ASCII 视图 `Ayaka_PermanentSkill_1`；报告同时标 `stringEncoding=null`、`nativeStringReturned=false`、`nativeStringConstructed=false` 和 native→managed 物理复制未证。它只是一段条件文件输入的 ASCII 视图：既不是已证文件字符串编码，也不是 `M+20h` 的指针、length、UTF-16 字节或实际文本，不能用它补实际 M。

当前 checkout 的 `ProudSkillData` 声明 host `openConfig`；`Avatar` 使用该值查询 `GameData` 的配置项，null 或空串在该方法中均直接返回；`AbilityTalentResolver` 将 Proud 的 `openConfig` 与 `paramList` 一起用于 host talent 处理。`ResourceLoader` 从本机 cache 和指定目录加载 host 配置集合，不能仅因某个文件有条目就宣称运行时一定采用该文件或目标客户端同源。

固定 `ConfigTalent_Ayaka.json` 的该候选 key 含一个 `UnlockTalentParam` 和两个 `ModifyAbility`，后两项引用 `%1`、`%2`。这证明当前文件存在可对照项，不证明当前目标客户端的 field→业务映射、实际 key、配置文件身份、效果或采用。对 host 这条特定调用路径，null 和空串都不会加入胚胎；输入记录仍须保留二者差异，不能推及所有消费路径。

## 4. 固定证据与复核

本轮只读核对如下来源，大小与 SHA-256 全匹配：

| 来源（相对 checkout） | 字节 | SHA-256 |
| --- | ---: | --- |
| `docs/CLIENT_72_D02_PROUD_MANAGED_ROW_REMAINING_20261112.md` | 7733 | `60741b92b7db4a55fcc9d83d03bafbc0fc2750e4519cc2520b2721fd6d2e84a2` |
| `local/migration72/d02-resume-20261111c/proud-managed-row-remaining-triage/triage.private.json` | 24816 | `b246d17f58eafd95a7b1883a352033fe70ea3081d306c5926f67b35721b2ab41` |
| `local/migration72/analysis/current-structures.json` | 302812743 | `d5d7f671a622439b2f9a3ed0018ce551aa5c573cd9ca68bed3b006712b8a98aa` |
| `local/migration72/d02-resume-20261110b/c-proud-base-prefix-reference/main-third-v2.private.json` | 27306 | `e01108b35842e1c2412e4343dd506b14cbf1715c3de285185154193c298267f6` |
| `resources/BinOutput/Talent/AvatarTalents/ConfigTalent_Ayaka.json` | 9379 | `0b0e1fbbab1fe4abefca66e3795c243c7516c5c27bec37710203be3dd5dda7f5` |
| `src/main/java/emu/grasscutter/data/ResourceLoader.java` | 56651 | `fa7b7d26ef234e92f6d7eac5790922d36dc76fb113b5a31cc80c7bad40d2a015` |
| `src/main/java/emu/grasscutter/data/excels/ProudSkillData.java` | 2237 | `bacd37a17a85d409b4cc701cc0c0e180fdea8fcf208026a148e73f1afaeadef2` |
| `src/main/java/emu/grasscutter/game/ability/AbilityTalentResolver.java` | 10337 | `3809064764b44c9d97ba0fe928a8b7755d92317e8cf62b666722736e7e8e5cce` |
| `src/main/java/emu/grasscutter/game/avatar/Avatar.java` | 71980 | `9176d33373ee5e663f1cc8f3a9137b65c3def1908fc13035dc5569e4bd4cc31d` |

以上 pin 来源为 31A 及后继固定审核 `local/migration72/d02-resume-20261115/proud-float-sources/source-audit.private.json`（17810B／SHA `687eab6923aaaef3119edaa7630022c08952adc26497a8c30d16f21ff720f117`）；31B 验收回执为 3387B／SHA `62ab88a57a0d31fc0c82afd938a32d3fe74f067947746d3626e34aa3c5e5884e`，仅验 Float getter 来源。复核时 Float 专题为 14973B／SHA `a8374a9e588eabd91f43cd2cc01db95b622037f290f35ed9a2e45a5f927e4305`，其后本包未修改。

本轮对固定 triage 与单个资源文件做 14 项只读交叉断言，14 通过、0 失败：字段和两项 String 布局、实际值缺失、22B ASCII 视图、原编码及构造/复制状态、catalog 三项及两个参数引用、alias/row 未批准。未执行目标、生产测试、构建、部署或客户端验收。

## 5. 精确剩余项与后继

已解除的是未来中性 String 输入记录的状态和来源合同准备，不是实际值恢复。尚缺：独立合法且可证明身份的既存 `M`；受批准的同一对象观察及读取范围／预算、最大长度和有效字节序证据；实际指针/null、长度及内容；目标 field 的业务消费者桥；目标配置同源身份。52RPM/298B 标量读取的既有范围没有覆盖这些 String 项，也不产生新的现场许可。

删除 A 的第二 allocator CALL/RET、热 Default、同 epoch 归属及 B 的 loader 修复和相关产物后，上述已存声明、条件报告、host 源和单资源对照仍可独立复用。当前材料无法形成可独立取得实际 String 或验证业务桥的成熟普通执行包；待上述准确输入和新授权范围存在后，由主线判断独立来源及验收。无需引入其他字段路线、全资源扫描或重复构造文件输入。`actualRowApproved`、业务 alias、`resourceReady`、D02-R3 与 D-02 均未升级；本包无新安全拒绝。
