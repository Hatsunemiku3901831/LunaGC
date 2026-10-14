# 车辆燃素通知的当前协议

旧入口使用未解析包号 -37，消息字段为 3／7。
经 SHA-256 校验的当前客户端类型 77150 `EMGJCNEPFLP` 在 `0x14a3f7ec0` 直接返回命令号 **7926**。
读取方法 `0x14a3f7ed0` 的对应分支：

| 读取 tag | 写入字段位置 | 当前语义 |
| --- | --- | --- |
| 0x6d | +0x1c，float | 燃素，字段 13 |
| 0x78 | +0x18，uint32 | 实体 ID，字段 15 |

通知改用已知 VehiclePhlogistonNotify 命令号；旧 Java／proto 名称保留为兼容入口，序列化字段切换到 13／15。
零燃素仍携带车辆 ID；燃素零值采用 protobuf 默认值，不凭空补充。

新增 3 项测试在旧实现上全部失败，修复后通过。最终车辆、燃素、队伍协议及龙附身相关 **125 项／7 套件**通过，零失败、错误及跳过；JAR 构建通过。
同时使用实际资源加载器检查 **1,444 份 Temp 能力文件**，解析无错误。配置记录数与非空 Default 能力数量分别计算，不当作玩法完成率。
源码／proto 宿主机与容器 SHA-256 一致；编译类与 JAR 字节一致，变化限定在包号、通知类及其生成协议类。
证据：`debug/aster-mid-20261011/batch222/vehicle-red.xml`、`vehicle-proto-native.log`、`vehicle-results/`、`vehicle-verification.json`、`resource-parse.log`。

本次配对了命令号 getter 与读取字段；完整原生收件处理、车辆画面及网络实机验收仍待完成。日常运行服务未替换。
