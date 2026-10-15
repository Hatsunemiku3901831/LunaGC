# Windows Java 启动与端口检查

本机启动服务时，JDK 17 的 `Selector.open()` 在默认临时目录下抛出
`Unable to establish loopback connection`，底层为 Unix-domain socket 的
`Invalid argument: connect`。Netty 因而无法创建事件循环，客户端报网络错误 4201。
使用项目内的临时套接字目录后，同一 JDK 和 JAR 成功启动。

`tools/serve.ps1` 现在为服务进程传入 `jdk.net.unixdomain.tmpdir`，
默认创建当前工作区的 `debug/java-sockets`；深层目录可在 `.env.local` 中设置
`LUNAGC_JAVA_SOCKET_DIR`，选择较短的目录。路径会引用并支持空格，
无需设置系统或用户级 `JAVA_TOOL_OPTIONS`。

测试还复现了另一个启动误报：HTTP 端口上只有 UDP 服务时，
旧脚本也报告“server already running”，实际没有启动 Java。
现在 HTTP 的启动、状态、等待及停止检查只读取 TCP，游戏状态只读取 UDP。

## 验证

`tools/test_serve.py` 在独立临时工作区复制实际启动脚本，
编译一个真正创建 Java Selector 并监听 TCP 的最小服务，使用明确指定的隔离 Mongo 容器。
它不会运行游戏服务、连接玩家数据库或停止已有的主服务。
测试前清除子进程的全局 Java VM 选项，覆盖：

1. 默认工作区目录与带空格的 JAR 路径。
2. 显式指定、带空格的套接字目录。
3. 同端口已有 UDP 监听时，仍真正启动 TCP 服务。

原脚本的 Selector 失败与 UDP 误报均先保留了失败结果。
修复后，以上 **3 项真实 Windows 启动测试通过，无失败或跳过**。
测试只清理自身工作区，并核对其 Java 进程的 PID、JAR 命令行和端口归属后停止。
这些是 Windows 服务启动测试；Gradle 构建及完整客户端登录分别验证。
