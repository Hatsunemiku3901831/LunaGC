# F-03d3s2 / Reader缓存、状态与补读合同（2026-11-11C）

任务：`F-03d3s2/SCENE-READER-REFILL-SEMANTICS`，父项`D02-R1`，同时服务Proud实际row reader。消费[486B候选原窗](CLIENT_72_D02_SCENE_READER_REFILL_WINDOW_20261111C.md)，不重做工厂、token、文件语法或分组。**派工时集成第三待主线，本页只分析候选固定raw，不替代来源验收或晋级自然reader/Scene/resourceReady。**

## 关键结论

`+20`在本窗只判零并完整传给`141123190`，没有拆token位；`+28`完全不读写。`+18`不是本窗产生的有效字节数，而是与`+1c`相加、经32位回绕和64位符号扩展后交读取调用第五实参的基值。

已有array需要补读时，本窗先写`+18=wrap32(+18+cursor)`、`cursor=0`再调用读取API。**初次array为空、从共享slot领入buffer的路径却不写`+18/+1c`**，直接用其原值之和作为第五实参。工厂只证清`+10/+1c`、没有清`+18`的缺口仍在，不能填自然初始基值0。

缓存足够条件是`signed(wrap32(cursor+count)) < signed(array.length)`，严格小于；恰好相等也补读。读取调用固定交token、当前array、0、**数组长度**及上述第五实参。本函数没有保存实际读数，也不检查API返回，故容量不等于有效字节数，短读/EOF/错误不能在这里被代答。下一必要原窗为同样服务Proud实际span的`141123190`，不展开通用registry、构造器或全部间接目标。

## 固定来源与实际核对

| 来源 | 大小 / SHA-256 | 覆盖 |
| --- | --- | --- |
| `local/migration72/d02-resume-20261111c/scene-reader-refill-window/{fresh1,fresh2}/body.bin` | 各486B / `dcf901e0952f9fa49adcf090c5b97510953019358c32f64bdf298840f8441999` | 完整`[14f614760,14f614946)`、122条 |
| 两目录全部9原件 | 逐byte相同；asm SHA `2f86e6324b5b82c4c3f5388144ed935d83e09180c8d1e006e30913184da18e48`；extraction SHA `db0b0e17a86fb3c482dbb4fe02132f1c423ee972f6207c5a7e6301bdda1301a0` | 本轮核连续hex拼回body、45引用、一RET、无外tail、五stock同源 |
| 采窗工具 | SHA `32c945ffdcc14385af7d170e672c3e15bccc36f30e62ccd4c466ff7f3801a45a` | 只读pin，不运行collector |
| 原pure audit | SHA `cf6667ed8bebd014569fae0571017d71e13b0398a9fdec3bda2d12cd22de4833` | Terra已有8门禁及纯原件审核，范围见原窗专题；本轮不重复计数 |
| metadata / signatures | 完整pin `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f` / `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe` | 禁止globals解析、核五实例字段与新native目标ordinary零命中 |

本轮来源检查：`local/migration72/d02-resume-20261111c/scene-reader-refill-semantics/semantic-source-check.private.json`，**7998B / SHA `49bb7610cc9879ccee4370643362c9fe01b0d85f43dd425c3e80e1b322fe7006`**。只读既存原件；新PE/callee/global内容/targetCPU/live均0。固定五stock和旧工厂来源沿[已验合同](CLIENT_72_D02_SCENE_READER_FACTORY_SEMANTICS_20261111C.md)复用。

## 输入和间接slot：先保留身份边界

记reader为R，T=`U64(R+20)`，B=`I32(R+18)`，P=`I32(R+1c)`，C为显式int32请求数，A=`R+10`的byte[]角色，N为当前A的`+18`DWORD。`+28`记为H，仅为保持原字段，不赋长度语义。

`476b/4770`先比较T。T零直接共同RET，甚至不进入后面的C保存和slot访问。T非零才在`4776/4779`保存C和R。

原访问链多次重复：从`[14589ff40]`所指值的`+ba`读U16位移；从`[145bc9170]`所指值读DWORD作为ECX；通过cell`143bac880`间接CALL。其返回保存到非易失寄存器，若零则调用cell`143bac3b8`。随后从保存值经`+10 → +60 → *QWORD`取得基址，再加上述U16位移访问slot。

| site | slot作用 |
| --- | --- |
| `4793/47a1 → 47b2` | 首次取得slot值并判零 |
| `4815/4823 → 4834` | 成长分支将原分配CALL的返回值写slot |
| `4889/4897 → 48a8` | slot原为空时将固定1MiB请求的原CALL返回值写slot |
| `48d0/48de → 48ef` | reader array为空时重新取得slot值，随后写reader`+10` |

八个间接CALL是四组静态site，不能称本次都已执行。零返回后的第二CALL并没有把新RAX重新赋给先前保存的context；不能假称它是成功初始化后继续的回调。slot自然值/范围/所有权、两cell真实目标及调用效果均未观察。

已有准确来源中没有找到这些cell的目标绑定。**不命名TlsGetValue、IFix、成功callback或自然线程缓存。** 本窗没有已绑定的IFix选择flag/wrapper边；也不以此断言未知间接函数内部没有派发。`140514ee0`两处只能按原CALL与请求数表达，不代答ArrayNew成功。其结果被byte[]字段/数组长度操作消费，不等于本轮已分配真实array。

## 全部正常路径与字段变化

以下均是相应间接链正常提供可访问输入后的条件合同，不是本轮运行结果。所有I32加法按原32位截断，比较条件为原signed条件。

| 条件 / 原PC | 字段与调用关系 | 边界 |
| --- | --- | --- |
| T=0，`4770→492e` | 五字段不写、不调用slot或read API | 仅token零旁路，不能据此宣布managed fallback对象有效；factory fallback未证清旧`+20` |
| 首次slot为零，`485e..48a8` | RCX=`[145886090]`，EDX=`100000h`，CALL`140514ee0`；返回值写slot，**此时不写R+10**。之后重读R+10 | 请求1MiB不证明返回array/长度或零填充；原R+10若非零会继续用原array，不能假定总改用新slot |
| slot非零且A非零，`47ca..47d8` | 读P，计算q=`wrap32(P+C)`，读N；若signed(q)<signed(N)，直接共同RET | 严格小于，不是≤；无byte有效性核验。N来自数组容量，不是read实际数量 |
| 上项不满足、signed(N)≥signed(C)，`490e..4929` | B′=`wrap32(P+B)`，写B′；P写0；同A交read API，N仍是同array长度 | 不复制残留bytes，而是从候选新基值重新读整N；第五实参由B′符号扩展 |
| 上项不满足、signed(N)<signed(C)，`47e7..4859` | RCX同上述cell，EDX=C，CALL`140514ee0`；其返回写R+10并写slot。再B′=wrap32(B+P)、P=0，重读R+10/N，进入同read | 真实分配结果/array长度未验；不是本窗倍增容量。若重读array为空，已写B/P后走`4940`错误边，不能回滚掉已见store |
| reader A为空，`48b9..490c` | 重新读slot到RDX，在`48f3`写R+10；非空后T取R+20，计算q=wrap32(P+B)，读取采用array的N | **B和P均不写、不清零**。C不参与这条领入后的容量比较；不能保证请求数≤领入array长度 |
| 同领入路径array仍空，`48fa→493b` | R+10已经写空，再进入错误helper | 不猜异常类型、helper必不返回或错误buffer内容 |

所以首个native字节的初始基值必须来自真实对象来源/重置合同，或另一个已证生产者：本helper并未为factory新领入buffer自动建立B=0。R+18的这个作用不同于**数组**+18的N，二者不得混成有效字节数。

## native读取与RET的准确合同

三路在`4921..4929`收敛，当前寄存器/栈为：

| 位置 | 原实参 |
| --- | --- |
| RCX | R+20完整opaque U64；没有本地拆位/变换 |
| RDX | 当前R+10的array值 |
| R8D | 字面0 |
| R9D | 当前array+18的DWORD；写R9D也将高32清零，不作符号扩展 |
| 第五栈参QWORD | `sign_extend_64(wrap32(B+P))`，已有array路径先写B′/P0后得到同一值 |

`4929`直接CALL`141123190`，下一条为NOP，随后恢复寄存器并在`493a`唯一RET。本窗不比较RAX、保存read数量、根据短读减少N、清尾部、核C是否满足、更新H或重试。因此read API正常返回的RAX会在寄存器中继续到RET，但声明返回类型未知，不能当bool/有效count；实际Scene/Proud消费者也不能据此得到成功保证。

其它共同RET入口：T零时RAX未在本body设置；cache足够时最后读P到EAX，其值不是状态码。不要把“正常RET”统一命名成功。`493b/4940`是两处`1404c2be0`CALL，末尾INT3；没有从这些site显式通往共同RET的边，异常与传播未执行。

本窗没有写R+20或R+28，base/cursor的修改也发生在read之前。即便后续实际API短读/失败后正常返回，这些先前store不会由本窗自动撤销；array字节受API怎样影响仍未知，不能造成功、补零或返回实际数的model。

## 可交的最小条件状态投影

规格文件：`local/migration72/d02-resume-20261111c/scene-reader-refill-semantics/conditional-state-projection-spec.private.json`，**4870B / SHA `b4d2dec7931b58e453f08edcc9e5d6a83f71d19e1e9a7dbb3cebd419ee5b4331`**。

此规格可交Terra做纯字段算术/读取意图投影，**不执行/model任何间接CALL、分配或native read**。起点限定为已声明的原PC条件：T零入口；已取得非零slot且array存在的`47b2`后；或slot已读出的`48ef`块。外部prefix没有证据就停在其调用前，不把fixture条件说成自然可达。输出已知字段前后位值、原PC、pending CALL或准确错误/分配停止点，保留unknown与ready=false。

| 独立期望例 | 源支持的结果 |
| --- | --- |
| 非零完整64位T=`fedcba9876543210`，B100/P3/C4/N8 | 7<8，只条件RET，字段不变，无read意图 |
| 同上C5 | 恰好8，B103/P0，同A；pending `(T,A,0,8,int64(103))`，H不变 |
| 同上C9 | 在`47f1`分配请求9之前停；不给原CALL假返回 |
| `48ef`领入已提供array，B17/P3/C1/N8 | 只写A；B17/P3保持；pending第五参20，不称已有有效8B |
| 领入时B−2/P1 | 第五参`ffffffffffffffff`，保留符号扩展 |
| 领入空slot | +10写空后停`493b`错误CALL，不造exception/正常返回 |
| C0/P=N | equality仍补读；不是零请求一律no-op |

JSON还列11个总向量中的负count、I32回绕和base回绕期望，只核原指令位语义，不把异常数值fixture称真实有效reader。不得加原窗没有的positivity/clamp规则。短读/错误测试在此只能确认“没有消费read返回/有效数store”的静态边界；有意义的bytes/EOF/错误结果驱动须等下述API原窗，不能靠有利回调补全。

## 唯一必要新原件

本轮在已存resource/index/provider/blob/payload、scene-runtime、astra原件及工具/文档中定向检查`141123190`，未找到其完整带raw body；已存Proud合同保存相同CALL实参和未检查返回的事实。这支持一个有实际后继价值的新窗：它同时决定Scene cache补读和Proud行span的可读性，不要求先解释通用token编码。

request：`local/migration72/d02-resume-20261111c/scene-reader-refill-semantics/native-read-one-window-request.private.json`，**15024B / SHA `213de70fa850bc4762869a177a62ae6e0ac79f5ccf91793b9923d8eed8c21f3f`**。

| 项目 | 精确单窗范围 |
| --- | --- |
| 前置/target | 主线接收486B集成第三后才采；唯一native target`141123190`。固定ordinary metadata零命中，AOT明确不适用/0读 |
| caller | 复用486B/122条原body与完整SHA；新PE仅核`14f614921..492e`13B `48894424204531c0e862e8b0f1`，原`4929` E8 signed rel32必须同target；不重读整个helper |
| pdata/body | 固定exception目录14344080B及SHA；exact/containing各唯一同记录，先保存真实12B pdata/pre-body。正extent≤8192B/2048条、可执行/file-backed；end未知，仅取原记录完整范围，无记录就0 body返回，不猜前缀/邻窗 |
| 问题/验收 | token/array/参数3/4及64位第五参怎样消费；实际字节写入、full/partial/EOF/错误和返回；是否保证容量范围初始化。双fresh、纯artifact audit、主线第三及来源/E8/64位rel32/pdata/节/短读/解码门禁 |
| 边界 | 只该body，不取storage slots、Pool、registry、ctor、另一个长度API、codec或live。首个仍必要的callee/data输入保存后返回；不把可本机取得raw的技术缺口称外部blocked |

自然reader对象/初始B、shared slot真实来源及有效native输入仍独立未闭。当前reader Pool未绑定准确A边，不能借World边泛封；如新源真出现A拒绝allocator/第二返回/Default或Bloader依赖，仅保存并停对应边。Proud初始化候选TLS路径和World/Proud现场不在本包。

## 交付边界

本轮仅新增本页与独占忽略目录的来源检查、投影规格和唯一read API request，没有实现任何runtime/model/driver，没有targetCPU、newPE/global/callee采集、构建/Java/DB/端口/client/调试器/部署或Git/index操作。其它tools、专题、Project/Harness/HANDOFF、config/Proto/patch/resources均只读；无需重启。没有新安全拒绝，既有A/B冻结及新拒绝原文停止规则继续有效。

主线负责来源第三、采窗/条件投影派工、共享状态和本地提交。本有界语义项交回即释放槽位，局部状态关系不升级为自然ready。
