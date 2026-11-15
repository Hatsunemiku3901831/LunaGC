# Proud String 查询与双字段判定的限定语义

`R3-PROUD-STRING-HELPER-SEMANTICS` 与 `R3-PROUD-CARRIER-572232-SEMANTICS` 已完成限定静态验收：主线结合[helper来源](CLIENT_72_D02_PROUD_STRING_HELPER_WINDOW_20261115.md)、[572232来源](CLIENT_72_D02_PROUD_CARRIER_572232_WINDOW_20261115.md)的完整指令审阅。此结果不批准OpenConfig、目标配置同源身份、实际值或resourceReady。

## String helper

正常模式且classinit完成或正常返回的条件下，`GLOBAL+331e0` 对应34315.field168277声明的static `Dictionary<String,60415[]>`。原输入String原样送 `15787cdc0`；返回index小于0时物理RAX为0，index非负且bounds正常时读取 `A=[D+30]`，返回 `QWORD[A+24*i+30]`。

命中槽位本身可以为null，因而RAX 0并非唯一miss证据；null String仍交给未知helper，不能推为miss。D/A为null或越界走guard，不是正常零返回。shared-generic内部、formal return/hidden/native ABI及实际classinit状态仍未知；不因此扩展writer/init/cctor或完整加载树。

## 572232 的String双匹配

本体以 `entry RDX=S1`、`entry R8=S2`、`entry R9=L`，按版本/backing/bounds门枚举L。row的目标String或native备选进入同一helper，其返回按array-like使用。过滤类型由固定metadata usage707979/source498582绑定为**17390**，不是44638；其 `field82353/+18` String对S1、`field82354/+20` String对S2，分别经过指针快捷路径及非空/长度/String比较。任一合法元素双匹配可触发命中分支。

命中只保证 `BL/AL=1`；EAX高位未归一，不能称完整EAX为1或formal Bool。遍历耗尽才清EBX并返回EAX 0。正常body无目标双数组直接读取、无whole List转交；IFix不作替代。目标业务、actual M/row/entry values/current/epoch均未知。

完整固定值流记录 `local/migration72/d02-resume-20261115/proud-string-lookup-and-gate-semantics/semantics.private.json` SHA-256 `beb065431076d3bc2dcfc120c454d0925e094d8c60f9f48a85479255515826eb`。本次没有新增PE读取、测试或目标执行，不重跑[原中性输入合同](CLIENT_72_D02_PROUD_STRING_INPUT_CONTRACT_20261115.md)的14项旧检查。原52 RPM/298B范围不自动覆盖String；A/B、preview和准确CALL依赖保持冻结，D-02 / F-05 / D02-R6 仍0/6。
