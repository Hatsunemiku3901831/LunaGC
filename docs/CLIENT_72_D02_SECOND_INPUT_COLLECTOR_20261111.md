# F-03d3f4d2：第二allocation只读输入实现计划

## 已有、缺口与本批最小出口

[首pop副本](CLIENT_72_D02_FIRST_POP_COPY_20261110B.md)已从准确入口经原lookup/TLS/helper返回，仍只是20个直接allocation中的首个路径。下一原site为`15183fea8`和`15183feb5`；复用[第二allocation的29源规格](CLIENT_72_D02_SECOND_ALLOCATOR_SPEC_20261110.md)，不重新设计HashSet身份、201B零参ctor或Default热路径。当前只有HashSet同epoch身份，缺第二record/完整block和Default三个原QWORD；自然allocator线程/GC所有权、第二pop和完整Avatar仍未验。

本包仅实现新的纯只读collector与人工测试/driver，不启动目标、安装DLL、执行CPU或写捕获副本。原四profile、输入与工具保持字节不变；新profile名称`avatar-r1-pool-head-ctor-second`，不把它注册进旧工具以偷用旧scope。主线后续再单独审核adapter/现场协调器和新的来源seal。

## 冻结实现合同

- 复用原core/head/pool/ctor函数的准确输入图；在无读取的`core.start_collection`后，将新ledger的四个固定限额设为`44/1184/88/2368`（首读数/首读B/含final数/含finalB），再按原begin_head_inputs相同正常流程收集TLS/R1/Pool/head/items/HashSet。不改全局READ_PROFILES或旧helper，不先调用有final的旧collect再拼第二图；同一ledger只统一final一次。具体函数与来源由原源码完整SHA固定，漂移拒绝。
- 第一块已满足原读图后，仅在原已审支持的CA/ordinary/tag/size路径继续。由实际S2计算bin，读`C+28h+10h*bin/8`、`R2+18h/8`、`R2+20h/8`、`R2+10h/2`及`H2/L2`；`0<S2<=208`、`max(S2,56)<=L2<=208`，单次长度恰等实读L2，最大单读208，不补齐/拆分/重试。
- 独立R2分支为上述五storage项；同R同bin2分支只取首块N指向的完整第二候选，复用已成功的record/count/span，要求L1足以包含本ctor最后store，N非零且完整span与H1不重叠；same-R/different-bin、其他重叠/alias、零context/record/head/unsupported size均准确partial，不跟第二块的N2。
- Default仅`B+594fd78/8 → U+38/8 → P/8=D`；U/P零停止，不读零页；D零记录cold未闭，不多追全局或伪造Default。D非零仍是opaque原QWORD，不读取其对象或声明自然Comparer已验。items E仅值复制候选，不读pointee/宣称Array身份。
- 可成功复用的同地址同宽span显式去重，其他重叠逐字节核一致。所有成功unique在同一A事务末尾逐个统一final一次，包括两个完整block和Default链；首失败不扩，final失败不重试，原值/失败原件保留。非原子/ABA/GC未pin边界保持。
- A新上界为88读/2368B；same-R实际可更少，但统一拒绝阈值不放宽。本次不重做已答B env/base，也不把旧AB98/2304作为新scope；将来如与其他采集合并，必须另外证明身份、阶段及总账兼容。

## 文件、验收、停止与恢复

Terra xhigh只写`tools/client72_d02_second_allocator_inputs.py`、`tools/test_client72_d02_second_allocator_inputs.py`、本文以及私有`local/migration72/d02-resume-20261110b/a-second-inputs/`。旧core/head/pool/ctor/adapter、所有共享生成器/Proto/矩阵、生产/patch/资源/配置、Project/Harness/AGENTS只读。主线统一Git、构建、DB、现场和后续adapter；子代理不得扩招。

验收包括独立R2/同Rbin2、最大尺寸/64位及非零真实形状、零/unsupported/alias、所有首读与final失败/漂移、Default null/cold/热不解引用、精确预算/一次final、来源漂移拒绝；人工图不称自然输入。原四profile的旧测试与源码哈希保持，第三独立driver给精确请求顺序/输出和来源全SHA。未知字段/分支不能猜，发现合同冲突立即交主线；工具完成即返回释放槽。

本包恢复仅需丢弃emulator无关的人工内存/新报告，原件/旧工具不写。新现场必须由主线完成adapter/owned/API账、源门禁与stock保护/预算/退出方案，再通知用户并等待新实时ready；本页不是启动、replay或副本写许可。只有完整消费者与必要资源充分后才推进正式g2，不能将第二块输入采集当完整Avatar。

## 离线实现与人工图验收（2026-11-11）

`tools/client72_d02_second_allocator_inputs.py`独立实现第五profile；只在新账本实例设44/1184/88/2368，调用原入口校验后复述head首遍流程，复用原items/HashSet，再收第二块和Default。相同地址宽度的成功span实例内去重，不同宽重叠仍逐字节核对；所有成功unique统一final一次。首块和第二块遇未审post-pop边停，Default只保留opaque值、不读其对象；不注册旧profile。`verify_sources`先执行原ctor链校验，再核冻结第二规格和29个输入的完整SHA。它是单独源门禁，未来adapter必须在任何现场读取前调用，当前没有现场接入。

离线人工图运行`python -X utf8 -B -m unittest tools.test_client72_d02_second_allocator_inputs tools.test_client72_d02_tls_inputs tools.test_client72_d02_tls_pool_inputs tools.test_client72_d02_avatar_block_inputs tools.test_client72_d02_avatar_ctor_inputs`：**63项通过**。两条分支全部首读位置逐一短读/错误、全部final位置逐一失败/漂移；核最大独立44/1184及88/2368、同record40/1158及80/2316、最小长度、U64、尺寸边界、同record不同bin、空指针、块重叠、不同宽重叠矛盾、Default cold和未知后继停止、旧四profile回归及来源漂移拒绝。原源码均在29源完整hash门禁中，旧注册未改变。测试图是人工数据，不是实采。

独立第三driver两次fresh Python输出逐字节相同，完整SHA-256均为`9c9005600e2c806aa71156cb828fd02c900f3611d4ceefb674d14cdccf0ddff1`；driver SHA `598a01c22b78d019e28ee4f3f5459781815e1279e9015d6873a0f10137754311`。原始完整来源SHA、88项地址/宽度请求与完整结果保留私有目录`local/migration72/d02-resume-20261110b/a-second-inputs/`。本包未使用现场API、原生CPU、写副本、服务或客户端，也没有部署；自然线程、GC所有权、完整构造和D-02仍未验。主线接收后负责独立审核、共享adapter/stock门禁和单独预算，再决定是否进入新的实时ready。

## 主线集成验收与当前实机前置

主线仅在新collector中补入已验GC clear16来源：当首块原oldFlags含8且实际L1=32、原首未知edge为140563d85时，按真实notify/registration/C7继续判断必经后继。原flags及head bytes不改；其他长度、第二块helper与剩余未知仍停止。首版沿用旧head预测会在此提前停，现已修正并新增回归，不扩大原四profile语义。新增三pin分别固定helper原code、主线GC静态第三和首pop第三。

共享adapter增加显式第五profile布尔选择，必须同启pool/head/ctor；implementationPins精确六工具，源门禁先于身份/TBI/RPM。TBI仍两次96B、原primary关闭一次，旧API_LIMITS不变。主线共83项人工测试通过；11种原四profile的完整报告/请求/API/cleanup相同，仅替换了必然变化的adapter source SHA后逐项相等，不能宣称raw报告全字节相同。原core/pool/head/ctor源码及注册保持。

最终两fresh人工driver报告一致SHA `a1da12089c12849a99bfc875918e11f3736ba319e534fd0eb85b6ced0d2961b4`，88请求首末44项对应、2368B；原Terra过程结果不覆盖。主线来源/离线验收 `main-offline-review.private.json` SHA `926670f712cfc50e10232977721cce047908ecaf870f3a5baabb933e80c7f930`。该review明确live/CPU/副本写/完整Avatar均false。

下一包 `A2-STOCK-PREPARATION` 由主线Sol xhigh独占新coordinator/四PS文件，Terra xhigh仅独立人工测试目录；计划在私有`a-second-inputs/stock-helper/plan.zh.md`，新helper只收A一次，不重复B。需新源seal、人工/owned/API与PS审核、actualCheckOnly、stock/保护/进程端口及退出方案均完成后，才通知用户本轮操作并取得新ready。目前未启动、未安装、未复用旧ready，也不把离线出口当完整构造许可。
