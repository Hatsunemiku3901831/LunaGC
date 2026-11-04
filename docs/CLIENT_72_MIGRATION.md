# 7.2 迁移现状与本地运行基线

核查日期：2026-11-05。主仓库起点 `167d716`，补丁 `f2874103ab70afc56260ee12d1eaca96247933b2`，资源 `dac2a9d43332ef51879522cf39da2831b0ca0543`。当前服务端仍声明并实现 7.1；本次建立便携运行基线及迁移审计工具，不代表已完成 7.2 适配。

## 当前证据

| 项目 | 核查结果 |
| --- | --- |
| 目标客户端 | `config.ini` 声明国服 7.2.0；可执行文件和元数据与已验证的 7.1 提取器配置均不同 |
| 协议 | `src/main/proto` 有 2803 个源文件；`PacketOpcodes` 注释及登录/入场测试仍固定 7.1；427 个负命令号常量尚未解决 |
| 版本准入 | `RegionHandler` 检查主、次版本，7.1 服务端会拒绝 7.2 |
| 资源 | 当前资源 README 明确以 7.1 为中心；未提供可验证的 7.2 服务端资源表 |
| 提取工具 | `extract_client_type_names.py` 校验三份文件哈希，并包含 7.1 专用解码参数与地址；不能跳过哈希验证后套用到 7.2 |
| 补丁 | 14 个已纳入审计的静态特征中，目标客户端匹配 7 个，未匹配 7 个；命中不等于回调 ABI、字段偏移或运行行为已验证 |
| 客户端完整性 | 根目录存在 `BeyondAssets_CN_7.2.0.49085326.zip_tmp`；尚未验证整份安装完成，不能单凭此文件断言客户端损坏 |

未匹配特征：`SDK_GET_REQUEST`、`SDK_POST_REQUEST`、`SDK_PRODUCT_RESPONSE`、`SDK_PRICE_CACHE_RESULT`、`SDK_PRICE_QUERY`、免费充值 `RESPONSE` 和 `FINISH_WAIT`。这是当前补丁对所提供文件的静态扫描结果，不能代替真实加载后的验证，也不能把可选商城功能问题当成所有登录失败的原因。

目前没有目标版本的 Proto、CmdID/名称映射或完整资源表。已有 7.1 回归测试只能保护迁移前行为，不能证明 7.2 的字段编号、类型和嵌套消息兼容。

## 便携运行

按 [部署手册](CODEX_ONE_CLICK_DEPLOY.md) 使用当前 checkout 的 `Start-GS.cmd`、`Stop-GS.cmd`、`GS-Status.cmd`、`Play-GS.cmd`。开发和验证期间保留匹配的 7.1 客户端配置；只读审计 7.2 使用命令行参数，不需要把 `GAME_PATH` 改为 7.2。

复制工作区后，先备份配置和存档，检查 `.env/.env.local` 的资源路径以及 `config.json` 的 `folderStructure.resources/data`。便携入口现拒绝跨工作区的数据路径和非本机便携 MongoDB 地址，避免启动新 Fork 却读取旧目录；不会自动移动或覆盖存档。需要外部资源时同时设置 `LUNAGC_RESOURCES_DIR` 与配置字段。

默认 JAR 名称从 `build.gradle` 派生；便携 build 支持 `LUNAGC_JAR` 简单文件名并向 Gradle 传递一致的产物名称。版本字符串仍保留 7.1.0，待真正完成对应协议和资源适配后再修改。停止、构建、启动依次执行，不并行更新。

start 复用进程和 status 检查会核对当前 JAR、进程与 `local/running.json` 的启动哈希；磁盘产物已改变时要求正常重建重启。切换文件名后 stop 仍能识别本工作区的旧 JAR，不会将其他工作区的 Java 进程当成本服务。

本次本机验证：JAR 重新构建成功，HTTP 健康、TCP 8088 / UDP 22101 的 Java PID、MongoDB dbpath、磁盘与启动记录的 JAR SHA-256 均已核对，7.1 补丁状态通过。39 项脚本测试和 5 项 `Protocol71CompatibilityTest` 通过。配置及已有数据库/运行数据已留存停机副本，未做恢复演练。启动日志仍报告部分 BIN 任务资源不完整；真实客户端登录和玩法尚未操作。机器路径、PID、哈希和原始审计报告留在被忽略的 `local/`，这些结果不是下一台机器的验收结论。

## 可复用的只读核查

```powershell
python tools/audit_client.py --client '<目标客户端目录>' --output local/client-audit.json
python -m unittest tools.test_audit_client tools.test_local_windows tools.test_patch_game tools.test_runtime -v
```

工具记录客户端关键文件的 SHA-256、三份源码提交、当前服务端登录/入场命令号、提取器哈希匹配情况及补丁静态特征匹配数量。报告包含机器路径，应保存在被 Git 忽略的 `local/`。文件哈希用于标识输入，不认证来源；工具不修改客户端，不执行提取、登录或发放物品，退出码 0 只代表审计完成。

## 后续迁移顺序与验收

1. 核对完整 7.2 客户端，获得对应的协议定义、命令号/名称映射，或为这组 EXE/metadata 建立新的、经验证的提取器配置；保留 7.1 配置用于对照。
2. 优先更新 dispatch、GetPlayerToken、PlayerLogin、Ping、选人和场景入场链路，逐包验证字段编号、标量类型、嵌套消息及请求/响应配对。使用真实 7.2 样本锁定测试。
3. 整理 7.2 Excel、BinOutput、Lua、文本和场景资源的来源及版本，比较新增与变更记录，避免按行号合并，也不覆盖现有子模块未提交内容。
4. 核对补丁的加载、HTTP/SDK 路由、密钥和每个回调的对象布局。可选功能缺失单独记录；不把签名未匹配的钩子强行安装到猜测地址。
5. 使用隔离测试存档验证真实客户端登录、创建角色、进入世界、移动、传送、战斗和退出重登，然后逐项验证深渊、商城和新增内容。遵循各专题接手手册。
6. 验收后更新版本声明与适配文档，重新构建、正常停服并部署，核对健康、端口 PID 及运行 JAR SHA-256，再提交到本地 Git。

服务健康、补丁静态命中和编译通过都不能替代真实客户端验收。当前 7.2 登录/入场/存档验收尚未执行。
