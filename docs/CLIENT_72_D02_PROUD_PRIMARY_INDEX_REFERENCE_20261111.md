# E-01 / R3 Proud PrimaryIndex 文件参考结果（2026-11-11）

本项仅对既存 7.2 原 payload 运行 host 文件参考算法，条件是每项读取时 `byte1457e5ef3 == 0`；没有观察该 flag 的自然值，也没有执行 native、CPU、客户端、资源 loader 或 backend。候选范围不是已批准的 row，不授予业务字段、Java 公式或资源 ready。参考规格为私有固定文件 `c-proud-index-handler-contract/next-primary-index-parser-spec.zh.md`，8480B，SHA-256 `9741042201743440428d892cf1bd687c98872c48e7ebed7274c55fe0ff5a046f`。

## 固定来源与独立正例

每次执行先核固定清单 47977B / SHA-256 `e0df5135b0ecbf585c86959d33f4c85412b814f2d6a657b73515ea50902ebf01` 内全部 238 项、合计 317072913B，逐项尺寸与 SHA 均匹配。三份既有 Proud index payload 各 31858B / SHA-256 `f87adf6f8b1006ccc99483af251b9d491737bf8bc87b4c7097bb893301e8eb05`，对应三份 locator result 各 52202B / SHA-256 `1a2d897cf40fd1b922ecf8e46e1c5c040ad06f2d6483360b6bab2eb2f63e3b02` 均在清单内。固定主表 locator result SHA-256 `e2513467fad5fb61a90e6aeec27c1ccf030b5b52fa75cb9a6874749190424839`；其已提取 payload 为 2132778B / SHA-256 `82a8017c6c2868e41e3ce8fc02f8bc04437cb641ec653ed3383c5cc109e25835`。工具核对该报告 entry 长度、SHA 和 payload span 后读取同目录原 payload，只以单项 pair 指定子范围作 slice。

在解析 Proud 前，重新解析既存 Gadget 原 index（163245B / SHA-256 `1e95ae771d6597160b2438d5a1ead40f04f58a84a26be466c0226fab6923138f`），与既存 native 结果（1860159B / SHA-256 `466b0762a484c8b0c946151fa2301ffed870e550e6b4cd4bddb1ea435a3f7deb`）对照：全部 31682 条有序 key/pair、cursor 163245、EOF、末端 2944342、三个 hit 与 max-U32 miss 均一致；首 3B 截断命中数组边界且无已插入项。这是旧 Gadget 的显式预分配 reader/owner/dictionary 正例，不是新 Proud 的 native 验收。

## 实际文件结果

- header span `[0,2)` 原字节 `c663`，raw U64 12742，按低 32 位 zigzag 得有符号 count 6371；首项的原输入起值为 4，非硬编码默认值。
- 条件参考正常完成 6371 项，12744 次 primitive，cursor 31858，余量 0；无重复 key、累计 U32 wrap 或主表范围越界。自然 handler 不检查 EOF 或主表范围；这些数值仅为本样本观察和研究筛选。
- 首项 key 22101，pair `(4,330)`，候选 `[4,334)`，slice SHA-256 `cfe85822a6606b824d731b5bb7ba442c8d0d4113187294de5457e3a1e04c8a6b`。末项 key 900101，pair `(2132482,296)`，候选 `[2132482,2132778)`，slice SHA-256 `65c10b11177870ea8dbf14a0c6358696273187a6fee44006388c937c5a49b946`。
- key 0 与 max-U32 查询均为 miss；没有全零 pair 或零长度项。查询的 hit 与 pair 值独立表示，不能用 `(0,0)` 判 miss。各项的完整顺序、key、两原 U32、数学 end、wrap、每次 readSpan/raw U64/高 32 位、候选 span 与 slice SHA 保存在私有报告；该样本各项 primitive 高 32 位均为零。

初始 epoch 的 fresh1、fresh2 由独立进程分别读取对应原 payload；root 随后用 main-third 独立第三进程复验，三份各 5954083B、逐字节相同，SHA-256 `ac70d466003ad5381ca98d7185a94b1c6c455cdacb9a767d6f8d0c1d5f9d8670`。独立 audit 使用第三份原 payload 重读全部 6371 项的所有 primitive span 和字节、重新计算 U64、pair、顺序、候选 slice 与 SHA，并核对终点及统计，通过；audit 文件 SHA-256 `a57b285d314480adb1b3b6c9ea07b506f8756a7cd28964d08d7e75ab49a91d50`。它不只比较最终报告 SHA。

## 语义与停止边界

文件 primitive 最多 10 字节，按固定 504B helper 语义计算 U64；第 10 字节高位仍置位时原 helper 写值、AL=1 且 cursor 不推进，参考驱动为避免无限循环停在该精确位置，不能声称原 helper 拒绝。截断记录原数组边界；负 N 原 handler AL=1、零项，但驱动标 partial；重复 key 保留原错误 helper 路径与重复项，不能解释为覆盖或正常 false。初值仅读一次，随后每项加法按 U32 wrap；主表越界仅是驱动范围标记。未知替换 dispatch 未模拟，handler 返回仅声明 AL，不推断完整 EAX/RAX。

限制字段均保持：`targetCPU=0`，`nativeParserExecuted=false`、`naturalBackendProven=false`、`actualIndexFilled=false`、`actualRowSpanApproved=false`、`factoryProven=false`、`groupAliasApproved=false`、`levelAliasApproved=false`、`thresholdProven=false`、`javaFormulaProven=false`、`resourceReady=false`、`completeAvatar=false`、`d02Complete=false`。同 key 的 pair 和候选 span 只作为下一项独立研究输入；真正 row parser 仍需目标 63831 消费者和选定 backend 有效字节条件的证据。

## 复现与测试

以下命令从 checkout 根目录运行，输入与输出均为已有本地私有目录；不会打开当前 PE 或调用旧 native 模块。

```powershell
python -m unittest tools.test_client72_d02_proud_primary_index_reference -v
python tools/client72_d02_proud_primary_index_reference.py run local/migration72/d02-resume-20261110b/c-proud-primary-index-reference/NEW-UNUSED-OUTPUT.json --source fresh1
python tools/client72_d02_proud_primary_index_reference.py audit local/migration72/d02-resume-20261110b/c-proud-primary-index-reference/fresh2.json --source main-third
```

9 项测试通过：空/截断 header、零与负 count、一次初值、两项以上累计、边界 key、高 32 位截断、非最短编码、9/10 字节与第 10 字节高低位组合、三个截断阶段、U32 wrap、重复、尾字节、零长度与越界；audit 篡改 span/slice 检查也通过。没有运行构建、部署或客户端验收；D-02 仍未完成。后续由 root 对第三进程结果和实现独立审核，再决定是否开启受控 row 消费者研究。

## root 审核修正：报告防覆盖

root 第三进程复验初始 epoch 的报告后发现，原 CLI 使用 `write_text`，会覆盖已有报告；因此不应对已有输出路径重复运行原入口。初始工具及测试已按原字节另存私有 `epoch1-tool.py`（SHA-256 `7148a43ebe4ab139711e8152f643dc28546e43ceddc2b24658f68106e6a019f1`）和 `epoch1-test.py`（SHA-256 `b2a101c593666b82d9fbe6010b0496a865a366fca4d2e259fae1a4c0b5ee3344`）。三份原报告和 audit 保留原字节；报告未包含本工具自身的 SHA，故此入口修正不产生新的解析 epoch。

现有工具改为以排他创建模式 `open('x')` 写报告；目标已存在时由打开操作直接拒绝，不采用先检查再写入。新增测试以已有的非文本字节文件模拟报告，验证 `FileExistsError` 且原字节保持，现共 10 项测试通过。原 9 项和 root 第三进程结果属于修正前 epoch；此次只验证入口修正及既有测试，没有新增 payload 或改变解析语义。上面的新输出名称是占位符，运行时须选择此前不存在的路径。
