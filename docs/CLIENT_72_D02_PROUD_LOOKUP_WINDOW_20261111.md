# E-01/R3-PROUD-LOOKUP-WINDOW（2026-11-11）

## 执行前有界计划

依据主线批准的 `next-lookup-window-request.zh.md`（SHA `cccd0d64ebf7b8cfc748193bff61237d223ae0f1eb27a4356a4e1c122d0d02b4`），只取闭合AOT绑定的 `157ffc4c0` 一个完整窗。先固定两份和主线第三AOT报告、其固定source、metadata/signature、原606B caller和Capstone来源；每个fresh重解GAME_PATH、核五输入全SHA、原caller参数和E8、pointerBase及84537原slot，再保存唯一exact/containing pdata和pre-body，确认真实完整extent≤4096B后取body，连续decode≤1024条。只机械列直接call、RIP、分支和全部显式内存操作数；不追callee或给方法命名、不作加载/实际row语义。两fresh八原件逐byte比较，离线重算与必要测试通过后交root/语义代理。跨pointerIndex同code仍未知；0其他新code/CPU/live/REA/网络/目标进程/构建/DB/Git。身份、pdata、预算或decode异常保留诊断停止；安全或Daybreak门禁不重试。仅写本文、同名工具/测试和独立私有根。

## 限定结果与来源

已取得唯一PE pdata对应的完整窗口 `[157ffc4c0,157ffc5ac)`，236B/71条，SHA `479e010ba8e0b7fbea184c024408f2a1ce266aa519b92445bf03279672f2fb4e`。唯一exact与containing是同条record RVA `1b0dec10`，12B `c0c4ff17acc5ff17ec6c1905`；unwind RVA `5196cec`仅记录，不读取xdata。边界和pre-body均先于取code保存，236B/71条分别低于4096B/1024条。此包没有执行目标代码；方法、加载和索引实际行为交Sol max，不称FindEntry/TryGetValue或loader已执行。

两fresh分别重新解析GAME_PATH，核五输入完整大小/SHA：EXE `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`；global metadata `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`；startup metadata `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`；SDK `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25`；Astrolabe `87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442`。五完整文件读取用于身份门禁，不将其算作新增body窗口。

原606B caller SHA `c090061f8eaef3bce27411310d52b2811eab410fe73f772dbe76424d22fd570d`，完整setup与E8 `4c8d45d089fa4531c9e87aeb840a`；另核 `14d7ad91f`、`14d7ad926` 的原global/field取值及 `14d7ad946` 的原AL检查。其直接E8解码目标是本entry。三份已验AOT报告均12430B/SHA `ab9f56b6d782430261d195a2b21cf245340e86b01663483cb9e676c483b06b5f`；复核field400190、18192/5071 `<U32,75077,47647,25030>`、method138527 `NGNLGNHAKKO`、VAR0按值/VAR1 byRef和唯一同pointerIndex注册行。pointerBase原cell `1422f9910`=`b00c474201000000`，slot `142515e78`=`c0c4ff5701000000`。未取其他slot；跨不同pointerIndex同code仍未知。所有固定来源含metadata/signatures、AOT工具、resolver、profile与Capstone均以完整SHA列于报告。

## 机械清单

全部原71条连续指令及原hex保存在两fresh asm和两次离线审计中，完整拼回236B。分类由Capstone机械导出，不对目标职责或实际执行作推断：9个直接call、8个跳转、4个RIP memory operand、20个显式memory operand，以及20个push/pop/call/ret隐式栈相关指令。显式operand清单保留Capstone access标志；其中两条LEA是地址计算，不将其自动当内存读取。隐式栈指令单独列出，避免把显式operand清单误当所有栈访问。

| 原直接call site | 原target |
| --- | --- |
| `157ffc4d3` | `157ffc650` |
| `157ffc4ec` | `1405336c0` |
| `157ffc524` | `140517680` |
| `157ffc52d` | `140538230` |
| `157ffc535` | `159b6e720` |
| `157ffc55d` | `1553c0640` |
| `157ffc59c` | `1404c2be0` |
| `157ffc5a1` | `1404c2650` |
| `157ffc5a6` | `1404c2be0` |

RIP引用为`157ffc4e5→145935a60`、`157ffc4fd→14594fd78`、`157ffc513→145bc8ee0`、`157ffc53d→14594fd78`。八个跳转的site/target分别为`4df→558`、`4f4→5a6`、`511→54b`、`54e→554`、`564→584`、`56d→59c`、`572→5a1`、`582→58b`，此表短写均以前缀`157ffc`补全；完整mnemonic、hex、operand和target保留于私有audit。未读取所列callee或RIP目标对象的内容，不由静态分支推断自然结果。

## 原件和验证

两个最终fresh各八件逐byte一致，位于 `local/migration72/d02-resume-20261110b/c-proud-lookup-window/final3/`及`final4/`：

| 原件 | 字节 | 完整SHA-256 |
| --- | ---: | --- |
| boundary.private.json | 7580 | `123a04fd03ec771898d3558b0a1c34c66a9373c6c4a8b2bde4c17658e1c1426e` |
| cell.bin | 8 | `0dd98708676985c256b3f7d5482a908695e34a2033b4ed5e1b134b64ea64ce88` |
| code.asm.private.json | 8203 | `588850032f28fe8432e8dc88e687e522686edbf7484dce34816bcb317f80c325` |
| code.bin | 236 | `479e010ba8e0b7fbea184c024408f2a1ce266aa519b92445bf03279672f2fb4e` |
| extraction.private.json | 18018 | `4d8f1660667d42398ab56c8e487ebaf8f5c350f73e348bc082bf2963ebc510ef` |
| pdata.bin | 12 | `bc51db5ccd1ae65bc99967caf12530ef5d35c35feea86d3b86f03aa4c6050fd7` |
| pre-body.private.json | 7786 | `c04c6e3a26ea77afcf3a64bee5244af45ee4a97915d19f6d186c8ab6f071c77d` |
| slot.bin | 8 | `765903ba2861e143e5eb57afe5d0fe2ef830541ecf40aeacdf908db0269dfdd9` |

```powershell
python -B tools/client72_d02_proud_lookup_window.py --output <新目录名>
python -B tools/client72_d02_proud_lookup_window.py --audit <fresh目录1> <fresh目录2> --output <新审计文件名>.json
python -B -m unittest tools.test_client72_d02_proud_lookup_window -v
```

最终两份audit各15186B/SHA `9e48e1dfb3bc27073e04934572dc21dc57b124ba7d7bed74cbf00701c6376f6c`。离线audit不读当前PE，核八原件相同、source/AOT/caller、pre-body顺序与内容、原pdata/slot、连续解码及所有机械分类。6项测试通过：唯一/重复/重叠pdata、4096上界、slot、decode缺失和1024上界、显式内存/分支、pre-body漂移；人工字节只是门禁夹具，不是客户端执行。工具14879B/SHA `9a696e877ccfe476bf45e58042fc2ef9478afacc340a0a659fa2edf3d7c36d19`，测试2113B/SHA `4602c2d5d606716ed92604ed761699d8b521df24702ce229f833ecf9c2c3935b`。

较早两fresh和audit成功后，补强了pre-body一致性验证，再增列隐式栈指令，最终重新双fresh与双audit；旧结果保留，不作最终交付。没有身份/预算失败或安全、Daybreak门禁；未触碰旧文件、第三literal、payload、callee、进程、网络、构建、DB或Git。客户端验收增量0，资源ready/完整Avatar/D-02仍未验；静态材料交root第三审核，再交Sol max分析唯一完整body。

主线第三已按本卡最终原件/报告通过，测试及限定验收见Project/Harness；六出口汇合proof SHA `0431793b8b8310d027d08d9de6761fe2164ce6f748eca039bb18a6b26b40c151`。只验本文静态/机械范围，真实执行、row/loader/语义及各父项不因该接受段晋级。
