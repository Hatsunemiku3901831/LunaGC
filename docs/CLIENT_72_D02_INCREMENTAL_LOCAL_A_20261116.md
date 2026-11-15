# D-02 A组既存输入增量关联研究

任务 `D02-INCR-A-LOCAL-RESEARCH-20261116`；接手 HEAD `2247e8b1a04601588a6db62edb1ccdfb2300c1fa`。承接[增量计划](CLIENT_72_D02_INCREMENTAL_REUSE_PLAN_20261116.md)，只读已固定元数据、签名和既存完整原窗；本文件与忽略目录 `local/migration72/d02-incremental-local-a-20261116/` 为本任务唯一写入范围。

**本项有界研究已完成。** 新闭合同包括同组件Skill/属性类型桥、enum77/MAX与78/START的输入值写入及旧值通知关系、Relic真实typed消费者的分项判断；没有形成cap资源字段或rank/set角色实现规格。后续独立来源仍开放，不将本轮有限材料解释为全网/全机或整个A2阻塞。新增静态采源、Ghidra及失败恢复均由主线逐项授权，原失败保留。

## 最小执行合同

1. A1：按实际类型引用关联88602 Skill资源与24887属性对象；在既存完整消费窗内检验同字段值流是否进入enum77/MAX_SPECIAL，并与78/START分离。区别于已做名称查询，不读取候选200/100来选择字段。
2. A2：按46431/74316实际字段及参数类型引用关联等级表/套装表查找；复用27候选typed排除，不重读18混淆body，不以名字/值大小代职责。
3. 先核输入hash，再记录确切筛选范围、候选关系、原窗锚点、被排除假设及未知；metadata声明不证明body返回或运行值。任何一条职责桥成熟立即交最小样本规格，另一项独立继续。
4. 固定metadata：`current-structures.json` SHA-256 `d5d7f671a622439b2f9a3ed0018ce551aa5c573cd9ca68bed3b006712b8a98aa`；`client72-signatures.json` SHA-256 `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe`。既存窗及其manifest另在私有输入账固定完整hash，不重新采集目标源。
5. A/B、preview及准确依赖、E8预算停点保持；不执行native/parser/getter/ctor/TLS/cache，不读取新的PE/资源字节，不运行JVM/DB/client，不修改共享状态/代码/资源/Git，不构建或提交。若需新静态源，先交唯一方法/slot、真实签名、完整可追溯边界、选择依据、预算和移除拒绝动作后独立性，等待主线判定。

已读 reverse-engineer-anything 技能；REA现有Evidence摘要返回0条，无法直接引用既存会话记录。初始既存关联阶段仅在已验导出物上做确定性关联，未开启二进制或运行目标；后续主线批准的Ghidra静态open及实际范围见后文，保留观察、推断和未知的区分。

## 执行结果

### A1：同组件类型桥成立，尚无MAX字段值流

实际关联89,748条类型声明的字段（包含明确的generic参数）与326,354条方法签名；参数逐一经`typeReferences`解析，未解析数为0，返回类型仍未恢复。88602有7条字段引用及14条参数引用，24887有4条字段引用及16条参数引用；直接owner交集为空，**不能因此排除间接连接**。继续核实际祖先后得到：

`53763 → parent41463 → parent59769 → field296496/+180:24887`；同一53763的`+210/+220/+270`容器持有53767，后者`field267629/+10:88602`。因此能量资源与属性确可在同组件中关联，不再把“没有同owner直接字段”当缺失证明；自然实例及字段赋值来源仍另证。

将此桥与既存457B普通、454B特殊和525B注册窗关联（合计1,436B，不重采）：特殊窗`15345d2ce`保存同receiver，`15345d34f`读其`+180`属性，`15345d385`读Skill `+74`；fallback在`15345d423/432`再次从同属性对象读`+458`。既验完整dispatcher的enum77到`150854841/+6d0`、enum78到`15085486c/+458`。这排除了把特殊fallback或START直接叫MAX的解释；三窗14处相关memory use均为读取，没有MAX/START目标字段写入，也没有88602某字段→MAX的赋值桥。它不排除其他函数存在该桥。

按实际参数类型再缩小同holder59769的5条enum32419方法：

| 方法 | 实际参数 / 本次判断 |
| --- | --- |
| `496192/GBLLFCBMHEG@150854030` | instance、flags81，enum32419+float；仅为值写入候选 |
| `496205/PKOMBNDIIGL@150857150` | instance、virtual slot70、flags1c6，enum32419+float+enum38256；仅为值写入候选 |
| `496233@15085d1b0`、`496297@150864090` | enum32419+Action&lt;30710,30710&gt;，不是同类float输入 |
| `496295@150854240` | 已验单enum getter，直接复用 |

对两个准确候选地址在既存引用/asm JSON及analysis TXT共1,303件、21,789,251B作精确token查询。唯一命中是`avatar-fight-props-enum-references.json`中的方法声明，不是CALL；其中`parameters:[0]`表示enum参数位置，不是运行值0或77。未发现已存完整caller及enum77实参值流；间接/virtual调用仍unknown。属性字段准确符号及字段ID亦无TXT命中，不将地址中的数字子串当字段引用。

这些声明本身能区分对象、参数种类与MAX/START属性，不能选择资源cap字段或证明MAX只来自协议；当时不按方法形状盲取body。随后新raw候选选中496205，实际语义与当前最小缺边见后文。候选200/100、START=0或Float同形始终不填职责缺口。

### A2：真实typed消费者已分项判别，角色桥仍缺

46431仅有`74316.field368811/+20`这一条声明字段引用，恢复的显参引用为0；74316有3条字段引用和6条参数引用，来自4个方法owner。实际模型`52700.field261342/+80:74316`和owner60446的资源字典均为真实typed桥；它们不是旧27个无资源边的UI名称候选。字典类型仅说明资源索引，不说明rank/set角色。

现成`disasm-1502eb590.txt`的局部普通路径读取`74316.+20→46431.+88`并交UInt32 getter，能够缩小一个实际标量读取候选。但该文件是100条指令的列表，已经跨至另一方法`1502eb724`，不能升级为一个完整方法来源。旧caller `18163@151851c00`入口`151851c21→1405336c0`有准确冻结边；依赖其返回dictionary的分组语义停止，不借此将`+88`命名setId。无依赖的字段读取与其他消费者仍可独立推进。

新增精确候选为`21946.m168869/ShowCodexReliquary@14c3fa5a0`：instance、flags86、2显参，`147248/typeRef339772→class74316`与`147249/typeRef124→uint32`，return未恢复。这条目标资源参数边区别于旧27候选，足以选择一个有判别力的UI consumer来源；名称本身不证明body包含rank或set逻辑。

主线已批准并由独立Terra执行该唯一slot边界门。首次包装`ast`未绑定在resolver/stock/PE读取前停止并保留；主线另行授权修正后run2固定fresh AOT、完整pdata唯一exact/containing/overlap，得到`[14c3fa5a0,14c3fb088)`、2,792B。该步body/decode/CPU均0。主线随后单独批准完整静态source，实际632条严格连续解码，body SHA-256 `3fd04ec7192e2347533827703f673cf834ef5b13963b7b452c791de9953df284`；直接冻结CALL为`14c3fa784→1405336c0`，整窗semantic=false。

本任务核新原件hash与重拼后，仅对不消费该返回的原参数关系分析：首A前108条中，原74316参数由RDX保存RBX，两个普通非空backing路径读`+20→46431.+50`；metadata将后者明确为`field229072/String`，不是rank/set的encodedUInt32候选。`14c3fa6a6`把该String传`159a8d050`，`6ab`即用该callee返回覆盖RBX，未跟其语义。

进一步核完整632条中的38处RBX用途：原RDX在`5d1`重定义，原backing-row只临时在RAX，未观察到原资源指针spill/store或其他保存副本；普通路径`6ab`后RBX分别来自callee返回、`this+a70`及数组项，不能按寄存器名回填为原资源。另有独立IFix bypass到`e7b`，在`e9c`将原资源转R8、`e9f`将原uint32转R9D，`eb0`尾转`14e1c9920`；它只给出原参数转交，没有rank/set字段读，不跟callee。故没有把首A后的全部代码泛封；本窗已观察到的独立原参数关系仍不足命名rank/set。完整复核原件`codex-original-argument-v2.private.json` SHA-256 `2af98fcf1ba5e1595a783402607a0c6d60ec0d4ca9ad2fe5bf5febe8dc3ca068`。

主线另派执行者完成真实typed转交者及60446唯一public入口的有限语义，最终3字段/6参数覆盖如下；旧27 UI及18混淆body不重做。

| 准确边 | 已判范围与未扩大理由 |
| --- | --- |
| `52700.field261342/+80→74316` | 上述独立字段读复用；旧分组caller的准确A返回依赖保持。没有由此选rank/set或重新取同族getter |
| `60446.field299590→13732<U32,74316>` | 新501523源确认一条flag分支使用runtime root+35468；key始终原输入U32。不能据容器名命名等级/套装表 |
| `60446.field299591→Dictionary<U32,74316>` | 另一分支使用root+35470，按同原U32查找并输出整体资源指针；没有局部raw字段→另一个表key |
| `21946.m168869(74316,U32)` | 上述完整2792B原参数String读取及IFix转交复用；未出现独立rank/set标量桥 |
| `49417.m411125@14cc953d0(74316,U32)` | 新146B/35条；沿真实继承字段`+38→60698`、`+10→21946`，普通路径原样tail ShowCodex；逻辑内联503451而非CALL它，无resource/raw字段读 |
| `60698.m503451@14d27a6a0(74316,U32)` | 新92B/23条；普通路径仅取`this+10`后原样tail ShowCodex；另一flag路径只转this/resource/U32到动态wrapper，不跟callee或推自然flag |
| `60446.m501523@147c38270(U32,74316&,bool)` | 参数实际byRef且attrs2，不冒称传入资源对象。新632B/134条确认两缓存/整体输出与原U32 key；局部未将raw成员用于不同表。RET只验AL状态，不以EAX高位或未知声明补bool类型 |
| `60446.m501529@147c384f0(U32,74316&)` | 声明/真实类型已核；未取body，亦非501523直接CALL。尚无读取raw角色的选中依据，不能凭byRef签名预取 |
| `60446.m501535@147c38a60(U32,74316&)` | 新501523在`147c3832d`以同原key/out槽调用此后继；仅为当前同key整体查找关系。未取body，不把未读说成永远无raw，也不以通用lookup扩树代替角色桥 |

两个UI转交consumer合计238B/58条的paired proof SHA-256 `f2587593c1c74e6be4150701e75a5fca014d8b22cbd4b7e1388938862d6e628d`；501523语义proof `e351b41270157dedcd82a9855d2eb338a148a829636a8d8779a65ce9ec09aee9`。剩余typed最终表`FINAL_REMAINING_TYPED.zh.md` SHA-256 `247c5502adaee2dd5ab6ba4e72b7c766f3d5ade061a9f8ee8a0bd6f001e1c425`，均位于`local/migration72/d02-incremental-relic-remaining-typed-20261116/`。本轮观察仍与rank/set候选标签交换相容；缺的是准确raw成员进入等级或套装不同用途的消费边，不是更多同型容器或整体资源查找。

### A1新增静态索引尝试与两目标筛选

REA实查起点为`open=false`、0条Evidence、无provider binding；Ghidra可用，未有可复用目标分析。主线批准一次固定PE只读Ghidra建库，当前portable resolver及完整450,927,000B PE hash通过，显式`provider_id=ghidra`绑定PE/x86_64。实际Ghidra 12.1.4、默认分析profile digest为`a8c04e13a343d9596302c20b750cea26ee2dcd89bec0936779aca5ce792e794f`；真实输入和建库IO不称仅两个地址。

首次`xrefs(150854030)`在已核默认330,000ms启动期限返回`provider_timeout`；尚未返回任何incoming结果，`150857150`未查询，覆盖为0/2。原诊断未确认OOM，不能由超时或EH分析局部错误推定无caller。后续只读盘点发现报告的owned PID已不存在、临时project/log不可读；这是实际检查时点，不追认错误返回时进程已停。本任务未杀进程、未删除目录、未重导入或改超时/全局配置。

已通过REA保存Evidence bundle及正常`close_binary(snapshot_path)`，目标绑定snapshot为2,501B、0 primitive/0 workflow/0 Evidence；它不是已建立的Ghidra引用库，当前不能提供可复用分析索引。完整原错误、profile、状态与关闭回执留`ghidra/`忽略目录。

主线比较恢复成本后，另批准一次仅针对`150854030/150857150`的有界direct CALL筛选，独立于Proud共享helper旧E8路线。固定请求`exact-direct-callers-request.private.json` SHA-256 `a78898fe123eef060bfa02821d052ce0cbbf05376c824147899325a23c5b0262`，约束raw≤32、完整caller≤8、单窗≤4,096B/合计≤32,768B；先unique pdata及alias，再连续解码确认CALL边界。

Terra实际完整扫描3个可执行raw区共376,612,864B，仅有`150857386→150854030`的5B原始候选`e8a5ccffff`；另一个target没有direct raw E8候选。caller门因包含目标entry且原合同`newTargetBodies=0`停止，body/decode均0；失败未保存触发的具体pdata行，不能宣称完整caller边界或真CALL。原失败`failure.private.json` SHA-256 `b610d0f11ba1be50248508e69762afad57d4c018761db37aaa8cf2655853d094`、manifest `b3bb7b39bdeb4390cbc02bca8d87be3ed14f3c570f8a9392fb112f828f69b32e`完整保留。该门限停止不是安全拒绝；虚调用、0命中仍未知。

新候选使方法496205不再是盲选：主线单独批准一次`496205@150857150`完整source，以区分“原enum/float转交”与“内部常量77/别源”。追加请求`caller496205-source-request.private.json` SHA-256 `33c107310d2a7b9a4d0ec2bd2aab702b1481331e0e5a770ebf8ab057c3b5bf3d`，只重核fresh AOT及唯一pdata，begin必须为150857150；完整一次≤4,096B、strict≤1,024条，必须确认候选为真CALL后再判值流。不重扫code，不取496192或其他callee，不自动扩generic树或重启Ghidra。

实际新源边界为`[150857150,150857e50)`、3,328B，完整body一次读取，SHA-256 `3c6c3e37aed64e03b6ff6790a0f0ab633fbc8fa21c5278dad16786d69b90c0be`。原线性strict失败为`incomplete decode`，没有原partial；本任务获准仅用已存body/实际driver离线归因。实际参数为1,024，未误用helper默认256；同Capstone5.0.7/skipdata=false在669条、2,994B后止于`150857d02`，不是指令预算耗尽。原失败保留，未改引擎、跳data或重新取PE。

主线随后验收完整code/data布局：

- 代码`[150857150,150857d00)`为2,992B/668条。
- `[150857d00,150857e38)`为78个signed dword、312B；实际`u32(R14D−4)≤77`、RIP LEA、MOVSXD×4、加表基址和JMP逐项固定。
- `[150857e38,150857e50)`为6个signed dword、24B；实际`u32(R14D−2000)≤5`，同样的有符号分派。
- 84个表target均在真实代码指令起点，显式内部控制目标无data地址；代码尾为INT3和3B NOP。2,992+312+24=3,328、0未分类字节，完整原bytes保持。因此不是把invalid尾裁掉；原“全窗线性解码通过”仍未成立。

这份独立布局使`150857386/e8a5ccffff→150854030`成为可验收真CALL。主线再验收的参数流为：

| 关系 | 原指令与限定结论 |
| --- | --- |
| 入口值 | `166: XMM6=XMM2`保存incoming float，`169: R14D=EDX`保存enum，`16c: RDI=RCX`保存receiver |
| 写前旧值 | `197`用同receiver/enum调用已验getter150854240，`19c: XMM7=XMM0`保存旧属性值 |
| enum77/MAX | 表项77→`150857abe`；incoming XMM6交既验30710 encoder，`ae4`将16B结果写同24887 `field122369/+6d0` |
| enum78/START | 表项78→`150857af0`；同输入值编码，`b16`写`field122328/+458` |
| 共同通知 | `37d/380/383`设置同RDI、原R14D和XMM7，`386`调用496192。enum原样转交；float为写前getter旧值，**不是incoming float透传**，也不是新增88602字段来源 |

全代码直接冻结hit为0，仅证明直接索引范围，不证明传递A-free或自然状态。本轮闭合generic MAX/START setter和旧值通知关系；没有Skill资源字段→MAX输入float桥。因通知接收的是旧属性值，不为寻找新cap来源再取496192。布局proof SHA-256 `09e010e0e7cc208abbcb69f5af3b9b399520d47b5e5cf2e51806505b4c02af10`；参数流proof `2281e07cad0303416fd0c2c4ef814d1e96817494936dedc240d1b66e1da4603f`。

## 最小未闭来源与其他主线

| 范围 | 当前准确缺边 / 允许解除输入 |
| --- | --- |
| A1 cap | 实际typed59769 slot70 producer/caller，以enum77提供float并独立连到同88602准确字段/getter；或具目标revision/字段身份的schema。exact E8未覆盖virtual/indirect来源；不泛搜slot70、不从旧值通知或200/100候选补cap。没有成熟新资源片段/必要cohort/Java实现规格 |
| A2 rank/set | 同target某raw成员→等级/套装区别消费，或同源真实输入+独立非零具名role oracle排除交换。仅新getter/lookup同型候选不够；有真实边再固定唯一有判别力source，不宣称全部必须外部输入或整族依A |
| R1完整Avatar/g2 | 新属性setter/typed桥未解除第二返回/完整构造准确A依赖，未提供同源Store/SavedVitals及完整资源；原完整Avatar/g2缺口保持 |
| R2自然SDK/认证 | 本轮没有Init/env→route自然采用、认证/信任/会话或完整校验新输入；B组合loader准确冻结及其他独立自然缺口保持 |
| Scene M1–M4 | 未提供9251实际receiver、同次返回R、首消费前+18或release/reacquire/reset证据；原允许来源与World准确A边保持 |
| Proud | 新30710属性编码/通知不连接目标Proud字段→共享Pair/Float consumer，也没有实际M/row/alias/key/current/同epoch或自然非空cache；旧cold和准确冻结边保持 |

因此这些新输入没有独立解除R1/R2/Scene/Proud的必要边，不另造现场、generic helper、旧源重跑或模型升级任务。R4–R6及D-02/F-05六项0/6不变；后续有上述准确允许输入才从对应最小项继续，不降低ready/required。

### 证据与范围

忽略目录保留固定输入账、type-reference v2、准确祖先/参数、完整原窗关联及精确caller查询。关键输出：`type-reference-association-v2.private.json` SHA-256 `71f5d4741e3a847bf352e5dc6a5b0c6940ae6c39d594ed24718fa9c90adb7313`；`window-association.private.json` `551592bd7ebcc0b2debebb970d208c589887782294ae1a5b7c945c26d5184352`；`exact-property-callers.private.json` `31936080170ebb48190be0648e7cfeb7661a2e1b2467b941e8919aec785a9d6b`。源pin账为`81342349eca6513fd250ad8ff9e48a2e9bcf9c95a4cf281a06c78da7bb80e7d0`。

本轮22件主要proof/原失败/关闭结果的逐件路径、实际bytes与SHA收在`final-proof-index.private.json`，6,353B，SHA-256 `13cebb86331584426c2a5544ad9261d53ee760c8ff61f1378437940081a59546`。其中Ghidra首超时原件SHA `7d0a16211092a31a73eeece64c340b2cefc9fa0e3496a9555fc28f5faa2a1999`，空snapshot `e60e2a36c7d3e0dab070289591f17e146d925c68987b05337c34400c8408b4ab`；最终REA `open=false/cleanup_pending=false`。3328B原线性失败SHA `f76fa970cd38dec55a6325046fdba02bc3c2b732b285861719b0652aabe9ef1f`，离线诊断SHA `f76d04f55abc5ba0eaab80832b0cb3e1ce9aee7bef92d9a40a5db464dcdd192a`；后继布局/参数结论不追认首轮strict成功。

首次参数关联v1未解析`typeIndex`，其零参数计数无效，已保留并由v2纠正；单行大JSON预览被截断，不当完整读取。普通来源缺口不标“外部全部阻塞”；本轮新类型桥、准确consumer及排除结论不批准cap/rank/set字段，不提升资源ready或D-02父项。新增PE静态来源及Ghidra分析进程按上述主线授权分列，没有执行目标方法、游戏JVM、DB或客户端。未改运行代码/资源/共享状态/Git，未提交、测试、构建或部署。
