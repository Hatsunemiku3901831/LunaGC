# F-03d3s2 / World 必要父资源单块（2026-11-11C）

任务 `F-03d3s2/WORLD-REQUIRED-PARENT-ONE-BLOCK-PAYLOAD`，父项 D02-R1。**首版执行因记录路径的 AttributeError 停止，其原 prefix 结果仍未知；主线封存、审核唯一记录修复并明确授权后，fresh2/fresh3 两次完整采集及纯原件审计通过，已取得3266B父payload。** 两次成功与首版未知执行分别记账。这不是完整MPLevel解析，也不是自然reader或Scene验收。

唯一request `local/migration72/d02-resume-20261111c/world-required-config-type-suffix/one-parent-block-payload-request.private.json` 为14181B / SHA `1064ea668af5daba75758e91fa5ca0e07d66994c20a62f0d949f725c73fd9a28`。实现入口核78项去重后的固定来源pin（含19项既有依赖、旧pre继承pin和已验metadata prefix）、profile及实际五stock；核完整key `336d0a54c32b516f`、class1210索引原32B、唯一 entry `54c32b51`/fileID0/pathID14619/fileSize3266，与class1208目录原object记录分开核对。物理file完整139134667B的SHA `cb1a68de51f3baeba7afc771284d46410a7b6cb993aa3d085a0c8a7813e14768`及MD5 `edc854321f0e9d8c64fd6e80426fe657`通过；只保留规定的真实prefix `[0,25777971)`，选择唯一block761 `[25741681,25777971)` 36290B。

在请求的一次前缀调用**之前**保存了 `fresh1/pre-execute.private.json`：其中前缀和LZ4计数为0，记录29段原allowlist、table、源范围和所有pin。当前PE读取如实计账：五stock的PE完整hash读取450927000B，随后探针输入又读取同一完整PE 450927000B；不是0 PE读取。没有申请新callee窗口或变更旧helper。

## 原始停止点和已留证据

命令：`python -B -m tools.migration72.scene_world_required_parent_payload --output fresh1`。发生在该工具 `collect` 的 `block-761-prefix` 阶段，结果计数 `nativePrefixCount=1`、`independentLZ4Count=0`。捕获的原异常为：

```text
AttributeError: 'Offline72' object has no attribute 'runs'. Did you mean: 'run'?
```

原因是我在 `finally` 中错误读取 `engine.runs`，实际已有probe的运行记录位于 `probe.runs`。该异常发生在前缀调用后的记录路径，并可能掩盖 `transform` 的原始异常；此次 `observedExecution`、原AL/stop/guards和call/write ledger未保存，因而**无法判定该次prefix正常完成与否**，不声称任何保护已通过。首版失败后立即停止；未自动重跑或更换工具、入口、seed和codec。之后主线封存并审核唯一记录修复，明确授权新目录重跑；下方分别记账。首版依然不能追认为成功。

私有根 `local/migration72/d02-resume-20261111c/world-required-parent-payload/`：

| 已保存证据 | 大小 / SHA-256 |
| --- | --- |
| `fresh1/pre-execute.private.json` | 20141 / `74365265821c8b0948d337ead10516613447585fc107d87e70183d20a1d088ef` |
| `fresh1/failure.private.json` | 20255 / `079ba506ff1adc244d92cfda1db9e2131a6625f4a290687a040f80a020953772` |
| `fresh1/physical-prefix.bin` | 25777971 / `f7cc42485d517870c1ec81151245680a43f67796e31c876247da1845fac19375` |
| `fresh1/block-761.raw.bin` | 36290 / `f5219ffbcada36b7ce6cd4d52ba6054f1c023da87f33d144419a57a35ebdb3c7` |
| `first-failure-trace.txt` | 原控制台trace的私有记录 |

首版 `fresh1` 没有 transformed、LZ4输出、object/payload/tail或成功审计；原始物理prefix和数据留忽略目录。下方的新目录结果独立记录。

## 实现及实际测试

首版工具 SHA `e883457e71931791480603c3ffce0030205fa5454a8b767ca25d26b16fa16b3f`；首版测试 SHA `249041a4cd4316465c9b9d5b8004e05608cfbe9f6b9f46ccea7906f2c71394a7`，均已封存。执行 `python -B -m unittest -v tools.migration72.test_scene_world_required_parent_payload`：7项通过，覆盖source及物理SHA/MD5漂移、索引原记录及完整64位pathID、class与目标来源、单块范围、独立LZ4损坏/截断/精确长度、真实count边界与人工非零tail、guard/model/stop/步数错误，以及source失败时prefix计数0。这些单元测试没有覆盖此次 finally 记录路径，不能作为前缀调用成功证据。

此前已核旧probe 29段allowlist、0 models、81B入口和原表；工具设计的100000指令停止门禁与额外观察hook在本次失败前被安装，但由于最终记录路径异常，实际步数和完整监控结果未知。测试夹具中的tail及LZ4内容不代表目标真实数据。

本次只修改指定新工具、测试、专题及私有忽略目录；未操作Git/index、构建、Java、DB、端口、live/client、native reader/factory或World slot。未知的自然反射cache、A第二allocator/返回及B组合loader继续冻结，Scene/reader/MPLevel/D02均未晋级。

## 首版封存和最小记录修复

主线确认这是普通实现记录错误，不是外部或安全阻塞，并授权仅修记录对象归属，不授权再次执行真实prefix。原 `fresh1` 全部文件、原trace、首版工具/测试/专题及实际命令已复制封存至 `failure-v1-freeze/`；`manifest.private.json` SHA `02a601168c8efe01050201bd7d78d061298f14d106c30cb2b0b2172790d57e2c`。原 `fresh1` 仍保留，任何证据未覆盖。

实际源码 `tools/client72_resource_index_native.py` 的 `NativeIndexProbe.__init__` 将 `runs` 建在 probe，`Offline72` 没有该属性。候选工具唯一生产代码差异为记录路径的 `engine.runs` → `probe.runs`；未改 entry、seed、codec、allowlist、guard、指令预算，也未吞异常。修后工具 SHA `c8b162ce94173b5cc03ec089a2a5c155f58503d2295e2b2b3303c02da8804ffb`；新测试 SHA `398d2cecd1d7ecdda2dcc08adbd459653336f6c843fb11ac6460449dfc8f3b00`；相对冻结版的 `fix-v2.diff` SHA `2e8f7e762c9874f5fbb93db30c59de53159d20cb263e934ac3776a80a224515e`。

修后执行 `python -B -m unittest -v tools.migration72.test_scene_world_required_parent_payload`，8项通过，日志 `tests-v2.log` SHA `19f9efed7edef7ade68a221e18eb5a69dc932affbd06af7180513e7ffc918a02`。新增纯mock模拟没有 `runs` 的engine：正常probe及抛出原始prefix异常均能记录 `probe.runs`、清理hook且不掩盖原异常；没有目标CPU、当前PE、真实prefix调用。日志末尾的3266B/`12fe`为人工fixture，绝非目标payload/tail。原7项继续通过。

主线随后审核通过上述最小修复，并明确授权新目录依原scope重新执行；首版AL/stop/guards仍未知，且不追认为成功。

## 授权后双 fresh 与纯原件审计

主线明确授权候选v2先执行 `fresh2`，完整成功后才执行 `fresh3`。两次各自重新核源/五stock/物理全长SHA与MD5、唯一块范围、已固定29段allowlist和0 models，保存 pre-execute 的 prefixCount/LZ4Count=0；每次只对block761执行一次原81B prefix，再做一次独立LZ4。两次的十件产物逐byte一致；纯audit `pureartifactaudit.private.json` SHA `3e434dbb759638070dbd55fe2ee4c2e0594bb3f92c038a8d5b0e975f028eab15`，passed/twoFreshAllFilesByteIdentical=true。它重新绑定固定源、索引/目录/header、保存的真实prefix与raw block，独立重解LZ4并核对象/count/tail；审计本身0当前PE/CPU/native调用，明确 `fullPhysicalIdentityRehashedInAudit=false`：完整物理file hash在两次采集中重核，纯审计核的是保存的prefix。

每次真实prefix：AL=1、stop=`0x620000000`，28761步≤100000，0 modeledCalls/memoryModels、0 failure/error；原 guards/tail完整，protectedWriteEvents=0，header storage 13725B保持；prefix写212次、前128B中128B变化，之后全部raw tail未变。每次proof包含483个目标序列项、482条实际call edge及2661条实际写事件（含原监控账），完整顺序与保护结果留私有 `block-761.proof.private.json`，不把观察target当作新增callee读取。

| 两份各自文件 | 字节 / SHA-256 |
| --- | --- |
| pre-execute.private.json | 20141 / `5490ba15b2c2894e887c48943143cd44b96d4938acd640c33c816c495cfb5c27` |
| physical-prefix.bin | 25777971 / `f7cc42485d517870c1ec81151245680a43f67796e31c876247da1845fac19375` |
| block-761.raw.bin | 36290 / `f5219ffbcada36b7ce6cd4d52ba6054f1c023da87f33d144419a57a35ebdb3c7` |
| block-761.transformed.bin | 36290 / `dba32d5997898915a5be57586785166f14e0ed7ca8eae975fb060a49fc0f678c` |
| block-761.output.bin | 131072 / `add50acd30c8273209aa9c774e85bce0a8ec94ec640acafcfe4272b67aa4b992` |
| block-761.proof.private.json | 291393 / `f5bdbd2b1937d77145e61883bafe5edde47d5b2abe0d356f660b17121b589c31` |
| parent-object.bin | 3272 / `4e0646474cb4f626fb8d7544bf7786038588e10062be3887913ecae064701d55` |
| parent-payload.bin | 3266 / `96dad87193eba63a502607247c1280f7a503b462872df14cc23cae771ef42bdc` |
| parent-tail.bin | 2 / `96a296d224f285c67bee93c30f8a309157f0daa35dc5b87e410b78630a09cfc7` |
| result.private.json | 902460 / `b5e36bfb92e8171838f0932b2b312374d4a126525d77a308b9e60414e07357fa` |

对象原LE32为 `c20c0000`，实际count=3266，与索引fileSize一致；其后3266B是父byte[]，末2B真实tail为 `0000`，单独保存，不把它吞入payload或按padding假设。没有解析子对象、MPLevel、21项或JSON，也没有native reader/factory、World slot、live、部署或业务运行。三次总体执行账为首版1次prefix结果未知、v2成功fresh各1次prefix/1次LZ4；纯audit的独立LZ4重算另计，不把首版归入成功。

实际命令的SHA-256按UTF-8命令文本、不含换行：

| 命令 | SHA-256 |
| --- | --- |
| `python -B -m tools.migration72.scene_world_required_parent_payload --output fresh1` | `d1abb0218fe013cb9a896da4e9ec015e14bdd82e8e8a867c9370bc45ffd73fa8` |
| `python -B -m tools.migration72.scene_world_required_parent_payload --output fresh2` | `98d72e46b0b43650060f80276216966b3884cc4fb46d61ba7997caeed3308353` |
| `python -B -m tools.migration72.scene_world_required_parent_payload --output fresh3` | `fe040535fafb227cfa2b8c33cc40e426d74fe7f99c679e8e18f64f8c35b8780d` |
| `python -B -m tools.migration72.scene_world_required_parent_payload --audit fresh2 fresh3 --output pureartifactaudit.private.json` | `523ae054b63b90d69eb7bad1b8b378d49dca982199597ed8799f31a03dcb276e` |
| `python -B -m unittest -v tools.migration72.test_scene_world_required_parent_payload` | `3f8d1921b22de860656faf6163d1c0310b0af2e75b861daa3a0fc47e9b882b71` |

主线唯一集成执行者第三次从checkout执行（先确认输出名不存在）：

```powershell
python -B -m tools.migration72.scene_world_required_parent_payload --output main-third-v2
python -B -m tools.migration72.scene_world_required_parent_payload --audit fresh2 main-third-v2 --output main-third-v2-audit.private.json
```

期望3266B payload及上述SHA、tail `0000`、28761步/AL1/正常stop/guards；audit `passed=true`、`twoFreshAllFilesByteIdentical=true`、`currentPEReads=0`、`currentNativeCalls=0`、十件hash同表。任何原edge/guard/stop/model/长度或来源失败保留证据立即停，不自动更改参数或重试。下一项只在独立验收后交准确consumer语义，不能从父payload推断子字段或自然ready。
