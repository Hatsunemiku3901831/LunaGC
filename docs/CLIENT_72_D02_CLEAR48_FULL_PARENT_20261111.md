# F-03d3f4d2 / CLEAR48-FULL-PARENT-V1 源码交付

主线已完成集成及两组人工 native 验收。下文保留子包交付时的状态，最终构建、BOOT 修正与验收见 [人工首组验收](CLIENT_72_D02_CLEAR48_FULL_BOOT_CONTRACT_20261111.md)。

本卡完成独立 parent 源码，交 root 进行构建、依赖/PE 审阅和独立验证；**本卡未构建或运行，不能视为 native 或客户端通过**。源码 `tools/client72_d02_clear48_full_helper/parent.c` 的 SHA-256 为 `993eb6dd631e57ecd6d821c51b3c0643d37482948caa523f6ca6b579d4f8520c`。冻结 main6 parent 只读来源 SHA-256 为 `53a0a42cc8079677db3657ab81f9d06c9918fe243a2ac32d50110d4c9882db13`。完整合同、实际 shared header、root binding 和生成数据按既有冻结版本消费；不引入旧 ABI 或 case 的兼容别名。

实现包含固定 CLI、输入长度和 SHA/实际 image 大小门、18键 sourceProof、owned child 和 v2 退出生命周期。ARM 读取并核对 384B descriptor、27B wrapper、904B source 和128B initial；13次限定 VirtualQueryEx 实录 SDK MBI，不假定 reserve 的 Protect。逐条按 case 顺序核实际代码、上下文、EFLAGS 分支与计算出的 EA/范围，外跳 `9a0` 在原指令 Continue 前因不符唯一后继而拒绝。真实 CALL/RET 栈槽、写前/写后实际值、AVX 重叠、非易失 GPR 与 RAX/RSP、最终128B、TF-only Set/实际回读、单 pending 三态和正常退出收口均有对应实现。所有 fault 均失败，无预期负成功。

输出为 `events.jsonl`、`parent-result.json`、child 所写 `child-result.json`；epoch 的18个 sourceProof 键位于顶层，summary 中为相同 `sourceProof` 字典。budget 键、`allowedRegion`/`allowedSpan` 与 `allowedRange`、原 MBI 字段按 root binding；wrapper transaction 的 `ownedLabel`/`ownedRva` 采用 root 新冻结值，helper 对这两项为 null。DWORD/WORD 的 raw 值输出 JSON 整数，EFLAGS 为8位 lowerhex；single-step calibration 区分 `sourceArena`、`childImage` 和未知地址，不将原代码投影到 child EXE。

本卡实际离线检查：冻结实际 header、root binding、生成表和旧 parent 的 SHA 与指定值一致；文本检查176目录行、六个 RIP 相对控制位移、两张 table 记录、五笔 store 和两处 `9a0` 目标；按冻结 C 表检查两条 case 的相邻目录后继、许可和控制读/写宽度；独立加总每条代码读取及固定检查得到 SSE 56次/1878B、AVX 58次/1929B；核对 sourceProof 18键、预算10键、13个 pageQuery 名、12个读取用途以及源码括号平衡。期间将 indexed table ordinal 草稿修正为87/163，区分了实际 source arena 与 child image 的 calibration，检查实际旧 witness 的 post EFLAGS 后不假定 TF 在退休后保持1。这些是静态文本与固定证据核对，不是 C 编译、模拟执行或真实原指令证据。

未执行构建、EXE、native CPU、Debug API、模拟器、客户端/进程、网络、Git、数据库或部署；未改 header、child、ASM、旧框架、生成器、构建器、validator、Project 或 Harness。尚缺 root 的实际编译与固定输入/依赖/PE 审计、独立 checker 和按授权实施的真实验收。任何编译、契约或现场失败均须保留失败并停止相应执行，不得以本卡静态检查冒充通过。没有新增客户端验收、自然 GC、第二分配或完整 Avatar 结论；本卡不安排后续 helper 或 stock 任务。

