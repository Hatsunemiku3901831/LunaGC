# D02-M0-PEER-MIN-SEMANTICS：Ready Peer剩余字段有限语义

## 中文有限计划

本项仅研究既验Cmd28392/root19176的剩余消费关系。四个u32的wire、token/tag11、localAuthorityKey/tag10与SceneInfo有限空状态合同只读复用；旧Peer/Authority/C阶段专题保持稳定。不取新handler body、不执行native CPU、不读global当前值、不新增模型或追未知VM。

1. 固定旧1464B typed receiver、metadata/signatures和既采Scene/Session资料，逐指令确认 `14CD63298` 真分支前测试 `+40` 的实际receiver来源，绑定已有typed字段与生命周期。未知运行状态保持未知，不以初始化0代当前0。
2. 核root19176 `field4/+1C→14586F4A4` 写入的直接dataflow及已有精确引用，查是否与OOJ/localAuthorityKey比较或进入已知业务key；没有真实consumer不得假定单人host相同。
3. 核 `field12/+20` 在普通正文的实际读取/未读取范围，明确尚缺的producer或必要性事实，不填Scene3作为默认值。
4. 若既采源缺F4A4 consumer，仅做一次固定EXE、单cell的精确RIP读引用候选扫描，保留全部候选、opcode覆盖与边界/AOT/typed声明；raw命中不冒指令。候选多只列表，唯一后继source规格交主线审定，不擅自取正文或CFG窗口。

成熟字段关系即时交集成者；准确A/preview/B及未知VM依赖原样停。目标为完整Peer sender提供最小真实取值合同，或给出准确最小剩余输入，有限通过不升级完整通知或entry-ready。

## 当前状态

**field4已闭为与localAuthorityKey同域的另一authority key；field12仍未知，完整Peer通知不放行。** 本项完成已有1464B receiver的加载任务分支分析、一次单cell读引用定位；主线继而只批准候选③的117B Entity消费者，完整strict source已证明F4A4被复制到同Entity `+42C`，并与OOJ比较后将bool交slot155。没有凭protected方法名或旧四u32布局命名。

| root19176字段 | 实际用途 | M0有限生产来源 |
| --- | --- | --- |
| tag11 / `MCCMGLDOECH/+18` | 交真实LoadingTask token查询 | 同Player enterSceneToken，沿已验Peer/握手合同 |
| tag10 / `AANCEPCPHKJ/+24` | 写可变静态OOJ，参与已有Entity authority关系 | 本地Player正常分配的authority key，与SceneInfo已证投影一致 |
| tag4 / `OHNANNOOMCJ/+1C` | 写F4A4，独立Entity消费者把该值写同 `Entity+42C` 并与OOJ相等比较 | 可用raw别名otherAuthorityKey；当前单人Host就是本地Player，主线明确允许由正常Host分配同一authority key。官方Host英文名及多人/切换规则未恢复 |
| tag12 / `OHAKDDILKKB/+20` | 既采普通receiver无显式读取；VM/IFix后继是否读取未知 | 不填写3，不推定为destScene，不发完整Peer通知 |

这里将field4用于单人Host同key是有实际同域consumer支撑的M0业务分配；不是恢复官方字段名，也不是从初始0、两个u32形状或“单人所以随便相等”推导。场景ID、entity ID、Player UID与这两个authority key仍分开。

## 固定输入与来源

沿已稳[Peer producer](CLIENT_72_D02_M0_PEER_PRODUCER_20261116.md)、[Peer wire](CLIENT_72_D02_M0_PEER_WIRE_20261116.md)与[Authority CFG](CLIENT_72_D02_M0_AUTHORITY_CFG_20261116.md)复用Cmd→类型、wire和已证key关系，旧文档均未改。

本轮每次新EXE步骤固定profile1453B / `a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d`，由当前 `GAME_PATH` 解析三输入：

| 输入 | 字节数 | SHA-256 |
| --- | ---: | --- |
| EXE | 450927000 | `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| global metadata | 83644188 | `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a` |
| startup metadata | 4116148 | `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105` |
| metadata私有索引 | 117201003 | `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f` |
| signatures | 78649549 | `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe` |

既采正文/摘要：

| 相对原件 | 字节 / SHA-256 | 本项复用范围 |
| --- | --- | --- |
| `local/migration72/d02-m0-peer-producer/writer-run2/result.private.json` | 63965 / `f9077d62e14b00025a5a6c5846f9d71b87a975ff503f2ea7cf9709d1360a6a4c` | 完整1464B/341条解码记录，含保护跳转和填充；code SHA `023e0d7b71c4bdef29128fff083bd4e185794400da6294bd41b107b25e5eb9b3` |
| `local/migration72/d02-active/main/scene-entry-third.json` | 9143952 / `917a5ad0505c6e90e57c4080f0521e26589fadd0ccfb29eb3257363b8f2a9780` | 14CD5F7B0完整正文，code SHA `41522a2dd9266cd961fb8d2e8ff8cc7b05ed00636e219bfae41adbc3c8ce6f5b` |
| `local/migration72/d02-active/main/scene-runtime-context-third.json` | 3870401 / `bf9efd6225cd2836286b3a301bb2ea2f33dab6c6ddfb103d53aeed8f10db81d4` | 23977.cctor完整旧源与primitive base绑定 |
| `protocol/cn-7.2/scene-normal-observations.json` | 46249 / `d8a2aaa915b0c6ee5801fd3002d97256a9a1d3a44f1e3f2631f2b24e66c1914a` | 已验首次task/已有current两种实际flag结果；与旧 `scene-normal-final/summary-repeat.json` 同字节hash，不重新执行 |
| `protocol/cn-7.2/scene-deep-observations.json` | 285458 / `bed41fc74af5ef39b951c6d57834c10a56b0577da1166957d5221f19ada4acd5` | 既有精确RIP索引，没有本轮新增F4A4 read正文 |

## `+40`的receiver：真实加载任务而非this

`owner18753/m142764 ANAODHPHHNM(root19176*)` 的1464B正文，先在 `14CD63169` 将消息保存为R14，`6316C` 将this保存为RBX。`63199` 取消息 `+18` token，`6319D` 的RCX仍为this，`631A0` 传R8B=false，`631A3→14CD5F7B0` 查询任务。`631A8` 检查返回非null，`631B1` 将RAX保存RDI；`631D6 CMP byte[RDI+40],0` 所测就是该任务对象。并非manager本身：metadata中owner18753 `+40` 声明是 `ILuaActor63141`，不是bool。

任务身份有两条已采typed依据：owner18753 `+38` field88512为 `Dictionary<uint32,75453>`，`+10` field88507与 `+30` field88511均为75453；旧lookup在 `14CD5F9EA` 从相同字典entry取value，`FA93` 原样返回。此外lookup两处pool type usage独立绑定75453。

75453任务的 `+40` 是field374710/public bool `OOMECNAEDBH`；相邻 `+41` 是field374711/public bool `PIGBPDNANNG`。receiver先比较任务 `+50`（enum75454）是否为3；不等时把 `+38`（enum75456）设为5，并调用该任务的 `147B2F6A0`，然后才读取 `+40`。两个enum在此只记真实字面值，不补状态名；optional helper及实时调度可能影响状态，不能将某次初始化当测试点现场值。

实际分支：

- `631D6/631DA` 为false：直接跳 `63413`，进入localKey及F4A4的普通store路径。可分析该路径，不把它称为现场必走。
- 为true：先从任务 `+54` 复制到 `+2C`，调用已有场景/任务后继；`63278` 取任务 `+3C` 写Session `+204`，`63287` 取任务 `+2C` 写Session `+1D0/+1E4`。`63298 JMP15A4DCCB7` 后继当前未知，未跟VM，也未假定它自然返回 `63413`。

已验生命周期不能支持“默认false绕过真分支”：旧完整lookup在 `14CD5FAED` 明确 `MOV word[task+40],0101`。旧SceneNormal的 `nonzero` 首任务样本实际返回同dict任务，`flags40_41=[1,1]`；`current-task-slot` 样本将旧current保存到owner `+10`、新任务写owner `+30`，新任务两flag保持 `[0,0]`。这是两组真实原生结果，后者不是首任务状态证明。

这些旧样本显式使用已初始化metadata/interface、给定的脏pool任务、固定字典存储及无timer/coroutine上下文；`147B2F6A0` 走原生early-return。其最后边界是 `153A18E80`，不包含本Peer receiver。故本项既不拿 `[1,1]` 冒当前实机状态，也不使用合成false、未知初始化或新成功模型绕过 `63298`。

## field4到F4A4的已有dataflow

`14CD634B9` 直接取root R14 `+1C` u32，`634BD 8905E1BFB0F8` 将EAX写到 `14586F4A4`，无算术变换。此前 `63486..634B4` 将Session/Player模型 `+168` 与root `+24` 交 `156633000`；本项不取其body，不把这一调用推定为field4与OOJ相等检查。现有receiver没有比较这两个静态槽。

F4A4独立声明为owner23977/field116017 `KLPDJBILPPM` / u32，attributes `0x16`（public static，无readonly/literal），rawOffset `02003E94`。旧primitive base `14586B610+3E94` 精确等于该槽；它是可变值。

23977.cctor在 `14D2CA662` 装入 `RAX=056C8CC100000000`，`14D2CA66C` 执行8B qword store到F4A4。该store的低32位令F4A4初始为0；上32位属于下一槽，不将它混入本字段。初始化不是Ready阶段当前值，本项未读取任何global现值。

在本项固定五份既采原件中，精确RIP指令只有上述receiver写4B和.cctor写8B，没有read消费者；这只是限定原件的负结果。随后实际完成下面一次单cell定位，不以“没有文档”停止。

## 单cell读引用定位：全部10候选/7方法

只扫目标起始cell `14586F4A4`。固定EXE三段file-backed executable section合计376612864B：`.text`35394048B、`il2cpp`325725184B、`.upx0`15493632B。实际支持MOV8A/8B、CMP38/39/3A/3B、TEST84/85、MOVSXD63、LEA8D、MOVZX/MOVSX的0FB6/B7/BE/BF、CMP-immediate80/81/83的group7、TEST-immediateF6/F7的group0；ModRM只接受mod00/rm101，精确计算RIP目标，无prefix或一个raw REX prefix。LEA仅标为address materialization，不冒内存read。

没有覆盖寄存器间接alias、cell内部偏移/从cell前方起始的重叠宽读、其他算术/RMW/SIMD、absolute moffs，以及其他legacy/address/operand prefix的真实起点。一次扫描的raw前缀/后缀候选全部保留，不能把匹配位移升级为真实指令。10个候选都在il2cpp段，每个均有唯一containing pdata及普通AOT；其中仅③后经主线单独批准完整source验证边界。

| 编号 / raw site与形式 | 唯一pdata / 大小 | exact普通AOT声明 | 当前结果 |
| --- | --- | --- | --- |
| ① `1483E10AC CMP`、`1483E10C4 MOV` | `[1483E0F80,1483E1142)` / 450B | owner46314（parent28174）/m384192 `LNBLIFJGPBE`，protected v147，零参 | 未取body，未作消费语义证明 |
| ② `148D8ED39 MOV` | `[148D8EBF0,148D8ED94)` / 420B | owner28174/m219325 `BJPEPIGBOAO`，public非虚，零参 | 未取body |
| ③ `148D951B7 CMP`、`148D951CF MOV` | `[148D951A0,148D95215)` / 117B | owner28174/m219422 `LNBLIFJGPBE`，protected v147，零参 | 唯一获批完整source，实际两个site均为指令起点，见下节 |
| ④ `148D98164 CMP`、`148D9817C MOV` | `[148D98090,148D98264)` / 468B | owner28174/m219494 `MBHLLAKIGKC`，protected v160，零参 | 未取body |
| ⑤ `14BDA0D88 MOV` | `[14BDA0CE0,14BDA0E0E)` / 302B | owner24740/m191795 `IIIEIFCLBHE`，private，参数非by-ref82874 | 未取body，不因typed消息形状命名 |
| ⑥ `152A4EA7D MOV` | `[152A4EA70,152A4EABA)` / 74B | owner55550/m461299 `IMJJLFNEJFB`，public static，零参 | 小型读取候选，未凭名字/长度当getter，不取body |
| ⑦ `153DD5CE6 MOV` | `[153DD58E0,153DD5F68)` / 1672B | owner15955/m119463 `LateTick(float)`，protected v43 | 未取body |

未找到具名public的key谓词；没有按名称挑其它候选或展开继承树。①与③同slot不等于实际derived运行时派发已证。

## 获批117B：两key与同Entity +42C的直接关系

主线只批准③。重新核三输入、owner28174/m219422声明（flags1C4/v147/零参）、ordinary AOT cell `142A51DC0` 的 `A051D94801000000`，以及唯一pdata `[148D951A0,148D95215)` /117B（recordRVA `1A7CD864`，raw `a051d9081552d9087c4ff103`）。全117B/29条strict解码、连续EOF及逐字节回拼均通过，codeSHA：

`21c1efb9439de2c42cc69b4098bcbc66236b41aab5ad902cd0f30b21271b38b4`

普通分支的source比“名字像Host”更强：

| PC | 真实数据流 |
| --- | --- |
| `148D951A5` | RSI保存this Entity28174 |
| `148D951B1` | EAX直接读OOJ/localAuthorityKey cell14586F438 |
| `148D951B7` | 真正的6B指令 `3B05E7A2ADFC`，CMP EAX与F4A4的u32 |
| `148D951BD` | SETE DL，把两key相等结果转为bool |
| `148D951C0..51C9` | 以同this作RCX、DL作bool、R8=0，CALL vtable+5A8，即slot155 |
| `148D951CF` | 真正的6B指令 `8B05CFA2ADFC`，重新读F4A4 |
| `148D951D5` | 将该原值写同Entity `+42C`，field138501/public u32 `HBBONDKCMJF` |
| `148D951DB` | 另写Entity `+4EF` byte=1，其业务角色保持未知 |

前阶段已经证明base slot155/m219460 `LGKKECCILEG(bool)` 在精确entry可达CFG内写同Entity `+460` bool，base getter slot88读回该bool。此处复用只保留“实际target是base setter/getter”的条件；没有把base槽映射当所有derived实际dispatch，也没有取新callee。

即便不扩slot155的derived语义，F4A4→同Entity `+42C` 的直接store以及F4A4与OOJ的u32相等比较，已独立证明field4与localAuthorityKey同域。它不是compare0或字段同形推断。普通patch guard `1457EC06E` 的替代分派仍未取当前值/未展开，source不当实机执行。

该成熟同域合同已即时交唯一集成者，可对当前单人Host使用正常分配的同authority key作field4的M0策略值；不需要等待官方英文HostPeerId。真实UID、sceneId、模板ID不能填这里。完整Peer sender仍等field12/真任务路径剩余边，不能由本节放行。

## field12负结果与下一唯一小source

对完整既采receiver逐条Capstone检查R14-based内存访问，只得到五次read：`63199/633AA→+18`、`63413/6348D→+24`、`634B9→+1C`。没有 `+20` 的显式read。R14保留同root19176；这只能证明当前正文未读field12，不能反推field12一定无用或一定只在VM读取。顶部IFix和 `63298` 真分支仍有未知后继。

主线要求先给真分支的最小新source规格，本项仅metadata/pdata/section preflight，**尚未读这份body**：

| 前置 | 已核事实 |
| --- | --- |
| 实际入口边 | 既采typed receiver的 `14CD63298 JMP15A4DCCB7`，不是凭字节近邻挑入口 |
| 唯一pdata | `[15A4DCCAA,15A4DCCD7)` /45B；target在begin+13；recordRVA `1B2946D0`，raw `aacc4d1ad7cc4d1afc093d1a` |
| AOT关系 | target及pdata begin均无普通AOT声明别名；原typed入口仍owner18753/m142764、cell1429BC230→14CD63160。不能把保护后继冒成另一具名托管方法 |
| 映射 | target完整落在file-backed executable `.upx0` 段 `[15A3CE000,15B294A00)`；本项只查section身份及pdata，没有读target指令 |
| 已知live-in | R14=root19176；RDI=上述75453任务；RAX=此前取到的Session；ESI=该Session旧+1E4；EBX=task+2C；ECX=0。其余volatile寄存器/真实对象现值不猜造 |
| 现有source检索 | 对既知 `d02-active/main`、`astra-scene`、旧Peer producer与SceneNormal原件精确检索，仅旧writer结果包含该JMP目的地址；没有目标正文。这是限定资料的负结果，不声称全盘不存在 |

若主线继续授权，最小独立步骤是按同固定输入取此**完整45B pdata范围**并strict回拼，只从已证B7入口追真实可达控制流；不扩大到整个VM，不把45B当完整typed handler。若R14+20的真实读在可达尾部即可独立记下；若遇未知CALL/间接分派/范围外后继或解码失败，准确报该一条边，不裁尾、跳data或补模型。不能事先保证32B入口后范围一定含field12消费。

当前还缺的是field12到具体业务值的实际consumer/producer，或该字段在真实所选路径无需填写的独立证据；不是缺完整SDK、全局metadata默认模型或整个场景研究。真task `+40` 的现场值若决定生产路径，应从真实同token任务生命周期取得，不能用合成false排除本边。此处没有启动动态进程、执行CPU或新取其保护body。

## 原件与实际验收

以下均相对忽略根 `local/migration72/d02-m0-peer-min-semantics/`：

| 原件 | 字节数 | SHA-256 |
| --- | ---: | --- |
| `locate_peer_min.private.py` | 17803 | `520cd258bf639714c3ad767507c3ff2ab50e36eede3b0400babfa1ca408f01e1` |
| `run1/existing-evidence.private.json` | 32806 | `b33e19597d6933088bb4f5818a7ea35c4a561a4452c4eefd80e23ce5e6c36f56` |
| `run1/raw-read-candidates.private.json` | 42300 | `319f48ae4c744910674b8fcd62f446661d7d122c5d67cacbcd21c46bb7e63d51` |
| `run1/result.private.json` | 68743 | `6ee38d43813978a5169d85903b9c87f2f9b87b4eb81b585fdc1720fae1e3e725` |
| `collect_entity_key.private.py` | 8134 | `4e9680d4a762a30a34feca41b64d8aa334d22aaeb2e35add5978f6f65ec415d9` |
| `entity-key-run1/result.private.json` | 10506 | `1a96a12951abfaa0aa5cba4b41439f4fe90ec07f6e9a559c3f3393f5d3b98c1d` |

本轮实际通过固定输入、既采指令精确解码、单cell候选保留、全部普通AOT/唯一pdata、唯一新增117B strict全回拼及两raw site指令边界核验。无新失败或安全拒绝；旧scope/Authority/Peer/C阶段停止原样保留。无CPU/native模型/新global值/运行/Proto/生成/构建/DB/Git/客户端动作，旧原生样本未重跑。唯一tracked修改为本文，由集成者独立提交；有限字段4可编码不升级完整Ready/Peer或entry-ready。
