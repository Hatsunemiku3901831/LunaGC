# Proud 原热 Lookup 有效缺键样本（2026-11-11）

任务 `R3-PROUD-HOT-LOOKUP-MISS`。使用同一自有 map：先实际执行完整原 Store → TryInsert，一次构造单 entry；随后顺序执行三次原 Lookup → prepare → FindEntry。这里只报告固定有效桶和 `next=ffffffff` 下的条件缺键；等待主线第三次独立复核。

固定合同为私有 `hot-lookup-miss-case-contract.private.json`，36416 B，SHA-256 `4231abaa5331649247f78145d34f8a98ffb12ea2a96cbacbecc251fd2a383aeb`。其中实际列出 71 项来源，入口逐项核长度及 SHA；另核已验 Store 工具、五个客户端输入、离线 CPU 包与五个完整 body 的连续汇编字节/原三 E8。仅执行合同列出的逐 PC 序列；不匹配、预算、未声明访问或调用即停止并保留记录，不建 helper/model。上游最初描述“三个不同哨兵”与合同冲突；主线已明确撤销“不同”，三次均重置为冻结的 `a5a5a5a5` 并由原指令各自清零，两侧四字节守卫保持。

最终两次新进程 `fresh5` 和 `fresh6` 报告逐字节一致，SHA-256 `d287c635f023695aaf2ce472487f415bcbdd53fa2f5e7ddb864ad372c5d512c9`。原 Store 执行 122 条，三次 lookup 各执行 109、87、104 条；各自到达原 RET 和 sentinel，实际模型调用为空。首轮 original prepare 在 `154d4030e` 将 wrapper+28 从 0 置 1，后两轮不再写该位；每次原 `154d40195` 均单独将 out 的四字节清零。三次 FindEntry 实际返回低 32 位 `ffffffff`，Lookup 的 AL 为 0，不能要求完整 RAX 为零。FindEntry 入口三个完整 64 位 pair 均匹配，包括最高位为 1 的输入；查询前后字典、entry 数组和 buckets 全字节保持。

独立测试 `python -m unittest tools.migration72.test_proud_actual_adoption_hot_lookup_miss -v` 六项通过：另算三种 hash 与 bucket，核 Store 的七项数据写、20 B entry、12 B buckets、count/freeCount/freeList/version，逐次核原 prepare、out 写、完整 pair、原返回与 map 全字节保持，并在合同漂移时于 CPU 前拒绝。私有 `fresh1` 与 `fresh3` 是加入完整 pair 及额外显式状态检查前的成功探索，不用于最终确定性声明；未覆盖它们。

主线已实际执行 `main-third`，报告与最终两fresh逐字节相同、同SHA，另6项独立门禁通过；接受的局部结论仅为 `originalConditionalHotLookupMissExecuted`。同hash真命中涉及未具来源的Default Equals虚调用，未进入；不把异常正数bucket、next或越界index伪装为false，且本合同没有这些输入。真实row/producer、真实命中、自然interface dispatch、突破门槛、resourceReady、完整Avatar与D-02均未证明。没有客户端进程/attach、目标Win32 DLL、JVM/数据库/端口、共享构建或部署。后继仅按准确热输入缺口继续，不能以miss通过代hit。
