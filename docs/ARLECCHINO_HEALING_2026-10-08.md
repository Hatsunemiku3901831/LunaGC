# 阿蕾奇诺治疗与生命之契适配

## 来源与本地证据

参考 [AstaPS `b7441c0c`](https://github.com/MeChen618/AstaPS/commit/b7441c0c063545df37eda3cbd0c968db22b572b3)
的自身爆发治疗例外、施法者生命之契比例项和实体锁。
本地实现按当前资源结算，未引入参考实现的全局玩家缓存、延时清空任务或名字包含判断。

[HoYoWiki 的生命之契说明](https://wiki.hoyolab.com/pc/genshin/entry/5653?lang=en-us)
区分普攻命中的生命之契消耗与自身爆发治疗：普攻消耗当前生命之契的 7.5%，间隔 0.03 秒；
战斗中仅爆发能够治疗阿蕾奇诺。实际公式和动作顺序以本机当前资源为依据。

资源文件：`BinOutput/Ability/Temp/AvatarAbilities/ConfigAbility_Avatar_Arlecchino.json`。

- `Avatar_Arlecchino_ElementalBurst_Attack.onAbilityStart` 先写入 `_HPDebts=0`，再保存当前生命之契。
- `Avatar_Arlecchino_ElementalBurst_HealDelay.onRemoved` 使用
  `_HPDebts * HealHp_Debt_Ratio + FIGHT_PROP_CUR_ATTACK * HealHp_Atk_Ratio`。
- 同一动作携带 `FFNEJGGNAFF` 和导出的 `__exp_healTag`，均为 `Arlecchino_ElementalBurst_Heal`。
- `SwitchHealToHPDebtsMixin` 的条件排除该治疗标签。
- `Avatar_Arlecchino_FireAttack_ReduceHPDebts` 为 0.03 秒的唯一修饰器，
  保存 `Cur_HPDebts` 后按 `Cur_HPDebts_Reduce_Ratio` 执行 `ReduceHPDebts`。

扫描当前 Ability/Temp：743 个 HealHP 动作携带 `FFNEJGGNAFF`，其中 93 个还有导出的治疗标签。
具体路径与字段保存在本机 `debug/arlecchino-20261008/heal-resource-tags.json`。
没有给未核实的混淆生命之契比例字段起新别名。

## 修复

1. 读取明文及上述已核实的治疗标签，增加参考实现的明文 `amountByCasterCurrentHPDebtsRatio` 项。
2. 自身爆发例外要求施法者和接收者为同一实体、角色 ID 为 10000096、
   资源技能名和治疗标签精确匹配；不使其他角色、其他技能或其他队友绕过治疗限制。
3. 治疗沿用已有的先偿债、再恢复 HP 结算。资源公式为 1.5 倍债务加 1.5 倍攻击时，
   债务 40、攻击 10 的请求治疗为 75；偿还 40，实际恢复 35。
   通知分别报告请求量、实际恢复量、偿债量及最终属性；满血仍能偿债，死亡不能通过治疗复活。
4. 计算和治疗使用同一实体锁；治疗转换标志不被临时关闭，也不需要异常时恢复。
   克洛琳德现有 0.8 倍偿债行为保留并改用共同结算器，她的闪避治疗标志仅写入她本人。
5. `AddHPDebts`／`ReduceHPDebts` 检查条件，优先读取明确的 value，动态公式读取战斗属性、
   全局值及技能常量。拒绝负数、无效数值和死亡角色；加契上限保留为最大 HP 的 200%。
   只广播实际变化，施法者没有 Player 对象时不再因无关的 getCurrentAvatar 调用失败。
6. 删除 Scene 对阿蕾奇诺任意攻击额外扣除 2.4% 生命之契的分支。
   普攻扣契由资源修饰器的 ReduceHPDebts 回调执行，技能、爆发、重击和多目标伤害包不再被此分支重复扣契。
7. Mixin 按实际类型识别，修复两个缺失处理器均为 null 时误触发治疗转换或燃素消耗的问题。

## 验证

新增 30 项测试，覆盖真实资源标签、爆发快照、实际广播内容、满血、死亡、静默效果、
双实体隔离、普通治疗限制、克洛琳德兼容、条件与动态表达式、无 Player、异常值、
同一实体锁，以及并发加契／偿债／普通治疗。

最终修正的测试在旧实现上复现 **30 项中的 23 项失败**。
初次夹具编译错误和死亡清债断言错误单独保留，不计作源码缺陷。
格式化仅针对本批六个小文件：四个生产文件已应用，两个测试文件因现有格式步骤不收敛而保持原格式。
大型 Scene、AbilityManager 和 AbilityActionHandler 仅修改必要代码段。

最终全量测试、构建及 SHA-256 记录在外部实现适配记录中。
原始日志、XML、旧源码快照、最终源码快照及产物位于本机 `debug/arlecchino-20261008/`。

## 剩余验收

- 本批未部署到日常服务，玩家数据库没有参与测试。
- 游戏内完整爆发动画、敌人血偿勒令回收、命座、武器增益、切人与联机仍需实机验证。
- 0.03 秒唯一修饰器及客户端动作去重沿用现有生命周期，尚未据实机日志确认所有攻击帧。
- 通用 HPDebtsMixin、SwitchHealToHPDebtsMixin 的进入／退出战斗和所有条件仍需完整实现；
  此批只修复错误分派和已核实的自身爆发例外，不能据此宣称角色所有技能已还原。
- 不改变克洛琳德的完整技能模型；其余生命之契角色与武器仍需按各自资源核对。
