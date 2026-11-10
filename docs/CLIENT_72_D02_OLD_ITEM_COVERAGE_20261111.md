# E-01d1 / R3-OLD-ITEM-COVERAGE：所选旧号有界物品对照

## 计划和边界

1. 核固定依赖投影、私有快照 manifest、目标六表报告和既有候选来源；按 `DatabaseHelper` 已核查询条件 `deleted != true && count > 0` 区分活动与非活动，完整保留后者的输入投影供审计。
2. 列出每个保存 Item ID 的 count、deleted、level、promoteLevel、equipCharacter 分布及缺字段；目标仅对 Weapon 288 行已解码 `key` 求交，不解码新字段、不认定业务类型。
3. 分别核固定候选四张 Excel 的 `id` 是否存在，并保存所属候选表及重复行数；重复或跨族同 ID 为 ambiguous，缺失为 unknown。候选存在性不是目标语义、允许缺省、P0 升级或资源就绪。
4. 检查正例、缺项、输入漂移、重复与歧义；三个独立 CLI 结果字节比较。语义缺口交主线继续研究，不部署、不调用数据库或客户端。

## 固定输入与来源

实际投影 `selected-dependencies.private.json` SHA-256 `3ef64138cd0525442df1625e823bb41a66089c3ea114deb99c7da24f201ec0b7`；同一目录 `selected-manifest.private.json` SHA-256 `9fffdc25ddb24376552e3163db9ed6de441548d843fbda82649996fb59d4910b`。投影列出的原 player/avatar/item BSON SHA-256 分别为 `95774a40f97ae3daacbf7a7b8602185889e8a3ca7ac3195fad8d5805f2147a43`、`273fa466d17e504d2eef2abb00b175afe3b4aa50f339d622b4614e828c3afc5a`、`563fa1ed07c4fc92722f1b723259ac7a0e79ddc12a718b300e4557e7828712dd`；本轮直接消费并核固定投影及 manifest，未重新读取 BSON。来源、一致性和选定账号的证据沿用[快照记录](CLIENT_72_D02_OLD_ACCOUNT_SNAPSHOT_20261110B.md)及本机私有产物。

目标 `local/migration72/d02-active/main/avatar-natural-tables-third.json` SHA-256 `1e9817cbea6da6db5ee564d5c3f3a64436fa3319e6d9b8ccf5aef0014cf85a86`；仅 `tables[name=Weapon].rows[].key`，288 键，无重复。此报告是限定目标静态读取，不是目标字段语义或实机验收。

候选来源为固定 `MeChen618/AstaPS-Resource` commit `6cfecfb55f889f8e3b7156adb12809608db4f74b`、root `3cf83488a94b47a018934e5f91c1bf7e93357aae`、Excel tree `a4957e38123fed121a3fb0b27519c14644b39b29`；旧 manifest SHA-256 `70aec1cb722b71280393389216bc5bceaa7c9d59aa04e2345411b042a1c4a80d`。复用既有仓库外 no-checkout clone 中的 commit/tree 对象，禁止 lazy fetch，逐层核 commit、目录项和 blob。此 clone 的五个所需 blob 均缺失；按固定 commit/path 只补取下表五个正文（共 13,905,271 字节，单文件最大 9,021,596 字节），均逐一核 Git blob 和 SHA-256。没有新搜索或 clone。网络请求、UTC、大小、HTTP 及哈希留在 `c-old-items/retrieval-ledger.private.json`，SHA-256 `0fe493dcb67cb0c1c4747742035dda72aaf92f587c2f25d3a0d1100b656b99d5`。实际下载 UTC 2026-11-10，本文日期按本地日期。

| 固定路径 | Git blob SHA-1 | SHA-256 |
| --- | --- | --- |
| README.md | `42c45a8f83fffbed61edb6122b96c051b8a3ae22` | `ee8ae32510108af279496a5328ab90d277fb33d5e622434573aee08a8f5b4013` |
| ExcelBinOutput/WeaponExcelConfigData.json | `0686f5f0682565bf99b3dd66cc09a532260eb152` | `7829d71be03251c5282b27a8d5a670104e59c9ad31d03ee4dcac9b4a8a639e24` |
| ExcelBinOutput/ReliquaryExcelConfigData.json | `dc617e939a0ad4864f88e32dbf98ace9ac11649f` | `00efb17747cfea347ac7d95bf381c8607c0de7d4887bfd41051841b04c7b5e85` |
| ExcelBinOutput/MaterialExcelConfigData.json | `25767f747465f7a9abf184c99a53767fb32cb954` | `c2898b0f2a22870f831544033c92021fa2017475cbaf3bb277278019574a0b0b` |
| ExcelBinOutput/HomeWorldFurnitureExcelConfigData.json | `6dc06fed99eb5038ef63f38e08138e953d208af8` | `afe28eca0e44aa02f137c725421748ae63d5653b3495dd88aee837bd21a6e012` |

README 自称在既有基线上更新部分 7.2 运行资源；此声明不证明与本机目标一致。未把候选的 `itemType` 或其他字段当成目标事实，也未读取其推荐安装位置。

## 结果

保存 12,254 行：活动 10,727，非活动 1,527；活动正 Item ID 9,128 个，装备关联正值 471 行。所有投影字段的 `missing` 均为空。活动 level 0/1/21/90 分别 8,698/1,162/350/517 行；promoteLevel 0/6 分别 10,210/517 行；equipCharacter 0/正值分别 10,256/471 行。按 ID 逐个统计以及每个 equipCharacter 的完整分布均保存在私有报告，非活动完整投影和原行序号另存 `inactiveAudit`。装备关联不证明类型、槽位或恢复成功。

目标 Weapon 288 原始键与活动物品交集为 **239 个不同 ID、1,663 行**。以下仅统计四个候选表独立 `id` 存在性；这些族名是候选文件名，不是目标 ItemType 判定。

| 候选表 | 活动不同 ID | 活动行 |
| --- | ---: | ---: |
| Weapon | 239 | 1,663 |
| Reliquary | 183 | 358 |
| Material | 6,367 | 6,367 |
| Furniture | 2,331 | 2,331 |
| 四表均未知 | 8 | 8 |

四表实际 ID 无重复或跨族命中；驱动保留歧义路径，不采用 first-wins。四表未知为 `224001`–`224008`，每个一行；不能由连续编号猜测类别。固定树还列有其他 Item/Furniture 相关文件，但本包五文件网络预算已用完，且仅凭文件名无法确认这些 ID 的归属；下一步交主线 Sol max 依据源码消费链和固定来源为这 8 个 ID 明确下一资源族，再独立取证、核定字段与目标对应。所有族的目标对应、升阶/装备语义及依赖闭包仍需研究，不仅这八项。

## 验证和复现

[driver](../tools/client72_d02_old_item_coverage.py) 与[测试](../tools/test_client72_d02_old_item_coverage.py)只读输入，报告独占创建，拒绝覆盖。校验输入 SHA、候选 blob、commit/tree、JSON 重复键和目标重复键；候选表重复与跨族冲突保留为 ambiguous。目录及 clone 按当前本机实际位置提供，不使用历史机器绝对路径：

```powershell
python -m unittest tools.test_client72_d02_old_item_coverage -v
python tools/client72_d02_old_item_coverage.py --clone <固定仓库外clone路径> --output local/migration72/d02-resume-20261110b/c-old-items/<新文件名>.json
```

实际 6 项测试通过，覆盖活动/非活动、完整非活动投影、分布、缺字段与缺候选、重复目标拒绝、候选重复/跨族歧义、JSON 重复键、SHA/blob 漂移、64 位与 count 边界。第三次为独立 CLI 进程；三个接受报告均 7,994,179 字节、SHA-256 `faf0f7a46989fc0380468dc7ba81d7305b6f31a4122bd0690c00e607eea07018`。另以独立直接遍历原投影、目标键和候选四表复算活动行、目标交集、候选覆盖及 level/promoteLevel/equipCharacter 分布，与报告一致。报告含旧号信息，只留忽略目录。首次 report 调整为保留完整非活动输入投影后重新执行最终三次，早期报告保留但不作最终验收。

本包仅离线工具、测试和本文，无运行代码、Proto、配置或资源修改，无构建、部署、数据库、JVM、服务或客户端操作。未作 Git 提交；主线负责整体状态更新及审阅。新旧号完整恢复、R3、资源 readiness 和 D-02 六项实机验收均未通过。

主线独立运行6项测试与当前实际外部clone的第四CLI，报告7994179B/SHA `faf0f7a46989fc0380468dc7ba81d7305b6f31a4122bd0690c00e607eea07018` 与三fresh一致，接受key-only和候选存在性。原Player BSON的手填SHA末尾已按固定manifest纠正为7a43，原文件及工具pin没有变化；不扩大目标语义/装备恢复或客户端结论。

<a id="tps-candidate"></a>

## 第五资源族的定向候选增量

`E-01/R3-OLD-ITEM-TPS-CANDIDATE` 只补原四表未知的8个活动ID。当前`ItemData`的ResourceType明确还包括`TpsWeaponExcelConfigData.json`，`TpsAvatarSystem.INITIAL_WEAPON`为224001；`Inventory.loadFromDatabase`在ItemData缺失时跳过原行，故有实际恢复路径价值。这个源码关系足以选下一候选文件，不能证明目标客户端类型或这些ID的原消费。

先在私有`tps-candidate-followup-plan.zh.md`冻结方法/单文件2MiB/至多1次网络/停止条件；复用既有README与固定commit/tree。固定Excel tree给出唯一blob `86af23355376accec851270b850db27a19b3d888`；外部clone原对象缺席，实际只请求该blob一次，不重试或扩其他表，不改clone refs。原候选为[固定提交的TPS表](https://github.com/MeChen618/AstaPS-Resource/blob/6cfecfb55f889f8e3b7156adb12809608db4f74b/ExcelBinOutput/TpsWeaponExcelConfigData.json)，完整SHA-256 `1134be51680369087c229f42347396a696077f4b64740c412b9dab8ab3716daf`。git blob、长度、strict JSON重复键与唯一id均核验。

该候选恰8行，224001–224008全部各命中一行，源字段字面`itemType`均为`ITEM_TPS_WEAPON`；这是候选原字段，不提升为目标ItemType等价。主线再次核原不可变dependency SHA及7994179B四表报告SHA，按原active条件确认8个活动保存行恰对应这8个ID。原四表结果和unknown标记保留为历史，不覆盖或回填旧报告。

私有`tps-candidate/retrieval-and-coverage.private.json` SHA `1c69406861042935cc1c513e57f1f39351cd547f9ffd9962c03c927b60892aa1`；独立`main-review.private.json` SHA `ef2d645759ffe92b52aa86ac88a744b1531b18f4cba810dba18c8256c9d9eac7`，含四个现有Java消费来源完整pin。候选资源族存在性缺口已关闭；目标表/解析器/引用/装备与完整Inventory保存冷恢复仍未知，不猜alias、不导入生产资源或整包替换。此增量没有运行代码、Proto、配置或资源变更，无构建/部署/数据库/客户端动作，也不关闭R3/D-02。
