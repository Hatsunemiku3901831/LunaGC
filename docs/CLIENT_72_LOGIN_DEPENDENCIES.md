# A-03b 登录管理器、事件与脚本源码依赖复核

C-01a同步（2026-11-06）：下面为原A-03b历史；当前48源码/9根/294方法、408边（71人工/337候选）、2856未关联，10开放锚点仍open。已复核删除的超时兜底边和选人onLogin调用，重设父复核表hash并确定性重生成，未删除未知或晋级完整闭包。99包构造的当前Proto矩阵见[依赖矩阵](CLIENT_72_LOGIN_PROTO_MATRIX.md)，业务/保存范围见[选人实现](CLIENT_72_BORN.md)。

2026-11-06，限定源码验收完成。输入为当前遗留服务端，不能据此恢复7.2业务名、CmdID或字段。**A-03整体仍进行中，完整闭包与实机复核均未完成。**

## 固定输入与可复现范围

`protocol/cn-7.2/login-dependency-review.json`继承A-03a的9根/13条人工边并固定父复核表SHA-256；现固定48份源码、70条人工边规格（展开72个调用位置），10处明确开放边界。源码哈希规范CRLF→LF；每个手工端点必须存在且从根可达，selector/种类/参数数量/出现次数必须匹配。其余同owner唯一名称/参数数边仍只是候选，不执行Java符号归属。

JDK17实际解析得到293方法、409条边（72人工源码位置、337候选）、2868未关联调用。语法构造的包形类型97种，包含动态BasePacket，不是97个7.2必需消息；A-03c继续逐个定位发送类和Proto。48文件不是所有方法必执行：字段/隐式初始化、虚调用、资源和插件都没有由“文件已读”变成已闭合。

## 实际复核的入口

| 入口组 | 源码结果 | 仍开放的条件/后代 |
| --- | --- | --- |
| Player.onLogin管理器 | ActivityManager先defer通知；codex/SotS/队伍修复；QuestManager；进度/手册；圣遗物定向/音游；成就；锻造/树脂/烹饪/加工；家具；活动快照均有具体端点 | 资源/存档决定分支；候选同owner调用不是符号证明 |
| 普通/免费商城及月卡/纪行 | ShopSystem.sendProductPriceCatalog端点、Player既有月卡方法及BattlePass触发有索引 | 价格目录不是付款；当前充值免费；未变更普通商品/通用活动币/熄星账本或经济行为 |
| 每日状态 | BattlePass刷新/登录触发、BirthdayMailSystem、DailyTask重置、ActivityManager.onDailyReset有明确边 | 日界、资源、保存与奖励后代仍需业务回归；不把重置源码当实际登录 |
| 家园 | GameServer.getHomeWorldOrCreate→HomeWorld构造→GameHome.getByUid、onOwnerLogin | computeIfAbsent lambda是源码条件；模块构造/布局及数据库后代不全 |
| 令牌前加载 | HandlerGetPlayerTokenReq→Player.loadFromDatabase；任务/邮件/战令/日常加载入口 | runner.submit并发；背包/角色/好友及数据库/UID服务后代未闭合；不展开重复登录onLogout |
| 场景与任务 | SceneScriptManager构造→this::init初始化线程；loadGroupForQuest→refreshGroup；ScenePlayBattleManager.sendTo；PostEnterScene→DungeonManager和QuestContent varargs队列 | Lua/group/timer、控制器、MainQuest rewind/checkProgress、回调队列均依赖实际资源/时序 |
| 登录事件 | PlayerJoinEvent→PlayerEvent构造，Event.call→PluginManager.invokeEvent→invokeHandler | ACTIVE之后可取消并关闭；不能只见ACTIVE就称注册完成 |
| 发送事件 | Player.sendPacket→GameSession.send，SendPacketEvent构造及Event.call | 事件可替换/取消最终packet；FanOut配置与UnionCmd内层仍开放，未开展C-01 |

PlayerCreationEvent还可替换具体Player类型并通过反射工厂创建；源码索引固定的是内建Player入口，不保证插件替代类。PluginManager消费运行时监听器表，未声称枚举所有外部插件。场景线程引用只证明提交入口，不证明初始化先于登录完成。10个开放调用锚点逐一验证并保持status=open；其余2868未关联位置亦未消除，不是2868个客户端逆向任务。

## 工具、回归与复现

扩展解析真实GameSession枚举时复现JDK对无参枚举常量生成initializer end=-1：原扫描器substring抛错。现在此类initializer使用实际字段跨度、不输出无源码末端的合成构造；有参枚举的真实参数调用继续记录。新增独立枚举样本回归。A-03a重生成后方法/边/构造及未知数量全部不变，仅toolSourceSHA256随扫描器更新；旧报告hash仍是历史证据，不能与新摘要逐字节混比。

```powershell
python tools/review_client72_login_dependencies.py --java-home local/runtime/jdk-17.0.20.1+1 --output local/migration72/evidence/login-dependencies.json --summary-output protocol/cn-7.2/login-dependency-observations.json
python -m unittest tools.test_client72_java_surface tools.test_client72_login_dependencies -v
python tools/validate_protocol72.py
python tools/validate_protocol72.py --ready
```

14项聚焦检查通过；全研究选集254项通过。检查真实复核产物、两次一致、父表/源码hash漂移、开放边界漂移/禁止晋级、不可达手工边和枚举跨度。没有新增客户端逆向或实机调试，没有改运行代码/主Proto/配置，无部署；V-01b/c的221项Java通过是另一组实际回归，完整Java集未再跑。

两次完整报告和共享摘要逐字节一致。本机`local/migration72/evidence/login-dependencies{,-repeat}.json`、`login-dependency-summary-repeat.json`、`login-dependency-determinism.json`，日志`logs/login-dependencies-{focused,regression}.log`。下一项A-03c消费本报告的来源方法与候选包构造，列出Proto及7.2三层证据缺口；不批准正式生成或运行。

2026-11-06 A-03c限定离线验收完成，见[候选消息/Proto与三层缺口矩阵](CLIENT_72_LOGIN_PROTO_MATRIX.md)：97包构造、148引用/种子、1139文件描述符依赖，缺DoSetPlayerBornDataNotify.proto如实保留。原三层状态及185字段不变；6新增/260研究回归、重复矩阵通过，研究校验0/ready1（213缺口）。V-01b/c经济/保存221项通过；完整Java集未重跑。按用户要求到此停止并交接Astra，下一研究为B-02选人三件组的独立7.2三层证据；A-03整体仍待完整闭包/实机。未改运行文件、未部署或调试。
