# 千音雅集 7.1 协议字段

## 本次范围

补齐千音雅集七条消息及四个嵌套结构，共 84 个字段。
新增 12 份 `.proto`，包括保留未知数值的投票枚举；它们使用独立类型，
当前旧活动的 `MusicGameStartReq`、`MusicGameSettleReq` 和 `UgcMusicBriefInfo` 继续各自使用已有定义。

| 类型 | 当前包号 | 字段数 |
| --- | ---: | ---: |
| MusicGameBookAllDataNotify | 8786 | 11 |
| MusicGameBookStartReq | 27235 | 3 |
| MusicGameBookStartRsp | 29833 | 3 |
| MusicGameBookSettleReq | 3829 | 17 |
| MusicGameBookSettleRsp | 24067 | 9 |
| GetRecommendedMusicGameBookReq | 8475 | 2 |
| GetRecommendedMusicGameBookRsp | 23303 | 2 |
| MusicGameBookRecord | 嵌套 | 4 |
| MusicGameBookSettings | 嵌套 | 7 |
| MusicGameBookBriefInfo | 嵌套 | 21 |
| MusicGameBookSocial | 嵌套 | 5 |

全量消息的字段 3 关联原生 `JEKECENHEAO`，字段 10 关联 `NMEKCJDJEGB`；
推荐响应的字段 7 关联 `FEHNOKHDMHN`，其字段 4 再关联 `GBFKJGCDBFA`。
这些类型、重复属性、字段号和数值类型均已配对。结算请求两个独立的浮点字段分别在 6 与 773，
其余高字段号、64 位字段以及数组也保留完整编码。

字段含义尚未配对的部分保留原生混淆名。结算响应的原生枚举 `MNDIJGCMEDL` 已确认类型，
各数值的业务含义尚未核实；暂只声明 protobuf 所需的零默认值，其他数值可解析、访问和原样重编码。
目前不能据此认定曲目解锁、收藏、成绩、标题和奖励标志的具体含义。

## 证据与验证

使用本机提供的 7.1.0 客户端，重新校验三份输入：

- YuanShen.exe：`7f89938da606c1281659607d464702cddb9a09a7d4320a196630f60a811ec38e`
- global-metadata.dat：`469eccd43aa48fe7a2df4e1caf66fa1268a17f5a4a03fa209e1427e467cb0161`
- startup-metadata.dat：`90848967d3f9432e7a13caffcec999bf457ac3be1f2ae86336443a63acbaadcf`

原生读取分支、发送分支、数组编码初始化、类型布局和包号常量，与实际 `.proto` 逐字段核对。
开始请求、结算请求和推荐请求使用原生发送函数；其他消息使用原生读取函数。
曲谱摘要数组的按位分支及压缩／非压缩两种输入也已分别核对。
这里验证的是当前可执行文件的原生编码路径，完整客户端热修复消费者仍需后续核对。

新增 `MusicGameBookProtocol71Test`，先得到 117 项、110 项失败、零错误与跳过的结果。
补齐协议后 117 项全部通过：字段布局、包号、真实生成类编解码、嵌套类型、11 个数组的两种输入、
完整 64 位数值、两个浮点字段、未知枚举数值、未来字段及旧活动类型隔离。
向量按原生编码独立构造，并非实机抓包。

与登录等 7.1 兼容、物品使用、道具放置、圣言自明机、重塑及伴随通知协议共同回归，
7 个套件、155 项通过，零失败、错误与跳过；Gradle `test`／`jar` 成功。
源码与构建容器逐字节一致，产物与最终编译目录逐类一致。
产物只新增这组协议所属的 58 个生产类，已有生产类没有变化。

构建产物：`debug/aster-mid-20261011/batch314/music-game-book-wire.jar`。
SHA-256：`8594b215a98007d6455d604e11acbccc8e6197c4a412a924de1301507403b43f`。
同目录保存原生字段核对、初始失败、专项及最终回归结果和 `verification.json`。
资源仍为 `dac2a9d43332ef51879522cf39da2831b0ca0543`，外部检出与子模块均干净。
固定 AstaPS 参考检出仍为 `7cc148d1f752a43d4b9146b3b73f26875902546f`。

## 继续接入的范围

参考 `MusicGameBookSystem` 当前发送空通知；本次补齐它缺少的消息定义。
道具使用入口、资源曲目与各数组的业务对应、解锁条件、开始／结算处理、成绩和设置保存、
奖励领取、推荐／自定义曲谱及完整客户端表现仍需继续接入。

本次没有增加这些请求的处理器，运行服务继续使用上一轮构建。
原先反馈的客户端错误也没有新增实机复验。
