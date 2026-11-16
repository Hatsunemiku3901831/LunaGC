# D02-OLD-SNAPSHOT-MODE / LIFECYCLE

本项在[完整Avatar/Inventory恢复](CLIENT_72_D02_OLD_SNAPSHOT_JAVA_20261116.md)之后实现显式旧号有限模式。只绑定已复制的固定账号、owner和Avatar/Item ObjectId集合；保存、真实World/Team/Scene对象和退出冷读已验。网络入口因完整快照及场景wire缺口明确拒绝，D-02六项仍0/6。

## 生效范围

`server.game.oldSnapshot.enabled`默认false，与M0互斥。Windows portable入口只允许已准备的独立InstanceRoot；原运行根不能启用。资源目录、数据目录及Mongo27027按便携入口检查，原mongodata/local/data不复用。私有配置另给manifestSha256与只读inputRoot，账号、UID、ObjectId不硬写在源码或共享文档。

`OldSnapshotResources.load`核固定manifest及全部输出SHA，实际调用ResourceLoader/onLoad加载29表、121能力配置及OpenConfig/Talent，再用同一hash固定Player/Account BSON核唯一账号归属。复制的Scene3/Level_BigWorld及明确的“不追加可选global MP/Team/level能力”策略只用于对象身份验证，不证明客户端能力wire。其独立ready不设置loadedAll，也不借M0 birth-ready。

`Player.loadFromDatabase`在任何AvatarStorage/Inventory前逐个核真实资源及全部保存引用，严格核同owner的完整ObjectId集合和原字段；羁绊用已证明无位置语义的多重集合。仅正常保存stamp和SavedVitals标量按既有规则处理。错误UID、缺11101或必需名片Reward时零storage加载、零DB写；不补m0Profile，不补默认武器、清装备或裁库存。

通过门后，同步真实载入121 Avatar和10727活动物品；PlayerProgress/Team绑定保留，Quest/好友邮件/BP/daily/活动/家园等非入场回调后移。有限World准备复用真实Team修复、World.tryAddPlayer及场景能力应用，不改原Scene3、位置、元素或四套队伍。tick保留现有60秒心跳超时，跳过玩法timer；这不是十分钟客户端心跳验收。

正常退出保留世界离开、Player/全已载Avatar Guard保存、quit事件和注销，不启动未载好友/家园等回调。首次退出严格字段检查发现，原profile.syncWithCharacter会从未载Home/资料缓存改写enterHomeOption、profilePictureId；有限模式没有相应资料编辑，故仅更新最后活跃时间，保留其他缓存。首次失败、只读归因及一个误用Player心跳方法的编译失败均留证，没有放宽整份profile断言。

## 实际验证

`revision-8` manifest SHA `941c5f30e1d66d0208f8a4618bca30e439b82527130b06a1168aa9cf2574e0b9`，29表/287输出。旧revision7不覆盖；准备脚本新增固定Account BSON及三项已验M0身份输入，逐hash复制，原10条数值策略保留，全局能力省略策略单独具名。原输入、配置和数据库均在ignored独立目录。

`OldSnapshotLifecycleMongoTest`最终11秒，JUnit1/失败0/错误0/跳过0；新随机测试库导入原Account/Player/121 Avatar/12254 Item。实际验证生产loader及准入、错误UID/缺武器/缺Reward零写、真实Token handler在Player创建前已验-1拒绝且零写、完整Player有限load、同Avatar/Weapon实体的World/Team构造、有限tick、正常退出注销及新Player冷载。全部121角色、10727活动物品、471装配、1527非活动完整Document保全；Player除正常stamp及按退出时间区间验证的lastActiveTime外全部原字段等值，四项HP clamp与冷载稳定另见Java专题。

另9套178项账号/出生/M0/保存/HP/场景关联回归13秒全过；Windows portable夹具30项10.864秒通过，均由假进程入口拦截，没有启动服务；离线准备两项14.969秒通过。原始XML、日志和completion回执位于`local/migration72/d02-m0-integration-20261116/old-mode-*`。

复现使用Java专题同一27027归属核对流程，将资源环境指向`revision-8`及上述manifest SHA，执行：

```powershell
.\gradlew.bat test --tests emu.grasscutter.data.OldSnapshotLifecycleMongoTest -PskipHandbook --console=plain
python -m unittest tools.test_local_windows tools.test_portable_instance
```

资源测试须独立Gradle worker，不与其他有限资源装载混跑。测试副本保留，不删除原物理副本或18集合证据。

## 网络停止与下一项

Token handler在认证正常入口之后、任何Player创建/storage之前发送既有-1失败并关闭；Login handler及Player.onLogin也在快照sender前明确停止。内部对象测试使用捕获session，不将它的World通知送给客户端，不安装旧号协议或伪造成功。

实际Store生产实现只支持Material/Weapon/Relic，default会省略2331 Furniture与8 TPS；Weapon尚未投保存affix/refinement的目标效果字段，Relic尚未投完整主副词条。M0 Avatar投影只有出生所需字段，普通Avatar分支也不能当已验121角色7.2快照。下一项须逐必要Store/Avatar/TPS出口复核现有raw合同和真实业务投影，成熟部分独立实现，未知身份/wire保留。完整SceneInfo/SceneTeam/Entity、自然SDK批准路由仍独立未闭；没有用“未测全部Manager”阻断已验有限生命周期，也没有放entry-ready。

独立旧号实例配置已准备，尚未启动、导入部署库或运行客户端。正式构建及产物身份在后续实际结果中登记；当前不启动猜测协议，原生产数据不写。
