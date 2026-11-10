# F-03d3s2 MPLevel 字典解析及空分支

以[前缀和置位原窗](CLIENT_72_D02_SCENE_MPLEVEL_PREFIX_20261111.md)为来源。置位后从当时 reader 内联读取 4 B，按 `(LE32 + 0xf8f43bce) mod 2^32` 得外层 count，推进游标 4 B。正 count 分配字典，调用对应初始化后先将引用写到 field350252，再处理各条目。reader 的条件 refill 分开记录。

| 顺序 | 可用于后继解析的静态合同 |
| --- | --- |
| 分组键 | U16 与 `0x57c0` 异或得长度。按原临时块路径处理完整 QWORD，再将同 reader、长度和字节交 `50379.m418862`。冻结原汇编显示该 helper 的 UTF8/UniqueString 路径和正常返回前按实际长度推进游标。零长使用已绑定空串。 |
| 子名称数组 | 子 count 为 LE32 与 `0x9cb545fb` 异或。各长度为 `((LE16 ^ 0xa88c) + 0x665a) & 0xffff`。依原顺序取子名称，经字符转换后按真实长度推进。零 child count 走已有 `string[]` cell，实际对象身份待验。 |
| 字典发布 | 将分组键及原序 `string[]` 交 `15787d2a0`，固定 mode byte 为 1。原调用正常返回后直接推进外层条目计数。该 callee 的实际重复键行为待验。 |

清位和置位 count=0 同调 `19861.m152274`。本轮从目标原件解析出 Scene usage 736806、MethodSpec 19390、instance 2671，并独立对照另一 Gadget 实例。公共 666 B 原窗中，缓存取得且类型核验通过时在 `154cd66fe` 写实际引用。缓存 miss 时分配、调用对应构造，再在 `154cd6787` 写引用。空指针写位于随后调用错误 helper 的路径。实际缓存对象和泛型构造未观察，成功分支的可省略语义仍未批准。

正分支 995 项静态断言，主线第三与双 fresh 均 74,815 B，SHA-256 `3162a55e75712cc55873cfecec31adfb9fd6640175bba1f52d2fb5f8ed2b5f19`。空分支三份原件双 fresh 一致，666 B/163 条，body SHA-256 `265da1ba231fc0fc3eaa23feda129c3c8254971f49a6da1adda792f3c0c7e403`。原件和 CLI 位于 `scene-mplevel-positive-analysis/audit.private.py` 和 `scene-mplevel-empty/collect.private.py`，执行时使用新输出名。

下一项需要实际 ConfigMetaConfig 的 PathHash.bin U64、对应原 payload 与 reader 状态，确认含 MPLevel 的完整分组 key 和原序子名称。已有候选 World JSON 和类型声明不能给出该对应关系。实际 World 返回对象/slot114、Pool/Scene、组件和正式发送各有独立前置。
