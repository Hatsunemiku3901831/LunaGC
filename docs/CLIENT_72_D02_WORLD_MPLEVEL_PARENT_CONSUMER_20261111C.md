# World MPLevel 父正文：准确 consumer、首项投影与条件 factory 后继

任务 `F-03d3s2/WORLD-MPLEVEL-PARENT-CONSUMER`，父项 D02-R1/R3。**本包没有运行原parser、旧runtime、分配或对象工厂。** 只消费已存原件、typed记录和文件原字节；不以3266B父payload称已解MPLevel，不用外部JSON补字段。

主线已确认 `acceptance21/world-payload-independent1.private.json`：新父资源独立第三通过，8 tests/十件三方同，独立LZ4、483targets/482edges/2661writes、AL1/28761步/guards/0model及class1210→class1208/count/tail均核真。这里只复用这个**物理载荷来源**验收，不晋级原parser、自然reader、map或子对象。首版fresh1的prefix结果未知仍封存，本包不消化为成功。

## 固定输入与不重复的旧研究

新原件根为 `local/migration72/d02-resume-20261111c/world-required-parent-payload/{fresh2,fresh3}/`。本轮核十件双fresh相同；parent-payload3266B/SHA `96dad87193eba63a502607247c1280f7a503b462872df14cc23cae771ef42bdc`，parent-object3272B/SHA `4e0646474cb4f626fb8d7544bf7786038588e10062be3887913ecae064701d55`，原LE32=`c20c0000`，独立tail=`0000`/2B。没有再次提取block761或执行prefix/LZ4。

新[载荷专题](CLIENT_72_D02_WORLD_REQUIRED_PARENT_PAYLOAD_20261111C.md) SHA `dc8f50243b0af243c22bf94b40c4f83d82997548be5b5288e6489e1a8aa84d5e`，工具/测试SHA分别为 `c8b162ce94173b5cc03ec089a2a5c155f58503d2295e2b2b3303c02da8804ffb` / `398d2cecd1d7ecdda2dcc08adbd459653336f6c843fb11ac6460449dfc8f3b00`；pureaudit SHA `3e434dbb759638070dbd55fe2ee4c2e0594bb3f92c038a8d5b0e975f028eab15`。父路径、typed后缀、key `336d0a54c32b516f`与唯一物理对象的来源直接复用[上包](CLIENT_72_D02_WORLD_REQUIRED_CONFIG_TYPE_SUFFIX_20261111C.md)，不重提707组、World21或type-map/nativehash研究。

两个关键 consumer 原窗早已验收，现直接复用[旧语义](CLIENT_72_GADGET_ABILITY_SEMANTICS.md)，不重采：

| consumer / 正式原件目录 | 完整原窗与SHA |
| --- | --- |
| 25270.m196497，`d02-parallel-20261109/r3-gadget-ability-semantics/main-parser-window1/` | `[14b1321a0,14b1326df)`，1343B/323条，`cd472e403f50e6f777b17333f38348330ef2fe6d37ebf140c0d4c23062d774b4`；manifest `d3685968bd816e86d3c53234e8d13d9edd42c0a03a87f74eb0619d6876e084e0` |
| 25270.m196498，`d02-parallel-20261109/native-successor-windows-main1/config-value196498/` | `[14b135cc0,14b135d67)`，167B/49条，`9e635fb07bdbe93ddb1bb4b33c573d5d6eaa58d238dfbb2c2df9dc1f11379575`；manifest `1678832dff0d4934ae11ad5bf4fd9c646a1925b724183f5fb60f526c69a5af6d` |

目录均相对 `local/migration72/`。本轮只核保存bin、manifest hex与372条原指令连续相同及关键E8目标，没有重跑旧语义/CPU或测试。

## parent、MPLevel与typed缓存的准确关系

已验54B真实template90000004行（slice SHA `3288b3a44679bcb4ff775fc52fcb5274e20818c9b58c3ce9bc5d33892198283a`）field199649/+58为 `MPLevel`。factory `15154bfd7..bffd`以它调用 `14aa97730/14aa9c630`；查询返回随后写entity+2b0。父+20的9999、+50的Default_Item不是这里的资源路径或子对象内容。

已存完整raw的 `14aa9c630` 正常路先查询 `14aa9b8a0`，未得owner时调用 `14aa97730`再查，非空时尾跳 `1524e1f80(owner,key)`。固定metadata表明17591.field83188/+30是 instance11687 `Dictionary<string,25270>`；25270继承37853，属于entity.field138419/+2b0的配置域。`1524e1f80`/`1524e2050`旧listing分别有查map、缺map后load及 `2050→2410` 边，但本包没有它们新的raw或自然执行。

旧准确caller `14aa9b160..14aa9b3aa`（586B，记录SHA `7464bfb3466a30ff539c7e4c4af5b3763e0700ba0e01507aa0ad2443c1ed8566`）在 `14aa9b287` 调m196497，后者签名为 `(reader42005, byref Dictionary<string,25270>)`。这提供同配置域的准确文件parser和值分派来源；**不能只凭相同dictionary声明，说17591自然load已使用该parser、当前reader从offset0开始或完整父文件已消费。** 本包不重开loader树。

## 删除分配产物后，首项能独立核到哪里

以下是显式以**文件origin0**为输入的primitive投影，不是观察到的reader状态，也不生成Dictionary/string对象。只使用旧parser直接表达的宽度/算术与实物原字节：

| 原始范围 / 原PC | 本文件结果 | 结论层次 |
| --- | --- | --- |
| `[0,4)`=`171e1ff2`；`14b13220c` XOR `f21f1e02` | U32 count=21 | 独立文件算术；与已验World21计数相符，不代表map已分配或21个value已解 |
| `[4,6)`=`c86f`；`2c1..2d5` | `((u16+d388) mod65536) XOR4357`=7 | 原位宽保留；原caller在`2ca`只把cursor推进到6 |
| `[6,13)`=`c58350472126e2`；原内联块XOR `5c8e4357221cd388` 的低7B | decoded bytes=`4d504c6576656c`，ASCII视图 `MPLevel` | 只是7个解码字节；不宣称未审helper已经返回该托管string |
| offset13原byte=`cc`；m196498 `d02/d04` | `(cc+35) mod256`=1，再零扩展为U32 | **仅按旧host UTF8/cursor边界P+=7时的条件投影**，不是原reader已到13或实际factory已经收到1 |

两个必须保留的停止面：

1. **准确A边：**1343B parser的 `14b132224 e8971440f5 → 1405336c0` 位于N>0 map分配；本文件投影N=21。分配返回、initializer、out-map与自然dictionary产物依赖立即冻结。没有执行旧runtime、绕过分配、假设map返回或写一个成功outSlot。删除这些产物后，上表的文件primitive仍可独立读取。
2. **key helper边界：**`14b1324b7..4bf`把同reader、长度7和临时解码buffer交 `14f0de820`，此前原caller的cursor只到6。旧 `client72_resource_deep.py::convert_string` 明确是UTF8/string/cursor模型，它将P加len；不能据模型证明原helper的实际副作用。因此早先把offset13直接叫“实际selector”的表述在本包收窄为上表条件投影。没有运行模型，也不新取UTF8 helper窗。

所以现在不能交完整parser或必要配置字段投影：完整framing/EOF、原key string、实际cursor、25270对象及其字段均未知。没有读取余下20个value，没有按外部JSON补combat/abilities、默认值或别名。

## 实际值分派与唯一有价值的 source 后继

已验167B m196498在原reader当前位置取1B，先将cursor加1写回，再对AL加`35`作8位回绕，零扩展ECX；`14b135d07 e894277703`准确CALL `14e8a84a0`。非空结果还要通过25270层级兼容性检查，之后才CALL **实际返回对象klass+100**，并返回原对象。

`25270.m196496/14b132c20`的slot6只是metadata声明，不是已绑定的实际+100值，不能先取它。下一窗只补 **62017.m514570 `PCKPBPDHLNB(uint32)`/`14e8a84a0`**，研究显式条件输入1的静态class/分派关系。返回声明类型尚未恢复；此窗不执行工厂，不以host成功对象填洞。若遇准确A分配，只记录该支的请求类型/原边，停止其构造返回及actual klass/slot依赖。

本轮按准确entry/start和工件名定向检查既存local JSON/原件，只找到四份旧analysis/request记录，没有找到该factory完整带raw body。不是重发旧无必要性研究：现在已有同源父正文与首必要entry字节，输入条件/收益更窄；不扩其余20或通用factory树，也不将可本机取source称外部blocked。

独立请求 `local/migration72/d02-resume-20261111c/world-mplevel-parent-consumer/conditional-factory-one-window-request.private.json`：**8942B / SHA `39f9b19b5fe5524d490389ee9e8f63665328cd8ba069aea98f7cb603bb27cc94`**。

| 项 | 固定机械合同 |
| --- | --- |
| 参数与身份 | owner62017/m514570，flags93/static/slotFFFF；parameter410581/typeReference124为非byref U32。输入1明确是条件文件投影，非真实reader frame |
| caller核对 | 复用完整167B原件；新PE只核 `[14b135cf8,14b135d0c)` 20B `0fb6440820ffc1894e1c04350fb6c8e894277703`，E8 signed rel32须回同target，保留完整64位地址 |
| ordinary AOT | cell1422f9950原QWORD须为1428a54d0；`P+514570*8=142c92520`须指14e8a84a0，共16B，不编造已采pointer |
| pdata/body | 固定exception目录14344080B/SHA `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d`；exact/containing各唯一且同原行、Begin=entry，先存12B/pre-body。**end未知**，只取真实完整正extent≤8192B/2048指令、可执行/file-backed；缺/歧义/超限0body返回，不猜邻窗/前缀 |
| 采集及验收 | 只1body，原bytes/asm/hash/RIP/CALL/branch/RET/fallthrough；保留computed branch/内联data歧义，不假logical complete；双fresh/pureartifact audit/主线第三。0子callee/global/xdata/live/CPU/model；来源、E8/AOT/范围/短读失败保存后停 |
| 直接收益 | 条件case1若有独立静态映射，可缩窄请求class/表/constructor/真实parser来源；A或未知返回边仍停，不能升级为MPLevel对象/字段。后续只按这个实际首缺再判，不自动扩slot6/helper |

## 产物与状态

私有根 `local/migration72/d02-resume-20261111c/world-mplevel-parent-consumer/`：

| 文件 | 字节 / SHA-256 |
| --- | --- |
| `source-and-first-entry.private.json`，16 pins/旧372条来源/文件primitive/模型边界/A停止点 | 8432 / `444cbc783ccde588a4fa4df16078e5775db4e2ae349f34c7183e7d8a14a92275` |
| `typed-parent-consumer.private.json`，固定metadata/signature禁止globals解析，17591/41430/25270与参数 | 67892 / `0b2d88e707a1c5ae22c8a7933e831a151331717472845c855dc50ccee6a313e2` |

实际只核新源pin/双fresh、旧raw连续hex/关键rel32、typed参数与首文件primitive；测试0、生产parser实现0、新PE/global/nativeCPU/live/模型/collector/构建/DB/部署/Git/index均0。候选Anime/Asta只读复用原固定commit，本包没有以其内容补值或再作对照。旧源码/专题/Project/Harness/HANDOFF/config/Proto/patch/resources保持只读；主线负责共享状态、集成及提交。本包有界完成，MPLevel实际子字段、自然map/reader/Worldslot114及Scene/resourceReady/D02均不晋级。
