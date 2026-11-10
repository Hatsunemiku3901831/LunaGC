# E-01/R3-PROUD-FACTORY-WINDOW（2026-11-11）

## 有界中文实施计划

本包只机械提取 MODE-FACTORY 已闭合的单一普通 factory entry，不分析返回语义。复用已验 README-first/分支检索与现有 PE/metadata/Capstone 取窗模式，无新外部检索或依赖。B loader 已冻结入 Daybreak 队列，本包不读改、构建或重试其材料。

| 项目 | 准确合同 |
| --- | --- |
| 静态身份 | class63831/count11/ordinal9/index773626，原4B `4c130860` → method529228/slot9；AOT slot142caef30原8B `40bbf04901000000` → entry149f0bb40。先核原type70B、word4B、method26B、metadata/signature及普通AOT。 |
| 原caller | 完整320B base ctor已固定；14d5e2532取actual klass、2535 RCX=this、2538 EDX0、253a原 `call [rax+118]`，2540同RAX写+20。没有E8直接调用，未观察自然actual slot。 |
| 新输入 | 每次新进程通过当前resolver解析GAME_PATH并重核五输入完整SHA；来源固定为旧私有第三报告/工具与原件，不pin会维护的public专题。 |
| 唯一新窗 | 仅149f0bb40一个完整原窗口；先核唯一exact与containing同条PE pdata，先保存12B及诊断，再允许body≤1024B、连续完整decode≤256条。xdata/子callee/live/原CPU/新REA/网络/目标进程/构建均0。 |
| 产物与门禁 | 两fresh各八原件逐byte一致；固定源漂移、非唯一extent、越界、错误AOT及decode不完整拒绝；只列原direct call、RIP reference和RET，不追helper。 |
| 写域与退出 | 仅本页、同名collector/test与独立私有目录。其余旧工具/专题/Project/Harness/Proto/patch/resources/config只读；不Git。遇安全门禁或Daybreak问题记录即停，不反复尝试或换模型。成熟即交Sol max分析并释放。 |

源合同：[MODE-FACTORY](CLIENT_72_D02_PROUD_MODE_FACTORY_20261111.md)、[PARSER-CONTRACT](CLIENT_72_D02_PROUD_PARSER_CONTRACT_20261111.md)及私有next-input-spec SHA `c98896f6a10dfd6dd8f5243293daf49e22c13d0b46ef69b6475cf8649a08edfa`。主线已验MODE第三41737B/SHA `b5cbd9f03020738f4c606209921f5ef2c1b9473a6284919f5a291b238fead987`；其public追加验收文字不作为源漂移忽略。

## 当前状态

本包单窗机械出口完成：两fresh各八原件逐byte相同，7项离线检查通过，两次最终离线审计输出相同。**只取原body，没有执行factory，也没有分析它的返回语义。** natural actual slot/MethodInfo/mode未观察；返回typed41105、分配/构造、mode成功、Parser/资源ready/完整Avatar/D-02均未验。没有运行代码或部署变更，无需重启。

## 来源与实际提取

私有根为 `local/migration72/d02-resume-20261110b/c-proud-factory-window/`。`frozen-inputs.private.json`固定20项实际来源，4673B/SHA `b9d75bcc76b6f498e6a3e9533648193921ecd6e0c74c04acbc920a31df5fbfb9`；其中两个public专题复制到私有context，门禁只核快照路径。MODE的public追加第三验收文字已按root指示读取；原工具、第三私有报告、next-input-spec与slot六文件均重新核其完整SHA，未改变旧pin或旧原件。

每个新collector进程通过当前已pin的`tools/game_path.ps1`重新解析GAME_PATH，并完整重算五输入：EXE `8b014f64…dbd6b5`、global metadata `45dc7a44…f6ef2a`、startup metadata `b3148108…4d5105`、SDK `adecdf0b…a6c25`、stock `87e0a005…0b442`；完整SHA/大小在原报告逐项保留。当前磁盘重新读取原type70B、slot9 word4B及method26B，逐byte等冻结原件；核metadata唯一声明及零参数signature省略、count11/ordinal9/index773626/普通method529228，并核320B原caller全体及18B setup/call/store `488b064889f131d2ff901801000048894620`。

原caller仍为`14d5e253a ff9018010000`间接调用；RCX=this、EDX0、原RAX写+20仅按原指令表达。没有制造E8，也不因静态definition闭合声称自然actual target已命中。普通AOT cell1422f9950=`d0548a4201000000`，table1428a54d0；slot142caef30=`40bbf04901000000`精确等149f0bb40。以上全部先于新body读取。

唯一PE exception exact与containing为同一条；先写boundary、12B pdata与pre-body诊断，通过≤1024B后才读取完整body，连续decode并逐byte拼回，≤256条。未用邻方法定end，未取xdata或任何callee body。

| 原件（factory1/2各一份） | 实际结果 | 完整SHA-256 |
| --- | --- | --- |
| `code.bin` | 149f0bb40..149f0bbb2 exclusive，114B/27条 | `28ff695b26c2b8ec33a1505639900e08a589e13667f37f29858701f12fd534e0` |
| `pdata.bin` | record RVA1a858c2c，12B `40bbf009b2bbf00908000504`；unwind RVA4050008仅记录未取 | `980405b3c62d1b3ece62e018a16ea08519babfa24a45d05b61f1201cdd80760b` |
| `extraction.private.json` | 各10173B，原身份、五输入/来源、pdata及完整机械清单 | `00c23216d432a8cd66d35c1a6343d6ef7853fe7743e410ed211a9725eb91d26f` |
| `final1.private.json`、`final2.private.json` | 各12296B，逐byte一致 | `6ae2d16635502569e6b545a920c332f86b3d22efc05b310f639e5bedcb4d6ce9` |

只机械记录本body的2个原direct CALL、6个RIP reference、0个RET；完整源PC/原hex/目标在私有报告。不对这些地址取窗，不将它们命名为分配/构造或把尾目标当已执行的返回证明。factory语义、返回typed41105及子helper留交Sol max，不能据窗口大小或静态命中判断模式成功。

## 实际检查与主线复验

```powershell
python -B tools/client72_d02_proud_factory_window.py --output <新factory目录名>
# 第二个fresh使用另一个新目录；每次都会重新解析GAME_PATH并完整核五输入。
python -B tools/client72_d02_proud_factory_window.py --audit factory1 factory2 --output <新审计文件名>.json
python -B -m unittest tools.test_client72_d02_proud_factory_window -v
```

实际`factory1/2`各独立host Python进程退出0；`factory1/2.execution.private.json`保留argv、时间、退出码及stdout/stderr哈希。最终audit1/2各独立进程退出0，报告完全相同。`offline.execution.private.json`记录两次审计和实际7项测试的stdout/stderr/退出码；两次取窗与本卡检查没有失败或安全门禁。

7项门禁覆盖完整静态definition；20源及冻结manifest逐项漂移/缺失拒绝；exact/containing缺失/重叠/重复拒绝；byte与instruction上界；错误AOT目标/cell/slot报告；decode缺失/乱序/错误bytes/间隙；真实间接caller不被E8代换，且actual slot、typed返回、parser或资源ready的无证据晋级被拒绝。人工两字节NOP/RET样本只测试decode审核器，不作为factory执行证明。最终audit不重读当前PE，仅消费两fresh八原件与冻结来源。

root第三审计应使用新的JSON文件名，核输出SHA仍为`6ae2d166…d6ce9`并复核八原件/20源，不需要第三次取新窗。工具16823B/SHA `47bdbdf9b565227e576c24fab50634726ef671e1599af8d0bd5ddd5cb9f4d89c`；测试5640B/SHA `64db2a47023aff71aad3fe506eb32c0a47f482de315fed6c5ddcdc8c4a1185b5`；其他完整SHA见私有delivery清单。本槽不stage或提交Git，Project/Harness/HANDOFF由root统一维护。下一可执行项是把这一个完整原窗交Sol max分析，未知语义交回后另定必要后继；本包不自行追helper。

本包原native CPU/live/客户端/调试器/目标DLL/目标进程操作/JVM/Mongo/网络/新REA/共享构建/部署/Git均0。仅使用host Python、当前resolver及已冻结Capstone做静态机械读取，没有改变生产、Proto、patch、resources、config、数据或B loader。factory/Parser/资源ready/完整Avatar/D-02全部继续未验，当前无待用户操作。

主线第三独立audit通过，12296B/SHA `6ae2d16635502569e6b545a920c332f86b3d22efc05b310f639e5bedcb4d6ce9` 与两fresh相同，7项门禁通过。只关闭本机械取窗子项；原27条语义、factory实际产物/返回、mode与完整parser仍须Sol max分析/独立验证，不将tail jump代自然成功。
