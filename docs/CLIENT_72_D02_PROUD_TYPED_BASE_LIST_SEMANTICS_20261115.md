# D02-R3：typed base-list 入口的限定语义

任务 `R3-PROUD-TYPED-BASE-LIST-SEMANTICS`。只消费已验 `69135.m572230 / 14c512370` 的346B原窗。**本体负责两份缓冲的清理、调用和条件转交，没有取得59500元素或读取三字段的准确值流。** 下一首缺缩到一个同owner直接调用 `14c512442→14c50f110`；不升级三业务alias、实际row或当前采用。

## 固定来源与验收范围

原窗 `[14c512370,14c5124ca)`，346B/91条，SHA `20759142b30c2710e63f270aa30db8e93e8eb393224aeff6608e1a39913499dc`。主线receipt位于 `local/migration72/d02-resume-20261115/launch-timeout/live-typed-integration1/typed-completion.private.json`，3414B，SHA `01d9590a395e38d7cf32a3d9d2909825fa7d2d65ce6bad93478cd7dc1025cd6c`。

本次重新核对receipt及其4附件、`fresh1/fresh2/main-third`的13类共39个原件和23个来源pin，全部匹配。独立32个control/RIP位移重算沿用主线验收，没有重新取窗或重跑原测试。完整审核与物理值流留在 `local/migration72/d02-resume-20261115/proud-typed-base-list-semantics/`。

flags81h仍为instance、3显式逻辑参数，formal return/隐藏参数/native ABI未知。下面仅用物理符号：`T=entry RCX`、`Q=entry RDX`、`B0=entry R8`、`B1=entry R9`；`S`仅为下述原调用返回的物理RAX，不给它或两缓冲命名current/diff/preview。

## 本体的正常条件路径

首mode byte `145842296` 为0才进入以下路径；本包没有读取它的实际值。所有后续到达关系均以相关原callee正常返回为条件。

| 原位置 | 可证物理关系 |
| --- | --- |
| `14c512392/395` | Q为0直接到`44b`，EAX置0，尚未修改两缓冲。 |
| `14c51239b..3bd` | B0非空后，DWORD[B0+1c]加1，保存旧DWORD[B0+18]并将其置0；旧count按signed32大于0时，以[B0+10]、0、旧count调用`159c68c70`。metadata准确为System.Array.m1025 `Clear(Array,Int32,Int32)`；未执行其body。 |
| `14c5123c2..3e4` | B1重复同一序列。B1为空的错误出口在B0修改之后，不能描述成原子清理。 |
| `14c5123e9..40f` | 第二mode byte `145805ee4` 为0时，从[T+18]再取其+28作为context；另一支由IFix wrapper给出context。随后物理RCX=Q、RDX=context调用`151841340`，返回RAX保存为S。 |
| `14c512412..42b` | 调`1545e9dc0`前，RCX=S、RDX=[T+50]、R8=B0、R9=B1；[rsp+20]保存从`145a57db0`取得的QWORD。该RIP单元未读，MethodInfo/generic身份未恢复；不能先命名此helper为diff、比较或应用。 |
| `14c512430..442` | helper返回后，signed32[B0+18]>0或signed32[B1+18]>0才调用`14c50f110`，setup为RCX=T、RDX=S。 |
| `14c512447/44b` | 上述调用正常返回后仅AL置1；否则EAX置0。AL=1不证明整个RAX等于整数1，也不恢复formal return。 |

B0/B1空指针及context为空走原`1404c2be0`调用；未取其合同，不补异常名或成功返回。首mode非零分支转交`14dd0eb50`，绕过正常缓冲路径；第二mode非零经`14e2a5d60`取context后回原调用。二者准确声明为IFix生成wrapper，本包不分析其body或实际选择。

## 类型关系与准确未知

`151841340`准确为18163.m138138 `NBAOBFKAHPC`，instance，显式参数28174，返回声明未知。`14c50f110`准确为69135.m572216，显式参数List<59500>。它们支持后续typed候选，但不能从这些声明直接把入口寄存器及S升级成实际业务对象或恢复隐藏ABI。

若T与声明this的绑定另获证明，`T+18`可对应39109.field192090/type27665，后者+28对应27665.field136031/type28174；`T+50`可对应69135.field341164/List<59500>。这些是声明/布局对照，不是已恢复native ABI。

本体没有遍历列表元素，没有可绑定到59500.field295065/295066/295067或26072.field127896/127897/127898的元素读取，也没有直接调用Float getter。`1545e9dc0`在固定普通method登记中无alias；它的实际副作用、RIP context身份、S的来源和内容都未知。因此当前不交普通数值投影或样本工具；重复模拟缓冲count不能解锁三字段。

## 唯一建议后继：同owner单列表来源

- 精确原边：`14c512442 e8c9ccffff → 14c50f110`；已存setup `[14c51243c,14c512447)`共11B，为`RCX=T、RDX=S、CALL`。
- 目标：owner69135，ordinary **m572216 `FLANEBGNPID`**，flags`881h`，instance，**一个List<59500>显式逻辑参数**，formal return/native ABI未知。S保持物理值身份。
- 问题只限这个方法是否存储/遍历59500，或接到295065/295066/295067及对应base字段；若没有，只给本路径的有限排除或一个准确member首缺。不先扩上游`151841340`、共享`1545e9dc0`或IFix树。
- 复用上一source的纯函数编排，使用新私有目录/新request，不改旧driver或已验模块。先核当前CodeReg cell`1422f9950`的P，再核slot`P+572216*8`必须指向`14c50f110`；旧P下slot提示`142d02e90`，不是当前输入。
- body0先核ordinary绑定、唯一exact/containing/selected-overlap同一pdata，以及完整extent`1..4096B`；strict decoder上限1024条。失败不取截断窗、不按RET/相邻方法补界、不扩预算。新包尚未执行，本轮没有新PE/pdata/window读取。

删除A/B动作及其未验产物后，这个已存原边、typed目标和来源问题仍独立存在；无需实际M、cache/Default、getter输入或同epoch材料。下游source只服务有限静态分类，不直接实现被拒返回/构造。

## 冻结边和交付状态

本体无到A共用`1405336c0`、冻结预览入口`[14cbdd560,14cbddd52)`或Setup`151976df0`的直接调用，也没有threshold/phase比较；间接/传递关系仍未知。若后继识别被冻结预览，停止该语义支；不得以`threshold==phase+1`推当前采用，两个List仍不命名current/preview。A分配返回/构造、热Default/同epoch及B loader继续冻结，遇准确依赖只记边，不分析或续接。

主线最新独立sample为cold-cache，cache0，没有提供actualM/row/getter；本语义包没有联调、补采或消费SDK循环作为新输入。actualM/Float继续null，sameEpoch、三字段bridge、actualRow、currentAdoption及resourceReady继续false。

实际完成：固定来源hash审核、物理控制/数据流与typed对照、一个精确后继首缺。未运行机械测试、目标代码、构建或部署；未修改WINDOW来源专题/索引/运行代码，无Git写入或新安全拒绝。专题及私有矩阵交主线审核，后继取源由Terra另包执行，语义再单独指派。


## 独立审阅与后继状态（2026-11-15）

本限定语义项已完成独立审阅，回执 `local/migration72/d02-resume-20261115/launch-timeout/typed-member-integration1/semantics-review.private.json` 为 3145B、SHA-256 `0772d4b012c065170647e8037a805c8f7cb55694967c1c5b736860f0fed19bb6`。审阅核对 23 个来源 pin、4 附件、39 份原件、5 个 callee 声明及 3 个字段布局，并独核已存物理路径和准确直接边；没有重读 PE 或重跑旧窗测试。本文此前的“尚未执行”描述语义交付时点，不覆盖随后单独获准的来源任务。

`R3-PROUD-TYPED-LIST-MEMBER-SOURCE` 现进行中：目标 `69135.m572216` 为 instance、一个 `List59500` 显式参数，formal return/native ABI 未恢复。作者报告 856B/211 条窗口、5 项门禁测试和双 fresh/纯 audit 已完成，13 件双 fresh 产物一致，独立第三仍在进行，不以作者结果提前验收。首次准备失败由 JSON 字符串键的普通错误引起，合并输出留存的限制保留；这不是安全审查拒绝，也不改变来源范围。

本项仅完成原 346B 方法的物理路径、类型对照和一个准确后继首缺；global mode 实际值、共享 helper 的语义、S 的身份及内容、native ABI/隐式返回、三字段消费桥、实际 row/current/epoch 和 ready 仍未知。A/B 拒绝及冻结 preview 的专项限制继续有效。
