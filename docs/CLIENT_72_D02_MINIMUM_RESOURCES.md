# D02-R3 最小资源：进行中

本记录是受控入场资源的分项证据，不是完整资源闭包或部署许可。固定候选 commit `d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1`、根 tree `5b334e711319726c504da95dbfe6ebb26baaa7d3`，来源及版本差异见[资源候选](CLIENT_72_RESOURCE_CANDIDATE.md)。上游候选没有 Scripts；scene Lua 仍为本项目旧实现及适配，不冒称 7.2 原始脚本。`resourceReady=false`。

2026-11-06 本轮重新按 `tools/game_path.ps1` 解析 GAME_PATH 并直接读取三输入 SHA-256：YuanShen.exe `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`、global-metadata.dat `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`、startup-metadata.dat `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`。资源子模块提交 `dac2a9d43332ef51879522cf39da2831b0ca0543`；原有 `ScriptSceneData/flat.luas.scenes.full_globals.lua.json` 的 SHA-256 `db3e6a2e87436fefb57d43e8f2b52528017d2a68d530e8cf56b85a53242e5bfe` 未动。候选目录是已按 Git tree/blob 固定的提取集，并非 Git checkout。

## 子合同 A：出生角色和初始武器、技能引用

`HandlerSetPlayerBornDataReq.java:56-89` 限定选择 `10000005`/`10000007`，任务开启时选基础 depot `501`/`701`，关闭时选 `504`/`704`；成功创建、绑定队伍并确认保存才进入登录。`Avatar.java:124-152` 和 `Player.java:1317`、`AvatarStorage.java:75-88` 将角色的 `initialWeapon` 用于新装备，只有 itemData 存在才添加并装备。具体生产依赖不能由目标资源存在推断已运行。

| 角色 | initialWeapon | questing 启用 depot | questing 禁用 depot |
| --- | ---: | ---: | ---: |
| 10000005 | 11101 | 501 | 504 |
| 10000007 | 11101 | 701 | 704 |

`tools/audit_d02_resources72.py` 验证 commit→根 tree→Excel tree→四个文件 blob/大小，再核以上两行及四个 depot 的 `skills`、`subSkills`、`energySkill`、`attackModeSkill` 正 ID 全部存在于候选 AvatarSkill 表；0 保持缺省哨兵。各 depot 对应的正 ID：

| depot | skills | subSkills | energySkill |
| --- | --- | --- | --- |
| 501 | 100540 | 10035, 10101, 10001, 10102, 5005010 | 无 |
| 701 | 100550 | 10035, 10101, 10001, 10102, 5007010 | 无 |
| 504 | 100543, 10067 | 10101, 10001, 10102, 10006, 5005010 | 10068 |
| 704 | 100553, 10067 | 10101, 10001, 10102, 10006, 5007010 | 10068 |

`attackModeSkill` 四行均无正引用。`subSkills` 是本次新增范围；此前 849 条检查不包含它们。`Avatar.java:976-977` 也将 subSkills 用于技能合法性判断；存在性不证明入场必定调用该分支或技能效果正确。两角色 `avatarPromoteId=12`；504/704 的 talents 为 71–76、inherent proud skill groups 为 721/722，相关表及能力、角色成长曲线、武器成长/突破和其他字段引用仍未核，不标通过。四个 depot 的 ability group 为 Boy/Girl 的 Common/Wind，内容与实际加载尚未验证。旧测试号必须按其实际角色/武器/保存状态另核，不能用此新号模型替代。

本次实际运行新工具两次，输出逐字节一致，报告 SHA-256 `a5974ef30f2d8e01e607b83e8ef68dcd577423a5a9ad1ab11db662bce9f5698f`；三项聚焦测试通过，覆盖两性、零哨兵、缺武器、缺技能、错误 depot 和负数拒绝。完整结果在忽略目录 `local/migration72/evidence/d02-r3-avatar-references{,-repeat}.json`。未运行 Gradle、生产加载、数据库或客户端。此项只验证固定候选中的选定引用存在，不验证客户端 revision、技能业务或实际装备存档。

主线审核时标准模块测试入口因绝对导入失败；本次修复包内/脚本两种导入后，实际执行下列命令，3 项通过、失败/错误/跳过为 0，模块方式生成报告也与原报告逐字节一致：

```powershell
python -m unittest tools.test_audit_d02_resources72 -v
python -m tools.audit_d02_resources72 --source local/migration72/sources/AnimeGameData/d54f6f0d423ded9d055bb740a3e5f3aa8f2667f1 --commit local/migration72/evidence/resource-source-commit.json --root-tree local/migration72/evidence/resource-source-tree.json --excel-tree local/migration72/evidence/resource-source-ExcelBinOutput-tree.json --output local/migration72/evidence/d02-r3-avatar-references-module.json
```

## 尚未闭合

生产新号 `GameConstants.START_POSITION` 为 `(2747,194,-1719)`、`Player` 初始 scene 3；这不是独立确认的 7.2 安全落点。必须继续按实际索引、邻近 group、加载顺序和触发器核验，不能将 13,616 组顶层加载当运行事件通过。旧号可能处于其他保存位置，不可删档。根 fastRequire=true 保持，已知固定审计 fast 407 组/449 错误事件及 inline 零错误的范围与[真实加载](CLIENT_72_PRODUCTION_SCENE_LOAD.md)、[嵌套 require 修复](CLIENT_72_REQUIRE_BINDINGS_FIX.md)相同，未证明目标运行兼容。

客户端内置 revision `49008628/49008628/48722240` 与候选标签 R/S/D `49086080/49210135/49230962` 的对应和热更新状态仍未知；须等 R2 自然观察，不能人为改 revision。scene 3 索引、具体触发器及 ScriptLib 后代、目标实体数据和实际落地仍待验证，59 个缺实现 API 不能整体假定必需或可后置。完整资源链及实机为 R3/R4/R6 开放项。
