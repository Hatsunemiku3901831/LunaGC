# Reliquary 具名候选的有限类型关联

`R3-RELIC-NAMED-CANDIDATE-TYPED-ASSOCIATION-20261116` 只审既选27个方法、10个owner及其准确祖先字段、固定完整参数签名和74316的18个方法声明。问题是这些入口是否真正连接目标 `46431` 资源或 `74316.field368811/+20 → 46431`，从而可选一个rankLevel/setId消费者单窗。没有新PE/body/raw读取、E8扫描、18方法试取或对象树展开。

输入为既存27候选query（SHA-256 `398352b413b504a7c59ebac7beaf92d206bf47da1d9ea902a52255d345da60f7`）与解释（`9b9d8f451fd033a546c9a38a7745702427a09e7225bce4a90b4a933ae02552d4`）、`analysis/current-structures.json`（`d5d7f671a622439b2f9a3ed0018ce551aa5c573cd9ca68bed3b006712b8a98aa`）及 `evidence/client72-signatures.json`（`28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe`）。四原件均实际核完整hash，未执行旧工具或模型。

27个方法的**实际声明**都记录0显参，均有instance receiver；这不是以signature缺席推0参。其10个Mono UI owner沿准确parent链到System.Object，直接与继承字段中均无46431/74316引用。26个 `get_*` 有同名字段候选，其声明类型为Text、Image、GameObject、RectTransform、Scroller、UI list等；不能只因名字一致就声称getter返回它或已验body。另一个 `17973.m136537/GetScrollViewSuit` 同样无目标资源参数/receiver字段边，返回类型未恢复。现成analysis TXT按这27个准确地址查文件名为0命中，未把这次有限检查称全域无源码。

74316的18个方法单独按真实声明分类：16个0显参，`613912/14a344fa0` 的唯一显参是String，`613922/14a345370` 的两个显参是reader42005与 `MoleMole.NativeConfig.INativeAllocator`。完整签名原件没有恢复方法返回类型，缺失保持unknown；不以 `.get()` 的空结果或混淆名解释为void、0参或rank/set getter，也不按小body猜字段名。已知backing字段边仍真实，但没有把一个准确raw字段绑定为rankLevel或setId的新职责来源。

因此本次没有成熟的新consumer source窗口。最小解除输入仍是同target具名职责与某一准确46431/父字段的桥：例如真实消费源把该字段用于套装配置查找或明确星级/品质属性；或允许的同源实际输入与独立非零oracle排除rank/set交换。旧Go JSON模型、UI控件名称、标量同形或候选表相同不能解除。该普通来源缺口不封锁该族已有parser/槽位成果，也不把潜在纯host行语法直接标外阻塞。

原始有限关联输出为 `debug/d02-skill-energy-search-20261116/post-depot-gap/relic27-typed-association.private.json`，53747B，SHA-256 `6c709c56a80d7502434cf69ca1f9a6e5332a266a54bf3abb689317dc451adc59`，逐项保留receiver祖先、字段类型、参数、返回unknown和边界。无实际新值、Java投影、ready或客户端验收；后续只有出现真实准确桥才另审最小source输入，固定hash冲突或准确安全拒绝即停止相关动作及依赖。
