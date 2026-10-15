# IFix 热修复流诊断

`patch/src/modules/ifix_capture.rs` 为现有补丁增加可选的热修复流观察入口。
它用于继续核对燃素 Add／Set 等原生入口转交的实现，默认不启用。
之前确认的文件名称和加载链见[字符串提取记录](CLIENT_LITERAL_EXTRACTION_2026-10-15.md)。

## 启用

先正常编译并安装当前补丁，再让游戏进程继承以下环境变量：

```powershell
$env:LUNAGC_IFIX_DUMP_DIR = 'D:\Projects\Experiment\LunaGC\debug\ifix-capture'
```

值必须是绝对目录。通过独立启动器运行且无法继承环境变量时，可以把该路径以 UTF-8 写入
`%TEMP%\lunagc-ifix-dump-dir.txt`；标记文件最大 4096 字节。
移除环境变量／标记后重新启动即可停用，`LUNAGC_DISABLE=ifix` 也可禁用该模块。
`.env.local` 不会自动向已经启动的游戏进程注入此变量。

本次诊断只给单独启动的游戏子进程设置变量，没有创建全局变量或临时标记。

## 当前固定客户端

该入口目前仅支持以下二进制与 metadata 配对，其他构建会记录原因并跳过：

| 文件 | SHA-256 |
| --- | --- |
| YuanShen.exe | `7f89938da606c1281659607d464702cddb9a09a7d4320a196630f60a811ec38e` |
| global-metadata.dat | `469eccd43aa48fe7a2df4e1caf66fa1268a17f5a4a03fa209e1427e467cb0161` |

检查两份文件后，还会核对 `PatchManager.Load` 的 RVA `0x1912a730` 及前 32 字节。
通过后才安装观察回调；不把当前地址套用于其他版本。

原生 MemoryStream 的缓冲区字段为 `0x20`，容量／起点／长度／位置分别为
`0x30`／`0x34`／`0x38`／`0x3c`，打开状态为 `0x43`。
回调确认流与数组的运行时类型、范围及 IFix 魔数 `073d637c857bda66`，
并要求每次进程内存读取完整成功。未识别输入不会作为有效补丁保存。

回调保留原参数、流位置和流内容，随后由原加载函数继续处理。
导出仍会产生文件 I/O 开销，因此作为按需诊断使用。

## 输出

- 文件名为 `ifix-<sha256>.bin`，内容是原加载入口尚未消费的编译流。
- 单个流上限 128 MiB，单次进程累计上限 256 MiB，最多接受 16 次有效观察。
- 同名文件只有在长度和哈希都一致时才复用；不同内容不会被覆盖。
- 日志位于 `%TEMP%\lunagc-patch.log`，记录开启／跳过、拒绝原因、长度及输出路径。
- 源文件哈希不符、签名不符或目录／挂钩失败时，退出该诊断模块并记录原因。

## 验证与剩余范围

先新增读取测试，在占位实现上出现预期失败，再补齐实现。
新增 9 项测试覆盖精确字节、非零起点／位置、类型和范围拒绝、超大声明、
真实进程内存读取、内容寻址文件、冲突保护及已知 SHA-256。
完整 Rust 单元测试 **27 项通过，0 项失败**；`cargo build --release` 成功。
本次 DLL 的 SHA-256 为：

```text
5d846b5bdd86bb588a33609cefff867f853dbb7db5e5c486c54eaf5c6b0d503e
```

已安装至提供的客户端并启动，真实日志确认文件／签名检查通过并挂载观察入口。
安装前后 `AccountPlatNative.dll` 哈希一致。
首次诊断会话尚未经过这个加载入口，输出目录为空；燃素动作的热修复标记读取为 0。
这不能证明所有 IFix 逻辑都没有加载，也不能证明已取得实际热修复包。
后续仍需在进入角色后读取观察结果，确认实际流类型和内容，再配对完整计算分支。

## 已初始化的 VM 状态诊断（2026-10-16）

同一个可选模块现在会从游戏进程自身记录燃素动作的 IFix 状态，
用于确认加载标记、包装器和 VM 是否已经发布。仍通过上述环境变量或临时标记启用；
临时标记在移除前会继续对后续启动生效，现有进程已初始化的配置不受删除标记影响。

新增诊断在检查 EXE、metadata、startup 三份文件的 SHA-256 及
`PatchManager.Load`／燃素动作入口签名后启动。
燃素入口 RVA 为 `0x7b469f0`；加载标记为 `base + 0x5780c6b`，
运行时根位于 `base + 0x5783de8`，静态存储和包装器偏移分别为 `0x256d0`／`0x57fcf8`。

- 标记为零时只读取标记，不解引用尚未初始化的静态槽。
- 当前包装器使用 metadata 类型 56285：VM 在 `0x10`、匿名对象在 `0x18`、方法 ID 在 `0x20`。
  不与类型 11743 的反向字段布局混用。
- VM 类型 7190 的 `unmanagedCodes` 在 `0x80`，`PatchedArrayLength` 在 `0x88`。
  记录方法 ID、这些字段及已知数组的长度，不访问数组元素或方法体。
- 每次读取要求返回完整字节，检查空指针、对象类指针、数组长度上限和地址溢出；
  结束时复核加载标记、根、存储、包装器与 VM 头，拒绝读取期间发生替换的快照。
- 每秒检查一次，最多 300 次；状态相同不新增文件，最多保存 32 个文件，每个至多 32 KiB。
  输出为 `runtime-<pid>-<sha256>.json`，包含进程 ID、观测时间、模块基址及三份输入哈希。
  文件冲突时保持原文件，模块停用后停止后续采样。

补充核对了 Tencent/InjectFix 固定提交
`5ba42eb8f63fe37134a3c78690f97d8425e13588` 的
[VM](https://github.com/Tencent/InjectFix/blob/5ba42eb8f63fe37134a3c78690f97d8425e13588/Source/VSProj/Src/Core/VirtualMachine.cs)
和[加载器](https://github.com/Tencent/InjectFix/blob/5ba42eb8f63fe37134a3c78690f97d8425e13588/Source/VSProj/Src/Builder/FileVirtualMachineBuilder.cs)。
公共加载器逐方法读取长度后分配指令缓冲区；当前客户端的 `PatchedArrayLength` 是另一个字段，
本次没有把它当作方法总数或代码长度，也没有按猜测的大小读取指令。
完整燃素 Add／Set、持续 Set 和 TPS 状态动作的计算仍需实际加载流。

新增 17 项测试，首轮保留 4 项失败；最终完整 Rust 单元测试 **44 项通过，零失败／忽略**，
`cargo build --release` 成功。新 DLL SHA-256：
`2fa28526cc5753242db63a411c67a8fd50d90e9bb4bf34917c510b862665d510`。

本次准备安装时检测到游戏进程正在运行，在修改客户端文件前停止了安装步骤。
运行中的会话仍使用上一版补丁，日志确认原始流捕获入口已启用，但当前捕获目录仍为空。
新增 VM 状态诊断尚未进行客户端运行验证。

源布局、公开源文件 blob 校验、红测试、完整测试、release 构建及安装前状态记录，
位于 `debug/aster-mid-20261011/batch272/`。

本机证据保存在 `debug/aster-mid-20261011/batch262/`：
`ifix-capture-red.log`、`ifix-capture-final.log`、`ifix-capture-build.log`、
`ifix-memory-layouts.json`、安装前快照及会话读取结果。
本次没有修改 Java 服务或替换日常运行 JAR。
