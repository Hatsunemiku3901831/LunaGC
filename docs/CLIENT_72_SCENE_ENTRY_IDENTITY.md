# F-03d3f2b：场景入场消息到本地角色实体

2026-11-07。固定国服7.2三输入 `extract_client72.HASHES`，验证 `PlayerEnterSceneInfoNotify` 的本地角色实体 ID 来源：**CmdID21761 → type34208/NOCHHDCLKDJ → tag12/OLCJCLBMMCF/+30 uint32 → 场景管理器+284 → 具名 GetLocalAvatarConfigId**。偏移均为十六进制。这里只验限定消费前缀及外层wire，不是完整初始化/场景实体生成、网络Handler或实机通过。

## 三层来源与外层边界

每次实际重读EXE、global/startup metadata并核三hash，重提取metadata/参数/usage，不使用历史地址解码输入。输入值见[Avatar核心](CLIENT_72_AVATAR_CORE.md)。

- CmdID→类型：type34208槽22 getter实际常量21761，网络注册`1507C2A12`的同号注册目标`1532ED560`保持；不是只凭旧号或上游名字。
- 类型→语义：目标typed网络Handler`1507D1620`和管理器`14CD68530`参数均重新解为34208，网络Handler实际调用管理器。其固定literal含`OnPlayerEnterSceneInfoNotify notify is null`，并有TeamEnterInfo/AbilityControlBlock/AvatarEnterInfo错误分支的独立文字来源。
- 字段→wire：128次tag1..32/wire0/1/2/5实际执行该类型reader，覆盖全部五个metadata外字段。tag12/+30和tag7/+34为不同uint32；另三份nested只验空envelope，不称非空图闭合。

| tag | raw字段 | 偏移 | 类型与本卡范围 |
| --- | --- | --- | --- |
| 2 | DIGAMAKGCPA | +18 | list→18295，空envelope |
| 7 | MCCMGLDOECH | +34 | uint32，业务意义未知 |
| 12 | OLCJCLBMMCF | +30 | uint32，本地角色实体ID用途已验 |
| 14 | LFLOLJADPBI | +28 | →44512，空envelope |
| 15 | AEKMONHBHAA | +20 | →49723，空envelope |

三份nested的`contentStatus=not-traced`保持。列表codec的+35000/tag2来自当前cctor，五个相关class usage重新提取，class初始化状态是显式托管模型。没有用空对象批准这些依赖或准入。

## 实际消费与独立反例

由真实reader生成消息后，直接将同一消息对象交给管理器`14CD68530`。它从+30加载EDX并实际调用`1517B18F0`，后者在`1517B1904`以四字节写场景管理器+284，随后在真实下游`153A20F30`入口停止，未替代效果函数成功。再实际执行具名`MoleMole.ActorUtils.GetLocalAvatarConfigId/153E46950`，它取+284经真实`1517ABD80`进行实体查询，返回该实体+3F4的角色配置ID。

场景/实体和uint32字典的索引查询是明确受控夹具，底层真实查找包装、数组长度/类检查、消息reader、管理器加载与store及具名getter执行。它不证明入场消息同时生成实体、GUID与实体绑定正确或生产世界已存在。该关联仍须完整消息/实体通知另验。

10个限定图包含小ID、0x80000001、0xffffffff、两个标量独立换值、重复显式零、只发另一个标量、tag12错wire跳过，以及三个真实停止边界。非零输入均只选择tag12的ID；tag7采用不同值，不被误读为角色ID。重复零最后写0，另标量/错wire不选择实体。**零例直接调用管理器前缀，没有执行网络Handler在+30为0时的前置拒绝，不是允许生产零ID。**

已有当前实体时，先停真实旧实体清理`14ADF3180`，保留原ID，不声称切人链已完成；null typed输入停`1404C2BE0`；IFix启用夹具停`14CD6872E`，均没有新store。3个拒绝分别为非法tag、截断varint及非空list研究范围拒绝，均是明确输入/空envelope模型错误，不冒称完整客户端throw。

[f2a Hero消费者](CLIENT_72_AVATAR_CONTROL.md)的root tag3/u64与这里的scene+284/u32仍是不同来源。本卡不批准`cur_avatar_guid`，也不恢复tag7的旧业务名；不改全局mapping、旧Proto、producer或ready。

## 复现、失败及实际测试

6项新增守卫、5项f2a及10项f1合21项实际通过，失败/错误/跳过0。两个正式新进程完整/摘要逐字节一致，SHA分别`0c6a06d8952e336ed3927e05f62f54c5b60002df05b66e57f3b4f07116e91788`与`550fcdd642132598256aca41bc79eec89bdfb7009ad2d22ab7a82409484644d1`。共享摘要为[scene-entry-identity-observations.json](../protocol/cn-7.2/scene-entry-identity-observations.json)。原始轨迹在`local/migration72/evidence/scene-entry-identity-*-final{,2}.json`，日志`logs/scene-entry-identity-final-*`。

早期失败保留：仅标量探针通过后，完整外层扫描先遇到未建模class usage的+0xc7读取；重新核五个实际类型来源并添加显式托管class模型后通过。消费探针的字典数组长度及研究返回地址未初始化也分别导致bounds/未映射读取；补齐真实布局所要求的长度和已用返回哨后重跑，未更改客户端指令或替代原生业务分支。见`logs/scene-entry-identity-run{1,2,3}.log`及`analysis/d02-r1f2-info-consumer-probe*.log`。较早报告仅夹具名不同，最终报告使用`other-scalar-only`避免暗示tag7业务已批准。

```powershell
python tools/client72_scene_entry_identity.py --client $env:GAME_PATH --output local/migration72/evidence/scene-entry-identity-native-final.json --observations local/migration72/evidence/scene-entry-identity-summary-final.json
python -m unittest tools.test_client72_scene_entry_identity tools.test_client72_avatar_control tools.test_client72_avatar_contract -v
```

没有修改生产、Proto、配置或资源，无重启/部署。下一步补34208的非空三子图、Team/能力块及实体/GUID关联、网络完整Handler/发送顺序；R1/R4/D-02保持进行中。
