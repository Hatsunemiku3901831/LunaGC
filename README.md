# LunaGC 7.1.0

外部项目的适配线索、已核对的本地缺口与后续验证清单见 [2026-10-03 外部实现调研](docs/UPSTREAM_RESEARCH_2026-10-03.md)。
最新完成项、已有替代入口及仍待适配的范围见 [参考差异当前状态](docs/REFERENCE_REMAINING_2026-10-15.md)。
协议与资源分析可使用[固定客户端字符串提取器](docs/CLIENT_LITERAL_EXTRACTION_2026-10-15.md)，支持编号查询、文本筛选及哈希校验。

GM／控制台的 `build` 命令支持大世界持久放置、临时放置、查询和立即删除，
用法和保存范围见[大世界持久建造](docs/SCENE_BUILDING_2026-10-15.md)。

`dps` 查询当前世界的角色伤害、承伤和秒伤，`dps reset` 开始新的统计；
可从 GM 命令目录使用，详见[战斗伤害统计](docs/DAMAGE_LOG_2026-10-15.md)。

`trackmat` 支持材料 ID／完整名称的采集点标记与清理，自动避让个人标记；
默认包含 20 种材料、560 个点，见[材料采集点标记](docs/MATERIAL_TRACKING_2026-10-15.md)。

尘歌壶与「烟花筒」已按当前协议和资源接入放置、回收、独立冷却及联机归属，
实现和客户端验收范围见[小道具放置](docs/WIDGET_PLACEMENT_2026-10-15.md)。

资源物品的共享冷却支持当前客户端通知及登录同步，营养袋沿用普通食用链路；
实现和实机验收范围见[物品冷却](docs/ITEM_USE_COOLDOWNS_2026-10-15.md)。
食物先确认库存扣除再执行效果，明确未生效时退回原堆叠，异常时保留记录防止重放，
见[食物付款与退款](docs/FOOD_PAYMENT_2026-10-15.md)。
食物故障记录可通过 `itemuse` 核对、退回库存成本或明确接受当前消耗，
见[库存成本结算](docs/ITEM_USE_RECOVERY_2026-10-15.md)。
特殊食物支持嵌套恢复、子效果独立计时与原生回血去重，见[嵌套食物配方](docs/NESTED_FOOD_EFFECTS_2026-10-15.md)。
条件食物按接收者的资源特征标签选择普通或增强回复，见[条件食物恢复](docs/CONDITIONAL_FOOD_HEALING_2026-10-15.md)。

`sound list [filter] [--page N]` 查询本次运行观察到的音效，每页 20 条；控制台支持实时 Tab 补全。
播放使用 `sound @UID <name> [x y z]`，可用 `audio` 别名，见[音效目录与补全](docs/OBSERVED_SOUND_CATALOG_2026-10-15.md)。

七天神像供奉支持一次跨级、保留部分进度，并按实际库存扣除、按资源表发奖；
供奉记录持久化，异常时阻止自动重复发奖，见[供奉升级链路](docs/STATUE_OFFERING_2026-10-15.md)。
新供奉支持按操作 ID 只读核对、恢复及取消未扣费预留，见[中断供奉恢复](docs/STATUE_OFFERING_RECOVERY_2026-10-15.md)。
`statue`／`sots` 支持神像查询、入口锁定／解锁及管理调级／重置，
用法和异常供奉保护见[神像管理命令](docs/STATUE_MANAGEMENT_2026-10-15.md)。
普通请求与任务解锁统一按资源区分神像和锚点，见[点位与奖励](docs/STATUE_POINT_UNLOCK_2026-10-15.md)。
神像奖励支持可选参考增强、逐地区物品倍率和等级间均分，默认保留资源奖励，
配置见[神像奖励倍率](docs/STATUE_REWARD_POLICY_2026-10-15.md)。
神像回复通过玩家 tick 检测已解锁入口的距离，离开或退出后停止，
保留玩家回复设置，默认按实际治疗量结算泉水，并支持可选免消耗模式，见[神像回复](docs/STATUE_RECOVERY_2026-10-15.md)。
客户端设置游戏时间已接入世界时间锁、房主保存、联机同步和等待时间任务，见[时间请求](docs/CLIENT_GAME_TIME_2026-10-15.md)。
圣言自明机已接入萃取、定向制作、套装次数、所选属性强化约束、存档及重塑空快照的登录同步；完整重塑及客户端验收的剩余范围见[适配记录](docs/ARTIFACT_TRANSMUTER_2026-10-15.md)。

## 保存队列与运行状态

数据库异步保存分为常规、账号、物品和场景四类有界队列，避免物品批量写入阻塞账号保存。
队列满时由提交线程执行，同一对象向同一数据库重复排队时会合并；同步经济写入仍等待 MongoDB 确认。
保存、重试与内置删除操作保留提交时选择的数据库，避免排队期间切换连接或运行模式后写入另一处。
正常停机等待四类队列及提交线程中的写入完成，超时会明确记录尚未完成的数量。

在 GM 顶部点击「运行状态」，可查看队列积压、失败次数、合并次数、逻辑循环耗时和 JVM 死锁线程数。
`/status/server` 发现逻辑循环停滞、死锁或队列积压达到 80% 时返回 HTTP 503，Docker Compose 使用该检查。
默认停机排空时间为 30 秒，Compose 预留 60 秒。队列参数位于 `databaseInfo.asyncWrites`，修改后重启生效。
参数、来源和验证范围见 [外部实现适配记录](docs/UPSTREAM_INTEGRATION_2026-10-08.md)。
双 Mongo 实例验证及连接切换边界见 [数据库写入目标](docs/DATABASE_DATASTORE_SCOPE_2026-10-09.md)。

## 资源加载与诊断

当前客户端的热修复流可通过补丁中的可选入口导出，默认关闭，且要求客户端哈希和加载函数签名一致。
启动方式、文件上限和实际验证范围见 [IFix 诊断](docs/IFIX_CAPTURE_2026-10-15.md)。

资源表中的单条类型或初始化异常不会中断后续有效数据，同类其余文件仍会尝试加载。
文件错误和被拒绝的行会明确记录；空配置不会覆盖已有有效缓存，重载不会误报 ID 聚合。
机关与场景能力配置保留空数组和键值数组格式。来源、异常行为与真实资源核对见
[资源加载记录](docs/RESOURCE_LOADING_2026-10-08.md)。

技能数值表达式检查操作数、除零和无效数值，异常时使用调用方默认值。
已逐一核对当前资源中的 7,745 个后缀算术表达式。
范围与验证结果见 [技能数值表达式记录](docs/ABILITY_EXPRESSIONS_2026-10-08.md)。

天赋参数按客户端的计算顺序合并增量与比例，支持数字、参数引用和旧缓存。
装备或命座变化时从资源基础值重新计算；范围及待接入项见
[天赋参数计算](docs/TALENT_PARAMETER_MATH_2026-10-09.md)。

普通技能冷却保留此次释放倍率与恢复队列，技能次数更新和切场景同步携带剩余时长。
已接入的九条天赋配置及未完成的动作分支见
[普通技能冷却](docs/ORDINARY_SKILL_COOLDOWNS_2026-10-09.md)。

技能参数使用基础天赋等级与对应命座加成，天赋升级后刷新已有角色和装备技能。
协议继续分别显示基础与额外等级，验证范围见 [技能等级与运行参数](docs/SKILL_LEVELS_2026-10-09.md)。

达达利亚与丝柯克的固有天赋可按当前队伍提供技能等级加 1，换队后清除相应贡献。
加成不写入基础等级存档，条件、通知及实机验收边界见 [队伍技能等级加成](docs/TEAM_SKILL_LEVELS_2026-10-09.md)。

天赋配置保留实际增级值和多个技能位置，技能次数加成支持累加与命座重置恢复。
启动时优先读取当前四类天赋源目录，并兼容旧缓存；范围见 [天赋配置与资源优先级](docs/OPEN_CONFIG_2026-10-09.md)。

队伍共鸣读取当前资源的实际参数，换队或旅行者切换元素后更新技能并清理失效属性。
真实资源、联机阵容检查与实机验证边界见 [队伍共鸣适配](docs/TEAM_RESONANCE_2026-10-09.md)。

召唤物回调支持 `CasterOriginOwner`，切人后继续作用于原施法角色，并检查实体生命周期。
当前接入范围见 [召唤物与施法者目标](docs/ABILITY_CASTER_TARGET_2026-10-09.md)。
命名位置快照与召唤出生位置已接通确定的坐标规则，缺失向量不会直接使用原点；
资源覆盖与地面、挂点等待接入范围见 [技能出生位置](docs/ABILITY_SPAWN_POSITIONS_2026-10-14.md)。
初始化、治疗和附加状态回调统一选择目标，全队附加效果每个成员只分发一次；
范围见 [技能回调目标与执行顺序](docs/ABILITY_CALLBACK_ROUTING_2026-10-09.md)。
技能属性缓存支持资源指定来源、召唤物施法者及全队合计，范围见
[技能战斗属性读取](docs/ABILITY_FIGHT_PROPERTY_READS_2026-10-09.md)。
具体实体类型条件与单项类型优先级支持受控治疗序列，范围见
[技能实体类型条件与五郎治疗链](docs/ABILITY_ENTITY_TYPE_CONDITIONS_2026-10-09.md)。
滑翔挑战、剧情与千星奇域武器的临时技能信息可以同步和清理，并保留命座次数；
范围见 [临时技能信息同步](docs/TEMPORARY_SKILL_INFORMATION_2026-10-09.md)。
共享组关联、槽位、次数恢复和当前协议证据见
[共享技能冷却](docs/SHARED_SKILL_COOLDOWNS_2026-10-09.md)。
玩家时间、暂停、换场景保留和实时活动技能的后续纠正见
[共享冷却时钟](docs/SHARED_COOLDOWN_CLOCKS_2026-10-09.md)。
技能就绪条件按当前有效的技能槽位及优先级查询恢复队列，移除切换来源后恢复剩余配置；
范围与原生证据见 [动态技能槽位](docs/DYNAMIC_SKILL_SLOTS_2026-10-13.md)。
角色模式记录区分进入与退出，体力扣除读取当前角色有效的资源减免效果；
来源、公式推断和实机验证范围见 [角色模式与体力消耗](docs/PLAY_MODES_AND_STAMINA_2026-10-09.md)。

元素反应、自身元素反应与全局值变化的客户端回调可执行资源中的动作队列。
动作按顺序处理，保留请求目标，并检查实体生命周期、条件及 Self／Caster／Team 目标。
接入范围和实机验证边界见 [技能反应回调](docs/ABILITY_REACTION_CALLBACKS_2026-10-08.md)。

已登记的无敌、锁血和濒死保护修饰器参与伤害处理，移除或替换后按剩余实例重新判定。
濒死保护阈值读取所属技能的当前参数，缺少阈值时仍防止死亡；初始保护遵守条件和目标。
具体规则及尚未接入的定时、其他保护状态见 [战斗保护状态](docs/ABILITY_COMBAT_STATES_2026-10-08.md)。

生命值比例修饰器支持叠加、移除及技能参数更新，角色和怪物重算属性后重新应用一次。
增减时保留当前血量比例和生命之契绝对值；客户端确认不会重复计入服务端请求的加成。
公式依据、资源核对和实机验证边界见 [生命值加成](docs/ABILITY_HP_MODIFIERS_2026-10-08.md)。
生命值修饰器可读取承载实体的 SGV／GV；Lua 写入保留小数并刷新属性，客户端哈希键按实体解析。
具体入口、六处活动资源绑定及验证范围见 [实体全局值](docs/ABILITY_GLOBAL_VALUES_2026-10-09.md)。
修饰器已接入上游的 37 种战斗属性映射，包含生命值比例、攻击、防御、精通、暴击、充能及伤害加减。
普通属性与额外属性分两步计算，登记、参数更新及移除合并发送一次属性通知；
移除巨大但有限的加成时保留原始基值，未知属性继续保留为待办。
资源核对、已验证范围及客户端公式边界见 [修饰器战斗属性](docs/ABILITY_PROPERTIES_2026-10-09.md)。
三类全局值区间监听可按档位附着／释放加成，支持跨实体更新、父状态清理和客户端确认去重。
Self 首次初始化缺失变量，NoInit 只读取变量；完整周期动作与实机语义仍待完善，范围见 [全局值监听](docs/ABILITY_GLOBAL_VALUE_WATCHERS_2026-10-09.md)。
能力状态条件可按任一状态匹配或反向条件附加子修饰器，状态与父效果退出时释放绑定创建的实例。
实际资源、支持范围与验证记录见 [能力状态绑定](docs/ABILITY_STATE_BINDINGS_2026-10-14.md)。

已支持的治疗、数值更新、条件分支和状态创建／移除可按资源间隔顺序执行，支持角色切换及离场停止；
服务端创建的子状态保留初始化与结束动作归属，客户端逐个确认多实例，避免重复执行和相互清除。
治疗加成读取施法者，受治疗加成读取目标。参数晚到可启动原有计时，重新施放使用新起点；完整施法仍待验证，
范围见 [周期治疗](docs/ABILITY_PERIODIC_HEALING_2026-10-09.md)。
服务端临时状态按资源时间结束并撤销加成，结束动作可按条件分发到施法者或全队、创建后续状态。
客户端确认后沿用客户端移除链；验证与边界见 [状态持续时间](docs/ABILITY_MODIFIER_DURATION_2026-10-09.md)。
刷新型多实例支持资源上限，满层刷新最早一层并保留周期起点，旧计时器不会清除刷新后的实例；
其他刷新／耐久度模式和完整客户端流程仍待适配，见 [堆叠与刷新](docs/ABILITY_MODIFIER_STACKING_2026-10-09.md)。
普通技能返能、元素球与直接补能限制到实际上下限，变化通知发送本次增减量；
固定返能保留原数值，元素球仍受充能效率影响。已验证范围见
[普通元素能量](docs/ABILITY_ELEMENTAL_ENERGY_2026-10-09.md)。
可识别的绫人、多莉和火主周期返能已接入；队伍切人保留后台能力计时，
按资源的后台标志执行动作，到期、退队与切场景仍停止旧效果。范围与剩余条件见
[周期返能与后台计时](docs/ABILITY_PERIODIC_ENERGY_2026-10-09.md)。
技能参数动作写入后刷新依赖状态和待启动计时器，指定技能时更新接收目标已有实例；
合法零值与无效公式分开处理。随机参数仍由客户端提供，接入范围与验证结果见
[技能参数写入](docs/ABILITY_OVERRIDE_MAP_2026-10-09.md)。
全局值写入、累加与复制遵守资源条件，拒绝无效公式和溢出；相同值不重复发送通知。
无公式的全局值转技能参数已接入可识别的周期链，随机配置仍需继续还原，范围见
[全局值与参数传递](docs/ABILITY_GLOBAL_PARAMETER_WRITES_2026-10-09.md)。
随机分支检查条件与概率，子动作按自身配置选择接收目标；完整可识别的随机树可参与
周期和状态回调，客户端补发不会重复结算。范围与未实现部分见
[随机分支与回调目标](docs/ABILITY_RANDOM_BRANCHES_2026-10-09.md)。
重复动作逐轮执行有序子动作，清除全局值遵守条件和通知规则；嵌套回调共享执行额度，
避免异常配置长时间占用服务。资源用例与尚未验收部分见
[重复动作与全局值清除](docs/ABILITY_REPEATED_ACTIONS_2026-10-09.md)。
当前恰斯卡／流浪者的单组随机队列先筛选条件，再执行选中组的有序子动作；
完整可识别的组登记归属并参与计时回调。多次抽选与客户端一致性仍待验证，见
[条件随机队列](docs/ABILITY_RANDOM_QUEUES_2026-10-09.md)。
基础技能目录优先读取当前角色配置，旧缓存作为缺失资源时的回退；
旅行者元素技能组独立加载。更新外部资源后需重启服务，验证范围见
[基础技能目录与资源更新](docs/ABILITY_EMBRYO_REFRESH_2026-10-09.md)。

## 场景切换与 Lua 时间轴

玩家进入场景前先退出旧场景，同一玩家的场景进入／移除串行执行，避免互换场景时等待彼此的锁。
实体编号使用世界内的原子计数，场景内创建实体不获取世界监视器。
来源、并发验证与剩余边界见 [场景切换记录](docs/SCENE_TRANSFERS_2026-10-08.md)。

世界和尘歌壶成员迁移共用场景切换锁，房主退出在锁外迁移访客，旧世界不能清除新归属。
尘歌壶为房主保留编号 1，访客编号不会因房主进入或其他访客离开而重合。
完整传送的坐标与通知按顺序执行；来源与目标的异常成员登记可修复。
验证范围和后续工作见 [世界成员迁移记录](docs/WORLD_MEMBERSHIP_2026-10-08.md)。

联机、尘歌壶和原素烘炉匹配检查进入结果，再修改位置和发送成功通知。
房主退出时逐个返回访客，临时失败按秒重试；缺失旧场景时尝试个人大世界。
空世界退役与成员登记协调，进入中和待恢复的玩家不会因空列表被提前清理。
接口、异常边界与验证结果见 [世界进入与恢复记录](docs/WORLD_ENTRY_2026-10-08.md)。

新世界在首次入场预检后才加入 tick；排队技能检查实体生命周期与施法者场景。
触发技能保留施法者归属，复用实例并执行每次开始动作。
具体边界和资源验证见 [技能生命周期记录](docs/ABILITY_LIFECYCLE_2026-10-08.md)。

`InitTimeAxis` 按秒处理全部时间点，支持小数秒；`EVENT_TIME_AXIS_PASS` 提供时间点序号和 `source_name`。
Lua 直接接收原始数组并检查数值与连续索引，避免数值重载把小数截断；Java 数组调用入口保留。
同名时间轴按场景组隔离，替换、停止、组卸载和场景销毁会取消旧任务。
共享定时线程与 Lua 执行线程分开，慢回调保留事件顺序。
资源核对、清理边界与验证结果见 [场景时间轴记录](docs/SCENE_TIME_AXES_2026-10-08.md)。

## TPS 开发支持

已接入 TPS 武器、配件与弹药资源、装备协议、库存保存和场景实体清理。
GM 物品目录可筛选 TPS 武器；开发命令支持发放、穿戴、解锁配件和补充弹药。
服务端已接入资源指定场景的专用旅行者和进入／退出队伍恢复，保留任务试用角色。
武器、配件与套装参数按实际装备生效，配件解锁按能力分别判断，装备变化后刷新能力参数。
换装重建的武器能力绑定实际穿戴者；弹药元消息按类型处理，只有通过校验的更新会转发。
游戏内入口、射击与结算仍需继续接入和验证，尚未部署到日常服务。
命令、协议证据、已知资源缺口与验证范围见
[TPS 基础适配记录](docs/TPS_COMPATIBILITY_2026-10-08.md)和
[场景队伍适配记录](docs/TPS_SCENE_LIFECYCLE_2026-10-08.md)，以及
[词条参数与解锁记录](docs/TPS_TALENTS_2026-10-08.md)。当前 TPS 词条的资源参数均为零，实际伤害与射速仍待核对。
接包流程、真实冰枪资源验证与能力副本对比见 [TPS 运行记录](docs/TPS_RUNTIME_2026-10-08.md)。
进入场景和补满后的同步已使用完整弹药总量快照，保留未穿戴武器及零数量的池；
原生字段、测试和弹匣的剩余范围见 [TPS 弹药总量](docs/TPS_AMMUNITION_TOTALS_2026-10-15.md)。
射击／换弹回调现已保存弹匣快照并在进场恢复，未知弹匣交由客户端资源初始化；
字段、归属校验、Mongo 保存及实机验收范围见 [TPS 弹匣](docs/TPS_MAGAZINES_2026-10-15.md)。
明确弹药池的资源补给已接入实际增量和榴弹回调去重；弹药箱父级分发与完整拾取仍待接入，
见 [TPS 资源补给](docs/TPS_AMMUNITION_SUPPLY_2026-10-15.md)。
未满条件现会拒绝满池领取，并与实际发放共用普通／TPS 场景容量，
见 [TPS 补给条件](docs/TPS_SUPPLY_CONDITIONS_2026-10-15.md)。
弹药包的全局值分发父动作已串通到补给与领取标记，包含默认／空分支、周期重算和确认去重，
见 [全局值分发记录](docs/GLOBAL_VALUE_DISPATCH_2026-10-15.md)。
弹药箱已接入指定／自适应轮流分配、移除发奖、重复确认与存档异常防重放，
见 [弹药箱分配](docs/TPS_AMMO_BOX_DISPATCH_2026-10-16.md)；场域、交互与完整拾取仍待完成。
12 个配件支持按背包解锁材料自动生效、移除和登录恢复，保留 GM 手动授予，见
[配件材料解锁记录](docs/TPS_ACCESSORIES_2026-10-08.md)。

## 千星资产归档

GM「千星资产」支持导入 GIA／GIL／GIP／GIR 与节点图 JSON、分页检索及原文件导出。
资产按 SHA-256 去重，保留未知字段；校验容器边界、节点引用和 GUID 冲突。
展开「生成实体 / 像素画 / 镜头 / 体素 .gia」可将实体 JSON 或 PNG／JPEG／BMP／GIF 转为 GIA 并归档。
像素画匹配 21 种方块，支持透明度、比例、平面和原点；自动 GUID 避开已归档的主资产 ID。
单文件最多 100000 项资产，列表仅保留前 100 项元数据预览。生成参数与限制见
[千星资产生成](docs/MILIASTRA_GENERATION_2026-10-08.md)。
选择「镜头 JSON」可填写五种镜头模板，修改视距、视野、偏移、角度和碰撞参数，
也可批量生成水平角、俯仰角、视距与视野变体；自动编号同时避开已解析的镜头内部编号和依赖引用。
生成文件与当前 7.1 客户端导入效果的验证范围见 [镜头生成](docs/MILIASTRA_CAMERA_2026-10-08.md)。
已有镜头 GIA 可「提取镜头 JSON」，在 GM 中编辑配置并转回 GIA；未编辑时恢复原字节，
编辑后保留编号、依赖、未知字段和其他资产，见 [镜头提取与编辑](docs/MILIASTRA_CAMERA_EDIT_2026-10-08.md)。
选择「体素 JSON」可读取 Blender 导出的坐标／颜色数组，设置边长、原点和透明度后生成 GIA。
项目提供 `tools/blender_miliastra_voxels.py`，使用本机已有 Blender，无需服务端安装；支持选中网格、
修改器、世界坐标缩放和 UV 贴图。完整流程与边界见 [Blender 体素生成](docs/MILIASTRA_VOXEL_2026-10-08.md)。
节点 GIA 还可转为 `.gia.json` 结构文件，保留输入值、连接、复合定义及未知字段；
编辑后重新导入并转回 GIA，未修改时恢复原始字节，见
[节点结构转换](docs/MILIASTRA_NODES_2026-10-08.md)。
编辑器画布 JSON 可转为 GIA，保留已映射的流程／数据连接、常量、泛型、分支与注释；
未映射内容显示原因，原文件继续保留，见 [画布转换](docs/MILIASTRA_CANVAS_2026-10-08.md)。
普通节点 GIA 或结构 JSON 可「转为编辑器画布 JSON」；未编辑时还原原字节，
编辑后保留原 GUID、节点索引、变量与未知字段，见 [反向画布转换](docs/MILIASTRA_REVERSE_2026-10-08.md)。
GIA 与节点结构 JSON 可「复制并重新编号」，联动更新实体、镜头、普通节点图的已知 GUID 引用，
支持自动避让、指定起点及重复去重；未知引用与复杂资产的限制见
[GUID 复制](docs/MILIASTRA_GUID_2026-10-08.md)。
游戏内发布、节点执行和奖励链路仍待接入。来源、存储位置与接口见
[千星资产记录](docs/MILIASTRA_ASSETS_2026-10-08.md)。

## GM 物品与装备

物品名称读取中文主文本表及 `TextMap_MediumCHS.json`，支持分片文本表与已知的 ±512 索引偏移。
升级会重建旧名称缓存；资源确实缺少文本的条目显示「名称待确认（物品 ID）」，可搜索「名称待确认」筛出，不将索引或猜测当成正式名称。

武器与圣遗物使用「配置并给予」窗口，目标玩家须在线，每次最多发放 100 件：

- 武器：等级、突破阶段、精炼等级按资源限制；可在突破临界等级选择突破前或突破后。基础攻击力和副属性由武器、等级与突破自动计算。
- 圣遗物：等级使用游戏中的 +0～上限（五星通常为 +20），可选该部位的主属性，以及最多四种副属性的单次数值与总次数；未指定部分按资源权重随机补齐。
- 星级、套装、部位和初始副属性数量由物品 ID 决定，窗口展示这些信息。主副属性冲突、重复属性、错误属性库及超出当前等级次数的配置会被拒绝。
- 发放前检查背包容量，成功结果须确认装备保存。连接或保存异常时先核对背包，避免重复发放。

## 游戏内武器强化

已补齐 7.1 武器强化与溢出矿石预览的请求包号和材料字段。支持使用强化矿石、消耗未装备且未锁定的武器，
扣除摩拉并提升等级；达到当前突破阶段的等级上限后，溢出经验按资源换回矿石。预览不会扣除材料。
协议依据和测试范围见 [武器强化协议记录](docs/protocol-weapon-upgrade-7.1.md)。

## 角色 90–100 级突破

满突破、90 级的自有角色可消耗无主命星直接升到 95 级，再消耗两个升到 100 级。
等级、属性和突破材料成本读取资源；试用角色不能使用，重复请求不会再次扣除材料。
满级时使用经验书会返回等级上限错误，不消耗经验书或摩拉。

重复抽取满命五星角色会赠送一个无主命星及原有 25 星辉；十连按每次发放后的命座状态计算转换。
服务端请求、结算及保存读回已有测试，游戏内突破与重登显示还需实机验收。
来源、协议字段与验证边界见 [角色突破记录](docs/protocol-avatar-extra-level-7.1.md)。

## 公告与游戏邮件

[GM 控制台](http://localhost/gm) 新增「公告」和「邮件」：

- 公告支持草稿、编辑、定时发布、撤下、删除和分页。点击「查看游戏公告页」可预览当前生效内容。
  新公告正文按普通文字显示，国服与海外服使用同一份目录。
- 邮件支持向单个或多个 UID 发送，最多一次 100 个，离线玩家也可收到。可设置发件人、标题、正文、
  1–365 天有效期和最多 10 种附件；附件物品 ID 可在「物品」页查询。
- 发送界面保留本次批次编号。网络失败或部分投递失败后点击重试，会跳过已经投递的收件人。
  点击「新建邮件」才开始下一批。发送前会检查所有 UID 与附件；不存在的 UID 不会创建账号。
- 「查看玩家邮箱」可分页检查正文、到期时间、已读和附件领取状态。读信、星标、领取与删除已接入 7.1；
  未领取附件的有效邮件不能删除。旧邮件在读取时自动获得稳定编号，删除其他邮件不会改变它。

公告保存在运行数据目录的 `data/Notices.json`，Docker 使用持久化状态卷；第一次使用时会导入已有的
`GameAnnouncement.json` 正文（旧 HTML 转为文字），文件不存在时提供欢迎公告。
邮件、发送回执和编号序列保存在 MongoDB，重启后保留。部署和迁移时须同时备份状态卷与 MongoDB。

游戏公告页 `/hk4e/announcement/index.html` 直接随服务端打包，不依赖官方 CDN；
国服与海外服公告接口均已注册，加载失败会提示重试而不无限等待。
若旧客户端补丁打开公告后持续转圈，更新补丁并完全退出游戏后再执行 `task patch`。
新版补丁保留公告窗口的原始公钥，仅替换短登录数据所用的 1024 位 SDK 密钥，避免公告 Cookie 加密超长。
邮件附件领取先保存预约状态再发物品；若进程在发放中途退出，预约会阻止自动重复发放，
GM 邮箱会显示「附件领取中断」，需要核对数据库与日志后恢复，不能直接清除预约再次领取。
7.1 协议依据及实机验证边界见 [公告与邮件协议记录](docs/protocol-mail-notices-7.1.md)。

## 商城与免费充值商品

[GM 控制台](http://localhost/gm) 的「商城」按游戏入口划分商品目录：

- 推荐礼包、礼包商城、尘辉兑换、角色装扮、凝取结晶；尘辉兑换可切换星辉、星尘和原石购买。
- 千星奇域的推荐、服饰、面部装饰、兑换、事象凝核与复刻。
- 纪行（大月卡）、原石直购（GM）、其他商店与全部商品。

推荐礼包中的空月祝福与礼包商城共用同一商品，上架状态同步。每个目录支持搜索与状态筛选，
兑换商品支持分页、新增与编辑。商品操作分为：

- **免费充值商品**：空月祝福（小月卡）、珍珠纪行 / 珍珠之歌（大月卡）、原石与创世结晶。
  现金价格统一为 **0**；填写顶部在线玩家 UID 后点击「免费购买」即可发放。
  月卡按资源配置提供立即结晶和每日原石，剩余天数最多 180 天，按上海时间 04:00 换日。
  在线跨日也会检查日奖励，发放失败可重试；初次登录保留原领取顺序，见 [日界线与在线刷新](docs/DAILY_REFRESH_2026-10-09.md)。
  纪行使用本期任务和等级奖励；珍珠之歌的等级与额外奖励读取当前资源，同档位不能重复领取。
  结晶礼包保留资源中的首次 / 后续额外数量，直接购买原石则发放标注数量。
- **兑换商品**：搜索、商店筛选、分页、编辑、新增、上架、下架与恢复默认。
  可调整数量、摩拉 / 原石 / 结晶 / 材料兑换价格、限购、冒险等阶和刷新间隔。
  普通商品的游戏内兑换价格保留；需要免费时可在 GM 设置为 0。

默认商品由 `resources/ExcelBinOutput/ShopGoodsExcelConfigData.json` 与服务端
`Shop.json` 合并，再生成圣遗物商店。资源加载后会完整重载商城，7.1 的批量查询也会返回商品。
角色装扮按 `AvatarCostumeExcelConfigData.json` 和对应的解锁道具自动补齐四星、五星非默认皮肤。
当前资源共 30 款，包含旅行者、杜林和替换装扮，默认价格为 0，每款只解锁一次；
已通过活动或 GM 获得的皮肤会显示为已拥有，不会因更换商品 ID 或刷新限购再次购买。
更新资源后重载商城即可补入新增皮肤，上架状态和价格仍可在 GM「角色装扮」中管理。
GM「全部特殊外观」补齐当前 7.1 资源中可解锁的历史外观：30 套衣装、19 款风之翼、
23 款游迹、26 款武器外观、293 张名片，以及千星奇域 976 个服饰／套装商品。
衣装位于游戏的角色装扮商城，其他提瓦特外观位于礼包商城；千星商品沿用资源中的商城分类。
默认价格为 0，永久解锁、每项限购一次；已拥有的外观不因商店刷新或商品 ID 改变而重复出售。
GM 可按类型分页筛选、上架／下架、编辑，选顶部在线玩家 UID 后直接「免费解锁」。
游迹与衣装检查对应角色，武器外观检查武器类型，装备状态及武器外观所有权随账号保存。
覆盖范围以实际衣装／风之翼／游迹／武器外观及千星服饰资源为准；NPC 模型、默认角色模型、
仅有物品定义而无外观定义的占位项目不会作为衣装出售。特殊玩法制服尚无常规衣装定义，
不计入已完成的常规衣装商城。协议字段与服务端解锁已测试，游戏内新外观页面仍需实机确认。
尘辉兑换以资源表为准，角色与武器按原始轮换表在每月上海时间 1 日 04:00 更新；
旧配置中的固定替换商品不再覆盖轮换栏，过期或物品 ID 无效的商品不会显示。
推荐商品入口使用客户端的 900 编号，推荐礼包和礼包商城都返回同一张空月祝福；商品限购、等级与价格使用
已核对的 7.1 字段，货币费用不会重复显示。
尘辉兑换保留资源的冒险等阶条件，许多材料需要 10、25 或 35 级，低等级角色看到的商品会更少。
GM 商品列表标明等级要求，编辑商品的「最低冒险等阶」可改变该商品的开放条件。
GM 覆盖保存在运行数据目录的 `data/ShopOverrides.json`，Docker 使用持久化状态卷，
重启和更新资源会保留设置。「恢复默认」清除覆盖；自定义商品则可删除。
游戏内重新打开商城刷新商品；月卡和纪行的免费购买立即更新在线玩家。

游戏内空月祝福沿用客户端的「推荐商品 / 礼包」入口，创世结晶提供六档礼包；
大月卡在「纪行」入口解锁珍珠纪行或珍珠之歌，当前资源要求冒险等阶 20 才开放纪行入口。
商品使用桌面客户端 ID 和资源中的价格档位，
SDK 新旧两版价格接口均将这些档位的现金价格统一返回为 0；客户端补丁覆盖 Unity 请求和原生 SDK 的 libcurl 价格请求。
登录和刷新商城时，服务端会同步商品 ID 与价格档位目录，让客户端能够发起 SDK 商品查询。
若出现“商品信息准备中”，更新服务端和补丁，完全退出游戏后执行 `task patch`，再启动游戏刷新 SDK 商品信息。
原石的直接免费购买位于 GM 商城。

纪行任务在资源加载后重建缓存，排除已停用任务和往期任务；当前 7.1 资源提供 29 条本期任务。
已接通登录、每日委托、采矿、树脂消耗、摩拉消耗、烹饪、锻造、秘境、普通首领、
洞天宝钱、摆设制作、祈愿和五星圣遗物升级的任务进度。每日任务按上海时间 04:00 重置，
每周任务与每周经验上限在周一 04:00 重置，离线错过周一也会补做重置。
完成任务后可领取纪行经验；达到等级可领取免费 / 已解锁付费轨道的物品和自选奖励，重复请求不重复发放。
七圣召唤、部分周本 / 深境螺旋任务、周游壶灵和 7.1 活动专项任务仍需对应玩法的进度接入，
不能把任务目录完整视为所有玩法已经还原。

原生充值请求已接入，官方客户端支付 SDK 的实际 0 元购买界面仍待实机核验；
GM 免费购买可直接发放，不需要支付信息。协议依据见
[7.1 商城协议记录](docs/protocol-store-7.1.md)。

## 摆设制作协助

协助业务层已支持每日次数、累计四小时减时、单份家园文档保存和失败重试，
并通过真实 MongoDB 写入与重载测试。
进入好友的尘歌壶后可在游戏聊天中输入 `/furniture help`，按当前拜访关系进行协助；
确认保存后通知在线主人，制作状态查询显示访客资料，离线主人也能取得保存记录。
**原生协助按钮请求尚未接通**：7.1 请求配对及成功计数列表仍在核对，游戏命令入口也还待实机验收。
实现与验证范围见[摆设制作协助](docs/FURNITURE_ASSISTANCE_2026-10-14.md)。
访客校验、通知与命令入口见[访客摆设协助](docs/FURNITURE_VISITOR_ASSISTANCE_2026-10-14.md)。

## 千星奇域商店与奇域手册

千星商店使用独立目录，支持资源中当前有效的服饰、面部装饰、兑换商品和事象凝核充值商品。
推荐和复刻目录按资源中的开放时间显示；过期商品不会自动重新开放。
GM 商城显示千星商品的中文名称，支持编辑事象凝核价格，并提供六档 0 元事象凝核礼包。
游戏内充值商品仍使用客户端 SDK，实际 0 元结算界面需要实机确认，GM 免费购买可直接到账。

事象凝核等已核对的千星货币使用各自的玩家余额。服饰和套装购买后解锁到千星衣柜，
保存到 MongoDB，重新登录会再次同步；套装按资源展开为单件，已经完整拥有的商品不能重复购买。
未接入发放方式的组合礼包会在扣款前拒绝购买。衣柜穿戴、保存装扮方案仍待接入。

登录时发送资源中的 11 组奇域手册目标及已保存状态，初始目标显示未完成。
**手册任务的玩法进度、奖励领取、奇域纪行及联机奇域关卡尚未完整实现**；
显示任务目录不表示这些玩法已可游玩或领取奖励。协议依据和验证范围见
[千星奇域协议记录](docs/protocol-beyond-7.1.md)。

## GM 目录与分页

[GM 控制台](http://localhost/gm) 将当前卡池和历史归档合并到「历史卡池」。
支持按版本、角色、武器或卡池 ID 搜索，筛选当前配置、生效中或历史归档；
当前配置保留启用/禁用操作，历史记录可复刻 30 天。
「历史卡池」和「往期活动」均按单条记录分页，默认每页 20 条，可切换为 10 / 50 条，
列表上下均有首页、上一页、下一页和末页。搜索、筛选或修改每页条数后回到第一页，刷新和操作后保留当前页。

没有 UP 的星级会显示候选角色和武器；每张卡池可展开查看五星、四星、三星的完整候选名单。
名单按对应配置及其默认池解析，搜索覆盖 UP 和普通候选；启用满命过滤的卡池会说明候选随玩家持有情况变化。

历史卡池随运行包附带 1.0～7.1 共 53 个版本、293 条归档，Docker 无需 Git 也能浏览与复刻；
开发目录仍可补充 Git 提交历史。归档覆盖不代表每一期内容和时间都已核实。
7.1 首期已按[官方祈愿公告](https://genshin.hoyoverse.com/m/ja/news/detail/166228)补入：
薇斯纳（#160100）、沃雅妮莎（#160101）、蝶变与漩流颂歌武器池（#160102）；
两个人物池的四星 UP 为迪奥娜、珐露珊、重云。新增归档默认关闭，可搜索角色名后按所需天数复刻。
已核实的 7.1 卡池标注「首期」；缺少分期依据时按角色池类型标注，不根据 `gachaType` 猜测上半或下半。
薇斯纳角色 ID 为 `10000143`，解锁卡为 `4143`；沃雅妮莎角色 ID 为 `10000140`，解锁卡为 `4140`。
现有资源已提供角色、技能和突破表。`languages/game-name-fallbacks.json` 为缺失的名称提供跨语言别名与武器名称，
覆盖 GM 物品搜索、卡池目录和服务端名称查询；正式资源的名称优先，升级后自动重建文字缓存。
这次补录核实的是角色资源与卡池目录，角色的完整战斗表现仍需游戏内验证；归档沿用现有通用祈愿展示模板。
运行包的归档只用于目录和复刻模板，更新不会覆盖已有卡池配置或改变生效状态。
复刻卡池可设置 1–365 天有效期（默认 30 天），当前配置也可单独修改到期时间。
修改到期时间不会改变 UP、概率、保底配置、开始时间或启用状态；重新复刻则从现在开始计时并启用该卡池。
到期后卡池停止展示和抽卡，常驻池也遵守设置的时间窗口。玩家已打开的祈愿页面需要重新打开以刷新目录。
新手祈愿按玩家累计次数限制为 20 抽：第一次十连后仍可抽取，抽满后从该玩家的祈愿列表隐藏，
最后一次抽取会同步刷新列表。重新登录或更换新手卡池编号不会恢复次数；GM 中仍保留启用配置，供尚未抽满的玩家使用。
武器祈愿与集录祈愿的定轨武器、命定值按卡池编号和本次复刻分别保存；新卡池和重新复刻默认未定轨、命定值为 0。
两个卡池即使有同一把 UP 武器，定轨也各自独立；取消或更换定轨只影响当前卡池。
仅修改到期时间会保留定轨。旧存档中无法确定所属卡池的共享定轨不再使用，已有抽数和保底计数保留。

旧默认表的部分模板（含启用卡池）曾没有 `scheduleId`，导致 GM 无法直接管理或错误显示为未加载。
服务启动时会自动分配并保存唯一的
本地管理编号：禁用模板使用 50000–89999；启用的旧配置从 1000 开始分配并避开已有编号。
已有编号、物品池、启用状态和时间均保留，迁移前的原配置保存为数据目录中的
`Banners.before-id-migration-*.json`。这些编号用于本服务器管理，不代表官服原始卡池编号。
活动目录也不等于完整玩法还原，具体进度见下文。

## 纳塔龙之魂附

已接入参考库中的扫描目标、附身、换龙、退出和死亡清理流程。
附身使用玩家实际选中的龙或魂烛，退出后保留可再次附身的魂烛；成功传送及离开场景时清理旧载具。
纳塔载具生命值和死亡延时从资源读取；载具骑乘资料同步到场景快照，退出按对应载具 ID 清理体力上下文。
服务端流程已有回归测试；当前客户端的动画、操作恢复、六类龙技能及联机实玩仍需验收，尚未部署到日常服务。
来源及验证范围见 [龙之魂附接入](docs/NATSAURUS_POSSESSION_2026-10-14.md)。
载具请求统一校验当前会话、场景登记和骑乘资格；重复上车保留体力，切换角色后退出仍清理原骑乘记录。
换乘和载具替换会清理旧座位，回复包构造仅编码数据。验证范围见 [载具请求入口](docs/VEHICLE_INTERACTION_SCOPE_2026-10-14.md)。

## 技能与生命值结算

已修正纳西妲技能状态索引、初始化参数和活动专用技能误入基础目录的问题，以及通用技能的计数、条件判断、状态添加和清理、联机选敌。
技能、食物和神像的复活共用正确的状态恢复入口，团灭重生会清除死亡标记。
当前队伍的范围和生命周期检查、特殊技能控制及命座流程的进度见
[死亡角色复活](docs/ABILITY_DEAD_AVATAR_REVIVAL_2026-10-14.md)。
旧角色存档会为技能库新增的技能补齐 1 级，保留已学等级，修复首次登录快照的空指针；
验证见[技能等级存档补全](docs/AVATAR_SKILL_MAP_LOGIN_2026-10-16.md)。
进入场景的天赋快照已修复数值增量导致的负下标异常，并保留绝对赋值的符号和有效零值；
验证见[场景天赋参数](docs/SCENE_TALENT_SNAPSHOT_2026-10-16.md)。
角色读档在装备加载完成后恢复存档生命值、元素能量和特殊能量，保留死亡与低血量状态；
保存与深渊联动验证见[角色状态读档](docs/AVATAR_VITALS_PERSISTENCE_2026-10-16.md)。
退出登录的清理与保存分别执行，单步异常后继续处理其他步骤及自有角色；
范围与故障验证见[退出登录隔离](docs/PLAYER_LOGOUT_ISOLATION_2026-10-16.md)。
元素爆发保护跟随实际角色的无敌修饰器，切人和召唤物攻击分别结算；
真实资源及战斗回归见[保护归属](docs/BURST_PROTECTION_SCOPE_2026-10-16.md)。
团灭复活会选择当前场景最近的已解锁、未被任务锁定的锚点；没有可用锚点时原地恢复。
资源、回退与响应验证见[复活位置](docs/WORLD_RESPAWN_POINTS_2026-10-16.md)。
距离条件支持水平、高度和三维比较，恢复区域与芙宁娜范围扣血的验证见
[技能距离条件](docs/ABILITY_DISTANCE_PREDICATES_2026-10-14.md)。
Rerir 的复活布尔条件已接入，后台、死亡与失效实体的处理见
[实体可用状态](docs/ABILITY_ENTITY_AVAILABILITY_2026-10-15.md)。
七七六命的技能控制、独立冷却和客户端两条复活入口见
[七七复活技能控制](docs/QIQI_REVIVAL_CONTROL_2026-10-15.md)。
芭芭拉六命已接入战斗和溺水时的后台自动复活，独立冷却及客户端确认规则见
[芭芭拉自动复活](docs/BARBARA_AUTOMATIC_REVIVAL_2026-10-15.md)。
燃素回调已去除重复扣除并保留零值，范围和当前资源边界见
[燃素回调检查](docs/PHLOGISTON_CALLBACKS_2026-10-15.md)。
可选的客户端 [IFix 诊断](docs/IFIX_CAPTURE_2026-10-15.md)可捕获原始加载流，
并记录已初始化的燃素 VM 状态；完整 Add／Set 热修复分支仍待实际数据配对。
角色战斗状态回调按消息区分进入和退出，并对重复确认去重；阿蕾奇诺资源检查与协议验收边界见
[进入与退出战斗](docs/AVATAR_COMBAT_CALLBACKS_2026-10-15.md)。
体力回调保留零消耗并拒绝无效输入，现有计时边界见
[体力回调输入](docs/STAMINA_MIXIN_INPUT_2026-10-15.md)。
治疗先偿还生命之契，剩余部分恢复生命值；生命之契偿还、实际治疗量和角色属性通知保持一致。
同一角色同时接受多个治疗时依次结算，避免丢失数值或重复偿还。

相关服务端回归共 116 项，包含纳西妲、班尼特、芙宁娜、七七等实际资源回调。
完整角色的动画、天赋冷却、伤害及联机玩法仍需游戏内验证。
范围和验证依据见 [技能与生命值结算记录](docs/ABILITY_BEHAVIOR.md)、[纳西妲技能状态记录](docs/NAHIDA_SKILL.md)。

## 往期活动

[GM 控制台](http://localhost/gm) 新增「往期活动」，按发布时间排列，显示历史开放时间和本次复刻时间，
支持开启、关闭、恢复与重新复刻。目录覆盖 401 条资源活动，158 条已对应历史时间。
每条活动可自定义 1–365 天复刻时长（0 为原活动时长）；已有排期可保存新的到期时间并保留进度和已领取奖励。
恢复开启沿用原排期，重新复刻会重置进度；修改到期时间会同步调整玩法关闭时间并保留原有领奖期长度。
通用活动天数按国服凌晨 4 点计数；旧排期、未知条件和错误参数不再误通过。
具体范围及仍未接入的条件见 [活动日界线与条件](docs/ACTIVITY_CONDITIONS_2026-10-09.md)。
在线跨凌晨 4 点会更新排期、通用条件通知与任务条件事件，不重置已有活动进度；
服务端验证和仍需游戏内验收的范围见 [日界线与在线刷新](docs/DAILY_REFRESH_2026-10-09.md)。
按发布时间从 1.0「原素烘炉」开始还原；已恢复主控脚本，接入活动详情、Lua 计分、倒计时、阶段回调和 13 项任务进度。
已接入活动场景加载与关闭清理，以及 7.1 开始、进度、点名和结束通知。
已接入当前世界的房主邀请、队友答复、20 秒准备及中断流程。
已接入跨世界 2–4 人整队匹配、房主/队员确认、入队许可、场景转移和加载后准备。
已接入每轮个人领奖：按开局时的个人世界等级读取原掉落表，预览不扣费，确认后消耗 40 点原粹树脂，每人限领一次。
已接入资源中的 18 项结算称号，按本轮凝块、个人效率与击杀统计选择 watcher。
多人完整挑战与称号显示仍待实机验证，各活动的玩法完成度单独显示。
第二项 1.0「百货奇货」已加载七天材料和七组奖励，接入国服凌晨 4 点换日、每日提交资格、
复刻进度结构及 7.1 活动详情。材料提交请求、库存扣除和持久化开匣机会已接入；
已接入立本七套场景通知、每日对话、七匣不重复抽取与实际发奖；完整客户端流程待验证，当前标记为「还原中」。
第三项 1.1「未归的熄星」已恢复六区探索的原始地图脚本、118 个碎屑点、两轮区域任务与六组原始奖励。
采集记录、货币 109 累计数与任务进度一起保存，重复采集、重复领奖和旧排期请求会被拒绝。
已核对并修正 7.1 探索通知字段；陨星回收、多人挑战、商店、特殊角色奖励与剧情仍在还原，客户端完整流程待验证。
第三章依赖的队伍浮点能量和场景战斗基础已接入，支持准备／开始倒计时、分段进度、
超时／离场中断、每轮玩家统计和 7.1 通知；结算称号仍待接入。
范围见 [场景战斗基础](docs/SCENE_PLAY_BATTLE_2026-10-11.md)。
能力调用主控 Lua 的 `CurScenePlay` 分支和异步轮次校验已接入，
见 [场景玩法能力调用](docs/SCENE_PLAY_LUA_CALL_2026-10-11.md)。
队伍 SGV 的设置、增量、读取、能力刷新与绝对值通知已接入，
见 [队伍服务端全局值](docs/TEAM_SERVER_GLOBAL_VALUES_2026-10-11.md)。
活动队伍机关的按玩家创建、专用同步、能力请求与结束清理已接入，
见 [场景玩法队伍机关](docs/PLAY_TEAM_ENTITIES_2026-10-11.md)。
跨场景组同步调用、目标／原调用上下文与嵌套恢复已接入，
见 [跨组 Lua 调用](docs/GROUP_LUA_CALLS_2026-10-11.md)。
场景组怪物潮的独立计数、补充上下限、暂停／恢复与结束清理已接入，
见 [场景组怪物潮](docs/MONSTER_TIDES_2026-10-11.md)。
第三章四个原始组和公共脚本已恢复，原始难度配置与能量提交逻辑已适配，
见 [大型陨星原始脚本](docs/ASTER_BIG_SCRIPTS_2026-10-11.md)。
实际击杀已同步接入离场前死亡计分，隔离倒计时、已结束轮次和旧一轮残留怪物，
见 [怪物离场前死亡事件](docs/MONSTER_DEATH_SCRIPTS_2026-10-11.md)。
场景组一次性计时、原始五轮增援和 7.1 模板提醒已接入，修复末轮跳过与续排误取消，
见 [场景组计时与熄星增援](docs/GROUP_TIMERS_2026-10-11.md)。
大型陨星胜利后的原始奖励机关与个人 40 树脂领奖已接入，按生成时的个人世界等级固定奖励，
多人各自限领一次，保存异常保留防重凭证；货币 110 可兑换第二页全部 15 项原始商品，
见 [大型陨星奖励机关](docs/ASTER_BIG_REWARDS_2026-10-11.md)。
大型陨星入口已挂载四个原始场景组，按第三章时间与等级开放；支持单人直接挑战，
以及全队同意后直接挑战或跨世界 2–4 人整队匹配；全员完成确认、许可和场景加载后，
进入原始匹配难度及准备流程，见[大型陨星跨世界匹配](docs/ASTER_BIG_MATCHING_2026-10-12.md)。
未领机关可从原排期记录恢复，保留原参加者资格、个人奖励和防重复凭证，
见[大型陨星机关恢复](docs/ASTER_BIG_RESTORE_2026-10-12.md)。
两页商店使用各自代币账本，保留原始价格、限购和异常预留，
见[大型陨星精粹兑换](docs/ASTER_BIG_SHOP_2026-10-12.md)。
完整称号、第三章任务、客户端余额通知和特殊角色原生领取按钮，以及游戏内实玩仍待完成，
见 [大型陨星入口](docs/ASTER_BIG_ENTRANCE_2026-10-11.md)。
中型领奖 14 次、大型领奖 7 次的原特殊奖励条件已按个人凭证更新，支持重载恢复和原生进度通知，
见[熄星个人领奖条件](docs/ASTER_MILESTONES_2026-10-12.md)。
菲谢尔服务端领奖已支持完整条件检查、角色／命之座材料／星辉转化、确认保存和防重复领取，
可在 GM 命令目录使用 `aster @UID status`、`aster @UID take`；命令不能跳过原剧情条件。
见[菲谢尔特殊奖励](docs/ASTER_SPECIAL_REWARD_2026-10-12.md)，原生领取包号和完整剧情仍待接入。
三章剧情任务已接入原始接取条件，登录或 GM 开启活动后会触发可接任务；
第三章完成后立即解锁三条原始支线，并更新菲谢尔的剧情条件。
阶段时间、前置任务和冒险等阶仍须满足，重复刷新保留已有进度。
见[剧情任务接取与解锁](docs/ASTER_STORY_QUESTS_2026-10-12.md)；完整对话、演出及客户端游玩仍待验证。
活动剧情条件支持从已完成任务恢复，重新开启熄星时不再要求重复完成第三章；
多任务条件须全部完成，保存成功后才同步进度，详见[活动剧情条件恢复](docs/ACTIVITY_QUEST_WATCHERS_2026-10-12.md)。
通用任务内容条件支持各目标独立计数和中途保存，交互次数按原资源要求判定；
物品、等级及脚本计数展示当前进度，详见[任务内容进度](docs/QUEST_CONTENT_PROGRESS_2026-10-12.md)。
清怪目标只响应对应的组事件，死亡后仍留在场景的怪物不再阻挡；同配置的新怪仍计入检查，
详见[清怪任务与死亡实体](docs/QUEST_GROUP_CLEAR_2026-10-12.md)。
死亡动画请求支持当前角色 GUID 与实体编号，空队伍、存活角色和旧场景请求不触发换人；
详见[角色死亡请求适配](docs/AVATAR_DEATH_REQUEST_2026-10-12.md)。
任务天气机关支持开启、结束清理和未完成任务的重新进场恢复；Lua 天气区域按开启／关闭处理，
详见[任务与脚本天气](docs/QUEST_WEATHER_2026-10-12.md)。
任务套组保存实际成功切换的最新波次，默认重置清除覆盖，旧空列表可重新记账；
详见[任务套组切换与存档](docs/QUEST_SUITE_PERSISTENCE_2026-10-12.md)。
套组刷新中的临时可见性保护在失败时恢复，默认重置按任务归属回收，保留联机任务与秘境兜底，
详见[任务套组可见性保护](docs/QUEST_SUITE_VISIBILITY_2026-10-12.md)。
固定任务套组指令可等待场景脚本就绪，失败／关闭会释放等待，旧任务轮次不会在稍后重放，
详见[脚本初始化与任务套组等待](docs/QUEST_SUITE_INITIALIZATION_2026-10-12.md)。
场景加载回调与完成状态原子同步，单个回调失败不再阻断其他通知，
详见[场景加载回调](docs/SCENE_LOAD_CALLBACKS_2026-10-12.md)。
Lua 按明确组号清理机关，删除记录随组存档保存并防止默认套组重生；活动回合重置可清除记录，
详见[跨组机关清理与保存](docs/SCRIPT_GADGET_REMOVAL_2026-10-12.md)。
任务 Lua 通知等待目标场景脚本、真实玩家进入及场景加载完成，旧任务轮次在排队执行时再次校验，
详见[跨场景任务 Lua 通知](docs/QUEST_GROUP_LUA_NOTIFICATION_2026-10-12.md)。
任务天气锁定指令更新玩家属性并发送原生属性通知，拒绝非法状态值，
详见[任务天气锁定](docs/QUEST_WEATHER_LOCK_2026-10-12.md)。
随机套组先完整校验候选配置，选取后复用固定套组的等待、可见性保护和保存规则，
详见[任务随机套组](docs/QUEST_RANDOM_SUITE_2026-10-12.md)。
任务动作的公共编号／名称查询正确索引动作枚举，未知输入保留原回退规则，
详见[任务动作映射](docs/QUEST_EXEC_LOOKUP_2026-10-12.md)。
任务可临时锁定传送点并保存，保留永久探索记录；解除锁定不重复发探索奖励，
详见[任务传送点锁定](docs/QUEST_POINT_LOCK_2026-10-12.md)。
旧任务存档会按条件身份重新绑定进度，资源缺失时保留整章存档，资源恢复后可重新加载；
迁移规则与旧格式限制见[旧任务存档兼容](docs/QUEST_SAVE_REBIND_2026-10-12.md)。
功能开关兼容 7.1 与旧资源字段，按真实前置条件解锁；登录全功能解锁可通过
`server.game.gameOptions.unlockAllOpenStatesOnLogin` 单独启用，默认关闭，
详见[功能解锁条件与登录策略](docs/OPEN_STATE_POLICY_2026-10-12.md)。
任务表缺少的定义可从原始章节恢复，已有任务表优先；缺少接取规则的任务保持受限，
不会因加载而自动启动，详见[章节任务定义恢复](docs/QUEST_BIN_FALLBACK_2026-10-12.md)。
原 Lua 的 5 / 10 秒怪物重生与跨组清场已接入。提供本次 7.1 客户端的元数据提取工具，
已恢复 88,904 个类型、440,172 个字段名称及类型引用、733,442 个方法名称及地址；
现已核对烘炉所需的六个消息结构，并直接从客户端提取了 4,923 个包号返回函数。
玩家提交请求、回包的包号与字段已适配，Lua 失败码会原样返回，结束脚本可清理玩家增益；
回包语义名称的推断依据见协议说明。提取工具支持嵌套泛型参数。
详见 [往期活动说明](docs/HISTORICAL_ACTIVITIES.md)、[烘炉协议核对](docs/CRUCIBLE_PROTOCOL.md)、
[百货奇货协议核对](docs/SALESMAN_PROTOCOL.md)和 [未归的熄星协议核对](docs/ASTER_PROTOCOL.md)。


A community server being migrated to the **CN Genshin Impact 7.1.0** client. A fork of girluh's
[LunaGC](https://github.com/girluh/LunaGC), itself a fork of Grasscutter, reworked
for the 7.1 protocol while retaining the CN launcher's login chain.

The 7.1 proto sources, generated Java messages, CmdIds, login/heartbeat field parsing,
and versioned jar defaults are in place. Login and scene entry were verified on the
previous local build; they must be exercised again with a 7.1 client after this wire
format change. The feature notes below record that earlier 7.0 validation and are
not a claim that every feature has been retested on 7.1.

Local handlers with no verified 7.1 CmdId use negative placeholders, so they cannot
accidentally consume an unrelated 7.1 packet. Their features need fresh captures
before being enabled. `BlossomBriefInfoNotify` uses the candidate map in
`data/proto-fanout.json`. The two team deletion messages now use distinct verified
7.1 CmdIds and field layouts; their old shared fan-out entry has been removed.

The 818 additional `.proto` files marked `Recovered from ...` preserve schemas
embedded in formerly checked-in generated Java classes. They use separate
protobuf namespaces to avoid conflicting with the 7.1 definitions while keeping
the Java API needed by existing handlers. These recovered schemas are historical
compatibility data, not independently verified 7.1 messages. Gradle now generates
Java into `build/generated/source/proto`; generated Java is not tracked in Git.

Very WIP — expect broken things. What is implemented is listed below; everything
else is not.

## Choose how to run it

| Mode | Command | Resources | State |
| --- | --- | --- | --- |
| Windows development | `task dev` | A separate checkout selected by `.env.local`, or the `resources/` submodule | Shared Compose MongoDB volume; config/data in the working directory |
| Docker Compose runtime | `docker compose up --build -d` | Pinned `resources/` submodule copied into the image | Shared Compose MongoDB volume; config/data in the server volume |

For a fresh checkout, install Git LFS, clone with submodules, and fetch the
resource files:

```sh
git lfs install
git clone --recurse-submodules https://github.com/invoker-bot/LunaGC.git
cd LunaGC
git -C resources lfs pull
```

Then follow [Windows development](#first-time-setup) or
[Docker Compose runtime](#docker-compose-runtime). The runtime build needs
Docker Compose and enough disk space for the resource checkout and image.
The client protocol is still being migrated; a successful server start does
not establish compatibility with every 7.1 client feature.
The signing and client-compatibility keys committed in this repository are
public test material; do not reuse them for unrelated accounts or services.

## Note from the maintainer

This is a fork from girluh's [LunaGC](https://github.com/girluh/LunaGC). VERY WIP, so expect many bugs.

Features and functionality of the ps is not guaranteed, try it yourself to see what works and what doesnt (Most are broken).
This is possibly the only public PS with updated mob and gadget spawns! (Up to Version 5.4)

Contribute if you want/can...

## 7.0 validation history

- **The CN 7.0 login chain end to end**: the dispatch server serves the CN 7.0
  SDK's session routes and a `RegionInfo` with the right `game_biz`, so the
  launcher's own login flow reaches the game server instead of stalling on
  "无法连接网络" or "账号或密码错误".
- **The beginner quest chain**. Questing is on by default (see below) and the
  opening chain runs: Paimon's talk, gliding and stamina unlocks, the first
  statue, and the element gain, which is handled by `ExecChangeSkillDepot`
  rather than being skipped. A brand-new account gets the chain; an account that
  already played without it does not replay cutscenes.
- **The "client is damaged" crash is fixed.** The patched image is swapped into
  its slot in-process — the patch crate moves it aside when the DLL is mapped and
  restores it when the game exits — so the on-disk file the client's anti-cheat
  (`Astrolabe`) checks is the pristine one while the check runs. This applies to
  both swapped slots: the anti-cheat proxy (`Astrolabe.dll`) and the passport SDK
  (`AccountPlatNative.dll`). Before this, every session died at ~100 seconds with
  a WER crash in that DLL.
- **Unhandled packets are logged, not silently dropped.** Any request the server
  has no handler for is dumped at INFO with its opcode name, its number, its size
  and its decoded fields — this is the discovery mechanism for the features
  above, and `task dev` collects the list for you (see below).
- **The harvest list closes into actual handlers.** This is the loop the previous
  bullet only half-described: a session plays through a broken feature,
  `harvest-opcodes.txt` names the opcodes, and handlers get written against them.
  Three from the first real login session are now in-tree — `SeeMonsterReq` (full
  `Req`/`Rsp` handshake), `ClientAIStateNotify` (a client-to-server report with no
  reply), and `AnecdoteGetDataReq` (opcode known, no generated proto on 7.0.0, so
  it gets an empty `Rsp` that still answers the client). Two of the three needed
  generated protos the 7.0.0 dump lacks, copied from the reference fork against
  the same protobuf 3.19.6. New handlers take effect on the next `task dev`,
  which rebuilds the jar.
  The second login session added eight more, and those eight are why the harvest
  keys on the CmdId rather than the name: every opcode the dump has no name for
  logs as `UNKNOWN`, and the harvest used to collapse all of them onto one
  `UNKNOWN` entry, so the backlog reported one anonymous opcode no matter how
  many had actually arrived. Entries read `UNKNOWN (2819)` now and the ids
  survive to be pinned. Two of the eight are named by block completion, not by
  lookup — 7.0 reshuffles every CmdId but a feature block's *message set*
  survives, and `ToTheMoonPingNotify` (6117) and `PathfindingPingNotify` (2347)
  are each the single gap in a block whose every other 6.x name already has a
  7.0 home. The other six keep `UnnamedOpcode<id>` placeholders because their
  blocks each still hold several unplaced names, so any assignment would be a
  guess; the id in the name keeps a later real name trivially greppable. All
  eight are no-ops — there is no proto to parse and no `Rsp` to synthesize
  (7.0 scrambles `Req`/`Rsp` adjacency: none of 558 name pairs are adjacent),
  so dropping the packet is the correct behaviour and the point is that the
  opcode stops filling the backlog.
- **Cooking works, and the recipes a new account sees are the real ones.** The
  7.0 dump writes `CookRecipeData`'s ingredient and output lists under swapped
  keys for most rows, so a cook paid the *outputs* and received the *inputs*;
  `onLoad` undoes the swap, and the handler now falls back to the lowest filled
  quality tier for recipes that only define one or two of the three slots rather
  than handing over a `{id:0,count:0}` non-item. The default-unlocked set is
  computed from the excel rather than hardcoded, and it is rebuilt on demand —
  `*Manager.initialize()` runs in the `GameServer` constructor *before*
  `ResourceLoader.loadAll()`, so an eager scan there sees an empty excel map and
  every account used to begin with no recipes at all, with the client showing an
  empty cooking panel. The nine rows it resolves to are pinned by a test.
- **The token exchange stays on a key the client can actually derive.** When the
  RSA handshake in `GetPlayerTokenReq` fails, the server sends a degraded
  response and now *keeps the wire on the dispatch key* instead of switching to
  the session key. The degraded response cannot be used to derive the session
  key, so switching would leave the client transmitting on the dispatch key
  while every reply goes out under a key it cannot reproduce — and the
  receive-side latch that normally corrects an inbound key mismatch cannot reach
  our *send* path. The client retries the exchange every 30–60s, so staying put
  still converges on the next successful request.
- **The session key is visible to every thread that sends a packet.**
  `GameSession.useSecretKey` was a plain `boolean` written once by the token
  exchange on the KCP I/O thread and read by `send()`, which is reachable from
  *any* thread — the game tick, the schedulers, and the world's `eventExecutor`
  for queued teleports. Without a `volatile` there is no happens-before edge, so a
  send on one of those threads kept reading the pre-token `false`, XORed the
  packet with the dispatch key, and shipped it to a client that had already moved
  to the session key. The client decrypted it to noise and reported
  `PACKET_HEAD_MAGIC_ERROR, CmdID:0` (`error_code` 4010) — a *whole-frame* garbage
  read, which is why the CmdId came out as `0` rather than a real opcode. The
  field is `volatile` now. The receive-side latch in `decryptWithEitherKey` was
  already correct: it tries both keys and latches on whichever produces the magic,
  so the *inbound* direction tolerated the disagreement all along. The bug was
  asymmetric — only the send path committed to one key off a possibly-stale read.
  Three client reports in the 2026-09-21 session, each within a second or two of a
  session-key establishment, and *zero* server-side bad-magic warnings in the same
  log — that combination is the fingerprint: the failure was outbound, and the
  server never noticed.
- **A queued teleport after logout is dropped instead of throwing.**
  `queueTransferPlayerToScene` sleeps for its delay on the `eventExecutor`, and a
  player can log out during it — `onLogout` → `World.removePlayer` nulls the world
  reference, and the enter-scene notify then dereferences it unconditionally. The
  NPE was swallowed by the `Future` nobody reads, so the teleport failed
  *silently*; now it logs at trace and returns, because there is nothing to
  teleport: the client is gone and would never have read the packet. The same
  race family as the key-visibility bug above — both are a cross-thread read of
  state another thread tears down.
- **Daily commissions.** The daily-task loop is wired to the real 7.0.0 opcodes
  and the client accepts the three notifies it needs: `DailyTaskDataNotify`,
  `WorldOwnerDailyTaskNotify` and `DailyTaskProgressNotify`. This one is worth a
  note, because it was silently dead and the monitor is what found it: the
  messages were ported from a 6.6 fork with CmdIds guessed against a 6.7 dump,
  and the 7.0.0 client has no message registered for any of them, so it rejected
  every packet with `PACKET_INVALID_CMD_ID` and dropped it whole. Commissions
  appeared to do nothing, with no server-side error at all. The client's own
  telemetry named the bad ids (`CmdId=24670`, `28030`, `24983`), the real values
  are `7522` / `1939` / `23872`, and they now come from `PacketOpcodes` rather
  than literals, so they cannot drift out of sync with the opcode table again.
  The message *field numbers* are still reasoned, not harvested: only
  `TakeDailyTaskScoreRewardReq/Rsp` exist among the generated protos, so a wrong
  field now reads as garbage instead of the packet vanishing — a strictly better
  failure to debug. See the harvest item under "What does not work".
- **The Stormterror quest is finishable.** Every stock Lua for
  `EVENT_SPECIFIC_MONSTER_HP_CHANGE` compares `evt.param3` against a
  whole-number *percentage* — `if evt.param3 > 20 then return false end` under the
  comment `判断指定configid的怪物的血量小于%20时触发` — and the dragon domain
  (scene 20020, group 220020001) gates quest 35722 击退风魔龙 on exactly that check
  reaching `AddQuestProgress`. The server was sending **absolute current HP** as
  `param3`, so the value was always orders of magnitude above any threshold, the
  condition returned false on every hit, the quest-progress key `220020001` stayed
  at `0` forever, and the chain dead-ended the moment the player entered the domain
  — 35721 would sit `FINISHED` with 35722 unreachable behind it.
  `EntityMonster` now sends `Math.round(clamped cur/max * 100)`, clamped to 0–100,
  with 0 for a dead or uninitialised monster (which every threshold counts as
  "below"). The conversion is pinned by `MonsterHpPercentTest`. Upstream Grasscutter
  has the same bug — this is a fix, not a local regression.
  This is not one quest's fix. The same comparison appears **102 times across 74
  stock scripts** — every boss with a phase-change or enrage mechanic reads
  `evt.param3` as a percent, with thresholds 20, 30, 33, 50, 66, 70, 75 and 80.
  Absolute HP cannot satisfy any of them, so *every* boss mechanic of this kind was
  dead on this server, silently, with no error anywhere.
- **The artifact upgrade screen stops popping a null-exception panel.**
  `InventorySystem.upgradeRelic` has three early returns — relic missing,
  no exp gain, payment failed — and only the happy path ever answered the client.
  A request the server *rejected* was indistinguishable from a request the server
  *ignored*: the client holds the response handle and surfaces a dialog when it
  never arrives, and because nothing went wrong server-side no instrumentation
  saw it. All three now send `ReliquaryUpgradeRsp` with a real retcode
  (`RET_ITEM_NOT_EXIST` / `RET_ITEM_INVALID_USE_COUNT` / `RET_ITEM_COUNT_NOT_ENOUGH`),
  and the new `PacketReliquaryUpgradeRsp(int)` ctor is the reason that is one line
  each. This was the visible half of the developer-mode feature below — the other
  half is finding the next one like it.
- **Artifact shop** — every official 5-star piece, rolled fresh per purchase.
  Configurable; see the table at the bottom.
- Updated mob and gadget spawns up to version 5.4, drops, the inbox, widgets, and
  the weekly boss.

## What does not work (yet)

The honest list, because "WIP" in the header is doing real work here. These are
known-broken, not unknown:

- **Daily commissions are field-guesses.** The CmdIds are correct 7.0.0 values
  now (see above), but the message *layouts* — which field number holds the task
  id, the progress, the reward id — are reasoned by hand, not read from the
  client. Only `TakeDailyTaskScoreRewardReq/Rsp` have generated protos in-tree.
  A wrong field shows up as garbage in the commission panel instead of the
  packet vanishing silently, which is debuggable rather than invisible, but it
  is still a guess. Fixing this needs a real proto harvest (below).
- **Proto harvesting is the single biggest unblock.** `debug/harvest-opcodes.txt`
  already collects every opcode the client sends that nothing handles, and eleven
  of its entries are now handled (above). But the opcodes are the easy half; the
  *schemas* are what is missing, and they gate roughly every feature left in the
  harvest list — the daily-task layouts above are just the one that bit hardest.
  `AnecdoteGetDataReq` is the cautionary case: the opcode is in `PacketOpcodes`,
  no `.proto` exists for it anywhere in the 7.0.0 dump or the reference fork, so
  the handler answers with an empty body rather than reading the request.
  That is also why six of the eleven still answer to `UnnamedOpcode<id>`:
  `PacketOpcodes` can place the *number* (the harvest always could) but nothing
  in-tree can name it, and guessing a name from block position would only hide
  which entries are still unverified.
- **Anything not in "What works".** If it is not listed above, assume it does
  nothing. The client reaching for real SDK gateways
  (`ConnectGateFailure`, `SafeConnect failed`) in `debug/telemetry.log` is
  expected on a private server and is not a bug here.
- **Combat ability noise.** `AbilityInstErrorTrue/False` lines and a recurring
  `UnhandledCombatType: COMBAT_SPECIAL_MOTION_INFO typeVal=19` appear in the
  client log at a low rate. Not crash-grade, and not yet investigated.

## Requirements

- **[Java 17](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)**
  or newer, on `PATH`.
- **Docker Desktop with Compose**. Both modes use the Compose `mongo` service
  and its named `mongo-data` volume by default. `task serve` starts this service
  automatically. For a separate existing database container, override both
  `LUNAGC_MONGO` and `LUNAGC_MONGO_URI` in `.env.local`.
- **[NodeJS](https://nodejs.org/) 20** — only for handbook generation. Skip it
  and pass `-PskipHandbook=1` if you do not want the handbook.
- **[Rust](https://rust-lang.org/learn/get-started/) + Cargo** — only for the
  client patch. The patch crate needs the **nightly** toolchain and the `windows`
  crate, both of which `cargo` fetches.
- **The CN game version 7.1.0** (`YuanShen.exe`) for testing the migrated protocol.
- **[Go Task](https://taskfile.dev/)** — the runner every command below goes
  through. On Windows, `winget install Task.Task` or `scoop install task`.

## First-time setup

Use the clone commands above. An existing checkout can instead run:

```sh
git submodule sync -- patch resources
git submodule update --init patch resources
git -C resources lfs pull
```

The patch submodule is pinned to the announcement RSA fix in
[invoker-bot/animegamepatch](https://github.com/invoker-bot/animegamepatch).
The resources submodule is pinned to a commit
of [LunaGC-Resources](https://github.com/invoker-bot/LunaGC-Resources).

Then:

1. **Resources.** Use the `resources/` Git submodule for a deployed checkout.
   Git LFS is required for its larger files. For local development with a
   separate resource checkout, put
   `LUNAGC_RESOURCES_DIR=D:/Projects/Experiment/LunaGC-Resources` in the ignored
   `.env.local`. `task serve` and `task dev` load this file and read that
   directory directly. Restart the server after changing resources that are
   loaded at startup. For a direct `java -jar` launch, export the same
   environment variable in the shell.
2. **`.env`.** Copy `.env.example` to `.env`. `.env.local` overrides it. Everything
   else in the template is optional —
   `GAME_PATH` can stay empty and the game directory is read from the miHoYo
   launcher registry. Set it only if the client lives outside the launcher.
3. **Build.** `task build` makes both the patch DLL and the server jar. See below.
4. **Patch the client.** `task patch`. See below.
5. **Start the server.** `task serve`.

On Windows, the server launcher creates `debug/java-sockets` for the JDK selector
pipe. For a deeply nested checkout, set `LUNAGC_JAVA_SOCKET_DIR` in `.env.local`
to a shorter writable directory. Paths with spaces are supported; global Java
VM options are unnecessary. See [Windows startup verification](docs/WINDOWS_JAVA_STARTUP_2026-10-16.md).

To advance the deployed resource version, run:

```
git submodule update --remote resources
git -C resources lfs pull
git add resources
git commit -m "Update resources submodule"
```

A deployment then uses that same pinned commit via
`git submodule update --init resources`.

### Docker Compose runtime

The Compose runtime builds the server jar and copies the checked-out resources
submodule into the image. Initialize Git LFS before building; the Dockerfile
rejects resource pointer files. The runtime does not read `.env.local`.

```
git lfs install
git submodule update --init resources
git -C resources lfs pull
docker compose up --build -d
docker compose logs -f server
```

Check container health with `docker compose ps`. The HTTP service should
respond on `http://127.0.0.1:8088/` once startup finishes. Use
`docker compose down` to stop the stack without deleting its data volumes.

Set `LUNAGC_PUBLIC_ADDRESS` in `.env` (or in the shell) to the IP address or DNS
name clients use to reach the machine. The default `127.0.0.1` is for testing
on the same machine. Compose publishes HTTP on TCP 8088 and the game server on
UDP 22101. MongoDB is also published on `127.0.0.1:27017` for local development
and is not exposed on external interfaces. Set `LUNAGC_MONGO_PORT` in `.env` to
change this host port. MongoDB data and
server-generated config/data live in separate named volumes and survive
`docker compose down`; `docker compose down -v` deletes them.

After changing the resource submodule commit or its files, run
`git -C resources lfs pull` and `docker compose up --build -d` again. The image
holds its own copy of the resources, so an existing container does not see later
checkout changes. Both runtime and development use this Compose MongoDB, so
accounts, player progress, inventory and wish history are shared between modes.

Before replacing a working runtime, save its installed jar, bundled resources,
Java runtime and image startup settings without stopping the server:

```powershell
task runtime:snapshot
task runtime:snapshot:build SNAPSHOT_PATH="debug/runtime-snapshots/<capture-directory>"
task runtime:snapshot:status SNAPSHOT_PATH="debug/runtime-snapshots/<capture-directory>"
```

Use the path printed by the first command. The script locates this checkout and
its Compose `server` automatically; project paths are not hardcoded. Building a
recovery image uses the captured files, with no Java compilation or resource
download. Repeating the build reuses the verified image when its ID is unchanged.
The snapshot stays in the ignored `debug/` directory and includes private runtime
configuration. Mounted server state and MongoDB data require separate backups.
See [runtime recovery and validation](docs/RUNTIME_RECOVERY_2026-10-09.md) for the
verified images, commands and remaining deployment checks.

### Switching between development and runtime

Stop the current server before starting the other mode; both use the same game
and HTTP ports. To switch from runtime to development:

```powershell
docker compose stop server
task dev
```

To switch back, close the development session and stop the local Java server:

```powershell
task dev:stop
task serve:stop
docker compose up -d server
```

MongoDB stays running through either switch. Server config/data files remain
specific to each mode; only database records are shared. An older `luna-mongo`
container is not used automatically. To migrate its data, stop database writers,
back up both databases with `mongodump`, and restore the old `grasscutter` database
into an empty Compose database using `mongorestore`. Verify document counts,
collection hashes and indexes before retiring the old container. If both
databases already contain player records, reconcile conflicts before restoring.

## Building

```
task build
```

This runs both:

| Task | What it does |
| --- | --- |
| `task build:patch` | `cargo build --release` in `patch/` → `patch/target/release/ext.dll` |
| `task build:jar` | `gradlew jar` → `LunaGC-7.1.0.jar` in the repo root |

`task build:jar` is timestamp-based, not checksum-based: the proto sources are
tens of thousands of generated files and hashing them all takes longer than the
incremental build would. Both tasks are no-ops when nothing changed, so
`task patch` (which depends on `build:patch`) does not rebuild the DLL every
time.

To skip handbook generation, build the jar by hand:

```
.\gradlew.bat jar -PskipHandbook=1 --console=plain
```

The handbook is generated as `GM Handbook.txt` / `handbook.html` in the repo
root; both are gitignored.

## Tests

Configure `LUNAGC_MONGO_URI`, `LUNAGC_TEST_MONGO_URI` and
`LUNAGC_TEST_MONGO_SECONDARY_URI` for an isolated test Mongo instance before running:

```powershell
.\gradlew.bat test serverSmokeTest -PskipHandbook=1 --console=plain
```

`test` runs the Java and resource regressions. `serverSmokeTest` starts a dispatch
server in a separate JVM, checks HTTP health and GM authentication, then stops it.
Its HTTP listener uses a loopback address and an automatically allocated port.
`check` includes both tasks. The separate JVM keeps startup independent of Lua
and other static state initialized by unit test fixtures.

## Running the server

```
task serve          # start MongoDB's container check + the server, detached
task serve:status   # is MongoDB up, is the dispatch server listening, is the game server up
task serve:stop     # stop the server (MongoDB keeps running)
task serve:logs     # page through start_stdout.log
```

The server writes `start_stdout.log` and `start_stderr.log` in the repo root and
survives the shell you started it from — it is a detached process, not a child of
the task runner.

Extra server arguments pass through with `-ServerArgs`. The useful one is
`-debug`, which turns on DEBUG logging without packet spam:

```
task serve -ServerArgs "-debug"       # DEBUG logging, no packets
task serve -ServerArgs "-debug all"   # DEBUG logging + every packet
```

Quest progress is only logged at DEBUG. If you want to follow a quest chain in
the log, `-debug` is required; `task dev` sets it for you.

**Create an account before you log in.** `task serve` opens a visible console
window for the server — type into it:

```
account create <name> <uid>
```

There is no web panel by default. If you closed that window, the same command
works from any shell that has the server's stdin; the console is the easy option.

## Patching the client

```
task game-path      # show the resolved client directory without changing it
task patch          # build the DLL if stale, then install it into the client
task patch:status   # what is patched right now, and are the backups intact
task patch:reset    # restore the pristine client files from their backups
```

Run these commands from the repository directory or a subdirectory. The patch
script derives the repository root from its own location, so moving the checkout
does not require changing the script. The client directory is resolved separately:
`GAME_PATH` from `.env.local` / `.env` takes priority; otherwise the script reads
the miHoYo launcher's install path from the Windows registry. It does not search
all disks for a client. Use `task game-path` to confirm the selected directory.
For an explicit override, put this in the ignored `.env.local`, adjusting it to
your installation:

```dotenv
GAME_PATH=C:/Users/InvokerBot/AppData/Local/hoyo/hk4e/versions/current
```

`task patch` does the whole job: it builds `ext.dll` if the sources changed, then
installs the proxy over `Astrolabe.dll` in `YuanShen_Data\Plugins`, rewrites the
40 dispatch URLs inside `AccountPlatNative.dll` and swaps its passport key for
the server's. It keeps `.lunagc-bak` backups next to each patched file so
`task patch:reset` can undo it exactly. Run `task patch:status` whenever anything
about the client looks wrong — it prints the whole state and exits non-zero on a
problem.

Exit the game completely before `task patch` or `task patch:reset`. Both refuse
to modify a running client, whose mapped DLL stays in use until the game exits.

The built DLL is an ignored local build output. Rust nightly is required for the
first build and whenever the patch sources change; `task patch` reuses an
up-to-date DLL.

**While the game is running, `task patch:status` reads the session state, not
the install state.** Both swapped slots hold their pristine image on disk for
the whole session — that is the mechanism that fixes "the client is damaged" —
so the anti-cheat slot reads `stock`, the passport key reads `miHoYo key`, and
each live copy reads `LIVE`. The exit code is 0 and the output names the running
PID. Only after the game exits do those same readings mean the install is
unpatched. `task dev` checks for a running client before it checks the patch, so
it reports the session rather than blocking on the slots.

### Manual install (if you cannot use the task)

1. Build `patch/target/release/ext.dll` with `cargo build --release` in `patch/`.
2. Back up `YuanShen_Data\Plugins\Astrolabe.dll` somewhere safe.
3. Copy `ext.dll` over `Astrolabe.dll` in that same folder.

The URL rewrites and the passport key inside `AccountPlatNative.dll` are not
optional — without them the client reaches the launcher's real passport server
and login fails with "账号或密码错误". `task patch` is the supported way to apply
all three.

### If the game crashed or was force-closed

The patch crate swaps a patched image into its slot while the game runs and moves
it back when the process detaches. **A game that dies mid-session — a crash, a
`taskkill /F`, a power loss — leaves that live copy stranded in the slot**, which
means the file the next launch loads is the pristine one and the client is
unpatched without anyone telling you.

`task patch:status` catches this, and `task dev` fixes it automatically (see
below). From the command line, just run `task patch` again — it detects the
stranded copy and re-deploys.

## Developer debug sessions — `task dev`

```
task dev            # server in -dev mode + patch check + client + the monitor
task dev:stop       # close the game and wait for the session report
task dev:status     # is a session being recorded, and by which process
task dev:report     # print the latest session report
```

`task dev` is the one command for debugging a play session. It starts the server
with `-dev` (reusing one that is already listening, and saying so if it was not
started with `-dev`), checks the patch state and refuses to launch if the client
is not launch-ready, launches `YuanShen.exe`, then exits and leaves a **hidden,
detached monitor process** in charge. The monitor outlives the terminal that ran
the task; it stops when the game does.

Pass `-SkipPatchCheck` to launch with the client as-is (`task dev -SkipPatchCheck`),
useful after a manual `task patch`.

### Developer mode — `-dev`

`-dev` is `-debug` plus instrumentation. Beyond DEBUG logging it arms the
**unimplemented-request check**: every packet the server decides it probably does
not implement fires `UnimplementedRequestEvent`, and a listener files each one into
`debug/dev-report/unimplemented.md`. Run the server on its own with

```
task serve -ServerArgs '-dev'          # '-dev all' also logs every packet
```

Three reasons are reported, and together they describe every way a player's action
can silently do nothing:

- **`NO_HANDLER`** — no `PacketHandler` is registered for that opcode. The client
  asked for a feature the server has no code for.
- **`HANDLER_THREW`** — a handler exists but raised, so the action was abandoned
  halfway. Already an error in the log; this collects it with the others.
- **`NO_RESPONSE`** — the handler ran to completion and never sent a packet back.
  **This is what an in-game error dialog usually means.** The client is waiting on
  the answer to its request and never gets one, and because nothing went wrong
  server-side, no existing instrumentation saw it — a two-hour session can be
  `CLEAN` while the player is staring at an error panel.

Each entry names the opcode, the player and scene it came from, the payload's
fields (`{no:wire=value, ...}`, enough to hand-write the proto), and a next step
telling you where to add the handler or which early return to make answer. One
entry per `(reason, opcode)` per session, so a player hammering a broken button
fills the file once, not forever.

The listener is registered by `Grasscutter#main` only in developer mode, and it is
a server-internal event handler rather than a plugin, which is why it has no
registrar. A plugin that implements the packet itself can cancel the event to stop
the report — `HandlerPriority.LOW` means a plugin that claims the request runs
first.

### What the monitor records

The monitor watches the game process and the server log until the game exits,
then writes a report. Everything lands in `debug/` (gitignored):

| File | Contents |
| --- | --- |
| `report.md` | The session summary — read this first |
| `report-<yyyyMMdd-HHmmss>.md` | A timestamped copy, so old sessions are not overwritten |
| `session.json` | The same data as machine-readable fields |
| `all.log` | Every server line from this session, nothing before it |
| `dev-report/unimplemented.md` | What developer mode filed — see above |
| `error.log` | Server errors and exceptions only |
| `unhandled.log` | Every packet that arrived with no handler |
| `harvest-opcodes.txt` | The deduplicated opcode list — the work list |
| `telemetry.log` | Client-uploaded telemetry the server received |
| `quest.log` | Quest accepts, completions and finishes |
| `index.txt` | Line counts per bucket |
| `autofix.log` | What the auto-fix did, if it ran |

The report's headline verdict is one of:

- **`CRASHED`** — the game exited with a non-zero code, or Windows Error
  Reporting logged a new crash for `YuanShen.exe` during the session, or a new
  dump appeared in `%LOCALAPPDATA%\CrashDumps`.
- **`ERRORS`** — no crash, but the server logged genuine errors.
- **`CLEAN`** — neither.

The report also carries the game's **exit code as an eight-digit hex**
(`0x0000002A`, not `-42`), the **WER event count against a baseline taken when the
session started** — so a machine with a long history of old crashes does not
report a false `CRASHED` — and the number of new crash dumps.

### Crash and error detection, and how it triggers a fix

The monitor is built around one observation: a private server's most common
silent failure is a packet the server does not handle. So the monitor separates
three things that a naive `grep ERROR` conflates:

1. **Real server errors** — `ERROR`, `Exception`, `SEVERE` in the server's own
   log → `error.log` and the report's error count.
2. **Client telemetry** — the client uploads JSON bodies containing `SuperDebug`,
   `"error_code"`, `WarningAlarm`, `PACKET_HEAD_MAGIC_ERROR`. These say "error"
   and trip a naive filter, but they are the client talking about itself, not a
   server fault. They go to `telemetry.log` only, and are explicitly excluded
   from `error.log`.
3. **Unhandled packets** — the `... arrived and nothing handles it - N bytes -
   fields {...}` lines the server emits at INFO for any opcode with no handler →
   `unhandled.log`, deduplicated into `harvest-opcodes.txt`.

`harvest-opcodes.txt` is the actionable part. It is a sorted, deduplicated list of
every opcode the client sent that the server ignored, so a session that walks
through a broken feature hands you the exact list of handlers to write. This is
how the beginner-quest chain and the login chain were found. Entries are keyed on
the **CmdId**, not the name — the 7.0 dump has no name for most unhandled
opcodes, so they all log as `UNKNOWN`, and keying on the name would fold the
whole backlog into one `UNKNOWN` line no matter how many had arrived. Entries
read `UNKNOWN (2819)`, and that number is what you pin in `PacketOpcodes` to
close the loop.

**The harvest reads the server's whole log, not just the slice the monitor
watched.** The monitor's lifetime is the game's — it stops when the client exits —
but the server is not part of the session and keeps listening, so a login that
happened after the game closed sent its packets to an empty room and
`unhandled.log` stayed at zero bytes while the backlog really grew. That is not
hypothetical: the session recorded on 2026-09-21 from 15:41 was clean and the
monitor wrote its report at 18:10, but the same server was still listening at
22:22 when a client logged back in, cooked three recipes, and walked the world
until 22:43 — eleven unhandled opcodes, all of them invisible to the report that
had already been filed. So at harvest time the monitor re-scans the server's own
stdout log as well as its own bucket, because the announcement is the
authoritative record: each opcode is announced exactly once per server process,
so the whole file is precisely the set that server still cannot handle, and any
opcode an earlier process announced is still unimplemented and still belongs on
the list. `all.log` and the other buckets remain session-scoped; only the
harvest reconciles, because only the harvest is a work list rather than a
transcript.

The **auto-fix** is deliberately narrow: if the session ended with a patched image
stranded in a slot (the "client is damaged next launch" condition), the monitor
re-runs the patch deploy itself and logs it to `autofix.log`. Everything else is
reported as evidence for you, not guessed at.

### Reading a session

```
task dev:stop       # closes the game; the monitor writes the report and exits
task dev:report     # prints debug/report.md
```

A typical loop: `task dev`, play until something breaks, `task dev:stop`, read the
verdict, then:

- `CRASHED` → check the exit code and `debug/error.log`; if the crash is in the
  client, `debug/telemetry.log` often holds the client's own account of it.
- `ERRORS` → `debug/error.log` for the stack, `debug/all.log` for the surrounding
  lines.
- a feature that silently did nothing → `debug/harvest-opcodes.txt`; if the
  feature's request is there, the handler is missing or wrong, and the logged
  field values are the request's actual contents.

`debug/all.log` starts exactly at the session boundary — the offset is recorded
once the server is up but before the game is launched, and the monitor seeks
there rather than asking `Get-Content` for "the end of the file", which is
wherever that cmdlet happens to run. The server's startup banner lands above the
boundary on purpose; everything the session itself produced is below it, and
nothing written while the monitor process was still coming up is lost.

**The small buckets look empty until the session ends.** The writers buffer 4 KB
before flushing to disk, and a session's worth of unhandled packets or errors is
often well under that — they are held in memory and land when the monitor tears
its writers down in its `finally`, which is after the game exits. `all.log` and
`telemetry.log` are big enough to flush repeatedly and look live mid-session;
`unhandled.log`, `error.log` and `quest.log` do not, and reading them while the
game is still up reads as zero bytes. That is buffering, not a capture failure —
`all.log` has the same lines, since every bucket is classified out of the same
ingested stream. Wait for the report.

### Bucket reset warnings

Each session starts by erasing the buckets from the previous one, so `error.log`
is *this* session's errors and not a rerun of the last session's stack traces.
The erase is verified, not assumed: it opens each bucket exclusively and
truncates it, then checks the file's length. A length that is still non-zero
means a handle is still holding the bucket open — the usual cause is a monitor
from an earlier session that never closed its writers, which is what happens
when a session is killed at the terminal instead of ending through the game
exiting.

When the erase is refused, the monitor **moves the bucket aside** to
`<bucket>.prev-session-<HHmmss>` and starts a fresh one, rather than leaving the
session to append onto a previous session's tail. The timestamp matters: two
sessions in a row that both wedge the same bucket would otherwise overwrite each
other's moved-aside file, and the second session's note would point at contents
that were no longer what it described.

If the bucket can be neither erased nor moved — a handle held with an exclusive
share, which is how the pre-fix writers opened their files — it is reported and
**skipped for the session**: the monitor marks that bucket dead, writes nothing
to it, and its lines never reach this session's counts. That is why a session
whose `all.log` was wedged still has a complete `error.log` and `unhandled.log`,
and why the verdict does not come back `ERRORS` on the strength of an old
`error.log` full of someone else's stack traces.

Both outcomes are surfaced in three places:

- a **`## bucket reset warnings`** section in `report.md`, placed above the file
  list it qualifies, which is where to look when a session's buckets look wrong;
- the **`resetNotes`** array in `session.json`, one entry per affected bucket,
  naming the file it was moved to or why it could not be moved;
- a **`[STALE]`** tag next to that bucket's line count in `index.txt`, marking a
  count that is accurate about the file but not about the session.

The moved-aside files are gitignored alongside the rest of `debug/`. Delete them
once you have read them; keeping them is only useful when two sessions need
comparing.

### Questing must stay enabled

`server.game.gameOptions.questing.enabled` defaults to **`true`** and should stay
that way. With it off, `PacketQuestListNotify` and `PacketFinishedParentQuestNotify`
drop every quest that is not already `FINISHED`, and a brand-new account has no
saved quest state at all — the quest log opens empty and the beginner chain never
appears. The old README told you to set it to false; that is wrong for this fork.

Turning it back on for an account that only ever played with it off is safe:
there is nothing unfinished stored, so nothing replays. The dangerous direction
is off **after** on, on an account that has saved progress — that is what replays
cutscenes.

`useEncryption` and `useInRouting` should both be `false`; they already are by
default.

## Artifact shop

Every official 5-star artifact piece - 290 of them, the five slots of all 62 released sets - is on
sale in the general goods store (Blanche's *Second Life*, next to the fountain in Mondstadt).

Each purchase rolls the piece fresh rather than handing over a fixed copy, the way an artifact
domain does: the main stat is drawn from the slot's real pool and the substats from the game's own
affix table, so every number printed on the piece is one the game would print. It arrives at +20
with nine substat rolls on it, and the odds are weighted towards CRIT Rate, CRIT DMG, ATK%,
Elemental Mastery and the DMG bonuses, and towards the top end of each roll. Buying several at once
gives you that many separately rolled pieces.

Tune it under `server.game.gameOptions.artifactShop` in `config.json`:

| Option | Default | What it does |
| --- | --- | --- |
| `enabled` | `true` | Turns the listing off entirely. |
| `shopId` | `1004` | Which shop carries it. `1001` is Paimon's Bargains, straight off the shop menu. |
| `costMora` / `costPrimogems` | `20000` / `0` | Price per piece. |
| `costItemId` / `costItemCount` | `0` / `0` | An item to charge on top of the currencies. |
| `buyLimit` | `0` | Purchases per piece per player. `0` is unlimited. |
| `artifactLevel` | `20` | The upgrade level pieces arrive at, `0`-`20`. |
| `critWeight` | `8` | Weight multiplier for CRIT Rate and CRIT DMG. `1` rolls them as the game does. |
| `damageWeight` | `3` | Weight multiplier for ATK%, Elemental Mastery and the DMG bonuses. |
| `highRollBias` | `3` | How hard each stat leans towards the best of its four values. `0` rolls evenly. |

Setting the last three to `1`, `1` and `0` gives you plain, unweighted domain rolls.

## Troubleshooting

- **"the client is damaged"** — the client's integrity check saw a patched file it
  does not expect. Run `task patch:status`. If the passport slot or the
  anti-cheat slot reads `stock`, `PARTIAL`, `STALE` or shows a stranded live copy,
  run `task patch` and relaunch. This was a recurring crash at ~100 seconds into a
  session; the in-process swap in the patch crate is what fixed it, and a
  stranded copy from a force-closed game is the one way it comes back.
- **"账号或密码错误" (account or password error)** — the passport key inside
  `AccountPlatNative.dll` was not swapped for the server's. Run `task patch`.
- **"无法连接网络" (cannot connect to network)** — the dispatch server is down, or
  the URLs in the client still point at the real ones. Check `task serve:status`
  and `task patch:status`.
- **Quest log is empty / no beginner quest** — `questing.enabled` is off. It
  defaults to on; see the section above. Also confirm the server is running with
  `-debug`, since quest progress is only logged at DEBUG.
- **Temple trial characters remain after the quest** — quest trials use a temporary
  party and are removed when their parent quest finishes. Logging in repairs older
  saves with leftover trial records or oversized parties, preserves owned characters
  and equipment, and restores a character reward missed because of a trial copy.
  Permanently obtained Amber and Kaeya remain available in the character roster.
- **Crates drop piles of ingredients** — wooden crate drops use the resource table's
  individual probabilities (typically 3–6% per ingredient). A crate's death is settled
  once, including overlapping lethal hits and self-destruction notifications.
- **No account / login refused** — create one first. The server has no web panel
  by default; use the console command `account create <name> <uid>`.
- **MongoDB connection timeout** — check the service. On Windows, `Win+R` →
  `services.msc` → look for the MongoDB Server entry and start it. On Linux,
  `systemctl status mongod` and `systemctl start mongod`; if that gives error 14,
  fix the ownership of the data directory and the socket
  (`sudo chown -R mongodb:mongodb /var/lib/mongodb` and
  `sudo chown mongodb:mongodb /tmp/mongodb-27017.sock`) and try again.
- **Windy** — put your `.luac` files in `C:\Windy` (create the folder if it does
  not exist).
- **The cooking panel shows no recipes** — the default-unlocked set is computed
  from `CookRecipeExcelConfigData` at login, so a server started before the
  resource checkout was ready, or a resource tree missing that excel, yields an
  empty panel. Confirm `ExcelBinOutput/CookRecipeExcelConfigData.json` exists
  under the configured resource directory. The set rebuilds on demand while it
  is still empty, so a re-login after the resources are in place is enough.
- **A feature silently does nothing** — run `task dev`, reproduce it,
  `task dev:stop`, and look at `debug/harvest-opcodes.txt`. If the feature's
  request is there, the server received it and had no handler for it. Most entries
  read `UNKNOWN (<number>)` rather than a name — the 7.0 dump has no name for
  them, and the number is what you search `PacketOpcodes` for.

## External reference sources

`task refs:sync` downloads the public repositories listed in
[the upstream research](docs/UPSTREAM_RESEARCH_2026-10-03.md) and their Git
submodules into `debug/`. It requires Python 3 and Git. No third-party setup or
build scripts are run. `task refs:update` only fast-forwards clean checkouts;
local changes are preserved. `debug/references.json` records each exact commit.
New checkouts materialize the complete current source tree. Synchronization also
expands existing clean sparse checkouts without changing their commits; dirty
sparse checkouts are preserved and reported as errors. `--manifest-only` leaves
checkout contents unchanged. The manifest records checkout completeness, local
changes, missing tracked files, and any remaining sparse scope.
The gcsim manifest entry compares the current commit's stack sources with the fixed
release used for adaptation; a later update recalculates that comparison.

These sources are ignored by Git and excluded from the runtime image. Closed
services are recorded as public information when no source repository is
available. Adaptation results and remaining gaps are documented in
[the integration notes](docs/UPSTREAM_INTEGRATION_2026-10-08.md).

The 7.1 portrait adapter loads the resource catalog, validates avatar, costume,
quest and ticket unlocks, and persists selected pictures and frames. Legacy
avatar IDs remain readable. Ticket use consumes one item and rejects duplicate
unlocks; non-default frames require an explicit reward grant. The protocol
evidence, remaining custom portrait and frame reward work, and client validation
requirements are in [the portrait notes](docs/protocol-profile-7.1.md).

Region membership scans now use integer hash lookups while preserving event
order and live callback effects. Lua skill calls restore the caller's context,
pass context before numeric arguments, and execute controller callbacks with
activity cancellation checks. Missing functions use bounded warnings; real
script errors retain their stack traces. Validation and remaining control-group
work are in [the scene and script notes](docs/SCRIPT_HOT_PATHS_2026-10-08.md).

Arlecchino's own burst now reads the resource heal tag and repays Bond before
restoring HP. Normal attack Bond consumption uses the resource modifier;
combat hit packets no longer apply an extra deduction to every attack.
Debt actions validate conditions and amounts and share the ordinary healing
monitor. Source evidence and remaining gameplay checks are in
[the healing notes](docs/ARLECCHINO_HEALING_2026-10-08.md).

Clorinde's healing conversion now follows installed resource modifiers and
their removal, with skill and inherent talent parameters applied to each
ability. Gunshots, burst and Impale use resource Bond actions; skill-start,
global-value and hit packets no longer apply extra Bond changes. Healing tags
also follow asynchronous callbacks without leaking into later events.
Current client predicate evidence, the exact gunshot boundary and remaining
gameplay checks are in [the Clorinde notes](docs/CLORINDE_BOND_2026-10-08.md).

## Repository layout

```
patch/          Rust cdylib -> ext.dll, the client patch (git submodule)
src/            the server
  main/proto/    tracked protobuf definitions, including recovered legacy schemas
build/generated/source/proto/  generated Java; ignored by Git
tools/          every task command lives here as its own .ps1
debug/          `task dev` session output (gitignored)
resources/      LunaGC-Resources (git submodule)
Taskfile.yml    the task definitions
.env            machine-local settings, copied from .env.example (gitignored)
.env.local      machine-local overrides, including external resources (gitignored)
```

Everything in `tools/` that the Taskfile invokes is named without a leading
underscore; the `_`-prefixed files are ad-hoc analysis scratch and are gitignored.

## Credit

girluh's [LunaGC](https://github.com/girluh/LunaGC)

kitkat's [patch](https://github.com/capyb2222/animegamepatch)

Terax for nt
