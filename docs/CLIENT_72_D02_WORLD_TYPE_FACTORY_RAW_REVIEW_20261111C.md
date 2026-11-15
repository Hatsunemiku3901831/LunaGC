# World type factory：392B 原件的控制流与解码修正

任务 `F-03d3s2/WORLD-TYPE-FACTORY-RAW-CONTROL-REVIEW`，父项 D02-R1/R3。**线性解码失败的原因已查明：末32B是入口指令明确引用的8项signed rel32跳表，不是缺失指令。** 只用已保存392B便可划清全部代码/表字节，不需要新窗或窗外table。

显式条件U32=1的表项指向请求25270类型的分支，随后准确CALL已拒allocator。分析停在请求类型来源；分配、构造返回、实际klass/+100/slot6与MPLevel对象依赖仍停止。本页不执行factory，也不将host游标投影改称实际reader帧。

## 固定来源与当前验收状态

原件根 `local/migration72/d02-resume-20261111c/world-mplevel-type-factory-window/fresh1/`：完整pdata region `[14e8a84a0,14e8a8628)`、392B，body SHA `780ba856fa671de842d525ab9dc0684d7f5a0267332cf532bbb902af3f83c9b8`；原failure7328B/SHA `f7297f43301d34927f506559292dbf0122b10787d454924c1da005dba3d7f1c0`。原轮失败保持 `stage=single-body/newBodies=1/bodyBytesRead=392/passed=false`，当时没有完整asm、第二fresh、audit或集成第三，不追认该轮。后续修正源已完成限定验收：8测试、双fresh与独立第三十件一致；见[恢复索引](CLIENT_72_D02_RESUME_20261111C.md)，提交 `08ee5792`。

本轮复核保存的8件门证据及failure引用的固定输入，共34个来源pin。boundary/pre-body各7252B同SHA `361f538d58b9e72c46b8ddb274bedfbff1e117282aaf816208d318caf5c3fcde`；真实pdata record RVA `1aa83f98`、原12B `a0848a0e28868a0ec00e5004`，unwind RVA `4500ec0`，exact/containing相同。原caller20B/AOT16B/profile/五stock/mapping/预算已过是**保存门证据的事实**；本轮没有重新打开GAME_PATH/PE或重验现场stock。

原[机械专题](CLIENT_72_D02_WORLD_MPLEVEL_TYPE_FACTORY_WINDOW_20261111C.md)和collector/test保持只读。collector SHA `29f09276e0d59e693244485440832f1758d89989d665a74fc08e2407869f06a5`，test SHA `98da3cb3826ef56de724f8aefe86b31541ed11c0ad169dc78116c92524d32e43`。作者此前6组测试不计为本包执行；本包只作保存字节的静态解码/来源与地址算术核查。

## 指令怎样证明跳表

固定Capstone5.0.7从真实entry解码，入口9条连续指令给出完整约束：

| 原PC | 原指令作用 | 数据/控制约束 |
| --- | --- | --- |
| `84a5 83f907` / `84a8 7739` | `CMP ECX,7`；unsigned `JA 84e3` | 进入表路的U32参数范围0..7 |
| `84aa 89c8` | `MOV EAX,ECX` | 索引从受界定的U32零扩展到RAX |
| `84ac 488d0d55010000` | `LEA RCX,[RIP+155]` | nextRIP84b3+155=`14e8a8608`，是表基址 |
| `84b3 48630481` | `MOVSXD RAX,DWORD[RCX+RAX*4]` | 每项4B，取signed32，不是QWORD或unsigned RVA |
| `84b7 4801c8` / `84ba ffe0` | `ADD RAX,RCX`；`JMP RAX` | 目标为**表基址+signed项值**，不是各项地址+项值 |

表范围据此为 `[8608,8608+8*4)=[8608,8628)`，恰在保存body内并终止于pdata end。逐项由原bytes得到：

| selector | 原LE32 | signed位移 | 目标 |
| --- | --- | --- | --- |
| 0 | `b4feffff` | -332 | `14e8a84bc` |
| 1 | `e2feffff` | -286 | `14e8a84ea` |
| 2 | `eefeffff` | -274 | `14e8a84f6` |
| 3 | `fafeffff` | -262 | `14e8a8502` |
| 4 | `86ffffff` | -122 | `14e8a858e` |
| 5 | `9fffffff` | -97 | `14e8a85a7` |
| 6 | `baffffff` | -70 | `14e8a85c2` |
| 7 | `d3ffffff` | -45 | `14e8a85db` |

8目标均是前方原指令边界；这个表判定来自bounds、寻址宽度和computed jump，不因某些字节解不开而将它们叫data。

原collector让 `base.decode`把全部392B当指令。它先正确越过360B代码/NOP，再将表头 `b4fe`误解为 `MOV AH,fe`，累计362B；随后在 `14e8a860a`的 `ffff`停止，触发完整覆盖断言。故异常精确复现为“进入数据区后的线性解码失败”，不需要补零、skipdata或邻窗。

## 392B 完整布局与未决边界

| 范围 | 大小 / 条数 | 直接依据 |
| --- | --- | --- |
| `[14e8a84a0,14e8a8606)` | 358B / 81条code | 从entry/direct branch和8项computed branch建立潜在控制图，全部指令完整解码；code SHA `ff72581b0136babd3694d4d7eef8ec3d0b87a9cda89b258c3e4d7897356819e2` |
| `[14e8a8606,14e8a8608)` | 2B / 1条NOP | 原 `6690`单独完整解码；无entry派生的正常控制边到这里。“用于对齐”只是解释，不是判定依据，也不把它当data |
| `[14e8a8608,14e8a8628)` | 32B / 8项 | 上述指令约束的signed rel32表，SHA `12b5a0194a84cfdd30184fa6784922a6b385f5e7af26e02842b2f57932595143` |

三段共392B，交叠0、未解释字节0。最高code为 `8605 INT3`，正常RET为 `85fa`；不存在从正常末端落入表的边。本轮记录93条潜在控制边、12个直接CALL site，仅用于source布局；**CALL后的语法续接不证明callee正常返回或任何内存效果**。没有读这些callee或RIP globals，未审返回/数据输入仍未知；完整code/data布局不等于完整logical function业务成立。

## 条件 case1：只保留请求类型，止于A

U32=1仍仅来自[首项合同](CLIENT_72_D02_WORLD_MPLEVEL_PARENT_CONSUMER_20261111C.md)的显式host文件投影：原key helper `14f0de820`未证推进reader到13，不能把该条件称自然selector或实际调用。

条件1的准确原边是：

1. 表项 `14e8a860c e2feffff` → `14e8a84ea`。
2. `84ea 488b0dc72cfff6`从RIP cell **14589b1b8** 载RCX；`84f1`原E9到 `8595`，期间不改RCX。
3. 同cell的旧固定usage702867/source95470已绑定class **25270 / AIBBPDMJMHB**；来源为已验167B分派语义 `value196498-analysis/main-third.private.json`，12975B/SHA `3fb9165f741a1831bd328c4183ad20f4f79a6fa24a6a3c7bbf4d99f8d4a8b7dd`。本轮没有读取cell的当前值。
4. `14e8a8595 e826b1c8f1 → 1405336c0` 是准确已拒A allocator入口。**在此停止分配/构造返回、actual klass/+100/slot6、map和MPLevel对象依赖**。后面原bytes仅用于布局覆盖，未分析为成功对象。

因此新增有界结论是“条件case1请求25270类型的来源已定”。不能由请求class推实际返回klass，不能据其选择 `14b132c20` slot6或取得原parser字段，也不通过host填返回值。旧1343B parser `224→1405336c0` map分配及key-helper/cursor模型边界继续有效。其它selector只核表目标/布局，不扩其配置业务。

## 历史最小修正与验收规格

私有规格 `local/migration72/d02-resume-20261111c/world-type-factory-raw-review/collector-layout-fix-spec.private.json`：**9355B / SHA `802e711249e8cf5d75e30f20c7c582f4b3df1532c185c4a1b1944edfbe494e18`**。本包不改collector、不运行测试或fresh。

修正只限当前collector的 `decode_body`、report/audit及对应测试，**共享base.decode与原来源/caller/AOT/pdata/mapping/预算门不变**：

- 用当前392B/原hash作来源门，再从fresh保存bytes重证入口9条、unsigned0..7、table基址/宽度/signed extension与表尾。不能仅按“末32B”切掉。
- `body.asm.private.json`保存前358B的81条code；新增 `body.layout.private.json`保存独立解码的2B NOP、8个原signed table项、computed edges及完整覆盖/无交叠检查。原9文件因此变为10件，统计/身份字段应如实更名。
- audit不再要求“指令bytes=392B”；独立重证 `81条code bytes + 2B NOP + 32B table = 原392B`，验证所有branch目标是code指令起点、无边进入NOP/table/interior，不接受未解释gap。`logicalFunctionComplete/naturalMapProduced/MPLevelObjectProduced/actualReaderFrame`保持false，另记 `layoutComplete=true`。
- baseline collector/test/topic先按旧hash留副本；获授权修改后的v2记新source hash，不要求v2继续等于旧baseline hash，也不放松未改来源的pin。原fresh1/body/failure/trace保持原样失败，不覆盖或追认。

测试规格包括真实392B的81+1+8独立预期、入口bounds/LEA/scale/MOVSXD/ADD/JMP变异拒绝、signed32/64位地址及table/NOP/interior/outside目标拒绝、未解释bytes与落入data拒绝、不匹配的普通incomplete样本仍失败、完整重建与audit篡改拒绝/0PE/CPU，以及全部业务flags保持false。原6组来源/rel32/AOT/pdata/节/预算/short read/late-failure门禁保留，不能用新布局放宽它们。

本包当时建议在**新目录fresh2/fresh3**只重取原同一个392B窗，补十件双fresh、pureartifact audit及主线第三；不重取callee/global/额外window。此处只是成熟派工规格，本包没有执行重试。源修正可独立验收，A依赖仍停，不因这是普通解码问题转用其它工具取得被拒返回。

## 本包交付

独占根 `local/migration72/d02-resume-20261111c/world-type-factory-raw-review/` 的 `layout-review.private.json` 为 **39946B / SHA `ccaf52110596c89ad81155beb31a22c8cead8b592e9b68921d05b30ba319e368`**，含34pin、原392B、82条静态解码记录（含NOP）、完整表/边及A停止点。

本轮新PE/callee/global/xdata/live/目标CPU读取与执行均0，模型/collector实现/测试/fresh/构建/Java/DB/端口/client/调试器/部署/Git/index均0。旧所有工具、专题和原件只读；只写本新专题/私有证据。没有新安全拒绝，不将普通技术失败称外部blocked，也不启新研究树。本包交回时source尚未验收；后续修正源已按本页首段限定验收，自然reader/MPLevel对象/字段、Scene/resourceReady/D02仍未晋级。
