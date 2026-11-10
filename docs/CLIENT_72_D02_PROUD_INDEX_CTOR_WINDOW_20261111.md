# E-01/R3-PROUD-INDEX-CTOR-WINDOW（2026-11-11）

## 有界中文实施计划

本包承接[ROW-READER](CLIENT_72_D02_PROUD_ROW_READER_20261111.md)及其5455B私有next-input-spec，复用已成熟单窗机械模式，不重新检索外部仓库、不分析业务、不追loader或其他callee。上一factory包已交root，只读；B loader继续Daybreak冻结，不读改重试。

| 项目 | 合同 |
| --- | --- |
| 输入身份 | field400190/raw4021f58，owner80063；原声明attributes31、18192实例5071 `<U32,75077,47647,25030>`。唯一`.cctor` method663526/entry14d7adbc0、零声明参数，只是下一初始化候选，不预称真实store。 |
| 前置 | 每fresh新resolver解析GAME_PATH、五文件完整SHA；固定metadata/field与typed声明、唯一cctor/signature、普通AOT真实slot。没有原E8 caller，不制造caller。 |
| 唯一窗口 | 仅14d7adbc0一个完整原body；唯一exact/containing pdata同条，先存12B/pre-body后核≤2048B，再取完整body且连续decode≤512条。xdata、其他callee、目标CPU/live/新REA/网络/构建/目标进程/Git均0。 |
| 输出 | 两fresh八原件逐byte相同，独立离线audit/必要门禁。只列原direct call、RIP reference及写内存原操作数，不因memory write列表认定field400190生产已闭。 |
| 排除 | 不执行157ffc4c0 closed-AOT tuple的独立数据计划，不取得同key索引值/row span，不执行cctor/parser、构造成功stub或推进资源ready。 |
| 写域/停止 | 仅本文、同名collector/test及独立私有根；其他旧源码/专题/共享索引/Proto/patch/resources/config只读。预算/身份歧义或超界留诊断交回；安全/Daybreak门禁立即记录停止，不换方法重试；成熟交Sol max未知语义并释放。 |

本包固定ROW-READER最终31839B/SHA `90c3920795c14fcb1ef2058eddd8e3692ca189e59703cd08b372fcd675f2aeee`；public可追加接受文字，复制私有快照，只pin私有版本及原工具，不放宽旧pin。无生产修改或部署需求。

## 当前状态

本包单窗机械出口完成：两fresh各八原件逐byte一致，两次独立离线audit报告相同，7项测试通过。只取得原cctor完整bytes及原指令清单，没有执行或分析业务；index producer/store、loader/path、同key两个U32、有效row span、Parser/资源ready/完整Avatar/D-02均未因本包晋级。

## 固定身份与实际原件

私有根为 `local/migration72/d02-resume-20261110b/c-proud-index-ctor-window/`。`frozen-inputs.private.json`固定10项实际来源，2297B/SHA `5d1117a090088715ce4b8299914ab9932e87d3f2f3a54ae5cb0c98e30964ddc8`：既有PE helper、resolver、metadata/signature缓存、Capstone来源、ROW-READER原工具/主线第三/next-input-spec及一份public专题的私有快照。共享public后续接受段不影响旧快照门禁，没有放宽或修改旧pin。

每次collector使用新host Python进程，通过当前已pin resolver重新解析GAME_PATH，完整重算五输入大小/SHA；完整SHA均在报告，仍与固定EXE `8b014f64…dbd6b5`、global metadata `45dc7a44…f6ef2a`、startup metadata `b3148108…4d5105`、SDK `adecdf0b…a6c25`、stock `87e0a005…0b442`一致。先核已固定metadata与主线第三的同一field/type、唯一cctor声明及零参数signature省略；不以相邻方法估end，也不伪造E8 caller。

原field400190 `BKOILGHDKDP` 属80063，rawOffset4021f58、type attributes31，声明18192/instance5071的四参tuple为`uint32/75077/47647/25030`。唯一`.cctor`为663526、flags1891、零声明参数、14d7adbc0；所有同entry声明也只此一条。它仍只被标注为静态字段owner的初始化候选，未据类型或名称认定producer。

每fresh在取得body之前核普通AOT cell1422f9950=`d0548a4201000000`（P1428a54d0），真实slot=P+663526*8=142db5400，8B `c0db7a4d01000000`精确等14d7adbc0，slot SHA `3c01034782a722ef838d88f6f64d5722c47dd3c7aa8590fde55ae63e7633ef9f`。这是本单entry许可内的两QWORD，不是157ffc4c0闭合tuple数据计划；该独立计划未执行。

| 原件（ctor1/ctor2各一份） | 完整原范围/大小 | 完整SHA-256 |
| --- | --- | --- |
| `code.bin` | 14d7adbc0..14d7adc9d exclusive，221B/45条 | `80ae3deeedfcc0287f634b115e03e22bb2a7d489621615890697a254394f06d9` |
| `pdata.bin` | 唯一record RVA1aa20404，12B `c0db7a0d9ddc7a0d98974304` | `5f5a3654c4ecaef7e39f00e6a59ec2afb2c6991fdc8adc33b90ecdfd6060787d` |
| `code.asm.private.json` | 各5636B，45条连续hex完整拼回 | `a7e243ab40d1539bdd134d1d0e631ba518d408c734647b7cccaa007806dc2b69` |
| `extraction.private.json` | 各12308B | `c37d855fe8c7b0833ef014a82830eebb769b153a79aeaeac89f0b0288b3892e3` |
| `final1.private.json`、`final2.private.json` | 各14772B，逐byte一致 | `ec156f873d7db1d3f6940a0487bea8e0fdd92fe746ad5f33b26bbb084b5da653` |

PE exact与containing为同一条记录，先保存boundary/pdata/pre-body，核2048B界限后才取完整body，再连续decode核512条界限。unwind RVA4439798只记录引用，xdata未取。八件固定为boundary/cell/code.asm/code/extraction/pdata/pre-body/slot；两fresh全部相同，不仅code相同。

仅机械列7个direct CALL、14个RIP reference与12条Capstone标注的写内存指令，逐条保留原PC/hex/mnemonic/operands及写operand序号。`rawMemoryWrites`包括原写内存操作数，不自动把它们全叫global store，更不据列表数量判断field400190是否已闭。没有取其中任何callee代码、追loader、读取自然field值或执行cctor；原callee职责与index初始化语义交Sol max。

## 实际测试、复现与交回

```powershell
python -B tools/client72_d02_proud_index_ctor_window.py --output <新的ctor目录名>
# 第二次fresh使用另一新目录；每次重新解析GAME_PATH并完整核五输入。
python -B tools/client72_d02_proud_index_ctor_window.py --audit ctor1 ctor2 --output <新的审计文件名>.json
python -B -m unittest tools.test_client72_d02_proud_index_ctor_window -v
```

实际两次collector退出0，时间/argv/stdout/stderr/SHA/退出码分别保留于`ctor1/2.execution.private.json`；两次独立audit与7项测试退出0，留`offline.execution.private.json`。最终audit只消费固定来源和两个fresh八原件，不重读当前PE。新包没有取窗失败、歧义/超界或平台安全/Daybreak门禁。

7项测试覆盖原field/tuple与唯一cctor；10来源及manifest逐项漂移或缺失；错误field、错误instance、重复cctor、错误signature；缺失/重叠/重复pdata及2048B/512条上界；错误AOT目标/cell/slot报告；原decode缺失/乱序/错误bytes/间隙；伪造caller、producer/store闭合、独立tuple数据计划执行、parser或资源ready晋级被拒绝。人工NOP/RET两字节仅用于审核器负例，不是原cctor执行证明。

工具16370B/SHA `9735a22b8c9c9f3855bb55fda5d33ecce28fcbcc109025d2c4829ed07396ae79`；测试6424B/SHA `89d75c44b6c7adeb3282f806ce8eece20f51d4ab2dd856a8a6dd316e9db7149b`。完整source/两fresh八件/报告和执行原件SHA逐项列于私有delivery。主线第三使用新的JSON名称，预期最终14772B/SHA保持一致，无需另取第三窗。

下一可执行项为root第三机械复验后交Sol max解读这唯一完整body；若需要loader或别的代码/数据，先交有来源的准确机械规格，不能本包自行追取。所有旧源码与上一factory/public专题未改，B loader未读改重试；无Git、共享索引、Proto/patch/resources/config、JVM/Mongo、目标进程、native CPU/live、新REA、网络、构建/部署操作。纯离线研究无需重启，新增客户端验收0，无待用户操作；Project/Harness/HANDOFF及提交留root统一维护。
