# F-03d3f4d2 / CLEAR48-NATIVE-CAPABILITY：主线实际结果

**独立自有 EXE 的原 6B 能力已通过。** 一个 UD2 异常证人及随后两个 fresh 能力进程完成；没有执行完整 904B helper、第二 allocator、Default 或 Avatar。实现专题记录 Terra 交回时的准备阶段，本页记录其后主线实际构建和执行，原源 pin 保留。

## 构建、来源和静态审核

主线按[冻结规格](CLIENT_72_D02_CLEAR48_NATIVE_CAPABILITY_20261111.md)审核[实现](CLIENT_72_D02_CLEAR48_NATIVE_IMPLEMENTATION_20261111.md)，5 项纯测试通过。首轮包装器在编译器/cmd 结束后仍等待进程树；只终止核对过的自有 PowerShell 包装器，未操作现场的 vctip。第二轮 `Process.ExitCode` 为 null。两轮源版本、日志、产物及未知退出码均保留，没有执行 fixture。改为同步调用自有 cmd、直接取 `$LASTEXITCODE` 后，第三新目录构建 **exit0**；C/ASM 内容未改。

第三构建固定 MSVC 14.44.35207 / SDK 10.0.26100.0。128 个实际 include、6 个被搜索的库、`/Bt+` 实际 c1/c2 路径均核完整 SHA；map 证明其中四库的成员归属，OLDNAMES/uuid 只记搜索。没有声称穷尽系统 DLL。主线另核所有来源、两份 C/ASM、112 原 pin、PE/map、23B/13B/3B 两种解码，以及 wmain 的三处真实 call、参数、栈与三个 SEH 范围；不把静态审核称作运行跟踪。

| 产物 | SHA-256 |
| --- | --- |
| 运行 EXE | `69259bce74f452829959e751d94c8682e1e4b1c760a7dcbce11da5e0764417f1` |
| 主线独立依赖审核 | `6288cd8be6f2b21f5a04baacf0b5cdc91de37f1aecfaa9931af11ac3ed7f36a8` |
| 三次原件独立复核 | `d132e6667535f8ab4bbd2d770c0eb5cf5f9c9a386e091a44a08e9ab314fdfea4` |

## 三次实际执行

原件仅在 `local/migration72/d02-resume-20261110b/a-clear48-native-implementation/`。每次新建隐藏自有 child、保留创建时间/句柄来源与各自 PID；不复用身份。父进程只管理自己创建的进程，均未超时。源码、依赖及 EXE 前后保持。

| case | child 退出 | 实际调用计数与结果 |
| --- | ---: | --- |
| `ud2-main1` | 30 | UD2 一次；真实 `c000001d`、ExceptionAddress/RIP 均对应 own witness RVA `1ea4`，无正常返回；CPUID/XGETBV/Probe 均 0 |
| `capability-main1` | 0 | CPUID 两次、XGETBV 一次、Probe 一次；host 三前置位通过，XCR0 原值 `e7`；原 6B 完成、真实快照和正常 C continuation 通过 |
| `capability-main2` | 0 | 第二 fresh，独立重复同一有限能力；相同 PE、owned 初始值与真实前后快照通过，不要求 CPUID EBX 相同 |

Probe 的原 `c4e37d18c001` 在精确 23B leaf 的 +8。真实 before32 为 `01..20`，真实 after32 为低 lane `01..10` 重复两次。三个 block 共 288B 的完整初始/最终数据均保留：input32 和全部 192B guard 不变，仅两个输出 payload 共 64B 由 wrapper 写出。这个 64B 是 Probe 快照写域；它不代替 GC helper 的 48B 域。没有伪填 post、host 模拟原指令或硬件逐指令 hook 计数的声明。

UD2 验证独立 phase3 的真实异常记录及向 C 的展开。XGETBV/Probe 的异常 scope 仅静态核对，正常路径实测通过，未故意触发其异常；SDK 原异常/unwind仍未验。

## 剩余与下一步

本卡只关闭单条能力、独立负证人和实现验收。完整 904B 的精确读写/重叠/原 PC、真实 RET/RAX/RSP 及单步时的 YMM 保留需[新 trace 合同](CLIENT_72_D02_CLEAR48_NATIVE_TRACE_SPEC_20261111.md)和独立 witness。捕获副本写、自然 allocator 线程、GC 所有权、第二分配/Default、完整 20 处分配/12 根及子构造仍未验；正式 Avatar g2、R3、Scene 和 D-02 不晋级。

全程没有客户端、DLL 安装、生产代码/Proto/配置/资源或数据库变更；无需服务重启或 portable 部署。本轮未遇安全/Daybreak gate；旧 B 包继续按队列冻结。
