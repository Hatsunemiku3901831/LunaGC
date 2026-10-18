# Compose 常驻运行与迁移验证（2026-10-18）

## 启动方式

```powershell
task serve          # 等同 task runtime：构建镜像、后台启动、等待健康检查
task serve:status   # 容器状态、健康检查和端口
task serve:logs     # 跟随日志；Ctrl-C 只退出日志查看
task serve:stop     # 仅停止游戏容器，保留 MongoDB 和数据卷
task runtime:build  # 只构建，不重启服务
```

`tools/runtime.ps1` 根据脚本位置定位项目，并指定 `lunagc` Compose 项目和配置文件。
构建失败不会继续启动。后台启动命令返回后，服务继续由 Docker 管理，
关闭终端或退出 Codex 不会停止容器。游戏容器启用 `init`，
游戏和 MongoDB 的重启策略均为 `unless-stopped`。

Docker Desktop 需要保持运行。显式执行停止命令后，Docker 不会自行启动被停止的服务；
再次运行 `task serve` 即可启动。游戏服务的退出宽限时间为 60 秒。

镜像构建会重新生成 JAR，并复制 `resources/` 子模块中的完整资源；
运行时没有绑定本机资源目录。修改资源后需重新构建镜像。
本地 Java 开发入口为 `task dev:server` 或 `task dev`，切换前先停止另一种运行方式。

## 本次迁移

- 游戏服务：`lunagc-server-1`，镜像 `lunagc-server:latest`。
- MongoDB：保留原容器 `lunagc-mongo-1`，没有重建或替换数据库。
- 存档数据库卷：`lunagc_mongo-data`。
- 配置及文件状态卷：`lunagc_lunagc-state`，挂载到 `/var/lib/lunagc`。
- HTTP：8088；本机 GM 入口：`http://localhost/gm`；游戏端口：UDP 22101。

迁移前先备份旧状态卷和 `grasscutter` 数据库，再将当前开发目录中的
`config.json`、`data/`、现有插件及密钥文件导入原状态卷，保留当前商城、卡池和活动配置。
资源路径、MongoDB 连接和公布地址由 Compose 环境设置。
此次导入的 10 个文件在重建游戏容器后逐个核对 SHA-256，均与源文件一致。

备份与本地验证记录位于忽略的 `debug/compose-runtime-20261018/`：

- `previous-state.tar`：旧运行状态卷备份。
- `host-state.tar`：本次导入的当前配置与数据。
- `grasscutter-before.archive`：迁移前数据库备份。
- `database-before.json`、`database-after-recreation.json`：数据库校验记录。
- `first-start-proof.json`、`recreation-proof.json`、`migration-result.json`：运行验证。

这些文件可能含私有配置，不提交到 Git。不要使用 `docker compose down -v` 删除持久卷。

## 验证结果

- 新增启动器回归测试，补齐实现后 **4 项通过**：后台启动与健康等待、
  构建失败中止、仅停止游戏服务、查询包含已停止服务。
- Compose 镜像从当前源码构建成功，包含资源；资源 Git LFS 指针检查通过。
- 镜像内 JAR 的 **17,386 个项目类**与上一份已验证构建逐字节相同，
  包括合成页面初始化修复；这次没有改动游戏业务代码。
- `task serve` 启动进程已结束后，两个容器继续运行且健康。
- 随后在无在线玩家时强制重建游戏容器，新容器恢复健康，MongoDB 容器 ID 保持不变。
- 重建后 `/status/server` 健康，`http://localhost/gm` 返回 HTTP 200，
  GM 命令接口可读取 75 条命令。
- 数据库保留 **11 个账号、2 个角色**；18 个集合的完整数据库校验值在启动、
  重建前后相同：`397bfebda518f25ef5fe3d30e2256778`。

新镜像内 JAR SHA-256：
`a6dccecce925aabd300abc1871c459f5ed0460d4588ab54a49783895985004ea`。

以上属于部署、文件和服务接口验证；本次没有操作游戏窗口或重复进行客户端玩法验收，
也没有为验证重启整台电脑或 Docker 引擎。

## 遗留容器清理

仅移除了两个确认已停止的项目临时容器：

- `lunagc-aster-mid-20261011-build`
- `lunagc-aster-mid-20261011-mongo`

旧构建容器的源码、构建包和测试结果已保存在本次备份目录。
清理没有使用强制移除、卷删除或全局 prune；旧容器关联的 3 个卷仍保留。
后续常规启动使用 Compose 构建及固定的服务容器，迁移所用一次性容器使用 `--rm` 自动回收。
