# CN 7.2 同调用原锁内 comboLogin 分类观察器

任务 `R2-CONFIG-ROUTE-WRITE`，归 `R2-ROUTE-CONFIG-SELECT`。实施合同为 [五点后继规格](CLIENT_72_SDK_STAGE_NATURAL_SUCCESSOR_20261110.md)，SHA `ddea93c0ecf709dc15e58ea89c84dc25fcf699f94fc506e491b4bb630a03b3e7`；私有 implement-bridge SHA `1b1405fbd895eac3306d983fd9b9196e35f78228ea5817f4d4e085c35baa4b94`，38检查 projection2 SHA `9c730dea9203ab1abc7dd53d79637c66850393a42ae405c297bf79b879dc0340`。旧三点草稿不作实施依据。

本工位交付独立源码、source-only和人工Python检查。**尚未执行本产物的Cargo、EXE/DLL、真实owned、安装、客户端或自然map观察。** 不由源码命中或人工模型声称ABI/原锁/正常返回通过。真实构建、两个fresh owned、独立第三、真实文件门禁、accepted、实机及恢复均归主线。

## 主线实际构建与离线准入

最终main2已实际独立release/locked/offline构建DLL和EXE，36传递源/147冻结pin、24项Python、规定两fresh owned及独立第三、10个near/far实际emitter、51 EAT/256B DATA和真实隔离文件门禁通过。DLL 587776B/SHA `3705960dfbd605610a4f48278a658597fe6e8daa3fcc0d7c2bece29fa4de27b3`，fixture827392B/SHA `6cd63c1d036e569c93eb1bd5122e5c148ff0f84d0fcb226d4743374045e70808`。普通Win64寄存器/栈/LastError、高64返回、原unlock一次与清理链、真实RSP+98/1A0和锁内完成分类均属于自有页/synthetic map限定验收，不是执行完整SDK writer/parser或真实SDK锁。

原件根为 `local/migration72/d02-resume-20261110/b-route-implementation/`：`owned-main2/proof.private.json` SHA `b95bdbf49da01aecb0b604d8afb3eef284d2ae4fcd423f11c81cc18c0eb7fa92`；独立第三 `owned-third-main2/main-third.private.json` SHA `d60acd74926354c1f3aae592d6896449273c851a568400998b6043f13531d424`；新 `accepted-main2.private.json` SHA `979cc7ac17483af8c6e268e64c965502c531ffda151096a406b1354dbd2e4697`。11项主审 `main-owned-review-main2.private.json` SHA `6bde1a88a9f9cb0e7f436c17389d3c8ec27aad075f2e3afcb4adfddf3a47d48d`。初版EOF空行检查未拦提交，主线修正后新目录完整重建/两fresh/第三/门禁，main1和原草稿保留，不回填旧proof。patch提交为 `7e83871` 与 `85dbaf9`，父仓库引用由主线核对。

本轮专用helper静态审查及主线实际CheckOnly退出0，五stock/carrier/13保护/portable/config匹配，相关进程/固定端口空，未安装启动；plan SHA `ced3d022220bc073d0b25a309cc7890868564bbb909bc67b6593c4fa4c07c60f`。下一步只在新实时ready后进行90秒零凭据自然观察，正常退出后按本轮journal恢复stock。旧stage/current-manifest已随新example变更而不再当前准入，历史Cargo快照dff85e…单独保存，新accepted独立绑定当前manifest16846a…，未放宽旧gate。

下文实施工位“未执行”是交回时状态，以本节为准；自然map、getter采用、Init/route、认证、异常透明与D-02仍未验。R1/R3/R4前置不足，GS未部署。

## 来源与实现范围

新增 example `sdk72_config_route_proxy` 及 loader/observer/map/fixture/assembly，新 verifier/adapter/两个测试和本专题；输出统一在 `local/migration72/d02-resume-20261110/b-route-implementation/`。原 stage/key/core/table、旧工具/accepted/raw不改；复用原47转发、冻结ilhook 2.3.0及mover。原 config/key inner保持明确dormant，新state为EAT@48 DATA、唯一active route inner@49，dormant分别@50/@51，精确51项。

唯一新增原码为 `380E60..380FA0` 320B / 87指令，SHA `29c2a60f06dd4a43c84ae595acc887e41b66508c7257fa49bf91a0a2890bed88`。原pdata `600e3800a00f3800949c7300`，SHA `f459ab0371029c1ee9c2b37a34c0bad35028deacfb538941367daa155ce91cfb`；只固定范围和unwind RVA `739C94`，没有xdata。外writer仍为5422B，SHA `ac3c851aa14b265d5db1874325fc0abd4af46f99fbf614e9193edb7c936bb42b`。

历史stage accepted `77296f80138e094ac5a76eae167ac436e85691189246b5cad5e9ccb2b4057abd`、原live1/raw及其来源逐项冻结；它们不作新自然结果。主线在追加新example前保存 `historical-stage/Cargo.toml` SHA `dff85e2794449214859a88dbc5f013c72b9be9d6ab250210de38c3307a309dce` 和 lock SHA `8482fc5d3e86ac5416038e446431e729a5e55c338d8824407f9aab878c323b2f`。新verifier用这两个只读快照核历史身份，**不调用旧accepted_proof要求当前Cargo等于旧版，不放宽或改写旧gate/proof**。当前新Cargo作为本任务实际build input另记。

新binding为 `binding-route.private.rs`，只含固定代码/owned片段及八个已审host+combo路径的字节常量，没有app_key或自然未知URL。早期未构建的 `binding.private.rs` 与 source-agent1/2/3 原件保留，不覆盖它们以制造同epoch。最终source闭包/pin数量由新prepare实际清单给出，不能沿用stage的33/105。

GitHub增量由主线交Terra max按Sol max中文计划完成：一次公开heads请求，唯一main仍旧tip，无新有价值代码差异、不clone。GPLv3及only/or-later边界未闭，不整包引入。manifest `38eaff793bd5afcec631c3c1cf18d8891d7b8e54f8d0125820526ce638b31385`、ledger `116b6e6778b879bc4485edc2b2396c8cbc60eb2b4a5f90ec11e6075ef4bacb48`、result `85df625e7089347d4768437c893488b577a7c965542833a5f3fb5e46197604c6` 一并冻结；原partial/墙钟超限和本轮初次0网络打印错误均保留。

## 五点、关联与保留的未知

| DATA索引 | 位置 / 完整stolen bytes → 回跳 | 观察事实 |
| --- | --- | --- |
| 0 P0 | EXE `6D809B5` / 14B→`6D809C3` | 首任意P0占用后，核原caller/native name/cell；同步绑定已加载SDK、装后3点 |
| 1 P1 | EXE `6D809CF` / 14B→`6D809DD` | 同owner/bridge RBP的原native调用返回，终结scope |
| 2 P2 | SDK `37894A` / 15B→`378959` | 首任意同owner writer先占尝试，再核caller `36FC80`，保存RBP/R15；BL非零仅parser位 |
| 3 P7 | SDK `379D5B` / 16B→`379D6B` | 同writer正常epilogue到达并关闭writer-open，不是RET |
| 4 M0 | SDK `380F5A` / 15B→`380F69` | 原unlock之前，在所选writer的原锁内完成有界map查询 |

P0仍是14B，C3/C6 mov与C9 native CALL原位。M0固定 `498d4e10e8e1d90d0090488d4c2448`，包含原 `CALL45E944`；只由原mover执行一次，不主动call unlock/getter或替代返回，不新增CALL/RET包装。P0/P1先安装，P0再绑定SDK及装P2/P7/M0；每个成功HookPoint/userdata/module hold即时进程期保留，失败不回滚/伪清mask。phase1→2条件发布，不覆盖已推进的phase3/5/6。

首次任意P0/P2均在任何相关目标读取前占尝试。首次任意同owner M0在writer-open、帧、caller筛选前先占map-attempt；须同时满足原RBP等于writer RBP、原RSP+1A0等于该RBP，再唯一读 `[原RSP+98]`=SDK `379882`。其它TID不占尝试且不读。R14是map对象指针，callee的R15已清零；不深读Config+860或用R15冒充原Config。首错即固定reason/Win32后seal event3/phase6，后续千次正确事件也不补采，P1不能把失败改成功。

P7关闭后不重开writer；首writer无URL，之后同RBP/R15的writer/M0也不能串位。查询在M0 callback内结束，不保存自然节点/字符串指针给P7/P1/collector。P7后同owner P1仅说明正常epilogue后native调用返回，void不是业务成功。

没有新env/app_key观测。已知CN数字只是资源route类别，不是本轮stored env或托管选择；`naturalComboRouteUseObserved`、`storedEnvObserved`、`naturalInitOrRoutePassed`、`completePayloadValidated`及`exceptionUnwindVerified`保持false。

## 锁内map算法与DATA

新 `map.rs`直接移植冻结 `combo_value/bounded_string`：descriptor16、sentinel32，unsigned-byte单路径至多32 node；每node只读links/isnil+key header64，heap key≤64。仅命中 `comboLogin` 才读value header32、heap value≤256。count/length/capacity用完整QWORD；count≤305，inline capacity恰15，heap pointer非零且length≤capacity/上限。循环、超深、坏头、不可读、short/error/超限均失败，不冒称absent。锁内用固定栈buffer、原始整数/切片比较/atomic/RPM；没有分配、磁盘hash/绑定、格式化、日志或网络。

DATA独立schema3/256B：前80B沿固定module/hold/phase/error/event布局，72为routeClass，76保留0；5个trampoline在80..119，尾136B全零。facts bit0..9依次为ordinary/name、native-return、writer-caller、parser、map-caller、lookup-completed、key-present、value-nonempty、known-CN-route、writer-epilogue。known→nonempty→present→lookup→map-caller→writer-caller→ordinary；parser/epilogue需writer-caller，native-return需ordinary。

routeClass=0 not-read、1 absent、2 empty、3 unknown，`0x100+k`为八CN常量索引。unknown不导出原文、地址、长度、hash。absent/empty只给静态getter将fallback的预期，不宣称原getter返回或route采用。

| 整轮独立账 | 上限 / 顺序 |
| --- | --- |
| P0/P2参数 | 16读/37B；return8、最多13×1 name、cell8、writer return8 |
| M0含caller | 69读/4440B / 单次≤256；caller8、descriptor16、sentinel32、32×(node64+keyheap≤64)、命中value32+heap≤256；是保守上限，不声称每次都读满 |
| 固定代码绑定 | 15读/6408B / 单次≤512；EXE359/46、SDK dispatcher261、writer 10×512+302、map writer320 |
| 外collector | 25读/9262B；DATA256、targets376、EXE359/46/cell8、dispatcher261、writer11块、map320、wrapper38、5×384 trampoline、DATA256；首DATA不合格只1/256 |
| 安装行为另列 | 原ilhook每点≤30B decoder view，成功entry JMP内省≤5×14B；mover/protect/patch/module API/磁盘hash不冒称前三账总访问 |

所有失败计已请求width、无重试/跨caller重置。固定绑定在P0，M0不做这些工作。新内存绑定不含export/engine/member-tail，不宣称旧full-chain范围。

## Verifier、adapter与待主线执行的owned

Collector只接受合法schema3、phase5/mask1F/event2/无错误的首DATA后继续；合法terminal/no-map也属有效负结果，不重采。每窗读取后即核，整桥/完整writer/map writer除准确5/14B patch外全部原样；不要求覆盖剩余bytes填NOP。trampoline读取地址从固定entry真实JMP推导，再与DATA相等；不能用DATA作任意地址入口。逐byte核250B前缀、mover/CALL/RIP/唯一回跳及384B零尾，wrapper必须精确38B且CALL到本epoch@49。未知编码/超过384拒绝，不加读。raw只来自同一25项固定读函数，不含自然map bytes，纯replay逐字段相同。

Adapter延续90秒/45次仅metadata monitor，一次collector；首末creation identity及四模块path/disk SHA/SizeOfImage/base相同、最后alive、finally一次Close。身份前置不满足0RPM；SDK缺席可保留为首DATA拒绝/无P0结果，不延长等待。`--preflight-only`仅核真实隔离五stock/proxy/orig文件布局，不构造Windows backend或启动程序，不要求ready；自然启动另须新实时ready。

自有fixture已写下列必验项，但本工位未执行：5点near/far共10个actual emitter；GPR16/XMM16/RFLAGS/栈128/LastError/高64与无hook基线；原CALL次数/参数和Drop；连续P0/C3/C6/C9/P1桥；原七push/3E0 writer与五push/70 map callee构成真实RSP+98、+1A0的mini链。mini链在自有页保留原M0 unlock与其后3个cleanup/cookie CALL/原epilogue/RET，目标callee及锁token为自有测试体；绝非SDK实际mutex/parser/writer执行。核分类在unlock之前完成，unlock一次且RCX=R14+10、返回高64；随后P7关闭。有效P7复用/错误M0先占/其它TID/错P7/帧反例、map每69 ordinal short/error、各安装序号失败保留均在新fixture中断言。

实际执行工位已运行24项人工Python字节/backend检查，`manual-python-agent1`保存原输出和exit0；包含map69×short/error、collector25×short/error、QWORD/边界/循环/非目标不读/unknown零泄露、首DATA各出口、漂移/退出/Close和同次replay。初期模板残留的8项地址数组/旧adapter import导致的失败保留为开发诊断，纠正后通过；临时owned文件自动清理，没有覆盖旧封存原件。新命名/source种子修正后的 `manual-python-agent-final1` 再次24项/exit0，20.203秒；`source-agent-final1` 实际source-only通过，**36份传递source、147项冻结pin**。最终 `binding-route.private.rs` 23207B / SHA `03a1eb6e4d987e18e096a2ec67a7b30407050246325b6d3b375894ad10c717de`；DEF SHA `7f2fc741ecf1c56f2bd4ae9fe35b61a6fe4f544e30c12525fd868e5f68b6a656`。清单见 `implementation-frozen-agent.private.json`。这些结果均不替代上述真实owned。

两个fresh与独立第三均保存原argv/cwd/PID/stdout/stderr/exit及hash，首失败不丢。新accepted由主线在全部门禁通过后通过 `acceptance_record()`生成并封SHA；现无新accepted或自然许可。原stage current-manifest gate有意失效，不能为重用放宽。

## 主线准确命令与停止条件

```powershell
python -B -m tools.verify_sdk72_config_route_proxy --prepare-build-inputs --output-directory local/migration72/d02-resume-20261110/b-route-implementation/source-main1
# CARGO_HOME/RUSTUP_HOME和GAME_PATH由主线按本轮配置核定；新独立target。
$env:CARGO_TARGET_DIR = Join-Path (Get-Location) 'local/migration72/d02-resume-20261110/b-route-implementation/target'
$routeDef = (Resolve-Path local/migration72/d02-resume-20261110/b-route-implementation/source-main1/config-route-proxy.def).Path
& local/cargo/bin/cargo.exe +nightly rustc --manifest-path patch/Cargo.toml --example sdk72_config_route_proxy --crate-type cdylib --release --locked --offline -- -C "link-arg=/DEF:$routeDef"
& local/cargo/bin/cargo.exe +nightly rustc --manifest-path patch/Cargo.toml --example sdk72_config_route_proxy --crate-type bin --release --locked --offline
python -B -m tools.verify_sdk72_config_route_proxy --client $env:GAME_PATH --production local/migration72/d02-resume-20261110/b-route-implementation/target/release/examples/sdk72_config_route_proxy.dll --fixture local/migration72/d02-resume-20261110/b-route-implementation/target/release/examples/sdk72_config_route_proxy.exe --run-owned --output-directory local/migration72/d02-resume-20261110/b-route-implementation/owned-main1
python -B -m unittest tools.test_verify_sdk72_config_route_proxy tools.test_observe_client72_config_route_proxy -v
```

每步核真实exit，失败留证，新轮用新目录。主线追加的唯一Cargo声明为该example的cdylib；lock保持。accepted/真实隔离文件门禁全过后再准备独立scope/stock与保护备份/恢复脚本和新ready。自然adapter接口为 `python -B -m tools.observe_client72_config_route_proxy --client ... --production ... --acceptance ... --acceptance-sha256 ... --output ... --user-ready`；文件预检使用同接口加 `--preflight-only`、去掉ready。

原xdata `738AD4`/`739C94`、finally/异常cleanup及搬移CALL异常栈透明性未知；不注册猜测EH/unwind、不用host unlock/默认空map/业务成功兜底。若异常透明成为实际前置或目标异常/完整性拒绝，停在该缺口交主线。自然env/app_key、原getter采用、真实route/HTTP、RSA/HMAC/窄信任/认证和D-02仍未关闭。不部署GS。正常退出、OS确认目标结束后，只恢复本轮精确stock和新建alias，再核五stock/carrier/13保护/config/进程端口；共享Project/Harness/HANDOFF及Git由主线维护。

## 本轮自然锁内map观察与恢复

本轮全新实时ready后，独立受控helper核输入/保护/进程端口、备份并安装已验main2产物；正常90秒/45次metadata结束后只执行一次固定collector。用户新反馈页面正常、无报错并已完全退出；主线重新核进程结束，再经本轮恢复UAC还原stock并移除owned alias。没有提交账号或凭据，没有启动GS。

同一256B DATA首末完全相同：schema3、phase5、event2、mask31、failure/win32_error0、facts1023、route_class256。普通name/caller、parser accepted、同owner writer帧与M0 caller成立；lookup完成、comboLogin存在且非空，其完整值属于固定 **cn-route-env-0** 类别；P7正常epilogue后P1同owner native调用返回。完整五点代码/trampoline/零tail、47转发槽及本次进程创建/模块身份均与原件绑定。

这只是原锁内写入结果。当前轮没有读取stored env，不与上一八点轮的DWORD0 store拼成同一epoch；route类别0也不是托管env选择。完整payload、自然Init/实际getter或route采用、请求/认证与exception-unwind继续未验，void返回不作为业务成功。

外采实际25读/9262B、单读最大512，全部成功且按序；原raw逐读重放与binding完全一致。主线16项独立审核全过，原件在本批 `b-route-implementation/helper-preparation/`：

| 原件 | 完整SHA-256 |
| --- | --- |
| `live1.private.json` | `62cc6a700e3e811250e34c3150bdceaca3cf35fd4032277dbecf5a5f828b1b84` |
| `live1.private.json.reads.private.json` | `7de0a6a0967ac3d6f1e4876a00c70f62e5c83ea3cded7b9f8e830b188cb1b182` |
| `main-live1-replay.private.json` | `765ed13656aed720081e33a93f707ff7b08470f47cc7241b542daea218c1e536` |
| `main-live1-acceptance.private.json` | `ff65adf58a0dfea4ece85fa6a468748b89f4812aaecfd1bd2fd14b01e592b09f` |

本批前缀为 `local/migration72/d02-resume-20261110/`。`post-sdk-route-live1-inventory.private.json` SHA `45a94c3d2111ce0528a7b6f0b5249c3aa44a2ea9a9ab9fdf4304e30855783fd1`：五stock/carrier/13保护及portable14配置一致、alias缺席、相关进程和固定TCP/UDP端口为空。原运行JAR、原数据库与运行数据未操作。ready及正常退出确认均属本轮新回复，不复用历史PID/地址/现场。

明确下一分界见[托管必要消费者](CLIENT_72_SDK_ROUTE_CONSUMER_SUCCESSOR_20261110.md)：成功账号回调的RequestComboLogin40108读取的是托管URLConfig.Instance.comboLogin，原Concat追加单个 `?` 后交PostRequest33107，不读取此SDK native map。该89项静态结论经主线第三20425B同原件验证；证明 `b-route-consumer/main-review.private.json` SHA `637bd2793f3c93936b1eb47eb5045f09f6ead23b420ee09609c4817b1463db03`。自然托管URL/env与真正请求采用另验，不能为套用旧classifier而剥query或放宽匹配。
