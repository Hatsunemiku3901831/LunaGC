# C-01 / R4 有限网络边界

本卡只覆盖国服7.2已有帧合同的Java边界和公开Handler注册入口。未知Union子消息、候选CmdID/同值别名、P0必要消息集合及完整客户端初始化仍归原卡；不由本卡批准。证据0审核已由主线完成并提交`c34808f`，真实入口红测已保留；主线逐差异审核最小生产修复，43项网络绿测全部通过，关联回归及构建待主线收口。

## 来源与适用范围

- [既有网络研究](CLIENT_72_NETWORK.md) A-04d5r/s及`protocol/cn-7.2/frame-observations.json`、`frame-reader-observations.json`：固定三输入、目标原生封包与收帧入口、独立字节表达式一致。帧为`4567 | command:u16be | headerLength:u16be | bodyLength:u32be | header | body | 89AB`；已有命令32767/32768/65535及65536字节body样本。数值域验证不增加业务CmdID映射。
- 原生收帧记录的command0也是传输层合法数值；`BasePacket.build`是纯编码器，保留0的编码。`GameSession.send`原有非正数sentinel/FanOut分支及业务策略不由本卡改写。
- 包头大于32767的范围来自帧的uint16声明，不声称任意65535字节都构成有效PacketHead；Java传输测试将它们按原字节交给受控Handler，不做业务Proto语义判断。
- Java入口为`GameSession.handleReceive`、`GameSession.send`事件替包后实际`BasePacket.build`、`GameServerPacketHandler.registerPacketHandler`。`handleReceive`旧代码将opcode/headerLength读作signed short，且先分配再检查可读窗口；`BasePacket`旧代码默默截断uint16；注册旧代码put覆盖，并catch Exception后只写日志。
- [表面盘点](CLIENT_72_PROTOCOL_INVENTORY.md)原受审范围没有正号Handler冲突，公开注册没有别名/同类重复特例，源码也没有重复注册调用。相同数值的常量不构成批准的Handler别名；本卡要求所有重复注册明确拒绝，保留首个实例和其NoResponseExpected策略。

## 测试设计与失败出口

`src/test/java/io/grasscutter/Protocol72FrameBoundaryTest.java`以dispatch-only的GameServer作无监听壳，安装只含显式受控Handler的注册器。真实`handleReceive → getPacketHandler().handle → handler`不替换；不加载资源、不创建Player、不访问DB、不启动游戏服务。帧oracle使用独立ByteBuffer字节构造，不由待测`BasePacket`生成。

覆盖合法32767/32768/65535命令，独立非零/u64包头原字节，header长32767/32768/65535，body长65536，合法聚合逐帧顺序，错误聚合不重同步。入口错误样本含短固定头、截断header/body/尾、负body长度、实际12字节但声明1MiB、高位header无内容、错误首/尾标记。所有错误都不得调用Handler；长度错误还须直接拒绝，不能靠caught NegativeArraySizeException/IndexOutOfBoundsException等替代验证。红测只分配有界1MiB；修复后追加两条只跑绿测的Integer.MAX_VALUE声明，分别含0/65535字节header声明，覆盖有符号int最大值与合计长度int溢出。

发送测试执行真实SendPacketEvent及替包、真实send和受控tunnel；合法替包逐字节匹配，最终opcode越界/超长header不能写入tunnel且计数不增。纯build测试另断言uint16域外opcode和65536字节header抛IllegalArgumentException，命令0及高位合法值不截断。

`src/test/java/io/grasscutter/Protocol72HandlerRegistrationTest.java`直接调用公开注册并实际dispatch，覆盖单注册、不同类/同类重复、两个方向的NoResponseExpected保留、超界正号拒绝、disabled/非正/无注解继续不注册。冲突异常必须到达调用者，消息含数字和双方完整类名，首个实例和策略保持。

修复后另加真实生产全扫描注册测试：`new GameServerPacketHandler(PacketHandler.class)`不启动监听，逐个核对本次反射发现的启用正号类、实际注册实例和NoResponseExpected。本次实际发现324类、启用227类、注册227类、冲突0；未自动批准常量同值别名。若后续遇旧表正号碰撞或构造被吞造成缺失，测试保留失败并列为R4阻塞，不改旧号或别名求绿；本次完整类扫描成功也只证明当前发现范围的注册，不能替代协议兼容或客户端验收。

## 复现与执行状态

由主线串行执行Gradle，代理不运行共享构建、不做Git操作：

```powershell
.\gradlew.bat --no-daemon test -PskipHandbook=1 --console=plain `
  --tests io.grasscutter.Protocol72FrameBoundaryTest `
  --tests io.grasscutter.Protocol72HandlerRegistrationTest
```

主线已在独立cwd执行真实红测：Frame 34项/20失败、注册6项/4失败，合计40项/24失败，errors/skipped均0，失败全为预期断言。原始XML/stdout在`local/migration72/offline-sol-terra/network-red/test-results/`；没有把MAX声明交给旧实现分配。初始源码/测试/来源SHA-256在`local/migration72/offline-sol-terra/network-boundary/test-plan-inputs.json`，生产修复前后源码及绿测前测试的精确差异/hash见同目录`production-fix-inputs.json`，其中尚未更新的greenExecuted=false为绿测前状态，不覆盖后述实际XML结论。

最小修复只改三个生产文件：

- `GameSession`：opcode/headerLength改为无符号读取，以long加法一次核对完整header+body+尾窗口；负payload和实际窗口不足都在数组分配前拒绝。其余合法逐帧分发、尾校验及密钥路径保留。
- `BasePacket`：在最终build入口拒绝域外opcode和超长header，抛IllegalArgumentException；合法uint16逐字节编码保持。SendPacketEvent替包后的实际包同样经过这一处验证。
- `GameServerPacketHandler`：公开入口拒绝超界正号及所有重复注册；冲突信息含数字和双方类名，检查在反射构造catch之外。构造失败仍只捕获ReflectiveOperationException并记录，首个Handler及NoResponseExpected策略不受失败注册影响。

主线实际绿测为Frame 36项、注册7项，合计43项全部通过，failures/errors/skipped均0。相对红测增加的3项为两条MAX声明和一次真实生产全扫描；原40项全部重跑通过，红绿存在重叠，不能将40+43相加为83项独立验收。绿测XML及扫描stdout在`local/migration72/offline-sol-terra/network-green/test-results/`，同目录其他任务的XML不计入本卡43项。

当前状态为**C-01/R4本卡有限边界红绿通过，关联回归、源码review精确hash同步、Windows portable构建及class/JAR核对由主线继续收口**。未知业务/映射/Union、全域C-01、R4完整汇合与D-02均不关闭；没有客户端验收或运行部署结果。

构建与部署：生产修复须由主线完成受影响回归、Windows portable构建及class/JAR核对；完整P0/SDK/资源合同不足及本阶段排除部署/实机，记录未部署原因，不启动猜测协议服务。

主线阶段收尾已完成：50套875关联Java、120研究和9生成检查通过；对应来源review精确同步并保持三层/required/runtime，旧拒绝与双recover保留。生产提交`8c1fb85`实际portable构建27.5秒成功，JAR `f475dbf6d91bac591a9b4346726b935171764b446b9ab64e6a8734b44c8cade8`，8变化class匹配编译、46296不变，无新增/删除。server-only status为stopped/Healthy=false，未部署/未客户端验收，运行PID/JAR无对象；完整C-01和R4不晋级。证据在`local/migration72/offline-sol-terra/portable-build/`，详细交接见HANDOFF2。
