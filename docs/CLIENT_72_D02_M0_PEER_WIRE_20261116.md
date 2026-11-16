# D02-M0-PEER-WIRE：进入场景authority key最小wire

## 中文有限计划

前置[PEER-PRODUCER](CLIENT_72_D02_M0_PEER_PRODUCER_20261116.md)固定SHA `3ec119d701b985d7fcbc7923ac450c6e4239595c9349056116cdc05380fd9779`，其typed m142764/1464B source已经证明28392/type19176普通路径把+24/u32直接写14586F438，+18进入既验token任务查询。此前候选不是按旧9633/四u32选中；前置专题保持只读，本项唯一tracked产物为本文，raw继续仅在`local/migration72/d02-m0-peer-producer/`下新run。

1. 仅取主线已批准的19176 reader m146084@152D740F0：复核固定profile/三输入、metadata/signatures、slot19与唯一非by-ref stream52310、普通AOT、唯一pdata及body<=4096B；门过才保存完整source并strict连续解码/EOF/逐字节回拼。不新取ctor/.cctor/helper或其他writer。
2. 从本reader实际tag分支、helper、store和变换恢复四字段raw wire。token、localAuthorityKey的业务值/变换分别对照writer实际消费，未知+1C/+20角色不套旧host/scene名。若保护入口或门失败，保留准确下一最小规格，不猜tag、不裁窗、不执行CPU。
3. 给M0的localAuthorityKey可由同一Player正常peerId或显式服务端key分配；它是服务端authority身份值，不是客户端assetID。必须与OOJ比较、SceneInfo World/Team对应字段及本次真实实体关系一致；不先假定官方peerId名称。未知bool、root+20用途、hash职责仍未知。
4. 成熟tag/变换/来源立即交集成者编raw Proto及独立非零/边界/错误wire断言；不发未闭sender，不把生产编译或静态源代作客户端Ready成功。静态取源阶段无CPU；后续只有下文主线单独批准的原样owned reader验证。无客户端、新hook、global现场取值、默认模型、运行/Proto/构建/DB/Git操作；A/B/preview原冻结及未授权VM依赖保持停止。

## reader实际静态源与准确停止

m146084的唯一非by-ref参数为stream52310，slot19；ordinaryAOT cell1429C29F0的8B为`F040D75201000000`。唯一完整pdata `[152D740F0,152D74278)`/392B，record RVA1ACA3478，按固定三输入和exception目录hash实际核对后读取。code SHA `f71541d4e31b4e503bafc4c68af2bcfaf95fa8d81a9d00cfb6c1b4bdbe64e50c`，严格连续/EOF/逐字节回拼通过。

该392B源仅有入口`152D740F0: JMP15A4E468C`、380条INT3和1条NOP填充，合计382条解码记录。它没有给任何字段tag；填充解码成功不算reader语义闭合。静态取源止于该保护后继，未新取其body、跟helper或执行CPU。

原件相对忽略根`local/migration72/d02-m0-peer-producer/`：driver `collect_peer_reader.private.py` 4945B/SHA `17b441632135d865db7c947ad9afa1083e5ec69207a880f8ae886f245c9f1193`；`reader-run1/preflight.private.json`3467B/SHA `813077eafca1243cfd45149fd578db6ca99098c33ab07af40bef82c561087d46`；`reader-run1/result.private.json`59530B/SHA `c1fd14b4ec90dfd6adbe2bffd8ef1d36132cc339404ca99d44ad33667e0f8a61`。

## 单独批准的原样owned scalar32：33个实际样本

静态停止后，主线明确批准复用已验Reader72/Offline72原样合同，实际执行此精确reader入口；不新建VM模型或取得新保护source。保留6个无关旧response codec fixture、3个旧TypeInfo及原13模型；它们不是本root来源。根字段全为u32，不配置此root codec/.cctor/list或目标metadata初始化，新增模型/hook/预算为0。

正式方案上限为53样本：空baseline（合成零对象/count1，不声称真实ctor默认），再tag1..32逐个规范wire0/value1，只观测metadata已declared的18/1C/20/24四个4B native-store；四个唯一tag齐即停，各命中tag再验0/1/1357/80000000/FFFFFFFF。未知helper/A/目标codec/init/越界/系统指令或原20M指令/20秒单样本预算首次停止，不扩tag、不加模型或默认返回。

实际在tag12前四字段均命中，共33个样本：1个空baseline、12个逐tag发现、20个逐字段边界。没有继续tag13..32；所有未写入/不变样本保留，所有33个都正常返回且耗尽各自输入。baseline无事件，所有写事件仅为目标四字段的4B native-store。每字段五向量均原值写入，结果为identity；该结果来自目标保护代码实际store，不是模型替字段赋值。

| raw tag / key / wire | 字段 / offset | native store PC | 用途与范围 |
| --- | --- | --- | --- |
| 4 / `20` / 0 | OHNANNOOMCJ / +1C / uint32 | `15A42A4F1` | typed writer另写static14586F4A4；业务角色未具名 |
| 10 / `50` / 0 | AANCEPCPHKJ / +24 / uint32 | `15A4D3B37` | localAuthorityKey，typed普通writer直接写OOJ14586F438 |
| 11 / `58` / 0 | MCCMGLDOECH / +18 / uint32 | `15A42FEAC` | enterSceneToken，typed消费者交既验任务/token查询 |
| 12 / `60` / 0 | OHAKDDILKKB / +20 / uint32 | `15A4C87B0` | 普通writer未直接读取；用途仍未知，不按四u32旧布局叫scene_id |

各行独立向量均为 `0→0`、`1→1`、`1357→1357`、`80000000→80000000`、`FFFFFFFF→FFFFFFFF`。例如token4951的literal wire为`58D726`，localAuthorityKey4951为`50D726`；两个高位样本分别为`588080808008`/`58FFFFFFFF0F`与`508080808008`/`50FFFFFFFF0F`。明确0样本也实际写0，不把缺省零对象当显式0覆盖证据。未测超出u32域值、恶意varint、错误wire、四字段组合/重复覆盖或完整typed handler，不对这些补结论。

原样Reader72 SHA `b12d1e6a2fcd5ddcb88365188f759d3a6f08f43ab9cb7fbe4a2fa8f6db348c32`、Offline72 SHA `8007d2594ef8058a4f2a9f1c24094fb5d2551a202b4e52154876cdfe77a17c3c` 和旧九pins在执行前核对，实际profile五stock也全部复核。初始13个模型、两个原允许区、before_instruction=None及原预算保持；此root的entry允许区仍按原合同到下一AOT152D74280，与完整pdata止74278的差别记录在执行前原件。只有既有ReadTag1474D8100、Skip1474D7BB0、scalar32 147520110模型被调用；无目标codec/.cctor/list/init或A调用，AReached全部false。33样本共3985220条observed指令，未运行Windows客户端。

| 原件（相对本项忽略根） | 大小 | SHA-256 |
| --- | --- | --- |
| `peer_owned_scalar.private.py` | 9557B | `3a46f4b2c7cce20475df5f8db55c774573e628dd512f45af7d3c3282d6c9e060` |
| `owned-run1/pre-execution.private.json` | 5320B | `343ff29f9578b4a40813dbc3578b657d49e5e83ece236dc5e2a0161a2b50903f` |
| `owned-run1/discovery.private.json` | 20396B | `a874448a78684624d2f233e5883e65f5ecf5dfaf9cd3b9c111557ba2897fe314` |
| `owned-run1/result.private.json` | 54672B | `55e7d2d5c7ec932400ee94157363fc5037bde000bf335cf2fc2dd6999bfed7a1` |

所有33个逐样本原件均保存。没有失败或新的安全拒绝，没有通过新增模型/metadata初始化把失败改成功；6个旧codec和3个旧TypeInfo的原fixture限制仍在，不把它们说成此root自然实机环境。

## 可编码的M0 key合同与准确剩余

已即时交集成者：28392/type19176完整四u32 raw schema，及已证tag11 token、tag10 localAuthorityKey业务投影。二者在上述边界向量中为identity，不套Ready Req/Rsp或PlayerEnterSceneNotify的其他token/scene掩码。

key可由正常Player.peerId或明确的服务端u32 identity分配；它不是资源/asset ID，无须先恢复官方peerId英文名。实际对象关系必须统一：本通知tag10→OOJ；SceneInfo44512/tag1→同Entity+42C的authority比较key；Team manager14828FEEA读取当前OOJ后交14828F870，最终authority setter/getter在AUTHORITY_CFG所列真实虚目标条件下使用同一key。World/Team实体ID仍分别来自真实World.levelEntity与Team.entity，不能把key、authority bool或90000004/90000002模板混作实体ID。

未知保持单列：tag4的另一static key尚未具名，tag12没有普通路径用途，+40真分支的未知保护后继仍未分析；四字段raw闭合不等于这些业务值可随意填入。生产方若使用最小token/key投影，未填两字段的实际必要性及完整Ready后继仍须由真实consumer/客户端判定，不因raw schema编译通过升级。

本阶段已做完整reader静态门与33个owned native scalar样本；没有执行typed writer/handler、全消息列表、A返回、客户端、生产Proto生成/测试/构建/部署或Git。唯一集成者继续raw Proto、独立wire断言及同Player key/实体交叉验证。Peer完整sender、M0入场及entry-ready均保持未验；producer前置与其他已稳专题不修改。
