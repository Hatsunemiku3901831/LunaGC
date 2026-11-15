# Relic Codex typed入口：完整边界与静态来源

`D02-INCR-A2-CODEX-BOUNDARY-20261116`与`D02-INCR-A2-CODEX-SOURCE-20261116`已完成各自限定来源出口。固定方法为`MoleMole.MonoSceneUISetup`（21946）的`ShowCodexReliquary`（168869）；真实参数包含目标类型74316，因而可作为[A2有界自主研究](CLIENT_72_D02_INCREMENTAL_REUSE_PLAN_20261116.md)的新typed消费者候选。**完整2792B来源已取得；整窗`semanticInterpretation=false`，rankLevel/setId职责、实际资源行及ready均未闭。**

## 固定身份与两个独立出口

| 输入或产物 | SHA-256 / 准确身份 |
| --- | --- |
| 固定目标PE | `YuanShen.exe`，450927000B，`8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| metadata / signatures | `current-structures.json`：`d5d7f671a622439b2f9a3ed0018ce551aa5c573cd9ca68bed3b006712b8a98aa`；`client72-signatures.json`：`28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe` |
| 原边界请求 | `codex-boundary-request.private.json`，8499B，`eb15628b4dce59380405ea2e26a3009c0d35127f800d8b11a322e755e2c314ea` |
| 边界run2结果 / manifest | 6991B，`8d17e8d2379690f8f25e3583ca619332dc65f5c1c8bf6402ee7bef60e11fa841`；manifest：`1dbb2f25cb7b801b77182fadf6913cc6fe9e804d16df239e91d7118b5547c881` |
| 独立source请求 | `request.private.json`，4185B，`b84fcfb5591576c1683daddb74639db5502442cc95acecc363ebeb2878f3a1cd` |
| source结果 / manifest | 4440B，`103c75c3894aebbfad1c9528de599884e379087b32070fbf0399512b89e16a9f`；manifest：`47c52a54633c0a2d4be301a5c62fd48a05914d0d9541d233f98e4830f363665e` |
| 完整body | 2792B，`3fd04ec7192e2347533827703f673cf834ef5b13963b7b452c791de9953df284` |

固定metadata/signatures只给出唯一21946/168869别名组合，receiver为instance21946。两个真实参数分别是类型引用339772→class74316 `DFHAIDJGOEG`，及类型引用124→uint32；返回类型仍为`not-recovered`，不按方法名称推断资源字段角色。

边界任务独立确认fresh AOT root/slot共16B均指向`0x14c3fa5a0`；完整exception目录中唯一包含且唯一begin的pdata记录给出`[0x14c3fa5a0, 0x14c3fb088)`，完整大小2792B、文件offset196750752。pdata的unwindRVA只记录，不读取xdata；该边界与既有冻结区间无相交。边界阶段读取PE header两次共4616B、4096 distinct，完整exception目录14344080B；五stock仅hash总550661784B。body读取/解码均0，不把边界通过当作body语义。

后继source使用已验边界，固定PE SHA后以**同一文件句柄只读一次2792B body**，Capstone5.0.7一次解码632条，完整references259条。新header/AOT/pdata/exception目录读取均0，callee/global内容/xdata/payload读取均0；未执行目标CPU、RPM、客户端、JVM或数据库。

## 准确冻结边与下一分析范围

完整静态索引确认直接`CALL 0x14c3fa784 → 0x1405336c0`，这是已有A精确冻结目标，保持该CALL返回、构造及依赖后继停止。整窗`semanticInterpretation=false`，不能凭body完整或方法名批准rank/set用途，也不将此CALL泛化为所有独立Relic来源都受限。

不依赖该返回的前置片段交Sol max继续有界分析。下一出口须实际建立目标字段到等级表key、套装用途或独立非零role oracle的关系；若仍只有typed身份，则保留该缺边，不读候选值选标签、不执行被冻结返回。没有批准新callee采集、全域扫描、资源行投影或实现合同；A/B自主研究继续，父R3及D-02六项0/6不变。

## 首次失败、工具限制与原件

REA `inspect_binary_layout`在本机Windows不支持，边界任务沿固定已有helper完成回退。首次边界尝试在identity阶段因固定helper的执行环境未绑定`ast`而报`NameError: name 'ast' is not defined`；`stockHashReads=[]`、`peReads=[]`，body/read/decode为0。该失败和原脚本保留于原目录；run2只补`ast`绑定后通过，不追认首次成功。

原件分别保存在忽略目录`local/migration72/d02-incremental-relic-codex-boundary-20261116/`（失败与`run2/`）和`local/migration72/d02-incremental-relic-codex-source-20261116/`。完整asm、references、body、driver、输入账及manifest仅留本机；共享文档只提交本摘要。集成只读核对固定result/body和manifest身份，未重跑PE、采集或测试；没有运行代码、Proto、配置、补丁或资源变更，无构建或部署。
