# F-03d3s2 / CONFIGMETA-LOAD-CONTRACT（2026-11-11B）

## 中文最小计划

| 项目 | 本卡合同 |
| --- | --- |
| 目标 | 用已保存8个raw候选的metadata归属、准确ConfigMeta持有字段及签名关系，选出有证据的最小caller提取优先级；若不能区分则给出完整有限分批方案。 |
| 已有 | Terra完整候选与唯一pdata清单，raw均为`4c8b05`；已验field108683/GLOBAL+c8e0的消费来源，ConfigMeta的两个实际声明持有字段；先前内置assets清查已冻结。 |
| 方法 | 先核固定原件完整SHA，再只将候选begin与固定metadata方法登记、owner字段、父类和已恢复参数作精确join。只用直接类型/字段关系排序，不按方法名或期待filePath。 |
| 读取和写权 | 不重复376,612,864B扫描、不读取新PE code body；只新增本专题及私有`scene/`的source清单、元数据关联和下一窗口规格。已冻结工具/专题及共享文件只读。 |
| 产物和验收 | 有哈希来源的候选分类、优先原因/限制、准确完整extent及预算；新窗交主线定夺/调度。raw命中不等于指令边界，声明owner不等于field实际写入。 |
| 停止 | 无新安全拒绝时继续普通技术分析；原预算停止不称外部阻塞。A/B被拒动作及依赖仍不读、不运行、不接续。 |

## 当前状态

原候选预算停止有效，属于本次扫描包的范围门禁，不是外部输入或安全阻塞。来源关联已形成最小优先范围，主线已批准先取单个141 B完整窗；本卡尚未读取或验证该body。

## 候选的准确声明关系

只读固定 `metadata.private.pkl`（117,201,003 B / SHA `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f`）、signature（78,649,549 B / SHA `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe`）和 [原扫描清单](CLIENT_72_D02_SCENE_CONFIGMETA_XREF_20261111B.md)。所有原件先核完整SHA；没有重新打开PE或扫描可执行区。

八个 containing begin 在固定 metadata 中均只有一个准确方法登记。表中“归属”是声明关系；raw load尚未通过完整body的指令边界验证，不能据此说已调用或写入字段。

| 原候选序号 | begin /完整B | owner /method | 与本问题的已恢复直接关系 |
| --- | --- | --- | --- |
| 0 | `148c1b910` /441 | 45973 /381954 | 本类没有已解析的ConfigMeta直接持有字段，也没有已恢复直接ConfigMeta参数方法；不能排除其访问全局或间接对象。 |
| 1 | `14b299c20` /566 | 59555 /494437 | 同owner的494423、494433显式接受ConfigMeta77121，属于第二层声明线索；本candidate本身零显式参数。 |
| **2** | **`14d1dad60` /141** | **23071 /177845** | **owner正是已验消费根field108683/GLOBAL+c8e0的声明持有者。** |
| 3 | `14d1db620` /357 | 23071 /177858 | 同一准确字段owner；本轮不启，141 B不足时另行决定。 |
| 4 | `14d1db790` /10,851 | 23071 /177853 | 同一准确字段owner；超过原8192 B门槛，当前不启。 |
| 5 | `14f486da0` /3,730 | 22132 /170469 | 没有上述已恢复直接声明关系；不按其名称排除。 |
| 6 | `1528b92e0` /724 | 23074 /177886 | 没有上述已恢复直接声明关系；不以相邻typeIndex推成23071的内部类或调用后继。 |
| 7 | `1531e4400` /2,121 | 12832 /94120 | 没有上述已恢复直接声明关系；不按其名称排除。 |

全量已解析字段中直接含ConfigMeta77121的声明仅有 `23071.field108683`（rawOffset `400c8e0`）和 `59556.field295467`（实例+10）。显式参数consumer仅有 `43744.method363828`、`59555.method494423`、`59555.method494433`。这是固定提取覆盖中的直接关系；返回类型和完整generic substitution仍未知。59556与59555的数字邻接和名称不能自行证明嵌套/闭包关系，当前不据此另开对象或body。

因此先选候选2的依据是：**已有实际消费者使用field108683；三项候选准确属于这个字段owner；141 B是这组中最小的完整真实pdata extent。** 这是用最少新原码检查生产来源的优先级，不是说它已经执行该store、已经给出filePath或其他七项无需再审。没有按方法名称、期待路径或“最像初始化”选择。

## 已批准的单一141 B提取合同

冻结请求为 `local/migration72/d02-resume-20261111b/scene/configmeta-m177845-one-window-request.private.json`，6,043 B / SHA-256 **`913283b2c80f7e9cc9bbbce79aafb467c949320a57f61d0ac4aa69c002755e1a`**。请求含五份stock大小/完整SHA、metadata/signature、原八候选/pre-body、原field来源及工具pins。

| 门禁或原件 | 单窗准确范围 |
| --- | --- |
| 方法声明 | owner23071 /method177845，flags96/static，零显式参数，slotFFFF，普通登记entry `14d1dad60`。 |
| 普通AOT | 从已验CodeRegistration `1422f98e0+70` 重新读取8 B `P`，再读`P+177845*8`（offset `15b5a8`）原8 B，必须指向准确entry；不复用旧P。 |
| 真实pdata | RVA `1a9f3a28`，原12 B `60ad1d0dedad1d0d6c2b3d04`，完整区间 `[14d1dad60,14d1daded)`。在完整PE SHA匹配条件下复用原unique目录索引，并定点核该12 B；不重复376 MB扫描。 |
| 原raw site | `14d1dad92` 的7 B `4c8b05af4088f8`，准确RIP target为MethodInfo cell `145a5ee48`。只有完整连续解码证明这里是实际指令起点后才记有效xref。 |
| 新body上界 | 仅1个完整141 B、至多141条；code/原site覆盖不重复算distinct。定向distinct原件169 B，含body前单独site复核的逻辑读取176 B；五完整文件hash读取另记。 |
| 产物 | 原code/pdata/AOT cell/slot/site、连续完整asm与SHA、实际call/ret及filePath/handle/MethodInfo准备；field108683的store只在原指令确证时报告。两fresh原件和可供主线第三的来源结果。 |

身份、AOT、pdata或site漂移在body前停止；解码不完整或raw site不是指令边界则保留真实结果，不扩窗。直接callee只登记来源，不取其body/xdata，不执行CPU/native/live。141 B不足后由主线单独决定357 B，10,851 B及其他候选当前均未启。整个8窗原范围合计18,931 B，但本卡没有申请整包读取。

## 关联审计及复现

私有 `audit-configmeta-candidate-priority.private.py` SHA-256 `41d9c443431dc520f3c933044d0a24b64f3c1f2fc88b9023c2f6c0115505a7cc` 只读冻结来源，25项来源/关联断言。两fresh报告各 **12,150 B / SHA-256 `e60f35fae8f90bc8dd0e205c0a1924fce367a15e858b882efefe1d7cd763d4a1`**，逐byte一致。不是25项原生测试，也未验证body或自然调用。

```powershell
python -X utf8 -B local/migration72/d02-resume-20261111b/scene/audit-configmeta-candidate-priority.private.py --output local/migration72/d02-resume-20261111b/scene/priority-main-third.private.json
```

使用新输出名，不覆盖已存原件。主线第三尚待；已验assets文件/工具保持冻结。本轮仅离线来源关联和规格，没有运行材料变更、构建、部署或客户端新增验收。实际ConfigMeta.bin/payload/reader、MPLevel原分组、World返回对象/slot114、Pool/组件与完整D-02均未验。
