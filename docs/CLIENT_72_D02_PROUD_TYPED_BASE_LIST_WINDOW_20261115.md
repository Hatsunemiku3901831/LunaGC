# R3-PROUD-NARROW-TYPED-CONSUMER-SOURCE：单窗来源验收

状态：**已完成（仅静态来源）**。本任务限定为 owner `69135.m572230` 的一个 typed 方法窗。声明为 instance 方法，flags `0x81`，三个显式参数；formal 返回类型和 native ABI 未恢复，不能据此指定寄存器、推断返回值或宣布 Proud 字段消费桥成立。类型依据为 Avatar 与两个 `List59500` 参数，以及 `59500` 持有数组和 `26072` base 的既有 metadata 关系。

固定来源的完整方法体为 346B、91 条指令，SHA-256 `20759142b30c2710e63f270aa30db8e93e8eb393224aeff6608e1a39913499dc`。单窗证据由 AOT 16B、pdata 12B、方法体 346B 共 374B 组成；header 4616B、异常目录 14344080B 和完整文件 hash 读取分别记账，不混入 374B。双 fresh 与独立 main-third 的 13 件产物逐字节一致；已有五项纯测试的两次记录经审核，独立审阅没有重跑该测试。12 个 call、15 个 branch 与 5 个 RIP 引用共 32 项由独立算术重新核对一致。

集成回执 `local/migration72/d02-resume-20261115/launch-timeout/live-typed-integration1/typed-completion.private.json` SHA-256 `01d9590a395e38d7cf32a3d9d2909825fa7d2d65ce6bad93478cd7dc1025cd6c`；纯产物审计 `typed-audit.private.json` SHA-256 `4a556d57fc414ef82b606b17c528b67bf07fa415ca9ee47cbde2d43dc5ba1621`；独立引用重算 `typed-independent.private.json` SHA-256 `6f00ab1d006b2a291da8d930edb77a3320443792fc2e2cccb8fde78ff23459e6`，两者位于同一目录。原始双 fresh、第三份材料与固定请求位于忽略目录 `local/migration72/d02-resume-20261115/proud-typed-base-list-consumer-window/`，供已授权的只读复核使用。

本次没有采集 callee、global、xdata 或目标进程数据，也未消费 A/B 拒绝依赖。对冻结 preview 的直接边计数为零，**不证明传递路径无关**。本来源不证明实际 M、三字段消费桥、同 epoch、row、current 或 resource ready，也不提升 D-02 客户端验收；另立的 `R3-PROUD-TYPED-BASE-LIST-SEMANTICS` 正在分析已有来源，结论以其独立专题为准。
