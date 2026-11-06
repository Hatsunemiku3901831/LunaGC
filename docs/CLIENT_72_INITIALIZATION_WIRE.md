# 国服7.2初始化raw字段与遗留坐标核查

F-03b运行补充（4bbf9fd）：容量目的别名经独立[消费合同](CLIENT_72_STORE_WEIGHT_CONTRACT.md)确认，5字段/enum已接入，3 raw未知省略；134原生reader的原始证据不因业务晋级而重写。旧选择缓存21136/46070不能用于目标，正号与onLogin发送停用，DoSet/SetPlayerBorn显式选人、准入与确认保存保留。完整初始化仍缺PlayerData/Store/Avatar等，非D-02。

2026-11-06，F-03限定离线证据。只读固定客户端三输入；不启动客户端、服务或调试目标，不修改运行Proto或三层mappings。参考AstaPS固定`a49f51aa6f2787ffbc25cee46801b70dee860d23`仅用于筛候选，不用其名称、tag或同型字段晋级目标语义。完整初始化闭包与D-02仍未完成。

## type28836/Cmd22609的完整raw标量切片

正式工具[trace_client72_initialization.py](../tools/trace_client72_initialization.py)通过`extract_client72.HASHES`固定三输入，重新提取元数据并执行`KNCCLAJAEEC`的目标reader`0x14fd45000`至返回。静态包号getter为`0x14fd44ff0`，Cmd22609来自目标静态调用链，Asta把同号命名为`StoreWeightLimitNotify`。这仍是语义候选；实际业务消费与类型身份未独立确认。

| target clientField | tag/wire | 对象偏移 | 目标存储类型 |
| --- | --- | --- | --- |
| PHDOBIIMHLB | 1/0 | +0x1c | uint32 |
| PAKLDABINMP | 2/0 | +0x2c | uint32 |
| ADCHFHMCOJG | 4/0 | +0x18 | uint32 |
| NAEDDJBKCFC | 5/0 | +0x20 | uint32 |
| CLFJNKKFONI | 6/0 | +0x30 | enum/type55345 |
| DECHDEGPBOE | 7/0 | +0x24 | uint32 |
| CKHIAGMBNCC | 9/0 | +0x28 | uint32 |
| GGGJAGOKMCI | 14/0 | +0x34 | uint32 |

完整摘要为[initialization-wire-observations.json](../protocol/cn-7.2/initialization-wire-observations.json)，每个字段保留native store地址；全部8个目标实例字段都有非零原生store观察。128个tag1..32、wire0/1/2/5有界扫描，加6个独立非零/uint32上界/显式零/重复末值/错wire/空输入样本，共134次原生reader执行。另5个错误输入由显式WireInput模型拒绝，不能称客户端原生拒绝。此消息没有uint64、nested或list字段，不宣称覆盖这些类型。

独立手写wire为`080b1016202c28373001384d4863708d01`，分别赋值11/22/44/55/1/77/99/141，并直接核对目标对象偏移。没有使用目标schema自编自解作为唯一证明。实际原生dispatch/store及重复末值与错wire跳过执行；tag/scalar/skip、对象分配/引用计数属于明确模型，IFix替代路径未执行。

当前builder实际写`store_type`、`weight_limit`、`reliquary_count_limit`和`furniture_count_limit`。Asta只有前两个在tag6/tag2外形相符，后者含20000+占位。不能将六个同型uint32任意配成其余限额。下一步的签名typed消费候选为`KKLMBGOADJM.AKCCKHDEPDP/0x153cbc210`、`IOHHDBCNLPJ.HFCGADDPEAD/0x14e3d6da0`；这些只证明参数类型，尚未执行消费链，不是业务名证明。应实际追踪对象字段到每种库存限额的使用，再核对枚举含义。

## AvatarSelectionDataNotify历史坐标不能沿用

现有历史Proto与2026-10-18文档把`HPHDHDJHCGO/type46070/Cmd21136`作为角色选择缓存通知。固定国服7.2三输入重新运行`classify_commands`得到4012条静态命令绑定，其中**Cmd21136命中集合为空**；目标type46070实际为`DDFOJHINPLA`，该类型的目标命令绑定为**29156**，getter为`0x149813ec0`、methodIndex382511。正式摘要`legacySelectionAudit`保留完整目标字段/方法/父类、21136空集合及实际29156绑定。

这证明旧名称、类型索引和包号不能复用为国服7.2身份依据；它不证明所有动态/未提取协议中不存在该通知，也不证明7.2无需对应初始化。历史空载荷与缓存修复验收属于旧目标，不能当作7.2空schema或登录验收。

本次审计前调用点为`Player.onLogin`的`Player.java:1928`，在AvatarDataNotify之后、AvatarWeaponSkinDataNotify之前；`PacketOpcodes.java:44`原值21136。融合实施应停用未经核实的正号及发送调用，继续保留正确已验的显式选人三件组。该停用属于运行改动，构建/回归和未部署边界由实施批记录；本项仅交付证据，不修改这些运行文件。

回归需要分别断言固定目标的命令命中集合和实际type46070名称，生产onLogin不再发送该未迁入通知，生产常量不再占用正号。现有Protocol71测试仅作未迁入诊断，不能改名为72后沿用。新工具的第6项测试已固化目标坐标矛盾。

## builder闭包下界与后续顺序

`HandlerPlayerLoginReq`新号发送DoSetPlayerBornDataNotify及LoginRsp后返回；旧号先onLogin再LoginRsp。onLogin还调用管理器/World/事件/任务和异步边界，静态直接构造的20包不是客户端最小必需序列。所选初始化builder的schema下界为：

- PlayerDataNotify → PropValue(type/ival/val)及map；候选目标7193/BIMKHOFCKHB/type79263，reader`0x14e420460`，typed消费`0x1507bc0e0`。下一步需非零uint64、字符串/布尔及非空map嵌套，不能只取外层Asta tag。
- PlayerStoreNotify → Item(item_id/guid/detail oneof)、Material(count)、Furniture(count)、Equip/Weapon/Reliquary及TpsWeapon分支。候选20892/CAIHCNHAMHB/type68537。不能把旧Item依赖整棵当成目标已验。
- AvatarDataNotify → AvatarInfo/AvatarTeam、PropValue、AvatarSkillInfo、AvatarFetterInfo/FetterData、SceneWeaponInfo，以及map/list/uint64 GUID。候选20143/ILEPMBEJKIA/type30509。仍缺递归目标reader及字段消费，不扩张为全玩法恢复。
- PlayerEnterSceneNotify → 目标已有外层26、位置3和六个嵌套15字段的raw8类型/44字段。builder的业务字段、枚举和算术转换仍未绑定，raw不能直接接旧setter。
- Ready/Init/Done/Post请求响应仍缺独立类型/消费链。特别是Asta的EnterSceneDoneReq号1017在目标有NCJGOJMOPHB/type22065与HDPGCNCMACO/type63999两条绑定，单uint32外形相符只能筛候选，不能消除重号。

本机完整14条候选的元数据方法、typed消费候选、builder成员、Asta候选Proto/hash和既有raw证据索引在`local/migration72/analysis/f03-initialization-audit.{md,json}`；本机原始trace不提交。

## 复现和验收

先从当前本机配置核对`GAME_PATH`。以下输出保持忽略目录；验证后可将`--observations`指向正式摘要进行字节对比，不能盲目刷新守卫。

```powershell
python tools/trace_client72_initialization.py --client $env:GAME_PATH --output local/migration72/evidence/initialization-wire-trace.json --observations local/migration72/evidence/initialization-wire-summary.json
python -m unittest tools.test_client72_initialization -v
```

本项6测试覆盖raw布局、独立wire、边界/重复/错wire、绑定漂移拒绝、明确模型拒绝及历史坐标矛盾。工具输出没有实机或运行部署通过状态；三层原状态不变。下一可执行任务是上述type28836 typed字段消费或PlayerData非空map/PropValue链，优先原生证据，不能按Asta名称批量晋级。
