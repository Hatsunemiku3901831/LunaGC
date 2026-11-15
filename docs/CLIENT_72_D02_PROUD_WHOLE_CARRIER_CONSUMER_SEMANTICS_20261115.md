# Proud whole carrier consumer 有限物理值流

`R3-PROUD-WHOLE-CARRIER-CONSUMER-SEMANTICS` 已完成限定静态验收：主线审阅[1239B来源](CLIENT_72_D02_PROUD_WHOLE_CARRIER_CONSUMER_WINDOW_20261115.md)的314条及五个CALL。原 entry R9 List 在 `f09d/f0a0` 传回已验 `69135.m572226` 的1770B String gate；该路径直接复用，不扩成新循环。R12 在 `f120` 改为 `1500d7730` 返回、在 `f323` 改为 `&stack40`，后续不能称原List。

## 五个局部 Float getter 调用

下列原E8均指向既验 `14f905140`；均将 `R15+10` 所指DWORD数组的一个元素复制到 `stack40`，以该4B槽地址作RCX，XMM0正常输出用于Single加法或74940写入参数。

| CALL site | 原E8 | 本地正常用途 |
| --- | --- | --- |
| `14758f164` | `e8d75f3708` | ADDSS stack44，再传写入 |
| `14758f260` | `e8db5e3708` | Single类型门后ADDSS对象+10，再传写入 |
| `14758f2d4` | `e8675e3708` | 直接传写入 |
| `14758f337` | `e8045e3708` | ADDSS stack44，再传写入 |
| `14758f3cd` | `e86e5d3708` | 直接传写入 |

这是五个独立的局部caller来源及用途，不能视作全项目caller覆盖；不重取166B或重跑既有419断言，也不回填失败的common-E8全域扫描。记录 `local/migration72/d02-resume-20261115/proud-whole-carrier-consumer-semantics/float-callers.private.json` SHA-256 `1c4126cb810373c8b2c8f5a43a69bbb0f0576a519adcc47d6e1f41927014c5d7`。

R15来自 `24683.m191199 / 14eb95400(String)`，formal return未知。它作为43346参数位置及 `+10/+20` 的布局是typed上下文，不绑定26072/59500实际数组或Proud row。实际stored/seed/table/M/epoch/数值及native ABI均未知；目标数组业务consumer仍缺。

`R3-PROUD-CARRIER-572215-SOURCE-PLAN` 已限定完成，按有限候选顺序选择69135.m572215；来源单窗进行中，不能视为业务概率排序或提前认定结果，572222仍未关闭。A/B、preview与 `1488a5772` 后段冻结不变，D-02 / F-05 / D02-R6 仍0/6；本语义整理没有新取源、测试或目标执行。
