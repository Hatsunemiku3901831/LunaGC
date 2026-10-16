# 环境材质结束回调与宝箱存活

## 来源与资源

本轮对照固定 AstaPS `7cc148d1f752a43d4b9146b3b73f26875902546f` 的
`EnvironmentalSealHelper`，再核对当前 Gadget、Ability 和原始 `Chest_Interact.lua`。
宝箱本体使用 99999 HP 和锁血；材质使用独立的 30 点耐久。

| 能力 | 材质 | 宝箱状态 | 资源中的结束动作 |
| --- | --- | --- | --- |
| SceneObj_Chest_Bramble_Interact | GrassMaterial／Wood | 104 | ExecuteGadgetLua |
| SceneObj_Chest_Frozen_Interact | IceMaterial／Ice | 105 | ExecuteGadgetLua |
| SceneObj_Chest_Rock_Interact | Chest_Rock／Rock | 106 | ExecuteGadgetLua |
| SceneObj_Environment_WoodenMaterial_Bramble_Interact | Bramble_Burning／Wood | — | KillSelf |
| SceneObj_Environment_WoodenMaterial_BrambleWorld_Interact | Burning／Wood | — | KillSelf |

当前 13 条对应宝箱资源均映射至 `Chest_Interact` 控制器。
其 `OnClientExecuteReq` 在参数 1 为 0 时调用 `ScriptLib.SetGadgetState(Default)`。
对应资源动作省略目标时，也按自身执行。

## 已接入

- 宝箱通过交互和材质流程完成；受击或能力扣血不会直接杀死宝箱。
  原有伤害事件、Lua 受击回调和生命值同步继续经过通用路径，实际扣血与伤害统计为零。
- 104／105／106 状态在进入新旧发奖路径前均拒绝开箱，材质结束后可正常领取一次。
- 服务端收到当前普通 modifier 的移除确认后，执行上述五组资源中指向自身的结束动作。
  宝箱运行原始 Lua，墙体通过普通 `KillSelf`／场景死亡流程销毁。
- 检查实际主人、当前实例、实体注册身份、所属场景／世界、资源能力绑定和对应宝箱状态。
  服务端已有回调、绑定或动画生命周期负责结束的实例仍使用原路径。
- 重复移除不会再次执行；先收到或后收到原生 Lua 请求时，状态变化保持幂等。
  脚本重新封印后的新材质实例可再次解封，初始状态已为 Default 的同类外观可直接开启。

本次使用资源中的材质结束回调与原始控制器。元素附着、30 点耐久的实际消耗和燃烧时序
仍由客户端及其当前协议报告推进；服务端不会把角色伤害值当成材质耐久。
参考中的固定 600 血量、最低 35 扣除及无元素报告作为火元素的补偿逻辑，
与当前资源数据及已验证的结束路径分别记录。

## 验证

补齐 Lua 引擎和会话测试夹具后，先运行业务用例：**12 项全部失败**，
复现致命伤害移除宝箱、封印状态提前发奖及五组结束动作未执行。
随后边界用例 **22 项中 1 项失败**，锁定施法者已经切换世界却仍能解封的问题，已修正。
旧根能力移除、死亡／生命周期失效、实体替换、资源绑定缺失、不同宝箱主人和重复消息分别覆盖。

新增 `EnvironmentalMaterialTest` **25 项通过**：使用真实 Gadget／Ability、
当前耐久报告与 modifier 确认，实际运行原始 Lua，并覆盖解封后的完整发奖调用。
连同宝箱掉落／图鉴、无敌生命值、战斗状态／生命事件、修饰器确认、耐久、主人存活期和 Lua 上下文，
**9 个套件共 227 项通过**，0 项失败、错误或跳过，`test`／`jar` 成功。

扩展回归发现的两项旧战斗用例在未改生产源码的基线上也失败。
原因是测试对不同父能力固定发送编号 1；现改为添加时使用实际编号、移除时使用原生允许的编号 0。
所有伤害／保护断言保留，基线记录见 `combat-baseline.log`／`combat-baseline-results`。

7 个源码／测试输入以及 7 个外部资源文件的宿主与容器 SHA-256 一致。
相对上一构建的 7 个变化类均来自本次生产源码，与编译目录逐个核对一致。
证据目录：`debug/aster-mid-20261011/batch294/`。
`environmental.jar` SHA-256：
`89997e65b83c8ece8a16a12ea28650f47d69d139d2615fea918faababdc68fc3`。

## 仍需验证

本轮确认了服务端结束回调、原始 Lua、受击保护及发奖路径。
真实客户端的元素命中、逐步耐久、燃烧与解封动画尚未实机验收，
其余环境材质及完整服务端元素盾结算也继续列在剩余范围中。
