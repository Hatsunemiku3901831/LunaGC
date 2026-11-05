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

另提取 UnityEngine.RuntimePlatform 和云平台 PlatformType；它们是来源域，不能直接当成网络 platform_type 的枚举。Miscs.GetServerPlatformType 含转换表，必须独立确认转换。随后补入 LoginReq 实际读取的语言来源 DCIIBLHGNIN，共7类型86符号；LanguageNone=0、LanguageEn=1、LanguageSc=2、LanguageTc=3，后续语言按目标常量表记录。语言字段实际 wire 仍为 uint32。

实际验证：5项工具单元测试通过，包含目标密文字向量、尾块、索引/数据边界、usage来源与目标分离、重复常量拒绝、有符号值和同值别名。目标原生字节解码4项、目标原生常量读取16项通过（最初6枚举检查点为14项），覆盖每个枚举的两端和 RuntimePlatform 的同值别名。所有76,320条default记录的字段/类型索引、重复和边界均检查；选定常量的存储类型与枚举底层类型交叉确认。原生枚举检查使用完整原始metadata，但 class/field/type 对象为合成；字符串只模拟栈探测和解码后的 UTF-8 消费者，不执行托管分配。日志是受控离线结果，非实机日志。

```powershell
python tools/extract_client72_literals.py --client $env:GAME_PATH --output protocol/cn-7.2/login-literal-observations.json
python tools/extract_client72_enums.py --client $env:GAME_PATH --output protocol/cn-7.2/enum-observations.json
python tools/check_client72_metadata_values.py --client $env:GAME_PATH --output local/migration72/evidence/metadata-values-native.json
python -m unittest tools.test_client72_metadata_values -v
```

## 响应身份（A-04d4b）

| 业务名 | 目标类型 / CmdID | 独立证据链 |
| --- | --- | --- |
| GetPlayerTokenRsp | CLDBKDNJHFG / 27808 | 1507C2A4E 注册；1507CC556 比较后在1507CD80B调用1507BE0E0；方法参数指向37481；getter槽22同值；保护路径15A4DE37D读取145B23FA8，对应目标literal46129 `[Login] OnGetPlayerTokenRsp: ` |
| PlayerLoginRsp | NDENMDAIAMC / 20835 | 1507C29E5 注册；1507CD613比较后在1507CE3DA调用1507B7BE0；方法参数指向40503；getter槽22同值；1507B7F21读取145B23D98，对应literal46063 `[Login] OnPlayerLoginRsp: ` |

trace_client72_login_semantics 每次从固定输入重新恢复两条参数签名、核对getter和注册参数/调用目标。原生分派以两个CmdID及0/65535作4组检查，Handler在该分派检查中只捕获接收指针。随后另行执行真实Handler的错误前缀：独立wire经真实保护reader写入对象，retcode=1/-1/INT32_MIN 各3组均到达各自命名诊断，格式化器只捕获数值。这6组不是完整错误处理执行。

纠正旧接手措辞：151221D70（Token）和1512160D0（Login）都取两个成员并将bool转发给151216140。前者取+48/+70，后者取+90/+58；4组原生参数检查验证bool的0/1透传。它们是共同配置处理包装，不是上游完整Handler，bool业务名称仍未知。当前两种**响应**业务名和CmdID可标静态verified；请求配对和真实客户端序列仍候选/待验。

## 字段来源闭合（B-02c1）

下表是已写入 mappings 的20条字段证据；同名的其他消息字段不自动晋级。对象偏移为十六进制。

| 类型 | 偏移 / tag | 稳定名称 | 目标来源/消费者 |
| --- | --- | --- | --- |
| TokenRsp | 104 / 14 | retcode | 真实reader → 保护Handler分支与命名错误诊断 |
| LoginRsp | E0 / 8 | retcode | 真实reader → Handler分支与命名错误诊断 |
| LoginRsp | B0 / 1255 | client_silence_md5 | GlobalVars.SILENCE_DATA_MD5 |
| LoginRsp | 18 / 437 | client_md5 | GlobalVars.DATA_MD5 |
| LoginRsp | 78 / 1792 | client_version_suffix | GlobalVars.DATA_VERSION_SUFFIX |
| LoginRsp | C0 / 744 | client_silence_version_suffix | GlobalVars.SILENCE_DATA_VERSION_SUFFIX |
| LoginReq | 50 / 213 | fp_id | Miscs.GetFPID |
| LoginReq | C8 / 9 | client_version | Miscs.GetUniversalGameVersion |
| LoginReq | 154 / 10 | platform_type | Miscs.GetServerPlatformType，转换后的值，wire是uint32 |
| LoginReq | C0 / 7 | device_name | Miscs.GetDeviceName |
| LoginReq | 48 / 2 | system_version | Miscs.GetSystemVersion |
| LoginReq | A0 / 15 | device_info | Miscs.GetDeviceInfo |
| LoginReq | 124 / 1931 | network_ip | Miscs.GetNetworkIP；未将其含义扩张为cloud_client_ip |
| LoginReq | 28 / 1785 | client_ip_str | Miscs.GetNetworkIPString |
| LoginReq | E0 / 552 | robot_check_output | Miscs.GetRobotCheckOutput；bytes，未套用旧版字段别名 |
| LoginReq | 20 / 345 | cps | Miscs.GetCPS |
| LoginReq | 40 / 891 | game_version | Miscs.GetGameVersion；与universal版本来源分开，不猜测checksum用途 |
| LoginReq | 60 / 1996 | client_version_hash | Miscs.versionHash backing field |
| LoginReq | 11C / 4 | language_type | PBNJKAMEJAI.GKJCGMHJLHM，类型DCIIBLHGNIN，直接复制，无转换 |
| LoginReq | 118 / 274 | client_data_version | GlobalVars.DATA_VERSION |

四个响应string使用独立wire输入，经过原生reader和Handler实际存储后，按指针及内容检查GlobalVars。原生执行到1507B7D41停止；stage bookkeeping和一个reset helper显式模拟，不代表后续初始化已运行。

请求运行151213730至151213AC6前缀。11个命名来源返回独立哨兵，3个静态来源放置独立值；两组输入覆盖非零、高uint32和ASCII/bytes，核对14处原生store，再交给完整原生Login writer检查独立tag和值。托管分配、环境来源和stage bookkeeping是模型，真实引用计数检查执行。原生writer需要非null空string/ByteString默认值；已修正研究夹具的部分对象null默认值，不把该夹具异常当成客户端缺陷。

当前总计17组原生语义夹具：4分派、6错误前缀、4共享包装、1响应全局消费、2请求来源到wire。工具和映射单元测试另行运行。详细trace在 evidence/login-semantics-trace.json；共享摘要 login-semantic-observations.json 不含本机指针和完整指令流。

```powershell
python tools/trace_client72_login_semantics.py --client $env:GAME_PATH --output local/migration72/evidence/login-semantics-trace.json --summary-output protocol/cn-7.2/login-semantic-observations.json
python -m unittest tools.test_client72_login_semantics tools.test_client72_metadata_values tools.test_validate_protocol72 -v
python tools/validate_protocol72.py
python tools/validate_protocol72.py --ready # 当前仍应退出1
```

## 剩余工作与模型边界

后续[会话研究](CLIENT_72_SESSION.md)已完成解密后seed片段、12组Java密钥对照与6组拒绝。此结果只补齐指定边界，不改变下面完整Handler/请求配对的未完成状态。

- 后续A-04d5b已从目标阶段枚举、构造self-class及原生getter确认GetPlayerTokenReq/24604、PlayerLoginReq/23172，见[会话研究](CLIENT_72_SESSION.md)。B-02c2另完成两个token字段。完整请求构造/发送、其他字段与实机仍待验证。
- Token handler 入口保护代码已可执行，探索成功分支在15A4DE262到达ByteString helper147CF3070。后续seed/密钥应用及状态初始化尚需明确对象和helper模型，不能将任意未建模调用默认返回成功。
- 本页最初来源检查以151213AC6为终点；后续A-04d5c已在指定环境执行至151214D20，新增token与3个SDK字段，Login仍有37个外层字段业务含义未确认。其余已知wire字段继续保持未知语义，见会话研究的实际环境范围。
- 共同配置包装151216140消费的字符串和NNKFJMDCCMB、bool具体语义仍未闭合。枚举原始符号/数值恢复不代表字段业务名称已确定。
- ASCII字符串、合成托管对象、环境getter返回值、无IFix前缀均为明确限制；没有执行新旧账号、客户端握手、进入世界或服务部署。本批仅离线工具、研究协议和文档修改，无需重启运行服务。
