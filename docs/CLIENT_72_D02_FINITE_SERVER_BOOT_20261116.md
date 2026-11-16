# D02-FINITE-SERVER-BOOT：有限实例核心生命周期

本项只处理显式M0/oldSnapshot的GameServer构造、周期调用和关闭，不恢复未参与入场的系统。已有认证/保存Guard与未知Scene停边保持，完整部署和客户端六项仍未验。

## 实际基线与纠正

最初仅从DungeonSystem构造器读取DungeonDrop推断“空实例data必然启动失败”；随后沿FileUtils.getDataPath核实会回退JAR内defaults/data，撤回该推断，没有移走默认资源制造失败。独立CWD/data初始空、仅复制已验M0资源且无UDP绑定的普通GameServer构造实际通过，1项47秒：加载内置116个DungeonDrop表、创建2个Quartz任务；Shop缺ProductIdConfigData.tsj的ERROR被原逻辑吞掉，原日志保留。正常模式不存在已证明的DungeonDrop缺失启动故障。

真正需要分流的是有限模式仍执行Energy/Cooking/Combine等初始化，构造Gacha/Shop/Dungeon/Tower等系统并启动非入场TaskMap/Quartz；正常tick仍运行crucible/scheduler，start仍查询Tower。有限模式红例明确检查构造未进入这些系统，15秒因真实DungeonSystem非null失败。它不是此前dispatch-only夹具缺服务NPE，也不是完整资源加载失败。

准备时一轮旧config缺oldSnapshot键导致脚本失败、未执行Java；另一轮Gradle默认512MiB堆GC thrashing停在编译、未执行constructor。各原始日志保留；后续仅命令级2GiB堆运行，未改仓库或全局Gradle设置。

## 最小实现

- 显式有限constructor仍建立真实packet registry、Player/World/HomeWorld容器和dispatch核心；不初始化非入场全局数据、不创建业务系统、ServerTaskScheduler或TaskMap。普通模式保留原路径。
- runTick仍驱动World和Player及真实tick统计/事件；Player保留原会话超时检查，有限模式不调用crucible或非入场scheduler。start保留game loop，跳Tower查询；正常关闭取消该loop并保留会话/World保存及实际网络关闭。
- package-private构造参数只供无绑定的真实core测试，生产公开构造器始终初始化KCP。没有config开关可在部署中冒充监听或健康；本轮没有绑定/HTTP健康验收。
- 有限packet registry只注册既验入口：M0的Token/Login/Ping、出生及Ready/Init/Done/Post请求；旧号只Token/Login/Ping，原快照前拒绝保持。其它请求在解析或旧业务sender前明确关闭，不伪造错误Proto/成功响应。已知请求继续原状态/认证检查。
- M0注销沿有限生命周期离开World、同步自身新profile、保存Player/Avatar及正常quit/unregister；旧号仍只更新活跃时间以保护未加载缓存。没有为了缺服务加try/catch或补无关资源；未建服务不会被调用。

## 实际验证

无绑定M0/old两模式核心共4项15秒通过：真实constructor、World/Player容器、零Quartz任务、core tick与正常core shutdown；通过真实packet registry的Ping两个非零字段往返和会话时间刷新；未知opcode携带非法header/payload仍在解析前关闭，未发旧成功。首次green4项71秒及后续补强结果分别保留。

真实新旧Mongo生命周期改用这份finite constructor，移除dispatch-only与反射maps夹具，两项24秒通过。M0实际onLogin→server.onTick→handleClose→正常保存→第二新Player冷读保持位置/HP/同Avatar/11101 ObjectId及Admission，不再有原三条缺服务NPE；旧号121 Avatar、10727活动Item、471装备及1527非活动原文档、Player原进度/profile约束全部继续通过。全量loadedAll仍false，旧号网络快照仍拒绝。

相关六套86项23秒通过：Handler注册、正常logout隔离、World tick、statue tick、Born与本地AccountAdmission。已有故障注入日志按原测试用途保留，不称本轮新生产错误。没有重跑无变化的全部524项。

原件在`local/migration72/d02-m0-integration-20261116/`：`finite-boot-full-probe*.log/xml`、`finite-boot-m0-red.log/xml`、`finite-boot-core-results/`、`finite-boot-lifecycle-results/`、`finite-boot-related-results/`和`finite-boot-results.private.json`；独立实例仅使用其自有数据与27027测试库。相关正式构建、产物和端口记录后补，不能以这些core通过升级entry-ready或客户端验收。

## 正式portable构建与未部署

运行修改本地提交0b7c67e后，通过`LUNAGC_RUNTIME=portable`及`tools/local_windows.ps1 -Mode build`正式构建27秒，命令级Gradle堆2GiB。JAR内嵌0b7c67e，SHA `d6bd05373dbadad0e9be8538d34730d6133a23882c87c19c360d2636d25a4e1e`，98289466B；相比c2faff7产物，class46386→46391，18变化/5新增/0移除，全部变化/新增逐项匹配build/classes。本包同时包含已验Motion前11字段raw。

两个独立InstanceRoot仅预备同SHA，没有start/play、补丁或客户端操作。post-build只有自有测试Mongo27027/PID51240；HTTP8088、Mongo27017、game UDP22101和游戏JAR进程0，没有健康/运行JAR正向对象。自然SDK/service/mode/批准路由和剩余Peer/SceneTeam/Entity必需wire仍未齐，准确记录未部署，不以core测试或JAR编译冒称ready。

产物原件`finite-boot-portable-build.log`、`finite-boot-build-artifact.private.json`及`finite-boot-post-build-runtime.private.json`留同ignored根；原子模块与初始他人差异保持，没有push。
