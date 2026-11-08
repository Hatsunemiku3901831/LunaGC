# R2-CONFIGSYNC-EVENT：local_config 生产、原生分派和有界观察

本卡证明固定文件中的静态路径，并在**自有离线 Unicorn 内存**执行原构造和 name 分派到原 writer 入口；没有运行游戏、连接 SDK、观察自然回调、安装 hook、完整执行 writer、构建或部署。不要把本卡的存在性证明当作自然 env 已选择、配置已写入、请求已发出或 D-02 通过。

## 生产者、入口与同步调用关系

固定 SDK 为 `AccountPlatNative.dll` SHA-256 `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25`。下述 EXE 和 SDK RVA 仅适用于既有固定五输入；上下文、原始窗口和注解在忽略目录 `local/migration72/d02-resume-20261109/r2-config-select/`。静态结论与离线执行分开：

1. 托管 `ComboManager.LocalConfigSync` 对应 EXE RVA `19806d70`：创建 JSONObject，把选择的 env 数值转 double 后写 `env`；将同源 `app_key` 写入；调用 `ConfigManager.LoadLocalConfig(env,true)`，对返回对象 `+b0` 序列化得到字符串并写 `url`。其构造 JSON 还包含其他字段，不能输出或保存完整正文。构造末尾 StringBuilder 转字符串，把 literal `local_config` 与字符串传给 AccountPlatformPlugin 实例虚表 `+f8`。`159807989` 是正常路径 tail-jump；注解中 `1598079a0` 属其他控制流，不能写成这个正常分支的直接 call。
2. SDK export `HoYoSDK_ConfigSync` RVA `13d5b0` 接收 Windows x64 的 `RCX=name C 字符串`、`RDX=payload C 字符串`，保存两指针，取得引擎后以 name 放 RDX、payload 放 R8，恢复自身帧并 tail-jump 引擎虚槽 `+70`。固定槽落 `1343b0`：查询 aggregate，若 aggregate 及 `+40` config 存在，以 RCX=config、RDX=name、R8=payload 同步调用 config 虚槽 `+3a0 → 36fba0`；调用后置引擎 `+d8=1`。该标志不是 writer 成功、认证或请求成功的证据；虚槽调用目标和 export 没有经本卡自然执行验证。
3. `36fba0` 把 name 构造为临时 `std::string`，在 config `+a80` 的有序 handler 树中用原比较代码查找；未匹配或处理器为空就清理临时字符串返回，命中时把 payload 指针放在当前栈槽并传其地址给 handler 虚槽 `+10`。固定的 `3637d0 → 3751e0` 构造逻辑注册 `local_config` 到 `36e4e0` 成员包装，`36e4e0` 读取 config 虚表并 tail-jump `+2e0`；固定 config vtable `6c6cd8 + 2e0` 的指针为 writer `378850`。后者 ABI 是 RCX=config、RDX=payload 指针，返回 void，不能用 RAX 冒充成功值。[原配置消费者合同](CLIENT_72_SDK_NATURAL_CONFIG.md)说明 env/key/url 及其剩余边界。

以上将托管生产侧和 native export/注册侧分别固定，并验证原 native 分派；**托管插件虚槽到该 native export 的自然调用、实际 marshaling 所有权、真实线程以及回调发生均未观察**。因此事件是明确的定向候选，不是已发生的本服配置事件。

## 原构造与正反分派：离线实际结果

先复用冻结 `RouteFragments` / `ResponseFixture` 和其 SDK 映像、分配、单线程 mutex 与 TLS 模型；原 `3637d0` 首次尝试在 `143055` 到系统 IAT `KERNEL32!MultiByteToWideChar` 时因未映射导入停止，共 83,450 条指令，`3751e0` 已进入，handler map head 存在但 count 为 0，原 lookup 和 writer 均未进入。该首缺没有靠假成功继续。

主线授权后，以 fixture 内存字节的副本调用**当前 Windows 的真实 `MultiByteToWideChar`**，原样传 code page、flags、输入长度及输出容量，将真实返回数和输出写回自有 fixture；真实 API 失败立即停止，不伪造转换成功，不对失败后的 Win32 last-error 语义作通过断言。其余沿用冻结 fixture 已列的底层 allocation/free、单线程 mutex/condition、静态初始化与 TLS 模型。SDK 本体不加载到操作系统、不启动目标进程，不读取自然内存。真实转换边界执行6次后原 `3637d0 → 3751e0` 返回、栈平衡；原有 handler map 计7项。此为这些模型条件下的原函数执行，不是自然 SDK 构造或完整进程初始化。

在同一已返回的 fixture config 上调用原 `36fba0`：

- 未注册名称及 `local_config_extra` 均正常返回，没有进入 `36e4e0` 或 writer；没有以 host 代码替代 map/字符串查找。
- `local_config` 经原 map lookup、真实 binder 和 `36e4e0` 到 `378850` **入口 cut**；在此检查 RCX 等于原 config、RDX 等于原自有 payload 指针。切点前的原始指令数为 464；没有执行 writer 或其返回。

每次原生调用上限 2,000,000 条指令，首次异常或未到期望切点即失败。`tools/client72_sdk_configsync_event.py` 对固定 SDK 和所用 fixture 源/库核 hash；v2 原生 proof 额外输出固定代码窗与原指令路径的 SHA、指令数、相对栈位移以及自有 fixture 的系统转换参数/返回和复制等值布尔，不包含正文、地址指针、内容或内容 hash。下述 v2 proof 供独立审核。早期未映射和带真实 OS 边界的探索脚本留同一忽略目录，不能把前者当通过。

## 后继最小观察规格（未实现）

先由主线审核独立装载、固定文件和精确入口、校验影响与全 ABI 保存。只在 `HoYoSDK_ConfigSync` 入口捕获匹配 name 的一个受控事件；原调用期间读取调用方有效的输入，只在该调用内进行必要处理。当前静态代码证明 SDK 同步传递原 payload 指针，不证明外部调用方延长了指针生命或全局无写入；不能把该指针保存到返回后，也不能无依据地跨线程深读。若入口处可合法、安全地复制有界数据，之后仅用自有副本在内存中分类；读取失败、未终止、超出审核的上限、身份不符或异常均只记录固定失败状态并停止。真实指针可读性、限额和实际 caller/调用频率须通过后续方案证明，不从离线模型推断。

仅对精确 `local_config`，在内存解顶层 JSON：env 必须是既证 CN 集合之一；`app_key` 以所选 env 的固定资源行作相等 bool；`url` 必须是字符串，再在内存解其对象并对 `comboLogin` 作相等 bool。输出白名单仅状态、既证 `cn-env-N` 和两个相等布尔；不得记录正文、device ID、key、URL、字节长度或其 hash。不得把这些等值结果称为原生 writer 完成、所选 route 生效或实际 HTTP 请求。未知 env、形状、引用不唯一和无匹配事件应保持未知。

本卡的 `classify_owned_event` 仅处理调用方传入的**自有 bytes**与内存中引用行，没有 PID、指针读取、注入、跨线程复制或文件采集后端；其 JSON 处理并未声明与目标原生解析器对任意输入完全等价。用合成例覆盖匹配/失配、近似 name、env 类型及未知、缺失/重复参考、错误嵌套和敏感哨兵不出输出；6项本地 Python 测试通过，错误 SDK 散列被拒绝。工具及测试是研究用离线代码，不构成安装或自然观察许可。

## 复现

在已经核对固定目标文件的前提下，仅对本机磁盘输入执行：

```powershell
$taskGamePath = & ./tools/game_path.ps1
python tools/client72_sdk_configsync_event.py --sdk (Join-Path $taskGamePath 'YuanShen_Data/Plugins/AccountPlatNative.dll')
python -m unittest tools.test_client72_sdk_configsync_event -v
```

无结果时保留状态并定位首个未知边界，不扩成完整 Init，也不安装 hook 或请求采样。后续只有在经过审查的观察入口中，才能决定是否采集自然事件；必要时使用独立测试账号和明确用户动作，保护全部原始敏感值。

## 主线审核补充：v2 可复核 proof

冻结 v1 原始源码为 `r2-configsync/client72_sdk_configsync_event.v1.private.py`，SHA `b779d91c8c0e8b8b07e74b2012b988ad209d3190b17f2106708422693e935591`；原先结果 `native-summary.private.json` 与单独复跑 `native-summary-v1-repeat.private.json` 均为 SHA `956160e1e50696f2040c2313eac6875fca8df51ecf9fa380b34f50bf2309073e`。这些均在 `local/migration72/d02-resume-20261109/` 下。

新增 v2 proof 双跑 `r2-configsync/native-proof-v2-final1.private.json` 和 `native-proof-v2-final2.private.json`，逐字节一致，SHA-256 均为 `3ecd3a8b26be29e989a7be65b170adee9a1f77346d1a4c04e9f00e8b4948680f`。两个结果标记 `owned-dispatch-entry-verified`；固定 SDK、v2 源及8项冻结依赖 SHA 和8个准确代码窗的 RVA、互斥结束 RVA、SHA 均在 proof。path SHA 算法是把每个按顺序发生的相对 SDK base 指令 RVA 编码成有符号 little-endian 64 位并依次拼接后作 SHA-256；其中含自有 Win32 转换边界 stub，非输入数据或指针摘要。

| 原调用 | 指令数 | 实际停止 | entry / stop 的 RSP 相对偏移 |
| --- | ---: | --- | --- |
| 原 constructor | 89073 | 正常返回 | 0 / +8 |
| 未注册名称 | 373 | 正常返回 | 0 / +8 |
| 近似前缀名称 | 449 | 正常返回 | 0 / +8 |
| 正确名称 | 464 | writer `378850` 入口 cut | 0 / -112 |

两个非目标名称均未命中 handler/writer；正例的 config 和 payload 指针**分别**只输出等值布尔，均为真，不输出指针值。6 次真实系统转换的 code page、flags、原输入长度、输出容量、系统返回数及副本输入/输出逐字节等值布尔也已单列；空输出缓冲的输出比较记为 null。这些长度仅来自受控构造函数 fixture，不是自然事件或敏感字段长度。没有打印、保存或 hash 字符串及转换结果本身。

另用私有 `os-failure-negative.private.py` 在首个系统边界故意注入返回0的**失败**，不注入成功：工具按预期停止，`os-failure-negative.private.json` 保留 `constructor`、`system-conversion-failed`、原最后调用指令 `143055`、首个转换的失败记录和当时路径；没有继续分派。该负例不声称 Windows API 实际发生过失败。前述第一次未映射原尝试仍保留，与此主动失败负例分别计算。

v2 工具 SHA `33fb97abb279e6c46b2a13858184ba54dea4fe5f4da98a1d2606dc791e6c1feb`，测试 SHA `627090965b1a3116804a0a04c9413d5632d788d27cceeb4b8f12896ff6883e1`；已以包路径 `python -m unittest tools.test_client72_sdk_configsync_event -v` 实际运行6项通过。v2 不增加被执行的 SDK 函数、writer 后继、未知 API 模型或自然进程读取；仍需主线独立第三次复核。

主线已实际第三执行，`r2-configsync/native-proof-main-third.private.json` 4670B/SHA与v2双跑逐字节相同，6项包路径测试通过。主线独立重读SDK/source/8依赖、四调用与六系统边界等9项核对，证明 `r2-configsync/main-review.private.json`；没有接受仅布尔摘要代替原始路径。自然事件的有限后继由[独立准确观察入口](CLIENT_72_SDK_CONFIGSYNC_OBSERVER.md)推进；普通构造模型、writer-entry cut、自然caller、完整writer返回、路由与认证保持分列。
