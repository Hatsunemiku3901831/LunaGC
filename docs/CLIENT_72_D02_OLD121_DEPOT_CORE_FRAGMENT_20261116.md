# 旧号 121 Depot 三键片段限定投影

`R3-OLD121-DEPOT-CORE-FRAGMENT-SCOPE` 与 `R3-OLD121-DEPOT-CORE-FRAGMENT-PROJECTION` 已完成限定验收。一次纯 host 投影只复用已验逻辑值和来源，形成 121 个 `fragmentOnly` 的 `{id, skills, energySkill}` 片段；presence 均保持 unknown，不把零推为原 token 缺席。保存当前 Depot 不证明运行时当前采用、默认选择或完整恢复。

入口校验 6 个固定输入；121 个保存 Avatar–当前 Depot pair 对应 121 个不同 Depot，与既有 171 条 whole 记录及 cursor 26024 对齐。363 个字段引用 726 条既有 getter 证据；484 个普通 skills slot 保留原序、重复及零，其中 248 正、236 零，另 121 个 energy 均为正。369 个不同正 Skill 引用只核既有 key/span，不产出 Skill 值。

既有 mapping 的 5 个控制中只有 704 与该 cohort 重叠，且同 key/source/三值一致；原出生范围真实 Java 控制是 501、701、704，不能称 5 个 mapping 控制均经该轮 Java 验证。**Java前的历史阻塞**曾为缺367个Skill片段及120个正向energy绑定；现已由[同源id/element批量](CLIENT_72_D02_SKILL_ELEMENT_HOST_BATCH_20261116.md)及[121 Depot真实ResourceLoader验证](CLIENT_72_D02_OLD121_SKILL_DEPOT_JAVA_20261116.md)解除。原任务`R3-OLD121-DEPOT-CORE-FRAGMENT-JAVA`已完成，限369 Skill两键→121 Depot三键及实际正向引用/element；没有空Skill、缺引用None fallback或假cost。本轮后继121普通cost与START来源/值及[真实Java加载](CLIENT_72_D02_OLD121_SKILL_ENERGY_JAVA_20261116.md)也已闭；特殊MAX cap、完整Skill/Player恢复仍未闭。

规格 `local/migration72/d02-resume-20261116/old121-depot-core-fragment-scope/spec.private.json` SHA-256 `2788ce4874449fb042eaaf8c1d31a499ad86b89ef32829e0f542bcc6a0205fc8`；投影 `local/migration72/d02-resume-20261116/old121-depot-core-fragment/projection.private.json` 为 266719B，SHA-256 `24b008df69e853d1739fe408634c77698340c8b69294f8344718aa82f108d654`。新 `tools/client72_d02_old121_depot_core_fragment.py` SHA-256 `a7e1d765049f2c1ead87f02d31bc927972748f7f548eeab2de787e49f10ada03`。

原host投影准备时一次结构探查误展开 `mapping.selected`，展示了部分超出预定范围的既有 Skill 字段；命令和工具原输出保留在执行者记录中。最终投影代码仅使用 `table_rows` 的 key/source 与引用存在性，不使用 Skill.values 作为 oracle 或新值；不能把该轮表述为严格无额外读取。该host批次未重跑既有Depot或phase6、未新增native/PE/model/ctor/JVM/DB/client/test；后继独立真实Java单项的实际范围和原件见上方链接，不追改此原投影回执。

该局部成果不提升父项、资源 ready、实际旧号恢复或客户端验收；A/B 和 preview 冻结保持，D-02 / F-05 / D02-R6 仍为 0/6。
