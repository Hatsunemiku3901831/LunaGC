# 普通秘境进场、重试与挑战机关

## 来源与适用范围

本轮对照 AstaPS 固定提交 `7cc148d1f752a43d4b9146b3b73f26875902546f`
中的 `DomainContinueSpawnHelper`、`DomainChallengeKeyHelper` 和 `DomainDungeonHelper`，
再用当前 Dungeon、Gadget 及 Scene Lua 资源核对。

适用范围为 `DUNGEON_DAILY_FIGHT` 中的天赋、武器和圣遗物秘境。
当前表内有 306 条对应记录、162 个不同场景；这些是资源目录数量，不代表全部玩法已验收。
剧情、周本和深渊沿用各自的入口。

| 编号 | 当前 Gadget 资源 | 处理 |
| --- | --- | --- |
| 70360010 | ElementDungeon Operator／Worktop | 普通挑战机关 |
| 73051002 | LeyLineChallengeKey／Worktop | 支持的挑战机关类型 |
| 70350096 | SnowDungeonAirWall／Gadget | 空气墙，不用于选择落点 |
| 70350035 | Mamolu Entity／Gadget | 防御目标，不用于选择落点 |
| 70340014 | 奖励神像 | 不用于选择落点 |

参考库的编号清单混入了空气墙与防御目标。本地按实际类型筛选，并仅选择静态组中
初始套件包含的机关。多套件的随机组不预选；只有一个套件时，`rand_suite` 标记仍是确定的。
缺少有效配置、目标或有限坐标时保留原出生点。

## 已接入

- 首次进场及领取奖励后的场景重建，均可落在最近有效机关朝向入口的一侧，距离最多 2.2 米，面向机关。
  坐标采用独立快照；原地出生时保留有效的原朝向。
- 目的地在 `PlayerTeleportEvent` 之前准备，插件仍可取消传送或指定其他目的地。
  现有成员保护、事务式场景切换及出口记录继续执行。
- 为选中的操作台准备 Default 状态与选项 7，处理近机关落点没有经过走廊区域触发器的情况。
  比如场景 40201 的原始 `ENTER_REGION_2001` 正是通过走廊后才设置该选项。
- 第一次接受开始请求后立即移除选项，重复请求仍收到响应，但不重复进入原有 worktop、Lua、任务及兜底分发路径。
  其他操作台和其他选项保持原流程。
- 通过场景中的机关 tick，在至少一秒后隐藏同一生命周期的机关。
  场景销毁、管理器替换、实体编号复用、生命周期失效或玩家离场后，不再操作旧对象。
  状态属于具体 Scene，没有按场景编号共享的延迟任务。
- 原始失败 Lua 可在同一场景恢复 Default 和选项 7，再开启下一轮。
  只有本次开始之后实际启动并失败的挑战可以解除上一轮保护；旧失败记录不能重开新的待开始请求。
  已确认失败时，旧的隐藏期限不会覆盖失败脚本的状态重置。

挑战怪物、胜负及奖励继续由原始 Lua 和现有秘境系统处理。
本轮不使用参考库中的全部 40100–40999 范围作为开启条件。

## 资源区块修复

真实资源用例发现，以下 20 个场景声明了一个 `block_rects`，但 `blocks` 清单为空。
对应区块及组文件实际存在；`SceneMeta.load` 因清单长度不匹配而抛出数组越界。

| 场景 | 修复 |
| --- | --- |
| 40760–40763 | 引用各自已有的同编号区块 |
| 40770–40773 | 引用各自已有的同编号区块 |
| 40780–40791 | 引用各自已有的同编号区块 |

资源仓库提交 [`dac2a9d43`](https://github.com/invoker-bot/LunaGC-Resources/commit/dac2a9d43332ef51879522cf39da2831b0ca0543)
已推送，主项目同步更新资源子模块引用。当前外部开发资源目录也使用该提交。

## 验证

- 修复前，40201、40300、40774 三个真实进场目的地测试全部失败。
- 20 个区块加载测试在原资源中全部复现数组越界，修复后逐个通过实际 Lua 加载器读取区块和组。
- 生命周期失效后仍可开始，以及旧隐藏期限覆盖挑战失败重置，分别先取得失败断言，再修复。
- 当前实际进场／取消／重试用例覆盖 40201、40300、40774、40780，包含三种适用秘境类型。
  另有插件覆盖目的地、重复网络请求、并发选择、相同编号替代实体与失败后新一轮用例。

最终八个测试类共 **196 项，失败 0、错误 0、跳过 0**：
`DungeonChallengeEntryTest`、`DungeonRestartSafetyTest`、`DungeonEntryOutcomeTest`、
`WorldEntryCallerTest`、`WorldAdmissionLifecycleTest`、`WorldMembershipTest`、
`TowerSceneHandoffTest`、`TowerChamberRetryTest`。

构建与测试证据位于 `debug/aster-mid-20261011/batch295/`：

- `domain-entry-final.log`、`domain-entry-final-results/`：最终构建与测试结果。
- `domain-spawn-red-results/`、`domain-resource-red-results/`、`domain-entry-boundary-red-results/`、
  `domain-failure-red-results/`：修复前失败记录。
- `domain-entry-verification.json`：8 项源码／测试输入与构建容器一致，16 个变化类与编译输出一致。
- `resource-proof.json`：35 个实际资源文件的外部目录／容器字节校验，以及子模块换行规范化后的校验。
- `domain-entry.jar`：SHA256 `75c4da2f8c10251aaf27e2c143b5660251f9a2a4663622a74838f2d878eb6130`。

这些结果确认服务端入口、资源加载与回归边界。实际客户端落地碰撞、机关显示、
完整多人挑战及奖励画面仍待实机验收；此前反馈的客户端错误没有因此标记为已解决。
