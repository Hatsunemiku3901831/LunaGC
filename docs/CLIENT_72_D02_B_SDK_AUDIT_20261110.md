# D-02 B：SDK 八点观察实施审核（2026-11-10）

任务为 `R2-CONFIGSYNC-EARLY-BRIDGE`、`R2-ROUTE-CONFIG-SELECT`。本批恢复指令覆盖旧暂停，旧 ready 已消费。**结论：冻结输入足以交 Sol xhigh 实施一个独立、零凭据、正常启动观察产物；尚无八点实现、构建、owned 或自然验收结果。** 本文修正一处 P0 覆盖文字冲突，统一实现与 collector 合同；不改旧专题、报告、pin 或已验结论。

本审核没有 Git、Cargo、Gradle、DB、客户端、调试器、GAME_PATH、取窗、安装或部署操作。新离线审核实际通过 26 项，证明为 `local/migration72/d02-resume-20261110/b/audit_frozen.private.json`，11,638B / SHA-256 `ecb9740624dfbe75425949345ddb824b7263f03c7259ba6cf75dc7c3f123ddcb`。它只证明冻结来源、逐指令投影、复用边界及预算，不能证明新 Rust 或 ABI 已通过。

## 输入、复用与本批文件

接手依据为 AGENTS、README、Project 3.2–3.4、Harness、HANDOFF2、迁移/部署手册、[自然后继冻结规格](CLIENT_72_SDK_PREFIX_NATURAL_SUCCESSOR.md)、[自然 prefix13](CLIENT_72_SDK_CONFIG_PREFIX_OBSERVER.md)、[早期桥](CLIENT_72_SDK_CONFIGSYNC_EARLY_BRIDGE.md)、[配置 loader](CLIENT_72_SDK_ROUTE_LOADER.md)及原 key/HTTP/登录链专题。旧暂停是历史记录；本批实际授权仍只有明确分配的审核和后续主线实施。

| 冻结来源 | 本次消费边界 |
| --- | --- |
| `b-sdk-prefix/helper-preparation/live1.private.json` / `8be0a8a5…e3cdef2` | 原自然 event13：`cn-env-0`、两 reference 等值；四项完整 payload/NUL/原 Marshal/Init-route 仍 false |
| 同名 `.reads.private.json` / `2ed9de53…8691b` | 同次 9 读/1450B 的固定 code/DATA；只离线消费其桥代码，不再读取现场 |
| `main-live-acceptance.private.json` / `934ba613…4171e` | 16 项主线接受、正常页/退出、stock/保护恢复；不能借给新 epoch |
| `native-successor-windows-main1/sdk-config-writer/manifest.private.json` / `4b42a197…9bfce` | 固定 SDK body `[378850,379D7E)`，5422B/1223 指令；code SHA `ac3c851a…bb42b` |
| `b-sdk-prefix-successor/writer-window-analysis/analysis.private.json` / `e65b13bd…68d29` | 原 21 项静态投影；原件保持 |
| 同目录 `attempt-budget-correction.private.json` / `2f45829e…82f0d` | 首次任意 P0/P2 先占 once 的 12 项纯模型；原件保持 |
| `b-sdk-prefix-successor/main-final-spec-review.private.json` / `954ca9ac…6c6f96` | 原 8 项规格审核，只接收规格，不是实现验收 |

上表相对证据根为 `local/migration72/d02-parallel-20261109/`；本批 proof 记录完整 SHA。固定 SDK 为 `AccountPlatNative.dll` SHA `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25`，EXE 身份仍以 profile 为准。

已核上一轮 Sol max 中文 `upstream-reuse/branch-search-plan2.zh.md` pin 与 Terra `branch-retrieval2-20261109T133247Z-terra/` 的 manifest/result/coverage/comparison。结果仍为 `bounded-partial`，2394.234 秒超过 1200 秒墙钟上限；4 库 heads 盘点不等于全部源码覆盖。所选 patch 仓库只有 main，tip `2b589125874b0b96f8694bfad31e34811eba8573`，复用缓存完整 18-entry 树，未读到准确 LocalConfigSync/Init 实现。C2 仍未全面覆盖。此次是固定目标窗与既有转发器的明确小后继，没有新的上游语义缺口或有价值的重复检索目标；不再爬已有结果，也不将 partial 包装成完整搜索。

本审核仅新增本页及 `local/migration72/d02-resume-20261110/b/{plan.zh.md,audit_frozen.private.py,audit_frozen.private.json}`。后续交 Sol xhigh 的明确文件清单如下，须由主线分配写入权：

| 新文件 | 职责 |
| --- | --- |
| `patch/examples/sdk72_config_stage_proxy.rs` | 独立 opt-in DLL/owned EXE、原 47 转发、进程期 holder、初始 P0/P1 安装 |
| `patch/examples/sdk72_config_stage_proxy/loader.rs` | 新 `stage_loader`：只绑定已加载模块、2/5 窗口及固定分块；不改原 loader |
| `patch/examples/sdk72_config_stage_proxy/observer.rs` | 256B DATA、八点 userdata/site、唯一 inner、38B wrapper、参数读取与状态机 |
| `patch/examples/sdk72_config_stage_proxy/fixture.rs`、`fixture.S` | 自有八点原覆盖/ABI/分支/预算/失败/恢复夹具；不加载或执行目标 DLL |
| `tools/verify_sdk72_config_stage_proxy.py` | source/build/material/PE/EAT/DATA/owned/逐点 emitter、纯 collector 与解码 |
| `tools/observe_client72_config_stage_proxy.py` | 主线专用 Windows adapter；唯一外读账本及进程/模块生命周期 |
| `tools/test_verify_sdk72_config_stage_proxy.py`、`tools/test_observe_client72_config_stage_proxy.py` | 必要负例、累计预算、生命周期、失败保留和同次重放 |
| `patch/Cargo.toml` | **仅主线**追加新 example 的 cdylib 声明；不改旧声明和 lock |

后续独立输出根约定为 `local/migration72/d02-resume-20261110/b-stage/`，不得覆盖审核目录或旧 accepted/helpers。新根包含 `binding.private.rs`、`source-main1/`、`target/`、`owned-main1/`、`adapter-owned/` 和另封存的现场 epoch。私有 binding 只含固定模块/窗口/指令材料，不嵌入新自然 payload/reference；允许 CN 集合复用旧 classifier 的 `CN_ENVS=[0,1,4,6,8,9,22,19]`。

原 `sdk72_key_proxy_loader.rs` 的 `SdkPolicy.windows` 固定为 `[Window;3]`，并整块请求 `window.bytes`。直接调用它不能满足本合同的 EXE 两窗、SDK 五窗及 writer max512 分块。新 `stage_loader` 复用其路径、磁盘 SHA、SizeOfImage、已加载模块加引用等合同；旧 loader 与旧 observer 仅作为冻结依赖保留。不使用 `wait_loaded/activate`，不新增 worker、主动 SDK 装载、目标调用或 route/key hook。

## 八点覆盖：P0 必须采用实际 14B

冻结末尾文字表将 P0 写成 20B→`6D809C9`；固定 ilhook 2.3.0 `Hooker::new` 的 `jmp_inst_size=14`，原 21 项 JSON 实际选择也是 **14B→`6D809C3`**。本审核从冻结 sidecar 恢复旧入口覆盖以外的完整桥，SHA 与原 359B 桥相同，再按原逐指令宽度独立选择，确认此结果。

不改 ilhook、不造强制 20B API，也不将原 JSON 改成文字的结果。**实现、owned、live collector 必须共用下表；P0 的 C3/C6 两条 mov 留在原位，C9 的原 native CALL 不搬移。** P0 原 payload-Marshal CALL 仍会被 mover 搬移，必须实际 owned 验证。

| 点 / 模块 RVA | 实际完整覆盖（长度）→唯一回跳 | callback 可记事实 |
| --- | --- | --- |
| P0 EXE `6D809B5` | `mov rdi,rax`3；`mov rcx,rsi`3；`call534480`5；`mov rsi,rax`3；**14B→C3** | name-Marshal 已返回；经 caller/name 验证后 ordinary/name 位；同步绑定/安装 SDK 六点 |
| P1 EXE `6D809CF` | `test rdi,rdi`3；`je6D809DD`2；`mov rcx,rdi`3；`call[free]`6；14B→`6D809DD` | 同桥帧原 native CALL 已返回，缓冲尚未 free；结束 scope |
| P2 SDK `37894A` | RIP LEA7；`test bl,bl`2；`je379CE5`6；15B→`378959` | writer gate；BL 非零表示原 parser 接受 |
| P3 SDK `378A07` | `lea rcx,[rbp+118]`7；`cmp qword [rbp+130],10`8；15B→`378A16` | env 非空写分支选中，尚未 atoi/store |
| P4 SDK `378A2A` | `mov [rsp+20],r13`5；RIP `movdqa xmm0`8；`movdqu [rsp+30],xmm0`6；19B→`378A3D` | 同帧 P3 已到时才记 store completed，取 saved EAX 的 DWORD |
| P5 SDK `378E4A` | `lea rcx,[r15+218]`7；`mov rdx,rax`3；`call138160`5；15B→`378E59` | 非空 app_key copy 分支选中；不是 copy-return |
| P6 SDK `379862` | `lea rdx,[rbp+220]`7；`lea rcx,[rsp+68]`5；`call14D2B0`5；17B→`379873` | 原嵌套 parser 已接受且 owner 非空，URL 更新分支选中；不是 `380E60` 返回 |
| P7 SDK `379D5B` | `mov rbx,[rsp+430]`8；`movaps xmm6,[rsp+3D0]`8；16B→`379D6B` | cookie CALL 已返回、唯一正常 epilogue 到达；不是 RET |

原 writer 唯一 RET 为 `379D7D`；P7 后无 CALL/分支，仍需随后同 scope P1 才可说“正常 epilogue 后原 native 调用返回”。原 parser 失败也会清理并正常 RET，所以 void、P7、native-return 各自均不能作为配置成功码。P5/P6 到达只发布 branch/path；不能单独发布 copy/update returned 或业务成功。

`378A23` 不是允许替代点：其 14B 覆盖有外部跳入 `378A2A` 中部的边。八点原直接入边分析保持；不新增 CALL/RET wrapper、不替换 RET、不取新 callee。

## 安装、唯一尝试和同次关联

首次原 Astrolabe 绑定/正常转发解析成功后，原 Once 顺序同步 pin self、绑定已加载 EXE，仅安装 P0/P1，然后才正常转发原 API。这个顺序不证明覆盖进程任意更早线程/调用。P0/P1 的 userdata、holder、模块引用及所有成功 HookPoint 在进程期保留；失败也不并发 Drop，不回滚已装点。owned 中独立验证 Drop 恢复。

P0 处理顺序必须是：

1. 任何目标数据读取前，CAS `event:0→1`。未取得者直接透明返回，参数读取 0。**不得抄旧 prefix 的“先解引用 caller 再 CAS”。**
2. 保存私有 owner TID 和桥 RBP，唯一读 `[RBP+18]` 8B，须为 `EXE+198035F6`。错误/失败即拒绝并 seal，不继续读 name/cell，不给后续 caller 留机会。
3. RAX 是第一个原 Marshal 返回的 native name。依次最多 13 次 1B，精确比较 `local_config\0`，首错/失败停止；不读 managed header/length、payload 或参数旁数据。通过才置 ordinary/name 位。
4. 唯一读 `EXE+57B4D60` 8B。只接受已加载且本轮身份合格的 SDK `+13D5B0`；不主动加载或轮询。完成固定 path/磁盘 SHA/SizeOfImage、模块持有和五代码窗绑定后，串行安装 P2..P7。
5. 每成功安装一个点立即保留其 HookPoint/userdata/module 生命周期并发布真实 mask/trampoline；某后点失败不能由 `?` 或局部 Vec Drop 隐式卸载先前点。首失败留下实际 mask/held 位，不能调用旧 `fail()` 把它们清成 0 冒称未安装。

桥 P0/P1 原 `RBP=RSP+70`，原 return 在 `RBP+18`；writer body 原 `RBP=RSP+100`，return 在 `RBP+318`。这些地址属于原 Registers 快照，不是 Rust callback 当前 RSP。

P2 只有 `event1`、SDK 六点安装已完成且同 owner TID 才处理。**第一次这样的任意 P2 在读取前先消耗私有 writer-attempt 位**，唯一读 `[RBP+318]` 8B，须为 SDK `+36FC80`（原 dispatcher `36FC7D call handler+10` 返回）。错误/读失败拒绝并 seal；后来正确 P2 也零读取。正确后保存私有 writer RBP/R15 和 writer-open 状态，置 gate，BL 非零置 parser accepted。

P3..P7 仅比较已存 TID、writer-open、RBP/R15 及 event，不读目标数据。P4 必须此前同 writer P3 已到才解释 saved EAX；只发布已知 CN 枚举，其他值为 unknown。P7 置 epilogue 后关闭 writer-open；这是首 writer 的结束标记，防止同一桥内第二次 writer 复用同一栈地址时串入第一份 facts。writer-attempt 不复位、不重新打开。P1 只需 event1、同 owner TID/桥 RBP，置 native-return 后 release 终止为 event2；之后所有事件忽略。嵌套同线程另一帧、其他线程、重复 P0/P2/P1 不能加读或串位。

私有 owner、帧、config 关联只活在此 observer，不发布到 DATA，不保留自然参数指针。没有已证零读取 caller 预筛；实验名称必须是“一次任意 P0 尝试”，不保证命中 eligible caller。

首失败先存固定 reason/Win32，再 release 发布 event3/phase6；P1 只能将仍为 1 的 event 终止为 2，不能把 3 改为 2。event 不回 0，累计计数不 reset。P0/P2 入口错误可固定命名 `p0-return-read/unknown-p0-caller/name-read/name-mismatch/native-cell-read/native-cell-target/p2-return-read/unknown-p2-caller`；模块绑定/安装保留固定 stage 和 site，不序列化任意异常正文。最终数字枚举由新 source pin 固定，不沿用 prefix event13 的解码器。

## 三本读取账与 DATA

这些都是**安装至正常退出的一整轮上限**，不按 caller、线程或对象重置。失败/short read 也按已请求次数及 bytes 记账；没有末尾重试。

| 账本 | 唯一顺序 / 整轮最大值 |
| --- | --- |
| 参数 RPM | P0 return8 + name 最多13×1 + native cell8 + P2 return8；**16次/37B**；P1/P3..P7 为0 |
| 固定代码绑定 RPM | 初始 EXE bridge359/caller46 两读；P0 SDK export234/engine81/dispatcher261/member-tail6/writer10×512+302；**17次/6409B/max512** |
| 外 collector RPM | 下表，完整 **30次/10415B/max512**；首 DATA 非合格终态仅 **1次/256B** |

未知 P0 caller 实际参数为1/8；name 第 k 字节拒绝为 `1+k`/`8+k`；cell 阶段最多15/29；P2 最多16/37。后续1000次正确/错误 P0/P2 均不得改变已封存计数。未知 P0 caller 只有预先 EXE 两个绑定读，SDK代码0读。

这里17次是显式窗口验证 RPM，不冒充所有安装/装载行为：原 loader 的磁盘哈希、模块 API，以及冻结 ilhook 的指令解码（每点最多30B原代码 view）、内存保护/patch/恢复均依原安装合同单列。不得添加额外窗口验证读藏入“安装”，也不能声称“全进程所有内存访问总共6409B”。新实现只更换显式绑定器的窗口列表/分块，不更换冻结 ilhook 的安装算法。

256B DATA 布局固定如下；`#[repr(C)]` 且 compile-time size256，保留区全零：

| 偏移 | 内容 |
| --- | --- |
| 0,4,8,12,16,20 | DWORD schema2/bytes256/phase/failure/Win32/installedMask |
| 24,32 | QWORD EXE/SDK base |
| 40,44,48,52,56,60 | DWORD EXE/SDK SizeOfImage、original/self/EXE/SDK held |
| 64,68,72,76 | DWORD event/facts/CN-env-or-zero/reserved0 |
| 80..143 | P0..P7 八个 trampoline 代码地址 QWORD |
| 144..255 | 112B 全零 |

facts：bit0 ordinary/name；1 native-return；2 writer-gate；3 parser-accepted；4 env-path；5 env-store-completed；6 app-key-path；7 url-update-path；8 writer-normal-epilogue；9 env-is-known-CN，其余0。bit9要求5、5要求4、3要求2；env字段仅bit9时为已知CN值，否则0。CN env0必须同时有5/9，不能由零初始化冒称实际写入。

event：0尚无任意P0、1已占用未结束、2同owner P1已结束、3拒绝seal。phase：0未准备、1准备EXE、2仅P0/P1已装、3本次P0绑定/安装SDK、5八点已装、6失败。mask仅低8位，初始完整两点03，完整八点FF。held/mask/trampoline按实际已成功动作保留，不能因失败伪清空。

EAT 精确维持51项：原47 @1..47，`Sdk72ConfigStageProxyState @48 DATA`、唯一active `sdk72_config_stage_inner @49`，明确dormant `sdk72_config_inner @50`、`sdk72_key_input_inner @51`。只有新 inner 可由八点共用38B XMM4/5 wrapper 到达；不注册/激活两个 dormant。保留冻结 GPR/XMM0..3 ilhook 保存、Win64非易失寄存器规则及 inner 内完整 GetLastError/SetLastError 包围。

## 外 collector：固定顺序与首 DATA 退出

只执行一次 collector；90秒/45次 monitor 继续只查模块/进程元数据，不轮询 DATA、不增加 RPM。第一读不是 `phase5/maskFF/event2/failure0/Win320` 的一致、合法256B DATA时，停止于第1读：phase6/event3为拒绝，phase2/event0为未采到任意P0，event1/安装中为partial；不读SDK/trampoline、不补末尾DATA。前后身份末核、最终alive及finally CloseHandle仍执行。

| 次序 | 固定对象 | bytes |
| --- | --- | ---: |
| 1 | DATA before | 256 |
| 2 | 原47转发目标表 | 376 |
| 3,4,5 | EXE bridge `6D80910`、caller `198035E8`、cell `57B4D60` | 359 / 46 / 8 |
| 6,7,8,9 | SDK export `13D5B0`、engine `1343B0`、dispatcher `36FBA0`、member-tail `36E4E0` | 234 / 81 / 261 / 6 |
| 10..20 | SDK writer `378850` 连续5422B | 10×512 + 302 |
| 21 | 共享 wrapper | 38 |
| 22..29 | P0..P7 每个固定384B trampoline（含零尾） | 8×384 |
| 30 | DATA after | 256 |

完整成功还须前后DATA逐字节相同、四个模块 EXE/proxy/original/SDK 的实际 path/磁盘SHA/SizeOfImage/base及creation identity前后相同、最后存活、恰一次关闭。SDK未出现的拒绝/无P0结果允许保持该缺席事实，不为等SDK延长窗口；若身份在采集前已不满足，0 RPM并保留原因。所有中途失败停止在原次序前缀，零重试。

代码验证必须同时做到：

- 完整359B桥与5422B writer只出现各点准确patch，其余全部原样；P0回跳C3，C3/C6/C9全部原样。近JMP实际写5B，far实际写14B，覆盖中其余原bytes保留，不能要求未写区域被NOP化。
- 地址仅从本轮模块base、固定RVA、已核entry JMP推导；DATA中的trampoline必须与entry目标相同，不能作为任意读地址接口。
- 每个trampoline按实际 emitter逐byte核共同250B prefix、所有实际 mover指令/重定位表/CALL目标/回跳和剩余零尾。源分配固定1024B零Box，**只读384B**；far保守最大334B（P1），actual near/far都须≤384。超出或未知编码拒绝产物，不增读。
- wrapper 38B与artifact相等，CALL目标必须是本epoch EAT@49的inner；inner身份由固定artifact/source及该准确CALL边绑定，不增加inner RPM来冒充本账本覆盖。不得读取userdata/core/帧/自然字符串/reference。
- collector只报告结构/代码绑定与固定facts。先验prefix13仅作历史证据，不能将其reference等值写入新epoch。raw sidecar只能来自同一30项读函数，主线以该sidecar纯重放输出逐字段相同；不另读一次生成“审计”数据。

## 主线构建与 owned 验收

主线先审diff，单独追加：

```toml
[[example]]
name = "sdk72_config_stage_proxy"
crate-type = ["cdylib"]
```

约定新verifier的CLI沿原格式，source-only准备不接触GAME_PATH、不执行Rust；实际产物命令中的GAME_PATH由主线按本轮配置重新核定，不能复制历史机器路径。以下是**待实现后运行**的准确入口，本审核未运行：

```powershell
python -B -m tools.verify_sdk72_config_stage_proxy --prepare-build-inputs --output-directory local/migration72/d02-resume-20261110/b-stage/source-main1
if ($LASTEXITCODE -ne 0) { throw 'Stage source gate failed.' }
$env:CARGO_HOME = (Resolve-Path local/cargo).Path
$env:RUSTUP_HOME = (Resolve-Path local/rustup).Path
$env:CARGO_TARGET_DIR = Join-Path (Get-Location) 'local/migration72/d02-resume-20261110/b-stage/target'
$stageDef = (Resolve-Path local/migration72/d02-resume-20261110/b-stage/source-main1/config-stage-proxy.def).Path
& local/cargo/bin/cargo.exe rustc --manifest-path patch/Cargo.toml --example sdk72_config_stage_proxy --crate-type cdylib --release --locked --offline -- -C "link-arg=/DEF:$stageDef"
if ($LASTEXITCODE -ne 0) { throw 'Stage DLL build failed; preserve failure.' }
& local/cargo/bin/cargo.exe rustc --manifest-path patch/Cargo.toml --example sdk72_config_stage_proxy --crate-type bin --release --locked --offline
if ($LASTEXITCODE -ne 0) { throw 'Stage owned build failed; preserve failure.' }
python -B -m tools.verify_sdk72_config_stage_proxy --client $env:GAME_PATH --production local/migration72/d02-resume-20261110/b-stage/target/release/examples/sdk72_config_stage_proxy.dll --fixture local/migration72/d02-resume-20261110/b-stage/target/release/examples/sdk72_config_stage_proxy.exe --run-owned --output-directory local/migration72/d02-resume-20261110/b-stage/owned-main1
if ($LASTEXITCODE -ne 0) { throw 'Stage artifact/owned gate failed.' }
python -B -m unittest tools.test_verify_sdk72_config_stage_proxy tools.test_observe_client72_config_stage_proxy -v
if ($LASTEXITCODE -ne 0) { throw 'Stage Python checks failed.' }
```

`--run-owned`须真实启动两个fresh自有EXE，保存实际source/manifest/def/lock/DLL/EXE身份与完整emitter结果。主线独立第三另选新输出目录，不把两个报告中的计数或static source gate当第三执行。新source闭包包括实际Rust include、Python传递导入、冻结assembler/loader/mover与全部材料，按实际文件清单逐项pin；旧六源/86pin保持原样，不机械宣布新闭包仍是这个数字。

| 必验矩阵 | 精确出口 |
| --- | --- |
| 八点真实 emitter | 每点near/far路径由实际距离和实际生成bytes证明，完整覆盖/直接及间接CALL目标、条件两边、RIP重定位、唯一回跳、≤384及全部零尾；不能用新Python模板自证actual结果 |
| 八点真实执行 | 自有代码/对象逐点实际hook/callback/mover/回跳/Drop；P0 payload-Marshal严格一次与两参/三参桥ABI；P1条件free/间接CALL两边；P2 BL双边/RIP LEA；P4 RIP XMM；P5 copy CALL；P6 clone CALL；P7 XMM6恢复 |
| callback与原流程透明 | callback前后GPR16/XMM16/RFLAGS/原参数栈128B/LastError相同；被搬移原指令及callee的合法变化，与无hook baseline比较；原调用次数/顺序/参数/输出相同，最终高64返回保持，不把RAX作为void成功码 |
| 正常与失败语义 | parser拒绝仍正常ret；env缺失/非零CN/未知；未P3直接P4不误报store；app_key缺失/空不P5；嵌套URL拒绝/owner空/正常更新；P5/P6后callee不返回、P7到而P1不到分别不报最终完成 |
| 关联与整轮限额 | 错bridge/writer return、name近似/大小写/short/error；首次错误后1000次正确P0/P2零追加；同线程另一帧/其他线程/嵌套/同帧地址后续复用不串位；首失败后P1不改终态 |
| 安装失败与寿命 | 各安装序号首失败保留实际mask/held和之前hook；余callback透明、不重试、不隐式Drop；owned结束正常Drop完全恢复原bytes |
| collector/adapter | 完整30/10415及1/256各出口；每ordinal short/error停止、DATA不等partial、invalid/reserved bits拒绝、模块/creation漂移与最终退出、0RPM前置拒绝、finally一次关闭、同次sidecar纯重放 |
| 真实文件门禁 | 当前五stock/SDK carrier、accepted source/artifact/EAT/DATA、已安装proxy+准确orig路径、四模块身份；先在忽略目录的真实隔离文件布局通过，不能mock哈希/文件门禁或直接安装来试 |

near/far自有页可以按owned地址规划，不改变生产Hooker；必须记录实际选中分支，达不到某分支就保留未验，不将模拟emitter或source命中称实际通过。fixture允许合成stage与owned callee，但必须明确它们不是完整目标writer/parser/Marshal原生执行。

## 单轮自然、恢复与准确停止条件

主线完成实际构建、双fresh与独立第三、artifact/source/collector审核及真实文件门禁后，再封存本产物专属scope、source/artifact/import闭包、当前五stock/13保护/配置、备份/安装/正常退出/恢复脚本。取得**新实时ready**后才运行；旧页面反馈/ready/PID/base不能复用。一次任意P0尝试，90秒/45监测、一次collector、零凭据，无事件或拒绝也完整收尾，不主动目标调用、加读、换入口或再启动。

用户正常退出且OS核目标结束后，只恢复本轮精确stock备份，仅删除本次新建且身份相符的orig alias，实际restore exit0；复核五stock、SDK carrier、13保护及原缺席事实，核本项目进程/固定TCP/UDP归属。没有待恢复DLL、未知残留target或待处理提权时才释放现场互斥。不得强杀未知进程、改验证/版本/返回/参数或触碰原数据。此临时观察不部署GS；R4前置满足后，运行改动仍需按AGENTS正常构建/portable部署及日志/健康/TCP/UDP/PID/运行JAR SHA核验。

结果必须区分：任意P0被拒绝且未取得eligible caller、无任意P0、writer未到、parser未接受、env分支未写/未知、app_key/URL分支未到、仅epilogue、以及正常epilogue后native-return。完整正常阶段结果也保持 `completePayloadValidated=false`、`naturalInitOrRoutePassed=false`，不新发布reference等值，不关闭自然route/key/信任/认证或D-02。

**剩余准确未知**：`738AD4` xdata未取得/解读；动态trampoline与搬移CALL的异常栈透明性未验；YMM/ZMM/x87/MXCSR、生产并发/完整GC边界不由普通Win64 owned晋级。135个原CALL和正常CFG不能证明异常后继。不得注册猜测unwind、补成功异常handler或新CALL/RET wrapper。此次仅准备用于零凭据正常启动观察；若启动前新出现必须异常透明的实际前置，应停在该xdata/异常验证缺口交主线，不自行取窗或扩大预算。

本审核出口已满足，下一可执行任务是主线分配上述新文件给 Sol xhigh 实施，然后主线串行实际构建/owned审核。原SDK父项和D-02继续进行中；本代理没有本地Git提交，专题与审核文件由主线检查归属后单独提交并更新Project/Harness/HANDOFF。
