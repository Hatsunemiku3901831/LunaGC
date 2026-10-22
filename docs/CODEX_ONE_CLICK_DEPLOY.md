# 给 Codex 的 Windows 一键部署与排障手册

基线：2026-10-23，服务端 `19d6a51`；补丁子模块 `f2874103ab70afc56260ee12d1eaca96247933b2`；资源子模块 `dac2a9d43332ef51879522cf39da2831b0ca0543`。后续以当前源码、子模块锁定提交和根目录 `AGENTS.md` 为准。

本文面向“把本项目交给另一台电脑上的 Codex，从准备环境到部署验收”的场景。默认 Windows x64、本机国服 7.1 客户端、便携 Java/MongoDB。**首次部署需要准备依赖、完整资源和匹配客户端；现有启动脚本不是自动下载全部依赖的安装器。** Codex 按下文完成准备后，可以连续执行部署命令；日常使用只需双击启动入口。

## 1. 直接复制给另一位 Codex

```text
请实际部署这个项目，不要只提供操作建议。
先读取 AGENTS.md 和 docs/CODEX_ONE_CLICK_DEPLOY.md，按本文依次完成：
环境盘点 → 完整源码/子模块/LFS → 便携依赖 → 本机配置 → 构建补丁和服务端
→ 启动 → 健康、进程、端口、JAR SHA-256 与补丁验证 → 本地账号和游戏验收。
默认项目 C:\GS，游戏 C:\GAME\Genshi，LUNAGC_RUNTIME=portable。
不安装 Docker/WSL、不启用虚拟化、不自动改为公网或远程部署。
使用当前交付仓库及其锁定子模块，不用上游 latest JAR 替代当前修改。
先检查已有配置、服务和存档；保留 mongodata 和 local/data，不清库、不覆盖旧配置。
依赖尽量放 local，缺少的依赖从官方来源获取并核对版本/校验值；记录实际工具版本。
有明确缺失且无法取得的客户端、资源或私有提交时，说明准确的阻塞项，不伪造部署成功。
退出码非零就停止后续依赖步骤并排查；不要并行执行停止、构建、启动或客户端补丁操作。
不要强杀未知 Java/MongoDB，不修改游戏运行中占用的 DLL。
只在现有授权范围内操作；需要改变部署方式、覆盖存档或遇到系统授权时再说明原因。
交付时报告源码/子模块版本、构建结果、运行 PID、TCP/UDP、健康、JAR 摘要、
补丁状态和真实客户端验证范围。需要修改仓库文件时完成后本地 Git 提交，不推送。
```

## 2. 先区分交付物与本机状态

| 内容 | 应如何交付或准备 |
| --- | --- |
| 主仓库和补丁、资源子模块 | 交付包含所需提交的仓库/仓库包，或提供能获取这些提交的远程地址 |
| Java、MongoDB、Rust、构建缓存 | 不在 Git 中；由新机器下载或从可信离线包准备 |
| `LunaGC-7.1.0.jar`、`patch/target/release/ext.dll` | 构建产物，不应假定 clone 后就存在；优先从本次源码构建 |
| 游戏客户端 | 用户自行准备匹配的完整客户端；服务端仓库不含游戏 |
| `.env`、`.env.local`、`config.json` | 在目标电脑生成/核对，不能套用另一台机器的路径和管理令牌 |
| `mongodata/`、`local/data/` | 前者为玩家数据库文件，后者为运行数据；全新部署与存档迁移分开处理 |

**本地提交不等于别人能从 GitHub 获取。** 本项目按约定不自动推送。如果提供的远程没有本地修复提交，另一台机器 clone 上游仍是旧代码。交付者须提供可读取的主仓库和两个子模块提交（可用分别制作的 Git bundle），或另行明确授权发布；不能为了“部署成功”悄悄改用上游版本。源码 ZIP 往往不包含子模块，也没有完整 Git 元数据。

`LOCAL-RUN.txt` 是原机器快捷说明，其中账号、UID 和“环境已准备”不适用于新机器。`Start-GS.cmd` 不创建游戏账号。

## 3. 部署前盘点

先在 PowerShell 中执行只读检查，保留已有未提交修改：

```powershell
Set-Location -LiteralPath 'C:\GS'
git status --short
git rev-parse HEAD
git submodule status
Get-Command git, powershell.exe -ErrorAction SilentlyContinue
Test-Path -LiteralPath 'C:\GAME\Genshi\YuanShen.exe'
Test-Path -LiteralPath 'C:\GAME\Genshi\YuanShen_Data\Plugins\Astrolabe.dll'
Test-Path -LiteralPath 'C:\GAME\Genshi\YuanShen_Data\Plugins\AccountPlatNative.dll'
Get-NetTCPConnection -State Listen -LocalPort 8088,27017 -ErrorAction SilentlyContinue |
    Select-Object LocalAddress,LocalPort,OwningProcess
Get-NetUDPEndpoint -LocalPort 22101 -ErrorAction SilentlyContinue |
    Select-Object LocalAddress,LocalPort,OwningProcess
```

端口未监听时这些查询可能报告“找不到对象”，不等于部署失败。端口已占用时，按 PID 查询 `Win32_Process` 的 `ExecutablePath` 和 `CommandLine`，确认是否本项目实例。不要按进程名批量结束所有 Java 或 MongoDB。

本机约定固定路径如上；另一台电脑若用户明确指定不同路径，可按其约定调整，但须检查旧 `config.json` 的绝对资源/数据路径。默认 Java 堆上限为 6 GB，MongoDB WiredTiger 缓存为 1 GB，还需给客户端和 Windows 留出内存。检查剩余磁盘空间能容纳完整客户端、资源、依赖缓存和数据库；不要把源码包体积当成总需求。

## 4. 首次准备：源码、资源和工具

### 4.1 完整源码与 LFS

尚无仓库时，先从**交付者提供且包含目标提交的来源**克隆到 `C:\GS`；已有工作区不要再克隆覆盖。核对 `.gitmodules` 中 URL 与交付来源能否取得锁定提交。工作区和子模块无待保留修改后执行：

```powershell
Set-Location -LiteralPath 'C:\GS'
$ErrorActionPreference = 'Stop'
git lfs version
if ($LASTEXITCODE -ne 0) { throw 'Install Git LFS before continuing.' }
git submodule update --init --recursive -- patch resources
if ($LASTEXITCODE -ne 0) { throw 'Submodule checkout failed; check access and pinned commits.' }
git -C resources lfs pull
if ($LASTEXITCODE -ne 0) { throw 'Resource LFS download failed.' }
git -C resources lfs fsck
if ($LASTEXITCODE -ne 0) { throw 'Resource LFS integrity check failed.' }
git submodule status
$gsAvatarTable = 'C:\GS\resources\ExcelBinOutput\AvatarExcelConfigData.json'
if (-not (Test-Path -LiteralPath $gsAvatarTable)) { throw 'Resource table missing.' }
if ((Get-Content -LiteralPath $gsAvatarTable -TotalCount 1) -match '^version https://git-lfs') {
    throw 'Resource is still an LFS pointer.'
}
```

不要用 `git submodule update --remote` 把资源推进到任意新版本。`lfs pull` 失败时先解决网络、权限、配额或缺失对象；不能把几十字节的指针当成完整资源。单个 Avatar 文件通过只证明抽样可读，完整性还要结合 LFS 检查和启动加载日志。

### 4.2 便携目录必须满足脚本的实际查找方式

```text
C:\GS\
  local\runtime\jdk-17...\bin\java.exe
  local\runtime\jdk-17...\bin\javac.exe
  local\runtime\mongodb-...\bin\mongod.exe
  local\cargo\bin\cargo.exe
  local\rustup\...
  local\runtime\task\task.exe       （可选，仅 task.cmd 需要）
  patch\target\release\ext.dll      （构建生成）
  LunaGC-7.1.0.jar                   （构建生成）
```

本机已使用的参考版本为 Temurin **17.0.20.1+1**、MongoDB **7.0.45**。这些是部署记录，不表示未来最新版本。新机优先复现相同版本；已有数据库不要为安装新工具随意跨 MongoDB 大版本升级。

- JDK：从 [Adoptium 官方发布页](https://adoptium.net/temurin/releases/?arch=any&mode=filter&version=17) 获取 Windows x64 JDK 17 ZIP，核对官方校验值，再解压到 `local/runtime/`。需要 JDK，不是只有 `java.exe` 的 JRE。
- MongoDB：从 [MongoDB 官方下载目录](https://www.mongodb.com/try/download/community-edition/releases) 查找匹配 Windows x64 的 7.0 系列 ZIP，核对版本和校验值后解压。使用便携 `mongod.exe`，不另装占用 27017 的系统服务。数据库备份工具和 mongosh 是额外工具，不由这个 ZIP 的存在保证。
- Rust：补丁仓库的 `rust-toolchain` 当前要求 **nightly**，不能直接套用 stable。按 [Rust 官方安装说明](https://rust-lang.org/tools/install/) 准备 rustup；Windows MSVC 构建还需 C++ 工具链，参见 [微软 Rust 环境说明](https://learn.microsoft.com/en-us/windows/dev-environment/rust/setup)。优先复用已有 Visual Studio Build Tools 的 C++ x64 工具和 Windows SDK，缺失时明确说明所需系统安装。
- Git/Git LFS 必须可执行；Gradle 使用仓库自带 `gradlew.bat`，当前 wrapper 为 8.5，首次构建需下载 Gradle、Maven 和 protoc 依赖。
- Task 可选：本文直接调用 PowerShell 入口，不依赖 Task。要使用 `task.cmd`，必须另备 `local/runtime/task/task.exe`。

解压后检查不要多套一层目录。脚本按目录名 `jdk-17*`、`mongodb-*` 排序选第一个，故每类只保留一个预期版本在扫描目录内；旧版本归档到其他目录。不要只安装系统 Java 然后期待便携脚本自动使用它。

Rust 初始化示例，前提是 `local/downloads/rustup-init.exe` 已从官方来源下载并核验，且 MSVC 构建依赖已具备：

```powershell
Set-Location -LiteralPath 'C:\GS'
$env:CARGO_HOME = 'C:\GS\local\cargo'
$env:RUSTUP_HOME = 'C:\GS\local\rustup'
# 仅首次初始化执行；已有工具链先检查版本，不重复安装或自动升级。
& .\local\downloads\rustup-init.exe -y --no-modify-path --profile minimal --default-toolchain nightly-x86_64-pc-windows-msvc
if ($LASTEXITCODE -ne 0) { throw 'Rust initialization failed.' }
& .\local\cargo\bin\rustup.exe show
& .\local\cargo\bin\rustc.exe --version
```

当前 nightly 未在仓库固定日期，未来可能改变编译结果。交付时记录实际 `rustc` 版本；遇到 nightly 回归先复现/核对已成功环境，不随意删除 `Cargo.lock`。离线部署还须准备完整 Gradle/Cargo 工具链和缓存，仅复制 JDK/MongoDB 不能保证可构建。

### 4.3 本机配置

首次部署且 `.env` 不存在时创建 UTF-8 文本，最小内容如下；已有文件只核对需要的键，不覆盖其他设置：

```dotenv
LUNAGC_RUNTIME=portable
GAME_PATH=C:\GAME\Genshi
LUNAGC_HTTP_PORT=8088
LUNAGC_GAME_PORT=22101
```

检查 `.env.local` 是否覆盖了这些值。`local_windows.ps1` 顺序读 `.env` 再读 `.env.local`，覆盖当前子进程同名变量；直接调用路径解析脚本时，进程中的 `GAME_PATH` 优先。不要通过点执行或 `Invoke-Expression` 读取 `.env`。

首次启动在 `config.json` 不存在时自动生成配置和随机 GM token，并在 `local/data` 不存在时从仓库 `data` 复制。**已有 config 不会自动改造成便携配置，也不会自动修补缺失的 local/data。** 从旧机器迁入时检查：

- `folderStructure.resources` 指向当前 `C:\GS\resources`；`folderStructure.data` 指向实际要保留的 `C:\GS\local\data`。
- `databaseInfo.server` 和 `databaseInfo.game` 的 `connectionUri` 为目标实例，默认 `mongodb://127.0.0.1:27017`，`collection` 默认 `grasscutter`。
- `server.http.bindPort=8088`、`server.game.bindPort=22101`，两边 `bindAddress/accessAddress` 默认为 `127.0.0.1`。
- 本机 HTTP 默认不启用 TLS；`server.gm.loopbackOnly=true`，保留本机新生成的管理令牌。

本流程固定默认端口。补丁中的 HTTP/SDK 地址仍有硬编码 `127.0.0.1:8088`，**不能只改 `.env` 就换 HTTP 端口或连接远程服务器**。便携脚本还固定 MongoDB 27017、根目录 JAR 文件名及 `local/java-sockets`；不要误以为 `.env.example` 中所有 Compose/开发变量都适用于它。特别是 `LUNAGC_MONGO_URI`、`LUNAGC_MONGO_PORT`、`LUNAGC_JAR`、`LUNAGC_PUBLIC_ADDRESS`、`LUNAGC_RESOURCES_DIR` 和 `LUNAGC_JAVA_SOCKET_DIR` 不会替你重写这套便携路径/配置。

## 5. 准备完成后的连续部署命令

先退出游戏。已有存档的升级先按第 8 节备份。本段会正常停服、构建两端、安装补丁并启动后台服务；不自动打开游戏，也不创建账号。首次部署和更新均可用，但不要与另一部署操作同时执行。

```powershell
Set-Location -LiteralPath 'C:\GS'
$ErrorActionPreference = 'Stop'
function Invoke-GsPortableStep([string] $GsMode) {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'C:\GS\tools\local_windows.ps1' -Mode $GsMode
    if ($LASTEXITCODE -ne 0) { throw "LunaGC step failed: $GsMode" }
}
if (Get-Process -Name YuanShen -ErrorAction SilentlyContinue) {
    throw 'Exit the game before deploying.'
}
# 必须在启动守护前确认 GAME_PATH、完整资源和编译依赖均已准备。
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\client_patch_guard.ps1 -Mode stop
if ($LASTEXITCODE -ne 0) { throw 'Could not stop the client patch guard.' }
Invoke-GsPortableStep 'stop'
Invoke-GsPortableStep 'buildpatch'
Invoke-GsPortableStep 'build'
Invoke-GsPortableStep 'patch'
Invoke-GsPortableStep 'start'
Invoke-GsPortableStep 'status'
```

每一步失败都先排查，不能继续宣布部署完成。编译失败时原存档和旧产物须保留；不要删除 `mongodata` 或 `local/data` 进行“全新重装”。构建服务端前必须停服，因为当前入口明确拒绝覆盖正在使用的 JAR。服务端构建参数为 `--no-daemon jar -PskipHandbook=1`，跳过 Node 手册生成，不代表整个项目所有前端构建都已执行。

有 Task 且已核对 portable 设置时，**仅服务端代码更新**可运行 ` .\task.cmd serve`，它会停服→构建→启动；这不会替你构建 Rust 补丁。只运行 `Start-GS.cmd`，遇到已健康的旧进程会直接复用，不会热更新 JAR。

## 6. 必须执行的验收

### 6.1 服务健康、端口和运行构建

以下命令不输出 GM token。`local/running.json` 是启动时的摘要记录，必须连同实际 PID、命令行、端口核对，不能单独作为运行证明：

```powershell
Set-Location -LiteralPath 'C:\GS'
$ErrorActionPreference = 'Stop'
$gsHealth = Invoke-RestMethod 'http://127.0.0.1:8088/status/server' -TimeoutSec 10
if (-not $gsHealth.status.health.healthy) { throw 'Server is not healthy.' }
$gsRun = Get-Content -LiteralPath .\local\running.json -Raw -Encoding UTF8 | ConvertFrom-Json
$gsProc = Get-CimInstance Win32_Process -Filter ('ProcessId=' + [int]$gsRun.pid)
$gsJar = (Resolve-Path -LiteralPath .\LunaGC-7.1.0.jar).Path
if (-not $gsProc -or $gsProc.Name -ne 'java.exe' -or
    $gsProc.CommandLine.IndexOf($gsJar, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
    throw 'Running PID does not match this JAR.'
}
$gsTcp = @(Get-NetTCPConnection -State Listen -LocalPort 8088 |
    Where-Object OwningProcess -EQ $gsRun.pid)
$gsUdp = @(Get-NetUDPEndpoint -LocalPort 22101 |
    Where-Object OwningProcess -EQ $gsRun.pid)
if (-not $gsTcp.Count -or -not $gsUdp.Count) { throw 'Expected TCP/UDP ownership missing.' }
$gsHash = (Get-FileHash -LiteralPath $gsJar -Algorithm SHA256).Hash
if ($gsHash -ne $gsRun.jarSHA256) { throw 'Disk JAR differs from startup hash; redeploy normally.' }
[pscustomobject]@{ Healthy = $true; PID = $gsRun.pid; TCP = 8088; UDP = 22101; JarSHA256 = $gsHash }
Get-Content -LiteralPath .\local\logs\server-err.log -Tail 60
Get-Content -LiteralPath .\local\logs\server-out.log -Tail 60
```

补充检查 `local/logs/mongo.log`、`logs/` 中最近日志，确认资源加载、数据库连接、保存错误和死锁情况。需要更强构建溯源时比较改动类在 JAR 中的字节与本次编译产物；不能只对磁盘 JAR 算一次 hash 就声称内存运行的是它。

首次启动可能需数分钟，当前脚本等待 MongoDB 45 秒、服务健康最多 5 分钟。超时后查看进程与日志再决定，不要连续启动多个实例。后台进程可在启动终端/Codex 关闭后继续运行，但当前没有安装 Windows 开机自启动服务；重启或注销后应重新检查并启动。

### 6.2 补丁、账号和真实客户端

1. 运行 `GS-Status.cmd`，或用无暂停入口 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\local_windows.ps1 -Mode status`。这同时检查服务和补丁，可能“服务健康但补丁失败”，必须分别解释。
2. 用 `tools/client_patch_guard.ps1 -Mode status` 查看后台守护。日志在 `debug/client-patch/<路径摘要>/`，补丁运行日志通常在 `%TEMP%\lunagc-patch.log`。默认 start 会启动守护并可能修复 DLL；它不是完全只读的“只启服务”。
3. 新机器需要本机账号。先阅读 [账号与存档指南](ACCOUNT_AND_SAVE_SYSTEM.md) 和 [运维手册](ACCOUNT_AND_SAVE_OPERATIONS.md)，通过 `Open-GM.cmd` 打开本机后台，核对已有账号再创建，不能假定旧机器的账号随 Git 带来。
4. 默认 `autoCreate=false`、`EXPERIMENTAL_RealPassword=false`。在 GM 控制台可执行 `account create <新用户名>`，不要强行指定旧 UID；此命令授予 `*` 权限，适合本机管理测试账号。只有确认仍使用默认免验密码配置时，登录密码才可为任意非空字符串。已有账号启用密码要求时应使用真实密码。不要在共享文档中记录凭据。
5. 运行 `Play-GS.cmd`；验证账号登录、选择/创建角色、进入世界、移动/传送、退出重登后进度保留。记录实际观察，自动化未操作游戏就标记“客户端待验收”。

`healthy=true` 只代表服务检查通过，不证明登录、任务、深渊、商城和所有玩法正常。不要为了验证部署而在真实存档上批量完成任务、发全物品或改余额。

## 7. 常见故障与踩坑

“历史已复现”表示项目已有代码/文档证据；“候选原因”表示部署时要用日志确认，不能看症状直接断言。

| 症状 | 原因与证据 | 处理顺序 |
| --- | --- | --- |
| 新 clone 缺 Java/MongoDB/JAR/ext.dll | 当前脚本契约：`local` 和产物不随 Git 交付 | 按第 4 节准备，再构建；不要反复点启动 |
| `not our ref`、子模块提交获取失败 | 候选原因：本地提交未发布、URL/权限错误 | 核对交付来源，索取含该提交的仓库包；不自动换成远端最新提交 |
| 资源存在却不能解析，文件开头 `version https://git-lfs` | 当前启动检查明确防范 LFS 指针 | `git -C resources lfs pull`、`lfs fsck`；处理缺对象/配额/网络 |
| Gradle、Cargo 下载失败或证书/连接超时 | 候选原因：代理、网络、缓存不完整、官方源不可达 | 看具体失败 URL 和工具日志；修正进程网络配置，不关闭 TLS 验证、不换未知二进制 |
| `link.exe`/SDK 缺失、stable feature 编译失败 | 工具链不全或没有使用仓库要求的 nightly | 检查 MSVC C++ x64、Windows SDK、`CARGO_HOME/RUSTUP_HOME` 和实际 rustc |
| `Get-FileHash` 找不到，Task 调 PowerShell 才失败 | 历史修复 `63f0fa7`：PowerShell 7 的模块路径传入 Windows PowerShell | 使用已修复脚本显式加载 `$PSHOME` 对应 Utility 模块，不复制更旧脚本 |
| 网络错误 4201；Java 报 `Unable to establish loopback connection` / `Invalid argument: connect` | [历史已复现](WINDOWS_JAVA_STARTUP_2026-10-16.md)：JDK Selector 临时 socket 路径异常 | 核对 Java 命令行有 `jdk.net.unixdomain.tmpdir`，便携目录为 `local/java-sockets`；检查路径长度/可写性。不要只改全局 JAVA_TOOL_OPTIONS |
| 脚本认为已启动但 HTTP 不通 | 历史旧脚本混淆 TCP/UDP；另有端口被别的程序占用的可能 | HTTP 查 TCP 8088，游戏查 UDP 22101，分别核对 PID；使用当前脚本 |
| 27017 被占用 / `Refusing to reuse it` | 便携入口拒绝另一可执行路径或 dbpath 的 MongoDB | 查明实例所属和数据位置；协调停止冲突服务，不强接别人的数据库 |
| `.env ports and config.json ports must match` | 环境与旧配置端口不一致 | 核对 `.env.local` 和 config；保持默认端口，注意补丁仍硬编码 8088 |
| 登录 HTTP 502、curl error 56 | 历史脚本注释记录：localtest.me 被系统代理接管，代理访问不到本机服务；v2rayN 还会重写 bypass | 使用当前 `127.0.0.1:8088` 补丁，检查代理对回环请求的实际行为；不要直接运行含旧 D 盘路径的 `tools/fix_proxy_bypass.ps1` |
| `Unable to decrypt account` | 补丁注释记录：只改登录 URL，没有同步 passport RSA 公钥 | 退出游戏，用当前源码构建服务端/补丁并重新 apply，确认对应密钥；不混搭上游 JAR 与本机补丁 |
| `wrong client version`、找不到原版 URL/公钥、hook signature 不匹配 | 候选原因：客户端版本/渠道不匹配或 DLL 已被第三方修改 | 核对客户端版本及文件摘要；取匹配原版文件，不绕过签名检查强写 DLL |
| 客户端闪退、更新后代理 DLL 过旧、`.lunagc-live` 残留 | [历史防护](CLIENT_PATCH_GUARD_2026-10-18.md)：异常退出和客户端更新可能遗留安装状态 | 退出游戏后由当前 apply/guard 修复并复验；保持 `Astrolabe_orig.dll` 与当前原版一致 |
| 已修改 DLL 却没有 pristine backup | 安装器会拒绝把修改版当成原始备份 | 保留现场，取得匹配原版文件；不把 patched DLL 重命名为 `.lunagc-bak` |
| 点击开始游戏白屏、反复重连，HTTP 仍健康 | [历史已复现](QUEST_LOGIN_ACCEPTANCE_2026-10-21.md)：Excel 子任务与章节列表不一致，登录任务初始化异常 | 查 `PlayerLoginReq/QuestManager` 堆栈，核对实际运行修复构建与资源；不删除任务存档 |
| 七元素加载卡 98% | [历史已复现](QUEST_LOGIN_ACCEPTANCE_2026-10-21.md)：批量商城逐商品保存大玩家文档，网络/tick 争锁 | 查 `PostEnterSceneReq`、线程栈与写入队列，确认包含 `7eb9f12` 后续修复；不是只看死锁数为 0 |
| 健康 HTTP 503、切场景卡住 | [历史已复现](SCENE_TRANSFER_DEADLOCK_2026-10-18.md)：Player/队伍列表锁循环；503 也可能是积压/逻辑停滞 | 保存健康详情和线程栈，再按当前修复和正常停机流程处理；重启恢复不等于根因解决 |
| 改了代码但行为没变化 | 当前 start 会复用健康进程；也可能用了旧 JAR/旧 ext.dll | 正常停服→构建→启动，核对 running.json、PID、hash；补丁更新需退出游戏重装再启动 |
| 构建成功但找不到约定 JAR | [历史命名修复](JAR_NAMING_2026-10-16.md)或自定义 `-PjarFilename` | 便携入口固定根目录 `LunaGC-7.1.0.jar`；保持默认构建，不只设置 LUNAGC_JAR |
| 停服后 MongoDB 还在 | 设计行为：Stop 仅正常关闭游戏服务 | 不当成残留进程强杀；冷备需要另行干净关闭数据库 |
| 正常停服 60 秒后仍未退出 | 候选原因：写入排空、死锁、GM stop 未成功 | 看日志和队列；脚本不会强杀。不要报告“已保存”，也不要覆盖仍运行的 JAR |
| 开始游戏进入新号或“存档丢了” | 候选原因：指错库/数据目录、用了不同用户名、错误 UID 关联 | 按账号手册核对路由与归属；不重建账号、清库或改计数器来试错 |

历史 `debug/`、`local/` 中的故障证据不会随 Git 分发。上表引用的已提交文档保存了原因和验证边界，历史通过数量不应复制成新机器的测试结果。

## 8. 日常入口、备份与恢复

| 入口 | 实际作用 |
| --- | --- |
| `Play-GS.cmd` | 启动服务、安装补丁、启动守护、打开游戏 |
| `Start-GS.cmd` | 启动/复用服务并启动补丁守护；不重新构建 |
| `Stop-GS.cmd` | 经 GM stop 保存并等待服务退出，MongoDB 保留运行 |
| `GS-Status.cmd` | 检查服务、数据库和补丁；含 pause，不适合无人值守调用 |
| `Rebuild-GS.cmd` | 停服后构建服务端；不负责随后启动 |
| `Rebuild-Patch.cmd` | 构建 Rust 补丁；不自动让已运行的游戏加载新 DLL |
| `Open-GM.cmd` | 使用本机 token 打开管理页面，不将完整地址发到共享日志 |
| `Restore-Client.cmd` | 停止守护、恢复原版 DLL；先退出游戏，不删除服务端存档 |

无人值守统一用 `tools/local_windows.ps1 -Mode ...` 并检查退出码，避免 `.cmd` 的 `pause` 让 Codex 看起来卡住。

存档迁移/升级先读 [备份恢复操作](ACCOUNT_AND_SAVE_OPERATIONS.md)。至少保留完整数据库、`local/data`（或实际配置的数据目录）、本机配置、源码与资源版本、JAR 摘要。**Stop-GS 不会停止 MongoDB，不能随后在线复制 mongodata 就称完成冷备。** 可以在游戏服务正常停写后用 Database Tools 逻辑备份；物理复制要求数据库也已干净退出。备份先恢复到隔离目录/实例验证，不能直接覆盖正式库试错。

回退代码不等于回退存档。保留上次可用 JAR、ext.dll 和匹配源码/资源版本，正常停服再切换；数据结构不兼容时须采用已验证的整套恢复方案。不要运行 `git clean -fdx`、删除持久化目录或清空 pending 记录。

默认单机不需要启用 WSL/虚拟化或开公网端口。用户以后明确要求 Compose 时，另读 [Compose 部署说明](COMPOSE_RUNTIME_2026-10-18.md)，规划持久化数据迁移；Docker 卷与便携 `mongodata` 不是可随意互换的同一运行环境。

## 9. Codex 交付格式

```text
部署结果：成功 / 部分完成 / 阻塞（列出准确步骤和原因）
项目与游戏路径：
主仓库提交 / patch 提交 / resources 提交：
Java / MongoDB / rustc 版本：
构建：服务端、补丁各自退出码和产物位置
运行：Java PID、MongoDB PID、HTTP TCP 8088、游戏 UDP 22101、健康结果
JAR：磁盘 SHA-256 与启动记录一致，PID/命令行/监听归属已核对
客户端：补丁与守护状态；登录/进入世界/退出重登的实际观察或待验收
数据：新建还是复用，备份位置与恢复验证范围（不展示令牌和密码）
未解决问题：
本地 Git 提交：仅在修改仓库文件时记录；未推送
```

本文本身为文档交付，不代表已经在另一台空白 Windows 上完成首次部署验证。执行者应以目标机器的实际结果填写上述清单，不能沿用原机器的 PID、hash 或历史验收结论。
