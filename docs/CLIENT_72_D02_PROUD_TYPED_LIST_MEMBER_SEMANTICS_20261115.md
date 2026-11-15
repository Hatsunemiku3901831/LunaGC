# D02-R3：typed列表成员的遍历、标量容器调用与保存

任务 `R3-PROUD-TYPED-LIST-MEMBER-SEMANTICS`。只消费已验 `69135.m572216 FLANEBGNPID` 的856B本体、346B父caller及固定metadata/既有listing。**该成员遍历输入与原存列表，只对63831兼容元素读取一个标量并作条件容器调用，最后保存输入列表引用；本体没有形成26072三个目标字段的直接消费值流。**

本体物理流、中性表示桥与准确缺口已完成限定验收；三字段业务桥、实际值及父项ready仍未完成。formal/native ABI和三alias继续保持原未验边界。

## 来源与时间分层

本体 `[14c50f110,14c50f468)`，856B/211条，SHA `c4345212e44d395f086973395b3e858ab56ef3e2e767aeb3058f7f4d43ec3ddc`。主线receipt `local/migration72/d02-resume-20261115/launch-timeout/typed-member-integration1/member-completion.private.json` 为5060B，SHA `157090b9c5112e6770f90d1d892052375d9299f8a94fd9c03f2c54237e184fa3`。

上轮必要来源审核已核三套13件共39个原件及8项receipt/source附件，均匹配。27个source pin在第三采集时匹配；随后审核记录为**26个未变，另一个是获批文档追加**：[父语义专题](CLIENT_72_D02_PROUD_TYPED_BASE_LIST_SEMANTICS_20261115.md)由6529B/SHA`47282e6361168ecdbb07a155265d22dfb5f7c6b5c000f399702ab682e5c5437d`变为7935B/SHA`309670d1261c23d9060ea7c9e0b512d6d92606f1797029ea492ae5f413387106`。该追加不改变原执行输入；旧semantic JSON `93feeb2d…`、audit `6516bd59…`及346B父body的匹配记录直接复用，不恢复旧doc或重跑PE。

本次恢复只核7030B草稿基线、原7117B语义矩阵、原7028B审核、16436B父语义矩阵及上游既存listing的固定身份，没有重复39件审核、第三采集或旧测试。原 `a5006f9c…` / `b935fdb1…` JSON保持不动；补充记录写同一私有根下的新名 `semantics-v2.private.json`、`source-audit-resume-v2.private.json`。历史采集、上轮审核和本次修订分别记录，不声称本次又核当前27项全同。

88项引用的独立重拼沿用主线验收；本包没有重跑原测试。原准备KeyError失败1785B/SHA`8691ef32ca67eeed262e1bbd83bf273c7acbc14db163ba5865e5cee8dbe7159e`保留，属于整数/字符串键处理问题，非安全拒绝。

## 普通路径：两轮遍历与一个指针store

flags881h声明instance、一个List<59500>显式参数，formal return/隐藏参数/native ABI仍未知。以下保持物理符号：`T=entry RCX`，`N=entry RDX`，`O=QWORD[T+50h]`；不把这些列表命名current/preview。首mode byte `145842288`非零时尾跳IFix `14d96dd70`，绕过以下普通路径；实际mode未读取。

| 阶段 | 本体可证关系 |
| --- | --- |
| 输入轮 `14c50f13f..2db` | N为空跳过本轮；否则捕获+1c版本，每项先核版本、+18 count、+10 backing及其长度，取8B元素。null元素和不兼容类型跳过。 |
| 原存轮 `14c50f2db..445` | 从T+50取O；为空跳过本轮。独立捕获版本/index，以相同方式遍历O。 |
| 正常保存 `14c50f445/449/44d` | 重载原N并写`QWORD[T+50]=N`，随后正常RET。没有复制、过滤或清空输入元素；N为空也可保存null。RAX此时保留N只是物理事实，不恢复logical return。 |

列表版本不一致、backing为空、越界和初始化/容器为空分别走原helper。它们的异常/返回合同及unwind未解释；正常store只在相关原调用正常返回并到达该处时成立。N与O是否同一对象、重复键/重复引用及自然执行结果均未观察。

## 新增的中性标量表示桥

两轮的类型检查都引用`14589fa38`。已固定metadata缓存117201003B、SHA `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f`中，usage703701明确将它绑定到63831；只复用该独立声明，不分析旧分配返回。

匹配元素E的两种表示在本体汇合：

| 表示 | 输入读取 | 后续 |
| --- | --- | --- |
| `E+40`非空，field316249声明41105 | `14c50f1cd/368`读`41105.field203368/+70`的58209编码DWORD | 写局部，调58209.m483725 / `14aef9db0` |
| `E+40`为空 | 同E的`+8 &~ff`，以offset`2c`调用既有`148ba9780` | 同样写局部，再调上述decoder |

这得到 **nativeData+2c与managed41105.field203368/+70的同用途标量桥**，不证明表示间物理复制、实际值或ProudSkillId业务alias。两轮使用相同读取关系，不涉及54724 Float getter。

## 后续只到custom容器，不能命名增减或通知

解码U32后，原`14587c2c0` backend byte非零等条件才进入容器调用。RIP `14589a0a0`的独立metadata usage702713绑定owner80063；原GLOBAL+21f68对应其field400192、类型13732<U32,63831>，不能套另一field400191的标准Dictionary语义。

| 遍历轮 | 原site／target | 可见实参setup |
| --- | --- | --- |
| N | `14c50f23d→154db6810` | RCX=容器，EDX=解码U32，R8来自`145a0ddb0` |
| O | `14c50f3da→154db7400` | RCX=同容器槽，EDX=解码U32，R8来自`145a0ddc0` |

两个context身份、共享helper算法、缓存/引用计数/所有权及间接副作用未闭。不能命名Add/Remove、引用增减、创建/释放、事件通知，也不能因同一shared地址沿用Scene另一MethodInfo的结论。本包不追这些callee：本体没有给出它们消费三字段或属于D02必要恢复首缺的准确依据。

59500.field295065/295066/295067及26072.field127896/127897/127898均没有在本体形成字段消费值流。这里的+18是列表count，+70是41105自身标量，不能按偏移形状混成三字段。实际三alias、数组/字符串内容和配置同源仍未验。

## 唯一上游复核：发现已冻结构造边，停止该支

346B父caller的准确边是 **`14c51240a e831ef3205→151841340`**，物理返回RAX保存为S；S随后进入本成员。其登记为18163.m138138 `NBAOBFKAHPC`、flags86h、instance、一28174显式参数，formal/native ABI未知。此前“不先扩上游”只是排期选择，不能作为技术阻塞理由。

上轮末已找到现成 `local/migration72/analysis/avatar-team-native-owner18163.txt`，898784B/SHA `425e8e64afb31e59b7ce85e8444f242d724068be9c5f3682cd5ffdcdb000309a`，匹配旧已验promote/query审计pin；本次只读核其固定身份并补入v2矩阵。该listing给出此entry的旧范围提示，并在`15184135a`标明List<59500>类型来源，随后 **`151841361 call 1405336c0`**。

当前专项冻结共用`1405336c0`的返回/构造，因此停在该CALL，不解释其返回、构造或后继，不跟后续callee，也不以IFix分支作替代。listing没有原hex，不能伪造E8或把旧232B范围提示当作新pdata。该上游需要回答的普通S生产问题遇到准确冻结依赖，本包不提出新的151841340取窗包。

这只冻结该正常生产支；已有856B遍历/store及中性标量桥移除A/B动作和未验产物后仍独立成立。三字段缺少的是准确field→读取方法/业务消费来源，实际M/同epoch/值另缺，不能把整个Proud或所有独立工作泛标A/B阻塞。

## 普通规格与缺输入的逐项判断

| 候选出口 | 现有允许输入能证明什么 | 本包决定／准确缺口 |
| --- | --- | --- |
| 双列表遍历与保存 | 原本体的版本/边界检查、63831筛选和同N指针store | 直接交限定静态合同；新driver或重复算术测试不会解锁字段消费，不另排。 |
| `native+2c ↔ 203368/+70` | 两表示在同一58209标量decoder前汇合 | 保留中性表示桥；缺实际encoded/合法M/epoch和业务alias，不能投影实际值或生成Java行。 |
| 26072三个字段 | metadata布局及已验Float/String条件合同可复用 | 仍缺各字段到准确客户端业务消费的来源；没有真实数组/String输入，不造样本或重新包装旧prefix。 |
| 两个custom容器helper | 本体可见解码U32和各自context来源 | context身份、算法/所有权未知，且未建立三字段或D02必要恢复的准确依赖；不以共享cache/事件树填工作。 |
| 上游S生产 | 既存listing定位到`151841361→1405336c0` | 正常生产问题依赖被冻结的分配返回/构造；移除该动作及产物后不能完成同一S证明，停止该支，不补回执或取新窗。 |
| 实际M/getter/row | 既有单次现场只取得cold-cache结果 | 缺允许的自然非空输入；SDK时点backend00/root非零/cache0不证明本方法的其他mode、列表或codec epoch，不循环补输入。 |

本包没有形成可解锁新业务结论的投影、driver或测试规格，也没有新的callee取源提案。已验Float419、String14、Scene来源差距及121项相关成果直接复用，不重跑或重新派工。这是本输入集的准确缺口划分，不是全项目无独立工作的结论。

## 交付边界

本体没有直连被冻Preview `25486.m198316 / [14cbdd560,14cbddd52)`或Setup`151976df0`，也没有phase比较；间接关系仍未知。识别冻结preview即停，不用`threshold==phase+1`推当前采用。A第二allocator、共用分配返回/构造、热Default/同epoch及B loader冻结保持。

实际M/getter为null，row/current/epoch/resourceReady不晋级；既有cold-cache样本不补成输入。本次只修订本页并新增 `local/migration72/d02-resume-20261115/proud-typed-list-member-semantics/` 下的v2记录，原审核/矩阵保留。没有新PE/pdata/window/global/进程读取、测试、Git或构建部署，也没有新安全拒绝。主线已按本页限定范围审阅，确认本体物理流、中性表示桥和准确缺口完成；来源及本语义验收不代替三字段业务桥或客户端验收。

主线审阅沿用本次恢复补充记录：`semantics-v2.private.json` 14180B，SHA-256 `cbcd22aeec4c65cd8a23145636d3f37f00a9f2f5153d10bc094e2fd251506326`；`source-audit-resume-v2.private.json` 4218B，SHA-256 `c16e3716dfc4e3087ee76f848e2881e6dc1c2415db0044f8df584362eb4cf924`。原 `a500`、`b935` 记录未覆盖；此审阅未重复旧来源采集或测试。
