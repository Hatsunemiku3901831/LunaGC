# D02-M0-PEER-TRUE-BRANCH：首任务真分支有限source

## 中文有限计划

仅补主线批准的 `[15A4DCCAA,15A4DCCD7)` /45B，真实入口为原typed Peer receiver `14CD63298 JMP15A4DCCB7`（begin+13）。前阶段MIN_SEMANTICS及Peer/C/Authority专题稳定不改。普通AOT别名为0不冒成另一具名普通method；旧任务样本不能当当前flag值，不能默认false绕真分支。

1. 核固定三输入、原1464B typed receiver/hash及实际JMP指令、唯一pdata/section映射与旧preflight，完整保存45B原件与hash，不执行目标CPU。
2. 对全部45B尝试strict线性decode与EOF回拼；任何失败保留原bytes和准确停止，不skipdata、丢bytes或裁尾称完整成功。
3. 独立只从B7已证入口携带R14=root19176、RDI=75453任务追真实可达指令，维护边界/重叠/分支覆盖。前13B与未到字节不作本路径语义；RET终止，未知CALL、间接、范围外或解码失败停止其后，不跟VMdispatcher/callee/global或加默认model。
4. 只检查field12/root+20是否有真实访问、是否与已有EnterSceneNotify/task场景字段有直接关系。未出现不当作无必要性；若只有保护分派，准确给下一最小边，不泛称SDK或所有协议blocked。

唯一tracked产物为本文，raw只在ignored `local/migration72/d02-m0-peer-true-branch/`。成熟结果即时交集成者，不放行完整Peer/Ready或entry-ready。

## 当前状态

**45B原件已取得；全范围线性strict decode未完成，B7入口可达前缀准确止于未知CALL，field12仍未闭。** 没有更改边界、重取小窗或将部分解码称完整通过。

从已证B7入口只得到两条真实可达指令：PUSH立即数 `56FE983E`，随后CALL `15A46C1EF`。此前没有root19176 `+20`、任务或场景对象字段读取。其后未知，不因此判field12可省或必须填某个值，也不以首任务默认false绕过真分支。field4的同authority-key域合同继续沿前阶段独立成立；完整Peer通知/Ready不晋级。

## 固定输入、入口与范围

前置 [MIN_SEMANTICS](CLIENT_72_D02_M0_PEER_MIN_SEMANTICS_20261116.md) 17064B / SHA `d664504f8841873c982528a1b3fd85d251d6f24c1e5efdce428fdb5820e99a6e` 保持稳定。本项不修改旧Peer/C/Authority文档。

三输入由本机当前 `GAME_PATH` 解析，全部实际复核；不记录本机客户端绝对路径：

| 输入 | 字节数 | SHA-256 |
| --- | ---: | --- |
| EXE | 450927000 | `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| global metadata | 83644188 | `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a` |
| startup metadata | 4116148 | `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105` |

profile1453B/SHA `a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d`、metadata私有索引117201003B/SHA `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f` 也按原pin核对。

原typed receiver证据为 `local/migration72/d02-m0-peer-producer/writer-run2/result.private.json`，63965B/SHA `f9077d62e14b00025a5a6c5846f9d71b87a975ff503f2ea7cf9709d1360a6a4c`，code SHA `023e0d7b71c4bdef29128fff083bd4e185794400da6294bd41b107b25e5eb9b3`。owner18753/m142764 `ANAODHPHHNM` 唯一非by-ref参数为root19176；ordinary AOT cell `1429BC230` 仍为 `6031D64C01000000`，指向 `14CD63160`。该完整旧source的实际 `14CD63298 E91A9A770D` 是 `JMP15A4DCCB7`，不靠近邻字节猜入口。

| 范围属性 | 实际复核 |
| --- | --- |
| 唯一containing pdata | `[15A4DCCAA,15A4DCCD7)` /45B，beginMultiplicity=1 |
| 记录 | RVA `1B2946D0`，raw `aacc4d1ad7cc4d1afc093d1a`，unwindRVA `1A3D09FC` |
| 异常目录 | RVA `1A4E6A40`，14344080B，SHA `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d` |
| 真实入口 | `15A4DCCB7=begin+13`，由typed原生直接JMP提供 |
| ordinary AOT别名 | target与pdata begin的普通metadata声明均为0；不能将本range冒成另一个具名托管method |
| executable映射 | 全45B位于file-backed `.upx0` executable section；读取前已核边界，不跨映射 |
| 与旧preflight一致 | 原 `entity-key-run1/result.private.json` 10506B/SHA `1a96a12951abfaa0aa5cba4b41439f4fe90ec07f6e9a559c3f3393f5d3b98c1d` 的唯一记录逐项相同 |

完整45B保存为忽略原件 `full-range.bin`，SHA：

`5cd763a5fde151a60e09b85b2e49fcdd10eb119240c85d0409425279efa4bb90`

本项只把它称为完整**pdata source范围**，不称原typed receiver的完整实现。没有读取CALL后继或其他候选body，也没有读取global当前值。

## 完整线性解码失败与独立入口CFG

Capstone固定5.0.7及既验module/DLL hash，`skipdata=false`。从AA开始的全45B线性尝试只解出3条/7B，停止于 `15A4DCCB1` 的 `D5` 起始字节，未到range EOF：

- `linearDecode.status=incomplete-stop-preserved`，`decodedEnd=15A4DCCB1`。
- 全45B及未解剩余字节原样保留，没有跳D5、改起点、删除前13B或裁尾来宣布线性strict成功。
- 这三条记录仅表示从range begin机械线性解码的结果。没有从原Peer真分支到AA的已证入口，故不将其当本路径指令执行事实或猜成数据职责。

根据主线明确授权，另做与该线性结果独立的 **已证B7入口可达CFG**。它只对已保存bytes逐条严格解码，不取另一个EXE窗口，不把其结果替换上述全范围失败：

| PC / 指令边界 | 可达指令 | 本路径事实 |
| --- | --- | --- |
| `[15A4DCCB7,15A4DCCBC)` | `683E98FE56` / `PUSH 0x56FE983E` | 仅压入该立即数；未证明其是opcode、token、sceneId或VM selector |
| `[15A4DCCBC,15A4DCCC1)` | `E82EF5F8FF` / `CALL 15A46C1EF` | 真实直接CALL，目标在批准范围外且职责未知；立即停止，不跟callee、不安排CALL fallthrough |

CFG只有一条已证fallthrough边B7→BC，覆盖10B，边界无重叠；没有观察到RET或本分支成功返回。前13B未默认纳入CFG；C1之后的22B虽已完整保存，也没有被当作CALL返回后的可达代码。`15A4DCCC1` 仅是CALL指令之后的地址，不能由架构返回地址推定callee实际返回此处。

入口继承原已采dataflow：R14为root19176，RDI为75453任务，RAX为此前取到的Session，ESI为旧Session `+1E4`，EBX为任务 `+2C`，ECX为0；其余volatile寄存器/实际对象值未知。这两条指令没有读写R14/RDI指向的对象字段，故其前缀没有field12或任务场景字段的数据流。但未知CALL可有任何尚未恢复的消费，不能把“前缀没读”升级为“该通知无需field12”。

## 现在仍缺什么

| 项 | 已有事实与准确缺口 |
| --- | --- |
| source | 45B、hash和真实入口已得；完整线性strict失败准确保留；可达前缀2条已证明。不是缺原件或没分析 |
| field12 | root19176/tag12 `+20/OHAKDDILKKB` 的wire已验，但本前缀未读取；与EnterSceneNotify/task场景字段的业务关系及非零/省略必要性均未闭，不填Scene3 |
| Task状态 | 所选边要求75453 `+40` 真。旧首任务样本 `[1,1]` 与已有current样本 `[0,0]` 保持各自离线前提；现场flag未知，不补synthetic false |
| 本轮停止边 | **`15A4DCCBC CALL15A46C1EF`，前有PUSH立即数56FE983E**。需要的是这一准确调用上下文内的真实语义/后继关系，而不是整个VM或完整SDK的泛称 |
| 准确冻结依赖 | 该前缀未触达已冻结A返回、preview/B；目前没有证据把新CALL目标等同那些动作。原冻结继续生效，不能把未分析callee泛写成已被安全拒绝 |

当前授权在未知CALL停止，未取新callee的边界、body、global内容或dispatcher模型。下一最小输入是上述唯一调用在此压栈立即数及R14/RDI上下文下的实际消费/返回关系，尤其是否读root+20并进入已证任务字段；其来源门和范围需后续独立审定。本项不预先给它贴VMdispatcher名，也不凭未看正文断言需要某个运行时Task对象、metadata初始化或准确冻结返回。

field4与本地authority key的同域关系已在前阶段真实Entity source闭合，可以独立供给有限raw投影；本轮未撤销它。当前field12/完整真分支仍是完整Peer通知的最小未闭边，局部身份、wire、空Team状态或编译通过均不能替代它。没有批准完整Ready/Peer发送器、部署或客户端入场。

## 原始证据与实际验收范围

以下相对忽略根 `local/migration72/d02-m0-peer-true-branch/`：

| 原件 | 字节数 | SHA-256 |
| --- | ---: | --- |
| `collect_true_branch.private.py` | 12462 | `e753bd97d2a2ba5490decded65cf7717cb87703592799feb2d179399d340e87e` |
| `run1/preflight.private.json` | 4303 | `787975c01de0f0b087577db1a9d4e976c622a1fa61cce0577008b9b4b0d8b78e` |
| `run1/full-range.bin` | 45 | `5cd763a5fde151a60e09b85b2e49fcdd10eb119240c85d0409425279efa4bb90` |
| `run1/raw.private.json` | 4525 | `56182d9ce9cf8a0b1f3686876da5cefabb7425e4299154f972fe3481d06449e7` |
| `run1/linear.private.json` | 5481 | `f5415ef2c64e16984dbae26e32bc39e7a71021c624a48a391bb844c41f019cb8` |
| `run1/result.private.json` | 7067 | `d3e9e57531775e5b5e4dbfa17334b05248730e17a89e9c96fcad7c0facef1b51` |

实际完成固定三输入/metadata/原typed AOT/JMP核查、唯一pdata和映射门、原45B保存、全范围线性失败记录、两条独立入口CFG及未知CALL停止。**没有全范围strict通过或完整CFG成功声明**；没有新安全拒绝。无CPU/native进程/global值/新模型/新callee/运行/Proto/生成/构建/DB/Git/客户端动作。只有文档与忽略证据，无需本项构建或部署；唯一集成者处理本地提交，未自行提交。
