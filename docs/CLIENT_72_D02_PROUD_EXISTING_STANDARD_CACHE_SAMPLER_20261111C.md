# D02-R3 既有标准缓存条件读回采样器（离线准备 v3r1）

任务 `R3-PROUD-EXISTING-STANDARD-CACHE-INPUT-PREPARE`；主线批准 v3 的离线实现，**目标进程读取、现场 scope 和新 ready 尚未获批**。本包不启动客户端，不获得实际缓存或 getter 值。结果仅供同 key 桥和文件字段的条件对照；不证明原生 getter 被调用、实际 row、当前采用、codec epoch、对象借用有效、resourceReady 或 D-02。

## 固定来源和规格

私有 `specification-v3r1.private.json` SHA `ab06e108985bdbc084bda33123ee0a89010c1d77f77a22b47ad4ee50340caa10`；原 v3 SHA `5929d2b5e59c1831212e76a616868dca7b8ff24b4a7c9e4db1626f325896fc6e` 和三件快照保留在 `v3-frozen/`，manifest SHA `42faac5f2e94460784365d848f813f80714d7fb939a3f0e5c9f0b1309050f0f2`。r1 仅修正原 excluded 误禁 seed/codec 只读的文案：禁止生成、初始化、全表/forward 及 callee/native 执行，允许 getter 明列的 inverse 选择字节与 seed 只读；读集和预算不变。原规格引用 v2 SHA `ea03f1d70f810fbc0d0651938bc4f922b815612da021180b2e67d57a30535218`、phase-A 23 pins SHA `b66688652e923c72861701218b3c9f98e527e6a98d5aba896ec5326500c7b164` 及 27A 回执。v2 三件与规格、测试日志已复制至私有 `v2-frozen/`，manifest SHA `49750c1e563eb241ed2be6705b360727f37daef46201695b7c1979c377c13d89`；v1 冲突原件保留，P 精确为 entry+0x10。

getter 新增固定来源为 `local/migration72/d02-resume-20261108/r3-mapping/main-third.json` SHA `d990c2c469e5a9d3ee40351e4dfe5223ae49afaa855abe5d912427139c66307e`，该报告记录 164B getter SHA `ef485aef75c3e3d9d8814d3fb800021f431508269e31f79431adc7915593e6a0`；现有 listing 1384B SHA `8b5b2f341f887eef750e7ed4b062556a04148b75cce0de4b15d8022beee004f5`。只核已存报告和 listing，不重新读取 PE 的 getter 原体。语义另绑定 [scalar 合同](CLIENT_72_D02_PROUD_SCALAR_SETTER_SEMANTICS_20261111C.md) SHA `c2ce85509487225c4f666fbdeb90141895283eedb5615e12f6f4f55be2a36b3f`；其他 663B FindEntry、689B caller、typed metadata 和字段桥按 v2 pins。

## 查询、getter 输入和双遍账

只观察 comparer=0、key22101，从实际 bucket 可达链最多读四个 12B entry；hash/key 同时命中才读 entry+0x10 的 P。再读 P+0x40 的 managed 指针与其 +0x84/+0x90 两个 encoded DWORD；managed 为 null 立即停止，不读取 native handle。初始七读为 53B；四 entry 命中的 v2 单遍最坏 15 RPM/125B。

取得两个 encoded 后读取同一已读 root+0xe970 的 table 指针 A、A+0x18 的 U32 长度 L、imageBase+0x5872d30 的 U32 seed K，合三读16B。A 为 null 停止。对每个 E 先计算 `q=E XOR K`，四索引依序为 `0x400|(q>>24)`、`0x500|((q>>16)&255)`、`0x600|((q>>8)&255)`、`0x700|(q&255)`；先检查该字段四个索引均无符号小于 L，再依序各读 `A+0x20+index` 的一个字节，高到低合并。L 不要求机械地达到 2048，实际可读性仍依赖每次读取；相同地址的重复读取各计一次。没有查全表、正向表、调用 helper 或原生 getter。

该 getter 最多新增 11 RPM/24B，单遍最多 26 RPM/149B。只有完整候选才依首遍**相同地址、长度和顺序**重读一次所有内容，逐字节一致、后身份复核与进程句柄关闭均通过才保留完整 `conditionalGetterU32`，整轮最多 **52 RPM/298B**。`observedEncoded`、`observedCodecInputs` 是本轮顺序观察；失败中仅保留明确标记的 partial，不将两字段宣称完整。`actualNativeGetterReturn` 和 `currentAdoption` 为 null，`actualRow`、`resourceReady` 为 false。两遍一致不等于原子快照或证明同一 codec epoch。

仅 -1 为正常空链；其他负索引、正索引越界、无效长度、原循环预算耗尽、四碰撞上限、null、短读、错误、比较器非零、表索引越界或重读变化均停止。请求/返回字节、错误和重复读取逐次入私有账；无 warm、初始化、allocator、refcount、native handle 或 A/B 被拒动作。

## 显式目标 API 上限和退出

identity 使用 `0x101000`，上限 11：OpenProcess 1、GetProcessTimes 1、QueryFullProcessImageNameW 1、WaitForSingleObject 2、GetExitCodeProcess 2、CreateToolhelp32Snapshot 1、Module32FirstW 1、CloseSnapshot 1、CloseProcess 1。sample 使用 `0x101010`，上限 68：OpenProcess 1、GetProcessTimes 2、QueryFullProcessImageNameW 2、WaitForSingleObject 2、GetExitCodeProcess 2、CreateToolhelp32Snapshot 2、Module32FirstW 2、CloseSnapshot 2、CloseProcess 1、ReadProcessMemory 最多 52；合计不超过 **79**。v2 的 57／30／250 只适用于冻结的 v2，不是 v3 的限制。

每次显式 API 调用前核类别预算；仅 Module32FirstW，不枚举后续模块，不枚举进程，严格核可执行映像、PID 对应创建时间、base、size 和存活。每个成功打开的句柄经 finally 唯一关闭，实际 BOOL 及失败记录，不重试关闭。入口要求显式当轮 PID、新 ready 和新的私有输出，不继承旧 ready。文件 SHA I/O、最多8192B PE 头读取及本地诊断另账；298B 为 RPM 请求上限，不表示系统内部拷贝上限。deadline 和 Ctrl-C 在 API 边界协作停止；sample 后核与清理仍尝试，不声称可中断单个阻塞的 Win32 调用或终止游戏。

## 离线复验

```powershell
python -X utf8 -B -m unittest tools.migration72.test_proud_existing_standard_cache_sampler -v
$env:PROUD_CACHE_SELF_TEST='1'
try { python -X utf8 -B -m unittest tools.migration72.test_proud_existing_standard_cache_sampler.Win32SelfTest -v } finally { Remove-Item Env:PROUD_CACHE_SELF_TEST -ErrorAction SilentlyContinue }
```

v3r1 初版 17 项纯测试通过，另 2 项真实自进程测试默认跳过；显式两项自进程测试通过。四碰撞命中自进程实际读取 52 RPM/298B 和 Module32FirstW，并检查真实句柄关闭 BOOL；mock 检查 identity 11/sample 68，包括失败 BOOL 和关闭。fixture 使用非零 seed、不同四 quarter、高字节以及两个字段实际八索引，覆盖各字段索引门、长度边界、null、两遍各次短读/错误和 getter 输入变化。仍不是目标进程测试。

首次 v3 纯测试的旧夹具在新增首遍的第 12 读便改动数据，误以为处于第二遍，导致 fixture 缺失新索引；`v3-test-first.log` 原样保留。调整重读位置后，最终日志 `v3r1-test-pure.log` 与 `v3r1-test-self.log` 通过。未改共享索引、提交、构建或部署；独立 intake 完成后，现场范围及是否需要新 ready 仍由主线另行判断。

v3 的 `v3-test-final-self2.log` 保留的是当时的完整 unittest 输出；52 RPM/298B 是该次通过的测试源码断言，原日志本身未逐次打印读取账，不把它说成已捕获逐次账。r1 另运行私有 `v3r1-self-ledger.private.py`，仅包装同一 Python 自进程测试的返回结果和 API 账；`v3r1-self-ledger.private.json` 保存本次 52 条读取及实际调用记录，`v3r1-self-ledger.log` 保存当次完整测试输出和计数。该辅助复验不访问游戏进程。


## 末次读取后的取消与超时返修

独立 intake 在冻结的 r1 工具上发现：第52次 RPM 返回时请求取消，后身份复核及关闭均正常，但旧代码错误返回 `completed` 并保留完整条件值。只读失败证据为 `integration/acceptance29-code/late-cancel1.private.json`，SHA `e5ec6073245736736a02861294173b1d00dbb1bd942df0d554f3991e275b7e18`；旧 r1 工具、测试、专题、规格和日志的私有快照 manifest SHA `573793fa2c8ef32f6b66ff37c7e432248ea5e7a32cf25db06ccb088c57b42b3b`。v3r1 规格本身未修改，旧实现的取消成功门不追认。

修复仅在 sample 的后身份复核及唯一进程句柄关闭完成后、成功发布前核一次取消与 deadline；若已取消或到期，撤下完整 `conditionalGetterU32` 并返回对应状态，保留标记为 partial 的观察和已完成读取账。已有身份/关闭失败仍按原错误报告；没有增加目标 API、改变读序、权限、source pin 或 52 RPM/298B、79 API 上限。

四个纯 fixture 分别在第52读返回时和后复核期间触发取消或超时；均保留 52 RPM/298B 和有效部分观察、撤下完整条件值，并验证后复核、两个快照关闭及进程关闭和 68 次 sample API 预算。修复前四例均失败的输出保留于私有 `late-cancel-regression-before.log`；返修后 21 项纯测试通过，另两项自进程默认跳过；显式两项真实自进程测试通过，日志分别为 `late-cancel-final-pure.log`、`late-cancel-final-self.log`。没有目标进程访问或现场授权；独立 intake 仍需复验返修。
