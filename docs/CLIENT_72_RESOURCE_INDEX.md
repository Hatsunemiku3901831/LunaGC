# D02-R3：固定 ctable 的资源索引读取研究

## 开始前的最小实验

| 项目 | 约定 |
| --- | --- |
| 目标 | 使用本机固定 ctable 实际字节与固定三客户端输入，确定索引打开/解码入口及逻辑资源名→identifier/block 的真实前置，优先取得可离线复验的索引数据。 |
| 已有证据 | 两个 ctable 副本同 SHA-256 `03b2478ba340c6fcda27a4988c6501c32665b1323022e927aaeeb1f338e4710b`；`LoadResourceIndex14713ea90→DeserializeBinary146f00000→AssetIndexBinaryReader ctor146d8bd20` 静态链，后者文件分支调用 `14146c630/141122120/141122e00`。 |
| 未知 | ctable 容器头结构、native 文件/entry handle合同、解码及完整性参数；`0x6f` 的准确角色；自然 IFix/原生配置分支和实际选择。 |
| 假设 | 固定客户端自带 native helper 能读取该固定输入；应通过实际函数和真实文件读数区分“外层容器读取”与“普通未解码 BinaryReader”。不预设密钥、算法、offset 或成功返回。 |
| 最小实验 | 第一阶段从原字节重新恢复指定 metadata 方法和精确 .pdata 函数，核 ctable 名称/路径选择和 archive/file helpers；第二阶段仅在入口/依赖明确后，以真实字节的窄 I/O 模型尝试实际原生调用，首个未知依赖停止并输出 register/call/input边界。 |
| 修改范围 | 独立 `tools/client72_resource_index.py`、对应 test、本页；原始反汇编、错误和输出均在 `local/migration72/d02-active/resources/index/`。不再改变已冻结的来源审计工具。 |
| 失败出口 | 通用 REA 容器探测不支持已记录；不改文件头求识别、不试旧密钥。未知 handle/metadata/IFix 返回不得设为成功；静态结果不能标为已解码或实际客户端。若需要进一步深度研究，交精确函数、已试样本、首个未知、最小补充输入。 |
| 验收 | 指定 metadata 名称/归属/地址与固定输入独立重算；代码按 .pdata 边界记录hash。若能实际读索引，需独立检查计数/非零逻辑名和block引用及截断/篡改拒绝，双新进程一致。没有达到时只关闭实际静态/限定执行子合同。 |

## 当前结果

已关闭限定的静态入口及原生路径/uint32 查找名字编码子合同；**尚未打开或解码 ctable**，不宣称资源就绪、revision 已选择或客户端验收。来源审计独立结果见 [资源输入](CLIENT_72_D02_RESOURCE_SOURCES.md)，其工具及最终 hash 保持冻结。

[独立工具](../tools/client72_resource_index.py) 在入口一次固定三客户端 SHA-256 和两个 ctable 副本，再直接从原 metadata 重算 9 个指定方法的 owner/name/address/参数个数，检查每项属于真实 owner 的方法区间。5 个指定 literal 从原字节重新恢复；15 个静态函数按各自 `.pdata` 完整范围记录原字节 hash、指令、直接/间接 call/jmp 与指定 literal 引用。没有使用旧 cache 的同名命中作最终声明依据。

## 静态事实与没有执行的分支

| 入口 / 实际固定声明 | 当前证据及边界 |
| --- | --- |
| `14b65aee0`，owner89475/method738725 | 非 IFix 路径使用 `ctable.dat`、检查目标是否存在，失败分支再使用 `ctable_streaming.dat`。本批没有执行这个初始化或文件复制；不能据本机两副本相同称自然选择已观察。 |
| `14d6d53a0→14d6e1570` | depot 具名 literal/loader 后继。通用读取在 `14d6e15f7` 调下一项；包装对象的 native buffer/count 与托管数组分支见来源专题。 |
| `146eb90f0`，CommonMiscs.LoadBinDataZipFile | owner11307/method80101，五参数；经 Runtime 同名方法 `147065360`（11089/78058）、provider 同名方法 `14713f3c0`（11132/78585）进入资源查询/读取。只静态恢复此完整调用范围，未执行业务读取。 |
| `147237b80`，ResourceIndex.GetResourceIdentifier | owner11099/method78158；同链前置 `CheckIsLegitAssetPath147234540`。真实 ResourceIndex/receiver、native-config/IFix 分支、文件选择仍未知。 |
| `14713ea90`，AssetBundleExternalResourceProvider.LoadResourceIndex | owner11132/method78581；在 `14713eb4a` 调 `AssetIndexDeserializer.DeserializeBinary146f00000`（10895/76986），成功才将所构建索引保存到 provider+30。没有执行成功分支或造索引。 |
| `146d8bd20`，AssetIndexBinaryReader ctor | owner10894/method76967。目标文件分支调用 `14146c630`，随后 `141122120(...,0x6f)` 和 `141122e00(...,0x6f,...)`，再构造 HandleStream/BinaryReader。这些后继目前只作静态调用合同；不猜 native handle 或已解码内容。 |

| 静态函数 | 原字节 SHA-256 |
| --- | --- |
| `14713ea90` | `59fa220e357cd609b242ba4f1c1c5726273bb91a730fc9363c6f2475e8d9b4ae` |
| `146f00000` | `3fc8e4bf83d5f5e85e968013dcc7d972db342beea9ebdd73aaf1ff63452662bd` |
| `146d8bd20` | `5d3918c531e001c536dd30765d3ddd49e8e6541a1092e2f8f82844a1ee5fbf4e` |
| `14146c630` | `39c03fb5f199194a882818eab0cfa19ab131292ce63c74df043b90b89d41af1c` |
| `141122120` | `69d44f0ee9fbd535778ea75f6715e03680a1f8b32d334e1296812c53db78ae2e` |
| `141122e00` | `e7ba3606c5c884e05c5e9e0b9e2068adc20c143f3a29edac1b10aef715e06097` |
| `141a12b90` | `16362e8a0e04380843b96fec2f7c2b5b04ad3f19935e3813588abd48e004da75` |

## 实际原生样本和模型

Unicorn 2.1.4、Capstone 5.0.9；未修改共用 Offline72。两个新进程实际运行 4 个路径样本和 4 个 uint32 样本，所有原生调用模型列表均为空。托管 UTF-16 字符串存储是明确夹具，GS+10 StackLimit 指向已映射的离线栈；native stack-probe 和 UTF-16→UTF-8 指令实际执行。UTF-8 函数的三个 `.pdata` 连续范围分别记录，没有把第一个范围的结束当全部函数结束。

- 真实固定文件路径、`资源/🙂/ctable.dat`、空字符串均在 `14146c630→1414d30b0` 后到 `140970e90` 的 native 字符串构造入口前停止。分别独立比较输出长度和字节与 Python UTF-8 编码；中文/emoji 覆盖非 ASCII 和 surrogate pair。没有执行构造入口的指令、分配 native 字符串、打开文件或解码。
- null 路径实际完整走到返回哨兵，原生结果为 0；没有将 null 改成成功。空字符串与 null 的不同路径被保留。
- `141a12b90` 实际将 uint32 `0/0x6f/0x80000001/0xffffffff` 生成 `00000000/0000006f/80000001/ffffffff`，同样在 `140970e90` 前取到 native 栈里的 8 字节并与独立格式化比较。这是传入查找函数的名称编码证据：静态的 `141122120/141122e00` 先经该函数，再交 `141136fe0/141137eb0`。**0x6f 目前对应这条链的 8 位小写 hex 查找名字，不是已证密钥或 byte offset。** 真实容器中该名是否可读尚未执行。

报告的 `instructions` 是 code hook 次数，包含停止边界观察，不是退休指令计数。各样本完整路径 SHA、实际 stop、允许范围/原字节 SHA、fixture 和版本均在报告；机器路径仅以 hash 进入报告，原始路径不提交。

## 验证、失败和复现

```powershell
$resourceIndexClient = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/game_path.ps1
python tools/client72_resource_index.py --client $resourceIndexClient --native-path --output local/migration72/d02-active/resources/index/native-review.json
python -m unittest tools.test_client72_resource_index -v
```

未指定 `--native-path` 时只做静态来源检查。完整报告只写忽略目录，既不写 protocol 摘要，也不晋级 required/runtime/ready。第三进程应比较完整原字节与 `native-final-2.json`，并逐项核限定范围。

| 冻结产物 | SHA-256 / 字节 |
| --- | --- |
| `local/migration72/d02-active/resources/index/native-final-{1,2}.json` | `a57966dabbc34b177d6a03bd76dfc91e02ba6bd9fe606e9974e66c41bb9dba13` / 312,462 |
| `tools/client72_resource_index.py` | `b7e1c58ccc6de5102b8e458c967a223bc2d070260b0b91e9830bf24e68e39273` |
| `tools/test_client72_resource_index.py` | `e72d817f6a9ffea352ce8365768a403a7981aabbe3ba2d3b8cc65e7682b974e5` |

5 项 metadata 断言实际通过：独立 70-byte type row、完整 64 位地址、非零 255 参数个数；错误 owner、错误地址/名字、截断成员明确拒绝。8 原生样本在实际 CLI 中检查独立 UTF-8/格式化/null 结果；不与 5 项单测混加。两新进程字节相同，证明 `index/determinism.json`。没有 Java/Gradle/Mongo、客户端或调试器执行，未构建部署；无运行变更不需重启。

首轮 narrow metadata 提取误用 0x40 type stride，owner membership 断言实际拒绝；改为复用既有 `STRIDE=70`，保留该断言和 `static-first-failure.txt`。REA 通用容器 open 对固定 ctable 返回 unsupported format、selection none；原结果 `ctable-rea-open.json` 留在资源目录，未重试未知格式或改头。静态结果由本机原字节/Capstone 得到，不冒充 REA 成功 Evidence。

本机探索 `open-boundary-first` 至 `sixth` 原始失败全部保留：先补实际纯 getter，之后识别真实 stack-probe/TIB 存储和 UTF-8 的后续 `.pdata`；没有把未模型调用改成业务成功。最终限定实验主动在 native 字符串构造入口前止，不再将这些已核纯路径工作列为未知。

## 下一项最小离线实验与深度交接

当前准确的下一条依赖是 native 字符串构造/真实文件 handle 和容器查找，仍可离线研究，不能说所有问题必须实机。无需重扫 2033 个 blk，也不需重做已关闭的 JSON/manifest 审计。

1. 已知实际 `14146c630` caller 入参为 managed path、edx0/r8d0/r9d2、栈第五参数0。`1414d30b0` 的 UTF-8 输出已验，后继 `140970e90→140970f80`；后者长度大于15时在 `140970fb9` 调 `141a2e450`，参数包含 size=length+1、alignment16 和调用方 allocator 标记。先核这条 allocator/copy 合同及真实 I/O 的 API 边界，再允许有明确范围的存储/实际固定文件读模型；不能给 allocator 或未知 opener 一个有利的假 handle。
2. 非空路径最终静态进入 `1419eccf0`，其首处分支依赖 `1415d36d0` 并可能改走 `141a08830→141a07f60`。必须从具体 native 状态/调用者恢复分支含义，或把可区分分支作为明确模型分别验证，不能默认只选成功分支。
3. 对固定 ctable 原字节实际执行 `141122120` 和 `141122e00` 的 `0000006f` 查找/读取，先取得 native output 的长度/SHA-256 和实际失败，再研究完整性、解压/解码。读取没有通过时不模拟一个正确的 reader payload；没有接口身份/文件读合同就停在首个未知函数，保留状态。
4. 只有真实输出已证，才继续 `DeserializeBinary` 的 type/name/asset/bundle/preload/block 段，校验非零资源名、引用、截断/错误与双进程重复。后续 `ResourceIndex.GetResourceIdentifier` 应对真实所审表名查询，不能构造人工 map 得出逻辑表→block 对应。

若该 native C++/handle/容器合同交给 Astra，输入就是固定三客户端原字节、上述两份同 hash ctable、已验证 `0000006f` 名称、实际函数/code hash、原始 `native-final-2.json`、本工具及 `open-boundary-sixth.json`；首个非平凡新工作是第1项的 allocator 与第2/3项的真实 opener/entry lookup，而不是重复冷导入 metadata 或让未知 I/O 成功。自然选择的 manifest/revision/loader 返回仍由主线独占实机取证，详见来源专题的精确缓冲清单。

## 2026-11-08 主线审核

主线审查真实工具、模型/停止边界及原始证明，以独立新进程第三回放和5项定向检查接收本限定子合同。完整raw `resource-index-third.json` SHA-256 `a57966dabbc34b177d6a03bd76dfc91e02ba6bd9fe606e9974e66c41bb9dba13`，与独立双跑逐字节一致；证明/日志位于`local/migration72/d02-active/main/`的`pending-contracts-main-proof.json`和`resource-index-main-tests.log`。准确声明/路径UTF8/u32转0000006f已验；资源manager/ctable及该entry内容仍未闭。 本项仅离线研究工具/测试/脱敏文档，无运行代码、Proto、配置、补丁或资源变更，因此无需服务重启；不改变ready/required/runtime或D-02客户端验收状态。
