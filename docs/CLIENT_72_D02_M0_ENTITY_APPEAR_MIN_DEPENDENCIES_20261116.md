# D02-M0-ENTITY-APPEAR-NECESSITY：单实体最小依赖

## 中文有限规格

2026-11-16。先复用 [SCOPE](CLIENT_72_D02_M0_SCOPE_20261116.md) 已固定OnAccept543B：owner80398/m666441 `14cd6f280..14cd6f49f`，code SHA `e39b912f0f543f6dd53a1680b95d268c195b267f99893cf4c6c3b2f0189316e8`，原件 `local/migration72/d02-m0-protocol-xref-level/entity-accept-run1/0-body.*`。不重取此body、不执行CPU/VM/A。以固定metadata/signatures/commands和已有caller核14b313d10是否普通typed业务谓词，不能把委托Invoke误认为最终callback。

若是wrapper，仅核已有允许根handler/构造索引的真实method pointer来源，不取第三handler或读A返回；缺producer精确报告。若确为唯一普通typed方法，固定三输入、ordinary AOT和唯一pdata，body≤8192B后只取这一个完整source，连续strict decode/回拼；不取新callee。源漂移、边界非唯一、strict失败或新拒绝保存原错误停，不skip/crop。准确A/未知返回只停相应依赖，独立已有对象分支分别记录，不泛封。

本专题唯一tracked可写；前两SceneAvatar专题保持稳定。raw仅忽略的 `local/migration72/d02-m0-entity-appear-necessity/` 新批次。本轮不写Git/Proto/Java/运行实例，不从31raw完整性反推首发必须非空41868；也不把SceneTeam的null分支搬到EntityAppear。

## 第一阶段：分类已实际成立

固定metadata cache SHA `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f`；signatures SHA `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe`；commands SHA `8dea972f3438e9c08adc801a90e00a13b97dc022477f0f56604fa238dee8e029`。原OnAccept完整ASM SHA `e402ae99411c5edcc5a698878c27c9a18b20910f7b7b2a32288629a7b6b32471`，与已固定543B回拼一致。

metadata普通AOT声明仅一项命中14b313d10：owner74440 `HFKMGFCGEOO`，m614946 `PNOEBLAAEAI`，flags0x96（static、非虚）、slot65535。单参p477801/typeIndex88186→准确class16796、非by-ref，不是泛型Func/Action参数。继承链74440→49803→54280→System.Object371，没有Delegate/MulticastDelegate。

OnAccept在 `14cd6f3d0` 将真实列表项16796放RCX，`3d3 CALL14b313d10` 后test AL；初始化class分支也在 `3ff/402` 做相同调用。它是直接静态调用，没有读取delegate对象/Invoke虚槽。这里只证明 **普通typed方法被用作谓词**，尚未从未恢复的return-type metadata猜bool原型，也未给其业务名。

因此进入许可的单body分支：owner74440/m614946/14b313d10，先ordinary AOT+唯一pdata再取≤8192B，不把同类其它方法或已采caller当source闭包。固定客户端三hash沿用extract_client72.HASHES（与上述commands inputSHA256一致），不换版本或设备来源。

## 第二阶段实际source

ordinary AOT/唯一pdata均实际成立，只取 `[14b313d10,14b313da8)` /152B，recordRVA `1a8fbf10` / 原12B `103d310ba83d310b94621b04`。完整40条连续strict decode、EOF与逐字节回拼一致；body SHA `9da8beca6daf5e57653d1fe466a11ff8e06227da13d601e41fc0998985473e61`，ASM SHA `83cf94f4431ed6aaa0bccb63e043e59bd4790adb7caeb6eb04c6584658514ef6`。

普通非IFix分支的完整行为已经可判定：

1. child null→`14b313d23/3d98` 错误入口。
2. `14b313d25` 比较child16796+90是否13；不是13直接到3d6e清EAX返回false。目标enum16797的13已确Gadget，Avatar case10在此不读+38或41868。
3. Gadget case13仅在+38对象非null且精确class47044时继续；比较47044+A0的次级oneof判别enum47045是否0x43，不给67猜业务名。
4. 再取其+50的Object，要求非null且精确class59430；最后 `14b313d61/66` 只以59430+18/class65333指针非null产生AL=true。任一普通不匹配返回false，错误class转类型错误入口。

两class cell分别为1458a87a0→metadata usage705222/type47044、1458de340→usage715155/type59430。没有EntityID、ProtEntityType枚举字段或41868内容读取；没有普通业务callee或A CALL。IFix分支仅尾转14dff9f90，未取wrapper/global或执行它。

**结论是Gadget专项条件，不是Avatar创建/完整EntityAppear校验。** OnAccept的AL判断只在此条件与root VisionBorn一起成立时调用this+28 Action；Avatar走false只跳过这个Action，不能据此宣称Avatar成功、字段可删或现有实体复用。Action的callback producer不属于首发Avatar经此分支的必要输入，不为它扩树。

原件 `local/migration72/d02-m0-entity-appear-necessity/predicate-run1/`，result SHA `d9be874b558526826e60b2a964eb2a6c9963252785f7dd9ffb68cc878caccf86`；driver `predicate-source.private.py` SHA `5b261d64b8d81a708f5dd13f3c6bb88a5c799618c201b08612c3d0fac744386c`。没有新CPU或安全拒绝。

## 第三阶段：唯一Handle的准确补充规格

主线接受上述专项判定后明确另授同处理对象的 **m666440 Handle@14cd6f4a0 /slot11** 单body。签名唯一参数p511299是 **int32**，不是root73627；this为owner80398，其+10由已验OnAccept保存root。不能凭Handle名字改参数类型，也不跟构造factory m666439 `(73627,Dictionary<u32,int32>,Action)` 的A返回。

复用相同固定输入/metadata/signatures/commands，先核ordinary AOT和唯一pdata≤8192B，随后只取此完整body并strict连续回拼。只追this+10列表到真正Avatar/entity消费的实际CALL、参数与分支，不取callee/CPU/新global。准确冻结或未知返回只停对应依赖，直接字段/转交分项记录。若真实CALL到旧14aa4c500，仅确认实参，旧924B严格失败保持、不重取或扩handler。源漂移/非唯一/strict失败保留原失败立即停，不skip/crop。

### Handle实际source与参数

ordinary AOT/唯一pdata成立，完整 `[14cd6f4a0,14cd6f618)` /376B，recordRVA `1a9ce870` / 原12B `a0f4d60c18f6d60c60ce3804`；98条连续strict decode及全文回拼相同。body SHA `26b416dff7ff0e12e352f7a38d4b33f570133c74f6c98e102b7590bc06a25442`，ASM SHA `d0e0cb487b657b7faf575d65792cb4beca7d1aec3ea94fcc391f57e4ed8c5900`，无准确A CALL。

实际数据流：

| 源位置 | 事实 / 当前范围 |
| --- | --- |
| `14cd6f4a7/4aa` | int32参数EDX存R8D、this存RSI；该参数用于列表索引，不是包内root+24 |
| `4ba..505` | this+10取root73627，+18取RepeatedField16796，经真实null/计数/bounds检查按索引取得child到RDI；列表项null转错误 |
| `520..52d` | child+98/u32→EDX，this+20的Dictionary<u32,int32>→RCX，CALL158b79980；未取得此helper返回语义合同，不给它猜FindEntry或世界实体查询名称 |
| `532..560` | EAX负转错误；非负用于数组bounds和16B slot地址，读取该slot的int32值。非零则ret；零继续。只闭条件指令，不能把未知helper返回/slot值解释成“已有世界实体” |
| `561..56d` | 再从this+10取root，root+20→ESI、+24→EBX |
| `580..592` | RCX=原child16796，EDX=ESI，R8D=EBX，R9D=0；epilogue后 **TAIL-JMP14aa4c500**。class冷分支只先到140525e00再回580，未执行或模拟初始化 |

精确目标仍是owner80395/m666353 `LBGLPKPOHKH`：p511203/class16796、p511204/enum42790、p511205/u32、p511206/u32，和已验literal/metadata候选为同一普通AOT。结合既有wire，真实调用是 **(child16796, root tag9 Vision, root tag1 u32, 0)**；不是另一个临近函数，也不把第三参数叫SceneID或其它业务名。

Handle没有oneof/type/41868字段读取。直接pending路径把整个child交旧Level入口；slot非零跳过Level的条件不能作为首发existing-entity证明：该slot是客户端处理对象内部字典状态，lookup返回、值的生产/业务意义尚未核，不是服务端可设置的包字段。OnAccept只曾把数值0作为参数交158b79c70，未取其body，不能把它等价成已证字典写入/状态定义。

原件 `handle-run1/`，result SHA `7c292ef6d0581e8f56637f874af1ab910434d5475a969607b2f0a3936baaca32`；driver `handle-source.private.py` SHA `ebde3a472ace88ef6d10b0a3d45bedfba714d0f8ed937e6784d472f4010568d3`。不跟lookup或Level callee，不执行CPU；没有以两body局部通过批准整个网络接收或Avatar创建。

## 第四阶段：原924B失败的保留缺口与准确补证规格

当前真正消费边已经收敛到旧 `[14aa4c500,14aa4c89c)` /924B。核旧SCOPE可知当时 **所有分析只在内存**，只写了body SHA `5caa59bdd7f8fa41610831de6dace7610b31f831242049f8b4f9a843cf84ae84`、唯一pdata和 `ValueError: incomplete decode`；原raw、已解prefix与失败offset没有保存。指定旧raw目录没有924B原件，按完整digest查local的结构化摘要/文本/日志也无命中。不能谎称原raw已保留/恢复，或用错误摘要重建bytes。

这属于证据保留缺口，不是安全拒绝、A权限或本机输入不可取得。主线因此新增明确许可：**一次重新读取相同固定range用于保留与故障归因**，不是放宽strict或重复历史全量。复核同三输入完整hash、m666353普通AOT/参数、唯一pdata `[14aa4c500,14aa4c89c)`，recordRVA `1a8b5af4` / 原12B `00c5a40a9cc8a40a64b11104`；仅取924B，新SHA必须完整等于旧值，否则停不改pin。

新run带真实UTC timestamp，记录本次新materialization与旧摘要关联，不伪称旧时已留。仅用这份相同raw和原decoder做线性prefix/失败offset诊断；原strict失败保持，不能skip-data、裁尾或选择另一输入过门。不取邻窗、callee、CPU或A/未知返回。若现prefix本身足以给内联数据表的真实布局/表界/target，先交独立code-data proof的最小规格；否则准确保留当前所缺事实，不以“incomplete decode”泛封其它资料或父项。

### 新保存原件与失败诊断

本次实际重新核同三hash、m666353 ordinary AOT slot142dbac58/原8B `00c5a44a01000000`、完全相同唯一pdata后，仅新取924B；SHA严格等于旧摘要。新原件在 `level-rematerialized-20261116T055934.478727Z/`，不是旧时保存的raw。没有邻函数bytes、callee/global/CPU或返回模型。

用固定Capstone5.0.7、skipdata=false做原线性prefix诊断，得到210条/910B，失败 `14aa4c88e` /offset910，剩14B。原strict整体失败保持，prefix不标整body成功。最后两个被线性decoder解成push/std的bytes，实际位于下面指令引用的table起点；这首先给出有来源的内联数据解释，而非需要推定保护VM或unsupported ISA。

| 原指令 | 可独立确定的结构事实 |
| --- | --- |
| `14aa4c5bb MOV EAX,[RSI+8c]`；5c1 DEC | switch选择器是child16796的ProtEntity枚举减1 |
| `5c3 CMP EAX,3`；5c6 JA14aa4c7e0 | 仅uint32索引0..3进入表，其余去default |
| `5cc LEA RCX,[RIP+2b9]` | 精确table基址14aa4c88c |
| `5d3 MOVSXD RAX,dword[RCX+RAX*4]`；5d7 ADD RAX,RCX；5da JMP RAX | 四个signed32相对offset，不是代码字节或绝对指针 |

同924B内四entry为 `50fdffff/37feffff/64feffff/acfeffff`，即−688/−457/−412/−340；target分别 `14aa4c5dc/14aa4c6c3/14aa4c6f0/14aa4c738`，均是已解prefix的代码指令起点。`[14aa4c88c,14aa4c89c)` 正好16B，和此前908B合计完整924B。此次只作为诊断与新proof规格依据，没有按切短body追认原strict成功。

### 已交主线的最小code/data proof规格

只用上面已保存、SHA相同raw，不新取EXE/任何source，不执行CPU或跟callee。验证 `[14aa4c500,14aa4c88c)` /908B完整连续strict回拼；上述4×4B作为有源table解释、所有target必须是code内指令边界。对整个924B逐字节全覆盖且无丢弃：核normal CFG没有指令fallthrough或其它branch进入table；不能仅“剔除最后16B然后成功”。原whole-window strict仍为失败，新的分区proof另列。

通过此独立proof后才只看target type1/Avatar对应5dc的直接条件数据流；未知callee/A返回不解释，不扩其它type或新函数。当前已将规格交主线，未因此提前升级code/data闭合或Avatar必需字段结论。

### 独立code/data proof实际通过，原整窗失败保持

主线明确批准后，只读同SHA的新保存924B，实际完成908B code的208条连续strict decode与回拼，加16B四项table，逐字节覆盖全部924B。normal CFG有207条可达指令，四target均为真实指令边界，没有正常控制流进入table。`14aa4c88b` 的NOP在INT3终止指令之后，已strict解码且无入口/入边；它是明确记录的不可达对齐字节，没有丢弃。若强行从该NOP起执行会fallthrough到table，这种假入口不作为正常路径掩盖。

原whole-window strict仍是失败；本次是另立的有来源code/data proof，不追认旧线性解码。原件 `level-code-data-proof-run1/`：proof SHA `203434a3bbb83700d8bae9b212480f1037fdd73b34cd19556178b048bb0f4c34`；code ASM SHA `76f59dd136b9269849ab2ef0e4807b7296027cb687dea3fcf6d837acafc1bd6c`；driver SHA `7882100886c401d0b4a747282ffe85591906b15d1e63a9fe8968ee6bedc60b95`。补证driver SHA `160be39e24f5ca78311a5193aa0c2366a3fa726a8585205f8b5b1017ea46339c`；原失败diagnostic SHA `b2a61cb06da92b2d7b6441e80c9a8e35e7117a4b78fa063d5e942ffa366ef16f`。

### 仅Avatar case的真实转交

共同前缀 `14aa4c534..540` 把child和第四参数交14aa4ea60，AL非零则return。其返回含义未闭，不能叫existing-entity；metadata只证明owner80395/m666329的参数是16796与u32。`579 CALL1547052a0` 的未知返回决定能否继续，亦不能凭结果非null称Avatar成功。没有跟这两个callee或读取global实际内容。

已验target enum31801的Avatar=1，所以child+8c/tag1选择表项0、精确进入`14aa4c5dc`。这个case只取符号global下+9e80 receiver，null转错误；`5f3 RDX=child16796`、`5f6 R8D=Vision`、`5f9 R9D=第三u32`，`604` **TAIL-JMP1507ec920**。此case没有读取child+38/oneof或41868内部，不能因此说下游不需要它们。调用来源闭为(actual receiver, child16796, root tag9 Vision, root tag1 u32)。

## 第五阶段：唯一实际Avatar消费者的有限规格

主线据真实尾边另授这一个body。先对已有metadata/已采source索引精确查`1507ec920`：命中均为owner/method索引；已固定`analysis/avatar-init-plan-inputs.json`的functions中没有该entry，现有允许完整source不能复用。没有因此扫描相邻body或扩大调用树。

准确声明是owner56238 `HBIJFOJIFAH` /m467576 `LBLCKEHFBFG` /`1507ec920`，flags0xc6、virtual slot16；三参p381225→class16796，p381226→enum42790，p381227→uint32，均非by-ref，和真实尾边寄存器一致。此处receiver不是协议root参数，也不把RDX48178或SceneTeam数据搬来替代它。

固定相同三文件完整hash、metadata/signatures/commands及decoder/tool hash；核ordinary AOT槽、唯一pdata起点准确相等且完整body≤16384B，然后只取这一个完整body，strict连续解码、EOF与全文回拼。新raw只在本案ignored目录新run，不取新global/callee/CPU，不跟A返回/preview/B。源漂移、边界非唯一、strict失败保存原错误立即停，不skip/crop。

分析只判这个Avatar消费者实际读取的16796/41868字段、oneof/null错误或省略分支，以及和已有AvatarModel/GUID/authority-key的实际参数联系。未知call只留参数；准确冻结返回或必须靠未知返回才成立的用途停该条，其他直接字段分别记录。不会为了31raw齐全扩所有getter、nested、case或下一函数。

### 194B实际source与省略行为

唯一pdata实际为 `[1507ec920,1507ec9e2)` /194B，recordRVA `1ab79c2c` /原12B `20c97e10e2c97e1008f17304`。ordinary AOT与三参数均一致；51条完整strict decode、EOF、字节回拼通过。body SHA `1bf7730d552b67f974fc7607794b6b5d467ad9f60f9d201155728143dd6f4d59`；ASM SHA `ee9a472c4850263d3216daff2ea5bd39de320c535a9759209f471d4fbaf2a2a0`；result SHA `8a54b8ebdb33fd11345e991e1eca01e5b61183cba5038e47574423d57a04d2ed`；driver SHA `0d8241412a8b2b60a83a44bfbf17d139e6124862322443ee422560a389d25207`。原件为本案 `avatar-consumer-run1/`，无A CALL、CPU、callee/global内容读取。

| 准确指令/条件 | 已闭事实 | 首发最小性限制 |
| --- | --- | --- |
| `1507ec936/939`；`93b/943` | child null或child+90≠10→98a/98f ret | 这是未分派，不是实体成功或existing-entity证明 |
| `945/949/94c` | child+38的Avatar oneof对象null→同ret | **非null41868对象是本普通Avatar分派的前置**；不能把SceneTeam可null用途搬来 |
| `94e/955/958` | 对象必须精确class41868，否则9d2类型错误入口 | metadata cell1458cda58/usage711831准确指向41868 |
| `95a/961/964` | this56238+180对象必须非null，否则9d7错误 | 实际声明与来源在下节闭合 |
| `966/96c/972` | this+180对象+168/u32与41868+CC/u32比较 | Scene字段是已验tag1/wire0/identity；仅此一个41868直接load |
| `974..985`，相等 | 第五ABI参数写0；CALL1507e5660(this,原child,Vision,第三u32,0) | 只闭真实参数，没有把未知callee结果解释为AvatarModel构造成功 |
| `990..99d`，不等 | TAIL-JMP1507e7670(this,原child,Vision,第三u32) | 远端分支未取；不能用它替首发本地分支 |

IFix路径仅转14e3420f0，不取/执行。这个body不读41868的+D8/tag3、+C4/tag4、tag20/+50、其它nested/list/motion/装备字段。只能解除“本层需要31槽非默认”的假前置；后继真正消费尚未由此闭合。

metadata交叉确认相等callee为owner56238/m467666 `PLIFNLDKHIK` /1507e5660，四显参p381308/class16796、381309/Vision42790、381310/u32、381311/u32；不等callee为m467755 `LMHMOPJIJMK` /1507e7670，三显参16796/Vision/u32。既有init-plan的24个完整functions中没有这三entry；精确检索的其他命中也是metadata/index，不是可复用完整body。独立bindings receipt `avatar-consumer-bindings-run1/result.private.json` SHA `af37834c27bfd66d5e63b473d87bc73afe6d41d98f283aaceb12fb8480650541`。

## 第六阶段：复用旧UID合同，tag1可投影正常Player.uid

主线要求先闭比较key而不盲扩callee。本项只读固定metadata与旧Login/UID合同，未开新source或CPU。声明链准确为：

- owner56238 instance+180：field278865 `<EIFKLHKHDOH>k__BackingField`，**class58013/HOMIEDIPBBJ**。
- class58013 instance+168：field288254 `IIKHJHNGKMJ`，**uint32**。
- 旧任务 **A-04d5j / B-02c7** 已独立验证TokenRsp type37481 tag9/u32→响应+D4→owner58013/m481804 setter `15217aa90`→该会话+168；随后以目标literal57480 `__LastUid__`保存十进制值。不是凭混淆字段名或相邻u32猜UID。

旧四个owned样本值为 **1、FFFFFFFF、80000000、0**，均有实际uidSaved=true与对应`__LastUid__`结果；setter不在这些trace的返回模型表中。模型范围及原wholeHandlerExecuted=false保持，未把旧owned结果说成实机或完整Handler。`HandlerCore72`明确把该account指针存同manager+180；既有token-flow另核56238+180声明正是58013。此次没有重跑旧密码学/SDK/UID或读取A返回。

旧来源与当前生产源固定为：

| 来源 | SHA-256 / 具体合同 |
| --- | --- |
| `protocol/cn-7.2/handler-core-observations.json` | `1f0a640989d48b838ff15e75a03052fae4c3c19080552a5555c4b1228bc113ee`；同三input hash、tag9/+D4/session+168及四样本 |
| `local/migration72/evidence/handler-core-trace.json` | `c773b179d60751623811bee2c3ed42f2d5292eb73efa900e7c593b43621855f8`；原四样本及停止点15a4e0d9a |
| `tools/trace_client72_handler_core.py` | `4a785abb92b5a1707edb110738b1f2454a5652b806564759134ff0aa702e3017`；真实UID setter、同manager+180、保存断言 |
| `tools/trace_client72_token_flow.py` | `fdb03b4d8ee795d2dc0ccedc079efe69e8b4bf5ca771c5b4d73f744352c50964`；同会话类型与所有权核验 |
| `docs/CLIENT_72_SESSION.md` | `ea6d1dcc569a51769767d0ce09ff8d271045a8cbeeeffb66c6b5b7792109c417`；A-04d5j/B-02c7实际范围与模型 |
| `src/main/java/emu/grasscutter/server/packet/send/PacketGetPlayerTokenRsp.java` | `890e40f3c83c2abc9196483dc96dc8ad41834f68194cdb0431498f935cd8106d`；identity实际 `.setUid(session.getPlayer().getUid())` |

因此当前服务器可直接编码 **SceneAvatar41868.tag1 = Player.getUid()**，与已发送TokenRsp UID一致，满足这个本地比较分支的来源关系；不需要synthetic key或恢复英文原名。它不是Peer的OOJ14586f438 authority-key、AvatarEntity.id、SceneAvatar+D8或account_uid字符串。这些即使数值偶然相同也不能互换。

本次静态复用receipt `uid-existing-contract-run1/result.private.json` /5593B，SHA `d2217a9f9251c485e8cf13070d5ad624395d489696c2448efa329bdfb5038f17`；逐项固定旧源hash、metadata字段/方法、四值和生产投影。无新PE/body/CPU/global读取；已即时交主线与集成者。

## 第七阶段：唯一本地Avatar后继的有限规格

在UID比较前置已闭后，主线明确批准 **owner56238/m467666@1507e5660** 这一个实际本地Avatar consumer。不得取不等远端1507e7670。既有完整source索引无此entry，仅有metadata声明；先核固定三输入、四显参16796/Vision/u32/u32、caller第五ABI值0、ordinary AOT及唯一pdata起点。完整body≤16384B才取一次，strict连续解码/EOF/全文回拼；边界或strict失败保留准确原错停，不裁小窗。若现字节有有来源code/data结构，先报告准确独立proof规格，不暗中skip-data。

只判在SceneInfo/Team已有模型前提下，首发本地角色真正消费的16796/41868字段、motion/装备等条件非空、与AvatarModel/GUID/entityId的真实参数关系。不执行CPU、不取新global/callee，不推A或未知返回为成功；准确冻结依赖只停该条。未消费字段不扩全31或其它Entity类型。

### 本地body实际通过与准确A边

四显参、普通AOT与唯一pdata均成立：完整 `[1507e5660,1507e766b)` /8203B，recordRVA `1ab79a28` /原12B `60567e106b767e1020ea7304`。1674条连续strict解码、EOF、全文回拼通过，未裁小窗。body SHA `04cb08ebef134ab699d0484fb6c5b3304d90ef91b6b26350a34a544655e725f4`；ASM SHA `80ddb3ed599eafd7a6b6f4e7d126603e557e876eddcfef378a671abdc02fc0a6`；result SHA `e5939e20166f72560aa79669fe3018a806892a918f32d5d46a4da8d4a01b56f8`；driver SHA `f11fb7baf0759a81bc7c2959b5196a0d86750565774e3f74d7afe481ccf6cbfa`。原件在 `local-avatar-consumer-run1/`。

准确1405336c0 CALL共六处：`1507e56ab /6da4 /6dd4 /6f5e /706d /7213`。前次即时消息只列初见两处，现据全文更正为六处。全部未执行、未解释返回、未建新模型。

正常分支先在56ab到A；其返回值后被存入RBX，R15后续又指向该对象的槽。**不利用该返回/对象stores恢复receiver，不把后续`[R15]`写入角色或完整构造说成独立通过。** 以下只分项证明仍直接来自原参数RSI的load、已有独立lookup合同及源码null分支。整个函数没有完成执行；这里没有“在A之前已检查motion/装备”的结论。

### 独立新增：41868.tag3就是同一Avatar GUID

入口`1507e5686`保存原child16796到RSI。`58fb..591f`按case10取得+38对象并核41868；`593c`从该对象+D8读完整qword→RBP。`5999/599c/599f`以 `(GLOBAL+A650的已有manager, RBP)` 调用 **旧已验14e9b2c60**。`59a9/59ac/59b3`只接受非null且精确class18163的返回；否则走59b5..59e0返回，不继续后半。

这里不是未知helper的成功推断：旧 **F-03d3f1** 和 **F-03d3f2a CONTROL** 已实际验证owner35156/m294247 `14e9b2c60→156f6f510`，唯一显参是uint64，用完整Avatar GUID查同18163模型。精确class cell14589bc70/usage702959→18163。当前新增的只是 **41868+D8→该已验lookup key** 这条原参数链，不需要A返回来计算key，也没有继续消费本次A派生receiver。

所以可编码 **41868.tag3/wire0/uint64/identity = 同一实际Avatar.guid**；它必须引用AvatarData已建立的那个模型，不能新分配Scene专用GUID、截成u32、转double或拿entityId替代。uint64线域是0..18446744073709551615；首发选已有非零GUID。旧管理器不索引0，0可编码不等于批准出生用0。

旧来源固定：

| 来源 | SHA-256 / 复用范围 |
| --- | --- |
| `protocol/cn-7.2/avatar-contract-observations.json` | `a54f62de9a803e2070c515ecc854488827ea6c62db4468c633190d6733f2b2f3`；F-03d3f1原22个native fixtures，本项只复用lookup子trace，`models=[]` |
| `protocol/cn-7.2/avatar-control-observations.json` | `8e2433b3d13ec17414cc26f21461f1cc62f64b16c413b327610ce0b7e7480ec0`；另7个旧CONTROL图，Hero/本地来源仍区分，不合计为本轮新样本 |
| `docs/CLIENT_72_AVATAR_CONTRACT.md` | `c9d89e1058f5f4f8c64e9576567cab85dc3fe576abdbae6679a7226554066329` |
| `docs/CLIENT_72_AVATAR_CONTROL.md` | `fd1a78352dc80bdb115e02bfab74a1af666dd17f83b06701f6623f2f3785785b` |
| `tools/client72_avatar_contract.py` | `74383f9c724767bb0ff3e1d9f0436b516a328f6858154063a846a03733b68491` |
| `tools/client72_avatar_control.py` | `16194f5b60dcb61d6e78e10389039fb5e26dcb7f4ecc4969bdf6dad5802df9bf` |

原独立lookup包括`2^53+1`、uint64最大值、`0x100000001/0x200000002`高位哈希碰撞与同GUID模型重用；旧CONTROL还验证`2^63+1`。这些旧范围只读复用，未重跑native；新tag3仍按前卡已证reader身份变换与本次静态key来源编码，由集成者补独立wire/Java断言。

### Avatar内容的最小性表

“直接检查”只指本body中的结构条件，不等于已执行A/未知helper之后的完整路径。“可跳过”只指该明确block，不泛化为完整入场可以省略。

| 对象/字段 | 真实检查或转交 | 当前最小结论与停止点 |
| --- | --- | --- |
| child16796的Avatar oneof：case10、+38/type41868（tag10） | 前一194B body已在任何A前检查；本body58fb/590f重复核对 | **非null41868才能进入普通Avatar分派**；空对象导致上一层直接未分派，不算成功。inner字段不因此全必需 |
| 41868+CC/tag1/u32 | 上一body96c与同会话58013+168比较 | 已闭Player.uid，正常首发使用同TokenRsp的玩家UID进入本地分支；不是authorityKey |
| 41868+D8/tag3/u64 | 593c→599f已验Avatar lookup；未命中/错class直接return | 已闭同一Avatar.guid；它选择已有模型。未因此闭后续A派生receiver/实体创建 |
| child16796+70/tag4/type49847 | `58ca/58ce/58d1`读取/判null，null→69a2错误入口 | 当前普通后段要求该原输入指针非null；本body没有这里的null跳过/fallback。它出现在初始A和`1507edbc0(this,child)`调用之后，不能说A前已验证或未知helper已通过 |
| 上述49847+40/type84343 vector | `58d7/58db/58de`判null，null→69a7；58e4/58ee读+18及+1c | 同一后段要求这个vector非null；本body没有null省略分支。84343的三个float32与x/y/z旧wire已闭，但**49847内部哪个tag对应+40仍未知**；不套旧MotionInfo位置/旋转布局 |
| 41868+C4/tag4/u32 | `5943/596a/5970`与+D8一起交15946e710，参数为(dict,Avatar GUID,C4,1) | 和SceneTeam相同字典关联形状；C4角色仍未知。不能当AvatarID、entityId或authorityKey，也未证明要求非零 |
| 41868+90/tag21/type27246 | `5dd2/5de6/5de9`；null直接越过5deb..5e11的复制块到5e18 | **该直接复制块有null省略分支**；其目标receiver由A派生槽取得，目标业务用途不恢复。其它整个Scene转交的效果仍未知，不能据此批准完整handler省略 |
| 41868+70/tag8/type53824 | `7144`取指针；148d75460返回AL非零时`7151/7154`要求非null，否则7659错误；AL为零走71cd，null→71fa | **这是SceneAvatar自身+70，和child+70不同。** 它只有条件非null要求；未知helper返回条件、A派生receiver/回调效果未闭，不列全首发强制非空 |
| 41868+50/tag20/type17469 | `5d90/5d97`送151850020；此处没有内层非null判断 | 只闭参数流；callee对null/旧值/资源的行为未知。不要求为“31槽完整”造非空17469，也不声称省略安全 |
| 41868+30/tag7 list、+40/tag13 map、+60/tag12 list、+18/tag29 list、+20/tag14 map、+58/tag32 list | 分别送15184eee0、151847f70和15184fe30等已有入口 | 在此body没有把每个容器都判为必须非空；具体成员/空输入效果不能据raw完整性猜。旧151847f70清技能字典的事实仍保留，不能把空集合通称“保留已有值”；本receiver路径依A，未升级完整消费 |
| 41868+B0/tag6、C0/tag11、BC/tag17、C8/tag19、B4/tag23、D0/tag24 | 原指针直接load到参数；部分经已验int codec或写入后续模型槽 | 可记录raw转交，**不以A派生模型receiver/store批准新的业务成员角色**。原raw scalar/边界合同可独立实现；本卡仅新增UID/GUID业务投影 |
| child16796+98/tag2 | `5e18`送152f7df60，`5ebf/5ec5`另写同会话58013+1a4 | 后一条是独立原字段→已有会话成员流，但该成员用途尚未知；前一receiver依A。仍不把此u32自动叫EntityID |
| child16796+88/tag17、+9c/tag18，及+40/tag24、+20/tag5、+18/tag6 | `5b60..5b72`与+70交151847580；后几个指针交模型/属性入口 | 参数来源已明，相关receiver/非空内部语义未闭；不要求所有列表非空，不跟新callee补整棵树 |

RSI正常路径保持原child；唯一重赋值`1507e6379`属于失败日志支路，经63e8直接跳59bd退出，没有重回上述字段消费。未用跨分支同偏移命中或线性寄存器传播混同对象。

独立静态receipt `local-avatar-independent-run1/result.private.json` /11000B，SHA `b71a89c45aee6bd665e01d9a60b5a03ddd5d9706177ad366bf07ae3e19b3a8fa`：固定body/ASM/旧lookup hash，逐条assert原指令、六A站点、class/字段metadata、旧无模型lookup子trace及Motion reader声明。无新body/PE/CPU/callee/global，准确冻结返回消费0。

## 本轮可编码交付与准确剩余

L1仍复用原Cmd4469→type73627接收链，未新造正包号；L2新增实际Avatar分派和正常本地UID分支/已有模型GUID选择；L3不改前两卡的原raw wire，仅新增这两项来源可编码：

| server DTO来源 | 准确目标 | 独立边界要求 |
| --- | --- | --- |
| 同TokenRsp的 `Player.getUid()` | 41868 tag1/u32/wire0/identity | 非零及0/1/80000000/FFFFFFFF旧四样本；不要混authorityKey |
| 同AvatarData/Store/Team实例的 `Avatar.guid` | 41868 tag3/uint64/wire0/identity | 非零、>2^53、>2^63和max uint64 bit型；不能截断/浮点化/另造实例，0不作出生GUID |

集成者可独立编码这两个投影与wire断言，不等待完整构造。这里仍未批准发送完整4469/SceneTeam或entry-ready：

1. **root73627+18列表tag** 仍只有0x12候选；原prefix实验卡在未知初始化/metadata事实，不是已发生新拒绝或全场景A限制。旧失败与最小解除输入仍在SCOPE末节，未重跑VM/codec。
2. **原child fresh oneof构造/A返回** 与本local函数六个A派生receiver/回调依赖分别保留；本卡不以新业务名称绕过原冻结，不恢复完整ctor15183fe60/第二allocator。
3. **49847+40的wire** 是一个明确、尚可独立研究的最小输入，不能称所有资料已穷尽或因为别处A就自动冻结。固定metadata唯一stream52310 reader是owner49847/m414303 `MOMMACBEMEK@152f32d80`、参数p346756/typeIndex31422、slot19；精确已有source检索只命中metadata索引，没有完整已采body。本轮只定位声明，未取AOT/pdata/body；若主线另授，只需这一个reader先唯一边界再strict，不扩所有Motion/Entity字段。
4. **子+98的业务名、其他Scene列表/标量用途及装备首发必要性** 未因本卡闭合。已有完整Scene参数转交15184e8e0/151845140、tag20→151850020仍是准确未知消费边；不能为了填31raw就追完，也不能把returned/not-dispatched当落地成功。后继需由主线按真正首发必要性单独选定输入。

本轮只修改本专题并保存本案ignored源/receipt；前SCOPE、SCENEAVATAR_WIRE、DIRECT_USES保持稳定。实际验收是固定源、唯一边界、strict/回拼、code-data proof与已有合同复用；没有执行新native/客户端/SDK、没有改Java/Proto/资源/实例/Git，也没有新安全拒绝。文档/原件hash供唯一集成者收本地提交及共享Project/Harness/HANDOFF状态；本子项不自行提交。
