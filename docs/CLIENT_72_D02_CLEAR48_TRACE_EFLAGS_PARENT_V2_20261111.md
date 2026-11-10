# CLEAR48 TRACE EFLAGS：parent V2 源码交付

任务 `F-03d3f4d2/CLEAR48-TRACE-EFLAGS-V2-PARENT`，2026-11-11。根据主线已采纳的私有实现计划，仅修改 `tools/client72_d02_native_trace/parent.c` 的 `set_tf`，并交主线独立审核和集成；本文件是源级记录，不是运行验收。

## 唯一差异与来源

冻结的 build-main3 `parent.c` 为 74416 字节，SHA-256 `e5b60ee368d724e947e2aad0d12fa2cbe1fe6201506d3775f441cc02a6f67cea`；修改前工作文件同哈希。新源 75342 字节，SHA-256 `781e5ee0c22f2d7d1583f6b59d692db827af03a84e343081cea92a2640771c7e`。以冻结文件为基线的纯文件逐行差异仅在 `set_tf`：

- 提交仍复制同 stop 的控制字段，并使用 `(before->EFlags & ~C48_TF) | (tf ? C48_TF : 0u)`；无其他写入或 API 调用变化。
- 成功回读后计算原始 `submitted.EFlags ^ readback->EFlags`。差为零分类 `exact`；仅提交 bit1 为 1 且差恰为 2 时分类 `reserved_bit1_1_to_0`；否则为 `rejected`。只有前两种可使 EFlags 合取项通过，15 GPR、SegCs、SegSs、Rsp 和 Rip 原合取项保持。
- Set 记录增加固定 `eflagsReadbackPolicy` 值 `c48-tf-only-reserved-bit1-readback-v2`；成功回读时以 `%08lx` 输出原始 32 位异或及上述分类，否则两个字段均为 JSON null。原 before/submitted/readback 不归一或覆盖。
- 与冻结文件对比，`set_tf` 外无差异；Get/Set 组、计数/ordinal、stop、事件状态、预算和清理逻辑未改。四 JSON、header、child、ASM、构建/validator/runner/旧原件未由本包修改。

## 人工真值表与静态审核

下表是按源码表达式人工推演，不是执行结果；接受仅指 EFlags 回读条件，其余字段与事件约束仍须满足。

| B → S → R（十六进制） | S XOR R | 分类及结果 |
| --- | --- | --- |
| 202 → 302 → 302 | 00000000 | exact，接受 |
| 202 → 302 → 300 | 00000002 | reserved_bit1_1_to_0，接受 |
| 302 → 202 → 200 | 00000002 | reserved_bit1_1_to_0，接受；覆盖清 TF |
| 202 → 302 → 200 | 00000102 | rejected，TF 未回读成立 |
| 202 → 302 → 202 | 00000100 | rejected，TF 未回读成立 |
| 200 → 300 → 302 | 00000002 | rejected，反向 bit1 0→1 |
| 202 → 302 → 301 | 00000003 | rejected，另有 bit0 差 |
| 202 → 302 → 308 | 0000000a | rejected，另有 bit3 差 |
| 任意 S → S XOR 非零且非 2 的掩码 | 相应掩码 | rejected；其他单个位及多位变化均不在例外内 |
| Set 失败或没有成功回读 | 不存在 | 异或/分类为 null，原失败路径保持 |

输入 `202 → 300` 本身改变 bit1，违反提交合同；上表回读分支不能授权该输入。对于合法输入，以上两种接受关系均保持 TF；GPR、其他控制字段或关联异常不能因 EFlags 分类而通过。分类是 EFlags 原始关系，不声称整个 Set 事务通过。旧 W1 按旧合同仍为 exit 2、零退休，未回写或追认。

本包实际仅做冻结 SHA/大小、修改后 SHA/大小和源码逐行差异的文件静态核对及上表人工推演；未编译，未调用 native、CPU 探针、Debug API、进程、网络、Git 或数据库，也未运行 validator 或客户端。无新客户端验收；真实调用、编译/PE、日志生成及独立负例仍待主线新 epoch 核验。未改生产服务、配置或资源，不涉及部署。主线负责 Project/Harness、独立 validator 交叉审核、构建和后续 owned 验收及本地提交。
