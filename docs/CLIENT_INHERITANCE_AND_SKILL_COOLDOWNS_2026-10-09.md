# 客户端继承关系与普通技能冷却

## 提取工具

`tools/extract_client_type_names.py` 新增 `--include-parents`，按当前客户端核实的
类型定义父引用偏移 `+0x2c` 和异或值 `0x5fcd8771`，读取注册类型表。
普通对象父类型关联到 `System.Object`，类父类型保留定义索引和名称。
泛型父类型保留原始引用；启用 `--include-generic-types` 后读取已校验的 startup 元数据，
解析父类型及泛型参数。注册表范围、定义索引、非法类型和继承环都会拒绝导出。

该选项可单独运行，也可与字段、方法、包号选项组合。只提取父类型时，泛型选项
无需再开启字段选项。客户端路径仍由调用方传入，文件摘要继续严格匹配固定分析配置。

```powershell
python tools/extract_client_type_names.py 'C:\Users\InvokerBot\AppData\Local\hoyo\hk4e\versions\current' --include-parents --include-generic-types --output local/activity-research/client-parents.json

python -m unittest discover -s tools -p test_extract_client_type_names.py -q
```

当前 88,904 个类型中，87,244 条父引用全部解析，其中 1,565 条为泛型父类型，
1,660 个类型没有父引用。独立继承关系提取约 2 秒，可用于快速找到具体执行类。
以下九条关系通过当前客户端实际导出结果检查：

| 类型 | 父类型 |
| --- | --- |
| `System.String`、`System.Exception`、`System.ValueType` | `System.Object` |
| `System.ArgumentNullException` | `System.ArgumentException` |
| `System.Enum`、`System.Int32` | `System.ValueType` |
| `ALNBAECOJGA` | `IEKHEIPJBDJ` |
| `ILFHLENBCND`、`KFOFIMGPIOK` | `OPKEMNOJJEP` |

14 项检查覆盖对象父类型、泛型引用、定义范围、非法引用、环、截断及已有成员保留。
完整组合提取仍得到 440,172 个字段、733,442 个方法及 4,923 个包号返回类型，
其中包号有 4,919 个不同值；字段中的 18,822 个泛型实例继续解析。
与原有 88,904 条类型记录逐一比较，原有字段、方法和其他已有值全部保留。
输出只说明元数据关系；没有据此猜测完整协议字段或替换包号。

## 普通技能冷却的新证据

公开参考库只有 `ModifySkillCD` 类型声明。当前资源的九条配置均对应
`shareCDID = 0` 的普通技能，因此必须接入普通技能路径。

| 配置 | 技能 ID | 增量或比例 |
| --- | --- | --- |
| `Ambor_Talent_211` | 10032 | 比例 -0.2 |
| `Barbara_Constellation_2` | 10071 | 比例 -0.15 |
| `Bennett_PermanentSkill_1` | 10322 | 增量 -1 秒 |
| `Kazuha_Constellation_1` | 10472、10473 | 比例 -0.1 |
| `Player_Rock_PermanentSkill_1` | 10077 | 增量 -2 秒 |
| `Razor_PermanentSkill_1` | 10202 | 比例 -0.18 |
| `Tartaglia_Constellation_1` | 10332 | 比例 -0.2 |
| `Yunjin_Constellation_1` | 10642 | 比例 -0.18 |

这九条资源中，比例导出字段为 `__exp_healTag`，需要按冷却配置语义处理。
当前客户端限定地址核对得到：

| 地址或类型 | 核对结果 |
| --- | --- |
| `DJNPABLIJEO` 初始化 `0x1495b0df0` | 技能 ID 在 `+0x30`，两项浮点运算数在 `+0x34` 与 `+0x38` |
| `0x1495b0ce0`／`0x1495b0b50` | 启用和移除时分别添加原运算数或其相反数 |
| `0x14b56ca30` | 通过实际技能 ID 查找技能对象，转入其冷却参数修改 |
| `OPKEMNOJJEP`，`0x1517a8320` | 分别求和增量、比例，结果为 `(基础值 + 增量) × (1 + 比例)` |
| `0x1517a8414`，常量 `0x1421ab164` | 比例计算中的浮点常量 1 |
| `ILFHLENBCND`／`KFOFIMGPIOK` | 普通队列与共享组技能属于两个不同的派生类 |
| `0x14eedcc30` | 普通技能状态恢复读取次数、时长队列与已流逝时长 |
| `0x14eedcd46`、`0x14eedcec6`，常量 `0x1421ab170` | 状态时长从毫秒除以 1000 转为秒 |

恢复逻辑按队列长度计算可用次数，并按首项时长减去已流逝时长恢复剩余冷却。
这些证据纠正了将九条修改直接放进共享冷却模块的方案。

## 继续接入的范围

本批完成继承关系工具和上述客户端核对。九条冷却修改的服务器执行路径仍待接入，
普通技能开始、运行时冷却动作、次数队列及状态恢复需要一起核对，避免用静态 Excel
时长覆盖客户端正在使用的动态值。当前资源另有 145 个 `ModifyAvatarSkillCD` 动作，
属于运行时修改，不能与九条天赋配置混为一次无条件设置。

证据保存在本地忽略目录 `debug/talent-cooldown-20261009/`。
本批只有提取工具、检查和记录变化，日常服务运行包沿用上一批已验证产物。
