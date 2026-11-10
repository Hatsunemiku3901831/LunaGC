# CN 7.2 ConfigSync 八点阶段观察器

任务：`R2-CONFIGSYNC-EARLY-BRIDGE` / `R2-ROUTE-CONFIG-SELECT`。这是 [冻结审核](CLIENT_72_D02_B_SDK_AUDIT_20261110.md) 的独立实现，不替换旧 prefix/split 产物、报告或 pin。它观察一次任意 P0 尝试，可能首先被错误 caller 占用并拒绝；不保证采到 eligible caller。完整 payload、自然 Init/route/key/认证和 D-02 继续未验。

## 主线实际验收与当前出口

主线已完成最终 `source-main4` 的独立 release/locked/offline 构建、规定两轮 fresh owned、独立第三次执行、真实隔离文件布局门禁及17项Python测试。patch独立提交为 `59b6439`。最终DLL为583168B/SHA-256 `e517a6cf28a395fde0649a1c08c7f1a11b2bc6da05cac0db443b6d4a77dca286`，fixture为772608B/SHA-256 `fba3b9d304c6196b44d8cdc80bdf6d15579acafe63bcbb6d5ce34d2b5716127e`；33源闭包、105冻结pin、51导出及256B DATA均通过。16个near/far实际emitter、原指令/寄存器/栈/LastError恢复、同owner连续bridge及拒绝反例属于owned限定范围，不等于自然完整writer或异常透明。

原件根为 `local/migration72/d02-resume-20261110/b-implementation/`：`owned-main4/proof.private.json`为两fresh原PID/argv/stdout/stderr/exit，`owned-third-main4/main-third.private.json` SHA `c82d369e88b35039f76227a5f48c11af63a33f960d4263e8e0d1787697286c9d`，新 `accepted.private.json` SHA `77296f80138e094ac5a76eae167ac436e85691189246b5cad5e9ccb2b4057abd`。`main-owned-review.private.json`的11项独立审核通过，SHA `121709c477a4b9246fe6137128c7be22e3b61cd3aaffbed70da1a41900550690`。隔离布局的可变DLL是独立副本，原历史布局和历次失败保留，未回填原报告。

本轮专用helper已静态审查且主线实际 `-CheckOnly` 退出0；`helper-preparation/live1-prepared.private.json`记录五stock/carrier/13保护/portable/config与当前进程、固定端口检查，未安装或启动。计划 SHA `f7b8ca2c77d8085e45772fce715ecb3c43a97919746278ce418ac291631ce193`绑定新runId、产物/accepted、备份及恢复。新实时ready、实际90秒自然观察和正常退出后的stock恢复仍待执行。下文源码开发期间的“待主线”是阶段历史，以本节实际出口为准；unwind、自然Marshal/writer/Init/route及父项未知保留。

## 本轮自然阶段观察与恢复

本轮在新的实时ready后使用专用计划实际启动；90秒/45次metadata，observer退出0，外部collector严格30请求10415B、单读最大512B。原始256B DATA前后逐字节相同；phase5、event2、installed_mask255、facts1023、env0、failure/win32_error0。完整两EXE/五SDK原窗、47转发、callback与八个实际trampoline/zero-tail均绑定本轮模块与固定来源；进程创建、image/模块身份、末尾alive和CloseHandle一次通过。

已验事实：同一ordinary配置经原bridge/native调用，writer gate到达、parser接受、env分支及store完成且值cn-env-0；P5 app-key和P6 URL分支到达，P7为正常epilogue入口，随后匹配owner/bridge的P1证明native调用返回。**P5/P6不证明各自setter完成，P7不是RET，void返回不是业务成功**。未外读payload/reference；完整payload、Marshal完整内容、自然Init/route/key/信任/认证、异常unwind继续未验。后继只追这些尚未回答的必要合同，不重复prefix/同阶段或直接增加预算。

用户确认SDK页正常、正常退出、无报错。专用恢复helper核本次journal/backup/proxy/alias和无目标进程后恢复stock并删除本轮alias；主线独立复核五stock、SDK carrier、13保护/缺席、portable/config以及相关进程和固定TCP/UDP均通过。原数据库/运行数据保留。GS未部署，准确原因是R1完整Avatar/Scene及R3完整必要资源/入场保存和R4尚未闭合；这轮只是临时观察DLL的受控安装与恢复。

原件位于本批 `b-implementation/helper-preparation/`。主线 `main-live1-acceptance.private.json` 的15项审核通过，SHA `9ca7be02728749d5e8ac10c5cd099318b720e79348680585aab2b32c1eb76f33`；`main-live1-replay.private.json`仅消费同次30读原字节，不是新采集。独立 `post-sdk-stage-live1-inventory.private.json` SHA `7bc1939bd8463a3c5a41832773510536027001fa10d3e604790a2cca11edab62`。本轮ready已消费、PID/VA均不得作为下一轮输入。离线实现阶段的待验段落仅保留历史。

## 实现和固定来源

新增 example `sdk72_config_stage_proxy`，复用冻结 core、47 转发、ilhook 2.3.0 与 mover。新 `loader.rs` 使用两 EXE 窗及五 SDK 窗的独立切片合同，writer 连续 5422B 以 10×512+302 分块。未调用旧 loader 的 `wait_loaded/activate`，未主动装载 SDK、创建 worker、调用目标函数或安装 route/key 点。旧 `sdk72_config_inner`、`sdk72_key_input_inner` 仅为明确 dormant EAT 项。

冻结审计输入为 `local/migration72/d02-resume-20261110/b/audit_frozen.private.json`，SHA-256 `ecb9740624dfbe75425949345ddb824b7263f03c7259ba6cf75dc7c3f123ddcb`；最终审核文档 SHA-256 `9f9801c85baa897b0476641564391813836aaff715901b9b43b16930a754daed`。新 verifier 先核原 source/provenance pin，拒绝漂移；source-only 不访问 GAME_PATH 或执行 Rust。原上游检索仍是 `bounded-partial`，墙钟超限事实保持；这个明确后继不重爬已有结果。

所有新增私有材料留 `local/migration72/d02-resume-20261110/b-implementation/`，包括 `binding.private.rs`、DEF、source/artifact/owned/accepted、构建日志和新自然 epoch。它与冻结审核所列 `b-stage` 是输出目录命名的调整，未改变读取/语义合同。binding 只含固定模块/代码窗/SITE_BYTES，不含自然 payload 或 reference。

| 点 | 精确覆盖及唯一回跳 | 发布事实 |
| --- | --- | --- |
| P0 EXE `6D809B5` | **14B→`6D809C3`** | 原 name Marshal 已返回；核 caller/name 后可记 ordinary/name，绑定并串行装 SDK 六点 |
| P1 EXE `6D809CF` | 14B→`6D809DD` | 同 owner/桥 RBP 的原 native 调用已返回，缓冲尚未 free |
| P2 SDK `37894A` | 15B→`378959` | writer gate；saved BL 非零为原 parser 接受 |
| P3 SDK `378A07` | 15B→`378A16` | env 非空写路径选中 |
| P4 SDK `378A2A` | 19B→`378A3D` | 此前同 writer P3 到达才记 store completed，saved EAX 只发布已知 CN 枚举 |
| P5 SDK `378E4A` | 15B→`378E59` | 非空 app_key copy 分支选中 |
| P6 SDK `379862` | 17B→`379873` | 嵌套 parser 接受且 owner 非空的 URL 更新分支选中 |
| P7 SDK `379D5B` | 16B→`379D6B` | cookie CALL 返回后的正常 epilogue 到达，同时关闭私有 writer-open |

旧专题表的 P0 20B→C9 文字错误只由新审核解释，旧报告和 pin 保持。P0 搬移 payload-Marshal CALL，C3/C6 两条 mov 和 C9 native CALL 全部留原位。P5/P6 不发布 copy/update returned；P7 不是 RET。只有 P7 后同 owner/桥帧 P1 到达，才叫“正常 epilogue 后原 native 调用返回”。void 不当作业务成功码。

## 唯一尝试、寿命和读取账

首次原转发绑定后同步 pin self、绑定已加载 EXE，顺序装 P0/P1。每点成功立即保留 HookPoint/userdata 并发布实际 mask/trampoline，模块引用成功即进程期保留；后点失败不隐式 Drop 前点，不清除已有 held/mask。初始 phase1→2 只用条件发布，不覆盖 P0 已推进的 phase3/5/6。未证明覆盖更早线程/调用或完整生产并发交错。

P0 在任何目标数据读取前 CAS event0→1，保存私有 TID/桥 RBP，先读 `[RBP+18]`，只接受 EXE `+198035F6`；再逐字节读 RAX 的 `local_config\0`，再读 EXE `+57B4D60`。错误/short/error 即固定 reason/Win32 后 release seal event3/phase6。P2 同 owner 且 SDK 六点已装后，第一次任意 P2 在读取前先占 writer-attempt，再读 `[RBP+318]`，仅接受 SDK `+36FC80`。正确后私存 writer RBP/R15；P3..P7 只比私有关联，不读目标数据。P7 关闭 writer-open，writer-attempt 不复位，防止同一桥内第二次 writer 复用同栈地址串位。P1 不能把 event3 改成2。

| 访问类别 | 整轮上限 / 说明 |
| --- | --- |
| 参数 RPM | 16 次 / 37B：return8、最多13×1 name、cell8、writer-return8；失败计已请求 width，无重试 |
| 显式固定代码绑定 RPM | 17 次 / 6409B / 单次≤512；初始 EXE 359+46，SDK 234+81+261+6+5422 |
| 外 collector RPM | 完整 30 次 / 10415B / 单次≤512；首 DATA 拒绝、无 P0 或 partial 仅1次/256B |
| 安装入口内省 | 仅每个成功安装 entry 的 JMP 14B direct code view，最多8×14B，用于发布实际 trampoline；不验证额外窗口，不作为任意地址接口 |
| 其他安装/装载行为 | 冻结 ilhook 每点≤30B 指令 view、mover、protect/patch/restore 与模块 API/磁盘 SHA 分列；不把前三账冒称所有内存访问总量 |

256B `repr(C)` DATA 固定 schema2，保留字和尾112B全零。phase=0/1/2/3/5/6，event=0/1/2/3，mask仅低8位，八个 trampoline 在80..143。facts bit0..9 依次为 ordinary/name、native-return、writer-gate、parser-accepted、env-path、env-store、app-key-path、url-update-path、normal-epilogue、env-known-CN。bit9要求5，5要求4，3要求2；实际写入 CN0 也必须有 store/known 两位，不能由零初始化推断。私有 TID、帧、自然指针和参数读取账不导出 DATA。

## Collector 与主线 adapter

`verify_sdk72_config_stage_proxy.collect` 是唯一固定读取函数。顺序为 DATA256、47目标表376、EXE359/46/cell8、SDK234/81/261/6、writer11块、wrapper38、八个384B trampoline、DATA256。第一 DATA 必须合法且 phase5/maskFF/event2/failure0/Win320 才继续；不合格直接停止，无末尾补读。中途 short/error 或已知结构/代码不符停止在该前缀。读取后验证整桥、整 writer 除各准确 patch 外全部原样，near 仅写5B/far写14B，其余 stolen bytes 保留。

Trampoline 地址只从本轮模块 base+固定 entry RVA 的真实 JMP 推导，再与 DATA 相等；DATA 不可单独驱动读取。逐字节核共同250B前缀、原 mover、CALL/RIP/条件目标、唯一回跳及384B内全部零尾。未知 near block-encoder 扩展拒绝，不加读。共享38B wrapper 必须等于 artifact，准确 CALL 连到本 epoch EAT@49；不另读 inner、userdata/core/帧/字符串/reference。

Windows adapter 复用90秒/45次仅元数据 monitor，一次 collector。采集前后核 creation identity 和 EXE/proxy/original/SDK 实际 path/磁盘 SHA/SizeOfImage/base，最后核 alive，finally 恰一次 CloseHandle。SDK 两次均缺席可保留为拒绝/无 P0 事实，出现/消失不通过；无身份前置则0RPM。raw sidecar 仅从这次 collector 的同一读函数写出，`replay` 纯消费该 sidecar，不再次读取进程。

新 acceptance record 由主线完成实际构建、双 fresh owned、第三审核及文件门禁后封存。`--acceptance-sha256` 显式传入其完整 SHA；record 精确列 `schema=2`、`proof/production/fixture/manifest` 的 source-id 和 `sourceClosure`。adapter 重验全部实际文件、manifest/lock/DEF、当前 source closure 和两个真实完整 emitter 结果。没有新 acceptance 会拒绝；旧 accepted 不复用。

## 实际验证与剩余门禁

实现工位实际 source-only 首轮通过，证据 `source-agent1/proof.private.json`；当时28份实际传递 source closure，不能改称旧六源/86pin。后续补列 build.rs 与 ilhook lib/err/x86/x64-tests 五项真实 include，当前33源；`source-agent-final1` 留有该次纯门禁。新增 Python 17 项实际全部通过：含每 ordinal 30×short/error、各首DATA出口、保留位/枚举依赖、C3原位、零尾、禁止 DATA 任意地址、同次重放、creation/module/final alive、一次关闭及首失败原stdout/stderr保留。这些是 owned byte/file/backend 测试，不是自然目标结果。

主线已报告第一次实际 nightly release/locked/offline DLL/EXE 构建均 exit0，Rust闭包前后SHA保持：DLL 583168B / `cea98b3426a191394dff8b51525241e4b1b4bd187ce79b2faf2f11cb9a75e9e1`；owned EXE 721920B / `018ef37f9d0183c7d6f67ea5add991c6465b1e664e4257dd459bd4837cde92fa`。这是首构建身份，后续修改必须另建新证据；此段不证明 owned 运行已通过。

主线随后真实第二epoch DLL/EXE均exit0，DLL 583168B / `a88ffa48d269d8e75dd2c7d8145eef2ebdb4f2c3311e26daa11b4a514feb92d4`、EXE 772608B / `57289c826eded8dd45eec9112a64c063bacc779885117aa90e1c98c8c9c8c307`。`owned-main2` 两fresh和 `owned-main3` 新Python闭包的两fresh均实际通过；所有前epoch原件保持。最初预检stdout另经实现工位及主线独立核完整16个实际emitter，near最大283B、far最大334B、384B内零尾全等。

真实OS异线程门禁与连续 P0→原位C3/C6/C9→P1 的自有桥已经加入第二epoch并由上述真实owned执行：校验 payload-Marshal/native/free 顺序各一次、native两参数和桥第三R8参数、高64返回、原完整快照及15次/29B账。自然完整writer仍未执行。原 `writer-reused-after-p7` 反例首writer全位已置，不能有效检出串位；最新只改fixture让首writer缺env/key/url，P7后同帧重试env22和全部阶段，必须仍无新增。错误桥帧P1也新增即时event1/native-return未置断言，防止只核最终正确P1掩盖错误提前结束。该新fixture SHA `f7d3c1120d82f6775cb182a5d05cac10b2fefc62b750c1dfb7ef5e102a938b05` 等主线 `source-main4` / 新构建与双fresh，不回填前epoch。

当前 fixture 使用真实自有 executable pages 和冻结 Hooker，尝试每点 near/far 实际安装/执行/Drop，原 stolen bytes 保持，owned callee 放在原 CALL/RIP 对应的本 region RVA；与无 hook baseline 比 GPR16/XMM16/RFLAGS/原参数栈128/LastError、CALL次数/参数和高64返回。阶段用另列的 synthetic state cases，不声称跑了完整 SDK writer/parser/Marshal。各安装失败要实际触发并保留之前 mask/held/patch，再 owned Drop 恢复。实际通过与失败由主线新 fresh 结果追加，本页不凭源码标记通过。

待主线完成：最新有效反例fixture的新构建/真实双fresh、独立第三、真实隔离文件布局门禁、accepted审核及本产物新scope/备份/恢复准备。新实时 ready 前不开始自然启动。自然完整阶段也保持 `completePayloadValidated=false`、`naturalInitOrRoutePassed=false`，不发布旧 reference 等值，不关闭父项或 D-02。

异常边界仍未知：`738AD4` xdata 未取得/解释，动态 trampoline 和搬移 CALL 的异常栈透明性未验；不新增猜测 unwind/EH、CALL/RET wrapper、RET替换。YMM/ZMM/x87/MXCSR、生产完整并发和GC边界不由普通 Win64 owned 晋级。若出现必须异常透明的实际前置，停在该缺口交主线。

## 主线复现入口

```powershell
python -B -m tools.verify_sdk72_config_stage_proxy --prepare-build-inputs --output-directory local/migration72/d02-resume-20261110/b-implementation/source-main1
# 主线按本轮真实配置设 CARGO_HOME / RUSTUP_HOME 与 GAME_PATH。
$env:CARGO_TARGET_DIR = Join-Path (Get-Location) 'local/migration72/d02-resume-20261110/b-implementation/target'
$stageDef = (Resolve-Path local/migration72/d02-resume-20261110/b-implementation/source-main1/config-stage-proxy.def).Path
& local/cargo/bin/cargo.exe rustc --manifest-path patch/Cargo.toml --example sdk72_config_stage_proxy --crate-type cdylib --release --locked --offline -- -C "link-arg=/DEF:$stageDef"
& local/cargo/bin/cargo.exe rustc --manifest-path patch/Cargo.toml --example sdk72_config_stage_proxy --crate-type bin --release --locked --offline
python -B -m tools.verify_sdk72_config_stage_proxy --client $env:GAME_PATH --production local/migration72/d02-resume-20261110/b-implementation/target/release/examples/sdk72_config_stage_proxy.dll --fixture local/migration72/d02-resume-20261110/b-implementation/target/release/examples/sdk72_config_stage_proxy.exe --run-owned --output-directory local/migration72/d02-resume-20261110/b-implementation/owned-main1
python -B -m unittest tools.test_verify_sdk72_config_stage_proxy tools.test_observe_client72_config_stage_proxy -v
```

每步须核真实 exit code，失败保留，新轮另选输出，不覆盖旧证据。自然 adapter CLI 是 `python -B -m tools.observe_client72_config_stage_proxy --client ... --production ... --acceptance ... --acceptance-sha256 ... --output ... --user-ready`，只有主线完成全部前置并取得新 ready 后运行。正常退出、OS核目标结束后只恢复该轮精确 stock 和新建 orig alias，再核五stock/SDK carrier/13保护/配置及进程端口；本观察不部署 GS。共享 Project/Harness/HANDOFF 与 Git 由主线统一维护。

`acceptance_record(proof_path, production)` 纯生成新record，要求当前33源/固定pin/manifest/lock/DEF/产物与两fresh原PID/exit相符；主线独立审核后才写为新 `accepted.private.json` 并封SHA。每个 `--run-owned` 进程在判定前保存 argv/cwd、真实PID、stdout/stderr原字节及hash、exit/timeout/launch-failure元数据。拒绝或超时不删除、不覆盖、不中途重置计数。adapter还核这些原件及其source-id。

在忽略目录用真实固定五stock、准确 `Astrolabe_orig.dll` 和本新proxy构成隔离文件布局后，使用上述自然 CLI **加 `--preflight-only`，去掉 `--user-ready`**，指定新的输出。这个模式只跑真实文件、PE/EAT、接受source/artifact和代码窗门禁；不构造Windows backend、不启动程序、不读进程内存。不能用mock文件哈希代验，也不为试门禁直接安装进游戏目录。
