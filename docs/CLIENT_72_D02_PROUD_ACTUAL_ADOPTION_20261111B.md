# E-01d1 / R3-PROUD-ACTUAL-ADOPTION（2026-11-11B）

## 本轮中文计划

本轮是 Proud 旧号依赖的独立续行。复用已验固定 key 22101 的条件文件语法、组和基础等级用途、全 121 个保存 Depot 原字段、旧号 phase6 与 Java loader；不重做旧原窗断言或扩大为整表恢复。既有 GitHub 分支与克隆来源继续复用。本题准确样本为旧号 typed 81127 第一个元素的 `(group,level)=(221,1)`，同一元素 `+0x10=1`、保存突破值 6。

仍缺两项直接证据：目标 pair map 对 `(221,1)` 的实际命中及返回行；同一 typed 元素 `+0x10` 的原读取/解码如何与同 Avatar 突破值比较，并决定采用或跳过。Java 现行算式和比较只作为待核实现，不能填目标结果或默认值。

1. 重核旧 Proud/门槛审计的来源 SHA 和边界，从原 map producer、准确泛型调用和已有消费者记录中确定首个尚缺的必要动作。只读既存原件，不因静态相符称已观察实际命中。
2. 先在已固定 metadata、完整签名及已存原窗中追同 typed 81127 的 `+0x10`，必须把元素身份、原 codec、Avatar 突破来源、比较及采用/跳过连成一条证据。已排除路径直接复用，不再重审。
3. 现有来源足够时，立即把准确输入、入口、条件、预算和预期输出交主线，由 Terra 实现独立 driver；否则提交唯一原边或准确缺输入与解除条件。新取窗、CPU/native/live、共享运行均由主线另定，不能用 unknown 成功 stub 填缺口。
4. 新增 pin-bound 离线审计和必要测试，两个 fresh 进程输出逐 byte 相同。固定输入完整 SHA、可复现命令、实际结论与未知写入本文，私人角色数据仅留忽略目录。

可写仅本文、新独有 `tools/migration72/*proud_actual_adoption*` 及对应测试、`local/migration72/d02-resume-20261111b/proud/`。其他文件及所有旧输入只读。主线独占 Git、Project/Harness、Proto、共享构建/JVM/Mongo、端口、客户端、调试器和部署。本轮仅离线工具/文档，不需服务重启。

`A-SECOND-RETURN-CONTRACT` 与 B 组合 loader 的安全拒绝及其依赖保持阻塞，不读取其草稿、不拆分实现。新安全拒绝立即保留原文、停止点和失败证据，停止该项及其依赖，不换工具、措辞或代理重试。单项局部通过不升级 resourceReady、完整 Avatar、D-02 或 ready。

验收为：每个新增结论有固定原输入和准确消费关系；实际执行与静态条件分别报告；不能证明的目标项仍为 unknown。无论能否闭合，交回可执行的最小后继与可复现证据，释放本分析包。

## 已验的新静态来源

本轮已把原 map 写入出口从未知泛型调用推进到准确热路径，尚未执行原 Store、原 Proud producer 或 lookup。旧组/等级、605槽、文件语法和121行 Java loader 均直接复用。

| 来源 | 实际结论与边界 |
| --- | --- |
| 原 Store `154d40010..154d400c2` | 178 B / 56条，SHA-256 `dee346d2bb53b96567d9de97db6ffca4c7cc6354d8f3e3033fd64a4aedfe2389`。wrapper+18非空时直接传同pair和原行ID，`154d400ab` 原E8 `e8407b6a04` 到1593e7bf0，R9B=2。冷allocator/Default另路保留。 |
| 精确 Dictionary 与接口声明 | Store usage145935d18为1222 `<80067,U32>` / class50461；18192的原接口引用500948确实为20037 `<同VAR0,同VAR1>`。只读复用TypeResolver的单引用解析，新增静态来源212 B；未运行构造或使用自然对象。 |
| 原 TryInsert `1593e7bf0..1593e819f` | 1455 B / 380条，SHA-256 `d7572088110eaf89adc9dd34d52a5db9d20e5f904f394592f09ecfeeb7b1985c`。唯一闭合AOT为1222.method10196/class50461/methodInstance-1，table46522/spec294419/pointer202494，slot1425fc4a0原QWORD `f07b3e5901000000`。 |

`sources1/2/sources-main-third.private.json` 各9507 B，逐byte相同，SHA-256 `46ef1a0d573e9293f326ab76e9e35305827a4e290ae8376f542f0cb0e9f6438d`。主线已第三核验Store两份五原件和TryInsert双fresh/第三；TryInsert机械工具6项测试通过。本项源工具不读取新code窗或执行目标码；实际interfaceOffset/vtable、自然dispatch、同Proud对象和实际命中仍未验。

Store首次主线采集在body读取前因把metadata绝对文件offset当PE映射而SHA停止，原store1保留；改用原collector确认的offset basis后，store2/3一致。该失败不是目标Store运行失败。另本分析准备时一次把JSON整数offset当hex字符串、几次历史assembly文件名/PowerShell wildcard查询失败，均为只读查询错误，没有产生目标执行或改写原件。

## 81127门槛入口的新增有界索引

本轮冻结新索引规格后，扫描固定PE的三个 executable、file-backed section，合计376,612,864 B。只保留指向已验Depot query14d6d70f0的精确E8/E9和7 B `REX.W/8B/mod10/non-SIB/disp212b8` load形状。没有读取完整函数body或xdata。73候选均有唯一containing原pdata和普通registered owner，落在69个函数、47个owner；候选上限128没有触发。

| 类别 | 实际范围 | 不能由此推出 |
| --- | --- | --- |
| 精确E8调用14d6d70f0 | 7处、7函数、5个owner；其中4处属于MonoGachaItemSlot/Poster，2处为AnimatorEvent子类，1处为owner65145。未见该查询的E9。 | 调用返回值如何消费、同元素+10读取、突破比较或采用。 |
| 7 B load形状 | 66处、62函数。包括既有Avatar、Team、Depot parser/组件/增强iterator，也有新UI与其他owner。 | base就是GLOBAL、原指令边界或typed22284数据流；不因displacement相同而批准字段。 |

索引只用于定位。其5/7 B局部形状加containing pdata不能证明原函数从入口连续解码到site；未把所有候选扩成原窗。所选旧号的81127+10读取、与同Avatar突破值的比较和采用/跳过仍未知。已有UI/ordinal/基础等级/1002事件排除结果直接复用，本次未重复计算旧断言。

`gate-index1/2.private.json` 各53025 B，逐byte相同，SHA-256 `5d0f81592c2c9bfce8bf91e0732a9fa0fae73d726ca95647d3b7741df7adf1e4`。5项实际边界测试通过：signed rel32/精确目标、REX.W与ModRM/SIB及短读、section边界、128/129超额不选前缀、唯一/缺失/重叠exception记录。主线第三结果由主线另记，未预称通过。

## 固定输入与复现

输入均通过当前GAME_PATH取完整文件SHA，未借历史路径或PID。五文件固定为：

| 相对GAME_PATH的输入 | SHA-256 |
| --- | --- |
| YuanShen.exe | `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| YuanShen_Data/Managed/Metadata/global-metadata.dat | `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a` |
| YuanShen_Data/Managed/Metadata/startup-metadata.dat | `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105` |
| YuanShen_Data/Plugins/AccountPlatNative.dll | `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25` |
| YuanShen_Data/Plugins/Astrolabe.dll | `87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442` |

还固定metadata cache SHA `df52eb70fe8e70dfecd1059e898d15e288cfa98bcefc78bcaabf753ae150349f`、提取器SHA `7d1c75162f77c0fe0df154aa1240c142ad3f6af3295c89cc3d7b85372d3cb97d`、旧closed-AOT SHA `6318807d1e034ecd6bcb4c8df95d07eb53670a1de8df76362b7c6270d926bf9e`及本轮原件。完整路径/尺寸/原记录保存在各报告的pins/sources；主线拥有的TryInsert机械工具与专题见[原窗专题](CLIENT_72_D02_PROUD_TRYINSERT_WINDOW_20261111B.md)。

源工具 `tools/migration72/proud_actual_adoption_sources.py` 为9644 B / SHA `5f5ddfbbecbef73ff3c45dfe916a5acd2d25c2a2d2357f1940844834e31f1627`；索引工具为7833 B / SHA `0590c402d028e4f5fad22fd3bdef005e42ffb8298169c2123b0fff3ac5159148`，其测试2412 B / SHA `99e53c1726c024bb86ee6fabe83bd7982a26af0e76c83435484392221465780e`。

输出选此前不存在的任务目录basename：

```powershell
python -X utf8 -B tools/migration72/proud_actual_adoption_sources.py --output <new>.json
python -X utf8 -B -m unittest tools.migration72.test_proud_actual_adoption_gate_index -v
python -X utf8 -B tools/migration72/proud_actual_adoption_gate_index.py --output <new>.json
```

## 成熟实施出口与当前未完成

已交主线的 `hot-insert-implementation-spec.zh.md` 和机器合同 `hot-insert-case-contract.private.json`（8369 B / SHA `016a317ab409a0f755d665a8e2b539be5d54fd622185225557858a1755605018`）给出可独立验证的原热写入：显式自有wrapper/Dictionary/两数组，非空已有数组、空bucket、容量足够、comparer=null、无free槽。原hash内联 `(group*0x18d xor level)&7fffffff`；在该条件下不触任何allocator、Default、Equals或增长，原Store→TryInsert可自行写entry的完整8 B pair与原U32行ID，以及next/bucket/count/version。合同含完整GP/XMM/flags、读写域、三个确定case、唯一允许E8、RET退出、1000指令/例及独立20 B entry/12 B bucket预期；本分析尚未执行该合同。

移除全部A第二返回和B组合loader的拒绝产物后，该热case仍仅用本两原body、固定静态输入和自有buffers成立。其验收只可批准条件原热写入；人工按221/1和22101调用不能代同原Proud行构造、原producer填map或lookup命中。lookup的18192.m138527/class50463原AOT唯一指向154d400d0，可另做有界单窗，实际interface dispatch仍独立保留。

任务目前实际增加的是静态来源和可实施小合同。`actualProudRowConstructed`、`actualProudMapProducerExecuted`、`actualPairLookupHit`、`promoteThresholdObserved`、`javaFormulaProven`、resourceReady/完整Avatar/D02均false。客户端验收增量0；未改运行代码/Proto/配置/资源，无GS构建、部署或重启。本包不做Git提交，Project/Harness及本地提交由主线统一处理。
