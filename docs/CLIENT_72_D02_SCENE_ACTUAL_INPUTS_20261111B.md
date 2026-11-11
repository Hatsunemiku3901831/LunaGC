# F-03d3s2 / SCENE-ACTUAL-INPUTS（2026-11-11B）

## 中文小计划

| 项目 | 本批约定 |
| --- | --- |
| 目标 | 优先从本机已有同源证据恢复 `ConfigMetaConfig.gadgetPathDataPathHashes` 的实际第二 QWORD/bin、对应 payload 和 reader 起点，以实际含 `MPLevel` 的完整分组 key、原序子名称为出口；独立检查能否沿 World 实际返回对象绑定 slot114。 |
| 已有 | 复用已验 131 B parser 前缀、2675 B 置位字典、文本游标/字段/count/插入和 666 B 清位/count0 分支；复用原 GitHub README 筛选、固定 clone 与候选对照。不重复提取或审计这些原窗。 |
| 缺口 | 目前尚无已验实际 bin U64/payload/reader 状态，也没有实际 factory 返回对象→klass→slot114 的同源输入。外部同名 JSON、metadata slot 声明和 requested class 不能代供。 |
| 方法 | 先按文件名及已验索引定位本机原件，再以完整 SHA 固定确有关系的输入；只读已有 source/provenance 和实际数据，分开记录观测、推论和未知。发现完整同源输入后才拟定最小转换/验证规格；缺输入则给出已查边界和准确解除条件。 |
| 写权 | 仅本页、新独有 `tools/migration72/*scene_mplevel_actual*` / `*scene_world_return*` 及必要测试；私有输出为 `local/migration72/d02-resume-20261111b/scene/`。Project、Harness、HANDOFF、现有工具/协议/资源/配置等全只读，主线负责 Git 与共享状态。 |
| 产物 | 有来源哈希的实际输入结论或可复现缺输入清单；输入成熟时即交回 Terra 实现规格。 |
| 验收 | 新结论须有实际原输入、类型及消费关系，不能用旧静态断言或编译代验。需要新工具时，以两 fresh 确定性结果、入口拒绝测试及主线第三命令验收。 |
| 停止边界 | A-SECOND-RETURN-CONTRACT 与 B 组合 loader 保持阻塞，不读取/运行未验草稿，不续接被拒动作。无有来源实际 target 时不猜地址/alias、不读候选 slot114 body；不操作客户端、调试器、运行时、JVM、数据库、端口或部署。新安全拒绝立即保留原文并停止该项及依赖。 |

## 执行状态

本机既有输入清查进行中。已得到新的准确 ConfigMeta 泛型加载来源，交主线另行有界提取 caller；没有重提旧 parser 原窗。父项 F-03d3s2、完整 Scene、resourceReady 与 D-02 均保持未验。

## ConfigMeta 实际输入的独立静态入口

已验 ResourceIndex 第三报告（92,384 B / SHA-256 `e8a02de4a4b24c58be647e8f1cb2638b5e5a06f1b30427a8ae5a627291d54ab9`）的 `types[7]` 为 `MoleMole.Config.ConfigMetaConfig → .ScriptableObject`。它是目标类型后缀证据，不是资源路径，也不能套用此前表资源的 `.MiHoYoBinData`。

本轮核 profile 五份原文件完整大小/SHA 后，按已验 usage 和 registered argument-pointer 格式做纯数据筛选：788,696 条 usage、122,513 个去重 MethodSpec、28,951 个实际参数实例中，只有下列一条绑定 ConfigMetaConfig77121。这里不是全 IL2CPP 返回类型/泛型替换覆盖声明。

| 关系 | 实际记录 |
| --- | --- |
| usage / cell | usage758408，MethodInfo cell `145a5ee48` |
| MethodSpec | 93696 → `(methodDefinition80455, classInstance=-1, methodInstance66311)` |
| 方法 | owner11312 `MoleMole.ConfigUtil.LoadConfig<T>`；metadata flags96/static，普通 registeredAddress 为0，不能由此发明普通 AOT 地址 |
| 原方法实参 | methodInstance66311 的唯一注册参数为 type77121 `MoleMole.Config.ConfigMetaConfig` |
| 显式声明参数 | `string filePath`、`ref uint32 handle`，由固定 signature 的 parameter85597/85598 独立绑定；返回类型仍未恢复 |

这条来源可让后继从准确 cell 的 RIP 引用找真实 `filePath`，再接上述后缀及目标 index。当前没有取得该调用方 body、filePath、资源 key 或实际 PathHash.bin。目标 metadata 的 global literal group 共19,331条，按 `configmeta`、`config/meta`、`meta_config` 三个不区分大小写片段筛选为0；只排除这个已定义范围，不能称所有路径均不存在。

私有 `configmeta-generic-uses.private.json` 为1,396 B / SHA-256 `18ed077f2fefd3e898dcf581041252aeb355b4d554f32a70f453b9d97468ce8d`；literal 筛选报告为1,749 B / SHA-256 `26368b3df4f77003b09603872e7a5e7b3465bf99b872acc576e03f59d3d482e6`。它们是本轮探索原件，尚非主线已验收成果。已将完整原 pins、精确 tuple、既有扫描器复用点、指令边界/唯一 pdata、body预算和多 caller 停止条件写入同目录 `configmeta-xref-spec.zh.md`，供主线调度 Terra；本代理不自行执行该扫描或新 code 提取。

## 已验子出口：本机15个内置资产目录

本轮读取当前 GAME_PATH 下 `globalgamemanagers`、`globalgamemanagers.assets`、`resources.assets`、`level0..5` 和 `sharedassets0..5.assets`，合计 **15 文件 /122,727,968 B**。各文件先核固定完整大小/SHA，再逐项核原安装 manifest 的大小、matched 状态和 MD5。manifest 只证明本机清单一致性，不能证明 origin/revision 或运行通过。五份 profile 原件每个 fresh 也均完整核同。

新工具只支持实际命中的 Unity2017.4.30f1、Windows platform19、little-endian serialized v17、无 type tree 格式。目录类型、对象 extent、脚本索引和外部引用依实际文件顺序解析，15个 metadata 均精确 EOF；共 **42,879 个对象**。class114 含额外 script ID，不能按此前仅含142/1208/1210的 byte-object目录直接套偏移。标准目录及 MonoScript 字段次序另与 [AssetStudio SerializedFile](https://github.com/Perfare/AssetStudio/blob/master/AssetStudio/SerializedFile.cs) 和 [MonoScript](https://github.com/Perfare/AssetStudio/blob/master/AssetStudio/Classes/MonoScript.cs) 的原始实现核对；未引入或运行其代码。

| 实际来源 | 完整大小 / SHA-256 | 本次结果 |
| --- | --- | --- |
| `YuanShen_Data/globalgamemanagers.assets` | 67,634,256 B / `2f8e34dcc68ca46d665b9ca4e658839d574c1678d9cd7567a42d07df62b4fbe2` | 28,895对象；其中唯一精确目标 MonoScript 的 pathID=26977、classID115、105 B。目标三元组为 `(ConfigMetaConfig, MoleMole.Config, Assembly-CSharp.dll)`。原对象 SHA `6425b85708815abe7565930dc1379ae2d6f00bcdfeddb7787a97de645336fc8f`；原件和偏移留私有输出。它是脚本声明，不是含 PathHash 数组的 ConfigMeta 实例。 |
| `YuanShen_Data/resources.assets` | 52,894,616 B / `8849ca4ea98fd1560fcd1ab93f5bc60e97c60204df9b35f91408b567c621280b` | 13,433对象、71个实际脚本索引；4,306个class114均按实际 `m_Script` PPtr和metadata脚本表交叉核对，没有指向该目标。 |
| 其余13文件 | 2,199,096 B；各完整SHA在固定 `assets-inputs.private.json` | 551对象；124个class114完成同样核对，没有目标引用。 |

合计 **4,430 个 class114 的实际对象 PPtr** 与其 metadata 脚本索引均一致；保留完整64位 pathID，并按实际 fileID/external路径解析跨文件来源。所有15目录中目标 MonoScript声明1项、目标实例引用 **0项**。这仅关闭本轮内置目录清查；**没有扫描 AssetBundles 的目录或内容，不能据此说 bundle 没有 ConfigMeta、资源不存在或能力为空**。下一直接来源仍是前节精确 LoadConfig cell 的真实 caller/filePath。

### 实际测试及主线第三复现

新增工具 `tools/migration72/client72_scene_mplevel_actual_assets.py`（SHA-256 `b7855047e003c49d5eeaa1bda932e076b462ba973506432e698677c21f6a75d3`）和同名6项测试。人工目录覆盖跨文件及高64位/低32位碰撞、class114 script ID与目录对齐、明文名称不能代类型绑定、对象PPtr与脚本表冲突、截断/端序/metadata余量、重复对象ID及source漂移。夹具均为人工构造，不提交客户端原bytes。

```powershell
python -X utf8 -B -m unittest tools.migration72.test_client72_scene_mplevel_actual_assets -v
python -X utf8 -B tools/migration72/client72_scene_mplevel_actual_assets.py --output local/migration72/d02-resume-20261111b/scene/assets-main-third.private.json
```

每次输出使用新文件名；上例第三文件由主线生成，已经存在，不覆盖。两个 fresh 和主线独立第三报告均 **20,492 B / SHA-256 `3b3bc0c7cb26678cb672606832c229571000279add93ad6703351495bc63239d`**，逐byte相同；6测试子代理及主线均通过。固定15文件清单2,319 B / SHA `5af18d727c823f44255b482c4213f226edaad57edce100fb50c6efdbfd4f2371`；原安装manifest 2,705,780 B / SHA `cd572bacd2c9dbbb8372d22cfe0fae675c5c40844ca78bc71f17f000d253af00`。双fresh比对证明、探索中间原件均在本批 `scene/` 私有目录。

本子出口未改运行代码、Proto、资源、配置或patch，无构建/部署/重启需要。客户端新增验收0；实际 PathHash.bin、payload/reader、MPLevel分组和原序名称、World返回对象/slot114、Pool/组件与通知仍未验。旧 slot114 3读/224B的实际对象输入规格直接复用，本批没有重跑旧缺槽审计或读取 metadata候选body。新静态入口由Terra另行执行，尚待主线独立审核，不因本节已验而自动晋级。

本专题与本资产工具/测试在本子出口交回后冻结，由主线维护Project/Harness和本地提交；后续caller语义另立合同，不与本页并写。
