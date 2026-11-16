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
