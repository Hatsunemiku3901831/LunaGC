# R2-ROUTE-CONFIG-SELECT / POSTREQUEST-ENTRY（2026-11-11）

## 有界研究计划

本轮只研究准确托管消费者 `MiHoYo.SDK.NetworkManager.PostRequest` 的入口合同。共享状态与提交由主线负责；本工位不改生产、补丁、配置、资源、旧材料或旧门禁。

| 项目 | 合同 |
| --- | --- |
| 目标 | 定出 owner4362/method33107、AOT VA `15992C890` 的唯一完整 body/pdata，回答入口最小 hook 覆盖能否保留普通 Win64 call/return 身份，并给出 RDX 最终 String 的一次有界观察与合法本服触发前置。 |
| 已有 | LoadLocalConfig2481B/readOnly 选择、40103 成功回调→40108、原 `19801312` CALL→33107/返回 `19801317`、单个 `?` suffix 及最终 URL 纯分类器已验；不重做这些子项。 |
| 准确缺口 | 33107 完整原 body/pdata、入口覆盖/搬移、普通返回及 String 生命周期；原 `[RSP]` caller 的观察合同；能由合法本服流程触发成功 SDK AccountModel 的已证前置尚待区分。 |
| 方法 | 先核当前 `.env/.env.local` 所解析 GAME_PATH 与 profile 五输入完整 SHA，再核原 metadata/signature、AOT 两 QWORD、唯一原 E8 caller；只用 exception 目录的唯一 exact/containing 记录定 extent，然后读取这一完整 body。code 上界8192B，超界或身份歧义先报告，不猜 end、不取邻 callee。 |
| 依赖 | 固定 profile/签名、40108 原 bytes、既审 String class/长度/字符来源、既有 ilhook/ABI 代码和本地 MDK/route/key/信任研究；仅复用已固定 GitHub 来源/结果。本次是同一 consumer 小续项，不为精确 PE 窗重爬；新大复用目标须另列中文计划交主线安排 Terra。 |
| 范围 | 指定本页、`tools/client72_sdk_postrequest_entry_20261111.py`、必要同名测试，以及私有 `local/migration72/d02-resume-20261110b/b-postrequest/`。xdata、邻 callee、新目标 CPU/live 读取均0；不启动客户端/调试器/API/DLL/JVM/DB/服务，不构建、不安装、不提交/切分支。 |
| 产物 | 固定原 bytes/pdata/AOT/caller 的完整 SHA 清单、可复现提取/离线审核、入口最小覆盖与普通返回推导、只输出类别的首次尝试/预算/生命周期规格、合法本服 trigger 的准确下一前置。 |
| 验收 | 唯一 extent先于body读取；逐指令原 bytes 完整拼接；原 CALL 的目标与原 return 分别验证；普通透明返回、未知 EH/unwind 和未发生的自然事件分列；不把pure分类/stock字段/静态调用当请求或认证验收。 |
| 停止 / 恢复 | 固定身份不符、extent非唯一/超界、decode不完整立即停止新提取并留下诊断；一窗足以给最小实现或准确输入即交主线、释放max。不为缺触发追网络/JSON/线程树，不再SDK页面90秒碰运气，不要求官方登录，不伪ret0或对象。后继hook实现/owned/现场由主线审定安排。 |

## 当前状态

**唯一原窗已取得，入口普通 call/return 的静态条件足够形成最小实现规格；合法本服认证触发仍未闭。** 33107 入口的14B ilhook覆盖实际为16B，只有八个push和一次栈分配；不搬原40108 CALL，所以可保留原 `[RSP] == B+19801317`。本函数正常出口是恢复栈后的尾跳，尚无同步返回观测。本轮没有实现/构建/安装hook，新增客户端验收0。

当前五输入由本checkout的 `tools/game_path.ps1` 新子进程实际解析，再按固定profile逐文件重新核大小及完整SHA；全部匹配。五输入仍为EXE `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`、global metadata `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`、startup metadata `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`、SDK `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25`、stock Astrolabe `87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442`。机器路径及调用记录仅留私有根。

## 唯一extent、原caller与完整身份

`tools/client72_sdk_postrequest_entry_20261111.py` 复用已固定SHA的旧PE文件映射器，先核profile/签名/原40108 bytes与AOT，再遍历exception目录，写出唯一exact/containing诊断并检查8192B上界，之后才提取这一完整body。没有把邻方法当end，没有提取邻callee或xdata。全文件SHA读取包括PE的原始文件内容；这里的“xdata0”专指没有新增xdata窗口提取、解码或展开验证。

| 原件 / 属性 | 本轮实际值 | SHA-256 |
| --- | --- | --- |
| owner / method | 4362 `MiHoYo.SDK.NetworkManager` /33107 `PostRequest`；签名六参41903..41908 | 固定签名源见私有41源清单 |
| AOT pointer cell | `QWORD[1422F98E0+70] = 1428A54D0`，原8B | `68f5f1908ac9f789c3fc0b5e71c5b8af00962605561f7b8ea2cc4718f00a83da` |
| AOT slot | `1428A54D0+33107*8 = 1428E5F68`，原QWORD=`15992C890` | `a6acdf8759cae4400774133430917321b182fa569f9ee019a0d186eec1e9e617` |
| 唯一body | RVA `[1992C890,1992C9C6)`，310B /79条完整指令 | `7994592283e5d24d2ebb66d4e5c891d4702dad902ba1dfbb0806d2ec9c507809` |
| 唯一pdata | RVA `1B233488`，12B=`90c89219c6c9921988394105`；unwind RVA=`5413988` | `2d5e615593d1bf523556be98b40edc1d62a335de993663887a130754abc66536` |
| 原40108 caller | RVA `19801312`，原5B=`e879b51200`；相对解码确到33107，原return=`19801317`；与冻结328B同一原片段逐byte相等 | `e93f40cc303afd39dd5c825e11191626e24a13edc5fe3b6745752113b4f3611c` |
| 提取报告 | 私有 `window-original/extraction.private.json` | `063f0d5846111dc851f181f1865969d55f166e93d499577d9f98ca8f3b46f4f1` |

以上是磁盘静态证据，地址中的B在后续现场必须换成当轮已绑定实际EXE base，不能直接使用首选VA。

## 最小覆盖与普通返回

入口从 `1992C890` 依次是 `push r15/r14/r13/r12/rsi/rdi/rbp/rbx`（12B）与 `sub rsp,68h`（4B），完整16B原bytes为 `4157415641554154565755534883ec68`。当前冻结 `ilhook 2.3.0 Hooker::new` 固定最小搬移14B，因此实际搬移这16B，唯一回跳为 `B+1992C8A0`，再执行原 `movaps [rsp+50h],xmm6`。即使entry最终写near E9，当前ilhook仍按14B政策解码，不能把它降格成只搬6B；6B只是理论5B跳转的完整指令下界，不是现有实现合同。

这16B没有CALL、分支、RIP相对指令或数据引用。`JmpBack` 保存原CPU上下文→同步观察→完整恢复→执行搬移指令→JMP回原位置的正常流程，不改原40108的 `19801312` CALL，不新增/替换它在callee入口栈上的原return。静态上可保留普通返回身份；这是实现规格，不是新trampoline/emitter或真实进程已验。

令 `S0` 为原33107入口CPU RSP。普通Win64 CALL使 `S0 mod16=8`，`QWORD[S0]=B+19801317`。八push加68h使原函数内部 `S1=S0-A8h`，`S1 mod16=0`；其后原参数装载为：

| 原body读取 | 回到入口S0后的含义 |
| --- | --- |
| `1992C8B1: [S1+E0h]` DWORD | `[S0+38h]` retryTime；原caller写3 |
| `1992C8B8: [S1+D8h]` FLOAT | `[S0+30h]` timeoutSecond；原caller写5.0 bits |
| `1992C8C1: [S1+D0h]` QWORD | `[S0+28h]` extHeaders；原caller原样转发 |

原 `1992C8AB` 将入口RDX保存R15，`1992C987` 再把R15交RDX，`1992C990` 调原虚槽 `[this.class+F8h]`。正常分支在 `1992C9A1..C9B4` 恢复XMM6、加68h、反序pop八寄存器，恢复S0；随后 `1992C9B6` 以E9尾跳到原VA `141456230`。**33107完整body没有RET。** 这条尾跳不另压栈，原 `[S0]` 保留；若其后继普通返回，会消费原return回40108。后继body本轮未取、未执行，不给它猜方法名、HTTP完成或成功语义。空对象/失败分支仍到原 `1404C2BE0`，不补成功返回。

入口观察足以报告“这个原PostRequest已经进入并消费了这次原URL参数”；它不能报告 `postRequestReturned`。若之后需要同步返回事实，另立准确返回观察合同；不把原 `19801317` 这个已知地址当成已经到达。异步发送、重试、回调及HTTP/账号结果继续另验。

## RDX String与一次入口观察合同

原40108在 `19801290` 调String.Concat，`19801295` 保存RAX到RSI，经过其原分配/包装流程后，在 `19801309` 把同RSI交RDX；33107随后保存为R15并在原虚调用前转发。这里是**原调用期间借用的实参**，不需要从旧heap地址追对象。本轮没有证明GC pin/不移动/全线程扫描，也没有执行IL2CPP GC；不得把stock v2两遍相同或自有假String当作GC所有权证明。

最小实现只在原入口的同步callback内使用该borrowed RDX：不缓存指针、不跨线程/异步读取、不等到原调用之后再取字符、不调用Marshal/String/目标分配器/JSON或任何目标托管函数。分类表、状态和固定栈buffer在安装前就绪，callback只做必要原内存复制与纯bytes比较；分类结束立即丢弃副本，原参数不变。这不会主动加入目标托管调用/GC safepoint；原运行时的完整GC/异常保证仍未验，不引入新pin、resolver、补页或伪对象机制。

以下为交Sol xhigh的**待实现**单入口规格，不是现有RPM/observer预算的扩展许可：

1. 在任何目标内存读取前先CAS占用唯一全局尝试。后续任何33107进入、重入或其它线程均透明经过、0参数读；不是“先过滤正确caller再占尝试”。保存/恢复LastError包住整个callback。
2. 只读保存的 `Registers.rsp` 所指原 `[S0]` 8B，须精确等 `B+19801317`。错caller/失败立即seal，不给后续命中机会。此身份只证明40108这个原callsite，不证明40108由40103成功分支进入；没有必要不加第二个上游点。若业务确需40103身份，另审40108入口 `B+197FFE96`、同TID及同一真实栈帧关系，不能仅凭同线程拼接。
3. 原RDX为null直接固定拒绝；否则读取fresh `QWORD[B+5886040]`（8B）为本轮String类型参照，非零后读取 `QWORD[RDX]`（8B），必须原64位精确相等。不沿class层级、tag mask或旧String实例猜类型。
4. 读 `DWORD[RDX+10h]`（4B），按原int32处理；只对65..70 code units读 `RDX+14h` 的恰好 `2*L` bytes（最多140B）。其它长度不读字符，只给固定unknown；不读尾NUL、monitor、body、callback或headers。String class/cell与字段来源直接复用[已审类型/布局](CLIENT_72_SDK_MANAGED_ROUTE_STOCK_SPEC_20261110.md)，这里不扩大旧stock采集器语义。
5. 按[已验纯分类器](CLIENT_72_SDK_FINAL_ROUTE_CLASSIFIER_20261110B.md)的七直接来源和八个完整base加**单个**`?` 的UTF16LE原bytes比较，输出固定 `managed-final-request-cn-env-k` 或unknown。原bytes不归一化、不剥query、不prefix匹配；Rust实现须从固定源生成/复核常量并与该纯分类器独立fixture对照，不能把Python运行时带入callback。

单次参数读取最坏账为 `8+8+8+4+140=168B`、5次；失败按请求width计账，无重试/末尾复读/补页。caller不符最多1次8B。地址与width在单一读取入口校验，内部不重复校验已确定类型。参数计数/bytes只在私有callback内部用于限额，不发布会间接泄漏unknown长度的逐事件计数；外部只发布固定终态、类别和“原caller/入口通过”事实位。未知URL、长度、hash、指针、正文、账号、token、签名与headers均不落DATA/日志/raw。

## 实现前仍须通过的普通ABI与寿命检查

固定依赖 `x64.rs` SHA `1b1350b1fc6b0a2cac0726bd3765c1dbc8bd90450f99439adca02ba208a9e8a7`、`move_inst.rs` SHA `f98e6d9f15faeaab12e00e81c912778b4f8e789f53058b5d3547e3abdcd30720` 与现有wrapper/loader源已收入41源清单。原ilhook通过 `push rsp` 保存hook前CPU RSP，JmpBack的内部CALL不改变保存的S0。已有[自有ABI](CLIENT_72_SDK_KEY_INPUT_ABI.md)两fresh及主线独立重建证明保存RSP/GPR16/XMM0..15低128位/RFLAGS/栈/LastError；原报告 `d8350d14ef9309295c4c803405de7008bced8aa5235c1c217af403e51759b815` 只覆盖其合成NOP函数，**不能自动验收本次16B prolog**。

后继明确实现只需新独立opt-in产物的一点入口，不复制五点SDK observer或开启其它旧模块：

- 复用透明47转发/已验loaded-module binder的来源与失败行为，重新冻结新manifest、artifact、导出表和code binding；实际EXE path/base/SizeOfImage/disk SHA与本轮身份相符。安装前显式code绑定只需原310B和原40108 5B CALL，合计2读315B；16B入口已包含在310B中，不重复RPM。String固定cell归属同EXE；ilhook自身30B decode访问/patch/protection另账，安装后实际entry/trampoline绑定由新emitter产物定准确大小，不能套旧297B。绑定、ilhook访问和5次参数账分别登记，不把其中任何一个账当旧预算。
- 新owned fixture必须真实执行这段原16B的near/far搬移，独立assembly核保存RSP等于原entry CPU RSP、`[S0]`仍为准确return、所有GPR/XMM0..15/RFLAGS/128B自有参数栈/LastError与无hook基线一致；原合成callee只执行一次并正常返回高64位值。还要用本body的原栈分配/反序恢复与尾跳形状验证原caller返回，而不是只调用callback函数。不得把执行真实SDK、目标GC或异常展开夹具混入此owned许可。
- callback复用现有assembly外壳：自备Win64 shadow/对齐并保存volatile XMM4/5，ilhook保存XMM0..3，Rust按普通Win64保持XMM6..15；不先用Rust破坏volatile状态再采快照。错误caller、首次错误后正确入口、重入、全部读取ordinal失败/短读、null/class/未知长度及unknown零泄露均有界验证。新数据状态只允许入口事实，没有伪returned/HTTP/auth标志。
- HookPoint、userdata、分类表与module引用安装成功后立即保留到进程寿命；失败不释放仍被callback引用的对象。沿用自有验证过的正常退出→OS目标消失→恢复stock方式，不在目标还执行时Drop/unhook或覆盖DLL。新loader/恢复全过程须由主线审定及实际owned/file gate验证后才可能安排现场。

unwind RVA `5413988` 的xdata本轮未取；动态trampoline/assembly callback没有由本轮证明可用于SEH/C++/托管异常展开。普通Win64透明性是可实现、待实测的出口，**不是异常透明或全CPU状态承诺**；YMM/ZMM上半部、x87/MXCSR、完整GC、并发安装/卸载和进程异常退出继续未知。无需为这些未知在本卡加入新机制或绕过原异常。

## 合法本服trigger：可复用成果与准确首缺

40108位于原40103 `ret==0`、AccountModel非空及已审普通uid/token分支之后。配置同步、stored map、当前managed env/base、正常SDK页面都不能制造该账号结果；成功也可能来自合法已有本服会话/票据，不能一概要求密码输入。当前没有证明的零凭据页面动作能保证进入33107。

| 成果 | 可复用的真实范围 | 仍不能据此宣称 |
| --- | --- | --- |
| 本地MDK/账号接收端 | 当前 `AuthenticationHandler` 注册CN `/hk4e_cn/mdk/shield/api/login`、`verify` 与combo；MDK分别交现有Password/TokenAuthenticator，保留密码/会话及确认保存规则。账号回归和敏感日志修正直接复用 | 固定7.2当轮自然服务就是MDK、MDK请求/响应与SDK完整回调已互通。MDK名字/服务端路由存在不是客户端采用证据 |
| AccountPlat/Session/Combo | AccountPlat密码响应单token形状、session单token真实Java HTTP→原生parser/formatter、combo六例真实Java HTTP/default认证/Mongo保存→原生响应均已验限定边界；[session](CLIENT_72_SDK_SESSION_ADAPTER.md)、[combo](CLIENT_72_SDK_COMBO_HTTP.md)直接复用 | 合法本服响应已经通过完整SDK阶段链回到40103；UI/合规/最终回调和本轮自然账号仍未验 |
| route来源与静态选择 | 准确包内server_pc、LoadLocalConfig/readOnly、托管base+单`?`和最终分类器；native `loginUrl` 固定环境表/对应AccountPlat路径；stored-map env0与stock managed env0/base0各自已验 | 当前native登录/恢复请求或最终combo实际被送本服；不同epoch值不可拼为一次采用 |
| SDK账号key | 原SDK CryptoManager1024公钥与本服旧私钥不匹配已证；原caller `34EEB7/34EF88` 的窄key核心与真实原生PEM/RSA/Base64→所选本服私钥解密已验，窄loader实际安装/代码绑定与stock恢复已验 | 自然caller已经命中/供给成功。旧EXE `SDKUtil.RSAEncrypt` XML hook不覆盖已证AccountPlat原生链；若实际选MDK，也不能无证据套用AccountPlat caller/key |
| dispatch/游戏信任 | 本地root/公钥/两类key签名预制、正式供给回调保留原验签、坏key/root/sign拒绝及portable信任路径传递已验 | SDK账号RSA、实际网络TLS与运行客户端信任路径已通过；dispatch key4/SigningKey不能代替SDK登录key |

**必须先闭的实际前置，是“所选本服认证路径能合法产生原成功账号回调”，其中最先尚缺的是原生账号请求的本服route接线/自然选址身份，不能由33107观察器反向解除。** 当前 `native_sdk.rs` 仅重写四类price路由，并明确保留session verify原URL；`http.rs` 的托管SDK匹配也只服务price，且旧PostRequest特征没有本轮新增的R13 push，不能当33107已覆盖。通用Unity/WebView hook不能无证据覆盖AccountPlat内嵌native transport。

主线下一最小业务动作应单列“原生账号本服接线”合同：复用已有确切consumer SDK `41E6F0` /EDX=10002及原 `loginUrl` producer，明确原service/mode与唯一批准的host/path/method类别，精确供给所选本服endpoint并保留原请求/返回/校验；原AccountPlat账号key继续只用已验两caller输入核心。**这是需主线另审的准确实现/输入，不是本轮新hook许可，也不是泛域名重定向建议。** 若实际分支是MDK，需其真实request consumer/key/响应合同，不能因为Java已有MDK路由就沿用AccountPlat或伪成功。

具备这一本服route及实际key/loader/必要信任、R4/R5隔离运行前置后，才可用独立本服测试账号或合法本服已有会话，验证真实HTTP认证成功/错误、原SDK各必要阶段和40103成功AccountModel；随后原40108→33107自然触发才有依据。不能为获得该事件让用户官方登录、向未知外部route提交凭据、伪40103 ret0/AccountModel或远程调用40108。缺trigger时不再用相同SDK页面90秒碰运气；入口owned准备可独立完成，现场等待准确前置。

## 本轮实际验证、复现与交回

实际只运行Python/PowerShell离线文件研究：新解析器/一次提取退出0；`python -B -m unittest tools.test_client72_sdk_postrequest_entry_20261111 -v` 的5项通过，覆盖exact/containing歧义、8192/8193边界、目录对齐、完整指令覆盖与源漂移首门。没有重跑机械纯分类器或历史native/Java/owned成果。

私有 `audit_frozen.private.py` 只读本轮固定原件、41个源与13份文档快照；共享文档快照只作当时上下文，不把可维护原路径设成运行门禁。两个fresh Python进程各95项静态断言通过，报告逐byte相同：7021B / SHA `8eb1499758339cda9bd542394511023e2bbadb6c080b7565e5def82478a5dfa5`。包含完整原指令拼接/连续性、原E8/AOT/pdata、覆盖、原参数/栈算术/反序pop/尾跳、固定String来源及既有ABI未晋级边界；它不是新target CPU、hook/GC或客户端验收。完整清单 `frozen-inputs.private.json` SHA `560d8ed068b378e225a91e90d5049515231fb814e7f6da60686c73306f4efc6d`。读取期间若干Windows wildcard/不存在旧路径仅影响查看，未改原件或扩大取窗。

复现提取须先用当前checkout的 `tools/game_path.ps1` 新子进程解析GAME_PATH，确认实际退出0，再用新独立输出目录（拒绝覆盖旧原件）：

```powershell
python -B tools/client72_sdk_postrequest_entry_20261111.py --client <本轮新解析GAME_PATH> --output <本卡新的独立目录>
python -B local/migration72/d02-resume-20261110b/b-postrequest/audit_frozen.private.py --output <本卡新的审核JSON>
```

交付只新增本页、提取工具与同名测试，原件在 `local/migration72/d02-resume-20261110b/b-postrequest/`。未操作Git提交/分支、客户端/调试器/live API/DLL、Cargo/Gradle/JVM/Mongo/服务或配置/原数据；不需部署/重启。主线独立审核后维护Project/Harness及本地提交。max研究至此释放；下一工位可直接按16B入口/168B首次尝试合同做独立owned实现，同时先收口上述本服认证route前置，再安排有合法trigger的现场。

主线独立第三 `main-third.private.json` 的95项静态断言通过，7021B与两fresh同SHA `8eb1499758339cda9bd542394511023e2bbadb6c080b7565e5def82478a5dfa5`，5项定向测试通过。仅关闭准确入口静态合同；独立owned另验，自然采用/请求、同步返回与认证仍未验。
