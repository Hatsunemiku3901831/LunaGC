# F-03d3f4d2 / CLEAR48-TRACE-EXIT-LIFECYCLE-V2-VALIDATOR

## 状态和范围

完成离线 validator 与独立 synthetic fixture。冻结设计依据为 `docs/CLIENT_72_D02_CLEAR48_TRACE_EXIT_LIFECYCLE_CONTRACT_20261111.md`（SHA-256 `cbeaa19898ee74e7f5f1d71db5d1844f459aff3e15da26f7dabc11d7366fb83b`）及本机私有 supplement（SHA-256 `cf52cfafa00eab47316a756a5e04a1edc59ca2b5606d4fa89d6cc2cb552105d8`）。此交付没有执行 native、Debug API、客户端或旧 run 的验证，不改变旧 W1 失败结论；synthetic 通过不是实际执行证据。

`check_inputs` 的旧 bootstrap/四 JSON 输入和返回的 `sourceProof` 不变。新增 supplement 的绝对解析路径及 SHA 由 `check_dependency` 在 review.source 和原 before 哈希链中验证，不添加 artifact。epoch、summary 和成功/失败报告均需要或标识固定 policy 和 supplement SHA；summary/report 采用各自 v2 schema，旧 epoch 不回填。

验证器从实际事件、匹配 wait/Continue 和返回 BOOL 重建线程来源、退出状态以及借用句柄的 API 合同释放；报告中 `borrowedReleases` 是合同推导，非内核 CloseHandle 观测，NULL 句柄无释放条目。TRACE 的操作、事务保持 primary；由完整退休、末 stop 的 TF 恢复及正常 Continue 确定 WAIT_EXIT 边界。正常尾接受规定的有限通知并以实际 EXIT 收口；W3 的原 UD2 和 Terminate 后所有实际 drain 逐事件关联，不固定两条。保留原操作数、源字节、寄存器、栈、EFLAGS v2、结果和预算检查。有效文件要求真实 raw/origin、身份、非空路径、非零镜像大小和成功关闭；PI cleanup 检查实际 handle 和退出码。

## 实际测试

`python -X utf8 -B -m unittest tools.test_client72_d02_trace_validate`：**18 个 unittest 方法，退出码 0**。均为 synthetic，含子例：W1/W2/W3 基础事务；不同线程创建/退出次序及最终非 primary；NULL 借用句柄、64 位原值、DWORD/WORD 边界、有效/NULL DLL、type 7/8；TRACE 前置边界；重复/未知/已退出 TID、wait/Continue 与 primary 操作身份；异常事件和失败 cleanup；PID 句柄与退出码；原 EFLAGS、向量、源、栈和预算；CLI 假依赖明确 mock 新 supplement 路径和 SHA。128/129 总事件上界有 synthetic 回归。人工 fixture 并非 parent emitter，覆盖不能替代对实际产生日志代码及运行结果的独立交叉核查。

私有测试回执为 `local/migration72/d02-resume-20261110b/a-clear48-trace-exit-validator-v2/host-unittest-receipt.synthetic.json`，记录命令、退出码、输出和源哈希。最终源 SHA：validator `a1b0ca35cfe1a0ee86b7ee9291f7e1026f5b2358d1a26c4a09bd1be5be7d37ae`；test `6e77745decc7385d97d1e59c2b8881018ed0e4b387d3238ffc7a1cf47440e9fc`。

## 待主线验收

由 root 独立审核两个实现与 runner 的真实字段/调用接口、执行冻结依赖审核及其适用的纯测试；之后按主线既定分阶段门处理构建、source/PE 和单次 W1。W2/W3、实际日志、实际 cleanup 和客户端均未由本包验收。本专题不授权任何 native 执行，也不把历史失败修改为成功。
