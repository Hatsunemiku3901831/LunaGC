# F-03d3f4d2 / CLEAR48-FULL-VALIDATOR-V1

主线已完成集成及两组人工 native 验收。下文保留子包交付时的状态，最终构建、BOOT 修正与验收见 [人工首组验收](CLIENT_72_D02_CLEAR48_FULL_BOOT_CONTRACT_20261111.md)。

本卡为纯文件工具和 synthetic 测试；已通过两 case 的完整 synthetic CLI 正例及定向变异，**仍须主线独立源级与接口审阅，不能单凭本卡作为执行或接受门**。本卡不构建、运行映像或操作客户端、进程、网络和数据库。真实 owned 实验的许可与状态由主线维护。

artifact 工具从指定的自有 PE/map、五源构建快照、固定合同和实际 header 审核 wrapper、XCR、384B descriptor 区段与范围，生成新的 256B bootstrap 和镜像。主线还须独立审核 caller/unwind 与实际依赖。

validator 独立检查 sourceProof、固定输入/依赖/receipt、页、记录与事务、TF、child API 和 v2 退出账。两 case 的 synthetic CLI 正例使用人工 PE 和明确标记 synthetic 的构建记录，固定来源与旧112输入真实哈希；两轮变异覆盖 schema、PC、EA、漏重步、身份、BOOL、TF、GPR、页、预算、pending、日志、退出、关闭、receipt 及 AVX 重叠旧值。23 项 Python unittest 通过，包含四项 artifact 测试。所有进程/事件、PE 和执行结果均为人工数据，不是 native 证据。

当前未闭：与最终冻结 parent emitter 的源级接口审阅、主线独立复核。禁止仅凭本文件、synthetic 通过或 producer 自报提升 owned helper、自然 GC、第二分配、Avatar 或 D-02 验收。日志失败及 Daybreak/安全拒绝按原合同停包，不换机制重试。首次综合变异把 system breakpoint 的 GPR 改动误当作 Arm 保持约束，未拒绝；测试改为实际 Arm GPR 后通过。另一次测试输出名与 receipt 同名，被独占创建拒绝，改为独立名称后通过；这两次均非 native 执行。
