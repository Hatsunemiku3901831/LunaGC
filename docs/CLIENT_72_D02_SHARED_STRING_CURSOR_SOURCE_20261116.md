# 共享 String helper 的独立游标来源

`R3-SHARED-STRING-CURSOR-SOURCE-20261116` 为Material/Weapon纯host行语法取得一个准确静态源。该helper早期为Skill raw-id提出后因不必需而取消，未执行，也不是安全拒绝；本轮不新增raw-id前置，不复活旧模型。

固定metadata唯一绑定 `50379.m418862/OEJADFIGFAC@14f0de820`，static三显参：reader42005、uint32 N、uint8*。当前GAME_PATH和固定五stock、fresh AOT slot418862、完整14344080B exception实际核对，唯一exact-begin/containing/overlap确定 `[14f0de820,14f0de91d)`、253B；body前boundary delta为2837B，SHA-256 `a4ec4c9bef85805a177d60f87ab830ed866c7b6e16b33b5384899f1c76528f9f`。一次完整body SHA-256 `f92001ee01d9c3df257f334051c7d213500f74e5bdfd95f33066051d25f69a1a`，strict重拼70指令/23引用；未执行目标、String/ctor/parser/getter/TLS或模型，未读callee/global/xdata/payload。

准确冻结CALL为 `14f0de8bb → 1405336c0`；仅索引该边，停止其返回/构造依赖。**整窗semanticReviewAllowed仍false**。另有独立标量事实：入口 `82d` 将原N保存EBP，`82f` 保存同reader到RDI；共同tail `904` 检查reader非空，`909` 执行 `ADD DWORD[ RDI+1c ],EBP`，之后正常RET；null到 `917` 原错误入口。正常到达该tail且reader非空时，`P_after = U32(P_at_tail + N)`。该表达式不读取RAX、输出String或A返回；N是同byte[] reader的字节索引增量，不能解释为UTF16字符数、UTF编码或实际文本。String/cache/TLS路径和callee成功均未由此验收。

因此，允许在**固定raw身份、准确文件cursor、caller长度token/presence及完整物理范围**已独立成立时，纯host语法按N跳过对应字节；不需要运行被冻结CALL来获得这个位式。该产物不生成String、文本或构造对象，也不能替换helper去恢复原native parser。零N只表示该字节增量为0，不能补空串/default；越过实际row/payload边界即停，不猜长度或改flag。

对Material，它解除共同父String的“已证长度之后如何推进”这一来源缺口；[有效backend/filebegin与+18](CLIENT_72_D02_MATERIAL_HEADER_SOURCE_20261116.md)仍未闭，不造offset0或执行header4B。对Weapon，已有首11101行 `[4,400)` 可作为后继依赖审查的候选锚；是否能从400定位一个真实新字段，还须核准确parser、framing、字段token、数组单位/顺序与首span来源，不能把本helper自动升级为全表walker许可。取得单字段成熟合同再单独审host读取。

原件根为 `debug/d02-shared-string-cursor-source-20261116/`：source capture result8179B，SHA-256 `668686be91e0b8ec2723698e057166ef8d8846217f3d26d5046290090a5a3e11`；manifest1629B，`0c8fe8935fbf3f4ac1e77ec9af7741f19dbd79cf88a5283185e6e2a899c6b7e2`；独立语义9482B，`39c69b8a1661026b62d7e9ddb0ee34733cac2c58b5d8331ffbc64118be87bc15`。完整listing为12014B，SHA-256 `fed84cc003b7537b05448e02b7f2c5eaac2c6ddacde6c27b79c7404bc0c91efd`。driver只在忽略目录，无新跟踪工具或运行资源变更。本子项完成仅限source与独立游标关系；首次固定输入/边界/strict冲突或安全拒绝停止相关准确动作及依赖，A/B/preview、父项未验及客户端验收范围保持。
