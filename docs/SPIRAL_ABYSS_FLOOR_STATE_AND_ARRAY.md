# 入层恢复与恒常机关阵列阶段状态（2026-10-22）

用户在原生客户端确认第九层连续通关并进入后续楼层后，反馈新楼层未恢复角色状态，以及 12-1 恒常机关阵列分裂后缺少小怪、阶段结束仍锁血。

## 新楼层的角色状态

原 `tryEnterLevel` 直接捕获当前生命和能量作为重试检查点，没有新楼层的恢复步骤。现在仅在第一间且本轮尚无检查点时准备完整双队恢复状态，在场景交接成功后恢复生命、元素／特殊能量、清除生命之契及选中角色的普通／共享技能冷却，再捕获检查点。

取消入场不恢复属性；同层第二、三间继续保留上一间的消耗；重试仍恢复该间入口检查点。未选角色和无关共享冷却不受影响。此处没有实现所有持久增益或本轮暂离恢复。

`TowerChamberRetryTest` 新增整层入口、取消入场、继续下一间三项用例。旧构建在完整双队恢复用例失败；实际场景交接测试同时覆盖死亡角色恢复、特殊能量、下半队伍冷却以及重试检查点。

## 阵列的阶段附加状态

实际能力资源 `Monster_Apparatus_Perpetual_ShaderState` 的 `Apparatus_Perpetual_LockHP` 在进入分裂阶段时通过 `AttachModifier` 附加 `LockHP`。旧实现仅注册子状态，未建立与父状态的生命周期关系，父阶段移除后仍锁血。

`AttachModifier` 现在把回调创建的子状态绑定到当前父状态。父状态结束时只移除其拥有的子实例；客户端确认子实例时保留同一绑定，重复的旧移除不能清除新一轮阶段。`ApplyModifier` 的独立生命周期未改变。

`PerpetualArrayPhaseTest` 直接读取本机 BOSS 能力资源，旧构建复现父状态移除后仍锁血；覆盖有／无子实例客户端确认及下一轮阶段。

## 召唤身份

当前资源导出的 `PCLFAKBGHCI` 对应 `summonTag`，原解码器遗漏该字段。阵列的召唤配置与怪物 `combat.summon.summonTags` 使用相同的八个标记：四种机兵各有普通和强化版本；例如 `24020102`／`24020103`。沙漠召唤者的该字段为 `1`，与 monsterID 不同，不能简单用怪物 ID 代替标记。

解码器恢复该别名，四个召唤实体各自保留资源标记、BOSS 主人和当前等级。资源回归测试在旧构建复现标记统一变成 0、主人映射相互覆盖。

服务端测试不代表真实客户端的召唤消息、坐标和整场 BOSS 战已经验收。阵列阶段请求和召唤结果加入有限日志，便于核对真实消息；缺少可解析出生坐标时记录失败原因，不在原点伪造召唤。

## 验证与部署

共 88 套件、2,205 项相关回归通过，无失败、错误或跳过。首次扩大回归中已有 `AbilityEventMixinTest` 使用 `/resources` 绝对路径，在 Windows 下找不到文件；改为项目相对路径并复测该套件后通过，其他套件结果沿用同次运行。

便携部署于 2026-10-22 04:30（Australia/Sydney）完成，健康接口、UDP 22101 及运行 JAR 与本次构建一致性均通过；SHA-256：`70b7dd8e58b258a8c8db6fba14ccee8d8e13f985175cab7150f4662a6dfe6a88`。机器可读结果见[本次验证](data/tower-floor-array-validation-2026-10-22.json)。

新楼层两队恢复尚未收到明确实测反馈。2026-10-22 晚间用户确认阵列问题依旧存在，下面记录进一步定位；此前测试与部署通过不能视为 BOSS 实战通过。

## 原生召唤与附加状态通知修复

22:59:05 实机记录四次 `ABILITY_ACTION_SUMMON=53`，动作 localId 分别为 `33315`、`33347`、`98899`、`33411`，每次 payload 为 24 字节。服务器均报告 `hasPos=false / hasRot=false`，所以没有创建小怪。此前测试用服务器自己的 protobuf builder 生成输入，未发现双方字段号不一致。

按[本机 7.1 固定指纹](SPIRAL_ABYSS_ENTRY_PROTOCOL_71.md)只读核对原生代码：

- 原生发送函数 `KHPDOPHGNHN.OHHDEHLCKJF`（`0x14c1657c0`）调用 `Miscs.GetPositionByBornType`，将位置写入 `GEPFCBAPAMC +0x18`，将旋转转换后的角度写入 `+0x20`，以参数编号 `0x35` 发送；另一召唤路径 `0x14c16e9f0` 使用同一消息。
- `GEPFCBAPAMC` writer `0x1535fd5d0` 写入 tag `0x0a` 和 `0x22`；reader `0x1535fd9b0` 对应读取。故 `AbilityActionSummon.pos=1`、`rot=4`，修正原错误字段 11／10。
- `AbilityAttachedModifier` 的原生 reader `PAPBNKBPCJK.NLGBJEEJDLG`（`0x147c1a320`）确认现有字段号 1、2、3、5、6；缺口是业务层未消费 `attached_instanced_modifier`。原生新增消息常只给根能力编号和本地状态索引，不重复能力名，不能依赖 `fromParentName` 触发服务端附加操作。

新处理在客户端报告有效父实例时，绑定同一实体、同一能力且资源明示的直接 `AttachModifier` 关系。父实例结束时清理对应子实例；无效引用、非普通来源或独立 `ApplyModifier` 不建立这一关系。跨实体和任意嵌套附加关系不包含在本次扩展中。

回归输入现在使用 `CodedOutputStream` 独立写原生字段，经过 `AbilityManager.onAbilityInvoke` 和四个实际动作 localId 验证召唤坐标、旋转、主人、等级和标记；另用不携带能力名的原生父子通知验证锁血解除。修复前两个用例失败，修复后通过。实机保护状态与分裂计数也记录到日志，用于继续核对阶段完成后的受伤能力。

本次 88 套件、2,207 项相关回归全部通过，无失败、错误或跳过。23:13（Australia/Sydney）已便携部署，HTTP 健康、UDP 端口及运行 JAR SHA-256 核对通过：`2062269019c7855412f7815f540e7fe82a8e1d9eb01bdaa39eaa3c872c787056`。[本次验证记录](data/tower-array-native-validation-2026-10-22.json)单独保留，未覆盖早先构建的证据。2026-10-23 用户实测确认小怪出现，但击破小怪后 BOSS 仍不掉血，后续定位如下。

## 同名状态遮蔽导致半秒锁血永久残留（2026-10-23）

00:16:34 的实机日志确认四次召唤成功，包括强化机兵 `24020303`。00:16:50 已收到 `WeakStart`、分裂子状态和父状态的移除及 `Apparatus_Perpetual_Weak` 的新增；`_TRIGGER_TIMES=1`、`Limbo=false`、`Invincible=false`，但 `LockHP=true` 仍然残留。因此不能把这次不掉血继续归因于没有召唤或没有收到虚弱阶段。

实际顺序中，客户端先报告 `Split_Temp_LockHP`，随后 `FirstSplit_Normal` 的移除回调在服务端创建同名的半秒状态。属性聚合 `activeControllers()` 对同名状态去重，客户端实例暂时遮蔽服务端实例。旧定时器错误地把这种遮蔽当成状态已注销，直接丢弃计时；客户端实例稍后移除，仍在注册表中的服务端实例便永久锁血。

修复让定时器存活判断及到期删除使用完整注册集合 `lifecycleControllers()`。同名属性仍按原规则去重，客户端实例仍由客户端移除；被遮蔽的服务端状态按资源时长到期，只清理对应实例。没有按 BOSS ID 强行解除保护或改写阶段逻辑。

`PerpetualArrayPhaseTest` 按上述实机顺序重放初始血量下限、半秒保护、首次分裂与虚弱通知，并使用可控时钟覆盖客户端实例遮蔽期间发生／不发生 tick 两种顺序。原生父引用保留本次实际观察到的 `ownerEntityId=0`，子实例按实际顺序显式移除。`AbilityModifierDurationTest` 另覆盖遮蔽期间到期与客户端先移除两种情况，检查属性去重、只清理服务端实例及结束回调仅一次。修复前共三项失败。新增伤害断言确认正常分裂期间不扣血、虚弱期间扣血。

阶段日志增加仍注册的保护状态名称，便于实机直接区分 `LockHP`、`Split_Temp_LockHP` 与初始血量下限。真实客户端击破机兵后的扣血验收仍需本次新构建复测。

本次 88 套件、2,211 项回归全部通过，无失败、错误或跳过。00:32（Australia/Sydney）已完成便携重建与重启；HTTP 健康、UDP 22101 及运行 JAR SHA-256 核对通过，三个改动类的 JAR 字节码也与本次编译产物一致。JAR SHA-256：`9d60d44244ff6e76be2e93ec0b50a1351ce2f2fdffa4b7ff43dbeb2f3fb0a6a4`；详见[本次验证记录](data/tower-array-duration-validation-2026-10-23.json)。
