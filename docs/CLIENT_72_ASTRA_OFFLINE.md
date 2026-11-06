# D-02 Astra 限定离线攻坚（2026-11-07）

**最终状态：本轮限定离线攻坚已收口并停止。** 共11项独立研究双跑与主线第三回放核同；完整Avatar/g2/SceneInfo、资源就绪/Quest正向重绑和D-02实机未完成。全部代理和自有离线进程结束，未进入生产实现/全面回归/实机/部署；准确交接见[HANDOFF2](../HANDOFF2.md)。

本轮仅恢复 Avatar、SceneInfo 和必要资源语义研究，不恢复完整 D-02 开发、生产接入、全面回归或实机。接手 HEAD `4b1a696`；主仓库无未提交修改，resources 仅既有 `ScriptSceneData/flat.luas.scenes.full_globals.lua.json` 差异。五份 profile 输入已重新核 SHA-256；初始清点位于忽略目录 `local/migration72/astra-main/initial-{inventory,processes}.json`。旧网络边界、raw 图/适配器、Chapter/303 和有限 Java 投影直接复用，历史测试数不作本轮执行数。

## 先规划的难点表

| 任务及已有证据/实际停止点 | D-02 阻塞与未知 | 最小离线实验、成功标准 | 失败出口/实机依赖 | Sol/Terra 后续规格 |
| --- | --- | --- | --- | --- |
| F-03d3f4d2：f4d1 四构造片段、12容器store；enum HashSet、SortedDictionary、34054继承间接构造未闭 | 完整 Avatar 状态/初始化顺序；g2、R1/R4 | 精确 MethodInfo/rgctx/继承声明与机器码交叉，非零哨兵验证真实连续构造；不套同形泛型 | 首个未知调用/冷 metadata 明确记录；无法取得真实上下文则禁止推全构造 | 容器/comparer/对象默认状态、具体编码与 Java 来源缺口 |
| F-03d3f4d2：空装备止15183f290，非空止15184a5f0，技能需depot+68 | 空装备也有必经后缀；+6c不可替+68，池/辅助结构/tag29/38未知 | 精确后缀反汇编及可执行片段；独立非空装备、非零depot、零/省略/重复比较 | 关键delegate、资源行或生命周期无证不成功模拟；现场依赖列具体入口 | 允许发送/必须保留/禁止猜测清单；完整合同前不开放g2 |
| F-03d3s2后继：s1九类36raw、s2a四字段身份；非空能力止151846096 | retain、生命周期、复杂Object后代、实体替换/拆除；R1/R4/R6 | 先从Java发送和目标21761 handler证明必经分支；精确slot/继承与非空样本，保留完整GUID、owner及消息顺序 | 不把51522命名Weapon；未知资源/世界状态止步，列最小实机观察 | 字段/对象状态/先注册Avatar依赖、Java getter和准确验收出口 |
| E-01/R3：30302真实Excel拒绝；BIN fallback63，loadedAll=false；Chapter/保存已验 | LogicType对象、acceptCond/BGH、depot/ability/energySkill、候选revision；2B和g2真实值 | 固定候选/三输入的精确资源reader→字段→独立消费者；只追必要依赖，不批量审表 | 仅同值/旧名不足转换；缺客户端资源/自然加载状态列缺输入，不换旧表或手设loadedAll | 有独立依据的转换/拒绝规则，真实加载/索引/引用与QuestManager正向重绑前置 |

## 分工、证据与收口规则

三个 GPT6 Astra xhigh 子代理分别独占新 Avatar、Scene、资源研究工具/测试/专题；主线负责共享索引、独立审核及 Git。公共模型修改须先交具体复现、影响和验证方法；本轮不以未知原生业务默认成功。原始反汇编、轨迹、输入、日志留 `local/migration72/astra-{avatar,scene,resources}`，只交脱敏摘要。

每个结论分开记录目标静态证据、实际原生路径、托管/分配/容器/输入模型、未执行分支。CmdID→类型、类型→语义、字段→wire不混层。新实验需独立断言和重复验证；非零、64位、非空嵌套/list、显式零、合并、错误/边界按适用范围覆盖。完整合同未闭只交限定结论。

## 主线复核的 Java 来源时序（静态，不是新执行验收）

`Player.loadFromDatabase` 的同一任务按 AvatarStorage → QuestManager → legacy trial恢复 → Inventory 顺序执行；`Inventory.loadFromDatabase` 在保存装备归属恢复及TPS附件处理后调用 `AvatarStorage.postLoad`，随后才标记inventory loaded。postLoad补缺失起始装备、recalcStats，再completeLoadingVitals；`Avatar.@PostLoad`保存的绝对HP/能量在此最终阶段恢复。故后续g2读取FightProp不能早于完整装备/属性恢复，不能把`getFightProperties`方法存在当成有效值证明。技能depot缺失或非零energySkill缺资源时，captureVitals明确保留存档scalar；不能用临时零容量覆盖保存值。

`AvatarStorage.loadFromDatabase`从存档skillDepotId查询当前depot，缺Avatar/depot资源会跳过该角色；旅行者随后setSkillDepotData/recalc/save。`Avatar.setSkillDepotData`补技能等级、天赋并重算属性/队伍加成，不是只设置一个ID。目标默认depot、当前网络depot及保存来源必须独立核对，禁止照抄默认值修档。

当前源码`Player.onLogin`构造AvatarData；`HandlerSceneInitFinishReq`构造PlayerEnterSceneInfo，随后才构造SceneTeamUpdate。客户端s2a另已证明首个Avatar未注册会结束整个列表。这两类证据分别约束实现顺序，但不等于目标网络完整handler或实机顺序已验。未来不得因旧sender的Weapon字段名给目标type51522定名。

本轮不操作客户端/调试器、不部署/安装补丁、不改生产sender/共享Proto/版本准入/根配置。实机或不可取得输入成为必要条件时记录任务、输入、失败、排除项、最小观察/解除条件及后继，再转其它独立难点。可离线必要项耗尽后停止，备份核验HANDOFF2再覆盖，HANDOFF仅追加；每项验收及时本地提交、不推送。D-02仍未完成。

## 已验收结果及交付规格入口

各行均有代理双新进程和主线独立第三回放逐字节一致；测试数按各自范围记录，不将重叠关联套件相加。原始输入、完整轨迹、失败和证明在忽略目录；共享摘要只保留限定证据。

| 项目 | 实际新增范围 | 主线验证/提交 |
| --- | --- | --- |
| Avatar f4d2第一项 | 61980 ctor/reset/pool边界、空装备清理、Relic赋值、技能字典清理、+68/tag13 | 23组原生、11断言；`0da1e37` |
| Avatar suffix | tag29六模式位图、tag38完整选择/清旧、typed Weapon/Relic工厂 | 17组（含48 bit片段）、22关联断言；`1487d28` |
| Avatar ornament来源 | 两个具名表getter/loader和cache类型，止文件I/O前 | 4条路径、5断言；`0572e7a` |
| Avatar Sorted构造 | 五具体ctor完整选定链、+1c0 store与cold/missing/null停止 | 7组原生、7断言；`979a526` |
| Avatar装备查询 | 9槽查询/GUID回退样本、3实际注册 | 7断言；`0703e8d` |
| Avatar rgctx/List | 精确class表/MethodSpec、引用类型canonicalizer、三个继承List stores | 6组原生、7断言；`cdf2d76` |
| Scene第一项 | 非空retain/release、World/Beyond条件、替换时序、前后缀 | 36原生场景/3 wire拒绝、39关联；`5389b1f` |
| Scene CopyFrom/hash | 77771双list深复制和旧值清理、真实u32字典碰撞/高位/Unicode/默认边界 | 15原生场景、48关联；`1529a5e` |
| 资源 n4a | 完整depot/条件parser默认/非零/错误、BGH有限消费 | 15完整解析/1边界/5拒绝、10断言；`be6c00c` |
| 资源 n4b | depot→LHFO数组wrapper→元素parser→首参数查询及类型反例 | 5完整/4拒绝、15关联；`d961556` |
| 资源 n4c | 已知BGH消费者selector确为GameTime18，LuaNotify20不命中 | 8原生场景、19关联；`7e0e039` |

详细字段、值转换、对象状态、调用顺序、Java来源/缺口和禁止触碰范围分别在[Avatar规格](CLIENT_72_AVATAR_DEEP_RESEARCH.md)、[Scene规格](CLIENT_72_SCENE_DEEP_RESEARCH.md)、[资源规格](CLIENT_72_RESOURCE_DEEP_RESEARCH.md)。这些是**限定研究与隔离适配验收规格**，完整g2/SceneInfo生产合同尚未闭合，不能当成正式sender实现许可。

Sol/Terra接管时可直接按已验literal/独立样本复验，并在未来授权下把已核getter与独立adapter连接成隔离测试；必须保持完整GUID、原tag类型、显式零/省略差异、顺序、retain/copy区别和错误边界。不得先把未知字段接旧Proto、发空能力/空复杂Object，或将资源未知变成0/default。正式实现前仍需逐项满足专题的完整构造、真实资源/存档来源和业务消费门槛；本轮未启动该实现或全面回归。

资源剩余准确输入为：可溯源的实际资源blob/exporter字段映射、缺失server acceptance规则、LuaNotify20真实键匹配、运行时revision选择。Scene剩余为自然stage/能力注册、实体组件/生命周期、完整handler，以及80154非零入场必要性；80154已核当前sender/旧Proto无独立来源，不扩战斗。实机或外部输入的最小入口/观察/解除条件均在对应专题末表，不能以健康、编译或静态命中替代。

统一review已从116精确增至127，旧116摘要与1269生产来源均保持，三层/required/runtime/矩阵其余字段无变化；旧拒绝、双恢复一致和24定向检查见`local/migration72/astra-main/evidence-integration/`。研究结构0错误，ready仍224拒绝。没有全面Java/研究回归、构建、部署或客户端执行。

最终现场核查`astra-main/final-inventory.json`：五客户端输入、五保护文件、5240生产文件保持，patch clean/resources原dirty不变，无游戏/Java/自有研究进程和8088/22101/27027监听。仅原Mongo27017保留，两既有调试器未动。交接备份已核验、HANDOFF仅追加并验证原字节前缀；没有新构建/部署或运行健康成功声明。
