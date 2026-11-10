# F-03d3f4d2 / CLEAR48-TRACE-WITNESS-VALIDATOR

## 中文有界计划与状态

本卡只实现三个自有 witness 的离线原件校验，读取四个冻结设计、root 唯一共享头、128B bootstrap 及其镜像、逐行事件、parent/child 结果和 root 外部运行回执。只写新工具、合成测试和本文；不写 parent、child、header 或输入原件，不实现原 176 条/904B catalog，不调用 native CPU、Debug API、真实进程、网络、数据库或 Git。安全/Daybreak 停包，不换路线重试。

已实现并通过纯 synthetic 测试；这不是本机 native 执行或 root 验收。输入缺失、异常或校验不满足均拒绝，输出以独占创建写出。不得将 synthetic 夹具或外部审核自述当成真实 CPU 证明。

## 输入合同与调用

`--header` 是固定 `shared.h`；`--contract-dir` 是四个冻结设计 JSON 所在目录；`--bootstrap` 为原 128B，`--bootstrap-mirror` 为 root 工具镜像。`--events`、`--parent-result`、`--child-result` 指向 `argv --output` 的三个对应文件；W3 的 child-result 路径必须不存在。`--receipt` 为 root 实际 Popen 回执；`--output` 是不存在的新验证报告文件。

```powershell
python -B tools/client72_d02_trace_validate.py `
  --header <shared.h> --contract-dir <contract-dir> `
  --bootstrap <bootstrap.bin> --bootstrap-mirror <bootstrap.private.json> `
  --events <run-dir/events.jsonl> --parent-result <run-dir/parent-result.json> `
  --child-result <run-dir/child-result.json> --receipt <root-run-receipt.json> `
  --output <new-validator-result.json>
```

报告 schema `c48-trace-validation-v1`，成功退出 0，拒绝退出 1；已有输出路径会直接拒绝，不覆盖。每个 case 单独运行；`pureArtifactValidationOnly=true`。W1/W2 的 `normalTracePassed` 可为 true；W3 仅 `expectedNegativeAccepted=true`、`normalTracePassed=false`。三项全部接受后仍需 root 自行审核构建来源、PE/unwind 和现场收尾，不由单个报告声称总体 trace calibration 或客户端通过。

回执 `c48-root-run-v1` 包含 caseId/runId、parentPid/parentCreationFileTime、parentExit/timedOut、startedUtc/endedUtc、before/after、argv 和 artifacts。artifacts 精确为 `parentExe,parentMap,childExe,childMap,header,bootstrap,bootstrapMirror,dependencyReview,interface,cases,eventSchema,frozenInputs`，全部为实际绝对路径；argv 精确匹配 parent 实参和输出目录。before/after 完全相等，并覆盖上述路径及外部审核所列源/依赖，逐文件重算 SHA。运行中新产生的事件和结果不预填 before；报告记录其输入 SHA。

外部审核 schema `c48-trace-dependency-review-v1`：`independentlyReviewed=true`、`actualBuildExit=0`、`peAndCallerUnwindReviewed=true`；artifacts 四个 PE/map 条目及非空 `source/includes/libraries/compilerDlls/buildLogs` 数组，每项精确 `{path,bytes,sha256}`。源条目需覆盖五个实际构建源及 `build-epoch.h`。本工具核列项字节、哈希、路径与回执，但不会独自证明外部审核列举穷尽或 CPU 真实性；该结论由 root 独立审核负责。

bootstrap 原 128B 解包后绑定 child EXE/header SHA、image size、descriptor 及 8 个 label；镜像 `imageBytes` 严格为整数，parent epoch/sourceProof 的同一数值严格为 16 位小写 hex。parent v1 接口以 root 的私有快照为基础，另按 root 澄清的 `ownedChild` 创建时间、`sourceProof` 和上述 imageBytes 类型消费；不得把可空项解释为 0 或成功。四设计和头各自固定 SHA，按原件重新计算。

## 离线核验范围

逐行重算序号、事件/等待/Get/Set/Continue/内存调用序号与预算，事件须绑定同一新 child PID/TID 和同一暂停期；所有 Continue 均要与原事件关联且成功，清理继续不能授权事务。系统断点须落于已记录 ntdll 映像；自有 marker、第一/后续单步与实际上下文分别核对，不预填异常地址与 RIP 的差值。`Get` 严格为 CONTROL|INTEGER；`Set` 严格为 CONTROL，回读 Get 通过 ordinal 与同停点配对，整数和其余控制字段不变、只允许 TF 位数值变化。

原始 descriptor、真实 sourceCode 字节、运行地址与固定 label 绑定；CALL/RET 的 RSP、栈槽和返回地址分别与每次上下文及读原件关联。事务匹配逐条 opcode、pre/post 事件、实际 RIP、TF Set、非 cleanup Continue、EA、宽度与完整读写。W1 断言 3 条退休和非零 64 位 RAX；W2 断言 7 条退休、32B input、两笔完整 store、288B 初末数据及全部 guard；向量性质只由实际 store 观察，不伪造 YMM context。W1/W3 的零 CPUID 数组仅是未探测初值。

W3 断言仅 CALL 退休、UD2 保持 pending 且未退休，实际首机会 `c000001d`、FaultLeaf 的地址与 RIP、RSP=C-8；parent exit 30、owned child exit 90、Terminate 成功后才有该异常及 EXIT 的 cleanup-only Continue，并关联实际 EXIT、进程 signal 和 PI 关闭；正常 child-result 不存在。所有 summary 计数逐行重算；不可用 parent 自报替代原事件、上下文和内存证据。

## 验证与未完成项

执行 `python -B -m unittest tools.test_client72_d02_trace_validate -v`：4 个测试方法通过，覆盖三个完整 synthetic CLI 正例、三个逐行 synthetic witness、24 项事件/身份/事务/上下文/内存/异常/退出变异、5 项 receipt 变异，以及已有输出拒绝。synthetic EXE/map/dependency review 仅是临时目录内的人工字节和测试对象，不是 native 可执行文件或真实审核结论。

本卡未进行构建、真实 Popen、Debug API、native 指令、客户端验收或部署；未修改运行代码、Proto、配置、资源，故不需重启服务。真实 parent writer、root 回执及三个运行原件尚需 root 审核并实际提供，再用本工具运行且独立复核；失败原件保留，不能重写、补造或换方式视为通过。完整 helper、GC 所有权、完整 Avatar 与 D-02 均不在本卡验收范围。
