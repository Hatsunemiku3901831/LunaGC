# AB-STOCK-STARTUP-299：首次 module snapshot 失败审核（2026-11-10）

状态：有界静态审核已完成，v2 最小规格可实施；尚无 v2 helper 或新现场结果。本页是本轮新增记录，不替代既有 stock 规格、source seal、CheckOnly 或 ready。

## 简明计划

- 现证（已核对固定原件；架构 probe 由主线提供）：既有 stock helper 使用 CreateProcess flags=0，随后核对进程映像和创建时间，并立即枚举模块；首次 metadata.modules 为 error=299 / items=[]，sampleInvocations=0，primary/process Close 各成功一次。已完成用户退出及 exit=0 核验；用户只答“已退出”，页面表现未知。计划文件固定 SHA-256 `d7da899d093d75e9ee81973214ce76788e5c9d081dd240ae6000e7e940aef765`。
- 排除与未明：委派方已实测 Python pointerBytes=8、maxsize=9223372036854775807、3.12.10，固定 PE 为 AMD64。因此不应把本次 299 直接归因为 32→64；loader 尚未初始化、loader/module 变化、保护或 ABI 等归因也须分别说明证据，单个 299 不足唯一判定。
- 只读复核：阅读 AGENTS、README、Project §3.3、Harness、迁移和部署说明、固定 stock 规格、collector 文档、固定 run 的全部失败原件以及对应源文件；按固定原件及源码哈希复核 ABI、错误捕获、调用顺序与清理语义。不连接目标，不调用现场 Windows API，不使用 PE/REA，不启动进程，不做构建、Git、DB 或网络目标操作，不派子代理。
- 修改边界：仅新增本页和 `local/migration72/d02-resume-20261110/stock-startup-299/` 的离线审核工件。旧 helper/tool/doc/pin、Project/Harness、patch/resources 保持只读。本轮不实施 helper，不复试旧 run，不更新旧 ready。
- 候选规格：只在原 45 次/90 秒、2 秒一次的 module-only metadata 既定时点中，允许首次有效 EXE 身份前的真实 299/items=[] 记为 unavailable；不增加次数、retry、时长或 RPM。首次有效身份后的模块/路径/base/size及错误规则保持严格；窗口内从未可用则 0 RPM 拒绝。A/B 身份、GC、cleanup、stock/source 与 A72/1852+B26/452=98/2304 预算不变。审核将判断该例外是否足够窄，并比较固定 sleep/首次延迟方案。
- 验收：以固定 raw/source 的独立静态核对及双 fresh 离线审计形成 v2 helper 实施规格、必要反例和真实失败归因边界。记录观察事实与推断；不存在的证据明确记缺。
- 停止：原件哈希不符、源封存不一致、失败不是指定首次 299/items=[]、发现失败后仍有 RPM、清理不闭合或候选放宽后置身份/预算时停止对应结论，不补造结果。
- 恢复：主线实施新 helper 版本后须取得新 source seal、新实际 CheckOnly、新实时 ready，且不得与其他现场工作并发；全部前置重新成立后才可重新启动新 run。旧 raw 和本轮失败保留。

## 审核结论

**接受“首次有效模块身份前的 299/空列表不可用状态”，由新的私有 helper v2 和 coordinator v2 实施。** 这是对现有采集 helper 首次真实失败的局部适配，只增加一个明确的初始状态分支及窗口末拒绝条件；无需改变通用 backend 的 ABI、flags、原 A/B reader 或 primary API。该规格不保证后续一定出现可用模块，更不证明 loader 已就绪、保护已绕过或自然业务成功。

固定 [stock 规格](CLIENT_72_SDK_MANAGED_ROUTE_STOCK_SPEC_20261110.md) 的 SHA-256 仍为 `606814b70431e5ed208a4f5c71ca87760b5e6bf2c513012b3f525fbe9354ae94`，原 [collector 记录](CLIENT_72_SDK_MANAGED_ROUTE_STOCK_COLLECTOR_20261110.md) 未改。当前 helper 的早期严格拒绝确实在有任何 A/B RPM 前终止；本轮不把这一真实拒绝改写为成功。

## 原失败能够证明的范围

私有根为 `local/migration72/d02-resume-20261110/ab-stock-implementation/`，本轮固定 prefix 为 `ab-stock-live1-20261110T044626Z-4ebed503`。24 份原件均已按原编码完整读取，JSON/JSONL/log 已解析，并对引用及前后身份做离线校验。下表只保留脱敏摘要。

| 原件 | 已核事实 | SHA-256 |
| --- | --- | --- |
| `-plan.private.json` | 45 次/90 秒/2 秒，正常 flags=0；原 A/B 预算 | `d7da899d093d75e9ee81973214ce76788e5c9d081dd240ae6000e7e940aef765` |
| `-capture.private.json` | 唯一 metadata ordinal=1，modules 为 error=299/items=[]；sampleInvocations=0，B blocked-by-A、identityCallbacks=0 | `046441b75988cb894d2fff33ee9789b5531e1daf8917f65f17a02bfdb826e5e0` |
| `-capture.private.json.groups.jsonl` | 8 个实际 group：probe→launch→image→times→alive→sample→primary close→process close；与 capture 完全对应 | `fbe7964820b6cc344a529939397841019f2ae7a221064c7f92a37caba362aad1` |
| `-exit-verified.private.json` | 用户退出确认、stock/文件保护通过、目标及固定监听缺席、无 kill/文件修改 | `465a23e8f601b0f0681d470868ee71243f481c0da704de358c11ce37cf977067` |
| `-user-exit.private.json` | userExitConfirmed=true；pageFeedbackProvided=false | `7cf22f04430aa6797c83413b04e2646eee952f38c156a4d223c2f4b81b46da88` |

CreateProcess 返回成功，原 image/创建 FILETIME 对应原创建身份，首样时 status 为 alive=true、signaled=false、STILL_ACTIVE。随后 `_module` 遇任何非空 error 即抛 `EXE module snapshot error`；没有建立 EXE 模块身份，没有调用 A，也没有进入 B。外层 primary/process Close 均实际成功一次。metadata 的 elapsedSeconds=0.0 是现有 group 记录，不作细粒度原生时延测量。

采集 `driverExit=1`。主线报告正常退出核验命令 exit=0；退出原件证明其列出的缺席和 stock 核验，**没有记录客户端 GetExitCodeProcess=0**。用户未提供页面表现，不能补写“正常登录页”或业务通过。三份 stock 盘点的 19 项文件身份一致，三份进程端口记录均为空；这是固定历史原件的离线复核，未查询当前现场。

## ABI、位数与错误语义

静态调用链为新 coordinator→冻结 A `run_flow`→`RetainedPrimaryBackend.launch`→`ModuleBackend.sample`→`WindowsBackend.snapshot`。对应来源是 `coordinator.private.py:30`、旧 helper `run_flow:67`、[retained backend](../tools/observe_client72_tls_inputs.py) 第43行、[module-only backend](../tools/observe_client72_clean_loader.py) 第108行、[snapshot backend](../tools/observe_client72_uninstrumented.py) 第97/123行。

| 方面 | 审核结果与限制 |
| --- | --- |
| 调用位数 | 主线已实测指定 Python 的 pointerBytes=8、maxsize=9223372036854775807、3.12.10，且固定 PE 为 AMD64。本轮不重跑该 probe、不重开 PE；不能套用“32 位观察器读 64 位目标”归因。 |
| API 声明 | `CreateToolhelp32Snapshot(DWORD,DWORD)→HANDLE`；Module32FirstW/NextW 使用 HANDLE、MODULEENTRY32W pointer、BOOL；CreateProcessW 使用原结构指针和 BOOL 返回，未见明显的截断或声明错位。 |
| 结构 | MODULEENTRY32W 的 base 与 hModule 是 pointer 类型，size 为 DWORD，dwSize=sizeof(item)。按其源声明及 Win64 自然对齐推导 base@24、size@32、hModule@40、szModule@48、szExePath@560、总1080B；本轮未运行 ctypes.sizeof 或原生布局实验。PROCESS_INFORMATION 原24B按 `<QQII` 解析，四值精确对应记录的两个句柄及 PID/TID；这只验证该创建原件的宽度与一致性。 |
| flags | helper 正常创建 flags0，module-only 路径为 8|16，即 SNAPMODULE|SNAPMODULE32。不能因目标为 AMD64 就删掉一个已允许组合的 flag 来碰碰运气。 |
| last error | kernel32 使用 use_last_error=True；Create 失效句柄、First 失败及 Next 结束后均在 Close 前取 cached last error。18 被映射为枚举结束；299保留为错误。没有证据说明本次299由“未清零last error”产生。 |
| 真实失败子阶段 | 原 backend 对 Create 返回 INVALID_HANDLE_VALUE 输出 error/items=[]，对 Module32FirstW 失败也输出相同形状。当前299/[] **无法区分 Create 与 First**；没有原生 handle/result/substage transcript，不能补出唯一来源。 |

Microsoft [CreateToolhelp32Snapshot 官方说明](https://learn.microsoft.com/en-us/windows/win32/api/tlhelp32/nf-tlhelp32-createtoolhelp32snapshot) 明确允许64位调用方组合这两个 module flags，并列出32→64的299条件；也说明 loader 表未初始化/损坏或装卸模块变化可导致 BAD_LENGTH 或其他错误。它没有把本次299唯一归因为 loader。结合现证，只能把启动时模块表不可用保留为可能原因；保护、其他运行环境影响或尚未发现的 ABI 问题均未被原件唯一证明。成功取得 image/times 与成功枚举模块是不同证据，前者不补后者。

## v2 唯一实施规格

1. 新私有 flow v2 保持同一次正常 CreateProcess flags0、原 hProcess/hThread、原 module-only backend、原45次/90秒/2秒循环、原 alive 检查及全部 group 记账。不要修改旧 helper、coordinator、tool、doc、pin 或 ready，也不要用 backend wrapper 伪造成功快照。
2. 初始 `module is None` 表示“本轮尚无有效 EXE 模块身份”。每个原定时点仍先做原 alive 检查、一次 module-only sample，并保存真实 raw。**只有此时恰为 error=299 且 items=[]**，才标为 unavailable；不调用严格 `_module`、不建立 module、不进入 RPM。它仍是失败原件，不是成功 snapshot。
3. unavailable 分支仍走循环公用的原 sleep。不得在 sleep 前直接 continue，导致45次突发；不加内层 retry、不加 snapshot、不改 deadline、不增加观测次数或延长窗口。其他错误，包括24/BAD_LENGTH、5、299带非空items，以及 error=None但无唯一正确EXE，仍按原严格规则拒绝。本次局部规格不引入通用 Toolhelp 重试器。
4. 首次非上述例外的结果必须通过原 `_module`：唯一目标名、准确 path、有效64位 base、原 size 下界全部不变；其原返回 dict 成为本轮 module。首次有效后每一个 metadata 样本都走原 `_module` 并与这份身份精确相等，任何错误（含299）、缺失、重复或 name/path/base/size变化立即拒绝，不重新进入 unavailable。
5. 循环原有 timeout 仍能提前拒绝；group ledger 只记实际调用，不能补足45。完整窗口末若 module 仍为 None，显式在 epoch identity 和 A/B 前拒绝，保持0 RPM。**不能在窗口后补一次 snapshot 作为恢复机会**。若第45次首次有效，可以走原有严格 epoch identity；该既有身份回调与额外救场取样要分别记账，前者仍不容许299。
6. module已建立且窗口完整结束后，原 `_identity`、A一次及其final、A后验/primary close→B前identity→B一次及其final→B后identity/source、外层alive/stock/source及cleanup全部保持原合同。A身份或cleanup失败仍使B0 RPM；只图partial但身份/cleanup有效时原独立B规则保留。任何晚失败仍统一脱敏B，不从未知/历史记录补敏感原件。
7. A72读/1852B、B26读/452B、合计98读/2304B、单读208B不变；primary API、GC/非原子边界、句柄唯一关闭、现有函数错误语义均不改。不增加OpenProcess/OpenThread、线程采样、RPM、保护绕过或新语义字段解析。
8. 新 coordinator 必须显式绑定 v2 flow 及其完整新来源闭包；旧 coordinator 的旧 helper SHA proof只能作为历史来源，不能冒充审核过v2差异。旧 A 五tool/B两tool epoch 映射继续冻结，旧四profile输出及读图保持。新版本先完成离线反例/独立审核，再新source seal、新实际CheckOnly、新实时ready和新run；绝不复试旧run或借旧ready。自然现场仍由主线独占并串行。

机器可读规格为 `local/migration72/d02-resume-20261110/stock-startup-299/implementation-spec.private.json`，SHA `39056b10c869fb679636a3bf05327abc1489b225e11528e2b32f54fdf4b16795`。它是明确实施合同，未包含 collector 实现。

## 为什么不只延迟首取

固定睡眠后再做首取没有本次证据支持的就绪阈值，只把同一不可用状态换个时间碰运气。沿90秒deadline只延迟第一取还会改变现有45次/2秒观察范围；整体后移窗口则改变创建后的时域并增加等待。已有45个有限 metadata 时点足以表达“启动时不可用→后来形成身份”或“始终不可用→0 RPM拒绝”，无需为本次问题再加等待 API、握手或猜测的初始化信号。

## v2 必须覆盖的反例

以下是下一实现的验收要求，本轮未执行v2采集器测试；全部22个精确输入/预期在机器规格中。

| 输入 | 必须得到的结果 |
| --- | --- |
| 开始299/[]，后续有效且稳定 | 不丢原299，不增取样；45次后先严格epoch identity才可A |
| 45次全299/[] | 原窗口结束0 RPM拒绝，无第46次救场 |
| 第45次才有效 | monitor仍45次；原epoch identity单独严格记账 |
| 首次有效后299、错误、缺失或身份变化 | 立即拒绝，不重新等待；A/B保持未调用 |
| 首次299有items、错误24/5、成功但无唯一正确EXE或base/size不合 | 原严格拒绝，不被新例外吸收 |
| unavailable后目标退出、窗口超时 | 保留真实前缀及cleanup，不伪造45次或存活 |
| 窗口稳定、epoch/A/B身份/source或cleanup随后失败 | 原拒绝/B0读/敏感脱敏规则不变 |
| 用旧module代填、sleep前continue、固定sleep声称ready | 实现拒收 |

原四A profile正常/首错/final错以及现有AB111项应由v2实施者按受影响范围复核；至少新增真实失败形状、pre-first转移、末尾无module拒绝、later299拒绝、精确次数/等待及清理各一次的人工回归。人工回调只能验证实现，不作目标模块或客户端验收。

## 本轮实际验证与交接

只运行新私有 `audit_source.private.py`，以stdlib读取固定原件及AST。两个fresh Python进程均exit0，各184项源/原件断言通过，报告逐字节相同，SHA `105fb1819223ed94ab1b2612f2420ce0f455b092c2c220296cc70889fc6b8d93`。这个184包含哈希、引用、字段及静态调用链核对，**不是v2 collector测试数量**。24原件及12来源全部前后hash一致；清单SHA `9c49a6debf55997c922f55b0d3877977c0a8413934c6f6829cb25047d62310e1`。实际命令/exit/合并输出保存于 `fresh-executions.private.json`，没有声称独立重定向stdout/stderr。

本轮新增只有本页及 `local/migration72/d02-resume-20261110/stock-startup-299/`。未导入backend，未执行Windows API、当前进程端口/GAME_PATH、PE/REA、目标CPU、JVM/DB、构建、Git或目标网络；唯一外部只读访问是上引官方API页。Project/Harness、HANDOFF及旧源码/资料按本轮派工保持只读，由主线统一更新；没有本地提交。

**下一可执行项：Sol xhigh/owned_teb 按上述单分支+末尾guard规格实施新私有v2，独立核差异、人工反例及旧输出保持，再由主线封新source并做实际CheckOnly。** 本轮已无阻碍这一步实施的输入缺口；原生失败子阶段及真正根因仍未知，只有新现场原件可补充。v2现场与页面/自然业务均未验，D-02和R2父项不晋级。
