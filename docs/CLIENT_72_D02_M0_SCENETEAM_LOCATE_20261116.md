# D02-M0-SCENETEAM-LOCATE：SceneTeam 精确类型交集定位

## 中文有限计划与边界

本项只定位新号单角色首场景所需 SceneTeam 候选的 `Cmd root→非 Proto typed consumer`，不重做全 97 包、literal 全扫或 SceneEntityAppear 专项。首发角色和实例策略沿 M0；前版 SceneInfo 专题只读不改。

1. 固定已审 metadata/签名缓存、commands 和 AvatarInfo `48178`；向 Entity 专项核实候选 `16796` 与 `16796/tag10→41868` 的原字段、hash 与验证层。候选语义不能因名称便利转为正式。
2. 对普通 Protobuf 基类 `67861` 的业务类只做一次直接类型引用交集：先找同时引用 `48178/16796/41868` 的类；三类交集为0时最多补两两交集。不扩大近名、literal 或任意后代树。
3. 对交集候选保留完整字段类型/offset；仅找有 `RepeatedField<该候选>` 的已登记 Cmd root，再按固定签名找该 root 作为参数的非 Proto 方法、继承关系与 `OnAccept` 虚槽。
4. 唯一 typed 候选成立时先交主线准确 body/AOT/pdata 范围和停止条件，再等其批准该必要 source；本轮初始不读取新 native body、不执行 native CPU、客户端、Ghidra 或未知 dispatcher。

允许写入仅本文与忽略的 `local/migration72/d02-m0-sceneteam-locate/` 原始证据。Proto/Java/Git/生成/构建/DB/实例仍由唯一集成者负责。原 A 准确返回、preview/B 与准确依赖保持冻结；普通 metadata 的独立有限交集不因此整族停止。

精确交集与边界成立后，主线追加批准两个 public typed source：m467433/`1507C3AC0` 与 m467585/`1507D11E0`。每项只取唯一 pdata 对应完整 body，8192 B 上限，固定输入/AOT/参数/严格连续解码与重拼均须成立；不执行 native CPU，不读 global，不跟 VM/冻结调用。private m467716 不取，只有真实 CALL 到它并单独形成必要规格后才可再交主线判断。

两个 public 源都实际 CALL `1507C42C0`，主线继而批准仅此汇合 source：owner56238/m467717，参数为 `RepeatedField<76429>`、`List<uint64>`、bool、u32，唯一 pdata 完整5743 B，上限16384 B。仍只静态取源/严格解码，未批准新 callee/global/VM 模型或 native CPU；准确 A 返回依赖分项停止，独立已有对象/标量读取保留。

## 阶段结论

`D02-M0-SCENETEAM-LOCATE` 的有限出口已取得：精确三类交集唯一 **type76429**，两个已登记 root **24706/type83228** 与 **1144/type86588** 各有真实 public typed 入口，并共同调用已完整取到的 `1507C42C0`。该共享源确实逐项读取76429、按完整u64查 Avatar 模型，并把48178与实体中的41868交已验 Avatar 初始化入口。这形成下一最小 wire 工作的准确来源，填补了原先只有旧 `29245/SceneTeamAvatar` 的空白。

当前仍保留两 root 的业务命名歧义；root/76429 的 tag 尚未恢复，不能直接发送1144或24706。没有用 root 字段数量、旧 Proto 或相同混淆字段名给其定名。共享源含 allocator、条件实体/回调及 IFix 路线，完整执行、必要发送字段集合与客户端效果都未验，`targetProtocolReady=false`。

## 固定输入与前置纠正

本项 metadata/签名交集使用已审纯数据缓存；源取证时重新核 profile 五 stock。profile SHA `a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d`，三个协议输入为：

| 文件 | SHA-256 |
| --- | --- |
| `YuanShen.exe` | `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| `global-metadata.dat` | `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a` |
| `startup-metadata.dat` | `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105` |

metadata cache SHA `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f`、signatures SHA `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe`，路径见忽略证据 `sourceChecks`。`protocol/cn-7.2/commands.json` SHA `8dea972f3438e9c08adc801a90e00a13b97dc022477f0f56604fa238dee8e029`，绑定层与业务身份层分开。48178 的既验 raw 来源为 `avatar-complete-raw-observations.json` SHA `37c90e4dea9fccc4ac20ce68616612250b5a57169e564081c33dc69a25def837`，业务角色复用 [Avatar Contract](CLIENT_72_AVATAR_CONTRACT.md)。

经 Entity 专项核实并实际核原件 hash，16796 已有 `4469→73627→80398.OnAccept` 的真实列表消费源，543B SHA `e39b912f0f543f6dd53a1680b95d268c195b267f99893cf4c6c3b2f0189316e8`；其 reader 全窗3269B SHA `454257157a43515d625cd205ecb3df4bdd5bec1cd709cd1f14e8ca7b4aee4a29`。`tag10/wire2→41868` 的 class cell 为 `1458CDA58`，usageRow711831/sourceIndex266975；enum16797 的独立默认表确认 **Avatar=10**。该默认表报告 SHA `a5596a267b736ecc7d0ab4f4b53f050b7d18bcfbdaf2185a5985485eb30e77bb`。本项没有因此声称41868的内部字段或完整实体创建已闭；详见 [M0 Scope](CLIENT_72_D02_M0_SCOPE_20261116.md) 的 Entity 专项。

首次工具在正式交集前把 `closureMessages` 的对象列表误按整数列表检查，产生 `ValueError: Fixed AvatarInfo raw type is outside the inherited closure`。这不是输入漂移或48178缺失；修正为按 `typeIndex` 读取后才执行本项唯一一次交集。原脚本 `locate_scene_team-v1-failed.private.py` 和失败 `failure-v1.private.json` 均保留，后者 SHA `ddd1baeb4e1b1396e5a197707a5458dceaa63ed994efd4b367c49fcb29abc8a9`；没有覆盖原失败或把它算成成功。

## 唯一三类交集与完整字段

只对继承普通 Proto 基类67861的类型，检查字段的直接 class、直接 generic argument 或直接 array element 引用；不递归展开任意后代。三类 `48178/16796/41868` 交集唯一为 **76429/EHOKGCLKMOO**，故未执行两两 fallback，也未做近名/literal 搜索。

以下为76429全部17个实例字段，offset为十六进制。两个静态87265字段保留在原件，不混入实例 wire 字段数量。表中类型是固定 metadata 事实；业务名仅在有独立消费源时使用。

| offset | 原字段 | 完整直接类型 | 当前用途状态 |
| --- | --- | --- | --- |
| +18 | MPHLKPODDHK | class16796/IJCEPPNOCNM | 已证实体消息载体；共享源检查Avatar oneof并转初始化/实体同步 |
| +20 | EIHPIGNKAPD | class41868/FKDEDKIHNDA | 直接类型已证；当前共享源未直接读取此字段，不把它与+18内的Avatar oneof混为同一次取值 |
| +28 | CDOAFDGJGDF | RepeatedField73976，definition25060/instance10766 | raw类型；业务/必要性未晋级 |
| +30 | LBJMODOPOCP | class34045/PLMODKAFNAD | raw类型；能力角色/必要字段未晋级 |
| +38 | CCODNLIKNGM | class34045/PLMODKAFNAD | 与+30独立，不按同形互换 |
| +40 | MMCFMLLFAEE | class48178/ONGOHFDHHNI | AvatarInfo；实际送15184E100 |
| +48 | JOBBNEKLCOF | RepeatedField53824，definition25060/instance6486 | raw类型；业务/必要性未晋级 |
| +50 | EOFFDMLNFIM | class89710/KOKHLKFCIMG | raw类型；能力身份用途未由本项证明 |
| +58 | IEDJNHILJBF | uint32 | 尚无本项直接业务映射 |
| +5C | LLHEBIHGDAE | uint32 | 实际交152F7DF60、1507E91C0/1507EC7D0；参数语义后继待单项 |
| +60 | BCMCOBDAMKM | bool | 与+62一起参与实体同步条件，不按旧字段命名 |
| +61 | KGDCGGDELJM | bool | 本地分支复制network+248；完整职责未命名 |
| +62 | OJLCDFKMPLH | bool | 与+60独立参与条件，不视为可删默认 |
| +68 | NPKKCHABEJH | uint64 | 尚无本项直接业务映射；不按同名18295字段移植Weapon名 |
| +70 | HCIEHNNJNCF | uint32 | 比较network+180对象的+168，并写Avatar模型+2D0；player UID仍需准确桥 |
| +74 | JMHKHAPJADA | uint32 | 写Avatar模型+238并比较network+1E4；Scene ID仍需准确桥 |
| +78 | OKMEHFHNFCG | uint64 | 完整 Avatar GUID：准确factory18163查询及已验GUID消费者交叉成立 |

原件 `metadata-intersection.private.json` 341229B，SHA `afbe33e1bbe08eb833670a19af4f23a89ba06279dfab7ee19828faabf048fefe`，保留原字段的index/typeIndex/完整泛型/offset及类型方法。

## 两个登记 root 与 typed 消费者

只找 `RepeatedField<76429>`，即definition25060、class instance31236、genericIndex308105 的单直接 argument，而不是任意包含候选类的对象树。登记 Cmd 命中两 root：

| Cmd / root | 全部实例字段 | 固定命令来源 |
| --- | --- | --- |
| 24706 / 83228/MJLFGFCIEOG | +18 `JFMIGFDIBDA`→RepeatedField76429；+20 `PCLLEOBNHBF`→enum83230；+24 `HDKOOKNLADL`→bool；+28 `EKGAFJOPIEL`→u32 | 普通getter m688639/149EF1930 |
| 1144 / 86588/FPEMHOKDCEO | +18 `JFMIGFDIBDA`→RepeatedField76429；+20 `HDKOOKNLADL`→bool | 普通getter m716490/14A6863E0 |

固定签名中 root 作为直接参数的非 Proto 方法共有三项，均owner56238：83228的public m467433/`1507C3AC0`；86588的public m467585/`1507D11E0`、private m467716/`1507ED0F0`。三者均是单参、非 by-ref、普通非虚方法；没有命中名为 `OnAccept` 的直接类型方法。不能把这个直接参数检索扩大为已审所有泛型 dispatcher 或网络注册。

86588有另一个 private 同 root 消费者是已证 metadata 事实，不能因本次public入口未读bool就断言 `+20` 永远无用。本项只核其普通AOT/唯一pdata为 `[1507ED0F0,1507ED172)`/130B；两个已取public源都没有调用它，故未取其正文。root83228和86588的原字段、类型、consumer参数和继承链均保留在上述原件。

## 已取得的真实静态 source

每项均先固定输入、普通AOT指针、精确参数和唯一pdata，再取完整body。使用已核 helper `scene_mplevel_actual_xref.py` 的 `locate_pdata/decode`，helper SHA `1e462537e4fc2b37a288c0d8cc15c13e61775e00a2d891e77bc4a88d90630355`。异常目录 SHA `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d`，没有裁尾、跳data或改变解码边界。

| 方法 / 参数 | 唯一完整边界 / 实际检查 | code SHA-256 |
| --- | --- | --- |
| public m467433，83228* | `[1507C3AC0,1507C3B6C)`；172B/55条，完整连续EOF，逐字节重拼一致 | `910996d6c1ca3d1eafffbb8365ecb45ca2b0e1d2ca7e2a449a425276ba8b0825` |
| public m467585，86588* | `[1507D11E0,1507D1288)`；168B/41条，完整连续EOF，逐字节重拼一致 | `b8b619c04fec7e4ff96c7159da3c696a229a04283225b9cec70e04c456c292e4` |
| private m467717，共享列表worker；RepeatedField76429/List<u64>/bool/u32 | `[1507C42C0,1507C592F)`；5743B/1220条，完整连续EOF，逐字节重拼一致 | `18a0a3c055248bfd4c6bd4fd393ffbe67bee33d5b67c94abd5a94560b2ac2753` |

合计 **6083B/1316条静态指令**；`nativeCPU=0/clientRuns=0`。普通AOT/pdata前置原件 `typed-roots-boundary.private.json` SHA `3e6172878f7cd6e2a3074117de18b73a22d0b9d32565d7ac8d127c61d17a2ddd`；public双源结果 SHA `93e278550bdd7c19dfa1725ce5617f3491479e0a481b6ee7a96faca89ae712ed`；共享source JSON SHA `c7b34af37ecece88ad6066358e6c5e663ab950e088181304fb0df33163c379ff`。目录为忽略根下 `public-sources-run1/` 与 `shared-worker-run1/`，不修改旧证据。

### 两入口的真实差别

- **24706/83228**：读取root+28的u32；若root+20 enum值为1，尾跳 `1507C3B70(this,u32)`。否则取root+18列表，并由 `151A9C2D0(GLOBAL+A420,root+28)` 得到上下文；将列表、该上下文、bool0和第五参数root+28交 `1507C42C0`。此处仅给真实数字条件与参数，不从旧消息名解释enum或u32。
- **1144/86588**：读取root+18列表，取 `GLOBAL+A420` 对象的+10作为List<u64>上下文，传bool1和第五参数0，真实CALL同一个 `1507C42C0`。此public源未读取root+20 bool；其余同 root 消费者仍在上节保留。

shared worker的bool3参不是根据名字称“本地/MP”后套用：两个public源传的0/1已证；worker仅在该bool为1时调用 `1507E2A30(this,list)` 和 `151AA0CC0(GLOBAL+A420,list)`，为0时调用 `151A9FF40(GLOBAL+A420,fifthU32,list)`。这些调用的内部职责没有在本项补桩或取新body，足以保留两条不同路径，尚不足正式命名完整消息。

### 76429→Avatar 的准确 typed 关系

共享源通过真实 `MoveNext` 调用逐项取76429。该MethodInfo的usageRow750729/spec67396/m282553、class instance31236独立绑定为76429。拿到非 null 项后：

1. `1507C458A` 读76429+78完整u64，`1507C459B` 交 `1547E1DD0`。精确cell `145990BC8`→usage737549/spec21679/method294224/methodInstance6525；当前注册argument record确为唯一参数 **type18163 Avatar模型**。这与既验Avatar Contract相同工厂，故GUID角色有独立类型来源，不是依混淆字段名猜测。
2. `+70` 与network+180对象的+168相等时，`+61`复制network+248，Avatar模型+2B0置1；否则把`+70`写Avatar模型+2D0并置+2B0为0。在后者路径取`+40/type48178`，并从`+18/type16796`的oneof discriminator+90检查值10，再取+38且要求精确class41868，随后在 `1507C4699` 调用 **`15184E100(Avatar18163,AvatarInfo48178,Avatar41868)`**。
3. `+74` 与模型+238不同时写入该值，并与network+1E4比较；`+60/+62`一起参与是否保留/同步对应实体消息的条件。这里有精确字段流，但Player UID、Scene ID和两个bool的业务映射仍不能只依典型布局正式定名。
4. worker另有真实 `1507C44F8→1507B25D0(this,76429,18163)`，其metadata为m467381/`DBFCBNFHKEF`，两个参数分别准确76429与18163。该helper尚未取源，不把它的未知效果归给本项；它是下一必要单头像/Weapon身份字段语义桥。

两份独立 typed 证据为 `shared-worker-typed-bindings.private.json` SHA `59d586cb94e3745527a338536ce2ca313e8762f37390f54b4936dc3c6404c390` 和 `avatar-factory-instance.private.json` SHA `dcffb2a0a6c4c91abe12673f8dea7a1b37aa4ff69635735f0b321775458d8df0`。methodInstance6525不是仅凭共享factory地址推定参数。

### 准确停止与未升级范围

共享body中 `1507C43E8/1507C4A2F/1507C4F0A/1507C525B` 有 `1405336C0` 分配调用。本项保存这些静态指令，不执行或补获准之外的返回，不据其结果证明临时List、清理closure、延迟回调或完整worker成功。既有模型/GUID/标量/typed参数关系可独立记录；不把一条准确冻结依赖泛化成整类不可分析。

IFix/反射虚槽/配置条件和各未取callee的实际效果保持未知。本项没有新安全拒绝，没有未知VM模型、global现场读取、客户端/进程/DB/构建/运行变更，也没有以条件分支静态可达宣称必经或已实际执行。

## 后续最小可执行项

本机已有固定输入足以继续这组必要reader/字段源，不把它们写成统一外部缺输入：

1. 先只核83228、86588、76429的reader与.cctor准确metadata参数/AOT/唯一pdata；只由实际reader或codec关系给tag，不取全97包或全候选树。
2. 按主线单项许可取实际CALL的 `1507B25D0/m467381(76429*,18163*)`，闭+70/+74/+78以及单角色/真实武器/实体/能力身份的可编码业务来源；遇准确冻结返回停其依赖，独立字段保留。
3. 对86588保留 private m467716同root消费者存在的metadata，不擅自取消root+20 bool；没有实际调用/独立必要性不取其body。
4. 最小消息成熟后由唯一集成者制作独立Proto和真实同Player/Avatar/GameItem/Entity投影、wire断言及必要回归；本阶段仅定位合同不能代替生成、部署或客户端验收。

本阶段仅新增本文与忽略证据，无自行Git提交。实际检查为固定hash、普通AOT、签名、唯一完整pdata、完整连续解码和字节重拼；没有把历史JUnit或原生夹具数算成本轮，也没有为一次性离线定位另建独立测试套件。阶段结果已即时交主线和集成者，后续 `SCENETEAM-MIN-WIRE` 继续使用新批次证据。
