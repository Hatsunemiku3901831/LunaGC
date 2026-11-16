# D02-M0-PEER-PRODUCER：本地authority key写入入口定位

## 中文有限计划

只研究已证可变静态u32 `23977.OOJNMMAIJMI` / cell14586F438。前置AUTHORITY-CFG专题SHA `574f3bee205e54aed5f259a86c9698a7c252d29ee6018269abb39ca2cbff9d60`、MIN-WIRE与其他已验专题保持只读。本项不能从IsAuthority名称、初始0、+42C参与比较或旧四u32 Peer候选直接命名Peer消息。

1. 固定同CN7.2 EXE/global metadata/startup三输入、profile、metadata/signatures/commands；先在已采Scene网络/.cctor完整源及其精确source索引中搜索14586F438引用，区分读、写、初始化，按原件hash复用。已有source足够时只分析实际参数与store值来源，不重复取源或读global当前值。
2. 对实际write方法只核普通metadata/AOT/typed参数和已有登记；分别记录CmdID→类型、类型→业务、参数→store证据，未知保持未知。旧Peer四u32/9633仅用于业务差异对照，不用于选中类型、tag或正包号。
3. 若既采源没有初始化以外writer，做一次固定EXE、单目标14586F438的RIP相对写入候选定位。明确opcode形式与覆盖边界；所有raw候选保留，必须经完整指令/普通AOT/唯一pdata及typed参数核查才可晋级。候选多只返回准确表，不挑最近、近名或旧schema。定位阶段不读取新writer正文；0-pdata只给精确entry窗口CFG后继规格，不擅取window。主线后续单独批准的一条typed writer source在下文分项记录。
4. 准确A/B/preview冻结、未知VM、边界非唯一/漂移或新安全拒绝均按实际停止该依赖，不换工具重试、不扩全族/调用树或派Astra。producer不是Peer则明确排除。无目标CPU、客户端、hook、global数据新读或默认模型，raw仅`local/migration72/d02-m0-peer-producer/`，唯一tracked产物为本文。

验收为找到实际producer/typed消息的最小入口，或完整有限候选与下一唯一source规格；不把“未分析”作阻塞，也不因局部定位放行完整Peer握手、M0或entry-ready。

## 结论与输入

定位得到唯一直接带已登记消息参数的writer候选后，主线批准该一条完整source。实际静态源已经证明 **Cmd28392/type19176的typed消费者m142764，在普通分支把消息+24/u32直接写入OOJ槽14586F438**。它是本地authority key的真实网络生产来源，不是仅因四u32同形选中的旧Peer类。当前可给该消息“进入场景时传入本地authority key”的用途；原英文消息名、其余字段的全部业务角色和完整握手成功仍未恢复。

本项固定profile SHA `a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d`；三输入为EXE450927000B/SHA `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`、global metadata83644188B/SHA `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`、startup4116148B/SHA `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`。metadata缓存SHA `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f`，signatures SHA `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe`，commands444706B/SHA `8dea972f3438e9c08adc801a90e00a13b97dc022477f0f56604fa238dee8e029`。每项实际核对；OOJ当前值仍未知，初始化0不替代当前key。

## 既采源精确引用：一个初始化writer、两个read consumer

先按已采source的精确RIP目的地址核查；只读复用原件，不新增其函数读取。

| 固定原件 | SHA-256 | 对14586F438的精确来源 |
| --- | --- | --- |
| `d02-active/main/scene-runtime-context-third.json` | `bf9efd6225cd2836286b3a301bb2ea2f33dab6c6ddfb103d53aeed8f10db81d4` | 23977.cctor中14D2CA658，u32 write立即数0 |
| `d02-active/main/scene-entry-third.json` | `917a5ad0505c6e90e57c4080f0521e26589fadd0ccfb29eb3257363b8f2a9780` | 14828FEEA，u32 read→R8D，随后14828FEFE交14828F870 |
| `d02-m0-sceneinfo-min/584597-source.private.json` | `e2476879b75b6146f4ee93392a369cbd332278fb80598441d1087a407f47748f` | 1515409B8，u32 read/compare，既验authority分支 |
| `scene-deep-observations.json`（protocol目录） | `bed41fc74af5ef39b951c6d57834c10a56b0577da1166957d5221f19ada4acd5` | 14828FEA0的同cell索引，无额外write证明 |

前三行相对`local/migration72/`。另核已采219141源，没有该global引用。除.cctor初始化外，这组既采源没有后续writer；这是限定输入的负结果，不声称全项目不存在writer。

14828FEA0的既采完整源（code SHA `c6362f35fe6a777a8f0eed5c7c371d105e7a05adebef4ad1443459e24b066ff5`）明确在FEEA读取当前OOJ、FEF1传栈第五参数1、FEF9将消息49723的实体ID送RDX，再FEFE调用14828F870。这说明本地Team路径实际使用该可变key；不把.cctor初始0充当Ready阶段的网络通知或当前值。

## 一次单cell MOV写候选定位及边界

实际扫描仅一个目标起始地址14586F438，遍历固定EXE三段file-backed executable section：`.text`35394048B、`il2cpp`325725184B、`.upx0`15493632B。覆盖的raw opcode为88/89（register→memory）、C6/C7（immediate→memory），ModRM mod00/rm101、精确RIP目的地址，保留无prefix与单REX的重叠候选。覆盖普通1B/4B及REX.W下8B MOV候选；未覆盖寄存器间接alias、目的偏移+1..+3、算术/RMW/SIMD、absolute moffs及其他legacy/address/operand prefix的真实起点。未命中这些未扫形式不构成不存在其他writer的证明。

共7个raw候选、5个不同containing pdata方法；两对是REX/无prefix的重叠raw候选，定位阶段没有把它们自动去重为真实指令。所有候选分别核固定exception目录SHA `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d`、唯一containing row、普通AOT及typed参数，未以最近metadata地址猜边界。

| raw site / bytes | 唯一pdata / 大小 | 普通AOT方法 / 参数 | 定位阶段状态 |
| --- | --- | --- | --- |
| `14CD63424 4489050DC0B0F8`；`14CD63425 89050DC0B0F8` | `[14CD63160,14CD63718)` / 1464B | owner18753/m142764 ANAODHPHHNM，参数19176 | 唯一直接registered Cmd输入候选；后经单独批准source判定前者真实起点、后者为内部字节 |
| `14D2CA658 C705D64D5AF800000000` | `[14D2CA110,14D2CA6A8)` / 1432B | owner23977/m185483 .cctor，零参 | 已有完整旧source确认初始化write0，不新取 |
| `14D2CC122 C7050C335AF800000000` | `[14D2CA820,14D2CC3B3)` / 7059B | owner23977/m185491 GGDILPOJMCP，零参 | raw候选，未读body、不据零立即数字节命名清理逻辑 |
| `14D2CCD40 448905F1265AF8`；`14D2CCD41 8905F1265AF8` | `[14D2CCD30,14D2CCDB9)` / 137B | owner23977/m185506 PKFLHADCKHL，单u32参数 | raw候选；两起点/具体store均未新读验证 |
| `14D2CD5CA C705641E5AF800000000` | `[14D2CD5B0,14D2CD6B4)` / 260B | owner23977/m185510 LNCGNKLMFBJ，零参 | raw候选，未读body |

后面三行没有为探讨Peer而自动取body；本轮只新增首行1464B source。Cmd候选来自真正write候选所在方法的typed参数与commands登记，不来自9633、旧四u32布局或混淆字段近名。

## 单独批准的typed writer：root19176→OOJ

主线审定唯一typed候选m142764，批准完整source前再次核固定三hash、AOT cell1429BC230（`6031D64C01000000`）、唯一pdata `[14CD63160,14CD63718)`。1464B/341条严格连续解码记录，EOF和逐字节回拼一致，code SHA `023e0d7b71c4bdef29128fff083bd4e185794400da6294bd41b107b25e5eb9b3`。这是完整pdata字节覆盖；其中有保护跳转和填充，不等同于完整handler可达语义或执行验收。

方法owner18753 LKMLKAHBGDN，parent为MoleMole.GlobalManager11247；m142764 ANAODHPHHNM只有一个非by-ref class19176参数。该参数在14CD63169保存R14，后续没有更换其来源。实际关键指令为：

| PC | 实际关系 |
| --- | --- |
| `14CD63413` | `mov r8d,[r14+24]`，取typed消息的AANCEPCPHKJ/u32 |
| `14CD63417 / 6341E` | IFix标志分支；本项未读当前标志，普通分支继续 |
| `14CD63424` | `4489050DC0B0F8`，真实完整指令`mov dword ptr [rip-74F3FF3],r8d`，目的地址14586F438 |
| `14CD63425` | 同一指令内部，**不是**有效完整指令起点；原raw alias记录不删除 |

在这条普通store路径，消息+24到global无算术变换或CALL间隔，故可用“本地authority key”用途别名；其wire/tag仍待reader，不能把unknown raw值直接发入M0。

其他实际消费分别记录，不套四u32旧名：+18 MCCMGLDOECH在14CD63199交既验14CD5F7B0加载任务/token查询（旧完整source SHA `41522a2dd9266cd961fb8d2e8ff8cc7b05ed00636e219bfae41adbc3c8ce6f5b`）；+24还在14CD6348D与当前PlayerModel58013+168参数一起交156633000，callee未取；+1C OHNANNOOMCJ在14CD634B9/634BD写另一个静态槽14586F4A4，其host用途尚未具名证明；+20 OHAKDDILKKB没有在本普通source直接读取，保持未知。

任务对象+40假分支可从14CD631DA直接跳14CD63413进入上述store。+40真分支中，14CD63298跳15A4DCCB7进入未知保护后继，本项未跟、未建模型，也不借普通分支证明该分支已闭。body没有直接1405336C0调用；所有其他helper/IFix/global当前状态/生命周期只保留静态调用边，没有执行或补默认值。

## 三层现状与下一精确source

第一层：固定commands确认 **28392→19176 IAKGECCPGDA**，getter152D746D0/m146089；不是9633。19176直接继承Proto67861，完整metadata仅四个u32字段：+18 MCCMGLDOECH、+1C OHNANNOOMCJ、+20 OHAKDDILKKB、+24 AANCEPCPHKJ。这种形状本身没有用于选中候选。

第二层：typed消费者在普通路径把+24写入已证local authority key槽，+18进入既验token任务查询；可给有限进入场景authority通知用途，原英文名、host/scene字段和整handler仍有明确未知。第三层：当前尚无19176字段tag/reader变换，不能发完整Peer sender。

主线已条件批准下一唯一reader m146084@152D740F0：先核slot19、唯一非by-ref stream52310参数、普通AOT、唯一pdata且body<=4096B；门过后只取此完整source/strict回拼，不取.cctor、ctor或新helper，不运行CPU。后续wire独占写新专题`CLIENT_72_D02_M0_PEER_WIRE_20261116.md`，本阶段producer结果固定后不回写其他已稳专题。若reader保护/边界门失败，保留准确停止并给下一最小规格，不代填字段。

## 原始证据、实际失败与验收范围

以下均相对忽略根`local/migration72/d02-m0-peer-producer/`：

| 原件 | 大小 | SHA-256 |
| --- | --- | --- |
| `run1/failure.private.json` | 837B | `7150ad58ae978ea6d9c0e8cbfb0f57fa901aae679e1114de793bdda4f9a81d91` |
| `locate_peer_producer_v2.private.py` | 13625B | `2368debb5035e58b981c028bb2085f1939e6d947e3a4cac31682bc95af8dcfc9` |
| `run2/existing-reference-index.private.json` | 3812B | `8a479318efe8850db6f705acbaa01d5ceb9f1763a35a170aca56909110bf5992` |
| `run2/raw-store-candidates.private.json` | 10556B | `bab7f759eacca94693a8828c70ae6b9a539f6f809238032d146dabae23b3d8be` |
| `run2/result.private.json` | 34106B | `ee4b2c6cbccec0e3fa8019708cad9fd0a89405f29f48e86c3bcf6a419b2b0039` |
| `root19176-declaration.private.json` | 4441B | `aa6e3d5b072e370ce23696a21379026add375cbd842621b144ca4ed55d8047e8` |
| `writer-run1/failure.private.json` | 2350B | `f71b143902ed826ba403d325ee5fdc814548a00f45ce537f69fe8d9fc6a8990b` |
| `collect_typed_writer_v2.private.py` | 5660B | `26de624e64f160fb1fbd527738f77359767a2b6cb9e893bab3bb64cb3fe4f645` |
| `writer-run2/preflight.private.json` | 3078B | `f0ae99302a9386d0b00d0f7b3d14f7cef2c067b0a058c2d4fef79ddb7b586cdf` |
| `writer-run2/result.private.json` | 63965B | `f9077d62e14b00025a5a6c5846f9d71b87a975ff503f2ea7cf9709d1360a6a4c` |

首次索引脚本误把Scene Entry原件的`declarations.native`当`methods`，KeyError发生于EXE定位之前，原失败保留；适配已存结构后只做一次EXE定位，没有重复扫描。首次writer collector手录profile pin漏一字符（63位而非已验64位），hash门在新body读取前停止；实际profile经独立核仍为本页既验SHA，修正成同一已验pin后重新核三输入才取源。两个失败均不是安全拒绝、真实输入漂移或放宽校验。

已实际完成固定输入/旧源只读精确RIP核查、一次单cell raw定位、全部候选保留、普通AOT/唯一pdata/typed参数核查，以及一条获批完整writer的strict回拼和REX alias边界消歧。没有新global值读取、目标CPU、客户端、hook、新模型或其余writer body；没有Proto/Java/构建/DB/Git操作。局部结果不升级Peer完整sender、M0或entry-ready。
