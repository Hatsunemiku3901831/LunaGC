# 队伍能量的 Lua 接口与 7.1 通知

## 已接入

`AddTeamEntityGlobalFloatValue(context, uid_list, key, delta)` 和
`SetTeamEntityGlobalFloatValue(context, uid_list, key, value)` 使用浮点数操作玩家的队伍全局值。
增量不会再被旧整数签名截断，赋值接口也不再只是返回成功的空实现。
`GetTeamAbilityFloatValue(context, uid, key)` 从同一个队伍能力值表读取最新值。

- 原始 `AsterBig.lua` 杀怪奖励会使用 `0.75`、`0.5` 等系数；提交能量时读取当前值，
  再增加其负数以清空。服务端先更新此值，再向对应玩家发送增量或赋值操作。
- UID 只在调用所在场景查找；每名选中的玩家接收自己的队伍实体通知。
  重复 UID 只处理一次，未在场景、尚无队伍或队伍生命周期失效的玩家跳过。
- 空 UID 数组是成功的无操作。正常调用返回 `0`，缺少场景或非法参数返回 `-1`。
  无法读取的队伍或值返回 `0`；无法反查的队伍实体返回 UID `0`。
- Lua 数组必须连续，且每个 UID 都是正整数；字符串、布尔值、带附加键或有空洞的数组
  在 Java 转换前拒绝。小数 UID 不会被截断后操作另一玩家。
- 空名称、非有限值和 float 溢出拒绝。每个队伍的增量和通知按顺序处理，
  同一接口并发增加能量不会丢失增量。多名玩家的操作不提供事务或回滚。
- 能力同步继续读取／更新同一张值表；本接口的更新保存在当前队伍实体内，
  不作为跨世界、断线后仍保留的活动进度或货币余额。

该功能是第三章多人挑战的依赖接口。场景战斗、原始组、能量提交交互、匹配、结算和
熄星精粹仍需继续接入；第三章保持未开放的状态，完整客户端游玩流程尚未验证。

## 原始接口语义

固定参考 [Havesten/hk4e-sources](https://github.com/Havesten/hk4e-sources/blob/d12e0ae8dbe6012aefca71128d4c13e48113b68d/data/jenkins/jenkins_hk4e/workspace/hk4e_3.4_dev/gameserver/src/script/script_lib.cpp)：
`GetTeamAbilityFloatValue` 读取指定 UID 的队伍动态能力值；Add 使用 float 增量与 `ADD`，
Set 使用 float 值与 `SET`。通知由找到的玩家自身发送，缺失玩家或队伍跳过。
旧实现依赖客户端随后报告动态值；这里先更新服务端现有能力值表，使连续 Lua 调用能读取
刚更新的值，客户端确认仍经过现有能力同步入口。

活动调用依据固定参考
[AsterBig.lua](https://github.com/Ahanlei123/2.8_live_data/blob/f25e3155e38f9dc8f647493bf23a935147caf59e/lua/common/AsterBig.lua)。
参考项目内同名整数空实现未直接采用。

## 当前客户端核对

对用户提供并确认的 7.1.0 客户端只读核对，使用实际可执行文件及
`YuanShen_Data/Native/Data/Metadata/global-metadata.dat`；客户端文件不进入仓库。

| 文件 | SHA-256 |
| --- | --- |
| YuanShen.exe | `7f89938da606c1281659607d464702cddb9a09a7d4320a196630f60a811ec38e` |
| global-metadata.dat | `469eccd43aa48fe7a2df4e1caf66fa1268a17f5a4a03fa209e1427e467cb0161` |

`OCPJGODBDKF`（类型 80676）的包号读取函数 `0x14e7735f0` 返回 **20882**。
读取函数 `0x14e773600` 和大小计算函数 `0x14e773450` 核对出的字段如下：

| 含义 | 字段 | 原始 tag | 混淆名称 | 对象偏移 |
| --- | --- | --- | --- | --- |
| delta | 1 / float | `0x0d` | POKNKHGLGGJ | `0x20` |
| key_hash | 5 / uint32 | `0x28` | LDKPLMFLLHC | `0x24` |
| update_type | 12 / enum | `0x60` | DAPIDJELDMH | `0x28` |
| entity_id | 13 / uint32 | `0x68` | HLHCLCHFIDK | `0x18` |
| value | 14 / float | `0x75` | OFMFIHJJEAN | `0x1c` |

实体、哈希和值的含义还通过 `PKEHHIHJEEA`（类型 82238、包号 1441）的相同成员名交叉核对。
其读取函数 `0x1487a1b90` 对应普通全局值通知的 key 7、entity 10、value 11。
`BKMDHFKFIEI`（类型 80678）的实际枚举默认值字段 399232–399234 为
`Invalue = 0`、`Add = 1`、`Set = 2`。

旧恢复描述符中的 update_type 11、key_hash 7、delta 14、value 15 已替换。
这些地址和对象偏移只适用于以上文件，不能作为其他版本的通用布局。
本地字段、枚举、反汇编和构建证据保存在 `debug/aster-mid-20261011/batch75/`。

## 验证范围

`TeamGlobalValueTest` 使用实际 LuaJ 接口、真实队伍实体及现有能力报告入口验证小数累加、
负数清空、赋值、玩家隔离、数组校验、失效队伍、离场玩家和并发增量。
`TeamGlobalValueProtocolTest` 独立构造上述 tag 的字节，检查五个字段的解析以及 ADD／SET
实际发送的字段、零值赋值和包号。相邻 Lua 时间轴、实体上下文、全局值能力和活动脚本
回归随本功能一起运行；这不是客户端多人挑战或掉线恢复的实机验证。
