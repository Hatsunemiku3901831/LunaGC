# 国服7.2库存种类容量通知合同

2026-11-06，F-03b限定离线闭包。工具[client72_store_weight_contract.py](../tools/client72_store_weight_contract.py)固定`extract_client72.HASHES`三输入，复用已验raw reader切片，不重复扫描reader，也不把Asta字段名/tag当目标证明。正式摘要为[store-weight-contract-observations.json](../protocol/cn-7.2/store-weight-contract-observations.json)。本项没有启动客户端、服务或调试目标，D-02未验。

## 身份与实际完整消费路径

目标type28836/KNCCLAJAEEC的命令getter返回22609。库存模块注册前缀实际执行至目标22609注册后的`0x153cbff90`；`0x153cbff84`设置22609，真实调用注册入口`0x1532ed560`。注册表保存动作是明确sink，并未声称完整网络收包。

固定非零wire随后连续执行以下原生路径至dispatcher返回：

`reader 0x14fd45000` → `dispatcher 0x153cbb460`（`0x153cbb8f9`比较22609，`0x153cbbd4c`转入typed wrapper）→ `0x153cbc210` → `0x14e3d6da0` → 实际generic Dictionary插入/覆盖`0x157d1a480` → `0x14e3d6c60` → 真实事件发送调用`0x1532d6720` → 返回。

metadata独立确认wrapper/application参数均为type28836。`+0x30`使用目标枚举55345：`StoreNone=0`、`StorePack=1`、`StoreDepot=2`。接收实例`+0x78`的字典声明为`Dictionary<目标item枚举34819,uint32>`，其中枚举常量独立从目标default表解出。运行时真正的bucket、entry、计数与覆盖写入由原生generic字典方法执行；只将枚举hash实现为明确恒等回调，不替换容量值或插入逻辑。

下游`0x14e3d7d70`的metadata签名为目标item枚举、`out int32`当前数量、`out uint32`上限。实际执行该getter及原生容量字典lookup`0x157d19fe0`，将刚由消息应用的材料限额141读回；继续执行`0x14e3e32d1`的实际比较，当前数量0/140走低于上限分支，141/142走达到上限分支`0x14e3e330d`。后者确实引用目标literal **SHOP_ITEM_LIMITED**，来源为usage group`0x71c6`、source25222、cell`0x145afb250`，源索引和槽分别解码并核对RIP引用。当前材料查找index及数量存储是明确输入，限额读取和比较执行原生代码；未执行本地化/UI或整个材料准入函数。

这些证据独立建立“按仓库和物品种类应用容量通知”的业务身份。**StoreWeightLimitNotify是本服务端的用途别名，不声称已经还原官方原始消息名**；SHOP_ITEM_LIMITED证明容量用途，并不是消息同名literal。此前有界literal搜索未命中消息名称，不能外推目标中绝无该literal。

## 可迁入的5个字段

| tag | clientField/对象偏移 | 已验含义 | 实际目标消费 |
| --- | --- | --- | --- |
| 6 | CLFJNKKFONI/+0x30 | store_type | StoreDepot2选depot，其他值走pack；明确StorePack1可发送 |
| 1 | PHDOBIIMHLB/+0x1c | reliquary_count_limit | 原值存入ITEM_RELIQUARY=3的uint32容量 |
| 9 | CKHIAGMBNCC/+0x28 | weapon_count_limit | 原值存入ITEM_WEAPON=4的uint32容量 |
| 14 | GGGJAGOKMCI/+0x34 | material_count_limit | 原值存入ITEM_MATERIAL=2的uint32容量 |
| 5 | NAEDDJBKCFC/+0x20 | furniture_count_limit | 原值存入ITEM_FURNITURE=6的uint32容量 |

四计数的字段含义和数值编码分别记录：语义来自目标item枚举与容量字典，raw值直接加载并写入，**没有额外XOR/加减编码**。tag6只用已验StorePack1/StoreDepot2值；生成Proto可保留int32存储而不宣称已经接管整棵旧StoreType或所有枚举运行语义。

独立手写全raw向量`080b1016202c28373001384d4863708d01`对全部8字段赋非零值；实际pack字典得到`{3:11,4:99,2:141,6:55}`，未选depot字典保持空。最小5字段业务向量为`080b283730014863708d01`，即圣遗物11/家具55/StorePack1/武器99/材料141。

`+0x18/tag4 ADCHFHMCOJG`、`+0x24/tag7 DECHDEGPBOE`、`+0x2c/tag2 PAKLDABINMP`在上述完整正常消费路径中未读取。对这三项分别改成uint32上界仍得到相同容量；该结果只覆盖已执行正常路径，不排除IFix/反射或其他尚未取证用途。尤其**tag2不晋级weight_limit**，不能照Asta把`inventoryLimits.all`送入未知字段。

现有PacketStoreWeightLimitNotify迁入范围应为StorePack1及`INVENTORY_LIMITS.relics/furniture/weapons/materials`四项；移除未经证实的all→weight_limit发送。该实施由融合批完成并统一构建/回归，本研究不修改生产/Proto/mappings。

## 实际样本、默认值与边界

11组样本含12次完整reader/dispatcher执行：独立非零pack、depot、StoreNone0与未知枚举回落pack、三个未消费raw字段扰动、四容量uint32上界、显式零、字段全省略、已有字典再次覆盖、重复wire末值、未注册命令65535。重复更新后原生字典仍为4项，没有将更新变成追加；未注册命令正常退出且不读消息字段、不写容量、不发事件。

原生构造函数`0x14fd44980`实际执行返回；对零存储与非零哨兵均不写字段。因此缺省零来自明确的托管零分配fixture，不能说构造器提供了业务默认限额。省略全部字段时，正常消费实际把四项容量设置为0并走StoreNone0的pack分支；不能凭业务预期补成配置默认值。

两个独立native边界：null消息、缺失pack实例均到真实throw helper`0x1404c2be0`调用入口停止，未执行helper、未伪造返回码或成功结果。事件281的完整24字节参数由真实发送调用捕获；事件监听器作为明确void sink，不声称监听器或UI已接受更新。

## 验证与复现

新增Python8项测试核对独立hex、五个业务字段与三项unknown、目标枚举默认表、实际分仓容量写入、遗漏字段/构造器、覆盖/重复/边界、下游实际容量阈值及结果漂移拒绝。新增Java`Protocol72InventoryCapacityTest`6项使用独立hex、canonical22609、全部8字段descriptor、仅五tag实际sender、零/uint32上界及重复/错wire断言；Java红绿和最终构建由根融合批记录，不能将本文的Python通过当作Java通过。

先从本机配置核对`GAME_PATH`，输出放忽略目录：

```powershell
python tools/client72_store_weight_contract.py --client $env:GAME_PATH --output local/migration72/evidence/store-weight-contract-repeat.json
python -m unittest tools.test_client72_store_weight_contract -v
```

工具固定依赖`initialization-wire-observations.json`的SHA-256，来源漂移直接拒绝；仅输出路径可变。模型仍限IFix关闭、已初始化容量17的字典、显式generic context/枚举hash/reader原语/事件sink。没有字典扩容、真实socket、事件监听器、客户端UI或实机验收，也没有nested/list/uint64字段覆盖。下一项初始化业务包仍需独立目标消费者与递归字段证据。
