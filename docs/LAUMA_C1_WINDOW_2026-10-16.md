# 菈乌玛一命监听窗口与队伍传播

## 资源与入口

本轮沿用 [治疗结算](LAUMA_C1_HEALING_2026-10-16.md) 的当前能力、天赋和命座资源，
对照固定 AstaPS `7cc148d1f752a43d4b9146b3b73f26875902546f`
与 gcsim `f5916931a18bb3db511c438e3c89df55d32b7261`。

| 资源位置 | 内容 |
| --- | --- |
| ElementalArt／onAbilityStart／7 | `TriggerAbility(Avatar_Lauma_Constellation_1)`，目标 Other，要求一命解锁参数 |
| ElementalBurst／OutCamera／onAdded／2 | 与上项内容相同的触发动作 |
| Constellation_1／onAbilityStart | 向 Team 附加 TeamHandler |
| TeamHandler | 持续 20 秒，每 0.5 秒附加 AvatarHandler，距离条件不超过 40 米 |
| AvatarHandler | 持续 2 秒，监听 MoonOvergrow，再应用治疗修饰器 |

E／Q 两个触发动作的完整对象已比较一致；受控调用链用实际 E 动作验证。
完整技能动画和实际客户端释放仍属于后续验收。

## 已接入

- 场景更新推进当前队伍实体的能力时钟。
- 支持资源中这类无元素、无属性变化的 `RefreshUniqueDurability` 反应监听器。
  Team 窗口只接入已经验证的持续时间、周期附加及子监听器结构；其他堆叠／元素耐久语义继续配对。
- 显式支持受控回调的 Other 和 Team 目标。
  Other 保留传入的调用目标，Team 使用当前玩家的队伍实体。
- 窗口开始时按资源附加监听器，不提前治疗；之后每半秒重新筛选。
  该窗口的 AllPlayerAvatars 覆盖同场景、同世界且仍在世界成员表中的玩家队伍，
  距离条件逐个检查。其他普通 AllPlayerAvatars 动作保持原有范围。
- 子监听器在范围内刷新两秒时长；离开范围后停止刷新。
  客户端确认保留原来的时钟，已确认和未确认的子监听器都会按资源到期。
- 父窗口结束或被移除时清理其子实例，保留其他能力的同名效果。
  源角色生命周期失效、队伍实体被替换时也清理窗口。
- 新施放创建独立窗口和时长，旧客户端移除不能删除新窗口。
  关联使用实际控制器及实体身份，不按 UID／实体编号共享静态状态。
- 反应回调在接受和排队执行时都检查当前实例；窗口截止时间直接参与判断，
  不依赖下一次场景 tick 才拒绝过期请求。
  省略修饰器 ID 时，只有唯一且有效的匹配监听器可以执行，不能借用根能力重放已移除的监听器。

实例关系的维护和条件移除按具体对象进行。子实例的注册、刷新及父效果清理保持分开的回调阶段，
避免在持有父实体修饰器注册锁时执行跨实体结束动作。

## 验证

新增用例先复现队伍时钟未更新、周期刷新被拒绝、客户端接管后窗口不结束、
父效果不清理子实例及联机传播范围缺项。
之后分别取得旧反应回放、排队回调、旧实例移除、确认后的两秒到期、
技能目标校验、队伍实体替换、截止时间及省略 ID 分支的失败断言，再修复。

`LaumaHealingTest` 当前共 26 项，覆盖此前治疗用例及本轮窗口流程。
其中包含开始、确认、有效反应、刷新、到期、迟到反应的连续流程。
联机资源用例确认 39.9 米角色收到监听器、40.1 米角色不收到，离开范围后两秒失效。

最终 22 个测试类共 **404 项，失败 0、错误 0、跳过 0**。
回归还覆盖燃素、体力、治疗、修饰器确认、队伍恢复、世界成员与场景切换。
测试场景共用夹具补齐 Lua 引擎初始化，最终日志已消除因此产生的加载异常。

证据位于 `debug/aster-mid-20261011/batch297/`：

- `lauma-window-red-results/`、`lauma-window-replay-red-results/`：初始窗口和回放失败记录。
- `lauma-listener-expiry-assertion-red-results/`、`lauma-team-ownership-red-results/`、
  `lauma-window-deadline-red-results/`、`lauma-implicit-listener-red-results/`：其他关键失败断言。
- `lauma-window-final.log`、`party-aura-final-results/`：最终测试与构建结果。
- `party-aura-verification.json`：14 项源码／测试输入匹配构建容器；17 个变化类匹配编译输出。
- `resource-proof.json`、`c1-triggers.json`：三个实际资源的校验与 E／Q 触发动作比对。
- `party-aura.jar`：SHA256 `9ab3cc415f193ede8e11f5cb15843e8653da937eb8aa6b8aa6ca4e208519ed0b`。

## 本地运行

2026-10-16 10:58（UTC+8），源码提交 `b0af5e891d880de542bf7ac6fc8f3b13c8928773`
已推送，并将本地服务切换至上述 `party-aura.jar`。
旧进程经 GM stop 正常结束；新进程 PID `49236` 同时监听 TCP 8088 和 UDP 22101，
健康检查通过、死锁为零、在线玩家为零。
Mongo 继续使用 `lunagc_mongo-data`，资源提交保持 `dac2a9d43332ef51879522cf39da2831b0ca0543`。

停止前检查、正常停止及启动验证分别记录在同目录的
`runtime-stop-preflight.json`、`runtime-stop-proof.json`、`runtime-start-proof.json`。
启动验证同时核对了实际进程命令、构建哈希、端口归属和数据卷。
客户端未启动，`clientGameplayVerified` 为 false；此次部署不证明此前游戏报错已消失。

## 验收边界

本轮确认服务端监听窗口、资源刷新、范围、确认及生命周期。
联机监听回调的治疗中心及共享间隔已有后续[服务端验证](LAUMA_C1_INTERVAL_LIFETIME_2026-10-16.md)。
实际 E／Q 动画、月绽放反应产生端、客户端图标及整场多人战斗仍需继续配对和实机验收。
这也不是其他 RefreshUniqueDurability 材质、盾条、全部角色或全部玩法已完成的结论。
