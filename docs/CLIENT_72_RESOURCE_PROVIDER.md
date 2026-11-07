# D02-R3：自然 provider 状态与新资源管理器读取边界

2026-11-07，接续[资源索引](CLIENT_72_RESOURCE_INDEX.md)及[native 字符串/reader 子合同](CLIENT_72_RESOURCE_INDEX_NATIVE.md)。本项只使用主线导出的自然运行内存、固定客户端文件和离线 CPU 执行；没有实机进程、调试器、断点、目标调用/写入、Git、Gradle、数据库或部署操作。旧 Sol/native 工具、测试及专题均未修改。

**新增事实是注册前缀、真实 vtable 和新管理器状态已经核对，并执行了新读取器的缓存分支及文件名查找原指令。ctable 仍未解码，未取得 `0000006f` 内容；自然客户端是否曾以这些参数读取 ctable 仍未知。**

## 开始前的小计划

| 项目 | 内容 |
| --- | --- |
| 目标 | 用新增自然输入区分旧 raw-view 未覆盖的 provider、缓存和资源管理器分支，推进准确的文件描述/读取边界。 |
| 已有证据 | 固定五 profile 输入、双副本 ctable、已核 native 字符串链、主线两轮只读导出及执行源码存档。 |
| 未知 | 自然 ResourceIndex 调用、实际传入路径、fileID/descriptor、选中分支、parser 实际输入、内容转换及逻辑索引。 |
| 候选实验 | 对照内存 vtable/代码窗与固定 PE；执行真实前缀判定；沿配置字节为 1 的加载器找到读取器；执行原生缓存复制、签名及路径/哈希查找合同。 |
| 范围与失败出口 | 仅新增 provider 工具/test/本页及忽略目录。遇未知路径/句柄时保留边界，不返回虚构 I/O 成功，不改头、不猜旧密钥、不把参数快照当已发生调用。 |
| 验收 | 输入/源码身份、每条读取 hash 和指针链、原生执行范围、独立边界/错误断言、两独立进程一致输出及主线第三复验。 |

## 自然证据的边界

忽略目录中的 `plain/initialization-router-elevated3.json` 有 42 条读取，`plain/initialization-provider-elevated4.json` 有 57 条读取。后者增加前缀和管理器等字段；**99 是两份快照的读取总数，不是 99 个独立结论。** 工具逐项核 error、长度、SHA、重复 label、读取地址、各级实际指针及 profile。两个导出具有相同 PID/creation FILETIME/module base；主线报告为第二轮原始启动、SDK 登录页停留、未提交账号。快照不是原子快照，也没有捕获 ResourceIndex 调用。

每份快照分别核三个对象的 18 个 vtable 槽：减去实际模块基址、加回固定 PE 基址后，54 个地址全部逐项相同。各 provider 的 VT+8、+10、+60 的 32 字节代码窗共 9 个也逐字节匹配。该证明只覆盖已读窗口，不扩张为整个函数或全部运行映像未变化。

执行源码以 raw 中的 `sourceSHA256` 对 `plain/source-snapshots/*.py` 做唯一匹配；不以当前仍可能继续维护的 sampler 文件替代执行版本。固定五客户端文件和两份 ctable 在每次离线 CLI 入口重新验 SHA。ctable 为 28,160,000 字节，SHA `03b2478ba340c6fcda27a4988c6501c32665b1323022e927aaeeb1f338e4710b`。

## provider 的实际类型与前缀

下表 VA 是固定 PE 的地址，运行时均按实际 B 重定位。

| provider | vtable | +8 / +10 / +60 实际入口 | 已证明的窄语义 |
| --- | --- | --- | --- |
| default | `1421e8a38` | `140b09830` / `140b09b20` / `14097aef0` | 默认打开/读取；accept 直接返回 true。 |
| registered-0 | `14220ba70` | `140984540` / 同址 / `1415a0da0` | open/read 是 `xor al,al;ret`；accept 比较配置前缀。不能把其他槽位也泛化为相同语义。 |
| special | `14220c260` | `1415c0480` / `1415c0860` / `1415c03e0` | 对象 +8 实际指向 default；accept 将调用转给该对象的 +60。special 的读取后继未恢复。 |

router 的 `+20` 是注册指针数组的指针，`+30` 是 **uint64 数量**，当前为 1。`1415b8030` 从末项向前试 accept；这与“+20 是内联数组”不同。`+88` 是 special 对象。FileCache 的 `+558` 当前为 0，只报告字段值，不据此推断全缓存不存在。

只在 VT 匹配 `14220ba70` 后解释注册对象 P+30 的 0x28 字节 native string：指针 +0 为 0，内联 +8 的前 9 字节为 `archive:/`，长度 +18 为 9。余下内联槽包含非字符串尾部数据，不能按 16 字节全量解释为路径。

原生 `1415a0da0` 实际调用 `141c46fe0` 做大小写敏感、指定前缀长度的比较。本工具执行 8 个候选：前缀本身和前缀加资源名接受；短前缀、大小写改变、前缀不在首位、`ctable.dat` 和两个固定磁盘路径均不接受。开/读 false 的 3 字节原指令也实际执行。**这些是离线路径实验，不是自然调用参数记录。**

## 配置 1 的新加载链

两份快照中 `B+5766598` 为 1，once guard 为 `8000002d`。静态 `1419eccf0` 根据 `1415d36d0` 分支；当前配置使候选关注以下链，而旧 raw-view 验证走的是另一分支。快照没有证明真实 ctable 已执行该链。

`141a08830 → 141a07f60 → 1419f1260 → 1419ea990 → operation VT+B0(1419ea1e0) → 1419f1030 → 1415a1bb0 → 1415a2680`。

- `141a08830` 返回 **静态对象** `B+55bc4b8`，不是指针 cell。其 +8 为 `B+57aa100`，+10 为另一个状态 `B+57aa170`，不能混用布局。
- 对 +8 指向状态 S 的自然 0x68 字节，S+0 是锁，S+8 是哈希表起点，S+10 是 sentinel，S+18 为 uint64 文件键数量 **38**，S+20 是 bucket vector，S+38 mask=63，S+40 bucket count=64。
- S+58 的 **425** 是 `141a08043` 对成功新增内部加载状态累加的另一计数，不能写成文件键数量。管理器本体 +1c 还有累计计数 437。单一时点计数并不证明键名或 ctable 已加载。
- `1415b5f00` 把路径经 `1415b60c0` 规范化后 intern，返回 ID 带 bit31，并通过输出参数返回 descriptor 地址。`141a09920` 使用 fileID 的 4 个小端字节计算 FNV-1a64，文件节点 +10 是 uint32 fileID；节点 +18 开始内部 offset 树，+20 是该树 sentinel、+28 是其数量。
- `1419f08b0` 的实际加载状态构造保存 fileID(+10)、descriptor(+18)、offset uint32(+40)、flags/size 参数 uint32(+44)。不能把 API 上游 64 位入参无条件叫作该处 64 位 offset。

### 新读取边界和实际原生执行

`1415a2680` 用状态构造文件描述 `1415b6b60`，先尝试预取/缓存，再构造 24 字节 reader：+0 file descriptor 指针、+8 cached window 指针、+10 uint64 当前 offset。调用 `1415b6ee0(reader, count, destination, outCount)` 读取签名 12 字节，然后 `1415aa740` 比较 magic `03626c42`、version 4/5、header size 至少 0x48。

本工具实际执行 `1415b6ee0` 完整 `.pdata` 范围和 `141fdf310` 原复制指令。cached window 采用固定文件**未修改**的字节片段，+0/+18 是存储指针的两种来源，+20/+28 是窗口起止。8 个案例覆盖头部、连续位置、非零窗口起点、窗口前/后不完整覆盖、空窗口、零请求及超过 32 位的逻辑窗口位置。输入不是一个真实客户端缓存对象，也没有声称大偏移来自真实文件。

8 个案例逐项比较独立切片结果、返回 bool、out-count、64 位位置和目标两侧 canary；所有 native call model 列表为空。没有 file descriptor 的缓存分支必须完整覆盖请求，否则 false、out-count 0、不移动位置；零请求在此分支也返回 false。不能照搬旧 reader 的零长/短读语义。

实际 ctable 头 12 字节 `0ef201b56156e331af51d2a0` 经 native 缓存读原样复制（true/count12/position12）；原生签名 leaf 随后返回 0。**它证明缓存读取分支没有替我们解码该视图，不能证明真实客户端读取失败，也没有执行完整 archive 构造。**

无缓存的同步分支经 `140adede0`，从 `140af0b20` 的 `WindowsSyncFileHandleCache::GetHandle` 取得句柄；首次缺缓存的源码可达 `CreateFileW`，随后 `SetFilePointerEx` 和 `ReadFile`。工具直接解析固定 PE import directory，核定 IAT `143bac168`、`143bac7c0`、`143bac6e0` 的三名字；不是只据日志字符串推断。该分支尚未离线执行，也没模型化 handle/Windows 返回成功。其并行读候选由运行时线程/全局字节选择，经 `1415c1050` 虚调用；线程与参数状态仍未知。

## 名字查找的原生合同

新增执行 `1415b60c0`：在显式离线 TLS 夹具内只把 `/` 换成 `\`，保留大小写，4 个样本含空串和混合大小写。没有观察自然线程 TLS，也未调用目标。

`1415beb80` 实际计算完整路径字节的 FNV-1a64，准确停在 `1415bf070` 前；后者按 bucket 和精确长度/字节比较查找字符串节点。5 个样本分别核 native hash、空 bucket、不存在/存在键，另外 4 个同长度同 bucket 的不同键必须不命中。节点均是明确人工夹具，不能作为真实文件 ID。5 个组内有 14 次 lookup，加 5 次 hash；不把同组步骤重复计为独立资源验收。

在文件注册表中，字符串节点 +10 是 `std::string`，+20 长度、+28 容量（容量<16使用内联），+30 是 uint32 ID。这个标准字符串布局与 provider prefix 的 native string 布局不同，禁止混用。

## 给主线的下一批最小自然输入

优先按已存在的 manager 首四项取得真实文件名，无需猜 ctable key，也不扫描整个堆。下列均是只读采样规格，本工具没有打开任何进程。

1. 重新读 M=`B+55bc4b8` 的 0x20、实际 S=`u64(M+8)` 的 0x68；记录 count=`u64(S+18)` 和 sentinel=`u64(S+10)`。读 sentinel 0x10，沿 `node=u64(sentinel+0)`、`next=u64(node+0)`，最多 `min(count,4)` 个节点，各 0x30；遇 sentinel、null、循环或读取失败立即记录停止。
2. 每个节点取 `fileID=u32(node+10)`。不展开节点 +20 内树。固定注册表 T=`B+5756ec0` 是静态对象（`1415b6919` 的 RIP-relative lea）。fileID=`FFFFFFFF` 无 descriptor；bit31=0 用 D=T+48，bit31=1 且 ID≠FFFFFFFF 用 D=T+c0。每个 D 只读 0x30。
3. `1415b6880` 的 deque 布局：D+10 为 block-pointer-array；D+18 为 mapSize；D+20 为 start；D+28 为 size，都是 uint64。index=`ID & 7fffffff`，必须 index<size；mapSize 非零 2 次幂。slot=`((start+index)>>1)&(mapSize-1)`。读 array+slot*8 的 8 字节取得 block，再读 block+`((start+index)&1)*8` 的 8 字节取得 UTF-8 名字指针。
4. 名字最多读 256 字节，按第一个 NUL 截断；无 NUL 只报告 truncated。保存每跳实际地址/长度/字节/hash/时间和同一目标身份。非原子采样不能证明未见的其他 34 项，不能由首四项没有 ctable 就宣布没有加载。

这个规格的来源是 `141a09920`、`1415b6040`、`1415b6880`；工具的 `descriptor_slot` 以独立 ring-wrap、奇偶、符号 bit、越界和错误 mapSize 测试覆盖。若找到可关联的 ctable 自然路径/fileID，下一步才限定读取该 entry 的实际 offset 状态和缓存/descriptor；没有关联时保持未知，不制造匹配键。

## 复验与交付状态

工具 [client72_resource_provider.py](../tools/client72_resource_provider.py) 和 [测试](../tools/test_client72_resource_provider.py) 只输出到忽略目录。运行：

```powershell
python -m unittest tools.test_client72_resource_provider -v
python tools/client72_resource_provider.py --client "$env:GAME_PATH" --snapshot local/migration72/d02-active/plain/initialization-router-elevated3.json --snapshot local/migration72/d02-active/plain/initialization-provider-elevated4.json --output local/migration72/d02-active/resource-provider-astra/provider-final-1.json
```

`GAME_PATH` 应由本 checkout 配置解析，不能照抄历史目录；第二次用新 Python 进程输出 `provider-final-2.json`。8 个独立单测已通过；两个冻结运行均为 36,979 字节，SHA `bd8908e4469da428a68bbed866d7cc2a285aae2fa4cd22160fdb2617e99c39b3`，逐字节相同。工具源码 SHA `16c5ab0f8a4f2d1a653cb99c8dd972c4b20b742a75e7adbc0f397edb905a7c7f`，证明记录在忽略目录 `determinism.json`。报告保存准确原生范围及每次执行路径 hash；单个 `.pdata` 可能只是函数一块，静态跨块范围单独标识，不能把首块当整个函数。

主线第三复验、索引文档、活动 checkpoint 和本地提交由主线统一处理。当前子合同可复验，D02/ctable 端到端保持未完成；仅离线工具/专题变更无需重启或部署。

## 2026-11-08 主线审核

主线审查真实工具、模型/停止边界及原始证明，以独立新进程第三回放和8项定向检查接收本限定子合同。完整raw `provider-third.json` SHA-256 `bd8908e4469da428a68bbed866d7cc2a285aae2fa4cd22160fdb2617e99c39b3`，与独立双跑逐字节一致；证明/日志位于`local/migration72/d02-active/main/`的`pending-contracts-main-proof.json`和`provider-main-tests.log`。两自然导出vtable/前缀/新manager及原生cache/map片段已验；未观测具体资源route，不把旧头拒绝当自然失败。 本项仅离线研究工具/测试/脱敏文档，无运行代码、Proto、配置、补丁或资源变更，因此无需服务重启；不改变ready/required/runtime或D-02客户端验收状态。
