# Scene cluster getter 单原窗采源

任务 `F-03d3s2/SCENE-READER-CLUSTER-GET-INSTANCE-WINDOW`；父项 `D02-R1/R3`。本文只交采源与停止点。**316B/63条来源已完成限定验收：严格上限修正后真实第三与双 fresh 十件一致，7测试通过。** 提交 `5458d0f`，独立回执 `integration/acceptance27b/completion1.private.json` SHA-256 `1a43f25e70e5da6e9d70126292cf589050f5b69c0074d8fddf1c390dcb3fb6a5`（相对本轮忽略证据根），详见[恢复索引](CLIENT_72_D02_RESUME_20261111C.md)。不证明自然 reader、receiver、`+18` 初值/reset 或 D-02 客户端验收。

## 固定输入和门禁

私有规格 `local/migration72/d02-resume-20261111c/scene-reader-cluster-remaining/getter-one-window-request.private.json` 为 12733B，SHA-256 `68084243b272e0e3d255ddc9361f94fe9df6bc798c4eada67624b86052389ebf`。采集器固定该规格、所列原件、工具、profile、metadata 和 signatures；只读重组旧 353B Recreate，核两个 signed rel32；完整 metadata 中 ordinary owner11009/m77588 为唯一地址别名，signatures 子集零命中不改变 AOT 适用。stock 五件全部按规格的大小和 SHA 核对。

每次 fresh 的准确读取账：

| 类别 | 实际读取与核验 |
| --- | --- |
| 文件身份 | 对规格列出的五件 stock 全文件计数与 SHA-256，结果见私有 extraction 的 `inputs`；固定来源和 metadata/signatures 分别全文件哈希，不算作新代码原窗 |
| PE headers | 328B header、520B section table，13 sections；不把它们混入目标函数字节 |
| caller | 两个准确 E8 各5B，合计10B；`146ecd910` / `146ecd96c` 均 signed rel32 指向 `146e52c30` |
| ordinary AOT | cell8B + slot8B；`1422f9950` → `1428a54d0`，`P+77588*8=14293cd70`，slot 指向 `146e52c30` |
| exception directory | 14344080B，SHA-256 `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d`；exact 与 containing 各唯一且同一原 row |
| selected pdata | 12B，SHA-256 `a1013d03d3e2cbb4b68f1949b6475519f52adf7ed23648e35b14168304962962`；row RVA `1a6d1030`，Begin `146e52c30`，End `146e52d6c` |
| body | 唯一完整 file-backed 可执行 region 316B，SHA-256 `5cb613136200b12274b42c859e7448ed31f693f9b673c04f911b3720c44794d4`；63 指令全字节解码，无自动扩窗或第二 decoder |

header、exception directory、五件 stock 哈希、caller、AOT、selected pdata 和 body 分别记账。未读任何 subcallee body、global 内容或 xdata；未进行 live、native CPU、model、getter/factory 执行。

## 准确停止边

本 getter 的原 PC 索引中 `146e52c62` 和 `146e52c7e` 都是准确 `CALL 1405336c0`。它们命中 A 安全拒绝边；对应返回、对象身份及 reset 依赖立即停止，不能解释被拒产物，也不能借普通返回或缓存接续。原拒绝与缺失完整原提示的记录见 [Daybreak 队列](CLIENT_72_D02_DAYBREAK_QUEUE_20261111.md)。其余引用仅保留为私有原件中的 CALL、RIP、branch、RET、tail 索引，不追踪目标或将普通 RET 升级为来源、自然分支或对象有效性。

## 验证与复现

- `python -m unittest tools.migration72.test_scene_reader_cluster_getter_window -v`：6 项通过，覆盖基本 caller/AOT、ordinary metadata 与 signatures 区分、pdata 唯一/预算/file-backed、完整解码与指令预算、pin 失败 body0、禁止 pickle globals。没有声称涵盖所有故障注入。
- 两次实际命令：`python -m tools.migration72.scene_reader_cluster_getter_window --output fresh1` 和 `--output fresh2`；各自完成全部门禁，结果同上。无普通失败、输入冲突或新安全拒绝，故无实际失败原件。
- `python -m tools.migration72.scene_reader_cluster_getter_window --audit fresh1 fresh2 --output audit.private.json`：10 个保存文件逐字节一致、纯原件 audit 通过，当前 PE 读取0；audit SHA-256 `93f1441201924fa5f6207c40bf3f5a116877f4e433118d5d354526bd76639b34`。完整边界、预检、原字节、逐指令和抽取账均在忽略目录 `local/migration72/d02-resume-20261111c/scene-reader-cluster-remaining/getter-window/`。
- 以上为原采源 epoch 的记录；严格上限修正后的主线第三次命令和审计见下节。接受原窗亦不能解除上面的 A 依赖。

本批仅离线工具、纯测试、此文和私有采源文件；无构建、部署、客户端运行、Git/index 修改或实机验收。实际执行与共享状态由主线集成登记。

## 严格解码上限修正（GETTER-STRICT-DECODE-CAP）

主线发现旧共享 base 解码循环先取第257条才报上限错误；原始 63 条结果没有触及该边界。此处只在本采集器实现固定上限：调用 Capstone `disasm(..., count=256)`，迭代时也先判断数量，达到上限且仍有剩余字节时直接报错，不请求第257条。若未达上限而解码器提前结束则报 `incomplete decode`；短读保留实际 body 并报 `short body read`。本修正不修改共享 base、固定请求或读取范围。

修前工具、测试和本文快照保存在 `getter-window/pre-strict-cap-snapshot/`，SHA-256 依次为 `ca433cfafba8b58c8556be7938c4ccb00fc1c1ff0b4d7ede127053d6d796b1c8`、`b1565bc6b630f9f3a5b03623279e0703f47a45b3c5fd940f4f75d9f3efaabd43`、`6934ab3838a5002ea0ba3216a39ed9946a73a0a32f8211e2f8b80f57e34466fa`。原 `fresh1`/`fresh2` 和 audit 原件保留；它们属于修前 epoch。

修正后：

- `python -m unittest tools.migration72.test_scene_reader_cluster_getter_window -v`：7 项通过。用真实 Capstone 的包裹计数核对传入引擎的 `count` 为256，且 257 条单字节指令样本只产出256条即停止；另覆盖恰好256条、短读原件保留及不完整解码失败。
- `--output v2-fresh1` 和 `--output v2-fresh2`：各自门禁通过，完整原窗仍为316B / 63指令，body SHA 与修前相同；两次十件原件逐字节一致。`--audit v2-fresh1 v2-fresh2 --output v2-audit.private.json` 通过，当前 PE 读取0，audit SHA-256 `86411a24e5586257f579b06cc69ed26d3aad37023e64eed4536492ad607dac44`。
- 修前 `fresh1` 与 `v2-fresh1` 的 caller、AOT、pdata、body、解码记录七个文件逐字节相同；三个报告仅 `toolSHA256` 不同。无新普通失败或输入冲突。每轮原件读取账保持上表范围。
- 主线第三次准确命令：`python -m tools.migration72.scene_reader_cluster_getter_window --output v2-third`；比较命令：`python -m tools.migration72.scene_reader_cluster_getter_window --audit v2-fresh1 v2-third --output v2-audit-third.private.json`。作者子任务当时没有执行第三次；其后主线已独立执行并比较通过，结果见本页首段。

此修正不解释或实现 A 调用对应返回、对象身份及 reset。无目标执行、运行服务、构建或 Git/index 操作。
