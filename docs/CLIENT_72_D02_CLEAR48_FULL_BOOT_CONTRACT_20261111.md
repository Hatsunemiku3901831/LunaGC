# F-03d3f4d2 / CLEAR48-FULL 人工首组验收

2026-11-11，固定原 helper 904 B 的 SSE-long（257）和 AVX-long（258）在两个新自有进程中各执行一次。两条路径均通过原始事件检查、主线写域重建和正常退出验收。两组输入均为人工值。自然线程、GC 所有权、第二次分配返回、Default 对象及完整 Avatar 仍需各自的证据。

## BOOT 修正依据

旧 SSE 首次执行在 BOOT 收到同一自有进程的 `CREATE_THREAD`。旧 [退出生命周期合同](CLIENT_72_D02_CLEAR48_TRACE_EXIT_LIFECYCLE_CONTRACT_20261111.md) 明确限制 BOOT 只接收主线程，导致父进程退出 2，子进程按失败清理退出 90。该轮未到系统断点或 ARM，Get、Set、内存读、页查询与 helper 退休均为零。37 条原记录和 308 个受保护文件的前后哈希已复核，旧轮保持失败。原事件能证明线程的创建来源，线程用途及创建原因未知。

主线采纳新 BOOT 合同，只在 `CREATE_PROCESS` 实际 Continue 成功后接收同一自有进程的线程创建、已登记非主线程退出、已登记线程的 DLL 加载/卸载和调试字符串通知。登记来自实际创建事件，Continue 成功使新线程变为 live。DLL 文件句柄仍按原合同核身份和关闭。字符串、start 地址和 TLS 只记元数据。系统断点要求主线程和仍有效的已加载 ntdll 范围，ARM 要求准确的主线程断点地址、RIP 和原 TF。跟踪开始后仅主线程的既定异常可推进退休。BOOT 登记的其他线程可能仍 live，主线程跟踪不能推断其他线程从未运行。

BOOT 合同 SHA-256 为 `2b78eed73664f85605bc693d85ab4562a68158e53d28ae2d6973479ef52ab338`。runner 与 checker 分别固定此值，构建依赖审核将合同列为完整性输入。新 parent 源及实际 EXE 由同一构建记录绑定。384 B descriptor、256 B bootstrap、18 项 sourceProof、child 和两 case 原预算保持原规格。借用句柄的释放从实际退出 Continue 与 Win32 API 合同推导。

## 实际验收

| 项目 | SSE-long 257 | AVX-long 258 |
| --- | ---: | ---: |
| 原 helper 退休及包装器 CALL | 37 + 1 | 40 + 1 |
| 实际 BOOT 新线程 | 2 | 2 |
| 调试事件及 Continue | 55 / 55 | 57 / 57 |
| Get / TF-only Set | 79 / 39 | 85 / 42 |
| 页查询 | 13 | 13 |
| 内存读取次数及请求字节 | 56 / 1878 | 58 / 1929 |
| 实际写入宽度 | 16 + 16 + 16 B | 32 + 32 B |
| 写域并集 / 重叠 | 48 / 0 B | 48 / 16 B |
| 父进程 / 子进程退出 | 0 / 0 | 0 / 0 |

MSVC 实际构建通过 67 个偏移断言。主线复核 PE、包装器与调用者的真实字节、unwind、导入和构建依赖。33 项 Python 测试通过，包括两条完整人工 CLI 路径及 BOOT 来源、TRACE 线程变异。两个实际样本的 309 项受保护文件前后哈希一致。独立 checker 与主线原始记录均核到 CALL 写槽、RET 读取同一返回地址、实际写前写后值、64 B guard、返回 C、释放与正常退出。原 helper 的 SHA-256 为 `34287f706e27541c8119091aa9e55c7e2916610935f90c9b74992856b9654b31`。

复现入口见 [Harness](../Harness.md)。私有原件根为 `local/migration72/d02-resume-20261110b/a-clear48-full-helper-owned/`。保留 `sse-main1-20261111` 和失败复核，使用 `build-main3-20261111`、`bootstrap-main3`、`root-dependency-review-main3.private.json`、`sse-main3-20261111`、`avx-main3-20261111` 及对应 `*-validation.private.json`、`*-root-acceptance.private.json` 读取本轮证据。合同审计在同批 `a-clear48-full-helper-boot-contract/root-adoption-audit.private.json`，SHA-256 为 `902731e57a6c1c8a14479fbda5eeaf1430e0c12ca3af0f2cf99fcf4d951f0490`。

Sol max 在交付审计前收到客户端版本不兼容错误。主线复用其草稿，修正审计对十四份官方材料和两项历史导出的计数，再完成纯文件核对及实现集成。
