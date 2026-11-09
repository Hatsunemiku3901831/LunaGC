# R2：自然 prefix13 的最短执行后继

2026-11-10收尾：本页仅完成原writer窗口静态分析、八点观察规格及整轮读取预算修正，经主线审核接收。**新八点观察器未实现、未构建、未安装、未实机执行。** 用户最新要求当前在途工作完成后暂停，因此不转入实现。前轮实际自然prefix13和stock恢复继续有效，不能用本页规格代验原Marshal、writer返回、自然Init、route或D-02。

归属 `R2-CONFIGSYNC-EARLY-BRIDGE`、`R2-ROUTE-CONFIG-SELECT`，承接[前缀观察器](CLIENT_72_SDK_CONFIG_PREFIX_OBSERVER.md)。本轮只消费冻结材料，不增加客户端读取、PE 窗口、hook、构建、安装、运行或部署。旧 event5、split event11 和 prefix13 分属不同 epoch，原件及其未知保持。

**当前出口：主线已接收唯一 writer byte窗；下文“单窗申请/收到窗口后合同”保留为前批历史，实施以本文末尾“writer 窗口后的冻结实施规格”为准。** 新规格可交 Sol xhigh 准备独立观察产物，仍须实际构建/owned/新门禁/新ready，尚不是自然或生产接入通过。

## 已消费的新自然事实

prefix `helper-preparation/live1` 的实际 90 秒 / 45 samples、同次 9 读 / 1450B / 单次最多 512B、完整 284B trampoline、51 EAT、稳定 DATA 和 16 项主线验收已接受。三个输入的 SHA-256 如下，路径均相对 `local/migration72/d02-parallel-20261109/b-sdk-prefix/helper-preparation/`：

| 输入 | SHA-256 |
| --- | --- |
| `live1.private.json` | `8be0a8a53eb173a375473645ad955d0fdd5ce5b0d63fa3f8c91dcfb73e3cdef2` |
| `live1.private.json.reads.private.json` | `2ed9de53be71624df8844d93ac6e01f63d8ebad38ad146374b8ac4927738691b` |
| `main-live-acceptance.private.json` | `934ba6137f724ffa35d20628ea7e8fd40cc65ed6a621df36892c9cd01844171e` |

准确 caller 的 event13 已证明：在原 32768 单位预算内，必要顶层字段已完整消费，得到 `cn-env-0`、`appKeyMatches=true`、`comboUrlMatches=true`，且真实 language quoted value 的词法状态跨过副本末端。这是 managed 入口前缀事实；不是自然完整 payload 分类。四个 `completePayloadValidated`、`payloadTerminatorObserved`、`originalMarshalObserved`、`naturalInitOrRoutePassed` 仍全为 false。未观察后缀可能无效或重复，不能从合法前缀反推其有效性。

用户正常页、正常退出、restore exit0、stock/13 保护及无相关进程端口，是本次已接受恢复证据。本轮没有重新盘点现场，也没有再次回放目标内存；离线检查仅核三个输入身份、已接受结果和下列静态边。

## 决策：下一步跨原执行边界

不再追 language 尾部、String 长度或 NUL，不放大预算，不重跑相同 prefix。自然 producer 已到准确 bridge，`R2-PLUGIN-UI-REGISTRATION` 不是当前必要前置；入口到达仍不能排除 IFix 分支，不能补写 ordinary branch 已执行。

下一唯一问题是：**在准确 ordinary 路径中，两次原 Marshal 返回之后，本次 `local_config` 是否进入原 writer，通过原 parser 的实际成功分支，执行必要字段分支并正常返回？** 若 parser 失败，即解释为何前缀正确仍不足以用于原生配置；若通过，再追实际 route/key consumer。不要以 void writer 返回、engine `+D8=1`、env 默认 0 或登录页代替其语义。

| 边界 | 冻结准确来源 / ABI | 当前已证与未证 |
| --- | --- | --- |
| managed producer | `LocalConfigSync` method40117 / EXE RVA `19806D70`；正常尾跳 `19807989`，implementation `6E363EE` 尾跳 bridge | prefix13 仅证明这一准确 caller 到 bridge；完整 serializer 未验 |
| bridge entry | EXE `6D80910..6D80A77`，359B，SHA `08de9a1aabf8fe27c2883958457b6ee1713923f851cda50af036b20b72e4cc1d`；RCX=this、RDX=name System.String、R8=payload System.String；原 CPU return `198035F6` | 实际安装与 caller 已验，ordinary/IFix 的自然选择未验 |
| ordinary Marshal | `6D809B0 call534480 → 6D809B5`；`6D809BB call534480 → 6D809C0` | helper `534480`、`507600` 仅静态；已有结果没有执行原 Marshal |
| native call | `6D809C3 mov RCX,RDI`、`6D809C6 mov RDX,RAX`、`6D809C9 call [EXE+57B4D60] → 6D809CF` | RCX/RDX 为临时 C-string；cf 后释放两缓冲，不能延长指针寿命。该原返回只证明 export 返回 |
| SDK dispatch | export `13D5B0`，RCX=name C-string/RDX=payload；tail→`1343B0`，RCX=engine/RDX=name/R8=payload；`1343E4 call [ConfigVT+3A0] → 1343EA` | 原 `36FBA0` lookup/handler 已 owned 执行；自然 export/分派未验 |
| writer | Config VT `6C6CD8+2E0 → 378850`；RCX=config、RDX=payload C-string、返回 void；handler `36E4E0` 为虚槽尾跳包装 | 原 constructor/dispatcher 仅到 writer entry cut；两个字段片段与完整 getter不等于完整 writer 返回 |

桥的 `6D809B5` 是已知 name-Marshal 返回点且位于 SDK export 解析之后；`6D809CF` 是原 native 返回点。它们提供同步窄观察的时序依据，**不是已审核的新 patch site**：特别不能从20B字节数推断 mid-function 搬移、CALL 返回地址或 unwind 已安全。优先复用既有正常加载和原调用，不开启 SDK wait 轮询、主动加载 SDK 或远程调用。

## 唯一关键单窗申请

现有完整 writer 的范围/hash 与伪码已足以证明问题必要性，但现有冻结报告没有完整逐指令行或原 bytes。`r2-route/main-third.json` 仅保留 `378DDA..378E70` 和 `379701..379882` 两片段；`r2-configsync/native-proof-main-third.private.json` 的 `sdkWindows` 仅范围/hash；`r2-natural-config/main-third.json` 同样仅范围/hash。不能据伪码 label 臆造 parse-pass、所有正常出口或 trampoline。

以下为提取前的原申请；单窗现已取得并通过下节静态审核，未执行原生CPU：

| 项 | 准确规格 |
| --- | --- |
| 固定输入 | `AccountPlatNative.dll` SHA `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25` |
| 唯一范围 | SDK RVA `[378850,379D7E)`，5422B，原 `.pdata` 完整 body |
| 已有代码 SHA | `ac3c851aa14b265d5db1874325fc0abd4af46f99fbf614e9193edb7c936bb42b` |
| 旧 source | `r2-natural-config/main-third.json:codeWindows[0x378850]`；`r2-route/rea-final.json` 的 `ev_abb7d2c29064aff33221aafa5a0437a1aad962ecbede61b59c0cbaa0bde84d41` |
| 调用边及 ABI 来源 | Config ctor 的 local_config 注册→`36FBA0` handler→`36E4E0`→Config VT `+2E0`；上述 event proof 的原执行 RCX/RDX 等值断言及 REA `void(config*,char*)` |
| 必须产出 | 私有完整 bytes、逐指令 RVA/bytes/operands、与旧 extent/hash 相等；标记原 JSON parser call/返回判断、成功/失败边、env store、app_key copy 返回、URL `380E60` call/返回、全部正常 ret 与异常边；核可用观察点的栈/寄存器及完整覆盖/回跳 |
| 关键性 | **关键**。决定一次自然实验能否区分 parser拒绝、必要分支未执行、writer正常返回；没有它不能给 Sol xhigh 实施/安装许可 |

不申请 `510CC0`：本问题可直接观察两次原 Marshal 返回与下游原 parser，不需要再模拟完整编码器。不申请 UI、完整 EXE 或额外 native getter 窗。若本窗仍不能给准确观察点，记录明确缺口，禁止自动扩大窗口或换路径。

## 收到窗口后的单次自然实验合同

这是**语义与预算上限**，最终 hook 数、完整 trampoline 字节和逐读账本须用上窗固定后再交实施，不把本节当已完成的 ABI/产物验收。

- 同一次受控 ordinary `local_config` 调用关联：name-Marshal returned、payload-Marshal returned、准确 SDK export、writer entry、原 parser 成功/失败分支、env/app_key/url 必要分支到达、writer normal return、原 EXE `6D809CF` 返回。嵌套同线程调用关联只在本同步调用内保存；未知 caller/name 不读 payload，不强制目标执行。
- **不复制 payload**，不遍历 Config/map，不读 managed header/length，不保存自然参数指针。关联只用原 CPU return、已知 native name `local_config\0` 的最多13个单字节比较及固定事件位；失败即停止该尝试、保持原调用。读取 name 的内存预算上限13次/13B，另调用关联最多两次8B原栈return；不得把这项上限冒充已有实现的实际账本。
- parser/分支/返回只发布固定 bit/status；必要值相等由已有 prefix13 作为历史来源，不能借给新 epoch 或据分支到达宣称存储值相等。原 parser 接受不自动命名为“完整 payload 验证通过”；writer返回 void，RAX不作成功码。新报告 `naturalInitOrRoutePassed=false`，直至独立业务证据闭合。
- 外 collector 只读固定代码和新自有 DATA。先设硬上限90秒/45 samples、最多32次/16384B、单次最多512B；窗口审核后必须下收为唯一逐读清单，包含各完整实际 trampoline、callback、两次一致状态和实际模块绑定。清单不足不扩大预算。原9读/1450B只属旧prefix，不能直接套用新多事件产物。
- 新独立 example/observer/fixture/decoder/adapter 与新 accepted epoch；旧六源/86pin、原observer和accepted全部只读。必须复用原转发/加载、固定 loaded 路径/SizeOfImage/磁盘身份/原窗/模块持有；任何失败保留固定stage，不改验证、返回、参数或 once，不隐式启用 key/route。
- owned 先验：准确两参/三参 ABI、原调用严格一次；每个观察 callback 前后 GPR16/XMM16/RFLAGS/128B栈/LastError保持，整体 wrapper 的原函数返回状态与无观察 baseline 对照；另验高64返回透明性、caller/name负例零payload读、原parser失败也正常返回的反例、非零env/字段缺失或空分支、writer未进入/未返回、跨调用不串位、first-failure停止，以及每个实际hook完整搬移/回跳/drop恢复。若使用 CALL wrapper，另核真实调用栈/返回地址和异常边，不把旧JmpBack结果借给新wrapper。
- 主线实际构建、owned双fresh、独立第三与代码/账本审核通过后，才新封存scope/保护基线/计划并取得新实时ready；一次自然尝试、零凭据，用户正常退出后核stock恢复、alias删除、五输入/13保护及现场归属。无事件也收尾，不自动重启或加读。继续沿用既有恢复流程，旧ready不复用。

## 与真实认证路线的汇合

完整 writer 的**执行证据**满足后，先看准确 AccountPlat 业务选择，再同一合法输入周期核实际 route 和 key；不能把 env0 的 `comboLogin` 等值用作另一字段 `loginUrl` 或独立 `passport_env` 的证明。

1. 原 `3773C0` getter 在 AccountPlat caller返回 `34ED67` 的实际 route，以及 `41E6F0` / option10002 的原调用有效期，给固定 host/path/method分类。复用[登录链](CLIENT_72_SDK_LOGIN_CHAIN.md)/[Crypto合同](CLIENT_72_SDK_CRYPTO_CONTRACT.md)，不追已释放队列/URL，不外部深读map。
2. `3950C0` 的两准确 key caller return为 `34EEB7` / `34EF88`，this VT `6D1CB8`；复用[原key核心](CLIENT_72_SDK_KEY_INPUT_ABI.md)、[原生桥](CLIENT_72_SDK_KEY_NATIVE_BRIDGE.md)及[加载器](CLIENT_72_SDK_KEY_LOADER.md)。既有实际安装没有采自然callback，不能称自然key已生效；需原key来源/供给和原返回的固定事实，禁止采账号/password/cipher或其hash。
3. combo builder `3BE5C0` 的 `3BEAD4→376EA0→3BEADA` 明确 R9=0，使用 Init map；`3BEA13→3958E0→3BEA16` 使用 config app_key。原生builder/签名→真实Java HTTP/保存→原生parser已由[HTTP桥](CLIENT_72_SDK_COMBO_HTTP.md)完成 owned 验证。自然实际 route、发送/返回、SDK callback及会话仍须独立闭合，不能重复owned算法作为替代。

当前不能给 Sol xhigh 生产 route/key 接入许可：自然完整 writer/parser/必要分支及认证 route/key调用尚未观测，R4完整前置也未齐。前批的单窗申请现已满足，准确实施后继见下节；本轮不部署或开启认证。

本轮 `analyze-frozen.private.py` 的15项离线检查通过，摘要2816B、SHA `9ff48ec979e8eebb85a7d56798db2989bfba540aea172100ca06104b8a499954`。初次把账本成功的 error=null 误断言为数值0，首失败已保留，仅修正新脚本的schema断言，输入未改；这不是目标读取失败。摘要与脚本仅在 `local/migration72/d02-parallel-20261109/b-sdk-prefix-successor/`；检查不执行原生CPU、不读取GAME_PATH、不重放自然payload。Project/Harness/HANDOFF及共享专题由主线统一维护，本卡没有Git操作或本地提交。

## writer 窗口后的冻结实施规格

主线9项 admission（SHA `fca900b3cb16868734d112f3613a388932c1777a0c980763905b192f42101bc9`）接收的输入是 `native-successor-windows-main1/sdk-config-writer/manifest.private.json`，SHA `4b42a197b4163a1f0285d1d91a72c74590d93a0fcf89eef24ae71d03aa29bfce`。5422B与原hash相同；1223条指令完整拼接等于 `code.bin`，12B pdata为 `508837007e9d3700d48a7300`，指向 unwind RVA `738AD4`。本卡未取得或解读该 unwind bytes，也没有取任何新PE/callee窗口。

### 原始控制流结论

- 外层原 parser 是 `378923 call14E110 → 378928 test AL`。非空输入且 AL非零时，`37892C` 令 BL=1；null/空/parse失败令 BL=0。临时字符串清理后，`378951 test BL; 378953 je379CE5` 是共同成功门。BL非零可证明本次原 parser 接受，BL=0只称“未接受”，不擅分null/空/解析错误。
- env 非空时从 `378A07` 进入选择SSO/heap→`378A1E call4903AC`→`378A23 mov [R15+8A0],EAX`；空值直接跳 `378A2A`。因此用两个点分别记录“写分支进入”及“写后公共点”，后者仅在前者同帧命中时读取**寄存器 EAX**为真实写入DWORD；不读 Config 值，不把默认0当写入。
- app_key非null/非空且原C-string扫描结束后，`378E4A..378E54` 调原 `138160`，目标 `R15+218`。url必须先经字段类型5检查，再由 `37984F call14E110` 接受嵌套JSON，且 `R15+860` 非零；此时才到 `379862`，随后clone返回 `379873`，`37987D call380E60 →379882` 更新map。
- 全body只有一个 `RET`：`379D7D`。失败门跳 `379CE5` 清理后也到它；成功路径还有剩余字段和析构，不能在 URL 更新后就称 writer 已返回。`379D56 call482380` 是cookie检查，返回 `379D5B` 后只有寄存器恢复、加栈、pop和RET，没有后续 CALL/分支。135个 CALL及三个虚CALL的异常后继不能据正常CFG宣称通过；原body无跳出范围的直接JMP，不等于已证明EH/unwind。

### 八个 JmpBack 点，唯一覆盖合同

全部使用冻结 ilhook2.3.0 `Hooker::new` 的14B选择规则，直到完整指令覆盖；不是按旧入口20B套用。下表的末端互斥，原回跳必须为该末端。检查了完整writer和完整bridge中的直接分支，没有从覆盖区外跳入区中部的边；到起点/末端的边保留。公开规格中“选中”与“完成”严格分开。

| ID/点 | 原完整覆盖指令；总bytes/末端 | callback中唯一事实 |
| --- | --- | --- |
| P0 EXE `6D809B5` | `mov rdi,rax`3；`mov rcx,rsi`3；`call534480`5；`mov rsi,rax`3；`mov rcx,rdi`3；`mov rdx,rax`3；20B→`6D809C9` | 第一个Marshal已返回；RAX是native name。核caller/name后同步绑定已加载SDK并安装P2..P7，随后原payload Marshal执行。原native CALL `C9`不搬移 |
| P1 EXE `6D809CF` | `test rdi,rdi`3；`je6D809DD`2；`mov rcx,rdi`3；`call[free]`6；14B→`6D809DD` | 同一桥帧的原native CALL已经返回；此时两临时缓冲尚未free。结束本次观察，不读取缓冲 |
| P2 SDK `37894A` | `lea rax,[rip+33051F]`7；`test bl,bl`2；`je379CE5`6；15B→`378959` | writer门到达及BL接受位，保存本次writer帧关联 |
| P3 SDK `378A07` | `lea rcx,[rbp+118]`7；`cmp qword [rbp+130],10`8；15B→`378A16` | env写分支选中；此时还未执行atoi/store |
| P4 SDK `378A2A` | `mov [rsp+20],r13`5；`movdqa xmm0,[rip+360E29]`8；`movdqu [rsp+30],xmm0`6；19B→`378A3D` | 只有P3同帧已到，才记录envStoreCompleted及EAX；EAX只发布已证CN枚举，未知值只报unknown |
| P5 SDK `378E4A` | `lea rcx,[r15+218]`7；`mov rdx,rax`3；`call138160`5；15B→`378E59` | 非空app_key原copy分支选中，不读source/target |
| P6 SDK `379862` | `lea rdx,[rbp+220]`7；`lea rcx,[rsp+68]`5；`call14D2B0`5；17B→`379873` | 原嵌套parser已接受且owner非null，URL更新分支选中；还未执行380E60 |
| P7 SDK `379D5B` | `mov rbx,[rsp+430]`8；`movaps xmm6,[rsp+3D0]`8；16B→`379D6B` | cookie检查已返回、唯一正常epilogue到达；此点本身不叫writerReturn |

不要选 `378A23` 做14B hook：`378A05` 会跳入其覆盖中部 `378A2A`。不要用 P5/P6 的到达直接发布copy/update returned。只有同一自然调用中 P7、随后P1均到达，才发布“正常epilogue后原native调用返回”；结合已选分支的无替代正常控制流，可报告这些分支随本次正常流程完成，仍不把void返回当配置成功码。不需要新CALL/RET wrapper或替换RET，不改原返回地址/参数；P0/P1/P5/P6的原CALL会由既有mover搬移，必须新增逐点owned验证，不能借旧ABI通过。

### 同一次调用的来源、帧和安装顺序

首次正常Astrolabe转发解析成功后，复用原47转发/self+EXE模块持有与磁盘/loaded身份绑定，只同步安装P0/P1，不等待或主动加载SDK。EXE的解析 `6D809A1→6D809A6` 先于P0，原native函数cell为 `EXE+57B4D60`。**第一次任意P0必须在任何目标读取之前，先以event的CAS `0→1`占用整轮唯一尝试；caller筛选在占用之后。** CAS未取得者零目标数据读取，直接透明返回。取得者先读原桥return `[RBP+18]`8B；不等于 `EXE+198035F6` 就固定 `unknown-p0-caller` 拒绝并seal，不读name/cell，也不把机会留给之后的正确caller。只有相等才读native name `RAX`，最多13次1B与 `local_config\0`逐项比对，再读准确native cell8B。SDK已由原resolver取得，P0核cell等于本轮已加载SDK `+13D5B0`，通过固定path/磁盘SHA/SizeOfImage/五代码窗并持有模块后，串行安装P2..P7。任何首失败不重试、不换入口、不清once；已安装的hook及模块在进程期保留，callback转透明，不并发Drop或卸载。owned另验Drop恢复。此实验准确名称是“一次任意P0尝试”，可能未命中eligible caller；不能称已保证采到一次eligible尝试。现有GPR没有已证的零读取caller预筛条件，本规格不假定存在。

原桥 P0/P1 的 `RBP=RSP+70`，原入口return位于 `RBP+18`。内部仅保留owner TID和这个桥RBP作短期关联；P1必须同TID/同RBP，不再读取原return。writer进入时push7个GPR再减3E0，因此body `RBP=RSP+100`，原writer return位于 `RBP+318`。P2只在event1且SDK绑定/安装已完成、同owner TID时处理；**内部writer-attempt位在第一次P2目标读取前置为true，之后所有P2零目标读取，不因caller正确或错误而重置。** 这唯一一次读取该return8B，须为SDK `+36FC80`：这是原dispatcher `36FC7D call handler+10` 的返回点；原owned分派到writer的RSP相对入口为 `-70`，以及 `36E4E0`虚槽尾跳共同支持该边。错误return或读失败固定拒绝并seal；正确才保存writer RBP/R15。P3..P7仅用同writer帧/同TID的寄存器关联，不读目标。原参数指针从不保留。

**事件参数读取硬账本：最多16次/37B**：P0桥return8B一次、native cell8B一次、name最多13次1B，P2 writer return8B一次。P1及P3..P7零目标数据读取；env取saved EAX，不读managed长度/header、payload、Config字段或map。此账本不包含固定代码绑定：初始EXE绑定为bridge359B和caller46B两读；P0 SDK绑定是export234B、engine81B、dispatcher261B、member-tail6B及writer5422B，writer按10×512+302分块，共15读/6004B。磁盘文件/模块清单继续用原loader合同，不把代码绑定冒充参数采样。

这是**安装至正常退出的全轮累计**，不是每个caller/每个线程的限额。顺序严格为P0 once→桥return→name→cell→一次SDK代码绑定→P2唯一return。首失败、event2或event3之后全轮追加参数读取为0，计数绝不reset，也不记到“caller检查”等账外项。未知P0 caller的全轮参数实际值为1读/8B；name第k个单位失败为`1+k`读/`8+k`B；cell阶段最多15读/29B；P2阶段最大16读/37B。任意后续P0/P2次数不增加这些值。P1错误线程/帧与非owner的P2只比较已保存GPR/TID和本proxy状态，零目标读取，不竞争新尝试。

caller不符、name不符、任一short/error、cell不符、SDK绑定/安装首失败、P2 return不符都统一先记固定reason，再release发布phase6/event3；后续所有callback只走原执行，P1也不能把3改成2。只有event1且同owner/桥帧的P1可发布event2；P1不补读，不重开预算。未知P0结果必须标明“唯一任意P0尝试被拒绝，未采到eligible caller”，而非“自然配置不存在”。本修正由 `writer-window-analysis/attempt-budget-correction.private.json` 的12项纯模型检查核对：含1000个后续正确P0、重复P2、错误writer后正确writer及其它线程，均无追加参数读；2343B/SHA `2f45829e048b15a3a2b2107b94a8bcd9245037c123446bace39bf63914982f0d`。旧21项报告及原来源保持，模型不代表实际Rust/ABI或自然验收。

固定代码绑定另有独立**全轮17读/6409B**上限：EXE两读405B先于P0安装；SDK15读6004B仅在唯一P0的caller/name/cell全部通过后执行一次。失败就停止后续窗口，不重新绑定或恢复读数；未知P0 caller只发生原先EXE两读，SDK代码0读。native cell8B属于参数账，不能再藏进代码账。磁盘哈希、模块清单和安装代码写入各沿原loader记录，不能宣称其属于这17次RPM。外collector另用下表30读/10415B；第一次DATA若phase6/event3、phase2/event0或活跃event1，固定在第1读256B后停止并报拒绝/无任意P0/partial，不解引用SDK/trampoline，不补一次末尾DATA以试图成功；身份末核、finally CloseHandle与退出恢复仍照常执行。若完整collector途中失败，也只保留已经发生的同次账本，零重试。

### 固定 DATA 与唯一外 collector 账本

新独立产物使用256B DATA，不改旧72B。布局：`0..23`为schema2/bytes256/phase/failure/Win32/installedMask六个DWORD；`24/32`为EXE/SDK基址QWORD；`40..63`为两SizeOfImage及original/self/EXE/SDK持有位六个DWORD；`64/68/72/76`为event/facts/CN-env-or-zero/reserved四个DWORD；`80..143`为P0..P7八个trampoline代码地址QWORD；`144..255`全零。只发布代码/装载元数据、固定事实位和CN枚举；不发布TID、帧、config指针、payload、自然长度/摘要或reference。env须有独立envStoreCompleted位，否则72必须零；非CN只报unknown，不能把零解码为env0。

facts位固定为bit0 ordinary/name、bit1 native-return、bit2 writer-gate、bit3 parser-accepted、bit4 env-path、bit5 env-store-completed、bit6 app-key-path、bit7 url-update-path、bit8 writer-normal-epilogue、bit9 env-is-known-CN；其余为0。bit9必需bit5，bit5必需bit4，bit3必需bit2。72仅在bit9置位时为CN枚举，否则为0。event固定0尚无任意P0尝试、1首次任意P0已占用且尚未终止、2已在同owner P1结束、3首失败拒绝并seal；**所有event均单调，禁止回0**。phase固定0未准备、1准备EXE、2仅P0/P1已装等待本次P0、3本次P0绑定/安装SDK、5八点已装、6首失败。installedMask仅低8位，完整状态为FF；初始两点为03。collector完整成功要求phase5/maskFF/event2/零failure及前后256B相同，不以phase5单独证明事件。新EAT维持原47转发，state@48 DATA、唯一active inner@49，以及原依赖实际引出的两个明确dormant@50/@51；不得激活dormant或新增观察入口。

八点共享一个冻结38B XMM4/5 wrapper和一个按owned userdata.site选择分支的inner。每个ilhook trampoline的源分配是**固定1024B零初始化Box**，其已写部分为250B共同前缀+原mover+14B回跳。规定每个只读384B：逐byte核完整实际编码/所有重定位表/返回边，余下至384B必须仍零。按所选指令和冻结far mover的保守上界最大334B（P1），近编码也须由实际owned emitter证明≤384；不按state里的任意length读、不读取1024B整块。若actual emitter超过384或不匹配，拒绝本产物，不扩大读取。

成功collector的**唯一30读/10415B，maxSingle512**如下，固定顺序；失败提前停止。地址只由本次模块基址、固定RVA与已核入口JMP目标推导；DATA中的trampoline地址必须与该JMP一致，不能当任意地址读取接口。

| ordinal | 固定区域 | 请求bytes |
| --- | --- | ---: |
| 1 | 新DATA before | 256 |
| 2 | 原47转发目标表 | 376 |
| 3 | EXE bridge `6D80910`完整窗 | 359 |
| 4 | EXE caller `198035E8` | 46 |
| 5 | EXE native cell `57B4D60` | 8 |
| 6 | SDK export `13D5B0`完整窗 | 234 |
| 7 | SDK engine `1343B0`完整窗 | 81 |
| 8 | SDK dispatcher `36FBA0`完整窗 | 261 |
| 9 | SDK member-tail `36E4E0` | 6 |
| 10..20 | SDK writer `378850`，依次覆盖完整5422B | 10×512+302 |
| 21 | 新共享callback | 38 |
| 22..29 | P0..P7完整trampoline及零尾 | 每个384 |
| 30 | 新DATA after | 256 |

新verifier须比较完整bridge/writer的准确patch及其余原字节、完整八个trampoline、原CALL目标/重定位返回、实际callback/inner及source epoch；不能只核hash/后缀或“≤384”。不读取userdata、core、自然字符串或引用。DATA前后不等、安装中、活跃scope、未终止event、短读或模块/创建身份变化即partial。继续采用90秒/45sample模块monitor、最后存活、finally CloseHandle与同次raw账本纯重放，不能复用旧ready/PID/base。

### 结果边界、owned出口及恢复

固定事实至少包括ordinary/name、native-return、writer-gate、native-parser-accepted、env-path、env-store-completed、app-key-path、url-update-path、writer-normal-epilogue。P1是本次scope结束，之后不追加事件/数据读；无writer、parser未接受、必要分支缺失、缺P7或非CN env均保留各自固定结果。完整正常阶段证据也保持 `completePayloadValidated=false`、`naturalInitOrRoutePassed=false`：它不验证未观察正文的独立语义、reference等值、自然route/key/会话或D-02。

Sol xhigh可据此实现新独立example/observer/fixture/verifier/adapter；不得修改旧六源/86pin/accepted/helpers/Cargo声明（主线另审manifest）。必须先冻结本分析输入及原47/loader/assembly/mover source。至少两fresh真实owned加主线独立第三，逐点验证near/far emitter、全部覆盖/转移目标、≤384完整零尾、drop恢复；ABI核GPR16/XMM16/RFLAGS/128B栈/LastError，原CALL次数/顺序/参数/原输出与无hook baseline一致。特别覆盖P0被搬移的payload-Marshal、P1的条件free/间接CALL、P2两条条件边及RIP LEA、P4 RIP XMM load、P5 copy CALL、P6 clone CALL、P7 XMM6恢复及最终高64返回。

语义矩阵需有：原parser失败仍正常ret；env缺失/非零CN/未知、先跳P4却未P3不误报store；app_key缺失/空且无P5；嵌套URL parse拒绝/owner为空/正常更新、无P6不误报调用；P5/P6后原callee不返回时无最终完成；P7到而P1不到只报epilogue；错误bridge/writer caller、name近似/不可读、同线程另一帧、其它线程及重复调用均不串位/不加读。仅用自有代码和对象验证；不把这些合成阶段夹具称为完整writer原生执行。

异常/unwind仍是明确限制：未取 `738AD4` xdata，旧JmpBack也未证明异常穿过动态trampoline；搬移CALL改变临时返回地址，不能宣称异常栈透明。实现不得补成功异常handler、注册猜测unwind或以新CALL/RET wrapper绕过。此规格只准备用于一次零凭据的正常启动观察；production route/key接入和异常路径验收仍未许可。若主线审核要求异常透明作为本次前置，则停止在这一准确xdata缺口，不自行取窗或扩大方案。

实际构建、完整owned、source/artifact/EAT/DATA/collector第三审核后，主线再封存本产物专属scope、五stock/13保护与配置基线、安装/正常退出/stock恢复脚本，并取得新实时ready。失败不主动目标调用、不重启或重试；正常退出且OS确认目标结束后恢复stock，仅删除本次新建且身份相符的orig alias，复核保护/现场归属。游戏服务仍不部署，认证凭据仍不输入。

本轮仅运行冻结材料的21项Python投影检查：`writer-window-analysis/analysis.private.json` 为10915B/SHA `e65b13bd252b3faba22fd11acf3a1e8142d72e6630672d904ab861fb4e568d29`；全1223指令/原bytes、八覆盖/无内部入边、关键原CALL与唯一RET、30/10415账本及原mover来源核对通过。未执行原生CPU、构建、客户端、现场读或部署；自然父项仍未完成。八点/新256B DATA独立产物只是恢复候选，暂停后没有交Sol xhigh实施。

## 主线规格验收与整轮预算修正

原“eligible caller后才占once”的表述不能限制任意错误caller重复触发的读取。主线指出此缺口后，本页已改成**首次任意P0在任何读取前占用整轮once**，错误caller立即拒绝并seal；P2同样在读取原writer return前占用唯一尝试，后续P0/P2零读取。没有已证的零读取caller预筛，不再假定存在。

修正后纯模型12项检查通过，包括1000次后续P0/P2、错误caller、错线程及首失败：参数整轮最多16读/37B；代码绑定独立最多17读/6409B；外collector成功30读/10415B，首DATA拒绝或partial时1读/256B后停止。摘要 `writer-window-analysis/attempt-budget-correction.private.json` 为2343B/SHA `2f45829e048b15a3a2b2107b94a8bcd9245037c123446bace39bf63914982f0d`。旧21项报告和分析源保持，没有重写旧预算证明。

主线最终8项只读规格审核通过，证明 `b-sdk-prefix-successor/main-final-spec-review.private.json` SHA `954ca9aca9e5e4ff697a816af77d670e3716e450187b2b58cd8b6312a46c6f96`，只接收原始source投影和修正后纯模型规格。上述相对目录位于 `local/migration72/d02-parallel-20261109/`。交付原文SHA `71a1dc3abc07328a52273ddf76c785041f5f94c119379ea888f6ac0f1bce41e9` 已备份到同根 `closeout-20261109T164643Z/before/docs/CLIENT_72_SDK_PREFIX_NATURAL_SUCCESSOR.md`，保留历史pin。

异常/unwind、实际搬移CALL的异常栈透明性、八点owned ABI/恢复、自然writer/parser/分支及完整Init/route仍未验证。没有新accepted产物或新实时ready；本页规格不是实现验收、部署许可或客户端成功。
