# F-03c：入场通知的已验字段与发送缺口

2026-11-06。固定国服7.2三输入，新增`tools/client72_enter_scene_contract.py`、10项测试及[机器契约](../protocol/cn-7.2/enter-scene-contract-observations.json)。本项没有修改八握手的五个既有交付文件、生产源码、mappings或共享任务表，没有启动客户端、调试API、服务或数据库，也没有本地提交。**最小Notify发送字段已确认scene_id、type、enter_scene_token和必需非空pos；完整场景入场及头像实际落地仍未验，不能由字段契约推导成功。**

## 实际可接入范围

原27748/type46311身份和raw reader来源按固定SHA-256复用；每次CLI重新校验客户端三输入并提取metadata。现已重新逐字段关联8类型44字段，即26外层、84343三个float和六类15嵌套字段。它是完整已观测raw结构载体，`rawSchemas`保存原始名称、tag、类型与容器，不把未知业务名补进Proto。

新增确认以下绑定；token继续引用已验八握手来源，不重复研究：

| 目标字段 | 结论 | 独立来源及实际执行 |
| --- | --- | --- |
| 46311 +7C / tag7 | `scene_id`；`sceneId = u32((raw XOR 2D03) + C0E3)` | 真实reader→1507AD82C的原生store到PlayerModule+1D0；153A3D378受限块读取同一字段，作为literal45681 `tryEnterSceneId`的第二参数；另原生ActorManager.GetCurrSceneId/14FEE86B0读取+1E4，明确区分当前场景和准备进入的场景 |
| 46311 +98 / tag8 | `type`；int32原样，目标Enter枚举86551 | 独立目标默认值表15符号；真实reader后执行14CD6753D的分派，包含合法值和越界/负值的目标诊断路径 |
| 46311 +94 / tag10 | `enter_scene_token` | 引用八握手已验原通知reader→Loading→有token命名日志的Ready构造；不改其证据 |
| 46311 +18 / tag14 | `pos`；nested84343，必须非空 | 真实reader→LoadingTask→原生transmit载体/队列→实际Default/DirectlySetPos接收者；独立同字段QuickV3分支进入目标具名`QuickTransmitData.pos2/centerAfter`，见下文边界 |
| 84343 +18/+20/+1C / tag1/2/3 | `x/y/z`，float32位型原样 | 原生reader→14CD673B8–14CD673F0转入实际LoadingTask+44/+48/+4C；任务字段声明明确是UnityEngine.Vector3/type2329，其命名x/y/z的boxed偏移16/20/24独立核对 |

sceneId覆盖0、1、3、uint32高位和最大值；诊断的currentSceneId刻意与进入值不同，原生比较走错误诊断路径，不模拟成功。只在box/格式化边界记录实际值；没有执行或替换其后的恢复业务。

EnterSelf/Jump/Other/Back/Dungeon/SelfHome/OtherHome/GotoRecreate/Beyond/Hall分别到14CD637B0入口；Goto/DungeonReplay/GotoByPortal/GotoByTpl到14CD67990入口。EnterNone虽然是目标已声明符号，也走`Unknown OnPlayerEnterSceneNotify type`诊断；-1、15和INT32_MAX同样到原诊断。入场分支函数本体不在这18样本中执行。实际目标符号及数值完整保存在`identity.enum`，不能由旧版enum顺序推导。

三个Vector样本覆盖非零xyz、负零、最小次正规数、最大有限float以及显式零；全部按原始bits比较。第四个样本缺少外层tag14，实际触达原null调用点14CD676D4→1404C2BE0。补充的六个正常模式消费和三个独立命名样本已闭合外层pos语义；保留`runtimePositionApplied=false`。当前生产`Position.toProto()`仅原样写getX/Y/Z，现有Vector为float x1/y2/z3，与本次目标wire一致，没有额外算术掩码；这是适配检查，不是业务语义的来源。

四字段的目标原名依次为JMHKHAPJADA/tag7、NOEEBKJAFAG/tag8、MCCMGLDOECH/tag10、HHAFHFEPLPD/tag14。84343子字段为CJMJBINEBAG/x/tag1、ALOKJBKPLGA/y/tag2、MGICHKNKDKO/z/tag3。机器契约保留`completeSenderContract=false`和`targetProtocolReady=false`，`minimumSenderFieldContract`只声明上述四个已验字段。其余外层/嵌套继续使用目标原名，不能把现有sender的旧值填入未知raw槽。

## 仍不能发送的业务候选

现有生产builder使用world_level、scene_begin_time、target_uid、dungeon_id、scene_transaction和limited_region_info；旧值、Asta同名getter或相同算术都不足以确认目标业务。当前四组独立wire实际执行了下列局部消费，但保持`semanticStatus=unresolved`、`productionAllowed=false`：

| raw字段 | 实际目标算术/存储 | 尚缺内容 |
| --- | --- | --- |
| +8C / tag2 | `u32(raw - 4DA1) XOR 7B5B` → manager+100；14CD6381F–14CD63847 | 独立world-level命名来源；当前字段声明混淆 |
| +68 / tag6 | `u64(raw - 416D) XOR 6CC3` → 静态145873388；14CD63858–14CD6387E | 独立scene-begin-time命名；owner47711的静态uint64字段FFIMJKAOILJ/rawOffset02007D78，名称仍混淆 |
| +80 / tag11 | `u32((raw XOR 1031) + 7633)` → PlayerModule+1D8；14CD63926–14CD6393D | 独立目标/世界owner UID命名，不能与已验登录UID混用 |
| +A0 / tag1 | `u32(raw + 45A5) XOR 6B98` → LoadingTask+28；14CD6739D–14CD673B0 | 独立dungeon ID命名或配置表身份；现有配置查询14A0E5E40仅为线索 |
| +38 / tag293 | 既有正常场景研究复制到LoadingTask+10 | 独立transaction业务命名 |
| 现有WorldRegions setter | 已有raw nested声明，不代表旧limited_region_info语义 | 目标实际消费者与业务含义 |

局部消费的32位样本为0、1、80000000、FFFFFFFF，64位样本为0、1、FEDCBA9876543210、FFFFFFFFFFFFFFFF。保存的是实际reader对象，再执行目标算术；没有用预期值回填目标存储。未知字段没有被当作默认成功、空schema或生产可用值。

## 复现与原生边界

当前独立边界共40项：5 sceneId、3 Vector位型+1缺字段null、18 Enter分派、4组未命名标量消费、6正常位置mode0/3和3独立QuickV3命名。10项新增及八握手/scene-wait/nested/normal合计48项回归全部通过，覆盖完整raw家族/字段来源、输入hash/tag篡改拒绝、current/entering不混淆、轴重排、enum诊断、uint64截断、正常/独立分支不能混写、float转double负零保持及未知字段禁止晋级。最终两轮SHA-256见下方冻结结果；首个31样本检查点及失败日志仍留local。所有研究操作仅脚本/API读取固定文件，不访问调试目标。

最终两轮完整trace SHA-256均为`2cf07066ecc089735ad9a4ed28e752c2488a12d22c0095d9ae790ea98e6570eb`（279451 bytes），共享摘要均为`0351840ad6f756d7adcc3c75b6bd0142406bdbeb487e71d279a771e38136892c`（68919 bytes）。重复性报告为忽略目录下`local/migration72/evidence/enter-scene-contract-determinism-v2.json`，逐字节相等。未提交原始trace或目标metadata。

```powershell
$sceneClient = & ./tools/game_path.ps1
python tools/client72_enter_scene_contract.py --client $sceneClient --output local/migration72/evidence/enter-scene-contract-trace.json --summary-output protocol/cn-7.2/enter-scene-contract-observations.json
python -m unittest tools.test_client72_enter_scene_contract -v
```

原始日志、完整trace及重复性报告位于忽略目录`local/migration72/{analysis,evidence}/enter-scene-*`。既有8包、scene-wait/nested的原始摘要hash固定在新工具`SOURCE_HASHES`；动态消费边界独立列出，不声称重新执行所有历史uint64/list样本。

首轮sceneId诊断夹具把局部frame放到统一run返回哨兵附近，目标`[rbp+C]`覆盖哨兵高位，之后getter发生非法取指；移动到独立frame区域后通过，保留失败日志。没有修改目标代码、模拟其未知返回或放宽执行范围。

## 位置命名的原生闭合与边界

每个向量分别执行raw bool1345/+85为0和1。原生14CD67C1C读取该字段和token，14CD64860暂存，再经14CD64E90→14CD62630→148B16AE0构造19399；模式字段+60由目标指令生成0或3，夹具不回填。独立目标enum19400把它们命名为Default、DirectlySetPos；基类+14写96，目标enum74697独立命名EvtTransmitAvatar。载体+50的原向量实际经过14E4DED50进入容量足够的List，样本断言数量1及原始对象指针。唯一库模型是pool borrow返回显式提供的零初始化载体和声明class；未模拟未知游戏函数的成功结果。

实际接收者是14B030540/type62249：先检查+14=96，再由14B030582的`CMP rax,[1458C5D10]`校验19399，并把原+50 Vector3送入14B031540第一参数。两个模式均实际执行该入口、类型检查和参数复制；随后分别执行14B031716或14B0316B7局部块，把捕获的原参数存到接收者+60。队列元素到接收者是显式传入；事件管理器的listener注册/调度没有运行，receiver辅助前缀也不在局部store证据中。不能称为完整运行期事件派发。

具名旁证独立于上述0/3路径：复制同类载体，只把明确的控制模式设为目标QuickV3/7，实际接收者分派到14B030B70。局部块14B030CBD在显式当前位置live-in和模式参数0下选择原+50作为目标位置；14B030D98原生把float32转为double，实际送入146E71F10。其目标metadata声明QuickTransmitData/type9740的参数为`pos1,pos2,yaw`，字段为`centerBefore,centerAfter,yawDiff,pitchDiff,quaternion`；原生ctor块将pos2写到unboxed+18即boxed centerAfter/+28。样本用不同的当前位置10/20/30，断言原向量进入pos2而非pos1，并按64位bits验证负零、次正规数和最大有限float转换。位置getter、旋转getter、后续四元数/头像运动均未模拟或执行，不能把QuickV3分支写成Notify自然路径。

查找接收者时必须包含RIP-relative CMP；只查MOV load会漏掉唯一直接引用。type usage row710395/source431371/cell1458C5D10与实际比较地址已经记录。初探误把模式5当作该快速分支，真实执行停在未建模14B030790；随后核对enum和分派，模式5实际是QuickV2，模式7才是QuickV3。失败日志保留，没有替换未知返回来通过样本。

19399 ToString的literal31681 `{0} transmit levelID`不命名+50；`98100001`在多个初始化函数出现，不能当唯一事件命令；未连接的BornPos字面量与UI worldLevel getter亦不能晋级字段。本轮到四字段最小契约停止，不继续world/time/UID挖掘。下一项可执行工作是主任务将四字段适配到完整raw类型家族并做独立wire回归；实机入场、scheduler和头像落地另留明确验收。未来若继续位置运行闭合，可从已定位14B0337D0→type62254/MoveNext14BC6E880的原Vector3复制与实体setter14ADF4890追踪，本轮不声称已执行这条后续链。
