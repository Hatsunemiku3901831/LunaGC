# E-01d1 / R3-OLD-AVATAR-COVERAGE：所选旧号角色只读键对照

本卡只对照已选旧号固定依赖投影与既有目标报告的 **已解码表键及其记录的来源跨度**，不读取对象 raw bytes 以新增字段，不猜 alias、不把候选资源当目标语义。旧号与目标键的逐角色明细只留 `local/migration72/d02-resume-20261110b/c-old-avatar/`；本页仅提供脱敏汇总。`20261110b` 是原批次名，不是新的采样日期。

## 实际对照结果

| 所选保存内容 | 数量 | 对照结果与限制 |
| --- | ---: | --- |
| Avatar | 121 | 121 个 ID 均有目标 Avatar 键和记录的来源跨度；仅 1 个 ID 在 bridge 已批准投影片段中，片段不是完整行。 |
| 保存的当前 skillDepotId | 121 个不同键 | 121 均有目标 Depot 键和跨度；仅 1 个有 bridge 片段。没有用默认 501/701 代替保存值。 |
| 保存的正 skillLevelMap 引用 | 409 次、389 个不同 ID | 409 次均有目标 Skill 键和跨度；4 次、4 个不同 ID 有 bridge 片段。技能等级为 10 的 406 次、等级 1 的 3 次；存在键并不证明技能组、成本、能力或升级语义。 |
| 保存的正 talentIdList | 762 次、762 个不同 ID | 作为条件引用逐项保留；本卡没有目标 Talent 全表键和 OpenConfig 闭包，存在性和语义未知。 |
| 保存的正 proudSkillList | 410 次、410 个不同 ID | 作为保存值逐项保留；目标 ProudSkill 行、组/等级和门槛尚未闭合，不能将保存列表直接视为最终运行列表。 |
| 等级 / 突破 | 121 个均 level 100 / promoteLevel 6 | 固定 Promote 报告有完整 level 1–100 AvatarCurve 输入；group 12 / phase 0 仅为出生必要用途，不覆盖这些角色的实际 group / phase 6。 |
| 活动常规队伍 | 4 个角色 | 4 个都能关联到同一所选投影内的 Avatar，Avatar/当前 Depot/12 次保存 Skill 引用键均存在；24 次 talent、16 次 proud 均仍是条件引用；此四人均无本 bridge 中的 Avatar、当前 Depot 或保存 Skill 投影片段。 |

目标报告全表分别为 Avatar 169、Depot 171、Skill 1176 行，row spans 连续至各自 EOF，键唯一。这里确认的是报告内的 key、记录的 span 与 SHA 及片段的 span 一致；**没有重新计算原 payload 对应区间的 SHA**，也没有运行 native 或证明自然活动 revision。正引用 0 条缺键不等于资源数据就绪；每条源 span、各角色条件和缺口见私有第三报告。保存列表、技能和队伍成员并不全部是无条件入场 P0。

## 消费条件与准确后继

- Java `AvatarData` 按保存 level 查询曲线；`Avatar.recalcStatProperties` 用实际 `avatarPromoteId` 与保存 phase 查询 Promote。level 100 曲线已在固定 Promote 报告中有完整用途输入，**每个实际角色的目标 promote group、phase 6 行及其必要字段/引用仍须各自证实**；不能把 level 100 的曲线存在当成对应成长计算全闭合。
- `Avatar.setSkillDepotData` 对当前 depot 的 skills/energy 补不存在的技能等级，并清空、按 inherent group 与 `needAvatarPromoteLevel <= 6` 及 quest groups 重算 proud；`recalcStats` 再按这些组及实际 ProudSkill 条目增加属性与 OpenConfig。以上是 Java 条件路径，不是目标字段已获批准。保存的 410 条仅提供核对对象，不能预先认作 410 条都被重算采用，也不能归零或以 Java 空规范化证明目标缺省。
- `recalcConstellations` 查询已解锁 talent 及其 OpenConfig；`skillLevelMap` 保存键的 Skill 行存在，也不证明其与该 depot 的成员关系或 `proudSkillGroupId`。技能升级按组和等级查 ProudSkill、检查 breakLevel 与材料，属条件路径，不能把全部升级材料升级为入场 P0。
- 下一项可独立执行的是按第三报告中的 **四名活动角色的真实 Avatar ID、保存 depot ID、level 100 / phase 6、12 条 skill ID+level、24 条 talent ID、16 条保存 proud ID 和各自已有 source span** 建立目标资源族需求。先为实际 Avatar 的 `avatarPromoteId` 与 phase 6 确立批准的同源身份/行与必要引用；再按已批准的 Depot talents 职责核对相应 Talent/OpenConfig。缺乏批准目标字段或目标表时保留未知，不用键命中推导字段。
- 若交 Sol max 审 proud 门槛：输入为同一固定 mapping 报告及其四个活动 depot 的具体 source span、私有第三报告的保存 phase 6 与实际 proud/skill 条件引用、现有 depot inherent `+18` / special `+10` typed-pair 证据及 Java `Avatar.java:279–302, 830–869, 1043–1063` 消费条件；需要明确目标 group、level、promote 门槛、quest 分支及 ProudSkill 行/引用的独立 target producer/consumer 和来源。不能凭保存值、候选 JSON、算术外观或旧版本命名认定这些字段，更不能从本卡 raw 行 bytes 自行解码。

## 固定输入及复现

driver 每次在写出前核完整 SHA-256；变更即拒绝。依赖投影 `3ef64138cd0525442df1625e823bb41a66089c3ea114deb99c7da24f201ec0b7`、manifest `9fffdc25ddb24376552e3163db9ed6de441548d843fbda82649996fb59d4910b`、验收 `80a39b3943d0e4658cdfae3bc561cafebd0ddcbbc755aeefc7d0df2d4f447667`。manifest 内 Player / Avatar / Item 原导出亦核原字节：依次 `95774a40f97ae3daacbf7a7b8602185889e8a3ca7ac3195fad8d5805f2147a43`、`273fa466d17e504d2eef2abb00b175afe3b4aa50f339d622b4614e828c3afc5a`、`563fa1ed07c4fc92722f1b723259ac7a0e79ddc12a718b300e4557e7828712dd`。导出来源及真实关联由固定快照验收材料提供，本卡不再连接数据库。

六份既有输入依次为 talents projection `ae86a458b6a9af5e139e624ede46f9867eba2e31d1aef9d27fb7a4749ad78f9f`、mapping `d990c2c469e5a9d3ee40351e4dfe5223ae49afaa855abe5d912427139c66307e`、promote `546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1`、equipment `50aaea9406152e1ec9e0c423ec532618b8fe635e3d0cf758d24fccc6403eb5bc`、affix `9007d08f91848c9aa2c63d8690b3f5f9c6e8cc7165a3fa07a0dc78b8e55a53c2`、gadget `51c63295b7756b6083157ed14d5e39134fe95ba29d78ea097046965239134880`；精确路径见 [driver](../tools/client72_d02_old_avatar_coverage.py) 的固定表及[旧档依赖](CLIENT_72_D02_OLD_SAVE_DEPENDENCIES_20261110.md)。三张完整表源于 mapping，本卡不从其他输入扩大字段语义。

```powershell
python tools/client72_d02_old_avatar_coverage.py --output local/migration72/d02-resume-20261110b/c-old-avatar/<新文件名>.json
python -m unittest tools.test_client72_d02_old_avatar_coverage -v
```

输出只能写私有目录且不覆盖原件。三个独立 CLI 进程的 first/second/third 均 227773 字节、SHA `ca9b24ed6e47cca54bc069a1c4f3b8cf19b5900117860efe755ade49293246ea`；独立 Python 从原投影及 mapping 单独逐项比较 121 角色、实际队伍、当前 depot、等级、所有正条件引用、Skill ID/等级及报告 span，结果 `independent-audit.private.json` SHA `c2ccbb7dc552d68599aeaf245e7cfcfd3f9f3637f4eca376712e0c5fa9af787a`。5 个 Python 测试通过，覆盖非零、64 位 UID、保存引用/活动队伍缺失、源跨度及片段漂移、哈希漂移拒绝；它们不是客户端验收。

本卡只读离线输入，没有 Java/Mongo/服务/客户端运行或部署；完整旧号恢复、完整 Avatar/Item/Scene 及 D-02 六项客户端验收仍未通过。共享任务索引、提交及其他代理工作由主线按授权统一处理。

主线独立运行5项测试与第四CLI，报告227773B/SHA `ca9b24ed6e47cca54bc069a1c4f3b8cf19b5900117860efe755ade49293246ea` 与三fresh一致。仅接受已列键/记录span和保存条件需求，未重算原payload、未覆盖全部121的字段/phase6/恢复。
