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

## 能力处理器差异

AstaPS 的首轮注解比较得到八个差异。AvatarCombatMixin 本次新增入口后，剩余七个按源码和实际本地入口分类：

| 参考类型 | 当前结论 |
| --- | --- |
| AttachActionToModifierMixin | 已由 ModifierAttachmentCallbacks 接入进入／移除回调及所有权；[生命周期记录](MODIFIER_ATTACHMENT_CALLBACKS_2026-10-14.md) |
| AttachToAbilityStateMixin | 已由 AbilityGlobalValueWatchers／AbilityStateBinding 接入条件；[状态绑定记录](ABILITY_STATE_BINDINGS_2026-10-14.md) |
| DoWatcherSystemAction | 参考 execute 仅返回 true，没有监听或更新逻辑；不能据此补齐玩法 |
| SkillButtonHoldChargeMixin | 参考 execute 仅返回 true，未提供蓄力控制实现 |
| AttachToStateIDMixin | 需要继续核对动画状态与修饰器生命周期；参考会立即附加配置中的修饰器 |
| AttachToNormalizedTimeMixin | 需要继续核对状态、播放时间及结束时机；参考省略这些判断 |
| AttachToMultiNormalizedTimeMixin | 同上，还需逐项处理多个播放区间 |

当前资源有 6,359 个 StateID 节点、4,097 个单播放区间节点及 1,668 个多播放区间节点。
这些是配置数量，不能当作独立玩法数量或完成率。
本地已有客户端修饰器登记入口，但不能仅凭此宣称全部动画条件、回调与原生表现已经验收。

LunaGC_7.1.0 和 HunkyMeow 的能力处理器注解未发现额外缺项，这也不是完整功能差异结论。

## 处理器之外

按相同文件路径比较，本地缺少 AstaPS 的 324 个路径、LunaGC_7.1.0 的 15 个路径及 HunkyMeow 的 34 个路径。
其中存在本地改名、合并实现、协议替代、扩展命令及空响应，不能按路径逐个复制或计算完成率。
本轮核对的 HunkyMeow 天气预报请求只写日志；LunaGC 的近期联机玩家与千星玩家请求只返回空消息。
HunkyMeow 的大世界持久建造已在本地接入，并修正非持久模式不生成、删除后要求重新登录和编号维护的问题。
具体命令、数据库保存及客户端验收边界见[大世界持久建造](SCENE_BUILDING_2026-10-15.md)。

这些路径需要继续按行为核对，当前没有完整覆盖结论。
原始比较、固定提交、能力配置数量和客户端检查保存在 `debug/aster-mid-20261011/batch215/` 至 `batch219/`。

## 其他仍需完成的范围

- 完整燃素消耗与恢复、龙技能、输入及动画流程。
- TPS 的真实射击、换弹、补给和完整模式。
- Rerir 整场战斗及其他角色的剩余控制条件。
- UGC 节点运行、发布及结算；文件生成／转换工具不提供完整游戏网络服务。
- 所有改动的当前客户端验收，以及战斗状态消息字段的完整编解码配对。

没有将上述事项标记为已经完成。参考库下载、基础接入和当前相关测试通过，均不等于项目整体玩法完成。
