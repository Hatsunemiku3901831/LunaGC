# F-03d3f4d2 CLEAR48 同 epoch 条件差异

对照[同 epoch 实采](CLIENT_72_D02_GC_DEFAULT_STOCK_CAPTURE_20261111.md)的 42 个稳定跨度和[人工 helper 实测](CLIENT_72_D02_CLEAR48_FULL_BOOT_CONTRACT_20261111.md)。stock 的第二次分配为 request56、bin4、独立 record、L64/flags8。CLEAR48 需要 `I=6`、`T0=524288` 和实际 AVX 表项 `01fdfb94`，三个控制跨度共 16 B。原条件在 `141fdfa9a` 取跳转，跳过人工 AVX 用例的四条指令和 T1/F 读取。候选路径 36 条指令。人工 AVX 的 40 条已实际退休，证明其共有写尾部及顺序。

stock 静态写域为第二块 `H2+0x20` 的 32 B，随后 `H2+0x10` 的 32 B。并集 48 B，重叠 16 B。该 epoch 的原 48 B 含 6 个非零字节，早期 A2 的 18 个字节属于另一 epoch。人工两组真实 CALL、RET 和写域已验。stock 条件下的原写和返回待取得证据。

第二 allocator 与热 Default 的已知数据操作数均可用同一 epoch 提供。后继先验证独立受控 allocator 返回 H2，再验证 201 B 热 Default 对该 H2 的两笔写和返回 opaque D。自然 caller、线程及 GC 所有权仍需真实事件归属，D 的对象身份另验。完整构造的下一个对象写为 `15183fe99`，随后 `15183fe9d` 需要真实 AvatarThis。20 处直接分配与 12 个 class roots 有静态清单，本 epoch 只取得前两处分配的数据。

主线在 `local/migration72/d02-resume-20261111-asc/a-stock-delta/` 第三次执行 `audit-stock-delta.private.py`。三次均 6,011 B，SHA-256 `76a0d56c5376d30015780d4588afc08bc68fbc9e113053bf81e1c3f33b3bf052`，核 31 个首 pop 读槽、原两人工 trace/RET、stock 分支和 201 B 热路径的静态范围。
