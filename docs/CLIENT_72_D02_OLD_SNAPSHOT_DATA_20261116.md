# D02-OLD-SNAPSHOT-DATA：旧号离线资源准备

状态：**有限离线数据与引用检查完成；完整旧号恢复未验，ready=false。** 本项只为选定快照准备候选资源和可追溯清单，不改变原 BSON、数据库、资源子模块或 M0 实例。首个可供纯 `ResourceLoader` 诊断的稳定包是 `local/instances/d02-old-restore/resources/revision-5/`；修正缺行报告后的复现包为同目录 `revision-6/`，两者 manifest 字节一致。不要把候选资产身份、离线引用检查或单次 loader 成功当作客户端或完整 Player 恢复验收。

## 有限运行模式补充：revision-8

新增hash固定的Account BSON输入以及从已验M0输出逐字节核对复制的Scene3、Level_BigWorld、显式optional-global策略三文件；29表/287输出。manifest SHA `941c5f30e1d66d0208f8a4618bca30e439b82527130b06a1168aa9cf2574e0b9`，脚本SHA `ccb9c71fd7cca191adc0d4e00e04bb052bc705816fbf7cddea37517afadc044a`。两项离线测试14.969秒通过；原10条业务数值策略与全局能力省略政策分列，不称target能力恢复。实际生产loader/只准复制档的Storage前门及World/保存生命周期见[旧号有限模式](CLIENT_72_D02_OLD_SNAPSHOT_MODE_20261116.md)。revision7保持不变，离线ready仍false。

## 当前集成补充：revision-7

真实全原字段恢复测试发现21表包缺羁绊/名片派生资源。唯一集成者按同一固定tree补取并核对FetterInfo、Fetters、FetterStory、PhotographExpression、PhotographPosename、FetterCharacterCard、Reward七个具名文件，脚本`FETCH`固定每个Git blob/SHA；私有下载回执留`evidence/explicit-fetter-reward-fetch-receipt.json`。未启用lazy-fetch，旧revision不覆盖。仅选择121角色相关行及名片所需Reward，不扩任务/奖励业务恢复。

`revision-7` manifest SHA `d7be51d25c5e4148eb2bf77d6dc5c2c8b55a33fc17df4f0ed3e96ba86a68f3a5`，28表/284输出，原10条server-provisional不变；当前脚本SHA `b8cf289ef882a2e9128907890730d425b6daae2ecfe77767b6867dbd0272c263`。更新后两项离线测试13.524秒通过。实际Java资源准入/缺项零写、121 AvatarStorage/10727活动Inventory保存冷载已完成，见[真实Java验收与首次失败](CLIENT_72_D02_OLD_SNAPSHOT_JAVA_20261116.md)；离线manifest仍ready=false，完整Player业务Manager/客户端均未升级。下文为Terra交付revision5/6时的离线基线，涉及旧脚本SHA和未运行状态按该时点理解。

## 输入和来源

固定选中快照及五类 target 片段的相对路径、SHA-256，以 [规格](CLIENT_72_D02_M0_SCOPE_20261116.md) 的 D02-OLD-RESOURCE-BUNDLE 表和 [脚本](../tools/migration72/prepare_d02_old_snapshot_resources.py) `PINS` 为准；脚本实际核了 selected-manifest、selected-dependencies、player/avatars/items 三个 BSON、active-item-saved-refs、全部五个 target 片段、八个本地候选文件。路径和 hash 不允许静默漂移。脚本还固定了本次新增的保存武器字段投影及三个具名配置下载回执。

候选固定来自 AstaPS-Resource commit `6cfecfb55f889f8e3b7156adb12809608db4f74b`、root tree `3cf83488a94b47a018934e5f91c1bf7e93357aae`；仅消费固定提交的具名表和配置，逐文件核 tree blob 与 SHA-256。仓外 clone 的部分 blob 缺失，经主线授权，先明确固定 tree 条目，再仅下载所需具名文件：AvatarTalent、ReliquaryLevel/Set、EquipAffix、121 个角色 ConfigAvatar、按角色和 Player 各元素筛选的 AvatarTalent 配置、玩家 AbilityGroup、五类武器与圣遗物 Talent 配置；独立 evidence 下保留逐文件 HTTP 状态、Git blob 和 SHA 回执。没有启用 Git 自动 lazy-fetch、更新上游 HEAD、运行上游代码或整树替换。所有这些数据仍为 `upstream-candidate`；源根 MIT 许可与版权原文已随包保留，游戏数据及第三方权利另核。

按主线新增授权，对既固定 `items-0.bson` 只读取得 1,663 个活动武器的 `affixes` 和 `refinement`，逐条核原 ObjectId、owner 和旧八字段、完整 12,254 行及 EOF；两个字段均为有值。输出 `evidence/weapon-saved-affix-refinement.private.json` SHA `a2f9637b36b26ba2f76bb92ef55b2afffbd34883afa076da97b078df1cdc9bed`。复用固定 BSON decoder SHA `8288e6507ff8f0b5c3bf7c37dc2c19cf7101415c5767507c02217b7528df68b0`，一次性只读投影脚本保存在同一 ignored evidence；未重导出或访问原 DB。该投影含私有身份，不提交。

## 输出和离线检查

`revision-5` 与 `revision-6` 的 `D02-OLD-RESOURCE-MANIFEST.json` SHA 均为 `1ada44d0ad16e69de52ea182a338c3410aa4c34e140dd78b7ec8cc0b4b7e661b`。其 277 个输出逐项写 SHA，含 21 种 Excel 资源、具名配置和许可；每条候选记录真实 blob、SHA 和来源分类。manifest 保留每个原角色和每个原 Item 的文档序号、ObjectId、owner、ID、活动状态及装备归属，原始快照和非活动行未被裁剪或修改。日志、原始内容和私有标识只在 ignored 目录。

- 原快照为 121 Avatar、12,254 Item，其中 10,727 活动、1,527 非活动；活动候选分类为 6,367 Material、1,663 Weapon、358 Reliquary、2,331 Furniture 和 8 TPS。所有活动行在五族候选中恰有一个匹配。
- 471 件正装备为 121 武器和 350 圣遗物；装备归属均为已选 Avatar，按 `ItemData.onLoad` 规则的槽位无重复，每个角色一把武器。此为投影和候选行关系检查，仍需真实绑定及防止 postLoad 补装的运行断言。
- Avatar 保存等级/突破均为 `100/6`；曲线包含连续 `1..100`，并检查所有 overlay 指定的曲线键。武器保存等级/突破为 `1/0`、`90/6`，同时选择初始化需要的等级 1/突破 0；圣遗物实际所需 `(rank,level)` 为 `(3,1)`、`(4,1)`、`(5,1)`、`(5,21)`。候选突破和曲线检查按实际组合，不将旧装备降级。
- 包含 121 Avatar、121 当前 Depot、707 所需 Skill、762 保存 Talent、793 保存及派生 ProudSkill、239 武器、183 圣遗物、39 套装、342 装备词条、28 保存主词条、22 保存副词条、12 TPS 配件及 247 武器/TPS Gadget 候选行。进一步检查技能等级、保存 proud/talent、角色配置、depot group、装备/圣遗物 openConfig、主副词条、TPS 配件、武器曲线和装备突破引用；逐 ID、行、等级及缺口详见私有 manifest。两个 TPS 配件字段的 12 个候选 ID 集合相等，原字段仍分别留在固定投影。
- `target-verified` 只覆盖固定片段的 Avatar base/growth、critical float32、当前 Depot 的 id/skills/energySkill、已有 Skill id/element/cost/START、AvatarPromote phase6 的 typed javaFields。其余字段为具名候选或下列服务器临时值；目标客户端 asset ID、自然 revision 与原始缺失字段没有因此得到证明。

## 明示的服务器临时策略

按用户允许的业务临时值，仅对实际保存引用使用以下策略；原存档不变，manifest `serverProvisional` 给出每个 key 和来源，不作目标原版恢复：

1. 保存武器精炼派生出候选缺失的七个 `EquipAffixData.affixId`：`1114124`、`1114164`、`1114194`、`1114204`、`1114214`、`1114294`、`1154154`。每个只用同组现有 0 级完整候选效果，输出行的 `affixId` 和 `level` 对应实际派生 lookup key；不改保存 affixes/refinement，也不造客户端资产名。
2. Avatar `10000076` 的保存 Skill `10765` 等级 10 派生 ProudSkill key `763910`。固定候选相同 `proudSkillGroupId=7639` 的 level 10 行实际 ID 是 `763310`；以其完整效果及已有 openConfig 作为该服务端 key 的临时行。并不声称两个原始 ID 在客户端同义。
3. Mavuika `11065` 和 Skirk `11145` 的服务端 `specialEnergyMin`（当前 Java 用作 cap）分别暂定 200 和 100；已有 target START 100 和 50 继续写 `specialEnergyMax`。原始 MAX 字段职责仍未恢复。

## 复现与验收边界

在当前 checkout 下，令 `$env:D02_RESOURCE_REPO` 指向已存在的固定 AstaPS-Resource clone；已下载、固定的证据须保留在 `local/instances/d02-old-restore/evidence/`。准备另一个全新 bundle 名称，脚本禁止覆盖已有 bundle，且只允许该旧号实例根：

```powershell
$env:D02_RESOURCE_REPO = '<固定 AstaPS-Resource clone>'
python tools/migration72/prepare_d02_old_snapshot_resources.py --source-repo $env:D02_RESOURCE_REPO --instance-root local/instances/d02-old-restore --bundle-name <新名称>
python -m unittest discover -s tools/migration72 -p test_prepare_d02_old_snapshot_resources.py -v
```

脚本 SHA `603c2db0e6a3116159acb220a7f4418cb08f20046e6d63335e80d5c2c62de1de`；测试 SHA `2900eda1df77788d44ee51e08f77db36f250d5c9781503bf83b8b2adf12cedb7`。实际两项测试通过：原完整计数、装备与策略；移除必需武器、等级100曲线、角色配置时准确报告缺口。首次负向测试暴露武器缺行时抛错，已调整为记录候选族及武器投影数量缺口；原 revision-5 不覆盖，修正后 revision-6 内容逐 manifest 一致。

纯资源阶段可由唯一集成者对稳定 `revision-5` 做真实 `ResourceLoader`/`onLoad` 与配置解析诊断；运行结果另记，不能通过这个离线报告推定。进入任何旧号 hydrate/save 前，须核真实 GameData 全部必要资源及引用、owner/GUID/装备逐一绑定；postLoad 不得补武器、清装备归属或增删 Item；重算策略差异单列，不改原始 HP/能量/装备讨好测试。保存后必须新建 Player 冷载并核 121 Avatar、10,727 活动物品，非活动 1,527 和原 18 集合证据不删除覆盖。全面 Manager 恢复、正常认证/会话、场景及客户端进入/重登不在本离线结果内。当前没有生成 ready 文件，也没有 JVM、Mongo、服务、客户端、构建或部署操作；本项未独立提交，交唯一集成者归档与串行提交。
