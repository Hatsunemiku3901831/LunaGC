# D02-M0-SCENETEAM-MIN-WIRE：单角色队伍最小字段合同

## 中文有限计划与边界

前置 [LOCATE](CLIENT_72_D02_M0_SCENETEAM_LOCATE_20261116.md) 阶段固定 SHA `9a778e61e546533b918f70c070740828897ada6b3d708ac7ddb57a05ac194040`，由唯一集成者独立提交，本项不再修改。其已验 `24706/83228`、`1144/86588`、唯一共同元素76429和真实 typed 消费源直接复用。

1. 固定同一 metadata/signatures/commands/profile；只核83228、86588、76429的reader与.cctor准确声明、参数、普通AOT和唯一pdata，不扩全协议或近名树。
2. 主线已批准实际CALL `1507B25D0` 的唯一字段桥（m467381，76429*/18163*）：固定输入/普通AOT/参数/唯一pdata后，完整body不超过8192B才取源并严格连续解码/重拼；不取新callee、不执行native CPU或读global现场。
3. 将reader/.cctor所需最小准确source清单及时交主线；后续只在批准范围内从实际tag分支、字段store与具体codec恢复wire，不用旧Proto或同形补tag。private m467716没有实际调用，仍不取正文；保留它也是86588 typed消费者的metadata事实。
4. 首发固定同一Player的女主10000007/depot701/11101/Scene3。分别列真实GUID/实体/UID/Scene来源、目标字段角色、wire和默认/必要性；数值可自定不改变协议身份。未知能力身份与必需嵌套不以空壳补齐。
5. 成熟字段立即交集成者编Proto/实际投影与定向wire断言；本项不改Proto/Java/生成/Git/DB/构建/实例。准确A/preview/B返回依赖停止，独立既有对象/标量字段可分项完成；无新VM模型、hook或客户端操作。

原始证据只新增于忽略的 `local/migration72/d02-m0-sceneteam-locate/` 下MIN-WIRE新批次。本文由本执行者独占写入。以下是已实际完成的有限结果；没有发布完整SceneTeam发送器。

## 阶段结论

76429的17个实例字段及83228的4个实例字段均已恢复实际raw tag；Avatar/Weapon的GUID、entity ID、两份能力状态和当前Scene ID有真实typed消费来源。86588另用主线明确批准的原样owned合同完成19个真实离线CPU样本，+20 bool确定为tag13/w0。成熟部分已交唯一集成者实现raw Proto和独立wire断言；本项没有运行客户端，静态七源采集本身没有执行CPU。

86588自有.cctor给出tag5/repeated76429；其reader完整pdata只有跳入保护区的入口及填充，静态源本身没有bool tag。metadata补查也没有得到独立、可识别的root专用writer/size来源。后续19样本仅执行原样owned合同，实际观测tag13的原生bool store，未取新VM source或建立VM模型。该实验不验证列表codec/解析，不升级完整sender、entry-ready或客户端验收。

输入沿用LOCATE的固定profile与五stock SHA：profile `a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d`；EXE `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`；global metadata `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`；startup metadata `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`；SDK `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25`；Astrolabe `87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442`。每批都实际核对，未换输入。metadata缓存117201003B/SHA `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f`、signatures 78649549B/SHA `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe`；commands沿LOCATE SHA `8dea972f3438e9c08adc801a90e00a13b97dc022477f0f56604fa238dee8e029`，未重扫97包。

### 单字段桥：76429的Avatar/Weapon身份与能力输入

`owner56238/m467381 DBFCBNFHKEF(76429*,18163*)` 的唯一普通AOT为 `1507B25D0`；唯一完整pdata `[1507B25D0,1507B29D2)`，1026B/243条严格连续指令，EOF及逐字节重拼一致。code SHA `21ffce122e3fd78b5ad994b7bbc43806cac6a5f4292ce477a159cbae52eb2293`，source JSON SHA `2964b1385c19f769d43c3a0f90a418752e0f5d4e92d4ede0de20d7ab54330b1f`。同profile五stock与LOCATE metadata/signatures固定hash均实际核对，`nativeCPU=0/clientRuns=0`。

该body没有 `1405336C0` 直接CALL。已有实体/虚槽/IFix分支的未知效果仍保留，不取新callee、不执行调用、不把静态边当完整实体替换成功。可直接交集成者的字段角色如下，准确tag见后表：

| 76429字段 / offset | 实际源中的关系 | 同Player真实服务端来源 |
| --- | --- | --- |
| `LLHEBIHGDAE/+5C/u32` | 为0时此helper直接返回；非零且与Avatar模型+218不同，旧实体查询/条件拆除后在 `1507B27B4` 交已验 `15184AEB0` 写Avatar实体ID | 同次真实 `EntityAvatar.id`；不能为0或复用武器ID |
| `OKMEHFHNFCG/+78/u64` | LOCATE已证精确factory18163 GUID查询；本helper也读原GUID用于后续关联 | 同Avatar完整64位GUID |
| `LBJMODOPOCP/+30/34045` | `1507B27C0→151846000`，与已审Avatar能力retain/store一致 | 同EntityAvatar的实际能力状态；raw业务投影仍须单独确定 |
| `CDOAFDGJGDF/+28/RepeatedField73976` | `1507B27CC→15183E790(Avatar18163,list)` | 精确类型/转交已证，当前不借旧TPS名补业务与默认 |
| `NPKKCHABEJH/+68/u64` | `1507B27F6→1547E1DD0` 使用已审精确Weapon工厂cell145994388；将owner Avatar模型+38写该51522模型+88，原+68写模型+38 | 同owner真实11101 GameItem完整GUID；不是另一个Avatar或0 GUID |
| `IEDJNHILJBF/+58/u32` | `1507B2820→14EAAF060`，与已审附属Weapon entity setter一致 | 该武器本次真实EntityWeapon.id |
| `CCODNLIKNGM/+38/34045` | `1507B282C→14AD08270`，与已审Weapon独立CopyFrom一致 | 同武器实际能力状态；不得误作Avatar retain或两字段互换 |
| `EOFFDMLNFIM/+50/89710` | `1507B2838→151845270(Avatar18163,89710)` | 具体能力control职责尚待该已typed边；仅原型/转交已证 |
| `BCMCOBDAMKM/+60/bool` | false时Avatar模型+2E9写1，true时本处不写；还参与LOCATE实体条件 | 不凭旧 `is_on_scene` 名自定发送语义 |
| `EIHPIGNKAPD/+20/41868` | 非null时读取该对象+C4/u32并按Avatar GUID交字典；null分支改用76429+70交148DD9E60 | +C4具体职责未具名；不能由41868 Avatar身份补名 |
| `HCIEHNNJNCF/+70/u32` | 除LOCATE的比较/模型+2D0写入，此helper又在null +20分支把(GUID,+70)交148DD9E60 | Player UID仍是业务候选；不把多一次u32转交当具名UID证明 |
| `JMHKHAPJADA/+74/u32` | 本helper未直接读；LOCATE已证模型+238/current network+1E4关系；本轮用既验具名GetCurrSceneId来源交叉闭合 | 同一Player当前Scene ID，M0为3；reader直接存储，不用EnterSceneNotify的掩码 |

Weapon工厂精确usage/method参数沿 [SceneInfo Contract](CLIENT_72_SCENE_INFO_CONTRACT.md) 与 [Deep Research](CLIENT_72_SCENE_DEEP_RESEARCH.md) 的已验role关联复用；不把所有51522都定名Weapon。两份34045只批准已列retain/CopyFrom关系，尚不批准最小初始化bool/列表或省略策略。

### reader/.cctor实际完整source

三reader均是各owner的 `MOMMACBEMEK/slot19`，唯一非 by-ref 参数为type52310/GEPIHFBDOKD；三个.cctor均为准确零参静态声明。每项已重新核普通AOT和唯一pdata，不依据方法名猜reader类型。

| 类型 / 方法 | 唯一完整pdata | 大小 / 严格解码条数 | code SHA-256 |
| --- | --- | --- | --- |
| 83228 reader m688633 | `[149EF15E0,149EF177D)` | 413B / 104 | `dd5797023e6268a8030ece956e535775945f3684e3af8f7d0d48bacaff876384` |
| 83228 .cctor m688629 | `[149EF17B0,149EF1863)` | 179B / 42 | `f0d3f81666f9ab63b9c7e1a104dbd8c078dbe9221aa384c3440f15328b007110` |
| 86588 reader m716492 | `[14A6864C0,14A6865B1)` | 241B / 233（含填充，非reader语义闭合） | `7a773b41578517145887fc1061507a6e85acadd9cc4af14b8cafcf9e7647f1d3` |
| 86588 .cctor m716486 | `[14A6863F0,14A6864A3)` | 179B / 42 | `0477ebe5c04b06f0d568f9cdb8fdacea6382096fcd12ee6cbcf4640e3c8c77b7` |
| 76429 reader m632299 | `[1505E6E80,1505E7669)` | 2025B / 451 | `a5088c2c739dce925506362a8d996acd46507e6c63446977326b87537f99a149` |
| 76429 .cctor m632289 | `[1505E4F30,1505E509A)` | 362B / 75 | `c74d12edc6f914e4eb8d6506e27cd2b71265b70ed7f0e3e818711f694571cfcf` |

主线批准六个准确source后，全部按唯一pdata完整读取。合计3399B/947条解码记录，连续、EOF与逐字节重拼均通过；没有skipdata、裁尾、小窗替换或CPU执行。加字段桥，本阶段新取4425B/1190条记录，不能把其中86588的填充指令算作字段语义覆盖。前置原件 `min-wire-field-run1/preflight.private.json` SHA `75059c669aa52c7c6cb1efd1a53635f3ed51097f8e38c9608da74b2a71c4b81e`；六源原件各在 `min-wire-readers-run1/{method}-source.private.json`。

86588的准确入口 `14A6864C0` 是 `JMP 15A4DC237`，后续231条INT3和1条NOP填充。静态取源止于该依赖；严格字节解码通过不等于保护区字段语义已知。后述owned实验由主线另行批准，实际执行既有合同允许的保护区原生指令，未继续静态取该目标source。本轮没有新的安全拒绝，也没有触及原A返回、preview/B组合loader或其冻结后继。

### 76429全部17字段：raw wire与实际对象

以下tag均由此类型自己的reader分支恢复，scalar helper为 `147520110`（u32/bool）和 `1475202D0`（u64）。所有u32/u64直接store，无加减/XOR变换；bool为`test/setne`的非零归一化。nested已有对象路径交 `1474D7E60`；新分配分支的A准确返回、构造store与真实分配结果不在结论内。

| tag / wire | key | 目标字段 / offset | wire类型 | 用途或已证关系 |
| --- | --- | --- | --- | --- |
| 1 / 2 | `0A` | `EIHPIGNKAPD/+20` | message41868 | 独立Avatar对象，helper读其+C4；与SceneEntity内oneof分开 |
| 2 / 0 | `10` | `KGDCGGDELJM/+61` | bool | self比较分支写network+248；具体发送默认未批准 |
| 3 / 0 | `18` | `HCIEHNNJNCF/+70` | uint32 | 与本地PlayerModel58013+168比较，UID仍是用途候选 |
| 4 / 2 | `22` | `EOFFDMLNFIM/+50` | message89710 | 交Avatar模型151845270，不能据名称省略 |
| 5 / 0 | `28` | `IEDJNHILJBF/+58` | uint32 | 同装备WeaponEntity.id |
| 6 / 2 | `32` | `CCODNLIKNGM/+38` | message34045 | Weapon能力状态，实际CopyFrom关系 |
| 7 / 0 | `38` | `NPKKCHABEJH/+68` | uint64 | 同装备GameItem完整GUID |
| 8 / 2 | `42` | `LBJMODOPOCP/+30` | message34045 | Avatar能力状态，实际retain关系 |
| 9 / 0 | `48` | `LLHEBIHGDAE/+5C` | uint32 | 同AvatarEntity.id；为0时字段helper早退 |
| 10 / 0 | `50` | `JMHKHAPJADA/+74` | uint32 | 同Player当前Scene ID |
| 11 / 0 | `58` | `OKMEHFHNFCG/+78` | uint64 | 同Avatar完整GUID |
| 12 / 2 | `62` | `MPHLKPODDHK/+18` | message16796 | 已typed SceneEntity；其tag10是Avatar oneof41868 |
| 13 / 2 | `6A` | `CDOAFDGJGDF/+28` | repeated message73976 | 交15183E790，未借旧TPS字段名补业务 |
| 14 / 2 | `72` | `MMCFMLLFAEE/+40` | message48178 | 已审AvatarInfo；与上行SceneEntity内Avatar一起交15184E100 |
| 15 / 0 | `78` | `BCMCOBDAMKM/+60` | bool | 实体条件及模型+2E9，发送语义仍须独立闭合 |
| 549 / 0 | `1128` | `OJLCDFKMPLH/+62` | bool | 高tag实际分支，不是tag16；具体发送默认未批准 |
| 645 / 2 | `142A` | `JOBBNEKLCOF/+48` | repeated message53824 | 高tag实际列表，不是tag17；尚无此列表的完整业务职责 |

完整字段声明与compare/store/readCall保存在 `min-wire-bindings-run1/raw-wire-contract.private.json`，22951B/SHA `2eadc9ce3fc9248c3b2a828a58007850598a4bafe4f0f307cc524c2b14c49207`，覆盖的字段index集合与metadata的17个实例字段完全相等。

三个实际列表CALL均调用158DC41F0，其精确MethodInfo单独由固定metadata解析，不能用同形泛型替代：

| cell | usage / spec / classInstance | 精确owner与类型实参 | 实际字段 |
| --- | --- | --- | --- |
| `1459EA648` | 746878 / 54314 / 31236 | owner25060/m194683 FNNADLCNGFO&lt;76429&gt; | 83228+18 |
| `1459B2388` | 740903 / 34065 / 10766 | owner25060/m194683 FNNADLCNGFO&lt;73976&gt; | 76429+28 |
| `1459C7230` | 743163 / 41759 / 6486 | owner25060/m194683 FNNADLCNGFO&lt;53824&gt; | 76429+48 |

原件 `min-wire-bindings-run1/repeated-reader-methodinfos.private.json`，5332B/SHA `fdf3a0b23a49fc76d953883bd144aaa3c7fc42b460761aba029184d1573782e6`。76429自己的.cctor分别给codec73976常量`6A`和codec53824常量`142A`，与本reader分支交叉一致；未执行其A分配/构造store。

### 两个root：字段来源与消费范围

83228自己的reader完整恢复4字段：tag4/w0→+28 uint32；tag5/w2→+18 repeated76429；tag9/w0→+24 bool；tag12/w0→+20 enum83230（raw按int32保存）。scalar同为直接存储，无业务掩码。自有.cctor的`2A`与精确class76429符合tag5。

86588自己的.cctor也独立出现`2A`及精确class76429/Codec87265，支持其tag5/repeated76429子集；后述owned实验独立证明+20 bool为tag13/w0，来源不是83228或旧布局。二者真实typed public消费在LOCATE已有区别：83228先读+28、enum+20==1走单独路径，否则flag0；86588调用共同worker传flag1和0。该flag的完整用途仍未知。不得以另一个root可编码为理由替换原root发送。

86588的private m467716也有同root typed参数，只有metadata/前置界限，没有实际CALL给它；本轮没有取得正文。public m467585没有读root+20，不等于该bool在所有消费中无用。

补查限定为86588全部13条声明及67861基类的准确writer谱系声明，普通AOT/pdata表已核，新增body读取为0：

| 独立关系 | 实际metadata / 界限 | 能解决什么 |
| --- | --- | --- |
| 86588 CopyFrom | m716493，唯一参数86588，slot24，`[14A686310,14A6863D8)`/200B | 能识别同root复制方法；仅声明不能给bool tag，未为此取body |
| 86588 bool参数方法 | m716495 BDPAKGMBGMB，参数bool，slot9，`[14A686620,14A686643)`/35B | 不能仅按参数或混淆名判为writer |
| 继承writer谱系 | 基类67861 m562044 CAIINKNGKDM，唯一参数19302，slot17，entry1495C9EF0；86588未override | 当前pdata为0命中，未读正文；不能取邻居边界 |
| 零参size候选 | signatures明确`returnTypesStatus=not-recovered`；未有具名size或typed返回证明。m716498 FLOMKNPPCKN/slot18 entry14A686740的pdata也0命中 | 不能按常见Proto布局把零参方法改称size并取源 |

这些结果是metadata备选方法的有限负结果，不是“整个类型不可研究”或安全拒绝。全部13条精确声明/参数/AOT/界限与0命中诊断保存在 `min-wire-bindings-run1/86588-declared-and-base-methods.private.json`，15919B/SHA `52378400ab815019ba4eee1925d5207cf413b70e344f10a819aa4a70850df5d8`。bool字段随后由下述owned样本独立恢复；没有改用邻居界限给这些0-pdata方法补body。

### 当前Scene身份的独立复用与41868边界

76429+74在worker `1507C46A4` 读出、`46B2` 写Avatar18163+238，`46F2` 与同owner56238+1E4比较。已验 [Enter Scene Contract](CLIENT_72_ENTER_SCENE_CONTRACT.md) 的具名ActorManager.GetCurrSceneId/m641775@14FEE86B0读取的正是PlayerModule+1E4；旧证据用5组互异current/entering值区分两字段。本轮只核旧trace固定hash及同profile，再与现有typed字段交叉，没有重跑旧CPU。故tag10可按同Player当前Scene ID编码，M0为3；不是Notify tag7的变换后raw值。

复用trace `local/migration72/evidence/enter-scene-contract-trace-v2.json`，SHA `2cf07066ecc089735ad9a4ed28e752c2488a12d22c0095d9ae790ea98e6570eb`；本轮join原件 `min-wire-bindings-run1/scene-identity-reuse.private.json`，4789B/SHA `e371b094be1408b6d1fc77757fd3ee324b149d7f0080e77b6d0c6663acaed96d`。76429+70与58013+168的typed比较已经确定，但旧SDK fixture填12345本身不是具名UID证明，仍保留用途候选。

`76429+18→16796+38→41868` 是SceneEntity Avatar oneof；不是读41868自己的+38。另一条 `76429+20→41868` 为独立字段/对象。字段helper `1507B2618` 从76429+78取AvatarGUID→R14；`284A` 取76429+20；`286A` 读此41868+C4；`288E/2891/2894` 排参后 `2897→15946E710(manager字典,AvatarGUID,C4,1)`。没有读取41868+D8，也没有把该u64当AvatarGUID。41868的内部wire由并行SCENEAVATAR-WIRE独立审定，本项不重复读它的reader。

SCENEAVATAR-WIRE已独立固定41868完整reader：`local/migration72/d02-m0-sceneavatar-wire/reader-run1/body.bin`，3430B/SHA `b743a2727f14b5eb72b268f665708744bb9537b6f7b8c1e93f01747c98e657e0`；完整asm JSON SHA `cc78536bde1e7289f80cfa7d2f9d1d90a1cb17c6d0dcdd0dc7fd216303d15631`。本项只读核hash后引用：tag4/w0经1504B33E6调用147520110，33EB直接写+C4/u32；tag3/w0经383D调用1475202D0，3842直接写+D8/u64，均identity。故上述+C4业务边现在有准确tag4 wire，但其具体角色仍未知；+D8没有被这两条SceneTeam消费源读作GUID。

### 86588 bool：原样owned合同的19个实际样本

先仅读既有Reader72/Offline72合同，主线再明确批准原样复用root73627标量合同做本root最小bool验证。旧18样本原件 `entity-root-scalar-owned-run1/result.private.json` 位于 `local/migration72/d02-m0-protocol-xref-level/`，SHA `ef3034dcb650301d61f135ebde43dce92340105a3b1c253a5a9fe33e62675d18`。其旧driver SHA `98acc28ca0aaf592ce5e803c21a922722d29dab8bda2e074658866067cf3270f`；owned Reader72 SHA `b12d1e6a2fcd5ddcb88365188f759d3a6f08f43ab9cb7fbe4a2fa8f6db348c32`，Offline72 SHA `8007d2594ef8058a4f2a9f1c24094fb5d2551a202b4e52154876cdfe77a17c3c`。九个原pins及本profile五stock均在执行前实际核对通过。

现有147520110→scalar(32)为受控varint低32位模型，不在模型内代写bool。相同合同已用于响应的15个bool及原生0/1写入验证，字段表 `protocol/cn-7.2/response-wire-observations.json` SHA `eae0e61bd6cdc0b0ff1995ac778da1d37890fd707937cfaaf5b3e18137dc6e64`。本次复用它提供primitive输入，归一化和对象store由目标代码实际执行，没有新增bool默认返回。

准确entry14A6864C0、type86588；空baseline为合成零对象/count1、+18预分配，不是执行真实ctor默认。主线正式输入固定为规范wire0/value1，按tag1..32逐个；在tag13发现唯一+20/1B原生store后即停止发现阶段。实际19样本为空baseline1个、tag1..13共13个、命中tag的5个值。前12个无写入/不变样本全部保留；未继续tag14..32，未试列表wire2。此为真实owned离线CPU进入原有保护区，不是只执行241B入口；原Offline72允许entry至下一AOT14A6865C0，已有完整pdata止5B1的差别明确保留，未新建VM模型。

主线明确确认保留的合同限制：原样Reader72构造函数固有6个旧response codec fixture、3个旧TypeInfo初始化及通用container模型，run还会按metadata预分配root+18。它们无86588 codec，本次没有配置该root的listcodec、运行.cctor或新增目标TypeInfo/metadata初始化；这些既存fixture不作为86588来源证据，也不称为“完全零codec配置”。

原13个模型/两个初始允许区、before_instruction=None、每样本20M指令/20秒预算均保持，新增模型/hook/预算为0；A不在模型/允许区。baseline正常返回、耗尽输入且无事件；其他事件全部为+20/1B的native-store，唯一store源 `15A46F06F`。实际模型调用集合只有ReadTag1474D8100、Skip1474D7BB0、varint32 147520110；没有list/nested/allocator/init调用或失败。19样本共观测2532895条实际指令，AReached全部false。

| 独立wire输入 | 目标+20实际1B写入 | 结论范围 |
| --- | --- | --- |
| `6800` | 0 | 明确0输入实际写0，不是缺省对象恰为0 |
| `6801` | 1 | tag13/w0写入唯一bool字段 |
| `6802` | 1 | 此非零向量由目标代码归一化 |
| `688080808008` | 1 | 80000000向量实际结果 |
| `68FFFFFFFF0F` | 1 | FFFFFFFF向量实际结果 |

表中2/高值是已观察结果，不将其外推成未测64位溢出/所有错误的bool规则。listtag5仍保持独立.cctor来源等级，不借bool实验升级列表或完整sender；本次没有恶意长度、错误wire或完整消息列表样本。

实际driver `86588_bool_owned.private.py` 为9616B/SHA `e0a2b16085bee5be82b99de897e3d8d161f473de817e36e32f4b78d74b3d87e2`；`bool-owned-run1/result.private.json` 为27350B/SHA `d00dbff8142376f60a509b79824d853e085ba35f296173307d8262056fb9d93f`，所有19个逐样本原件均保留。执行前合同 `pre-execution.private.json` SHA `3a77ae09e255e525bdda44f0160415d4ab72816fa1f1519e86858bb0efef3646`。早先 `raw-wire-contract.private.json` 保留boolTag=null的静态阶段原文，不覆盖为假静态证据；新增tag13以本owned report为准。预审计划SHA `0a4e441688d364cc2719d97ca7cfae77769348962622539e2ec47e04a6cd21e2` 仍只读保留，正式输入为本driver/result中的value1。

### 可编码范围、未闭项与验收

已交集成者：Raw76429完整17字段、Raw83228完整4字段、Raw86588的tag5列表声明与tag13 bool，以及同Player/同Avatar/同装备的GUID、entity ID、Scene ID来源。列表来源等级和bool实测分开。应由其串行完成独立wire断言，包含非零、64位、高tag、嵌套、重复列表及错误/边界；本执行者完成的只有上述19个native bool样本，没有运行生产Proto协议测试或生成/编译Proto。

首个真实单角色投影仍须满足以下剩余输入，不发布“先填空”的完整sender：真实root选用/flag与root tag13 bool的业务语义；76429 tag3的具名UID桥；tag2/15/549的发送语义；tag1独立41868与tag12 SceneEntity Avatar oneof的最小必要字段/是否同值；34045/89710能力状态的必要性与同对象实际投影；73976/53824列表的业务默认。已验AvatarInfo48178及Avatar5基础能力只读复用，不能将“不追加可选全局MP/Team/level能力”的server-provisional策略当作这些协议能力嵌套已经闭合。

本项实际验证为固定输入hash、普通AOT参数/entry、唯一完整pdata、七源严格连续解码/EOF/逐字节回拼、17字段完整性与三个精确泛型MethodInfo绑定、既验Scene身份只读交叉，以及另行授权的19个owned真实CPU bool样本。未启动客户端、JVM、DB、构建或部署；未改Proto/Java/运行实例/Git；没有新的安全拒绝。本项有限工作完成，完整M0/SceneTeam/entry-ready保持未验，由主线继续安排最小独立项。
