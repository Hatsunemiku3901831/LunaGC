# D-02 定向公开复用检索：Skill、资源与自然链路

任务 `D02-SKILL-ENERGY-SEARCH-20261116`；执行输入为[中文计划](CLIENT_72_D02_SKILL_ENERGY_SEARCH_PLAN_20261116.md) SHA-256 `239602ecee31e39a59d9666ae1b98addb23cbbf024d635ba7c165f2fe03ec61e`、[独立输入审计](../debug/d02-resume-independent-20261116/summary.md)及既有 UP-72 账本。本次限定公开 source-only 检索与数据对照：**有具名候选和消费方式，没有足以直接实现目标资源字段的完整合同或独立目标 oracle**。D-02 六项客户端验收不由本报告改变。

保存 cohort 的既验 121 Depot 片段输入 SHA-256 `24b008df69e853d1739fe408634c77698340c8b69294f8344718aa82f108d654`；其中 369 个不同正 Skill 引用有 2 个已验旧片段和 367 个新缺口，121 个不同正 energy 引用有 1 个旧片段和 120 个新缺口。保存 Skill 的 389 键是另一集合。这里的「行匹配」只表明候选 JSON 的 ID 和值，不证明目标运行时版本、字段解析、presence 或加载结果。

## 固定来源和许可

| 来源 | 固定身份与边界 | 本次分类 |
| --- | --- | --- |
| [AstaPS-Resource main](https://github.com/MeChen618/AstaPS-Resource/tree/6cfecfb55f889f8e3b7156adb12809608db4f74b) | commit `6cfecfb55f889f8e3b7156adb12809608db4f74b`，根 tree `3cf83488a94b47a018934e5f91c1bf7e93357aae`；README 称在 7.1 基线上补充 7.2 两角色及依赖，非目标 revision 证明。根许可证 MIT；游戏数据权利与目标身份另核。 | 允许只读对照；不直接投影 |
| [AnimeGameData 固定候选](https://github.com/DimbreathBot/AnimeGameData/tree/d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1) | commit `d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1`，根 tree `5b334e711319726c504da95dbfe6ebb26baaa7d3`；候选标签与本机内置 revision 不同，自然选择仍未知。已审根树无独立 LICENSE，数据权利未确认。[原来源审计](CLIENT_72_D02_RESOURCE_SOURCES.md) | 既有候选，只读对照 |
| [AstaPS 服务端](https://github.com/MeChen618/AstaPS/tree/a49f51aa6f2787ffbc25cee46801b70dee860d23) | commit `a49f51aa6f2787ffbc25cee46801b70dee860d23`；根 LICENSE 为 GPL-3.0。服务端源码和注释只能证明其实现意图，不能证明目标客户端语义。 | Java 消费参考 |
| [LunaGC_7.2.0](https://github.com/capybr2222/LunaGC_7.2.0/tree/acc7a0b48f44f3d51666c91f1125e8e1ee923ca4) | commit `acc7a0b48f44f3d51666c91f1125e8e1ee923ca4`；根树无独立 LICENSE。既有 LoginReq 冲突及[UP-72 来源清单](CLIENT_72_UPSTREAM_SOURCES_20261114.md)保持，不整包替换。 | Java 消费及冲突参考 |
| [excel-hk4e](https://github.com/shizukayuki/excel-hk4e/tree/1c5ea225b3a13a1ead61538b3d6c525e6b35a277) | commit `1c5ea225b3a13a1ead61538b3d6c525e6b35a277`，tree `4d809651ad590d57acd34b0a44afc5f347efddeb`，MIT。README API 返回 404；[Go 包介绍](https://pkg.go.dev/github.com/shizukayuki/excel-hk4e)提供数据模型线索，仓库默认分支仅 `master`、公开 Fork 0、全部状态 Issue/PR 均 0。固定 `reliquary.go` blob `e79aa955f40849eb0023123dd2f99420fec1005e`，SHA-256 `14fdad17052153dcbb9802c51aa3a3dbdff0ec018b5d7b2ed6a4e776bc50e59b`。 | 通用具名 JSON 消费参考，无目标桥 |
| [mavuika-rs](https://git.xeondev.com/mavuika-rs/mavuika-rs/src/commit/67833c157cdfb57eb0854e9b2781d5d89014204b) | 公开 `master@67833c157cdfb57eb0854e9b2781d5d89014204b`，tree `09ee5cd070a34a973205457f8019047a7f9bbaa2`，AGPL-3.0。README 明示 `CNCBWin5.2.51`；仅一个分支。已在仓库外 clone、只读查看必要源码，未执行。 | 旧版方法参考，不能作为 7.2 reader 或 ctor 输入 |
| [genshin-il2cpp-re 7.2](https://github.com/portra400nc/genshin-il2cpp-re/tree/9ba116e5cfed3edca08a3de300480674d315e921) | `7.2@9ba116e5cfed3edca08a3de300480674d315e921`，tree `76062c89f164923d7760ea9a12016f32076d1468`，MIT；README blob `b9df4dd932e678e538793fa29329ff1b7f853a04`、SHA-256 `506748b8d30e8007cbcc5c6df7f4dbe1d2827ff7c6fff2b93d86ec76425cfa7e`。其 EXE/global/startup 三 hash 均不同于[本机国服固定输入](CLIENT_72_EXTRACTION.md)，且 README 明示构建间常量和偏移变化。 | 非目标 profile，不提供本机准确边界 |
| [Hk4e-SDK](https://github.com/CokeSR/Hk4e-SDK/tree/e00979460f5990e81a45343a2f751205c191c917) | `main@e00979460f5990e81a45343a2f751205c191c917`，tree `c271d7a32c44321f9997c728547142e8a4beabfb`，MIT；README blob `24b16c0af3cdb44aa3d570ba844941013d791070`、SHA-256 `181d709e6b5929a11dbbe36c8cf1162d081d74f4bb2ce3656a7d910ea6ecf101`。介绍为服务端 SDK，密码支持说明为 3.2–3.4，未给目标自然采用链。 | 不适用于 SDK 准确缺口 |

AstaPS-Resource 的 Skill 表 blob `de622160959a3a7842022a9b002e1a93cb306eb6`，SHA-256 `5258ed90da6372ea9da572748252775deb4089e908bb0a96c9067eced28eb6ec`；Depot blob `d20132d4da823cf0445a616905a6251cdd30ecd1`，SHA-256 `7e91c34c75387a6926c13710caa1d6829271453224743b2f28cbbc0ebb35acdd`。AnimeGameData 两表分别为 blob `f58ac30e0b62ee342ea11fc76d4e2b0d0613b24c` / `f7770f01224583537156de91e0ec94ee9ff5115d`，SHA-256 `d799dcba02f383dae99fe32b3393408773f67001fe9622801366a524732102d7` / `c149f12cce4df46aa9af15701a107b42a45feb7716c7f13448ccb6d7b90258b8`。比较前实际核对两个 Skill 原字节 SHA-256 及 Git blob、投影输入 hash；Depot 的 Asta 原字节与其既有已验缓存 hash 一致。

## Skill / energy 行级对照

| 目标集合 | AstaPS-Resource 原始 JSON | AnimeGameData 对照与边界 |
| --- | --- | --- |
| 367 个新正 Skill ID | 全部 ID 在候选表唯一命中；120 行显式有 `costElemType`，247 行该键缺席。 | 120 个具名元素值相同，247 行两边均缺该键；缺席不能称 `None` 或物理零。 |
| 120 个新正 energy ID | 全部唯一命中，120 行均显式有具名 `costElemType`。 | 120 个具名值相同；来源独立性与目标版本未证。 |
| 121 个 energy ID | 全部唯一命中；121 行显式有具名元素。`costElemVal` 119 行显式非零，2 行缺席；字面 `specialEnergyMin`、`specialEnergyMax` 及 `LNEGGCDIDCP`、`GHCNBMAPHML` 均 121 行缺席。 | 元素 121 行相同、119 个显式 cost 相同；另 2 行 cost 缺席。相同数据不构成独立 oracle。 |
| 121 个 Depot | 按唯一 ID 对齐后，121 行候选 `energySkill` 均与既验片段相同。 | 仅为引用对照，未验证 Java 正向绑定或自然采用。 |

另逐键检查，不将特殊能量的旧名误报为缺席：AstaPS-Resource 的 `specialEnergyCostMax` / `specialEnergyCostStart` 在 11065 行分别显式为 200 / 100，在 11145 行显式为 100 / 50；其余 119 行这两键缺席。这两行同时是 `costElemVal` 缺席的行。Anime 固定候选的这两个**旧具名键**在 121 行均缺席，不据此判断它是否有其他混淆字段、也不推零。`10019` 候选为 `Ice` / `costElemVal=80`，Depot 201 的 `energySkill=10019`；既验旧控制 `10068` 候选为 `Wind` / `costElemVal=60`。另一个公开新角色对照为 Depot 13601→11364、Skill 11364 的 `Electric` / `costElemVal=60`；11364 不在本次 121 保存 cohort 中。

固定 [AstaPS AvatarSkillData](https://github.com/MeChen618/AstaPS/blob/a49f51aa6f2787ffbc25cee46801b70dee860d23/src/main/java/emu/grasscutter/data/excels/avatar/AvatarSkillData.java) blob `63628dfff12a92b6165ab0cee4ba4e88a01309ec`、SHA-256 `55c66a4313ddc2fd5cc363d1f52e4be43b5e8c4cd286d601c2f3ca32286e92f6` 声明把上述旧名作为 Gson 备选，并以注释描述条上限和释放需求；这是 Java 候选的声明，**不是目标字段映射**。该仓及 LunaGC72 的 `Avatar.recalcStats` 均从 `specialEnergyMin` 设置最大特殊能量，以其一半设开始值；当前 checkout 则另读 `specialEnergyMax`。这个差异需真实目标消费者与非零样本决定，不能整段移植或用一半规则代替字段合同。[Asta Avatar](https://github.com/MeChen618/AstaPS/blob/a49f51aa6f2787ffbc25cee46801b70dee860d23/src/main/java/emu/grasscutter/game/avatar/Avatar.java) blob `11ec5e6570aea70c95fbf67e0d17b923d356343e` / SHA-256 `51994c65afd1328902ba6ecaf70c7baf2b6fe67ab495c7e0185727a4ee87ac98`；[LunaGC72 Avatar](https://github.com/capybr2222/LunaGC_7.2.0/blob/acc7a0b48f44f3d51666c91f1125e8e1ee923ca4/src/main/java/emu/grasscutter/game/avatar/Avatar.java) blob `2d0c01f0d5b09f2599df572a4c3d93e3f365f08a` / SHA-256 `b02c80e5d15b5441dddf215a128ac2af62061559e53f51d5bdfae728343c3e2c`。

## Fork、分支和讨论范围

2026-11-15 UTC 的公开 API 返回 AstaPS-Resource **11** 个 Fork、全部状态 21 条 Issue 记录（含 20 个 PR）；此前账本有 12 个 Fork，缺少的 `zornicza/AstaPS-Resource` 本次仓库 API 返回 404，原因未知。11 个当前 Fork 的默认 README 均已读取，4 个 README 含 `7.2`，逐一枚举其分支；其余 7 个默认 README 未命中 Skill/energy/7.2/revision 筛词，不宣称其非默认分支已审。

入选 Fork 中 `main` 均为 `6cfecfb55f889f8e3b7156adb12809608db4f74b`；`mechen/resources-7.2-runtime@9f1ae21ea6b4610fca4fde8484ae4316cb3860b1` 与 main 同树。`tangyanliang1` 的 `develop@c0213668889ed05cbeb86660f424c3daf18bfacd` tree `51b6e16211a5f2dc8e8520be7f50187ac212ee4d`，README 声明 7.1；其 Skill / Depot blob 分别 `4685663a23e02cc0f3ff5c17b561219b791aa5d8` / `caf32c71bb0646fef87327a9a3c218dd90dacb88`，SHA-256 分别 `895bc20a0fe16caf02c2fbf8d5f3aacb6c1cca4e2af3f0eed791012505c81259` / `bf874bab09806391dcfb30a9cbe06c94ffa582269d1bd84d45ececac2e426f03`。`update-quest-excel@9fe14a67667e3bca4db026d9c98e452342e2b989` tree `3e5a05b61b2ea4139ee4989a10bb72f05cafe091` 的两表为相同 blob；旧树与 main 的 121 个 energy Skill **整行相等**，不计独立新来源。

[PR #21](https://github.com/MeChen618/AstaPS-Resource/pull/21) 的 268 个文件按 100/100/68 三页读取，修改两张目标表；其描述称保留原有行，且未完成真实客户端完整玩法验证。按 Skill/energy/cost/解析关键词检查全部 Issue 标题与正文，仅 #21 和 #11 命中；两者 Issue 评论及 #21 的 PR 评论/review 已取，未提供目标字段桥。未逐读其他 19 条讨论的评论。既有 patch 对照只引用[定向审计](CLIENT_72_D02_UPSTREAM_REVIEW.md)，不把 WinHTTP 差异作为自然 SDK 采用证明。

## 其他准确缺口和检索出口

本轮均以目标固定 7.2 输入和已验 source-only 结果为前置；输出是来源线索或准确缺输入，停止条件是未出现可连到对应目标对象/行的独立证据，不因部分线索提升父项。

| 任务 ID | 准确目标与本次发现 | 下一份最小合同 / 停止点 |
| --- | --- | --- |
| `D02-MATERIAL-REUSE-20261116` | 需分类 literal 的真实数值、目标 row/consumer、独立非零 oracle。固定 Go `material.go` blob `53c45ac7d515776fdff6fd70ca531fce90c1cd28`、SHA-256 `85b23afc992b3c41d84c9377fa934eff9064c06607f282a832ec878a22551749` 只有 `MaterialType string` 等 JSON 模型；无目标 literal 值或目标消费者。 | 获得固定目标版本具名 consumer/实际行和独立非零对照后再审；旧字符串清单不当枚举值。 |
| `D02-RELIC-REUSE-20261116` | Go `Reliquary.Set()` 用 `SetId` 查套装，`Level()` 用 `RankLevel` 和等级查等级表；这是第三方 JSON 语义，不能连接本机目标候选以排除 rank/set 交换。 | 找固定目标具名 property/consumer，或同源真实输入与独立非零 oracle 排除交换。 |
| `D02-PROUD-BRIDGE-REUSE-20261116` | 通用 Go 模型存在 `ParamList` / `AddProps`，无 `26072.field127897` 或 `59500.field295065` 到同一共享 Pair consumer 的目标桥，也无实际 row/cache/key/same epoch。 | 新固定来源准确连接目标字段、同对象和独立值；不借 preview、Default 或重复同 SDK 页。 |
| `D02-SCENE-LIFECYCLE-REUSE-20261116` | 未发现能连接 M1 producer→同次采用、M2 同 receiver 返回 R、M3 同 R 消费前 `+18` 与 writer 顺序、M4 同 R release/reacquire/reset 的目标输入。旧服务端 Scene 处理不能替代这些边。 | 取得这四边之一的允许独立自然来源，保留 World slot114 的准确 A 依赖停止。 |
| `D02-SDK-NATURAL-REUSE-20261116` | 所筛旧 SDK 服务端 README 无固定国服 7.2 自然 Init/env→route 采用→认证完整链；已验锁内 map 与服务端响应仍不证明实际采用。 | 固定真实自然链各对象/来源与完整校验；B 组合 loader 继续冻结。 |

`mavuika-rs` 的 `mavuika-data` 有 5.2 Depot、varint/float/string reader 与存在标志 derive；该 tree 没有 AvatarSkill 行类型，缺位时 derive 直接 `Default`。它既不指定本机 7.2 字段布局，也不提供目标 typed 初态或 TLS，不能用其默认策略造值。方法文件 `from_binary.rs` blob `83a62f3f3e3bb3dff7c836dd5302d6ac6a440078` / SHA-256 `1eeec4ee59f99610ed88a912887b77aa0c72b2ed0a125086668a809d40215888`；derive blob `e42c121db105eb217652a278a7002ae0d25fa933` / SHA-256 `6e168e8a0a2faed3e601b64d6acb81a5a6ea9dc4728f8fce329d619b77c52612`。

## 实际检索、错误与未覆盖

2026-11-15T17:29–17:40Z 执行 GitHub REST 仓库搜索，统一附 `is:public fork:true in:name,description,readme`，每页 100，分别取 relevance 和 updated；以下可完成项两排序均为第 1 页、`incomplete=false`。仓库/README 的零命中**不是源码不存在**：

| 准确查询 | 两排序各自 total / 已取页 |
| --- | --- |
| `AstaPS-Resource AvatarSkill`；`AvatarSkillExcelConfigData 7.2`；`AvatarSkillDepot energySkill`；`costElemVal specialEnergy` | 各 0 / 1 |
| `Genshin ExcelBin parser 7.2`；`原神 7.2 技能 能量 资源解析`；`specialEnergyCostMax specialEnergyCostStart`；`specialEnergyMin specialEnergyMax`；`Mavuika Fighting Spirit AvatarSkill` | 各 0 / 1 |
| `hk4e resource parser skill` | 各 9 / 1；9 个默认 README 已看，主要为 7.1 LunaGC 谱系、语音映射和收藏清单，未出现可用目标 Skill 合同 |
| `MaterialExcelConfigData materialType 7.2`；`ReliquaryExcelConfigData rankLevel setId 7.2` | 各 0 / 1 |
| `ProudSkillExcelConfigData paramList addProps 7.2`；`ProudSkill Float Pair parser` | 各 0 / 1 |
| `SceneInitFinish 7.2 scene entity team`；`hk4e SDK Init environment route config authentication 7.2`；`Genshin comboLogin route env init` | 各 0 / 1 |
| `MATERIAL_ Genshin resource parser`；`scene reader pool release reset` | 分别 47,279 / 37,275，仅各取父查询第 1 页；过宽且超 1,000，未拆分全量，不据其第一页计完整检索或结论 |

另用搜索引擎查精确字段名、目标版本、GitLab/Gitee/Forgejo 和 SDK/Scene 自然问题，并对上述明确命中的候选先看介绍再核相关文件；搜索引擎不可分页穷尽。额外严格字段查询仅找到旧版字符串和上表 Go 模型，未提供目标桥。原始搜索引擎结果未另保存为文件，以本页查询词和来源 URL 记录；GitHub API 原始页及元数据在忽略目录。GitHub 全局代码搜索未执行；除明确固定的文件和 #21 页面，不宣称所有源码、所有 Fork 非默认分支、其他 PR 评论或其他托管平台已检索完。既有两个 Codeberg restricted 地址未换入口重试；当前 zornicza 的 404 与未搜索分开记。GitHub 其余本批列出的 API 请求成功，不把未覆盖或 404 记成无项目。

首版本次比较脚本误将单行列表当作行对象，错误显示 121 行具名字段全缺；错误输出保留，修正后 v2 开始才可用。随后补查旧特殊能量键、人工查看 10019/10068/11065/11145 及 11364，最终以 v4 和独立候选表为准。初次 Asta contents 请求为取元数据也返回了本机已缓存的 Depot 原字节，是一次重复下载；两者 SHA-256 相同，不当新增独立来源。

## 产物、验收与可执行后继

忽略目录 `local/migration72/d02-targeted-search-20261116/` 保留 API 原页、源码与比较记录。三份私有候选表只保存候选原字段的 presence/value 和固定来源，**不是经批准的目标资源投影**：`skill367-candidates.private.json` SHA-256 `43622f5a5001eff46aede71b7fc32c3d3669340b051d8a83d4e435af56767376`；`energy120-candidates.private.json` `0708374025c3d1a7e4f694e099679e8ba4aa4575f00d15db70b212d25ea323a3`；`energy121-candidates.private.json` `06f4b91c560fff2f1d3a1e672af1be85c9beea02882ad2c08cbdc01b29a96763`。API 搜索 manifest `search-manifest.json` SHA-256 `2baa1efcbf4a440d372091ee92c4a34a67b467cec00ad9ac38cd20151b8456e4`，`secondary-manifest.json` `2983721d84d504cea86e4a92f1eaef94d6c865a7f83423cca0f5127e26eb6570`；固定文件 manifest `fixed-manifest.json` `1e91ac933835c1f540c72ef48148aac7371b0df785867b421a32ae3323132ea2`，服务端 manifest `servers-manifest.json` `8a86d5f5ce9ac1cd403bb878a3991c3e3b0d100fb73ddcd70faefb6c28119a4e`，PR manifest `pr-manifest.json` `8247aa527c5dfe07d2f45b8a8c2b86cad7586a09d1eeeb899a4b9651351dee5d`。

可直接复用的是已验 Depot 引用、固定表及上述只读候选比较；现阶段没有可直接合入的目标字段实现。后继可由主线以固定目标输入审定单个 `id/element` 的独立 reader/codec 合同，再以 10019 等显式候选作对照；候选相等只作参考，不能代替目标来源和独立 oracle。三能量字段分别需要允许的具名 target 消费者、同源真实值和独立非零对照；缺席、零、非零与各旧名分别记账。缺同一 PE 的准确完整 ctor 边界时停止该窗口，不猜长度或按 RET 补边界。

本报告记录公开检索时点的出口。同期独立本地[元素后缀pilot](CLIENT_72_D02_SKILL_ELEMENT_HOST_PILOT_20261116.md)及[logicalID绑定](CLIENT_72_D02_SKILL_SUFFIX_SOURCE_CONTRACT_20261116.md#logicalid-与-tablekey-的既验绑定)已另行验收，后继不再要求额外raw-id或reader窗；这些结果不来自本报告的候选JSON，不改变上述来源独立性或成本字段缺口。

本次未运行上游代码、客户端、native/parser/getter、JVM、数据库、构建或部署，未改运行代码/资源/Proto，也未使用 computer-use。A/B、preview 及其准确依赖继续冻结；无新安全拒绝。共享 Project/Harness/交接和 Git 由指定唯一集成执行者串行管理。
