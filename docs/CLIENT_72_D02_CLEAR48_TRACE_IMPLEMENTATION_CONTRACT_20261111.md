# F-03d3f4d2/CLEAR48-NATIVE-TRACE-IMPLEMENTATION-CONTRACT

## 中文有界计划

本卡只把已有TraceSpec压成可分工的接口与下一自有witness规格，不写C/共享header/runner、不构建、不执行CPU/Debug API/目标或live、不操作进程/Git/网络、不扩招。先读当前AGENTS/README/Project/Harness/迁移/部署，原TraceSpec、verify/delivery、root静态第三和冻结输入；再读新无debug原6B实际结果及已接纳GitHub固定源码/官方API摘要。旧文件与上一包写权均归root，只读。

1. 固定输入SHA与验收分层：无debug能力已通过；debugger的退休时点、TF、CONTROL-only/YMM、真实CALL/RET与失败收尾仍未验。不重爬GitHub，不抄Titan含DEBUG_REGISTERS的context实现。
2. 选C parent+WinSDK原类型，定义parent/child/离线176清单/validator接口与唯一writer建议；只给机器可读自有layout设计，公共header由root唯一生成并freeze，子包只读消费，不手写Windows CONTEXT偏移。
3. 首批只设计三个独立新自建child：最小退休/CALL/RET、原23B leaf的不同YMM lane及两store、独立UD2失败。精确列bytes/状态/数据/事件/上下文返回、预算和收尾；首次系统breakpoint/自有INT3的实际RIP关系由witness测，不写RIP纠偏或预填结果。
4. 一次列全执行前置、暂停期和句柄归属。parent只Get CONTROL|INTEGER，Set CONTROL只改TF；不请求或回写浮点/XState/debugregs、GPR/RIP/RSP、预期buffer，不patch/step-over/attach/host模拟。未知线程/事件/异常/EA/外跳留原失败，安全/Daybreak立即停包。
5. stock-shaped AVX48是witness验收后另批的最小原helper出口：准确两笔32B/16B重叠old/new和原RET/RAX/RSP。人工SSE/AVX-long及捕获副本分开；本首批不执行904B。成熟接口和witness规格立即交root/Terra，停止写入并释放max。

## 当前状态

本有界合同已完成，四个机器可读设计JSON已冻结并先交root。root据此生成唯一`tools/client72_d02_native_trace/shared.h`；本代理只读核对48个自有offset断言、两个size、七byte array和常量一致，未编译。当前没有新trace实现/CPU/API结果。root已可将child和parent分别派Terra；validator另包。交回后本文与私有目录停止写入。

## 来源、已验与未验

原[TraceSpec](CLIENT_72_D02_CLEAR48_NATIVE_TRACE_SPEC_20261111.md)21318B/SHA `ac6176db1a6ebecc1f2d3f55781e9ca888054d53fecad5759f1fb0d763e95a49`保持；原/root第三静态报告各25514B，仅self-source SHA不同，内容相等。其121源冻结清单第三SHA `0ea702f27cb7a0b7ff682d686dbc4ead032c98ac4d704923e8d16977bd010746`逐项通过，涵盖旧112pin、904B/176条连续bytes和stock标量来源。原904B SHA仍`34287f706e27541c8119091aa9e55c7e2916610935f90c9b74992856b9654b31`。Project/Harness/当前AGENTS只作当前context，不固定易变正文。

[无debug能力结果](CLIENT_72_D02_CLEAR48_NATIVE_CAPABILITY_RESULTS_20261111.md)已由root实测接受：一次UD2真实异常、两fresh各一次原23B Probe，EXE SHA `69259bce74f452829959e751d94c8682e1e4b1c760a7dcbce11da5e0764417f1`；原主审SHA `d132e6667535f8ab4bbd2d770c0eb5cf5f9c9a386e091a44a08e9ab314fdfea4`。这些不证明新Debug API往返保留YMM或逐条退休。

[固定GitHub/API审计](CLIENT_72_D02_CLEAR48_TRACE_GITHUB_20261111.md)12837B/SHA `3e4999943da08b5dc50e41cd460de03e05b492eb5726a76fd6a69b63df2af5f3`、root16检查SHA `64147f9705dee232322e15e07c2b64ca93ebdd79abdc2612a53c1b6c8a9941ea`复用；24高层操作和clone底层HTTP未知的partial边界不变。本卡0网络，不复制Titan代码/运行库；其CONTROL|DEBUG_REGISTERS与完整context不适用。WinSDK10.0.26100.0原`winnt.h` SHA `8693f0ad4ada355c912d40ca41ce929b480e92291bda2f9227acf56736bffbff`及已核debugapi/processthreadsapi/minwinbase用于原类型，不猜CONTEXT偏移。

## 最小分工与冻结接口

| 唯一writer | 输入与输出 | 不交叉写入 |
| --- | --- | --- |
| root | shared.h、实际SDK/构建/source epoch、postlink bootstrap128B及JSON镜像、执行审批 | header/schema与实际RVA只由root生成；不由子包补字段/偏移 |
| Child Terra | child.c、witness.asm；消费只读header；显式owned存储、host门、descriptor及child-result | 不写parent/header，不包含或调用904B |
| Parent Terra | parent.c；消费只读header/bootstrap；debug loop、逐API/event/事务原件、失败收尾 | 不写child/header，不重写寄存器结果或child payload |
| Validator独立包 | 纯离线catalog/validator；读固定176清单、设计schema与实际报告 | 不执行CPU/API，不改decoder/原件/header，不生成“native通过”样本 |

选择两个独立C EXE，避免共享wmain。parent使用WinSDK的`DEBUG_EVENT/CONTEXT/PROCESS_INFORMATION`；不引Python ctypes的Windows结构布局。root仍可用现有脚本构建/启动parent，但创建、Wait、Get/Set和Continue归同一个native parent线程。

唯一header已由root创建，SHA **`689c722e78d116d8de876238eea125958d289a1c2b7971ad7b34baa3cacb61b1`**。`C48Descriptor`256B/pack8、`C48Bootstrap`128B/pack8，所有字段offset/size均有C11断言；本轮只检查断言文字与设计相符，未声称编译断言已执行。Windows类型不放进本自有pack块。子包必须pin同一header，漂移回root，不自行修offset。

四个只读机器设计位于`local/migration72/d02-resume-20261110b/a-clear48-trace-implementation-contract/`：

| 文件 | bytes | 完整SHA-256 |
| --- | ---: | --- |
| `interface.v1.private.json` | 8967 | `0230c5212b0f1645345069455716e50a1635b575d6708670f03cf0bd2b7ae832` |
| `witness-cases.private.json` | 5816 | `1250c0bdc83182de47b89b33981d08b741679e8ac68b4978e6014cbf1932be99` |
| `event-schema.v1.private.json` | 6126 | `5cc14c09ac7682101f2af9f052920b503a9b7e7e79c7771d877eae4f6f5fb461` |
| `frozen-inputs.private.json` | 3165 | `a965f6309a988ea221b4789e02e794a17c9616090c1534a460ae9e11049627f5` |

parent入口为固定child绝对路径、root bootstrap、case1/2/3、16B runId、全新输出目录，无PID/attach参数。child入口只有case/runId/该run内新child-result文件名。bootstrap由root实际map/PE产生：descriptor RVA、EXE/header SHA、image size、Arm/Call/AfterCall/wrapper与三leaf RVA；当前不填实际RVA。parent先核bootstrap与EXE/header同epoch，再CreateProcess一次；不把child自报label当独立定位证据。

child只在准备完毕后写READY descriptor：magic/version/size、真实PID/TID/imageBase、runId/case、root所对应labels、owned范围、stack bounds及W2真实host门。parent在Arm停点只读核对。reserved是本自有结构的显式0，不代表未知页；child返回C才写RETURNED/continuedToC，报告完成后写REPORTED。parent不写descriptor或远程内存。所有u64地址/寄存器JSON为16位lowerhex，原bytes为定长lowerhex；unknown使用null+status，不能填0冒充观察。

## 共用25B wrapper和三个精确witness

root header固定`C48Wrapper`：C参数为`(leaf,input,before,after)`，原RCX/RDX/R8/R9由ASM变为R11=leaf、RCX=input、RDX=before、R8=after。先真实分配28h栈frame并提供正确unwind，然后自有INT3、真实CALL R11，最后正常回C；不向栈预写返回值。

```text
wrapper25B: 4883ec28 4989cb 4889d1 4c89c2 4d89c8 cc 41ffd3 4883c428 c3
Arm=+16, Call=+17 (41ffd3), AfterCall=+20, End=+25
W1 leaf11B: 48b8efcdab89674523f1 c3
W2 leaf23B: c5fe6f01 c5fe7f02 c4e37d18c001 c4c17e7f00 c5f877 c3
W3 leaf3B: 0f0b c3
ReadXcr0 leaf13B: 31c9 0f01d0 48c1e220 4809d0 c3
```

上述均是拟实现的byte合同，header常量不是新PE/运行证据。实际ASM/map/PE连续解码、wrapper prolog/unwind及全部label先由root核；差异停止评审，不改原6B或调整RIP补救。以Call停点真实RSP=C，要求C mod16=0；真实CALL只许写[C-8,C)8B，返回地址是AfterCall。parent读slot old，不预设其值或先poison栈；退休后实际RSP=C-8、slot new=AfterCall，才能封CALL事务。RET执行前读取同slot8，真实post须RSP=C/RIP=AfterCall。

| case | 应有硬件路径与真实断言 | 次数（设计上界，不是已测值） |
| --- | --- | --- |
| W1_RETIRE_CALL_RET | CALL→leaf+0；MOV RAX=f123456789abcdef退休→leaf+10；RET→AfterCall。逐次实际RIP/RSP和64位非零RAX核对 | 3条退休；TF置/重设3次、恢复1次；Get含一次system BP最多9；CPUID/XGETBV0 |
| W2_YMM_CONTROL_ROUNDTRIP | CALL→leaf+0；逐条post RIP为+4/+8/+14/+19/+22/AfterCall。+4 store真实32B应为01..20，+14 store真实32B应为01..10重复。load/VINSERT与store之间均真实经过CONTROL-only Get/Set | 7条含CALL退休；TF置/重设7、恢复1；Get最多17；child真实CPUID2/XGETBV1 |
| W3_UD2_FAIL_CLOSED | CALL先真实退休，UD2尝试后首机会C000001D，ExceptionAddress/RIP均FaultLeaf+0，RSP=C-8；UD2 pending未退休，后面的RET/C continuation不执行 | CALL退休1、UD2尝试1未退休；TF置/重设2、不恢复；Get最多6；parent失败30、child按自有终止90；只可验“预期负证人” |

W1的literal是明确自有程序测试，不是原helper成功stub。W2复用无debug已验23B，但Debug API路径仍是新实验。三个独立96B block各含32B前guard/payload/后guard，W2新reserve64KB、只commit一页并显式poison；仅288B逻辑buffer域有效，padding禁止作为operand。input=01..20，before/after初始分别CC/DD；三块前后guard分别A1/A2、B1/B2、C1/C2；未声明padding用A7 poison，仍无读取许可。parent从真实store各取32B old/new，不写expected。

第一store保留不同高低lane的01..20，证明load后CONTROL往返没有破坏YMM高半；第二store检出VINSERT结果跨CONTROL往返后仍正确。普通CONTEXT不读或伪造完整YMM；这两个完整真实内存快照才是向量证据。+19 VZEROUPPER后不再声称YMM仍保留快照。input及全部192B guards不变，两store64B是witness写，不能叫GC的48B。

W2每个新child仍自己测host XSAVE/OSXSAVE/AVX及XGETBV(0)的XMM/YMM bits，不能硬填旧e7；不满足就停在Arm之前。W1/W3不执行AVX，host两probe计数0。所有case每child至多一次wrapper CALL；无warmup/loop/重试。

## 事件状态机、marker与CONTROL-only

状态固定为BOOT→ARM_STOP→PENDING/POST_STOP→TRACE_DONE→WAIT_EXIT→EXITED，失败进入FAILED_STOP→TERMINATING。每次Wait返回只处理自己的PID与初始TID；没有SuspendThread/OpenThread/attach。BOOT允许明确列出的CREATE_PROCESS、LOAD_DLL及一次Windows初始breakpoint，记录module来源/地址/RVA；不把“遇到的第一个任意异常”称system BP。未知thread/异常/事件停止。armed后只允许匹配的step；DLL/thread变化也失败。

系统初始BP的ExceptionAddress、CONTEXT.RIP及其差必须实录。自有INT3也记录地址/实际RIP；只有原marker bytes/label匹配且实际RIP已经等于Call才可Arm，不能先填“+1”或改RIP。若Windows实际关系不同，保留校准诊断停止。首次校准也保存SS的ExceptionAddress与实际post RIP关系，不预填Windows如何保存TF。

每次停点用SDK原`CONTEXT`，只请求`CONTEXT_CONTROL|CONTEXT_INTEGER`。Set使用独立CONTROL-only结构：从**同一暂停期成功Get**逐字段复制SegCs/SegSs/Rsp/Rip/EFlags，仅TF(100h)按本次请求改变；其它CONTROL和全部GPR不改。每次Set后再Get CONTROL|INTEGER，记录BOOL/失败时LastError、readback TF和其它CONTROL/GPR不变。不能用FULL/ALL、FLOATING_POINT/XSTATE/DEBUG_REGISTERS/SEGMENTS，也不回写这些组。局部CONTEXT零初始化不是child未知页初始化；未请求字段不当观察值。

首次Arm保存原TF；本最小新child要求实际原TF=0，非零留诊断，不强制改成0后继续。每次只授权一条后都明确Set TF=1并核返回，不假定Windows trap后的TF值。RET后的AfterCall停点先记录真实RAX/RSP/完整buffer，恢复**原TF位**并核返回，再正常Continue到自有C收尾，不继续trace wrapper epilog或OS代码。

交易只保留一个pending：pre停点核实际PC/bytes、寄存器、明确operand EA/宽度和允许span，保存old/read；成功Set/Continue后，唯一同PID/TID的首机会SINGLE_STEP、实际post RIP落在该条精确允许后继才封retired，再读write new。CALL/RET以后继和实际RSP/栈槽核，不用线性PC+size伪造。源指令是post事件以前的pending PC，不能把post RIP标作store PC。

witness EA只需这几种明确族：CALL R11的栈写；RET的栈读；RCX→input32读32；RDX→before32写32；R8→after32写32；其余为无数据operand的固定寄存器指令。MOV/VINSERT结果只能事后比较，parent不实现x86结果模拟器。W3的UD2是唯一批准的witness-only故障指令：正常SINGLE_STEP或RET不符合该case，原native fault必须失败停；validator可接受正确失败行为，不把parent30转称正常trace通过。

## 原件接口与执行预算

`events.jsonl`逐项保存raw debug event（PID/TID/code/firstChance/address/flags/参数）、每次Wait/Get/Set/Continue的实际返回、暂停event序号和句柄来源。Get失败时context无效，不把零buffer当寄存器；成功API的旧LastError不当失败。Set记录before/submitted/readback，证明只改TF。每笔ReadProcessMemory记录purpose/address/width/BOOL/实际nread/raw bytes；nread不足立即停止，不借padding补齐。

事务记录最少为txId、preEvent/prePC/source bytes、真实pre GPR/EFlags、每个operand的EA/宽度/允许span/old、TF与Continue记录、postEvent/实际PC/GPR/EFlags/new、retired/status。fault时pending保持未退休，最后已完成事件和partial bytes保留。inspector reads不是CPU硬件read hook，总线写次序/原子性未验。

每case硬上限：1 child/1 CALL/1 pending、10秒墙钟、128 debug events，Wait slice50ms，Get≤32、Set≤16。W1/W3 inspector最多16读512B，W2最多64读4096B；源文件/PE静态读另列，不记CPU operand。预算以真实API计数保存，不能预填期望数或延长重跑。输出目录新建，parent-result/events/child-result/stdout/stderr/validator均CreateNew；失败、缺失child-result和退出码原样保留。

## 失败收尾与句柄归属一次列全

parent只CreateProcess自己的固定EXE，DEBUG_ONLY_THIS_PROCESS/隐藏窗口；不额外CREATE_SUSPENDED制造Resume计数。创建线程负责全部debug API。显式记录DebugSetProcessKillOnExit(TRUE)返回，使debugger退出时只由其所建debuggee受系统终止策略；不detach留下未知child。

来源/host/header/layout失败在Arm前停；EA/PC/宽度/外跳失败在本条执行前停；未知异常保留first-chance原证据且不续成成功。首个失败粘住，不再Set TF、授权指令、回写结果或标pending退休。用自己CreateProcess返回的process handle调用TerminateProcess(exit90)，保存BOOL；只有终止请求成功后才允许对outstanding event作**cleanup-only Continue**并排空EXIT，不把它记作原指令继续/退休。EXIT_PROCESS事件Continue后再核owned process handle signaled和真实exit；child仍活/超时则cleanupComplete=false，不假称正常退出。Terminate失败时不继续原暂停事件，保存失败并让debugger按已接受的KillOnExit策略结束；不重新OpenProcess、按旧PID处理或改方法。此失败路径本身由W3验证，未证就不接受trace校准。

| 句柄来源 | 正常/失败的责任 |
| --- | --- |
| CreateProcess的PI.hProcess/PI.hThread | caller-owned，parent仅用它们读自己的process/thread；收尾确认后各手动CloseHandle一次，记录来源与结果 |
| CREATE_PROCESS/CREATE_THREAD debug event中的process/thread handles | debug API管理的借用句柄；对应EXIT Continue的自动关闭，不再手动CloseHandle；不与PI所有权混同 |
| CREATE_PROCESS/LOAD_DLL的有效hFile | parent记录文件身份后手动关闭一次；不能套process/thread自动关闭规则 |
| 未知TID/事件 | 不OpenThread/Get/Set；仅保存失败和终止/EXIT drain信息，不产生新的执行许可 |

cleanup Continue也记录原event PID/TID/返回值；Terminate未成功时没有此许可。一般工程错误保留准确阶段/API/error与未完成项；安全门禁/Daybreak立刻记停，不换模型、编译器、VEH或其它方式重试。

## 离线176清单、validator与后续边界

现有`a-gc/gc-helper.manifest.private.json`已经固定176条；其SHA、904B拼接及范围均在121源冻结账。后续catalog包只输出不可变数据：ordinal、原PC/RVA/offset/size/bytes、mnemonic/operands、数据operand方向/宽度/EA表达式、control-flow族/后继、支持状态。按176行连续拼回904B；没有充分族/implicit访问支持就标unsupported，不能漏列后运行。该清单**本首批不链接或执行904B**；witness parent只消费25/11/23/3B的小清单，不引新runtime decoder或通用DBI。

离线validator输入root source/PE/bootstrap/header、三设计JSON、parent原件、正常case的child原件与完整buffer；只验证事实/变异拒绝，不调用API。分别输出W1正常trace、W2向量往返/两store、W3失败关闭是否成立；`traceCalibrationAccepted`须三者及root收尾核验都通过。W3只有“预期负证人接受”，其`normalTracePassed=false`。错误PID/TID/事件、漏/重复step、错误Get/Set组或非TF变化、短读/错EA、错stack/nextPC、伪post/guard、被标retired的fault、budget/exit不符均应拒绝。synthetic变异明确标记，不覆盖实际样本。

本首批验收只到这里。之后另批的最小helper为**stock-shaped AVX48**：I6/T0=524288、原AVX DWORD01fdfb94、长度48、T1/F不提供且禁读；新稀疏arena reserve06d20000，只commit已声明页，poison未授权padding而不将其授权。H64是人工owned数据，只复用stock标量形状，不是captured heap。未来新的child adapter自己准备RCX=D/EDX0/R8=48和真实CALL；parent仍不写GPR。原两笔b94:32B H+20..40、b9b:32B H+10..30必须各有独立退休和真实old/new，sum64/union48/intersection16，第二重叠old等于第一实际new；再有原RET→AfterCall、RAX=D/RSP=C。完整真实trace另批前不链接/执行该904B，更不以最终全零替代逐笔。

人工SSE/AVX-long仍独立case，不为这次48B资格扩大长度/loops或补T1/F。捕获副本写、原第二allocator/Default、自然线程/GC所有权、完整Avatar/g2和D-02全部保持未验。

## 本轮实际核验与交回

本轮纯读文件核对原TraceSpec/verify/delivery、121源及904B/176条、root无debug能力proof、新GH16检查和SDK四原头。四JSON与root header的只读证明保存于`shared-header-review.private.json`；只是源级匹配，没有执行C static_assert、构建、witness或Debug API。本代理未改root header或旧source，没有网络、CPU、进程/现场、构建、Git或新子代理。唯一public改动为本文，全部写权交回root后停止；成熟接口由root/Terra实施，不占max做机械实现。
