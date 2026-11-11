# E-01d1 / R3-PROUD-CURRENT-ADOPTION-SOURCE-PLAN

2026-11-11C，父 D02-R3。**本机既存来源有预览以外的当前 Proud 选择／重建链，可以继续静态推进。** 前一 scalar 包“已验输入无 caller”只覆盖其输入子集，不能作为全项目外部阻塞结论。本专题更正范围判断，冻结旧报告不改；不重做 `threshold==phase+1`，不重扫 73 候选或新爬 GitHub。

本项先固定最新 Project、恢复索引及采用/lookup/query/promote 等专题的版本，留 `document-pins.private.json`；技术原件另 pin。仅写本文和 `local/migration72/d02-resume-20261111c/proud-current-adoption-source-plan/`。没有新 PE/global/live/CPU、collector、机械测试、JVM 或 Git 操作。

## 现成非预览来源及证据层级

直接复用[已验 Promote 专题的独立入站链](CLIENT_72_D02_PROUD_GATE_COMPARISON_PROMOTE_SEMANTICS_20261111B.md)、[原行／组等级合同](CLIENT_72_D02_OLD_PROUD_PAIR_20261111.md)、[两表示字段桥](CLIENT_72_D02_PROUD_GATE_COMPARISON_ROW_BRIDGE_20261111B.md)及[lookup 合同](CLIENT_72_D02_PROUD_LOOKUP_CONTRACT_20261111B.md)。证据分别是：

| 层级 | 已有准确来源 | 未由它证明 |
| --- | --- | --- |
| 入站字段／列表 | raw class48178 的 `+68/tag14`、`+80/tag35` 是 repeated U32，wire0/2；`15184e369/36d` 分别作 R8/R9，经 `15184e390→151847f70` 传入同 Avatar 的 m138165 参数2/3 | 没有本轮自然包；不批准 inherent/special 业务别名，不证明 Java sender 的对应字段。 |
| 初始行对象 | m138165 以原 U32 k、`&out`、bool低8位0，分别在 `1518488ca/151848f1a` 调 `14d7ac240`；caller 先测 AL，再使用 typed63831 out P | 该 helper 本体未存；k→P、AL/out 一致性、bool/cache/清理语义仍须原窗。 |
| 原字段值流 | 同 P 的 `+40` 声明41105，读 `+90/+84`；否则同 P 的 `+8 &~ff` 交 `148ba9780` 读 native30h/38h，再经58209 getter。非零 group 后，将同 group/base level 经32758编码，交同 Avatar `+1f8` 的 `1593cff80`，R9B=1 | 没有 actual encoded/getter；encoded-key Dictionary helper 的插入/覆盖语义未审，不能据 flag/name 补结果。 |
| 当前行选择 | 同 Avatar `151846d90(group,bool低8位1)` 从 `+1f8` 和 `+200` 查同 group，解基础值与附加值，32位加和后调 `14d7ae900(group,sum,bool)` | 这不是 phase 门槛；缺基础键走原错误分支，附加键**已证缺失**才走原 encode(0)，未知输入不能当缺失。 |
| 选择后采用 | selector 返回 D 存 RBX，null跳过；正常空集合／无重复且容量够路径，把**同 D 引用**写入 Avatar `+1c8` 或 `+40` 的 backing array，并更新 count/version | 原 duplicate 查找和 cache 侧支不替换成 host equality；没有自然采用／map hit，也不声称无条件原样追加 k 对应行。 |

typed 字段独立核：Avatar18163 的 `+1c8/field85835`、`+40/field85816` 是 class34054，承载63831集合；`+50/field85818` 是另一集合。`+1f8/85841`、`+200/85842` 均为 `Dictionary<32758,32758>`。63831.field316249/+40 声明41105；其 `+90/203376`、`+84/203373` 均为58209。两个编码类型不可混用。

原 caller 的关键 PC 为 `151848a3e`（同 group/base level 实参）、`151848a97`（同 Avatar selector）、`151848a9c`（D→RBX）、`151848aa2`（D为空跳过）、`151848803`（D引用入+1c8 backing）。另一列表对应 `15184908e/90e7` 与 `151848e56`。这些来自完整旧 listing，不伪称本轮新增 raw。

### selector 的现成静态业务关系

`151846d90` 是唯一 owner18163/method138266，instance 参数 U32、bool。旧完整 listing 范围 `[151846d90,151846fbc)` 提示556B：

- `6daf` 取同 Avatar+1f8；原 signed-index helper 为负时走 ThrowKeyNotFound 路径，不返回默认基础等级。
- `6e2e` 取+200；已知负索引分支 `6ee8..6ef7` 明确调用32758 setter编码0，存在时读取并解其值。
- `6f2c` 用原32位 ADD 得 `sum=(base+extra) mod 2^32`；`6f35` 将 group/sum/bool交原351B `14d7ae900`。
- 原351B在 `14d7ae949..94f` 构造 `(u64(sum)<<32)|group`；interface lookup AL命中后取 out U32 k2，再于 `14d7ae9f9` 调已存 `14d7adf70(k2,bool)` 返回原行。没有 `group*100+level`，更没有自动 `group*100+1`。

因此 **k 与最终 k2 必须分开**：输入 ID 先提供 group/base level，随后同 Avatar 状态可选择同组另一行。当前采用的静态参数关系已有来源，实际 k2/map hit/行仍未知。

## QueryWrapper／对象寿命与真实首缺

现成435B `148ba9780` 的正 handle 路线与原 parser 都调用 `146e031f0`，用第一个 out QWORD 作读／写 base，已把 native30h/38h 对到同 wrapper 的管理90h/84h。它证明槽用途，未观察两个调用返回同一实际 base、owner 的自然状态／清理或其存续期。

本链可以证明同 P 被连续用于两表示读取、同 D 在 caller 中保留为 RBX 并写入集合；**保留 managed wrapper 引用不等于已经证明其 native base 的寿命**。首个准确缺体是 ID→byref P 的 `14d7ac240`，需要确认其 AL/out、同 k、bool/cache及正常清理边；另一个直接必要体是同 Avatar group→pair 的 selector，需把已存556B listing升级为准确 raw/分支合同。两者不要求先自然观察，也不需要扩 dictionary/generic 树。

已定向核固定 metadata／全签名：663528（80063，static，U32/byref63831/bool）和138266（18163，instance，U32/bool）各唯一；返回声明未恢复，保持调用者实际 AL/RAX 消费约束。检查既存 astra-main/astra-avatar 的89份 static/native/summary容器，没有这两个 exact function 原件。`151847f70` 老 static 记录有真实 exception extent及5470B原 code SHA `abacbc17e787061613dfe80879a2b01454f6115b36062c908edb2374f637678b`，但只有无 hex 的 instruction text，不能从 hash 逆造 raw。

## 已存索引的定向使用与排除

73候选原报告53025B SHA `5d0f81592c2c9bfce8bf91e0732a9fa0fae73d726ca95647d3b7741df7adf1e4` 只作现成数据过滤。按 typed owner18163 得四项：m138129、138165、138264、138270；只选138165的完整已有来源。该索引的 `151849387` global-load 在其另一支路，不是上面 tag14/35 的证据；不能把+212b8形状当本链数据流。

已验944B/795B的 regular/energy／Avatar+50 消费与上述+1c8/+40列表分开；不再取其 UI/helper树。2034B Promote预览冻结，4427B交集旧局部上下文也没有比此链更直接的当前采用输入，不为排队请求它。现成仓外7.2候选及已完成 README/branch 检索边界继续复用（含既有 bounded-partial），外部JSON和Java只作候选／实现事实，不承担客户端采用证明。本包没有网络、clone或新全局检索方向。

## 可立即交 Terra 的双完整 source 包

`source-request.private.json` 合包两个准确直接入口：

| entry / ordinary method | 必要问题 | 边界／预算 |
| --- | --- | --- |
| `14d7ac240` / 80063.m663528 | 原 ID→out P、AL、bool/cache/清理与对象来源；准确 successor若已存可供后续语义复用 | 真实 extent 未知；current unique pdata先诊断；cap4096B/1024条。 |
| `151846d90` / 18163.m138266 | 同 Avatar group的基础／附加值、原错误／缺键、sum、bool到pair和返回D | 旧完整 listing提示556B；current pdata必须独立给出并核对该范围，不能用listing/邻入口作边界；cap1024B/256条。 |

caller `151847f70` /18163.m138165 的两段**新复核**17B是 `[1518488be,1518488cf)` 与 `[151848a8b,151848a9c)`。旧来源只有 listing，尚无这34B的原 hex。必须分别验证四条PC/边界/寄存器值流和 E8 signed displacement。第二段只设置 R8B=1，不能写成 R8D全值1。

一次 fresh 普通 pointer cell `1422f9950` 取 P，读三槽 `P+663528*8`、`P+138266*8`、`P+138165*8`；旧slot `142db5410/1429b35a0/1429b3278` 仅提示。每项 current exact/containing/overlap pdata、caller归属、file-backed映射、byte cap和**两体累计预算必须全部在任一body前通过**。保存全体 bodyRead=0 的 pre-body；一项不符则两体均不读取，不自动用 prefix/邻窗 fallback。

目标 entry 去重后，body合计最多4652B（4096+确认556），caller34B、ordinary cell+三槽32B、两target pdata24B及caller pdata12B，bounded source evidence上限 **4754B**。全 stock hash输入550661784B、exception index14344080B和header/section最多8192B分列；pdata记录从已读索引保存，实际I/O与证据字节不重复伪计。指令 cap 只能在 admitted raw读后核，失败必须保留真实读取量。单次同时核两入口，不顺读任何 helper/global。

后继可写新 `tools/migration72/proud_current_adoption_source_windows.py`、对应测试、新 `docs/CLIENT_72_D02_PROUD_CURRENT_ADOPTION_SOURCE_WINDOWS_20261111C.md`，独立输出仅本忽略根 `source-windows/`。验收覆盖 caller原字节／R8B区别、真实AOT/pdata、去重和累计门前零body、连续raw重组、短读／指令失败真实记账；双fresh和独立原件oracle，主线第三验收。未实现 collector 或测试。

## 对旧号121角色后继的减量判断

无需重新推算 phase6、605槽或旧号快照。**静态参数化选择已可定义；当前还不能无条件给出121角色的真实必要 Proud ID名单。** 源包完成后，只需沿这个已定位方法建立有限 per-Avatar manifest：显式 supplied ID列表及其 target tag来源、每 k 的实际／条件 row group/base level、同 Avatar 基础／附加 map结果、准确 pair→k2/row、表示和base寿命。未知 map状态不当缺键，未知row不补22101；duplicate分支不造host比较。

首个小case可限定清空后的集合、单 supplied ID、已有足够backing capacity，检验选择后D的条件append；它绕开未知重复比较，不证明自然 hit。实际121投影只在这些来源明确后排期，不复制保存proud作为目标当前集合，不采用Java `<=phase` 或ID公式，也不造资源让loader通过。现有raw字段对Java sender的别名、原行/encoded、自然pair命中和实际使用仍分别验收，不能以旧Java测试或文件语法正常结束替代。

## 本轮验证、拒绝边界与交付

本轮核25项技术源pin、四个ordinary声明/完整参数、相关typed字段、34个精确listing锚点、原351B pair用途、现成73的四项typed子集及89容器库存。没有执行其中历史CPU/models；库存初次遇list形JSON的AttributeError在报告前停止，修正只读形状处理后完成，记录于库存报告。最新Project/恢复索引按读时版本保存，源码门只冻结稳定技术输入，不将并行索引更新当技术源。

移除A/B拒绝动作和产物后，本静态链及两体取源仍独立；不构造Default、不分析被拒第二返回/同epoch所有权。准确A allocator/second-return/Default或B CALL/data边只留边停该依赖；新拒绝保留原提示/site即停，不换方式。当前恢复索引记A完整原拒绝尚缺，不能伪造；B原句 `This content was flagged for possible cybersecurity risk.` 保留，本轮无新拒绝。

`plan-review.private.json` 33462B SHA `1529e06f999a50537e86da33496b8b6426cee9d3099819f3faf174c402c9ea63`；`source-request.private.json` 14264B SHA `f80391509e6cc161cd1269d618b0bb2369d51be7fceebf2334cef7d07f7f630d`。actualEncoded/getter/原row/current采用、map热hit仍未知，O0冷停不变；resourceReady、D02-R3、D-02未晋级。不改旧topic/工具/运行资源、Project/Harness/HANDOFF或Git，共享索引交主线维护。
