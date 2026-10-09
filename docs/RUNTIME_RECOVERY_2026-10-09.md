# 当前运行包快照与恢复镜像

## 使用方法

```powershell
task runtime:snapshot
task runtime:snapshot:build SNAPSHOT_PATH="debug/runtime-snapshots/<首次输出的目录>"
task runtime:snapshot:status SNAPSHOT_PATH="debug/runtime-snapshots/<首次输出的目录>"
```

`tools/runtime_snapshot.ps1` 根据脚本位置找到项目，再读取 Compose 的 `server` 容器。
直接调用脚本时也可以指定 `-Container`；没有写死项目或客户端目录。
已有快照目录拒绝覆盖。第一次构建核对文件摘要及镜像启动配置；再次构建可复用
已核对且 ID 不变的恢复镜像，不重新导出、编译或下载资源。

快照保存实际安装的 JAR、文件系统和启动配置。数据库及挂载状态卷不包含在
[Docker 容器导出](https://docs.docker.com/reference/cli/docker/container/export/) 中。
因此它提供程序和内置资源的恢复点，数据库及 `lunagc-state` 卷仍需要单独备份。
私有配置和构建日志只保存在被忽略的 `debug/` 下。

## 本次已经验证的产物

| 项目 | 产物 |
| --- | --- |
| 原运行包 SHA-256 | `f43c64fff62916a7df406698a63bab8e3cec27d747af0976087f14df24ee84cc` |
| 新运行包 SHA-256 | `ee3d1c889367d43a852b162e9803e1ff9aa9517c357072da63458cd62fef154a` |
| 恢复镜像 | `lunagc-server:recovery-20261009` |
| 恢复镜像 ID | `sha256:2f7a47e36f2c390699dc72935f7cae9863676e19fb262e59066e38f0653ba859` |
| 候选镜像 | `lunagc-server:runtime-candidate-20261009` |
| 候选镜像 ID | `sha256:e8a6b22be5197d75676c4514a448a1393c25cff07f2da5b0f2ffb567aedfc4c1` |

当前原镜像的本地元数据已缺失，直接重新标记或提交容器曾失败。
本次从仍在运行的容器导出 4,162,359,296 字节文件系统，并重建恢复镜像。
环境、用户、工作目录、入口、参数、端口声明、卷声明、健康检查与停止信号
均与捕获配置核对；没有宣称镜像历史和全部标签与原镜像逐字节一致。
归档中的 JAR 与单独保存的原 JAR 一致，没有导出挂载状态卷的内容。

恢复镜像在隔离数据库和临时状态目录上完整启动，HTTP、游戏 UDP 端口、GM 页面
与容器健康检查通过。候选镜像只替换新 JAR，复用恢复镜像中的原资源和 Java，
同样完整启动，扩展健康检查、GM 页面和包内健康探针通过，实际 JAR 摘要一致。
两次临时运行容器已删除，镜像与证据保留。

这验证了新包能用日常服务的原资源启动。完整客户端施法、商城 SDK、历史活动
和千星奇域流程仍需各自验收，启动检查不替代游戏内测试。

## 脚本与部署状态

Windows PowerShell 5 的语法检查、状态读取和已验证镜像复用通过。
从其他工作目录调用捕获模式，能找到本项目 Compose 服务，并拒绝覆盖已有快照。
完整捕获与首次构建已按相同操作完成；最终脚本没有再次导出一份 4 GB 文件系统。

截至本批验证，日常服务器和 Mongo 的容器 ID、启动时间保持原样，两者健康，
有一名玩家在线。版本切换的时间选择仍待答复，尚未部署；原数据卷保持原样。

本批没有 Java 源码改动，复用第四十二批最终运行包及其验证：调整最后一处锁边界前
全量 1516 项／173 套件，调整后最终相关 537 项／29 套件通过，均无失败、错误或跳过。
未将最终包描述为再次完成全量测试。

本地证据：`debug/runtime-recovery-20261009/`、`debug/runtime-candidate-20261009/`。
已有恢复镜像可快速查看和复核：

```powershell
task runtime:snapshot:status SNAPSHOT_PATH="debug/runtime-recovery-20261009"
task runtime:snapshot:build SNAPSHOT_PATH="debug/runtime-recovery-20261009"
```
