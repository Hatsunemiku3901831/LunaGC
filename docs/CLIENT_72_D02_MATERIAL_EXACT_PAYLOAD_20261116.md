# Material 的准确索引与资源输入

`R3-MATERIAL-EXACT-PAYLOAD-LOCATOR-20261116`和`R3-MATERIAL-SERIALIZED-ENTRY-EXTRACT-20261116`已完成各自的纯host限定验收。旧三根7666项精确stem零命中只保留为历史文件名盘点；Material资源输入现已从既有固定索引和容器定位、提取，不能再据旧零命中声称本机无法定位。

既验literal index70971/cell`145b547f8`对应`Data/_ExcelBinOutput/MaterialExcelConfigData`。复用原host reference hash及旧通用loader的`.MiHoYoBinData`后缀，九个旧native目标控制一致，得到key`16540db9943871cc`。固定31049740无命中，31049741唯一14B record `[2692670,2692684)`为tag0/bundle178；entry名`b9943871`，sidecar唯一tuple为block25539185/bundle178/0/0/0/category7，与旧固定容器图一致。无新native hash、解密或loader执行。

准确entry提取复用已验旧容器图与helper，只选择`b9943871`：fileID0/pathID1362/class1208。容器索引record `[118624,118656)`，对象 `[39328712,40888824)`，m_bytes `[39328716,40888821)`；实际1560105B，SHA-256 `088f2b43c860c2892c1f6e9a0349547dcd94b021e5d56983349f2cfd81e7ee89`，与旧图hash相同。完整容器71555980B一次读/hash；旧图2153个索引名比较和68904B原索引复扫明确计账，未重跑会哈希所有对象的`serialized_objects`。

固定结果在`debug/d02-material-exact-locator-20261116/`：`run2/summary.private.json` SHA-256 `92f9a5d1d03f455bf967f14be707cbc98ab30f20a93666cc08c8b8fe67b3017b`；`entry-scope/scope.private.json` SHA-256 `c411656f0b84ab356b6ecba6c31e27268f81f0bcf2bede51c0f717a14a6b5900`；`entry-extract/result.private.json` SHA-256 `812fe541fc17534f641510bd4644efbee7e060bd7f63c9943359a7f0334cbb0e`。实际二进制仅留`entry-extract/Material.native-m_bytes.bin`。本次one-off脚本及精确输入/IO账均留忽略目录，不增加通用工具或重复执行命令。

首pin门因集成提供的两个文件/hash角色对调而停止，未查index、算key或读payload。纠正后`local/migration72/evidence/inventory-contract.json`是1139232B完整trace，SHA-256 `626cce90ce4f67a75969aa32d2ff3c96f1dcdb8897fe065ba6bc618f1a9d57a3`；`inventory-contract-observations.json`是194136B摘要，SHA-256 `130f6387005ad3b5cf0e2dc2e7c73b930e698fa07f03a7fe8b48fefe87dc16db`。原输入未改，原failure保留在`summary.private.json`；纠错delta SHA-256 `a0a7cb573f984f6f991be989243db4b5ead92a860425eba4c6a2964389ae151b`。主线审定更正后才执行RUN2，不自动交换pins；这不是资源不存在、外部阻塞或安全拒绝。

本项仅闭合准确资源输入，尚未证明Material row framing/count/keyspan、parser入口cursor、真实分类字段值、Java加载或自然revision采用。后继先复用[分类职责与literal](CLIENT_72_D02_MATERIAL_RELIC_ROLE_BOUNDARIES_20261116.md)建立纯host同row合同；不按候选制造行，不执行native/constructor/reader/TLS/JVM/DB/客户端或部署，R3和六项仍未完成。
