# F-03d3s2 / MPLEVEL-ACTUAL-PAYLOAD（2026-11-11B）

## 中文实际解析计划

| 项目 | 本卡约定 |
| --- | --- |
| 目标 | 从正式目标byte[]恢复完整分组、含MPLevel的完整key和原序子名称；确定文件语法起点与reader42005自然起点证据各自覆盖范围，交成熟host reference规格或准确首缺。 |
| 已有 | field386052→第二U64 bin→精确entry44935541→local pathID14783/class1208→782517B byte[]已通过主线第三；payload SHA `25bf9c7e24ca9d708b39deafe361c0a339daf9cbb5aadc6b61a2324e57cd891e`。复用已验131B分流、字典字段/count、QWORD文本变换、游标、原序数组、插入和空分支。 |
| 准确缺口 | 自然reader对象的backend/游标初始化、实际全部分组及重复键；World实际factory返回与slot114仍属独立后继，不能由文件解析代供。 |
| 方法 | 正式main-third输入先核完整大小/SHA；仅以byte[]起点0应用已验语法，不扫偏移、猜alias或执行未知helper。保留完整有序条目和解出bytes，核每一步游标/长度及EOF；实际字符串决定编码覆盖和重复是否影响本样本。另查已有准确reader来源，未知仍未知。 |
| 修改/产物 | 仅本页和私有scene/；成熟机械工具交Terra。旧所有工具/专题、Project/Harness/Git/build/live只读。产物为实际解析报告、MPLevel完整分组与host reference规格/准确首缺，不提交原bytes。 |
| 验收 | 实际完整782517B、非零分组和长度、原序名称、64位输入链、文件消耗/错误边界、双fresh/主线第三；不得将host reference成功升级成native reader、factory返回、Scene或D02通过。 |
| 停止 | A/B拒绝及依赖不触；不重复原窗或静态断言，不执行未知native/helper，不给空字典或替代alias；新安全拒绝保留原文并停止。 |

## 正式输入与实际解析

主线已验正式输入为 `local/migration72/d02-resume-20261111b/scene-gadget-bin-target/main-third/payload.bin`，782517 B，SHA-256 `25bf9c7e24ca9d708b39deafe361c0a339daf9cbb5aadc6b61a2324e57cd891e`。其来源是已验 field386052 的 `PathHash.bin` → 精确 entry44935541 → fileID0/pathID14783/type1208：对象 782524 B 的 LE32 数组长度为 782517，随后是该完整 byte[]，最后 3 B 实际 `000000` 已另存。目标提取主线第三的 34 件原件与两次 fresh 相同，4 tests 和纯 audit 已过；本页不重跑提取。

本次只以纯 host 运算复用[前缀](CLIENT_72_D02_SCENE_MPLEVEL_PREFIX_20261111.md)、[完整 parser 原窗](CLIENT_72_D02_SCENE_MPLEVEL_PARSER_WINDOW_20261111.md)和[字典合同](CLIENT_72_D02_SCENE_MPLEVEL_DICTIONARY_20261111.md)。从 byte[] offset 0 开始，没有搜索偏移或试选参数：

| 实际量 | 结果 |
| --- | --- |
| 文件前缀 | raw `6a`，按已验公式得到 `a0`，bit `0x20` 置位 |
| 外层分组 | 707 |
| 全部子名称 | 15835 |
| 消费范围 | `[0,782517)`；准确 EOF，余字节 0 |
| 文字覆盖 | 全部解出 bytes 都在 ASCII 范围 |
| 相同 decoded bytes 的重复 key | 0 |
| 空 key / 空 child string | 0 / 0 |
| childCount=0 的实际条目 | 5，原序 ordinal 为 450、461、502、511、575 |
| 精确 child bytes 为 `MPLevel` 的分组 | 唯一，ordinal 544 |

host 消费公式是 `1 + 4 + Σ(2 + K + 4 + Σ(2 + L))`；此次恰等于实际 byte[] 大小。完整 EOF 是本样本的验证结果，原 native consumer 本身没有追加 EOF 检查。重复统计以 decoded bytes 为准，未向原字典 comparer 或 insertion callee 赋予额外语义。

## 实际 MPLevel 分组

该条原文件范围是 `[731264,731558)`，294 B，raw entry SHA-256 `08d0ca8d1eb5afaf7d998dfb64a2ab3cfbd6d380f6e6c60345eaa53c76d937a1`。key 长度字段位于 731264，47 B key 从 731266 开始，实际完整值是 `Data/_BinOutput/Gadget/ConfigGadget_Scene_World`；decoded key SHA-256 为 `8ac986bbf8379bc4f8c85eb0060e3705e9e1e3293df8c404617067235d777cf2`。child count 字段位于 731313，值为 21。

21 个名称按实际数组顺序为：

```text
MPLevel
Team
Enviro01
Enviro02
Enviro03
Grass01
Water01
BrokenIce01
Tree01
Bush01
Bush02
Bush_NoFire01
Bush_Yellow
Bush_Ashes01
CustomTile
Foxtail_Grass01
Foxtail_Grass02
Windmill
Oil
Storm_Lightning
Storm_Lightning_New
```

首名称的长度字段位于 731317，7 B 字节从 731319 开始；最后名称的 19 B 字节从 731539 开始，恰结束于 731558。保留完整 key 和原序名称，不将 `World` 截短为另一个标识。

## 交给机械实现的冻结范围

冻结规格位于 `local/migration72/d02-resume-20261111b/scene/mplevel-actual-host-reference-request.private.md`，9585 B，SHA-256 `a529fee6c08f54f783e4ed83e7fec84f4249024ba95f35b99e3cb7bc6f8f39d4`。它固定正式输入及原语法来源，定义纯 Python 有序条目 reference：入口只校验一次；保留 decoded bytes、SHA、ASCII view 和每字段游标；不转成可能吞掉重复 key 的字典；清位和正 count=0 在准确游标处明确停止，不伪造空字典。实际 childCount=0 只表示文件的逻辑零子名称，不声称 managed 共享数组身份。

要求以实际解析结果独立逆编码完整输入并逐 byte 比较，测试两套 QWORD 常数的溢出及 1..7 B 短尾、游标/越界、空分支停止、有序重复 key 和非 ASCII bytes 保留；随后双 fresh、独立纯 audit 和主线第三。纯探索报告 `scene/mplevel-actual-exploration.private.json` 为 5056454 B，SHA-256 `b09b44d69a10c6745a90684312bfb6dac31b4db1f72f024cc06932789ed144f9`，提供完整条目及此次实际 oracle，不能替代机械实现从输入计算。

当前实际执行范围是一次固定输入的完整 host 探索解析；上述新 reference 的测试、双 fresh 和独立 audit 待机械实现完成。旧原窗及静态断言未重复运行，没有执行 native、emulator、decoder 或未知 helper。

## reader 与 World 的准确剩余项

已验 caller 链把 `PathHash.bin` 交 `60257.m500134/14d6f9210(bin,0,0)`，将返回的 reader 交 `70954.m587454/14b6accb0`，再注册同一 70954 对象。此次真实文件从 offset 0 完整匹配语法，已经给出文件 framing；但没有观察 reader42005 自然对象的 backend、初始 cursor 和 refill 状态。不能把两个 0 参数命名为 offset/count，也不能把文件 offset 0 换成 runtime 字段初始化结论。

全部实际文字都是 ASCII，使本次 bytes 的 ASCII/UTF8 显示一致；自然字符串 selector 及另一转换路径的完整字符语义仍未观察。此次没有实际重复 key，不需要猜原插入调用处理重复键的行为。

真实 `ConfigGadget_Scene_World` 分组和 child names 不证明 World factory 的返回对象、slot114 的 Pool/Scene 生命周期或各 child 的配置正文。这些是后继技术缺口，尚非外部阻塞。后继消费者和资源定位须另给准确来源，不能凭名称猜后缀、hash 或候选 body。Scene 与 D02 均未因此升级通过。

本页已冻结，供主线提交。后续机械验收状态由主线更新；本轮未修改旧工具、旧专题或共享任务文档。
