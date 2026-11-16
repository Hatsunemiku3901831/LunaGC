# D02-M0-SCENE-RAW-JAVA

本项把来源人已完成并固定的SceneAvatar/SceneTeam raw合同转成独立Proto，未编写完整sender、升级Scene握手或运行客户端。三层来源及语义剩余按[SceneAvatar](CLIENT_72_D02_M0_SCENEAVATAR_WIRE_20261116.md)和[SceneTeam](CLIENT_72_D02_M0_SCENETEAM_MIN_WIRE_20261116.md)读取，不能用本项Java编译/测试代替目标实机或fresh原生对象构造。

## 固定来源与schema范围

- SceneAvatar专题SHA `aefcfa3e4e769b5ccdcc163647f62444a08b19977914b280ed8e63ad80eac196`：41868的13个无变换scalar、7个u32列表、2个u32/u32 map及6个已有批准内容的引用槽，共28字段进入`Client72M0SceneAvatarRaw.proto`。26952、17469、27216三个未知内层不声明、不造opaque空对象。scalar名字不自动批准AvatarID/depot/GUID业务值。
- SceneTeam专题SHA `01cca3ca3dc17c0774e38da334810131f8434bcf941a842602025752c03f40e4`：76429全部17个外字段、83228四字段进入`Client72M0SceneTeamRaw.proto`；86588仅声明19个真实owned样本已证的bool/tag13。其list/tag5保持.cctor-only来源等级，没有因本项Java适配提升为reader闭合。
- 73976五字段、83320三个字段及89710列表从既验`scene-info-raw-observations.json`复用，文件SHA `f52f41a817241646d63d692b120d5c134c1b30dfe8b2da5d440de5d9557f7c75`，进入`Client72M0SharedSceneRaw.proto`。83320的tag2/3保持fixed32，不赋予name/override角色。34045/27196/27246/48178/53824直接引用已批准65型`Client72AvatarWire.proto`，不复制旧语义schema。
- 初始16796只引用此前已证scalar子集；后续按下节UID/GUID桥增加已证Avatar oneof/tag10，root list仍未闭。83228外层完备不代表内层实体、业务flag/能力状态或完整场景完备。

optional标量只用于Java显式零/false合并存在性；packed输出是同已批准packed/unpacked primitive输入合同的适配，不声称恢复原Proto语法。所有Java由Gradle/protoc生成，未手改生成物。

## 实际回归

`M0SceneRawGraphWireTest`新增10项，首次执行即通过；连同Entity raw12项及Scene身份2项共24项，25秒，失败/错误/跳过0。独立手写wire按primitive key/varint/length规则构造，不用待测Java builder产生预期值：

- 13个scalar的非零/u32 max/u64 max，保留2^53以上GUID；不经double。
- u32 packed与unpacked同字段追加、两独立map、高位u64叶子及既验复杂子图非空嵌套。
- 两个Team列表元素、不同Avatar/Weapon GUID及实体ID、Scene ID、能力retain/copy两份独立状态、两个fixed32原字节、高tag549/645。
- 86588的0/1/2/80000000/FFFFFFFF五个既有native样本及Java bool归一化；不存在/显式false分开。
- 截断、错误长度及未知内层未声明的schema边界。错误断言只针对Java解析器，不冒称本轮新执行了目标native错误路径。

命令：

```powershell
.\gradlew.bat test --tests emu.grasscutter.server.packet.send.M0SceneRawGraphWireTest --tests emu.grasscutter.server.packet.send.M0EntityRawWireTest --tests emu.grasscutter.server.packet.send.M0SceneEntryWireTest -PskipHandbook --console=plain
```

原XML和汇总归档于`local/migration72/d02-m0-integration-20261116/scene-raw-java-results/`；首轮日志为同根`m0-scene-raw-graph-wire-tests.log`。本项没有新native CPU、helper/model、A返回、客户端、DB或补丁操作。正式portable构建25秒通过，内嵌89b2ceb，JAR SHA `74049899d59c4a8b2652aeaba2ff14b9fb5ca81ae2e94acd08eb41f1caa001f3`；3变化/33新增class与build/classes全部匹配，0移除。两个独立实例仅预备同SHA未启动；post-build只有自有测试Mongo27027，HTTP8088/game UDP22101/游戏JAR进程0，无健康或运行JAR正向对象，未装补丁。原件scene-raw-build-artifact.private.json、scene-raw-post-build-runtime.private.json及scene-raw-portable-build.log在同一ignored根。完整SceneTeam/Entity/SceneInfo和自然认证仍有准确未闭边，不发送完整场景包。

## SceneInfo非空身份增量

在Peer及能力最小性研究同时，按已证对象关系继续独立实现两个非空identity子树：21761/type34208的root14→44512.tag5来自同World.getLevelEntityId，root15→49723.tag7来自同TeamManager.entity.id；真实非零且两ID不同，不能写90000004/90000002模板。此前六个Avatar/Weapon/当前实体/token关系保留。root14.tag1 authority-key及能力子图仍未宣称闭合，不填未知bool/hash或空能力对象。

`Client72M0SceneEntry.proto`仅增加上述真实ID字段；实际M0 projection与独立golden `720228047a023805`、真实出生/保存/冷读后的World对象交叉全部通过。M0Birth1、SceneEntry wire3、SceneHandshake15共19项，29秒/失败0/错误0/跳过0；原始结果归档同ignored根`scene-nonempty-identity-results/`。Init/Done仍在准确缺口处停止，无完整场景发送或客户端验收。此增量之后另行正式构建，不追认上节89b2ceb产物已经包含新字段。

给来源执行者的待接清单分开处理：Peer raw闭后用真实消费保持localAuthorityKey/OOJ/entity+42C的关系，不因官方英文名缺失阻塞；44512.tag2新实体已有非null才Clone分支，49723.tag4的null状态、tag1/89710 count0是否跳过hash循环、tag3/80154空列表合法性逐个核既采源。未知72829及float不造默认值；真实MP/Team无追加可选global能力和Avatar5基础能力仅是服务器状态，不是所有network能力分支必须存在或必可省的证明。

## SceneInfo普通新实体最小状态

来源[MIN-STATES](CLIENT_72_D02_M0_SCENEINFO_MIN_STATES_20261116.md) SHA `602d8a5f4a070b0573929851a62708878a5e4c3c7d8723550e5fffdef47d4355`分别闭合四个必要性问题：新World与Team分支的34045为null时真实跳过Clone；89710控制对象需非null，其正常零元素83320列表清缓存并直接返回，不调用两个hash lookup；80154正常空列表清状态，不允许拿null代空。替代分派及已有实体+426非零的后继仍未闭，不外推热重入。

M0 projection现在明确输出Team control对象、0个83320、0个80154；没有创建空83320记录或空34045来装作初始化。零列表在wire中省略，control对象保留`0a00`存在性，不能在control内部再写一个`0a00`假元素。单独raw80154两字段来自既验SceneInfo raw，只声明而不赋未知业务key。实际World/Team若已有runtime能力则拒绝该零团队策略；不走legacy默认团队能力getter。

第一组27项27秒通过：真实M0出生/保存/World交叉、Peer7、SceneEntry4、握手15，普通Init/Done仍准确停止。原件`scene-minimal-states-first-results/`与`m0-scene-minimal-states-tests.log`留同ignored根。本增量尚待后续统一正式构建，无客户端或网络发送放行。

## 同玩家UID与Avatar GUID的非发送投影

固定后继来源[ENTITY-APPEAR最小依赖](CLIENT_72_D02_M0_ENTITY_APPEAR_MIN_DEPENDENCIES_20261116.md) SHA `49e9451fa9c97e04332ee79ae19f3e0e7440ac185b2fa688a037356f2a5817cc`；UID receipt SHA `d2217a9f9251c485e8cf13070d5ad624395d489696c2448efa329bdfb5038f17`、GUID/最小性receipt SHA `b71a89c45aee6bd665e01d9a60b5a03ddd5d9706177ad366bf07ae3e19b3a8fa`。

`M0AvatarIdentityProjection`只构造同一实际Player/Avatar/Weapon的身份行，不是Packet sender。76429.tag3与41868.tag1的UID来自正常`Player.getUid()`；依据既验SESSION A-04d5j/B-02c7中TokenRsp tag9→15217AA90→session58013+168/LastUid的四个原生边界样本，以及新消费者把这两个字段分别与同一session槽比较。正常`PacketGetPlayerTokenRsp`的实际UID也在真实M0出生夹具中交叉验证；它独立于Peer的localAuthorityKey。

41868.tag3使用同Avatar.guid：localAvatar消费者1507E593C读取该u64，599C/599F交既验14E9B2C60→156F6F510的Avatar GUID lookup并核返回18163模型；不另分配Scene GUID。76429中Avatar/Weapon GUID、两实体ID和Scene ID仍来自同实例。16796只增加已证case10 Avatar oneof，不填用途未闭的tag2、不猜MotionInfo或未知子图；完整Entity根列表仍未声明。

新增四个UID边界和一个独立高GUID手写wire，共5项；与真实M0出生/保存冷读、Peer、SceneInfo、Scene raw和握手回归共6套54项，10秒，失败/错误/跳过0。独立向量`520b0807188180808080808010`将UID7与GUID9007199254740993分开编码；真实对象测试核Token UID、Avatar/Team UID、同Avatar GUID及Peer key互不混用。原XML归档`avatar-identity-java-results/`，汇总`avatar-identity-java-results.private.json`，日志`m0-minimal-state-uid-tests.log`，均在同ignored集成根。本增量未放行完整SceneTeam/Entity/Peer通知，正式构建另记。

## 旧号快照出口短核

此为精炼桥交付前的短核；后继[旧档武器projection](CLIENT_72_D02_OLD_WEAPON_PROJECTION_20261116.md)已独立闭合正affix组→原零基refinement。以下精炼“未闭”按当时阶段读取，Furniture/TPS/Relic其余缺口保留。

有限旧号mode已保留全部存档，但完整网络入口仍拒绝。代码及[库存生产合同](CLIENT_72_INVENTORY_PRODUCTION.md)、[Equip合同](CLIENT_72_EQUIP_CONTRACT.md)当前只批准Material与Weapon/Relic有限字段；raw15型47字段不是所有分支业务合同。Item7/8/9/10不能按形状命名Furniture/TPS。现生产Store会略2331 Furniture与8 TPS；Weapon affix_map已证“EquipAffix组→组内level key”，保存refinement的映射尚未有目标桥，不能默认减1或把Java组合lookup当客户端身份；Relic2/3/4/5/7/8仍未闭业务角色。

下一输入分别是准确Item分支typed消费者、Relic字段实际使用或保存精炼与组内key的桥；可用已有raw继续单小合同，不需要全Manager或全97包。未找到可直接照搬的完整Furniture/TPS/Relic生产实现；这个有界检查不是宣称所有外部来源穷尽，也不阻已有M0女主/武器身份子集。

## 最小身份与状态正式构建

SceneInfo A/B/C与UID/GUID独立实现提交后，经`LUNAGC_RUNTIME=portable`及`tools/local_windows.ps1 -Mode build`真实构建22秒通过，内嵌c2faff7。JAR SHA `533dce1ebab1cbd5d147bf769be025cdf19aae16d101ef16721cf49e6cf182e4`，98266970B；相对上一89b2ceb产物，46365→46386 class，18变化/21新增/0移除。所有变化和新增class逐项等于本轮build/classes；没有以测试缓存或旧JAR代替打包结果。

同SHA只预备到两个独立InstanceRoot，没有启动游戏JAR。post-build HTTP8088、Mongo27017、game UDP22101及游戏JAR进程均0；只有既核自有测试Mongo27027/PID51240。日志/健康/运行JAR正向验收仍无部署对象；补丁和客户端未操作。未部署准确前置是自然SDK/service/mode/批准route，以及Entity根列表/Motion等未闭必要合同，不是重新要求全原版资源或全Manager。

原件同ignored根`minimal-identity-portable-build.log`、`minimal-identity-build-artifact.private.json`与`minimal-identity-post-build-runtime.private.json`。上节历史产物及各子项测试范围保留；客户端六项仍0/6。
