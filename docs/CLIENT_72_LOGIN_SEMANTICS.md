# 7.2 登录身份、字段来源与枚举

输入固定为 extract_client72.HASHES 的三份目标文件。这里的 verified 只用于明确列出的静态/离线证据，不代表真实登录、IFix 或运行时 ABI 已验收。原始反汇编和 trace 留在 local/migration72/analysis、evidence。

## 目标字符串与常量（A-04d4a）

已实现 extract_client72_literals、extract_client72_enums 和 check_client72_metadata_values；未使用旧版解码参数。共享结果为 protocol/cn-7.2/login-literal-observations.json、enum-observations.json。

- 字符串：14052D397–445 解码前缀表、数据区；14052F9F0 解码字节。注册器 1402F846C–473 将 usage registration 指向 1428A5460；其 +58 是 literal slot 数组，+4 加 9E6F867A 得到 87,252 个 slot。slot 数量不冒充源字符串数量。140544469–47D 恢复全局初始化 group 314557；逐条解码该 group 的 source index 与 destination slot，分别确认四条标记的落点，未假设二者总相等。
- 前缀：table = 210 + u32(header[1E4] + 9DEB89A5)，data = 210 + (header[124] XOR 36C4F639)。index 经 84FD、44A4DE7C、2A04E474、右移23、C6A574CC 混合后与表字运算，细节见工具。字节 key 和逐8字节步进均来自 14052FA33–FBBA。
- 枚举：System.Enum.GetEnumValuesAndNames → 140524350 → 140524190。field-default 表起点 210 + u32(header[1D8] + DE66F77A)，长度 header[1F4] XOR 159A5178 = 915,840；每条12字节为 typeIndex/dataIndex/fieldIndex。常量数据位于 210 + u32(header[1CC] + 9C0BD319)。按元数据 value__ 的底层类型读取，拒绝缺值、重复field、越界和非literal成员；不按声明次序编造数值，允许同值别名。

四个协议相关枚举实际值：

| 类型 | 原始符号和值 | 范围 |
| --- | --- | --- |
| DLEEHOLOJKM | None=0, BeyondPlay=1, BeyondEdit=2, HallMain=3, HallEdit=4, BackPlayerWorld=5, BackPlayerWorldDefault=6, JoinPlayerWorld=7, JoinHome=8 | 嵌套字段的目标枚举，业务字段名另验 |
| KBOEJJBMALM | GameProcessNormal=0, GameProcessUgc=1, GameProcessHall=2 | 嵌套字段的目标枚举 |
| HGPBEGGCNED | None=0, BeyondWorldInfo=11, HallWorldInfo=12, PlayerWorldInfo=13, PlayerWorldAnchorPointInfo=14 | KEDIEGLJIMC 的 oneof 判别值，与已执行 writer/reader 的 case 值一致 |
| JBJNJEAMIEL | None=0, SpecificGameserver=11, MinloadGameserver=12 | KCIAPDNPNHE 的 oneof 判别值 |

另提取 UnityEngine.RuntimePlatform 和云平台 PlatformType，共6类型70符号；它们是来源域，不能直接当成网络 platform_type 的枚举。Miscs.GetServerPlatformType 含转换表，必须独立确认转换。

实际验证：5项工具单元测试通过，包含目标密文字向量、尾块、索引/数据边界、usage来源与目标分离、重复常量拒绝、有符号值和同值别名。目标原生字节解码4项、目标原生常量读取14项通过，覆盖每个枚举的两端和 RuntimePlatform 的同值别名。原生枚举检查使用完整原始metadata，但 class/field/type 对象为合成；字符串只模拟栈探测和解码后的 UTF-8 消费者，不执行托管分配。日志是受控离线结果，非实机日志。

```powershell
python tools/extract_client72_literals.py --client $env:GAME_PATH --output protocol/cn-7.2/login-literal-observations.json
python tools/extract_client72_enums.py --client $env:GAME_PATH --output protocol/cn-7.2/enum-observations.json
python tools/check_client72_metadata_values.py --client $env:GAME_PATH --output local/migration72/evidence/metadata-values-native.json
python -m unittest tools.test_client72_metadata_values -v
```

## 待闭合的业务关系

四条标记分别为 OnPlayerLoginRsp、OnGetPlayerTokenRsp 及 seed 解密失败分支。需将 usage cell、实际引用、原生分派、方法参数和已有wire连接；仅字符串相似不能提升消息或字段。网络管理器 151221D70 / 1512160D0 是共同配置处理包装，不是两个上游完整响应Handler。后续条目将保留这一层级区别。
