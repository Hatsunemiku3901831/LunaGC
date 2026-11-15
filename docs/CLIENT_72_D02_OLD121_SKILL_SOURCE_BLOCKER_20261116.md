# 旧号 Skill 构造器来源边界与解除条件

`R3-OLD121-SKILL-CTOR-BOUNDARY-TRIAGE` 已完成限定归因；`R3-OLD121-SKILL-RESOURCE-CTOR-WINDOW` 仍因缺少完整边界来源而阻塞。该停止是普通 source-only 边界门失败，不是新安全拒绝，也不泛封全 Skill、R3 或 A 以外的独立工作。

一次采集中，五 stock、pins、metadata 和 fresh AOT 通过，确认 `88602.m732415/.ctor` 入口为 `149b59940`，入口身份不提供长度。完整 exception 目录没有覆盖该入口的 RuntimeFunction，`containing=[]`；`beginMultiplicity=[]` 是前者的派生投影，不是另一项独立负证据。原规格的唯一 pdata 门按记录停止；审阅既有记录和门逻辑未发现索引、地址换算或 gate 错用，未重新扫描目录。选中 pdata、body 和 body reads 均为 0，无 code hash、指令或引用索引；没有重试，旧 manifest 和来源摘要未创建，不补造。

对 15 个既有 source 索引及 34 份 Avatar listing 的有限查询未发现该 ctor 的精确完整 body/边界；方法声明或下一 metadata 地址 `149b59950` 均不能给出长度。既有 Skill row parser 原窗及 `+68` ID、`+54` element 的原职责继续复用，但不能代替 ctor 初始化来源或证明新行值。[Microsoft x64 ABI 的 unwindability 说明](https://learn.microsoft.com/en-us/cpp/build/x64-calling-convention?view=msvc-170#unwindability)表明 leaf 可不需要 pdata/xdata；这是一般 ABI 可能性，不能据此认定本 ctor 为 leaf，亦不提供完整长度。

解除此单窗缺口须提供**同一固定 PE 的 `m732415/149b59940` 可追溯完整 `[begin,end)` 或长度来源**，或者提供此前已验且同输入的精确完整 body 与边界原件；如使用新来源，先交主线审定新规格。不得放宽旧门追认失败，也不能猜 16B、扫至 RET、借 Avatar 构造器或用 host 零存储替代初始化证据。

当前 Depot 的 369 个正 Skill 需求与保存 Skill 的 389 个键属于不同集合；其中 2 个旧出生结果复用，367 个新值尚未批准。whole A 原记录计数为 1177，旧摘要不覆盖；这不证明所有单行路径依赖 A。

固定私有证据：`local/migration72/d02-resume-20261115/old121-skill-ctor-boundary-triage/reasons.private.json` SHA-256 `a6f5663ca2bd8abfb90c34b192c8b36c351803b17e32deb81a39865092dcc429`，同目录 `结论与解除条件.zh.md`；原失败及边界、AOT、header、stock、pin 原件位于 `local/migration72/d02-resume-20261115/old121-skill-row-ctor-window/capture/`。原 source-only 规格 SHA-256 `bf72e96bf2e762ba56a238b24c2288ea22d3d9bef7e0c4aafcf5ab56f69ec72e`，保持不变。

本轮归因未重读 PE、重取窗口、执行 native、Skill Java 或客户端；A/B、preview 及准确冻结边保持，未取得 ready，D-02 / F-05 / D02-R6 仍 0/6。
