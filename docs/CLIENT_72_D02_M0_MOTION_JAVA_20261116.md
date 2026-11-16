后继已接M0首次有效出生的零面向一致状态及显式tag2 Vector；既有非零只加载保持，partial投影拒绝非零，通用旋转未闭。下面raw/position阶段的旧限制按各阶段读取。

# D02-M0-MOTION-JAVA：有限Motion原始结构

本项只将[Motion外层研究](CLIENT_72_D02_M0_MOTION_WIRE_20261116.md)（最终SHA `713be733e65adf45c606d66e9f96d029c4dce2bb9bf8715893090e836bd081b3`）的成熟部分转成Proto/Java，不给四个Vector套用位置、旋转或速度角色，不发送完整Entity通知。

## 来源与实现

partial-contract原件SHA `ed36d351db2a254da765de698653e26adb1f446043f19edbe4fc3f5f34da87e9`，逐字段保存准确helper/store及嵌套类型；bindings receipt SHA `ce5c756321dd5b3f9c134253ae0fc43903b36b70dd5aed54a110456c22c9eabb`。

49847唯一普通reader `152F32D80`完整1487B/325条严格解码及回拼，body SHA `8b1cdddce487c947eddf4fbdeaa94c5e7dc80f086f647d1736d4505ddeacf99b`。普通已有对象分支可独立绑定四个singular Vector：tag1/+30、2/+40、3/+18、6/+28；tag5/+20为RepeatedField84343。六scalar分别为tag4 raw enum/u32、7/8/10/11 u32和9 u64，直接读取后identity store。

Vector84343直接复用既验`Client72SceneHandshakeNested.proto`及`enter-scene-contract-observations.json`，SHA `0351840ad6f756d7adcc3c75b6bd0142406bdbeb487e71d279a771e38136892c`。x/y/z为float32/tag1/2/3，不重新读取Vector body，不把shared Vector的轴名当Motion槽位用途。

新增`Client72M0MotionRaw.proto`的Raw49847前11字段；16796增加tag4的raw引用。tag100的具体codec在本阶段仍未批准，不声明；fresh nested分配的准确A依赖保留。普通本地Avatar消费者要求Motion及其+40/tag2指针非null，只证明输入结构前置，不等于整个消费者已执行或角色已落地。实际业务投影仍不生成这份Motion。

## 实际回归

`M0MotionRawWireTest`新增11项，连Entity17、Scene raw10及SceneInfo4共42项，21秒首轮全过，失败/错误/跳过0。所有预期wire手写，不从待测builder产生golden：

- 四槽使用不同Vector组件；两元素repeated保留顺序。
- 五u32字段的0/1/1357/80000000/FFFFFFFF；u64大于2^53、大于2^63及最大位型。
- 已有nested合并显式0、IEEE754负零原bit、Motion/Vector不存在与非null空对象分别检查。
- Avatar UID/GUID同Motion嵌套共存、未知tag100保留及Java截断/错误长度边界。错误断言不冒称新native错误路径执行。

原件`local/migration72/d02-m0-integration-20261116/motion-raw-first-results/`、`motion-raw-first-results.private.json`及`m0-motion-raw-first-tests.log`。正式构建和必要源后继另记；完整Entity根列表、Motion业务值和完整sender不因这组raw通过而放行。

正式portable构建已包含本项：27秒、内嵌0b7c67e、JAR SHA `d6bd05373dbadad0e9be8538d34730d6133a23882c87c19c360d2636d25a4e1e`；18变化/5新增class全部匹配输出，两个独立实例同SHA只预备未启动。详情见[有限核心正式构建](CLIENT_72_D02_FINITE_SERVER_BOOT_20261116.md#正式portable构建与未部署)。

## 实际位置与静止出生速度的部分投影

后继成熟partial合同SHA `2d7adb8d66c88351c3a5a620ee08c4cafcdeacdba5b6fb9c45578e0536635d06`及receipt `9a8aef2ab1191feb80a30493db3614c50039c1fc2fcc5ff02a8a10b5291b5508`，只读复用旧1102B/338条完整listing，文件SHA `425e8e64afb31e59b7ce85e8444f242d724068be9c5f3682cd5ffdcdb000309a`。metadata把given18163+C0明确绑定MoveSyncTask；boxed header16，position/member10→embedded C0，velocity/member1C→embedded CC；实际原输入分别由Motion.tag1/+30与tag3/+18直接xyz写入，没有新A对象或未知数学输出依赖。

未发送的`M0AvatarIdentityProjection`现增加Raw Motion：tag1取同实际AvatarEntity.getPosition（与Player.position同对象）的三个float原bit，tag3按明确M0静止出生策略写三轴0。没有造坐标、改变存档位置或借EnterScene轴类型猜Motion角色。tag2虽已知是rotation输入，完整转换/角单位未闭，因此仍不填写；该行不是满足全部非null前置的完整可发送Entity。

新增一条独立手写wire把position(1.25,-2.5,3.75)与非null三轴零velocity区分；真实M0出生/保存/World fixture交叉三轴原bit与同Player身份。4套40项26秒全过，失败/错误/跳过0；`motion-position-velocity-results/`、`motion-position-velocity-results.private.json`与`motion-position-velocity-tests.log`留同ignored集成根。旧raw列表/float/64位断言一起保持；本项没有新native/模型/客户端或完整消息发送，正式构建后补。

位置/速度后继正式portable构建26秒通过，内嵌9d11ae8，JAR SHA `bd9856cbb43110a66720ea712666143f4b6566928fa3f2f5eeea6554df5f1330`，98290007B；4变化/0新增/0移除class全部匹配输出。两个独立实例仅预备同SHA，HTTP8088/27017/game UDP22101/游戏JAR进程0，只有既核自有测试Mongo27027，无健康/运行JAR正向对象或客户端。自然SDK、Peer真分支/Entity根列表及rotation等准确剩余不因此放行；原件`motion-position-portable-build.log`、`motion-position-build-artifact.private.json`、`motion-position-post-build-runtime.private.json`在同ignored根。

## M0首次Born零面向一致状态

固定来源[MOTION_USE](CLIENT_72_D02_M0_MOTION_USE_20261116.md) SHA `5e5157db61f022a357a70c8e012f38d1908db51ce29a9c9b70f923e374dfae39`与零面向policy SHA `42ebfcbe5f5b22ec46610ae827d9339e54983d8e00f4b6b027c7c171f0ef752b`。tag2 rotation-input角色与Vector wire已闭；服务器仅为显式M0独立新号的首次有效Born选择(+0,+0,+0)，不是恢复未知数学函数或Quaternion输出。

`HandlerSetPlayerBornDataReq`通过已有首次出生/选择/命名/资源校验后、任何最终保存前设置真实Player.rotation正零；同AvatarEntity.rotation就是同Player对象。后续普通Player/Avatar/装备确认保存链不变。无onLogin/load赋零，没有改旧号，重复Born在原116拒绝点返回；真实出生position保留。

未发送的Motion projection明确保留tag2零Vector message，内部三个float可省略，canonical为`1200`；tag1取实际位置、tag3保留明确静止速度。遇已有非零rotation，partial projection明确拒绝尚未批准的非零单位，不重写其保存状态或假造零。**已有M0非零朝向只保证正常load/onLogin保持，不能宣称任意朝向的客户端重登入场已经通过。** 原SDK/完整Peer/Entity等停止边与0/6保持。

实际6套66项34秒全过，失败/错误/跳过0：M0真实拒不支持选择及空白名时不改原非零旋转；首次成功前数据库、Player、冷读和同Entity三轴正零bit一致；重复Born116不改刻意设置的live非零或原BSON；非零经正常关闭保存、第二新Player.load及onLogin不重设，并被partial Motion拒绝。独立手写wire区分零Vector存在/省略且保位置。普通Born19、Born兼容15、Entity17、Motion13和旧号完整生命周期1共同验证对应边界，没有重复全524。

原XML/汇总`m0-zero-facing-results/`、`m0-zero-facing-results.private.json`，日志`m0-zero-facing-first-tests.log`在同ignored集成根。首次执行即通过；没有新native、客户端、字段猜名、资源或数学返回模型。

与武器精炼成熟项一次正式portable构建26秒通过，内嵌49b4ae6，JAR SHA `2df2d0954c105e917cbe5bc08d15add0852a51c3ae4663f3de9b0a99dd168e03`；7变化/1新增class匹配输出，双实例同SHA仅预备未启动。详细class/运行边界和未部署原因见[统一构建](CLIENT_72_D02_OLD_WEAPON_PROJECTION_20261116.md#正式portable构建与未部署)。一般非零旋转及任意朝向客户端重登入场仍未验。
