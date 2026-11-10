# F-03d3f4d2 / CLEAR48-TRACE-EXIT-LIFECYCLE-V2-INTERFACE

2026-11-11，Sol max 独立源级接口审计。**确认一项 W3 validator 时序缺口，root 已作最小修复；其余本轮新增阶段、线程来源、真实 union、API BOOL 和收口字段未发现进一步 emitter/checker 冲突。** 本结论是编译前的源码与人工证据审计，不代表任何新 W1/W2/W3 或客户端通过。前三次 W1 仍失败，客户端验收增量 0，完整904B、自然 GC、第二分配、Avatar/g2 和 D-02 均不晋级。

本包只写本文及私有 `local/migration72/d02-resume-20261110b/a-clear48-trace-exit-interface-audit/`，没有修改 parent、validator/test、runner/test 或其他实际工具、共享索引、原始 run。未执行构建、dumpbin/PE、CPU/native、Debug API、live/REA、进程操作、网络、Git 或 DB；未调用普通 `validate` 或生成普通 accepted 报告。root 另行负责修复、测试、集成和提交，本包不执行交接文件写入。

## 计划、冻结和实际方法

先读 AGENTS/README、Project 当前派工/验收、Harness 当前 trace 索引、迁移/部署边界，以及本日 [合同](CLIENT_72_D02_CLEAR48_TRACE_EXIT_LIFECYCLE_CONTRACT_20261111.md)、[来源](CLIENT_72_D02_CLEAR48_TRACE_EXIT_LIFECYCLE_SOURCES_20261111.md)、[parent](CLIENT_72_D02_CLEAR48_TRACE_EXIT_LIFECYCLE_PARENT_20261111.md)、[validator](CLIENT_72_D02_CLEAR48_TRACE_EXIT_LIFECYCLE_VALIDATOR_20261111.md)和 root 私有实施计划。来源成果直接复用，不重查网站或重审全库。Project/Harness 为活动上下文，未将其 pin 为运行接口输入。

中文有界计划为2613B，SHA `c3881dcabe12fe1f85af1701d1c271594e2a1ae5aa42fb10d89aa4b98efc39de`。冻结21项必要源码、契约与专题/计划快照；逐项保存 bytes、SHA-256 和原字节副本。审计沿 native 真实调用和记录顺序进行，synthetic fixture 只用于定位下述单一反例，不用其分类标签代替 emitter 审查。

| 关键输入 | 冻结 SHA-256 |
| --- | --- |
| v2 机器规格，12071B | `cf52cfafa00eab47316a756a5e04a1edc59ca2b5606d4fa89d6cc2cb552105d8` |
| 最终合同 | `cbeaa19898ee74e7f5f1d71db5d1844f459aff3e15da26f7dabc11d7366fb83b` |
| parent，82135B | `53a0a42cc8079677db3657ab81f9d06c9918fe243a2ac32d50110d4c9882db13` |
| 审计起点 validator / test | `a1b0ca35cfe1a0ee86b7ee9291f7e1026f5b2358d1a26c4a09bd1be5be7d37ae` / `6e77745decc7385d97d1e59c2b8881018ed0e4b387d3238ffc7a1cf47440e9fc` |
| runner / test | `900503a9505344ffb08d7cfc3662939ec88f50c9b11728b73fdde7cf818e95d5` / `79d25733168a7653b7ae8ba02923e6d3e6d90da2277720c8a012ebf926b99740` |
| root 修复后 validator / test | `50d71da7f89486c753edba030e445871137f9f551dccc837b2b2b430910f71ad` / `1e27f52b99e8bc75e8700339a4df21c5ebf99b3fe473ce56653a1c40ad5a8823` |

收口时独立重算副本与当前源：只有 root 明确通知的 validator/test 两文件发生上述修复，parent、runner 和其他实际源保持；专题/计划只作上下文快照。shared.h、child.c、witness.asm 与原合同25输入 manifest 的固定值匹配，build.ps1 与原 main5 review 的 source 项匹配；四旧 JSON 均匹配原 pin。`line_begin/get_context/set_tf/read_memory/emit_transaction/validate_descriptor/prepare_transaction/retire_transaction` 八个函数与原 parent `6e0e7d…48d73` 逐字相同。59项文件/文本断言含21份副本复核，不冒称59个运行测试。

## 发现、最小反例和 root 修复

起点 validator `check_witness` 397–404 只要求唯一 Terminate 的 BOOL TRUE、请求90，以及全部 cleanup Continue 的 seq 晚于 Term；没有要求 Term 晚于原 UD2 故障及未退休 transaction 记录。parent 的真实顺序本来正确：`event_loop` 1223 标 `expectedUD2` → 1282 输出未退休 transaction → `cleanup_run` 1314 调 Terminate。

本包以独立 synthetic W3 为基准，只将唯一 Terminate 行移到 UD2 的 wait 前，连续重排 seq/引用和 summary 的 fault stop，其他值不变。得到 Term seq38、UD2 event seq40；起点 `check_records + check_witness` 仍返回。该人工证据违反本合同要求，不能因 parent 正常不会发出这种顺序而由独立 checker 放行。反例只存在本包私有 synthetic 文件中，未修改旧 raw 或 fixture/tool；派生回执固定 `accepted=false`，没有普通验证成功报告。

root 接受最小规格并独占修改：W3 增加 `txs[-1]['seq'] < term['seq']`，保留所有 cleanup Continue 晚于 Term 的原约束。于是原 UD2 event、context、未退休 transaction、Term 和原 stop 的 cleanup Continue 形成有证据的先后关系。root 新增一个方法、两个子例：Term 移到 fault wait 前、移到 fault transaction 前均须拒绝。本包独立核新 SHA、准确源码 diff 和两个子例，确认 validator 只有这项条件变化；不重复执行 root 的测试。

root 实际报告修复后37项纯门通过：validator19、runner10、artifact8；本包开始时 root 已独立通过原36项。两次测试均由 root 执行，本包没有把这些计成本包重跑或实际 native 证据。未发现需要修改 parent 或扩大合同/预算的第二项接口问题。

## native emitter → checker 逐项映射

下表 parent 行号对应冻结 `53a0…db13`；validator 行号以起点 `a1b0…7ae` 为准，root 修复在403处分成两行，后文行号相应加1。表中“核验”仅指形成接受所需的字段和关联，不声称所有既存诊断字段均新增了完整 schema 验证。

| 记录 / 字段 | parent 真实发射与 JSON 类型 | checker 真实要求与结论 |
| --- | --- | --- |
| epoch / summary / report 政策 | parent11–12、1401–1405、1550–1552：顶层固定 policy 和 supplement SHA 字符串；summary schema v2 | validator29–31、136–139、575、581–585/601–604：两顶层固定字串，成功/失败报告 v2；旧 epoch 无字段拒绝。契约 SHA 没有混入 sourceProof |
| sourceProof | parent1441–1451 仍为原13个 bootstrap 字段：9 RVA 整数、3 SHA 和 imageBytes hex16 | `check_inputs` 返回原字典，`check_records` 要求 epoch 原字段和 summary.sourceProof 精确相等；runner dummy 接口保持，不要求第14个字段 |
| ownedChild PI | parent1591–1603：primary PID/TID、PI 两 origin/raw hex16；GetProcessTimes BOOL 和成功 FILETIME hex16，未取得为 null | validator140–149：正 DWORD 身份、creation 下限、两固定 PI origin 与有效 raw；summary.tid 仍等 primary，退出 TID 另列 |
| wait → event 关联 | parent1045–1086：成功使用此次 DEBUG_EVENT 的实际 PID/TID/code；wait 的 stop 指向紧随 event。timeout/error 时三身份和 stop 均 null | validator150–168：成功 wait 三元组等其真实 event，stop=seq+1，事件与成功 wait 双射；timeout 仅接受原121，三身份/stop均 null；没有替回 primary |
| event 通用头 | `line_begin` 235–249 与 logger942–964：实际 stop 头；raw code 整数，Continue 状态/BOOL 为 null，另行记录；非异常 union 保留 null | validator161–178：actual PID=owned、TID正 DWORD、code一致、separate 固定；每个 event 唯一 Continue，后者须在下一 wait 前且实际三元组相等 |
| CREATE_PROCESS borrowed | parent994–1005：两 raw hex16、bool valid、origin=`CREATE_PROCESS.borrowed`；start/TLS hex16 | validator453–459：初始 primary 来源唯一、两 raw/valid/origin 一致，两个 pointer 经 hx；NULL 原零可表示，sentinel 拒绝；不把 PI 值替作 event 值 |
| CREATE_THREAD union | parent869–889、1011–1017：仅 actual owned type2 登记 TID；raw hex16/valid bool、`CREATE_THREAD.borrowed`、start/TLS hex16 | validator460–464：独立从 type2 建来源，拒绝 primary/活项/墓碑重复和超128项；hx16及raw/valid/origin核验。真实零/false 与缺字段/null区分 |
| 线程退出 / 最终进程退出 | parent1018–1019、1029–1030：实际 DWORD exitCode JSON 整数；944–948 在收到 type5 时保留实际 seq/TID，即使后续 Continue 失败 | validator438–439、483、488–502：DWORD严格整数；4成功Continue终结对应线程，5来自尚存来源且为最终 event。summary.exitEventSeq/TID匹配真实 type5，不强制 primary，不要求所有线程先单独 type4 |
| CREATE/LOAD file 原值 | parent965–993：raw hex16、各自 hFile origin、valid bool；有效时原路径/identity/size/close API 字段，实际无效时这些观察值 null且requiredLength0 | validator467–482：raw/valid/origin关系；有效需非空路径、正imageBytes、DWORD identity、requiredLength 1..2047及成功 close/无error。无效路径不被伪记成成功关闭；CREATE还需真实child SHA和imageBytes |
| LOAD 既存模块字段 | parent965–967、1007–1009 保留 moduleBase hex16、条件 imageBytes、ntdll标志和file SHA | checker以既有 NTDLL 区间匹配核系统 breakpoint，新增有效file门不改变旧NULL hFile许可；未把DLL名、路径或相邻事件当线程创建者证据 |
| UNLOAD union | parent1020–1021：lpBaseOfDll→moduleBase hex16，仅元数据 | validator484：hx16；事件只可在由raw推出的退出/drain范围，不能用于TRACE、模块扫描或新读 |
| OUTPUT_DEBUG_STRING union | parent1022–1025：pointer hex16、两个 WORD→JSON整数（0..65535），不读payload | validator485–487：pointer hx16、两个严格整数 WORD范围；没有字符串内存/API或新memory预算。7/8仅人工覆盖，未实测 |
| RIP union | parent1026–1028：实际两个DWORD JSON整数 | checker正常/预期负接受在有限事件集合处拒绝type9；不会因有raw RIP字段获得退出许可。该拒绝路径不宣称独立验证其union所有字段 |
| Continue API / 写日志 | parent1096真实调用→1105–1116依真实BOOL更新outstanding、创建live、线程墓碑或process exit→1118判写日志结果 | checker要求实际 BOOL TRUE、status=`00010002`、cleanupOnly严格bool和event关联；完整日志不齐不能通过。parent不会因API TRUE而日志失败重放Continue，也不因辅助函数返回false撤销实际已发生生命周期 |
| Terminate PI | parent1314–1318：唯一 PI.hProcess 请求90，先保存真实BOOL再记日志；FALSE不进入Continue/drain | validator398–405、511–516：W3唯一Term TRUE、handleRaw等owned PI、requested90；root补 fault transaction < Term，全部cleanup Continue > Term。正常case禁止任何Term |
| PI signal / GetExit | parent1354–1374：EXIT成功Continue后，用PI等待原deadline剩余时间；signaled后GetExit，BOOL TRUE才有DWORD exitCode，否则null | validator384–391、507–514：EXIT Continue < signal < GetExit，result0和无error；实际GetExit必须等event exitCode；不是用child自报回C或root外部盘点代替 |
| PI手动Close | parent1377–1384：PI.hThread与PI.hProcess各一Close，原handleRaw保留，真实BOOL/error | validator384–391、504–516：两个PI源各一、在GetExit后、raw匹配owned。event borrowed不准手动Close；file关闭留在各自event字段，不混作PI关闭 |
| 自动释放报告 | parent只更新必要状态，不发自动Close API记录 | validator488–500从创建来源和成功4/5 Continue导出 `borrowedReleases`，basis=`api_contract`。NULL项无释放条目；process最终释放覆盖剩余线程，不伪造内核Close BOOL |
| summary 收口 | parent1385–1387、1396、1411–1440：实际exit/continued/signal/GetExit/close/log/lifecycle合取；未取得值才为null | checker392–412、501–502：完整字段/实际值及case结果相符；normal无failure/Term/cleanup-only；W3 pendingTx2/false、完成1、first expectedUD2、OS90/parent30/无child-result全部保持 |
| root review / 12 artifacts | runner126–129：补合同到review.source/declared与before/after集合；原五编译源、build-epoch和12 artifacts不变 | validator520–554：精确resolve后的supplement path唯一、固定SHA、真实bytes/hash/before，仍要求12 artifact集合。旧review因此不能授权新运行，不添第13个artifact |

## 阶段、失败和预算的交叉结论

TRACE 与退出通知边界没有靠 phase 标签放宽。parent1150–1153在 WAIT_EXIT 之外仍要求 primary；W1/W2在全部退休、必要buffer/RET/RSP检查、pending清空、同stop CONTROL-only恢复TF及真实Continue成功后，1239才写WAIT_EXIT。checker逐条原transaction/source/栈/向量/TF回读及Continue关联，先推出 terminal boundary，再要求退出通知位于该边界后。最后退休前的新2/4/6/7/8不能取得正常许可；边界后不能再有Get/Set/memory/transaction/calibration。`traceDone` 仍仅在最终 EXIT 成功Continue、event_loop返回1后为真。

线程登记只做一次：成功Wait入口调用一次 observe，metadata失败后的同一outstanding清理不再重复登记；foreign PID在metadata/file/context操作之前被隔离，仅记录其raw union，后续不Continue。正常WAIT_EXIT仅2/4/5/6/7/8；除真实type2建立来源外，其他通知必须已有live来源。primary先退出、新线程最终type5、其他线程无需逐个type4均与checker表一致。TID按值查找的小数组，墓碑保留；没有新增ID查询、OpenThread、context/suspend/resume、线程复制或payload读取。

W3原UD2保持未退休：fault分支不调用retire_transaction，pending保持2，首失败expectedUD2粘住。Term真实TRUE后，原UD2及全部actual drain才可cleanup-only Continue；额外异常/RIP/未知code、未知非创建TID或foreign范围不一致使完整收口失败。root已纳入的parent修正 `next==0 → cleanupInconsistent` 保留了“首失败expectedUD2后，wait/log_event元数据失败且仍有outstanding”的后来失败；可按原授权释放暂停事件，但不能遮蔽该失败变成预期负接受。Continue API TRUE后写日志失败的状态推进已在源码顺序中核实，`log.failed`仍阻止cleanupComplete。

原10秒/128事件在TRACE、WAIT_EXIT、TERMINATING之间共享；Wait最大50ms，限额先于Wait检查。清理没有重置deadline/计数；越界最多既有outstanding的有限收口，不新增Wait或退休。原case Get/Set/读/字节预算及TF/EFLAGS v2未改。本包没有将仍未实测的7/8、具体CRT事件顺序、线程起源或OS自动关闭实现登记为观察事实。

## 私有证据、失败尝试和交回

| 产物 | SHA-256 |
| --- | --- |
| `frozen-inputs.private.json`（21项和副本） | `1853fca91f00769a657cddaceed1de69f58fe7384fb0d7e5eb3484ada95ad54e` |
| `interface-probes.synthetic.private.json` | `834c1f81c96e0f8930168667748350a3d80821d11965182a87dea270679cc68d` |
| `early-terminate.synthetic.json`（纯人工反例） | `430cee4c04a2c7f4df76c7b4215c3dfa87ed27c42e6bbd584bfd9021da38f695` |
| `source-interface-audit.private.json` | `5f7819b7e7ff53dab2594cc3f36ca1f383803a28be3ec18bc0a0412b644b37a8` |
| `root-fix-client72_d02_trace_validate.py.patch` | `500742ef73b9772da72d671006456fbe601c7bff7ff9a27c271ae6d43b4daad8` |

本包保留三次私有审计脚本失败：首次冻结误取其他包的同名frozen-inputs，被哈希断言拒绝后按原实现合同更正路径；收口脚本首次多余括号，解析前停止未写文件；随后精确diff断言中的预期缩进多1空格，检查真实diff后仅改私有预期。最终纯文件脚本退出0。这些均未改实际工具/原件，也没有安全、approval或Daybreak标记。本包没有安全拒绝；先前parent写入errno22及root具体修复沿原parent专题留账，不冒记安全事件。

任务交回：接口审计已完成；一项已确认缺口已由root最小修复并核源，不等待新build或native。Git、Project/Harness和最终本地提交由root统一处理，本包未作Git提交或推送。下一可执行动作仍是root对最终新source epoch统一构建、PE/caller/unwind、依赖审核和真实policy/SHA绑定；通过后另批owned新W1，实际normal OS0/完整日志/独立checker均通过才决定W2，再W3。37纯门和本审计都不替代这些门，不授权完整helper或客户端实验。
