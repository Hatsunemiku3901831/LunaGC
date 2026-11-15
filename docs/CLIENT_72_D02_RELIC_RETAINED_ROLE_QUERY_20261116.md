# Relic rank/set 的既存具名入口查询

`R3-RELIC-RANK-SET-RETAINED-NAMED-ENTRY-QUERY-20261116`已完成一次有限查询。固定89748条声明和718579条business方法索引，只匹配Relic/Reliquary/Artifact与rank/setId/setConfig/setBonus/setEffect/setCount/suit/rarity名称，并单列46431/74316/88968/34113四个准确边界。未读取PE、metadata二进制、资源、任何新body，未扫18个混淆方法或E8。

| 实际结果 | 可证明范围 |
| --- | --- |
| 27方法及同一27条business记录 | 来自Mono UI owner，主要为控件getter及`GetScrollViewSuit`；名称不建立raw46431字段→rank/set职责。没有把控件名称当资源consumer。 |
| 37字段 | UI控件声明及5个enum literal；其中具名rank/套装literal不绑定资源字段。 |
| 四个准确边界的具名role 0 | 74316.field368811/+20→46431引用边可复用；18方法仍为ctor/混淆名称，不能依布局选一个新body。 |
| 精确rankLevel等具名方法0、SetId getter0 | 补充宽`setid`子串产生SetIdle/普通set_id等假命中，原件保留并按准确名称分开，不能报成资源套装来源。 |
| 保留analysis txt 2行 | 仅`SetReliquaryStarStateReq/Rsp.proto`参数文件名，不是资源rankLevel/setId读源。 |

声明方法schema实际有`parameterCount`、flags、slot及registeredAddress；缺少parameters/returnType的早前三声明诊断值为unknown，不表示零参数或void。该次仅名称查询，不完整恢复签名或证明这些候选本体不消费资源。主线已将27候选交Sol继续判断是否有准确typed consumer入口；不据“未读body”宣称最终外部阻塞。

固定源`local/migration72/analysis/current-structures.json` SHA-256 `d5d7f671a622439b2f9a3ed0018ce551aa5c573cd9ca68bed3b006712b8a98aa`，`business-methods.json` SHA-256 `6e574e15c822cd3572e1f64f82d53653aec67d6daeb82ae3a0468eecf75c9b3b`。原角色候选/标签互换反例复用[已有缺口](CLIENT_72_D02_MATERIAL_RELIC_ROLE_BOUNDARIES_20261116.md)；[本轮公开检索](CLIENT_72_D02_TARGETED_REUSE_SEARCH_20261116.md)的Go JSON `Set()/Level()`仍非本目标字段桥。

原件在`debug/d02-skill-energy-search-20261116/post-depot-gap/`：query44632B，SHA-256 `398352b413b504a7c59ebac7beaf92d206bf47da1d9ea902a52255d345da60f7`；宽子串补充SHA-256 `e2099197bdd1e9519f4dd18bc589c755f2f62cbf240c12994c374d84881ba039`；分流解释SHA-256 `9b9d8f451fd033a546c9a38a7745702427a09e7225bce4a90b4a933ae02552d4`。准备及查询的完整JSON读/hash次数另账，OS/loader IO未全计；一次性脚本留忽略目录，不新增通用工具/测试。

最小角色输入仍是：同target准确字段或getter到rank等级查找/套装累计用途的具名consumer/property，或可排除同型标签互换的独立非零role oracle；真实取值另需同row输入与cursor。角色来源两者可独立推进；本查询无新拒绝、无值/Java/Player或ready，不泛封Relic或项目。

后继[27候选有限typed关联](CLIENT_72_D02_RELIC_TYPED_CANDIDATES_20261116.md)已完成：准确参数/receiver祖先字段仍未建立46431/74316资源边，没有成熟rank/set consumer窗。该结论基于有限类型核查，保留body/return未知，不把UI字段同名推为实际getter返回。
