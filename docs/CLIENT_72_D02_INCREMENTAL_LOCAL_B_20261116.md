# D-02 Material / Weapon 本地增量关联

任务 `D02-INCR-B-LOCAL-RESEARCH-20261116`。本报告由 Sol max 负责；共享索引、Git、构建及运行归主线唯一集成者。本轮以已验[计划](CLIENT_72_D02_INCREMENTAL_REUSE_PLAN_20261116.md) SHA-256 `50abc5bdd26c6c8c9c80ec00e60938ba0240834e02ac41e13f805dfa510cce69`及[B组公开报告](CLIENT_72_D02_INCREMENTAL_REUSE_B_20261116.md) SHA-256 `9497b02788d53fded16786ae89e8d0e5cc1ca28f9da6c1f5d19a88a3d8a861d2`为输入；公开3.5解析链和3.4→3.5 Weapon差异仅作版本参考，不再扩搜索。

## 执行前的最小自主研究合同

| 子项 | 准确问题与可区分假设 | 最小动作与验收 |
| --- | --- | --- |
| B1 Material | 239B loader和238B factory均清同返回对象的`+10/+1c`，但有效backend基值`+18`是否由一个现有、独立于A返回/构造的写入到达首次refill？假设一是有可核同reader的新store/目标文件语法，假设二是到达定义仍在未知virtual/构造或未验外callee。 | 固定loader、factory、refill及其已有完整wrapper原件；按reader身份列`+18/+20/+1c`所有本地定义、普通/旁路控制流和首次read第五实参。只在独立新边闭合后交一个实际header/首前缀合同；没有该边不读条件P=0的4B，不猜filebegin。 |
| B2 Weapon | whole记录的87097 wrapper入口/返回，是否能通过现成完整父源和observer建立同reader/同row的20600、88968真实F入口？假设一是现存记录已提供准确嵌套入口及cursor等式，假设二是observer只在wrapper采样且内部body缺失。 | 固定whole、准确observer源码、既存完整2018B父源及必要metadata；逐边核实采样点、寄存器/对象身份和已存父CALL/字节语法。不按11101值扫定位，不把`[4,400)`或String尾推进当父入口；有成熟独立桥立即交最小必要行样本规格。 |

私有输出仅写 `local/migration72/d02-incremental-local-b-20261116/`；本文件执行前不存在。只读原件分析，允许为记录原件身份和关联结果编写私有离线脚本，不执行旧collector、fixture、parser、getter、ctor、TLS或目标CPU，不读取新PE/callee/global/payload字节，不改运行代码、配置、资源和他人差异。

新准确静态源须先交主线：唯一metadata slot与真实签名、选中依据、固定PE/hash、可追溯完整边界、仅解决的一条边、字节预算，以及删除被拒动作和产物后仍独立的说明。普通缺源只形成技术首缺；不自动写成外部阻塞或请求现场。新hash/边界冲突或安全拒绝立即停准确动作及依赖，不换工具/模型重试。A/B、preview、Material两A CALL、shared String准确A返回和Weapon旧ctor/parser路径继续冻结。

## 实际关联结果

**B1、B2的本轮有界关联均已完成；两个实际入口合同仍未成熟。** B1新增了后台创建参数与首次读取偏移的区分，并定位一个不能支配首refill的清理写入；B2新增了旧whole中wrapper→raw→父row的同reader/同receiver关联。没有读取新header或新行字段。已验Material 1,560,105B载荷、97 literal、Weapon 128,936B raw、11101六键及239必要cohort仅只读复用。

本轮固定既存原件并逐条原hex重拼8个body，共4182B/1070条；全部连续并与既验raw逐byte相同，没有重新解码或重新采集客户端。另核既存三个完整backend wrapper raw共1100B的SHA。具体输入、完整hash与锚点在文末私有账；不把重拼成功当字段、自然reader或实机验收。

### B1：首次refill仍消费一个没有已证初值的B

| 已核完整原件 | 此次关联得到的事实 |
| --- | --- |
| Material路径loader `60257.m500132`，239B `[14d6e1570,14d6e165f)` | `15c3` virtual返回R，`15d2`保存同R；`15d5/15dd`清R的`+10/+1c`，`1613`返回同R。非零backend结果才在`160c/160f`写`+28/+20`；没有`+18`直接store，零backend也不清旧`+20/+28`。 |
| U64工厂 `60257.m500134`，238B `[14d6f9210,14d6f92fe)` | 同样只直接清`+10/+1c`，但其backend零分支另有`146eb3550` fallback array；**Material准确500132没有这个fallback**。两者请求相同pool槽，不能据此等同其实际receiver或用500134的fallback补Material输入。 |
| Material表头 `17822.m135074`，569B | 原reader留R15；`7a04`在`+20`非零时把同R交refill，请求4B，然后才在`7a16`读当前cursor。首A前的原条件和独立倒计数结论复用，不解释两处A返回。 |
| refill `42005.m350685`，486B | 初次array为空的`48b9..490c`只在`48f3`写array，不清`+18/+1c`；第五读取实参为 `sign_extend_64(wrap32(B+P))`。已有array补读时`4842/4911`才写`B'=wrap32(B+P)`并清P；这不是首读前独立初始化B。 |
| LoadedBundle 220B、native read 208B、read core首region 204B | 创建后台的a=0与首次read的O是不同来源。`14112320f`把原第五QWORD传至R8，`141138c8b`留在R13，`d18`参与无符号界限比较，`d27`参与DIV；O没有在这条已存局部链中改成0。204B只是首pdata region，不代表完整逻辑read或已读满。 |

这组来源能区分两件事：loader确实清了P，但未给B一个局部定义；backend创建参数为0，也不能消去read所消费的B。在没有新定义的情况下，不同的B都与上述局部store及参数记录相容，却使首次read的O不同。因此“清P→读payload offset0”不是现有证据的结论，不能读取那4B来反向挑一个有利B。

新发现的写入线索位于既存accessor文本 `analysis/disasm-14a1d7890.txt`：`14a1d790c XORPS`后，`790f MOVUPS [RSI+10],XMM0`的16B范围覆盖reader`+18/+1c`，再清`+20/+28`并经pool`+f8`归还。准确声明是 `17822.m135053/CDMPEFBPCLP`、static零显参。该carrier没有原hex，未升级为新raw验收；**即使授予该文本的清理语义，它也在`7907`表parser调用之后，不能支配本次首refill。** 把它用于下一次取得，还须另证归还→再次取得的同reader身份和顺序。为此补一个accessor完整窗也不会解除当前入口，所以没有申请无效新窗。

继续以42005的全部12个准确方法地址，在9份已知reader carrier内作定点关联：只得到12条声明及两个旧refill执行计数，没有新的完整writer来源。范围限于私有账逐件列出的scene-runtime、Scene观察、typed/control-tail、resource-index、inventory和accessor来源，不宣称全机没有writer。混淆方法名、零参或参数形状没有选中一个能支配首refill的准确方法，故不从12个声明试取body。

B1的普通缺输入是同reader在首refill之前的有效B定义，以及这个backend与固定Material `m_bytes`物理字节起点的对应；独立目标文件语法也是允许的替代来源。已知pool getter/Recreate→A返回路线仍按原准确依赖冻结，不能借它补同reader返回身份；这不禁止其他独立Material来源。

### B2：同reader/同父row身份有新关联，cursor等式仍无记录

已固定whole 15,112,922B及准确observer 44,027B；后者SHA与whole的`sourceSHA256`一致。observer的`row_entry`取87097的`CNLIBMNOALB`，在该入口且RDX等于reader时，以保存的返回地址登记wrapper RCX和`reader+1c`，到该返回地址再取cursor；最后按字典内wrapper pointer连接span。这只记录wrapper入口/返回。

本轮新关联whole的18,966条`callOrder`：**288组都是87097→20600→88968连续的入口事件，三者RDX相同，后两者RCX相同。** 11101已验wrapper对应第一组三事件索引17/18/19；wrapper receiver与raw/base receiver不同，reader相同。这里仅复用旧受控模型run的身份记录，没有使用A产物运行或生成新字段，也不是独立自然row oracle。

独立metadata又确认：87097继承34113；87097.field434158/`+20`声明20600；34113.field167172/`+10`声明88968；20600继承88968。20600/88968 parser分别为m158676/m735168、同virtual slot4，wrapper m720803为slot12且另有可选`INativeAllocator11476`参数。这说明wrapper与raw/base是不同角色，不能按继承或同reader省掉中间cursor边。

实际完整2018B父源把reader RDX留R14、row RCX留R12；`150b6a9f1`可能refill，之后`aa07`才取flag cursor、`aa16`读LE32 F。但`callOrder`事件只有`entry/rcx/rdx`，没有cursor、caller PC或return stack；连续的函数入口事件之间仍可存在普通cursor指令。因此下列两种关系都未被这些记录区分：wrapper→raw有不同的cursor推进，或raw→父F前有不同的推进/补读。完整父源只解释从其自身入口以后怎样读，不能恢复前两段缺失body。

whole的`nativeSources`对wrapper/raw只存`end/sha256/references`，不存完整body；旧hash/RIP引用不能补这两段语法。`[4,400)`仍只是11101的wrapper跨度，String尾ADD仍不是入口净推进。当前没有新的准确F位置或可实施的新必要字段合同；没有重读11101六键、扩11201、按候选值扫定位、运行row或生成walker。已知旧Weapon ctor/parser路径及其准确A返回依赖继续停止，潜在独立host语法保持开放。

## 最小新证据、获取前置与下一动作

| 子项 | 已有输入无法区分的准确关系 | 最小新证据及如何进入后继 |
| --- | --- | --- |
| B1 | P清零与后台a=0均不能确定首read的B/O及其与Material物理字节的对应。post-parser清理不支配首次读取；同pool请求不证明同对象复用。 | 一份已允许、固定同target/hash的来源或同帧记录，明确同R的首refill前B、T所指资源及物理起点；或者目标revision的独立完整文件framing。新source必须由真实写入/调用边选中唯一方法，再交slot、签名、完整边界和预算；现有12个混淆声明不足以发包。若未来有允许的自然记录，需在同资源同reader的表入口与首refill前各保留B/P/T及来源身份，不能仅拍SDK页或任意heap；本轮没有适用现场、未准备/执行collector。 |
| B2 | 已有身份事件不约束 `P_wrapper→P_raw→P_F` 的差值，亦不提供F实际物理范围。 | 一份新的允许独立schema/完整来源，逐边给同reader的cursor变化及实际字节边界；或一份固定同target、同row的真实嵌套入口cursor记录，明确wrapper/raw/F采样点和补读关系。旧wrapper/raw body重取仍属已停路径，不作为换名新请求。出现新独立producer或已有允许记录后，先只核其唯一源和三个入口身份，再交一个必要新row的最小pre-String字段样本；不先实现全walker。 |

当前没有能够用已有输入再独立完成、并区分上述剩余关系的准确方法动作。这里是本轮有限输入的技术缺边，**不宣称公开或本地来源穷尽，也不把两个整族都写成外部/安全阻塞**。没有新的合格静态源请求：清理窗不解决首读，混淆方法缺选择边，已停Weapon body不重新取得。后继输入到达即按本合同选一个最小实际样本，不等另一组；既有准确冻结边不自动解除或派Daybreak。

## 原件账、实际验证与交回

私有根 `local/migration72/d02-incremental-local-b-20261116/`：

| 产物 | 字节 / SHA-256 |
| --- | --- |
| `run2/input-manifest.private.json` | 3821 / `31f862fa73edb8978da55ebd5509716c42ceb90b59b473c1d5f7dc4fda2b0ea5` |
| `run2/saved-source-reconstruction.private.json` | 1462 / `8f02e3458f2cb3b9174e5c7fcd6b514a46b1731e5dabf2579bae0eca8bdef85d` |
| `run2/weapon-entry-identity-correlation.private.json` | 3084 / `e07d9b8fd66290d189519b91eaeae057351bcb463d77f5764e64a4cf248e5ae7` |
| `reader-existing-source-lookup-v2.private.json` | 15829 / `5764b0378eedca134ba3b08e965ac4ff7fcf69762ce13d1479c48209c1fe425a` |
| `backend-offset-chain.private.json` | 7841 / `2367db461299b8f180a8f1b378b97178bfde5cee77b5a890aeea9da973dc255d` |
| `findings.private.json` | 6839 / `a720285cd0e96ea40641c21cdbde0e8f341de2a658f25ffa0fb5b774dd85e313` |

实际运行三个私有one-off脚本：`inspect_existing.private.py`修正RUN2 exit0；`reader_existing_sources.private.py`原lookup及补地址字典key的v2均exit0；`backend_offset_chain.private.py` exit0。首稿把signatures的`typeReferences`字典误作list，exit1发生于固定源检查、listing重拼和身份计算之后、四份JSON输出之前；失败记录和五份部分listing保留，未追认首稿成功。修正只涉及私有脚本的字典读取，改用新`run2/`输出。没有新增JUnit/原生执行测试，不重跑已验业务回归。

本轮新PE/callee/global/payload采集、目标CPU、JVM、DB、客户端、构建、部署及Git/index均0；没有新安全拒绝。仅本报告和独占忽略证据有新写入，未修改Project/Harness/HANDOFF、运行代码或资源。离线文档/研究无需重启。主线负责分项验收、共享文档与本地提交；本代理未提交、不推送。D-02 / F-05 / D02-R6仍0/6，ready/required和父项状态不变。
