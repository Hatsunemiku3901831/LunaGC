# 国服 7.2 Equip / Weapon 最小消费合同

F-03d2b2 接续 [库存 raw 图](CLIENT_72_INVENTORY_GRAPH.md) 的 15 类、47 个 wire 字段和 [最小材料合同](CLIENT_72_INVENTORY_CONTRACT.md)。本卡闭合 `Item.tag6 → Equip` 及其 Weapon、Reliquary 两分支的用途，批准 **10 个限定字段**。名称来自目标客户端的具名资源、实际模型及角色装备槽消费者的交叉证据，是用途推断；没有恢复原始消息英文名，也没有借用 Asta 或旧 `GameItem` schema 批准字段。

本卡执行实际原生 reader、Clone、装备基础转换、武器属性/经验/词条查询、GUID 字典以及分段角色装备赋值。完整武器构造器、角色初始化及后续事件/UI 未执行，保持 `runtimeStatus=not-tested`、`targetProtocolReady=false`。这不是客户端登录成功的验收。

## 固定输入与复现

| 相对 `GAME_PATH` 路径 | SHA-256 |
| --- | --- |
| `YuanShen.exe` | `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5` |
| `YuanShen_Data/Managed/Metadata/global-metadata.dat` | `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a` |
| `YuanShen_Data/Managed/Metadata/startup-metadata.dat` | `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105` |

工具另固定 `inventory-graph-observations.json` 的 SHA-256 `ca45b0d9b038cd46fc35943924ca77ae46c8fbae82c06e131245e03f43d95b18` 和 `inventory-contract-observations.json` 的 SHA-256 `130f6387005ad3b5cf0e2dc2e7c73b930e698fa07f03a7fe8b48fefe87dc16db`。原生 reader 和容器证据从既有图接续，不把业务命名写回 raw 来源。

```powershell
python tools/client72_equip_contract.py --client $env:GAME_PATH `
  --output local/migration72/evidence/equip-contract.json `
  --observations local/migration72/evidence/equip-contract-observations.json
python -m unittest tools.test_client72_equip_contract
```

完整轨迹只留忽略目录，正式脱敏摘要为 `protocol/cn-7.2/equip-contract-observations.json`。每段保留 entry、停止边界、真实指令路径 SHA、经过的原生入口及显式模型清单；固定所用工具的源文件 SHA。正式复现重复运行至另外两个文件，再比较完整轨迹及摘要的字节。

## 三层结论及最小接管规格

1. **CmdID→类型**：继承库存合同的 `20892 → 68537 CAIHCNHAMHB`。本卡没有新增命令身份，也没有推断其他通知复用方式。
2. **类型→用途**：`65333.tag6 → 44169 BNDIKNLDLOJ` 是 Equip；其 oneof tag 2 的 `64947 FNECPDFLEHN` 是 Weapon，tag 1 的 `74063 JBBEOMJDLFM` 是 Reliquary。两者分别进入目标具名 Weapon/Reliquary 资源，并由角色模型按 Weapon 槽或 Reliquary 资源槽消费。仅按单个 uint32 形状无法得到这些结论。
3. **字段→wire**：下表的 tag、wire 和类型来自此前完整 raw reader 合同；本卡增加实际业务消费者。只接管下表与材料合同已批准的共享 ID/GUID，不能扩展到旧物品全 schema。

| 类型 | tag / 原字段 | wire / 最小别名 | 本卡独立语义来源 |
| --- | --- | --- | --- |
| 65333 `NDGHFPMJFIM` | 6 `FLPGMLOFAKP_6` | oneof message 44169，`equip` | `150ED35E0 → 148FED420` 处理装备，并由两个实际装备模型及角色槽消费 |
| 44169 `BNDIKNLDLOJ` | 1 `FLPGMLOFAKP_1` | oneof message 74063，`reliquary` | MethodSpec 99285 选择模型 52700；具名 Reliquary 资源查询；角色装备槽取自该资源 |
| 44169 | 2 `FLPGMLOFAKP_2` | oneof message 64947，`weapon` | MethodSpec 99283 选择模型 29353；具名 Weapon 资源；角色消费者使用 `EQUIP_WEAPON=6` |
| 44169 | 3 `FAPABAJBMAK` | bool，`is_locked` | 原生转换 false→1、true→2，存入模型 typed enum 85101 的 `UnLock/Lock` |
| 64947 `FNECPDFLEHN` | 1 `NJAGFJINHMA` | uint32，`level` | `150758430` 作为 WeaponCurve key，`14A2C38C0` 作为 WeaponLevel key；角色装备编码存储实际解回同值 |
| 64947 | 2 `NILMBAGDEAL` | uint32，`exp` | `14A2C38C0` 将该值除以当前等级和品质对应的 WeaponLevel 资源值；实际执行 float32 运算 |
| 64947 | 3 `KFDCBKBPPGL` | uint32，`promote_level` | `150758430 → 1507587B0 → 147BF60C0` 作为 WeaponPromote 资源组内第二层 key，增加突破属性 |
| 64947 | 4 `JMGKMMFNABP` | map<uint32,uint32>，`affix_map` | `150758E60 → 152B8EF00` 使用 key 选择 EquipAffix 组、value 选择组内等级；角色消费者独立枚举并原值编码保存 |
| 74063 `JBBEOMJDLFM` | 1 `NJAGFJINHMA` | uint32，`level` | 独立 Reliquary 模型路径在 `15184A5F0` 写入与武器相同的角色装备等级槽；原生整数编码/解码读回同值 |
| 74063 | 6 `CGKIGPFDFOF` | bool，`is_star` | 原生转换 false→1、true→2，存入模型 typed enum 73539 的 `UnStar/Star` |

上表 oneof 的 `_6/_1/_2` 是 raw 图为共同存储字段 `FLPGMLOFAKP` 添加的 tag 后缀，并非新恢复的客户端字段名。所有消息语义字段名是用途别名，最终 Proto 可只声明已验最小集合：`Equip` 的 oneof 分支 1/2 与 bool 3，`Weapon` 的 uint32 1/2/3 与 map 4，`Reliquary` 的 uint32 1 与 bool 6。旧 `GameItem` 的精炼字段如何转换到 `affix_map` 仍需单独证明，不能默认减 1。

`64947.tag5/6` 保持未知；`74063.tag2/3/4/5/7/8` 保持未知。特别是 Reliquary tag 2/3 与 Weapon tag 2/3 的混淆名字相同，并不能据此批准经验/突破含义；tag 4 和三个列表也不能直接命名为主副词条或其他旧业务。列表在 reader→Clone 中的值已验证保留，业务用途未恢复。`Item.tag7/8/9/10`、材料附加结构及全经济/TPS 不在本卡范围。

## 两分支身份的独立证据

工厂 `150ED35E0` 的参数声明确认为 Item 65333。outer oneof 6 且 inner oneof 1 使用 usage 760452 / MethodSpec 99285 / cell `145A721C8`，泛型参数为模型 52700 `CJJDJJNDMEP`；另一个装备分支使用 usage 760451 / MethodSpec 99283 / cell `145A721C0`，泛型参数为模型 29353 `GPBLKEHGFPG`。泛型分配方法 `15459E4B0` 实际执行，pool 的 class 分配是显式夹具。

两个模型继承 51337，存储结构 68895 的 metadata 明确将 +0x40 声明为 64947、+0x48 声明为 74063；+0x28、+0x38、+0x3C 分别声明为物品类别 34819、锁状态 85101、星标状态 73539。实际 `148FED420` 从 wire 对象转换并调用各自 Clone，不由夹具填这些业务字段。共享 item_id、guid 分别进入模型 +0x10、+0x18；装备数量常量为 1，类别从资源行取值。

资源身份连贯绑定同 owner 的路径 getter、loader、metadata 静态字典和实际查询来源，并非只搜到了字符串：

| 具名资源 | owner 类型 | 静态字典偏移 / 值类型 | getter / loader | literal index / cell |
| --- | --- | --- | --- | --- |
| Weapon | 71245 | +0x34900 / 87097 | `154177230 / 154177240` | `80414 / 145B66F10` |
| Reliquary | 60446 | +0x35470 / 74316 | `147C38570 / 147C38D90` | `82242 / 145B6A830` |
| WeaponCurve | 67884 | +0x123A0 / 12924 | `1502C1CE0 / 1502C1BF0` | `27754 / 145B00170` |
| WeaponLevel | 12244 | +0x281D0 / 71884 | `1490466D0 / 149046840` | `53603 / 145B32938` |
| WeaponPromote | 22605 | +0x34E28 / 内层字典→20394 | `147BF6620 / 147BF6530` | `81103 / 145B68498` |
| EquipAffix | 45983 | +0xDFD8 / 内层字典→15583 | `152B8ED00 / 152B8EFD0` | `21444 / 145AF3C40` |

每个路径均为 `Data/_ExcelBinOutput/<表名>ExcelConfigData`。原生 getter 和 loader 执行到 `14D6E1570` 文件 I/O 边界，检查参数正是已解出的路径来源。资源字典和行内容由控制样本供给，未读取真实资源数据集；这足以验证字段如何索引资源及运算，不能批准真实资源 ID 全表。Weapon 的资源字典直接被原生转换访问，Reliquary 还经过原生查询 `147C38EC0`。

第二条独立链来自 typed 角色模型 18163：`15184E100` 的 metadata 参数为 48178，静态调用链将其 GUID 列表交给 `15184EC00`。本卡实际从后者执行：每个完整 uint64 GUID 经 `14E3DA130 → 156F6F510` 查库存模型；Weapon class 29353 路由至 enum 49540 的 `EQUIP_WEAPON=6`，Reliquary class 52700 从 Reliquary 资源读出槽值。控制资源的 `EQUIP_BRACER=1` 与 `EQUIP_DRESS=5` 都分别到达正确槽位。这里只批准这三个实际消费的槽符号、两个实际类别符号 `ITEM_RELIQUARY=3 / ITEM_WEAPON=4` 及上述锁/星标枚举，不批准旧装备枚举全表，也不据此提前批准 48178 的所有身份与字段。

## 数值与 affix_map 的实际合同

原生属性计算读取 WeaponCurve 的 level key，按资源属性的 curve type 匹配倍数，再使用 WeaponPromote 的资源组 ID 与 wire promote_level 查突破附加值。控制基础值 2.5、level 3 倍数 3、promote 2 附加值 1.25 时，实际结果为 **8.75**；只改 level 为 4 得到 **11.25**，只改 promote 为 3 得到 **9.75**。未知 phase `FFFFFFFF` 在该控制资源表中未命中，附加值为零。

经验进度函数按资源中的品质等级选择 WeaponLevel 行的列。本样本第 1 列为 100、第 2 列为 1000，资源品质为 2；exp 250 得到 **0.25**，exp 1500 得到 **1.5**，该函数不截断到 1。0 与 `FFFFFFFF` 均覆盖，最大值按原生 uint32→float32 转换，再完成除法。这些资源编码值通过实际 `14AEFA090` 写入、`14AEF9DB0 / 14F905140` 解码，不用 Python 替代目标运算。

`affix_map` 的含义限定为 **EquipAffix 资源组 ID → 组内等级 key**。资源配置提供可用组 77、88，实际 `150758E60` 的行为为：

| wire map | 原生资源选择 |
| --- | --- |
| `{77:4}` | `(77,4)` |
| 空 map | `(77,0)` 和 `(88,0)`，使用资源中配置的组 |
| `{77:0}` | 只选 `(77,0)`，不再给 88 补默认 |
| `{999:4}` | 无选择，未知 key 不代替资源配置组 |
| `{77:3}`，控制表没有等级 3 | 无选择，不退回等级 0 |
| `{77:4,88:1,999:FFFFFFFF}` | 选择 `(77,4)` 和 `(88,1)` |

角色装备赋值 `15184A5F0 → 14AD07E80` 另走独立消费者：原生 map 枚举器 `1576CDEE0` 读取真实 reader/Clone 节点的键值，每个键和值经 `14B1046F0` 编码写入角色装备节点的字典存储边界，再由实际 `14B1049F0` 解回。所有输入键值保持相同，包括资源查询未使用的 key 或 level。空表默认只发生在上述资源选择函数，不改变 wire map 或角色存储 map。**这里没有 `value - 1` 的转换；不能把旧服务器 refinement 数值直接视为已经验证的 wire 等级。**

Weapon.level 与 Reliquary.level 也分别经过 `15184A5F0 → 14B1046F0` 写入相同装备等级槽 +0x50，并由原生解码读回。可逆字节表和随机源初始化是显式夹具，真实整数 codec 的指令执行；并非生产客户端初始化随机状态的证明。

## 分段边界、错误与验收

22 组独立 wire，共构造 **26 个装备模型：19 个 Weapon、7 个 Reliquary**，并完成 26 次角色槽赋值片段。覆盖不同 ID/GUID、相同 GUID 覆盖、输入顺序与缺失 GUID、高位哈希碰撞、0/max uint32、0/max uint64、`2^53+1`、`2^63`、锁/星标真假、未知列表保留、same-oneof 显式零覆盖及跨 oneof 最后分支胜出。GUID 索引插入 `14E43AF70 → 156F6F850` 和查找 `14E3DA130 → 156F6F510` 均原生执行，没有沿用 graph 的 UInt64 lookup 代用模型；同 GUID 后写覆盖会使角色列表两次都取得后一个物品。

边界逐段公开：

- Weapon 走实际构造器 `150757D20`，在完成基础转换/Clone/赋值后的 `150757DFB` 停止；尚未执行后面的派生状态计算、图标/事件和回调。因此 `completeWeaponConstructor=false`。Reliquary 经基础构造器 `150ED3210` 返回。
- 角色 GUID 路由 `15184EC00` 中，槽赋值临时作为交付 sink，路由停于尾部 `15183EAE0`。随后每项分别真实执行 `15184A5F0`；Weapon 在 `15184A8F4`、Reliquary 在 `15184A84A` 停止，均未进入后续角色状态 fanout。没有声称一次完整角色 Handler 返回。
- 资源查询的字典 key 存储、资源行、泛型 List/NativeArray 容器、类和 pool 分配为控制夹具。角色装备节点分配与编码字典的最终存储也是明确模型，目标枚举、字段转换、整数编码及回读原生执行。未读取实际资源全集或构造现场单例。

8 个拒绝样本中，空 Equip oneof 命中工厂的原生 null-error helper 边界 `1404C2BE0`；Weapon 分支搭配 Reliquary ID 在角色赋值阶段、Reliquary 分支搭配 Weapon ID 在角色槽路由阶段命中同一边界。只记录抵达错误 helper，没有执行完整异常派发。递归上限、负嵌套长度和截断 Equip 进入 3 个原生 reader 错误 helper；非法 tag 和 uint64 varint 溢出由明确输入模型拒绝，不能冒充原生异常。

初始缺失实现的红测留于 `local/migration72/evidence/equip-contract-red.log`。独立手写 wire 预期的嵌套长度曾误计 1 字节，按实际 payload 长度修正；取证中还遇到显式类存储缺少初始化标志、资源字典 count 未设导致空选择、以及重复片段未清空编码 map 观察缓存，均已通过相应原生值和零/非零样本纠正。最终审核测试会拒绝把实际 codec/GUID 核心替换成模型、把 affix level 减 1、将模型拒绝升级为原生错误、删掉未知列表零值或把本卡升级成实机验收。

正式双跑和测试结果见文末验收记录。该批仅新增离线工具、测试、专题和摘要，不修改运行代码、Proto、配置、补丁或资源，不需部署或重启。下一项优先以独立 typed 入口 `1507CEE50` 接续 Avatar 最小用途/字段合同；生产侧可依据本卡限定映射另建接入卡，保留未知字段，并单独证明服务器精炼数据到 affix_map 的转换。

## 正式验收记录

10 项新增测试及库存合同、完整 raw 图、容器、外层、graph、AOT、签名和 literal 回归合计 **69 项通过**。两次正式运行各完成 22 组正向样本和 8 组拒绝，完整轨迹与摘要逐字节一致：

| 产物 | SHA-256 |
| --- | --- |
| 完整轨迹 `equip-contract.json` | `2be34f4d11430391174f4b4bae62851aacf7c459e0502e9407ed660e9f13cdaa` |
| 脱敏摘要 `equip-contract-observations.json` | `77e1df9d53d93380362f30ec0ad604b447a1123f4840963fa9f91a1da41b5011` |

双跑比较记录为 `local/migration72/evidence/equip-contract-determinism.json`，测试记录为 `local/migration72/evidence/equip-contract-tests.log`。这批没有生产修改或实机运行，不需要更新运行 JAR；不把编译、健康检查或本卡离线模型当成 D-02 客户端验收。
