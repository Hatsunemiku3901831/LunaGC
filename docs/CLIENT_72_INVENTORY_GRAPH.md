# CN7.2库存完整raw对象图

后续状态：F-03d2b1已取得[库存用途/Material六字段合同](CLIENT_72_INVENTORY_CONTRACT.md)；F-03d2c1新增[独立raw Java适配器](CLIENT_72_INVENTORY_WIRE_ADAPTER.md)，15类47字段由实际Gradle/protoc及32组原生对象向量核对。旧生产Store/Item/包号/发送链仍未接管，原raw摘要与本页研究边界不变。

2026-11-06，F-03d2a3。**PlayerStore用途候选的15类型47个wire字段raw闭包已完成离线验证；身份/业务字段名仍unresolved，生产与客户端初始化未验收。** 本卡接续[d2a1依赖/外层](CLIENT_72_INITIALIZATION_CANDIDATES.md)和[d2a2 primitive容器](CLIENT_72_INVENTORY_CONTAINERS.md)，不是将候选Asta名批量晋级。

正式工具[trace_client72_inventory_graph.py](../tools/trace_client72_inventory_graph.py)每次校验并重读固定三输入、重新提取metadata及primitive map AOT；固定依赖outer摘要SHA-256 `2c14b416e5e18d868bde7d645939f6ea9c2a936761967e555e92538c2d11e20f`和container摘要`352f094fec870074abb70207af317870525b025c9b68805500c6410347feb72d`。目标Cmd20892→68537来源保留在outer证据，不借用旧22160。Asta仅作初始候选，不提供本卡任何字段命名或raw结果oracle。

## 递归闭包与剩余12个wire字段

前两卡已恢复root/Item外层/两个单uint32叶子13字段和六个primitive容器类型22字段；本卡补齐以下五类12字段，共15类47字段。15类的全部实例存储为42槽；三组oneof把共享Object/判别槽展开为11个message分支，因此不能拿42当wire字段总数。摘要保留所有raw字段，后缀`_1`等仅区分共享Object的tag分支，不是原始clientField或业务名。

| 类型 | raw字段与tag/wire/偏移 |
| --- | --- |
| 63442/KKOPHGDBCEO | GNHFNECPAHD tag1/wire0/+20 uint32；PNGOHOKGKHP tag2/wire2/+18→28471 |
| 63899/MKFCDFEEAAL | GNHFNECPAHD tag1/wire0/+20 uint32；PNGOHOKGKHP tag2/wire2/+18→61085 |
| 28471/JDDDHIGLMGJ | IJPBJCNCDEC tag1/wire0/+24 bool；Object PNGOHOKGKHP/+18，判别KGFJLNELOGO/+20：tag2→28473、tag3→28474、tag4→28475，均wire2 |
| 44169/BNDIKNLDLOJ | FAPABAJBMAK tag3/wire0/+20 bool；Object FLPGMLOFAKP/+18，判别EBJAMFMOAJO/+24：tag1→74063、tag2→64947，均wire2 |
| 28474/KNNPHAOJMAH | BHOHKJOCNOL tag1/wire0/+18 uint32 |

Item65333的六个message分支tag5/6/7/8/9/10及上述两组内部oneof均实际读取非空内容。root的一组独立输入同时携带六个非空Item，每项含ID=42和GUID=9007199254740993；动态经过三个层次的oneof、五类primitive map及五个packed/unpacked list。输出不再有`untracedType`或`empty-envelope-only`占位。

## 同分支CopyFrom与切换语义

目标reader在同oneof分支再次出现时先分配新子消息、执行相应原生CopyFrom/clone，再读取第二段wire；省略保留、显式0覆盖由实际路径分开验证。不同分支切换后返回旧分支时，旧分支数据不会恢复。

在Item tag10的两次非空消息中，第一段list=[1,128]/map={7:11}，第二段list=[128]/map={7:0}，实际结果为list=[1,128,128]和map={7:0}。执行路径包括原生map clear `0x15893c8b0`、map copy `0x15893c340`、map add `0x15893d3a0`、list copy `0x158db7cd0`，以及既有primitive adapter/setter；没有由模型直接复制最终业务结果。

新增`InventoryCopyRuntime72`只给上述原生容器函数提供托管enumerable/MoveNext/Current/Dispose、linked clear回调及数组清零/拷贝fixture。map clear的字典字段与array clear调用、copy的原生枚举循环/add、list copy的长度和追加逻辑都执行目标代码；底层managed dictionary lookup/insertion仍是容量64的明确模型。具体消息CopyFrom/clone、nested字段分配和oneof判别切换执行原生代码。此上下文不是实机泛型ABI或完整GC恢复。

32组独立组合图含六个Item分支、三个内部TPS分支、两个Equip分支、root六元素列表、三层同分支map覆盖/list append、独立bool/计数显式零、两标量叶子零、切换全部六分支后返回空分支、uint32与uint64上界。1920次有界tag1..32/wire0/1/2/5扫描对照完整15类47字段，另分别确认五个list同时接受packed/unpacked；不把扫描范围当protobuf全tag域假设。

六个坏输入严格分记：recursion_limit=0到`0x1509cb410`、负长度到`0x1509cafa0`、嵌套长度超过输入到`0x1509cb360`，均实际走到原生错误分支入口并由helper停止fixture终止，没有执行真实throw helper或称客户端异常处理完成。非法tag、packed截断和GUID varint溢出由显式WireInput模型拒绝。

## 实际验证与复现

两轮各1920扫描/32原生组合图/6拒绝的完整报告与摘要逐字节一致；摘要SHA-256 `ca45b0d9b038cd46fc35943924ca77ae46c8fbae82c06e131245e03f43d95b18`。8项新增与包含它们的54项关联研究回归通过。本机证明为`local/migration72/evidence/inventory-graph-determinism.json`，正式摘要为[inventory-graph-observations.json](../protocol/cn-7.2/inventory-graph-observations.json)。

```powershell
python tools/trace_client72_inventory_graph.py --client $env:GAME_PATH --output local/migration72/evidence/inventory-graph-native.json --observations local/migration72/evidence/inventory-graph-summary.json
python -m unittest tools.test_client72_inventory_graph -v
```

首组合图已通过全部15类/47字段扫描及六分支非空图，随后同分支回归暴露缺失的map clear/copy native路径；补齐明确managed接口fixture后才通过native copy/clone和重复wire。该失败保留本机分析记录，未删同分支样本或把非空改为空。公共graph/map runtime、生产Proto/包号/发送合同、服务/数据库/客户端未修改或启动，无Gradle/部署/实机通过声明。

下一可执行项是F-03d2业务身份/字段目的地取证：将已固定raw字段沿`0x153caf190/0x14e3dc3d0`等typed消费链与独立具名来源闭合，再单独评估生产最小发送字段。完整初始化还缺AvatarData等；不能以本raw闭包宣布F-03/P0/D-02完成。

## 后续生成/生产schema可复用规格（未实施）

生成器应以本摘要的完整15类型/47字段及两个固定前置摘要为唯一raw输入，重新校验三客户端哈希与上述摘要SHA；`--check`发现任何输入/类型/字段/tag/wire/oneof/nested依赖漂移即拒绝。不得按Asta名称、旧Proto形状或编译错误自动补字段。初期可输出研究namespace的独立15类Proto，正式接入另卡审查；本卡没有新增生成器或修改生产Proto。

- 标量依证据使用uint32、uint64、bool；root的CLFJNKKFONI保留32位raw值，目标enum55345不等于已核实本包业务`store_type`。五类map全部`map<uint32,uint32>`，五个uint32 list用repeated且支持packed/unpacked读取；root为repeated Item候选消息。
- 三组oneof必须真实声明：Item tag5..10、type28471 tag2/3/4、type44169 tag1/2。每个branch引用摘要中的具体目标类型；不能把共享Object直接生成为bytes、把判别槽当额外wire字段，或丢弃“未在当前builder使用”的分支。
- 非oneof标量在Java适配Proto中使用显式presence（例如proto3 optional），使同分支nested merge能够保留第二消息的显式0/false；这只是Java合并合同适配，不声称恢复了客户端原Proto语法。oneof空message presence、map零entry与list零元素必须保留。
- 原有PlayerStore/Item/Equip/Weapon等业务Proto及GameItem.toProto不能按tag同形直接接管。接入前分别闭合消息身份、ID/GUID/数量/仓库/装备等字段业务目的地与值编码；未具名字段保留raw名且不作为生产发送依据。静态20892绑定不单独授权给`PacketOpcodes.PlayerStoreNotify`设置正式正号。
- 新生成器应只写Proto源并通过既有Gradle/protoc生成Java，不能手改generated Java。未来运行合同回归至少覆盖本卡root六非空Item、>2^53/u64上界、三组同分支显式0/false、map覆盖/list append及不同分支切换；原生fixture与独立wire继续作为外部oracle，不做自编自解唯一证明。

15类raw图可作为独立schema生成的已固定输入；正式包号、用途别名、业务builder适配和生产发送最小合同仍是另一层验收。涉及生产/Proto改动时才执行对应构建、portable部署及运行产物/端口核对，本研究不提前执行这些步骤。
