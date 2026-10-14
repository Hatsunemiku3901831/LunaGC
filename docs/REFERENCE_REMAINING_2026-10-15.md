# 参考差异的当前核对状态

九个参考库使用 `debug/references.json` 中的固定提交，本次重新检查均完整检出且工作区干净。
完整检出、同名类存在或注解数量均不能单独证明全部特性已适配。

## 本次完成

| 内容 | 实现和验证记录 |
| --- | --- |
| 芭芭拉六命自动复活 | [战斗／溺水、死亡结算、独立冷却](BARBARA_AUTOMATIC_REVIVAL_2026-10-15.md) |
| 燃素回调 | [重复扣除、零值、表达式及生命周期检查](PHLOGISTON_CALLBACKS_2026-10-15.md) |
| 角色战斗状态回调 | [明确进入／退出、父实例、重复消息及协议边界](AVATAR_COMBAT_CALLBACKS_2026-10-15.md) |
| 体力回调输入 | [零消耗、无效输入及失效实体](STAMINA_MIXIN_INPUT_2026-10-15.md) |
| 大世界持久建造 | [保存、临时模式、恢复及立即删除](SCENE_BUILDING_2026-10-15.md) |
| 战斗伤害统计 | [实际扣血、召唤物归属、DPS 与 GM 查询](DAMAGE_LOG_2026-10-15.md) |
| 材料采集点标记 | [20 种材料、最近点、个人标记保留及清理](MATERIAL_TRACKING_2026-10-15.md) |
| 动画时序的效果生命周期 | [客户端确认、进入／退出、重复消息及父实例清理](ANIMATION_TIMING_2026-10-15.md) |
| 当前资源燃素动作与同步 | [消耗／补充／设值、换图保留、联机各自同步](PHLOGISTON_RESOURCE_ACTIONS_2026-10-15.md) |
| 持续燃素消耗与补充 | [调用 135、原生 0.3 秒缺省周期及明确目标](PHLOGISTON_PERIODIC_MIXIN_2026-10-15.md) |
| 活动无限燃素 | [队伍阻止消耗、最后实例移除恢复](INFINITE_PHLOGISTON_2026-10-15.md) |
| 车辆燃素通知 | [命令 7926、当前客户端字段 13／15](VEHICLE_PHLOGISTON_PROTOCOL_2026-10-15.md) |
| 燃素球联动 | [人龙各自补充、调用 136、拾取去重及物品动作](PHLOGISTON_BALL_PICKUP_2026-10-15.md) |
| 燃素倍率开关 | [当前存量消耗、容量补充、目标及配置优先级](PHLOGISTON_RATIO_CONTROL_2026-10-15.md) |
| 全队倒下的任务失败 | [真实任务失败、复活顺序、并发去重和数据库保存](TEAM_WIPE_QUEST_FAILURE_2026-10-15.md) |
| 活动全服开放日 | [12 条真实条件、04:00 日界线及复刻排期](ACTIVITY_GLOBAL_DAY_CONDITIONS_2026-10-15.md) |

## 能力处理器差异

AstaPS 的首轮注解比较得到八个差异。AvatarCombatMixin 本次新增入口后，剩余七个按源码和实际本地入口分类：

| 参考类型 | 当前结论 |
| --- | --- |
| AttachActionToModifierMixin | 已由 ModifierAttachmentCallbacks 接入进入／移除回调及所有权；[生命周期记录](MODIFIER_ATTACHMENT_CALLBACKS_2026-10-14.md) |
| AttachToAbilityStateMixin | 已由 AbilityGlobalValueWatchers／AbilityStateBinding 接入条件；[状态绑定记录](ABILITY_STATE_BINDINGS_2026-10-14.md) |
| DoWatcherSystemAction | 参考 execute 仅返回 true，没有监听或更新逻辑；不能据此补齐玩法 |
| SkillButtonHoldChargeMixin | 参考 execute 仅返回 true，未提供蓄力控制实现 |
| AttachToStateIDMixin | 已接入客户端确认的子效果生命周期；状态判断由客户端执行，完整原生／画面验收待完成 |
| AttachToNormalizedTimeMixin | 已保留播放区间并接入确认生命周期；不在能力加载时提前附加 |
| AttachToMultiNormalizedTimeMixin | 已保留多个播放区间并接入确认生命周期；客户端逐区间时序仍需实机验收 |

当前资源有 6,359 个 StateID 节点、4,097 个单播放区间节点及 1,668 个多播放区间节点。
这些是配置数量，不能当作独立玩法数量或完成率。
本地已有客户端修饰器登记入口，但不能仅凭此宣称全部动画条件、回调与原生表现已经验收。

LunaGC_7.1.0 和 HunkyMeow 的能力处理器注解未发现额外缺项，这也不是完整功能差异结论。

## 处理器之外

本次重新比较相同 Java 文件路径，并补入全队倒下及活动全服开放日处理器后，本地缺少 AstaPS 的 322 个路径、LunaGC_7.1.0 的 14 个路径及 HunkyMeow 的 33 个路径。
其中存在本地改名、合并实现、协议替代、扩展命令及空响应，不能按路径逐个复制或计算完成率。
本轮核对的 HunkyMeow 天气预报请求只写日志；LunaGC 的近期联机玩家与千星玩家请求只返回空消息。
HunkyMeow 的大世界持久建造已在本地接入，并修正非持久模式不生成、删除后要求重新登录和编号维护的问题。
具体命令、数据库保存及客户端验收边界见[大世界持久建造](SCENE_BUILDING_2026-10-15.md)。

这些路径需要继续按行为核对，当前没有完整覆盖结论。
本次扩展核对了任务内容／条件／执行器与活动条件／watcher 注解入口；补入上述两项后，三个 Java 参考库在这些入口未再发现缺少同名注册的类型。
这只说明入口覆盖；七项能力注解差异仍按上表分类，其他实现与时序差异继续审阅。
原始比较、固定提交、能力配置数量和客户端检查保存在 `debug/aster-mid-20261011/batch215/` 至 `batch221/`。

## 其他仍需完成的范围

- 持续 Set 模式、Add／Set 倍率的完整客户端热修复分支配对，以及完整龙技能、输入和动画的实机验收；当前资源的普通动作、周期 Cost／Add、倍率开关、无限燃素、车辆通知及燃素球联动已分别接入。
- TPS 的真实射击、换弹、补给和完整模式。
- Rerir 整场战斗及其他角色的剩余控制条件。
- UGC 节点运行、发布及结算；文件生成／转换工具不提供完整游戏网络服务。
- 所有改动的当前客户端验收，以及战斗状态消息字段的完整编解码配对。

没有将上述事项标记为已经完成。参考库下载、基础接入和当前相关测试通过，均不等于项目整体玩法完成。
