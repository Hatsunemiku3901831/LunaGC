# 共享 String helper 的独立游标来源

`R3-SHARED-STRING-CURSOR-SOURCE-20261116` 为Material/Weapon纯host行语法取得一个准确静态源。该helper早期为Skill raw-id提出后因不必需而取消，未执行，也不是安全拒绝；本轮不新增raw-id前置，不复活旧模型。

固定metadata唯一绑定 `50379.m418862/OEJADFIGFAC@14f0de820`，static三显参：reader42005、uint32 N、uint8*。当前GAME_PATH和固定五stock、fresh AOT slot418862、完整14344080B exception实际核对，唯一exact-begin/containing/overlap确定 `[14f0de820,14f0de91d)`、253B；body前boundary delta为2837B，SHA-256 `a4ec4c9bef85805a177d60f87ab830ed866c7b6e16b33b5384899f1c76528f9f`。一次完整body SHA-256 `f92001ee01d9c3df257f334051c7d213500f74e5bdfd95f33066051d25f69a1a`，strict重拼70指令/23引用；未执行目标、String/ctor/parser/getter/TLS或模型，未读callee/global/xdata/payload。

准确冻结CALL为 `14f0de8bb → 1405336c0`；仅索引该边，停止其返回/构造依赖。**整窗semanticReviewAllowed仍false**。另有独立标量事实：入口 `82d` 将原N保存EBP，`82f` 保存同reader到RDI；共同tail `904` 检查reader非空，`909` 执行 `ADD DWORD[ RDI+1c ],EBP`，之后正常RET；null到 `917` 原错误入口。正常到达该tail且reader非空时，`P_after = U32(P_at_tail + N)`。该表达式不读取RAX、输出String或A返回；N是同byte[] reader的字节索引增量，不能解释为UTF16字符数、UTF编码或实际文本。String/cache/TLS路径和callee成功均未由此验收。

因此，只有在**固定raw身份、与tail对应的准确文件cursor、caller长度token/presence及完整物理范围**独立成立时，纯host语法才能复用这条N字节推进关系；不需要运行被冻结CALL来获得这个位式。不能仅凭尾ADD把 `P_entry` 升为 `P_at_tail`：本253B虽没有local refill或早期显式reader cursor/base读写，也没有向本地callee传reader实参，但这不是外callee副作用审计。该产物不生成String、文本或构造对象，也不能替换helper去恢复原native parser。零N只表示该字节增量为0，不能补空串/default；越过实际row/payload边界即停，不猜长度或改flag。

对Material，本项只补共同父String的尾部字节增量来源；[有效backend/filebegin与+18](CLIENT_72_D02_MATERIAL_HEADER_SOURCE_20261116.md)仍未闭，不造offset0或执行header4B。输入到tail的准确cursor关系也须按具体语法路径保留，不能将该条件源称所有String入口净推进已闭。

Weapon后继已完成有限只读判断。固定128936B payload SHA-256 `cca19efbe032e5416d78c28fcb69c2ea2d9707187197aa4ed90d869c3a1ff918` 的旧whole报告 SHA-256 `1e9817cbea6da6db5ee564d5c3f3a64436fa3319e6d9b8ccf5aef0014cf85a86` 已验288键/EOF。first11101的 `[4,400)` 是 **87097 wrapper parser `154498500` 的entry/return cursor跨度**；准确observer源码44027B、SHA-256 `920395dfd4817523951d2c766167ca481d8f6b2b64288980fee2cade2ae9ecad` 与旧报告sourceSHA一致。它不等于raw20600或父88968的F入口。下一已有wrapper跨度是11201 `[400,804)`，但固定239清单7994179B、SHA-256 `faf0f7a46989fc0380468dc7ba81d7305b6f31a4122bd0690c00e607eea07018` 中11201不在必要cohort，整个旧coverage groups也无该ID；11101/11301在239内。不能为适配400起点另造必需任务。

现成whole可读listing有4B count变换 `LE32 XOR 167e36b3` 和同reader转交wrapper；wrapper完整8368B `[154498500,15449a5b0)` 与raw20600完整5866B `[15043f0f0,1504407da)` 的旧记录只有完整hash/边界/RIP引用，有限现成源检查未取得对应完整body。已验2018B父源能给出String前 `itemType` 的flag/固定宽token条件，却仍缺**独立于冻结A返回/构造的wrapper→raw父入口cursor/字节语法绑定**，不能把400或别的wrapper起点当F，也不能按候选值扫描。当前没有成熟的新必需字段host合同；不新取已停止的Weapon构造路径源码、不执行wrapper/row或全walker。解除输入是新增允许的独立source/schema或真实raw入口cursor样本；潜在purehost路线保持开放，不能将此普通缺源泛封全族。准确判断为 `debug/d02-weapon-host-syntax-20261116/assessment.private.json`，6904B，SHA-256 `f008fa394121d96e46d2d4304921be61a2a5d1b21e6480c880f62015d0682d3e`。

原件根为 `debug/d02-shared-string-cursor-source-20261116/`：source capture result8179B，SHA-256 `668686be91e0b8ec2723698e057166ef8d8846217f3d26d5046290090a5a3e11`；manifest1629B，`0c8fe8935fbf3f4ac1e77ec9af7741f19dbd79cf88a5283185e6e2a899c6b7e2`；独立语义9482B，`39c69b8a1661026b62d7e9ddb0ee34733cac2c58b5d8331ffbc64118be87bc15`。完整listing为12014B，SHA-256 `fed84cc003b7537b05448e02b7f2c5eaac2c6ddacde6c27b79c7404bc0c91efd`。driver只在忽略目录，无新跟踪工具或运行资源变更。本子项完成仅限source与独立游标关系；首次固定输入/边界/strict冲突或安全拒绝停止相关准确动作及依赖，A/B/preview、父项未验及客户端验收范围保持。
