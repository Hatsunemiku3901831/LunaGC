SceneInfo独立身份增量：root14.tag5真实World level ID与root15.tag7真实Team ID已接，两个非空子树独立/非模板，19项29秒通过；完整authority/能力/Entity仍未闭，Init/Done停止保持。此后新构建记录见[场景raw增量](CLIENT_72_D02_M0_SCENE_RAW_JAVA_20261116.md#sceneinfo非空身份增量)。

最新构建/增量：显式旧号模式及真实World/保存生命周期已完成，[场景raw Java](CLIENT_72_D02_M0_SCENE_RAW_JAVA_20261116.md)24项与正式portable构建25秒通过，内嵌89b2ceb，JAR SHA74049899d59c4a8b2652aeaba2ff14b9fb5ca81ae2e94acd08eb41f1caa001f3。两独立实例仅预备未启动，六项0/6；下文M0首次实现/构建数值按历史阶段读取。

# D02-M0：实际集成与验证

本轮从 `fcdd46af7965980dd4d318b13a646fac943e60fb` 接手；用户原AGENTS、patch/resources差异和两项未跟踪E8工具保持排除。只有国服7.2/Windows portable；没有部署或客户端验收，D-02六项仍0/6。最小规格、来源分类和固定输入分别见[SCOPE](CLIENT_72_D02_M0_SCOPE_20261116.md)、[DATA](CLIENT_72_D02_M0_DATA_20261116.md)、[复用](CLIENT_72_D02_M0_REUSE_20261116.md)。

## 已实现与实际测试

- `server.game.m0.enabled`默认false，portable入口要求显式InstanceRoot。M0读取该独立配置的资源/数据路径，通过原ResourceLoader的真实文件解析/onLoad实现有限加载，验证女主10000007、701、11101、Scene3、六技能、ability embryo/group、曲线/phase0与正值Avatar重算。全量`loadedAll`始终false；登录准入和加载等待使用只适用于显式M0的出生就绪。此状态仅为birth-ready。
- M0 Avatar sender使用目标Cmd20143和独立raw Proto：角色列表tag6、Team map/tag5及current Team/tag12；角色ID/GUID、三普通属性1001/1002/4001、同实例装备GUID、已审七FightProp、实际当前depot及当前HP决定的ALIVE/DEAD。没有投影Hero控制GUID或未知附属字段。
- 定向JUnit312项通过：新真实sender1项、独立Avatar raw275项、Inventory raw36项；新sender检查高位GUID、Store同装备实例、显式0属性、非零HP与死亡态。随后真实隔离资源/缺文件拒绝与sender复验2项通过。资源输入更新后的保存/冷读和场景对象验证继续。
- Windows portable夹具27项全部通过，包括M0原runtime拒绝、独立实例允许与已有端口/JAR/DB路径保护；夹具不运行真实Java/Mongo。最终日志`local/migration72/d02-m0-integration-20261116/portable-file-wrapper-tests.log`。

首次sender测试因未先初始化Grasscutter触发静态配置循环，失败XML保留后修夹具。首次资源测试的负例揭示FileUtils静态缓存路径：事后改配置未切换资源，首个正向不能算独立DATA通过。M0 loader现按显式Path调用同ResourceLoader，后续正负通过；原data/AbilityEmbryos.json不存在，加载方法只有读文件/内存写，无缓存写入，未恢复或覆盖原数据。

便携夹具初始EncodedCommand包装超时无输出，改直接多行Command后仍有3项既有实例例超时。最终将同一测试脚本写到临时文件并用`-File`执行，27项全过；前两份失败日志均保留，不追认为通过。未改实际launcher的停止、构建、端口或数据库归属策略。

## 当前未部署原因与下一动作

M0已完成有限onLogin/同步角色库存冷载和Player/World tick分流，不再触发活动、daily、家园和未审周期通知。新Player持久m0Profile=true，Token handler在任何storage加载前拒绝未标记旧档。Born入口创建前拒绝该模式不支持的男主，女主命名后同步确认Player/Avatar/11101装备才返回成功；M0不发欢迎邮件。

实际独立Mongo验证通过：正常出生→同库三对象确认→新Morphia Player→真实AvatarStorage/Inventory冷恢复，角色/装备ObjectId、同owner物品绑定、701、非零HP及装备攻击保持。旧档负例通过真实Token handler，AvatarStorage/Inventory均未加载，原Player BSON逐值相同；测试库留在独立validation实例，没有删除或覆盖原数据库。

同一冷恢复Player通过真实M0 onLogin创建World、EntityTeam、Scene3、EntityAvatar和EntityWeapon；目标21761的独立raw SceneInfo包读取相同Avatar/Item GUID、非零实体ID和本次token。独立literal wire测试覆盖已知tag、嵌套、双列表、高位64位GUID、显式0、uint32高位及截断拒绝；不填未知root14/15或空能力占位。GlobalCombat明确临时策略仅支持服务器身份验证，见[DATA](CLIENT_72_D02_M0_DATA_20261116.md)。

相关10套166项回归全部通过，涵盖M0、Born19、账号创建40、准入36、Account Guard29、Player Guard15、Vitals22及有限wire。随后M0 Ready仅发送已验Rsp，Init/Done在准确未知wire处关闭，保持LOADING并且不发送旧通知/伪成功；Post只在已LOADED时回已验Rsp，跳过任务/cutscene。新增停止边与SceneInfo独立wire3项通过；已有四套场景关联回归也通过。最后合并结果及正式构建另记录。

最终同轮16套**524项全部通过，失败/错误/跳过均0**（14秒），包含上述M0、账号/保存/出生/HP与Avatar/Inventory raw、四套Scene关联。该轮在最后M0握手停止修改后运行；原始XML已保存至`local/migration72/d02-m0-integration-20261116/final-java-results/`，套件汇总为同目录外的`final-java-results.json`，日志`m0-final-related-regressions.log`。没有以测试数量代表入场完成。

M0独立config/resources/local-data已准备并通过portable配置检查，未启动游戏实例。SceneTeam/Entity出现、SceneInfo Team/level/ability子图和自然SDK路由仍有准确必要缺口，当前构建版本声明门也未变更。没有可请求用户启动的实机包；不能以birth-ready、编译或健康替代入场。继续成熟协议及独立旧号资源/恢复工作；必要wire/会话未齐时不启动猜测协议。

原始JUnit、失败、构建和运行材料只留上述忽略目录。Project维护任务与完成范围；Harness索引本报告。M0通过也不关闭完整D-02。

## 正式便携构建与实际部署状态

运行实现本地提交`366de3d`、`d7cdda0`，规格/复用/SceneInfo限定研究提交`7ff683a`；三次均核对指定Author/Committer，未推送。随后实际执行`tools/local_windows.ps1 -Mode build`，25秒成功；JAR SHA-256为`42636ecc42bd9b5c1a98a89fcde662e293f343f589b5e0efd36cf6ec1214e217`，内嵌`d7cdda0`匹配。旧JAR SHA`8f093e19b839ff1263984c596584d1ae12e6d4fd841d82562aaf30f998267291`已私有备份。旧46306类→新46319类，60变化、13新增、0删除、46246保持；变化和新增运行类全部与当前编译输出SHA一致。构建证明`portable-build-artifact.json`与日志`portable-build.log`位于上述忽略目录。

新JAR已复制到独立M0实例，同hash；config/resources/data均指向该实例，Mongo配置27027通过portable路径检查。未启动此实例：实际8088 TCP、22101 UDP及游戏JAR进程均0；27027仅为本轮拥有的独立validation Mongo，用于唯一测试库。没有正向服务健康、运行JAR SHA或loaded补丁验收对象，也未安装/构建客户端补丁。patch/resources子模块HEAD及原dirty范围保持，旧运行数据库和data未操作。版本声明门保持原状，不能由本次构建直接启动7.2客户端。

`D02-M0-BUILD`只覆盖上述打包和身份核对；`D02-M0-RUN`仍因SceneInfo必要子图/Entity出现与自然SDK路由前置而阻塞。当前实验包不会请求用户ready。旧号资源与服务器完整冷恢复是下一可独立任务，不受本M0有限资源ready代替。
