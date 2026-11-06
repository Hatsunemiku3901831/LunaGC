# 国服 7.2 库存用途与最小材料合同

F-03d2b 在完整 15 类、47 个 raw wire 字段之后，闭合库存替换用途及 **6 个最小字段**。`PlayerStoreNotify`、`Item`、`Material` 是依据本客户端原生用途作出的明确推断，并非恢复到了原始英文消息名。未采用 Asta 名字、旧 `GameItem` schema 或单字段形状作为命名证据。

这是一组分段离线合同。实际通知 dispatcher、typed wrapper、清空逻辑、列表遍历、材料构造和数量运算，以及独立的原生 GUID 字典操作均执行；类别回调、完整索引 fanout、界面与实机登录尚未验收。`runtimeStatus=not-tested`、`targetProtocolReady=false`，不因该合同单独晋级 D-02。

## 输入与复现

三个目标文件仍固定为：

| 相对 `GAME_PATH` 路径 | SHA-256 |
| --- | --- |
| `YuanShen.exe` | `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| `YuanShen_Data/Managed/Metadata/global-metadata.dat` | `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a` |
| `YuanShen_Data/Managed/Metadata/startup-metadata.dat` | `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105` |

同时拒绝三个既有摘要发生漂移：

| raw 依据 | SHA-256 |
| --- | --- |
| `player-store-outer-observations.json` | `2c14b416e5e18d868bde7d645939f6ea9c2a936761967e555e92538c2d11e20f` |
| `inventory-container-observations.json` | `352f094fec870074abb70207af317870525b025c9b68805500c6410347feb72d` |
| `inventory-graph-observations.json` | `ca45b0d9b038cd46fc35943924ca77ae46c8fbae82c06e131245e03f43d95b18` |

范围由 [完整 raw 图](CLIENT_72_INVENTORY_GRAPH.md)、[容器原生验证](CLIENT_72_INVENTORY_CONTAINERS.md)、[初始化候选](CLIENT_72_INITIALIZATION_CANDIDATES.md) 接续。工具只读已哈希绑定的文件；不启动客户端、调试器、数据库或服务。

```powershell
python tools/client72_inventory_contract.py --client $env:GAME_PATH `
  --output local/migration72/evidence/inventory-contract.json `
  --observations local/migration72/evidence/inventory-contract-observations.json
python -m unittest tools.test_client72_inventory_contract
```

正式摘要为 `protocol/cn-7.2/inventory-contract-observations.json`。完整地址计数和路径轨迹保留在忽略目录。每个原生片段记录 entry、stop、模型边界、指令数及路径 SHA；摘要另固定所用工具源文件的 SHA-256。重复运行换用另两个输出名，按字节比较两个完整轨迹和两个摘要。

## 三层结论及接管规格

1. **CmdID→类型**：`20892 → 68537 CAIHCNHAMHB`。继承已有唯一静态身份，并执行 `153CBB460 → 153CAF190`；typed 参数、slot 22 命令 getter 与当前 metadata 再次校验。
2. **类型→用途**：消费者按 `StorePack/StoreDepot` 选择仓库实例，经 slot 25 清掉既有库存索引，随后逐项处理列表。因此可稳定使用用途别名 `PlayerStoreNotify`，含义为仓库库存替换/初始化通知；不能据此声称仅用于首次登录。`Item` 是原生物品模型输入，`Material` 由具名材料资源加载/查询来源及实际构造链独立支撑。
3. **字段→wire**：只批准下表。实际 tag/类型来自此前 raw 原生 reader，语义来自本卡消费者；两个来源分别绑定。

| 类型 | tag / 原字段 | wire / 最小别名 | 独立语义来源 |
| --- | --- | --- | --- |
| 68537 `CAIHCNHAMHB` | 2 `MBEEKCPJBEL` | repeated message 65333，`item_list` | `14E43BE70` 在清空后逐项取得实际 reader 生成的列表元素，调用 `14E43A630`，初始化标志为 1 |
| 68537 | 14 `CLFJNKKFONI` | varint / enum 55345，`store_type` | typed wrapper `153CAF190` 读取 +0x24；目标具名 enum `StoreNone=0, StorePack=1, StoreDepot=2` |
| 65333 `NDGHFPMJFIM` | 1 `PJAGIOHBKLD` | uint32，`item_id` | +0x28 传入资源查询 `148446B80`；材料构造再次传入 `14A1D8440`，作为材料资源字典的 uint32 key；模型 +0x10 保存相同值 |
| 65333 | 2 `FBLKPHOCBIK` | uint64，`guid` | +0x20 → 模型 +0x18 → 仓库 +0x68 的 `Dictionary<uint64,24040>`；原生哈希、64 位相等比较、插入、覆盖、移除及查回。`guid` 是物品实例标识的用途别名，不是靠名字相似批准 |
| 65333 | 5 `FLPGMLOFAKP` oneof 分支 | message 63442，`material` | 原生 factory 用 MethodSpec 99291 选择模型 75768，模型消费者调用材料资源查询；oneof 判别值 5，与资源类别枚举值 2 分开 |
| 63442 `KKOPHGDBCEO` | 1 `GNHFNECPAHD` | uint32，`count` | 原生转换 +0x20 → 模型 +0x20；实际应用计算新旧数量差，并按最终数量是否为零选择插入/移除 |

最小子结构只需 `Material { uint32 count = 1; }`，`Item` 仅声明共享 `item_id=1`、`guid=2` 和 oneof `material=5`。这是可接管的有限映射规格，尚未修改生产 Proto/Java，也不批准发送任何未知分支。

`68537.tag8 PAKLDABINMP` 未知，不能命名 `weight_limit`。`63442.tag2 PNGOHOKGKHP → 28471` 只保留 raw 嵌套合同，不赋予 TPS 或其他业务意义。`Item.tag6/7/8/9/10` 的业务身份与字段均不在本卡批准范围；尤其 **tag 7 和 tag 9 的单 uint32 叶子并不是本卡材料分支**。

枚举只消费 `55345` 的三个已具名 Store 值，以及 `34819.ITEM_MATERIAL=2`。不批准旧物品类别全枚举、其他原始类型中的枚举或材料的业务子类型。`store_type=0/FFFFFFFF` 在本 wrapper 实测走 pack 侧 fallback；这只描述未知输入的代码行为，不批准新增服务器发送值。

## 原生数据流与分段边界

通知实际执行 `153CBB460 → 153CAF190 → slot25`。测试显式装配 metadata 已声明的两种仓库 vtable：派生 43222 的 `150809180` 先清 5 个附加字典，再调用基类 59658 的 `14E43BE70 → 14E3DC3D0` 清 9 个字典；68182 继承基类 slot25。所有字典预放非零哨兵并检查 count、version 和数组内容，未被选择的一侧保持哨兵，容量指针 +0x78 保留。后续逐项交付真实 reader 生成的元素，列表枚举器与 `Array.Clear` 的存储操作为明确夹具。

这里没有执行现场单例初始化。额外静态检查发现 `ManagerController.CreateGlobalManager` 的 MethodSpec 95866/95868 分别使用 43222/68182；其 usage 758923/758925 位于 `145A654C8/145A654D8`，初始化调用点为 `14F42B138/14F42B150`。动态静态字段布局仍由泛型运行时生成。本卡没有把显式装配的 vtable 说成已经观察到的现场对象 ABI；背包与仓库的路由数值来自实际 wrapper 和具名 enum，清空结果限定于这些已声明实现。

通知里的 `14E43A630` 暂作为交付 sink，随后在同一 fixture 内对每个实际元素单独执行该函数。其真实路径查询旧 GUID；未命中时查询资源并创建 wrapper；已命中时复用 wrapper。`150ED35E0 → 15459E4B0 → slot9 150ED3210 → 148FED420 → slot11 150ED3510` 执行原生 oneof 选择、材料 Clone/CopyFrom、ID/GUID/数量/类别赋值。使用 pool、泛型上下文和资源行存储夹具，未用 fixture 代写物品业务字段。

材料资源来源不是孤立 literal：`17822 PFLIFPKFDLF` 同时拥有路径 getter `14A1D79C0`、loader `14A1D7890` 和查询 `14A1D8440`。本卡实际执行 getter 及 loader 到 `14D6E1570` 文件 I/O 边界，实参来源为 usage 783160 / literal 70971 / cell `145B547F8` 的 `Data/_ExcelBinOutput/MaterialExcelConfigData`；同 owner 的 +0x32480 字段 metadata 是 `Dictionary<uint32,63074>`，63074 继承物品资源 34113。真正的资源查询执行，字典 key 查找和资源行是控制输入，**没有读取或宣称验证真实材料数据集**。

物品应用先停在类别回调 `14E43B0E0`。另一个片段从回调之后的 `14E43A94B` 开始，只验证数量零/非零对 slot23/slot22 的选择；类别回调没有执行。再单独调用实际 GUID helper `14E43AF70` 或 `14E3DD4F0`，执行具体 AOT 字典 `156F6F510/156F6F850/156F70100`。原生 UInt64 comparer `158BB0140` 的 MethodSpec 360608、定义 10296、class instance 58 已校验，字典键的哈希与相等不由 Python 代算。其余分类、id 二级索引、派生索引和 UI fanout 未执行，不能把这些片段拼成完整 Handler 返回的证据。

## 验收结果与保留的失败

19 组独立 wire 通知，合计交付 22 个物品，其中 21 个到达材料模型/数量分支；1 个缺失资源样本实际返回且不产生模型（其他资源表显式设为缺失）。两个空列表照样清空选定库存。覆盖双列表元素、相同 ID 不同 GUID、相同 GUID 覆盖、相同 GUID 数量归零移除、0/max uint32、0/max uint64、`2^53+1`、高位不同但 UInt64 hash 相同、负数量差、same-oneof 显式零、切换 oneof 后材料胜出，以及保留未知嵌套值。

数量本身是 uint32，未缩放；数量差采用 32 位减法再按有符号值消费。`FFFFFFFF` 保留为 4294967295，差值可为 -1，仍按数量非零选插入。GUID 9007199254740993 和 18446744073709551615 均完整保留；两个 hash 相同的 `0x100000001`、`0x200000002` 可独立查回。控制资源类别改成 7 后模型也变成 7，证明类别取自资源行而非 oneof tag；此负向样本不批准类别 7 的业务名字或实际材料表内容。

9 项新增合同测试与 raw 图、容器、外层、graph、AOT、签名和 literal 回归合计 **59 项通过**。两次正式运行的完整轨迹和摘要均逐字节一致：轨迹 SHA-256 为 `626cce90ce4f67a75969aa32d2ff3c96f1dcdb8897fe065ba6bc618f1a9d57a3`，摘要为 `130f6387005ad3b5cf0e2dc2e7c73b930e698fa07f03a7fe8b48fefe87dc16db`。对比记录为 `local/migration72/evidence/inventory-contract-determinism.json`，测试记录为 `local/migration72/evidence/inventory-contract-tests.log`。

另有 5 个错误样本：递归上限、负嵌套长度、截断材料命中 3 个原生错误 helper；非法 tag 与 uint64 varint 溢出由明确输入模型拒绝，两者不冒充客户端原生异常。初始缺失实现的红测保留于 `local/migration72/evidence/inventory-contract-red.log`。取证中还修正了重用 raw graph 时遗留的 UInt64 dictionary lookup model、既有模型复用时的 BeforeRecycle vtable、删除路径默认 comparer 初始化；最终所有 GUID 业务存储操作均执行原生代码，夹具模型清单公开保留。

本卡无生产代码、配置、补丁或资源修改，不需部署或重启。实际单例初始化、全部类别/库存索引、客户端 UI 与登录成功均未验收。下一步优先 **Weapon/Equip 最小用途和字段合同**：新号选人后的初始角色通常携带初始武器，仅材料分支不足以构成登录所需最小库存；继续沿 tag6→44169→实际 equip 子分支构造与独立装备/武器消费者取证，不先扩大到全经济或未知材料附加结构。
