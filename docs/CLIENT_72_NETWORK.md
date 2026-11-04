# 7.2 网络调用和登录协议研究

2026-11-05；输入 SHA-256 见 [提取记录](CLIENT_72_EXTRACTION.md)。所有 VA 只对应该输入。以下区分静态代码证据、业务命名和客户端验收；没有执行目标客户端登录。

## 静态包号调用链

`NCKOJEOGIIC`（类型67861）的零参虚方法 `AMPEGOHOBJM` 是槽22，基类返回0。派生类型的严格 `mov ax,imm16; ret` 返回值现可确认是静态网络 command 值，依据如下：

| 环节 | 目标指令位置和作用 |
| --- | --- |
| 虚方法槽 | 0x14051E626–0x14051E667解码flags、slot、参数数。flags=`u16((method[10] XOR DC05)-key)`；slot=`u16(method[12]-key+58B5)`。 |
| 发送入口一 | AKFCDHJBCHD.CBENCLHEKEH，0x1472CE538读取消息，0x1472CE55D调用`[class+180]`，0x1472CE56C写入包对象`+22`的word。 |
| 发送入口二 | BHBBNHFEPPB.CBENCLHEKEH，0x147E04023同样调用`[class+180]`，0x147E04032写包对象`+22`。 |
| 封包 | FBNMCCIAMBP（22292）方法0x14A447E00：0x14A447EB9写4567前导，0x14A447EF2从`+22`读command，经0x1483D30D0转换后写下一word。转换函数0x1483D311F/312B依次写高/低字节，即网络序。 |
| 解包 | 0x14A446CC0检查至少12字节；0x14A446E14–E25验证前导，0x14A446E4D–E56读取下一word到`+22`；0x14A446FAE–FC1将其传入消息工厂0x151A853D0。 |

运行类的虚表**代码指针每槽8字节**，起点+D0，因此槽22在+180；MethodInfo数组另按vtableCount接续，不能套用代码/MethodInfo交错16字节的旧假设。此布局由目标调用指令验证，仅适用于本哈希。

工具 `extract_client72_commands.py` 重新读取三份固定输入、恢复结构、校验基类/父链/虚槽/非static/零参数与严格常量代码后输出 [commands.json](../protocol/cn-7.2/commands.json)。方法flags/slot测试使用4组实际目标记录字，不通过逆向编码器自编自解。

结果：4,012个类型，4,007个不同command值。1004、1017、1027、1028、2002各有两种类型，保留 `ambiguous-domain-or-alias`，未自动合并。整个表的业务名称、网络域及运行时热修复覆盖仍未核实；**不能直接生成服务端 PacketOpcodes**。静态确认类型绑定不等于确认业务映射。

## 登录候选与实际调用点

| 业务候选 | 目标类型 / 索引 | 静态 CmdID | 证据与状态 |
| --- | --- | --- | --- |
| GetPlayerTokenReq | NJAALDCMHEP / 82491 | 24604 | 与旧参考实例字段形状一致；Clone的自类型单元1458C5500在网络管理器151220013实际分配，15121FF70起填充平台/SDK相关值；业务命名仍candidate。 |
| GetPlayerTokenRsp | CLDBKDNJHFG / 37481 | 27808 | 字段形状唯一最近；尚缺响应回调独立命名证据，candidate。 |
| PlayerLoginReq | KOHBOMDDFCB / 76410 | 23172 | 旧参考形状最近但多一个bool；自类型单元1458C3DF0在1512137BE分配，151213730起填充客户端设备/版本相关值；candidate。 |
| PlayerLoginRsp | NDENMDAIAMC / 40503 | 20835 | 字段形状唯一最近；回调和新增语义未确认，candidate。 |
| PingReq | LMINJCCKNDD / 64405 | 26605 | 自类型单元1458C39F0；15120CAC0和15120D8A0构造，15121EC30及15120F4CB周期调用；时间/帧率形状及内存采集共同支持心跳用途，业务名保持candidate至响应路径闭合。 |
| PingRsp | 未确定 | 未确定 | 有80个相同基础字段形状候选；未凭3个标量选择。已尝试Clone类型单元与网络管理器引用相交，无直接命中；下一步追踪泛型回调签名/注册。 |
| PacketHead | LOPNMMJPFAJ / 27182 | 无（embedded） | 包对象+18所持有类型，封包写入header区；直接结构与读写证据，非独立command。 |

旧7.1的名称/类型对应仅用于排列研究优先级，未作为7.2证据。候选结构比较报告、类型单元交叉引用、反汇编在 `local/migration72/analysis/`。元数据不会保留可直接信任的业务名称。

## 当前阻塞与可继续项

TokenReq.WriteTo（1500C2660→15A4DCF40）、TokenRsp.Merge（148B7EA10→15A4DBA53）、LoginReq.WriteTo（152EEB470→15A4E48B5）、LoginRsp.Merge（14B89EC90→15A4DC5A8）入口均跳转到保护代码；线性反汇编无法恢复有效读写流程。未把后续填充字节当作指令或猜测tag。解除需恢复保护分派后的真实数据流，或在独立客户端运行中取得受控样本/读写观测。

可独立继续：未保护的PacketHead、PingReq完整静态wire；业务构造/回调类型关系、嵌套泛型参数；登录P0依赖闭包。运行时IFix分支、保护入口和真实网络序列仍需验证。
