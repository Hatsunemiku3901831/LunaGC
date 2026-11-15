# Proud carrier consumer 有限 String 值流

`R3-PROUD-CARRIER-CONSUMER-SEMANTICS` 已完成限定静态语义验收：主线结合[1770B来源窗](CLIENT_72_D02_PROUD_CARRIER_CONSUMER_WINDOW_20261115.md)的完整410条及详细值流审阅，确认一条有限 String consumer 关系。该关系不批准 `OpenConfig` 或其他业务名，也不证明实际 M、row、current、epoch 或 resourceReady。

## String 及 gate

本体从 `59500.field295067/+20` 取得 26072，再读取其 `field127898/+20` String；managed 对象为空的替代路取 native `+8 & ~ff`，经 `148ba9a90`。两路所得值传给 `34315.m287516 / 151bbd710`。该helper返回在本体中按 array-like 使用，formal return 及 helper 内部合同未知。

既有固定 metadata 的 usage717745 绑定到继承60415的44638。类型匹配元素的 `field220427/+30` String 与物理 entry RDX、`field220424/+18` String 与当前枚举元素 K 双匹配时，控制 gate 成立。**K 不是 entry R8**：本体在 `14c51108f` 以枚举槽覆盖 R8，`14c5110c4` 保存该值；entry R8 另存并参与另一 fallback。这里的比较及控制关系不赋予字段业务语义。

## 数组、数值与未知

本体未直接读取 `59500.field295065/295066`、26072的两个数组，或43346的数组/String；物理43346输入仅见 `+34` bool。另一数值路径在列明条件和正常返回下按原顺序计算 `(F0+A)*(B+C)`；C 的实际位值未知，不能代为1。`1488a5af0` 与其他helper的内部合同未知，不能凭调用形状推断其值流或业务。

私有 `local/migration72/d02-resume-20261115/proud-carrier-consumer-semantics/semantics.private.json` SHA-256 `2de1b37d5722cf6d11f780b3a95f9b7d9ec853377ec1a324a42b38a4193f8b0d` 保存完整来源和值流；同目录 `conditional-spec.private.json` 仅为**未执行**的条件向量，不是测试结果或 Java 实现许可。本次语义整理没有新取源、目标执行或测试。

后继 `R3-PROUD-PARAM-ELEMENT-CONSUMER-SOURCE-PLAN` 已限定完成：`33507.m281263 / 1488a5af0` 的typed理由来自 owner 的 `IList<54724>` 与本体直接调用，但该列表是否来自 carrier 数组未知。单窗来源任务另项进行中，未预称结果。A/B、preview冻结及其准确依赖继续有效；D-02 / F-05 / D02-R6 客户端验收仍0/6。
