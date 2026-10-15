# TPS 资源补给动作

在[弹药总量](TPS_AMMUNITION_TOTALS_2026-10-15.md)和
[弹匣快照](TPS_MAGAZINES_2026-10-15.md)基础上，接入当前资源的 `TPSSupplyAmmo`。
本次完成明确指定弹药池、没有额外补给倍率的分支，以及榴弹发射器的实际 modifier 回调。
弹药箱的父级分发与拾取流程仍在后续范围内。

## 资源与原生证据

核对参考 AstaPS 固定提交 `7cc148d1f752a43d4b9146b3b73f26875902546f` 后，
继续按[客户端提取工具](../tools/extract_client_type_names.py)固定 SHA-256 的原生文件配对。

动作类型 `LBFCIEMIBND` 的 `Execute` 位于 `0x1477d50d0`，
调用 `IMHPJAGBBNN.FCLOLKJEKAH`（`0x14a823d00`），再由
`EOHNDJFNKOK.KOHAFLHKECI`（`0x14a24db40`）分支到
`IGCPKFEMDNH`（`0x14a24d470`）。

| 资源字段 | 本地字段／含义 |
| --- | --- |
| `EMNMAKMAKLM` | `tpsSupplyAmount`，动态浮点数量 |
| `NHCCEJKBOPB` | `tpsSupplyAmmunitionId`，明确的弹药池 ID |
| `FMGHPKANDIA` | `tpsSupplyUseExplicitAmmoId`，是否使用上述 ID |
| `DBPIABFDELD` | `tpsSupplyUseBonus`，是否应用额外补给倍率 |

原生先按 float 求值，再用 `cvttss2si` 截断整数。
明确池分支将补给量写入调用参数 31 的正向条目，没有先在本地增加弹药总量。
服务端发奖后发送 `TpsAmmunitionChangeNotify`（24371）实际增量，不能把总量当作增量发送。
额外倍率和非明确池分支尚未完成配对，当前明确拒绝这些配置。

当前资源只有两个文件含该动作，共 9 处：

- `ConfigAbility_TPSWeapon_GrenadeLauncher.json`：1 处，为
  `Avatar_TPS_GrenadeLauncher_Normal_WeaponAdd_OnFire` 的
  `UNIQUE_Avatar_TPS_GrenadeLauncher_Set_BulletOverride_1.onAdded[2]`，补 6 发榴弹。
- `ConfigAbility_Scene_V7_0_ShadowArea.json`：8 处，位于箱子的
  `TPSAmmoBoxDispatch` 和弹药包的 `DoActionByGlobalValue` 分支中。
  每组分别配置 1001／75、1003／6、1005／1、1004／8。

9 处均明确指定弹药池，未开启额外补给倍率。
其中 7 处使用当前表已有的池，另外 2 处指向资源缺失的 1004；没有为缺失池编造容量。

## 服务端行为

- 注册真实动作处理器，读取当前及可读字段别名，支持能力参数表达式。
- 数量使用原生 float 精度后截断，拒绝缺失、负数、非有限值、无效表达式和 int 越界。
  合法零值保持成功，不保存或发送补给包。
- 来源必须是当前场景中的实际实体，能力仍安装在该实体上。
  接收者必须是该玩家当前登记、仍存活并装备 TPS 武器的角色。
- 补给只改变总量；已有弹匣记录保持不变。按资源上限截取实际增加值，只保存一次。
  满池补给保持成功，不重复保存或发送增量。
- 原生 modifier 的添加会执行服务端补给；该实例与后续叶子确认共用一次领取记录，
  包括唯一活动实例下缺少 modifier ID 的确认。重复添加或确认不会重复发奖。
- 移除后的迟到确认拒绝处理；真正移除后重新添加的新实例可以再次补给。
  移除阶段的冷却记账不会执行补给。
- 客户端自行发送的 Picked／Supply 正数量保持拒绝，避免与资源发奖叠加。
  没有放开 UI 视觉动作的服务端执行范围。

## 验证

先以未实现的处理器运行测试，复现 9 项行为失败，再实现补给与实例去重。
新增 35 项测试覆盖全部 9 处实际资源、float 精度和截断、边界、容量、实际增量、
来源／能力／场景／接收者归属、换队、未装备、动态能力、弹匣保留，
以及真实 modifier 消息、重复／无 ID／迟到叶子确认和原生正向补给请求。

相关 TPS、资源、冷却、回调路由与重复动作共 385 项测试、12 个套件通过，
0 项失败／错误／跳过，`test`／`jar` 成功。
宿主与容器的 6 个改动输入哈希一致，16 个变化类均与编译目录、JAR 字节核对一致。
产物 SHA-256：`12bd0b190906850815b75ef5adee6feba43e40af6fb857f96e5e606da8ebc584`。
核对结果位于本机
`debug/aster-mid-20261011/batch265/supply-verified-verification.json`。
红测试、原生反汇编、资源提取及最终报告也保存在同一目录。

## 尚未完成

未满条件与普通／TPS 场景容量已在[补给条件记录](TPS_SUPPLY_CONDITIONS_2026-10-15.md)中接入。
弹药包的全局值分发及实际父动作已在[分发记录](GLOBAL_VALUE_DISPATCH_2026-10-15.md)中接入。
`TPSAmmoBoxDispatch` 的指定／自适应分配与移除回调已在
[弹药箱分配记录](TPS_AMMO_BOX_DISPATCH_2026-10-16.md)中接入；
场域、交互、消耗／刷新、跨进程领取事务和完整拾取生命周期仍需继续接入。
1004 池、非明确池和额外补给倍率仍缺少足够配对证据。
完整 TPS 模式、容量、伤害、弹道、客户端真实射击／换弹／拾取及多人验收仍待完成。
本次没有替换日常服务 JAR，也没有据此标记整体模式完成。
