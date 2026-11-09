# R2 early classification：冻结 event5 的离线首诊与最小实现规格

本卡属于 `R2-CONFIGSYNC-EARLY-BRIDGE`、`R2-ROUTE-CONFIG-SELECT`。结论是：**在实际 live、accepted source、构建 epoch 与磁盘 DLL/fixture 的绑定下，旧 event5 可排除 name 分支，只剩 payload 的两种限额终态；无法再区分是哪一种。** 没有得到自然字符串、正文大小、NUL 缺失、分类成功或自然 Init/route 成功证据。此前[早期桥](CLIENT_72_SDK_CONFIGSYNC_EARLY_BRIDGE.md#本轮自然实测及停止结论)把 name/payload 都列为未知，是当时未完成源码与产物绑定时的保守范围；本卡仅凭下面的新审核收窄它，不覆盖旧输入或旧报告。

分支 B 只新增本页、`tools/client72_sdk_early_classification_audit.py`、对应测试与 `local/migration72/d02-parallel-20261109/b-sdk-analysis/` 输出。没有修改 Rust、原观察器/验证器、源 pin、限额、参数、转发或校验；没有 Git 写入、构建、进程/端口/客户端/调试器/REA 操作或部署。运行代码的最小实现交主线 Sol xhigh。本卡不晋级任何父项。

## 冻结同 epoch 的完整 join

输入主仓库 `7d1244f54e181dcc1d9b148b4c970c106946310f`，patch `8c255548e89c54d01f71c44dee0820831c7f0b87`。resources 既有 dirty 保留。审计不读取当前 GAME_PATH、自然参数、私有 reference 或配置原文，只读取固定代码/报告及既有构建产物。

| 来源 | SHA-256 / 绑定作用 |
| --- | --- |
| `r2-early-bridge/live2.private.json` | `d7f1f483ae293069dbd4611837377c4b076674d7fe04cf42c9a1cc1f79488771`；inputs 中的 accepted proof、production、fixture、五源及 manifest 身份 |
| `live2.private.json.reads.private.json` | `ad4fe1971aea1a3db7fd384b65d2596cd94560b69480cf9a127ccbf7dcc79c0b`；同次 9 读/1450B，首尾 72B DATA 相等，event=5、两个事实位=1、env/等值位=0 |
| `owned-main1/proof.private.json` | `394964693753e2f52e12cc298d8502ae7bcd8a0094536f48a8b703c2a71c1b04`；与 live 的 artifactAuditSHA256 相同，五源与两 fresh ABI 结果 |
| `source-final1`、`source-final2`、`source-main-third` 的 proof | 三份均 `8344e740ed4063dbcd03eac62ffdc7e6dc828987cecad5f60b4996b00a96e033`；五源与 accepted/live/build.sources 完全相同 |
| 实际 DLL | `20186081e073678510d0c162e6bbab1597e8f00b3154861809e90935932854a9`；与 accepted artifact、live production/installedProxy、主线 review 相同 |
| 实际 owned EXE | `eb498ab5fbacf1625d6126ae36c70fe40cafe46a56d344379675d1e77ce942f5`；与 accepted fixture、live fixture、主线 review 相同 |
| `main-owned-review.private.json` | `6b51fa169b4915d8e5738ee1991711fee78bb5e6d59a00551e7c5018a079b573`；12 项既有主线审核，production/fixture/accepted join |
| `main-build-epoch2.private.json` | `52ccc1769272057fcc652101da0e00514ef9b5dd97156cc9955a0303d1543053`；五源及实际修后 manifest/原 lock |
| Cargo manifest / lock | `f3a71cfb6415581cddcaf2dbcb654ca012b6ee4ae372b11e2719f89b64cb1a2e` / `8482fc5d3e86ac5416038e446431e729a5e55c338d8824407f9aab878c323b2f`；不把修后 manifest 冒充更早 source proof 的 manifest |

以上相对路径共同根为 `local/migration72/d02-resume-20261109/`。新审计对每个来源先查 SHA，再核 join；完整 26 项 pin 在新报告 `frozenSourcePins`。关键 early observer 源为 `e2c009a04d93c2f3653d9a0537b802088f0f0185af0088f72c466a49528335d6`，冻结 classifier 源为 `c93e4da7dd167fca267156e7ee20b7bcb6445c89ed1ff36475552949989c7b75`。源码、proof 或实际 artifact 任一变化都拒绝本旧 epoch 结论，不重算旧 pin 继续。

这次只复核既有两 fresh、每次 18 case 的 ABI 证据，没有重新执行它们。既有 GPR16/XMM16/RFLAGS/栈128B/入口及返回 LastError、高64返回、原调用一次、源不变和 drop 恢复保持原验收范围；它们不是原 EXE Marshal 执行证据。

## 实际 ABI、参数表示与 caller

| 原始来源 | 准确对照 |
| --- | --- |
| 固定 raw metadata，accepted `staticContract.rawTypedParameters` | method70148 / `6d80910` flags134、method70136 / `6e363b0` flags198，均 instance，两个参数均 non-byref `System.String`。因此入口为 RCX=this、RDX=name、R8=payload；不是两个 native char 指针的 ABI。 |
| bridge 完整 `6d80910..6d80a77`，SHA `08de9a1aabf8fe27c2883958457b6ee1713923f851cda50af036b20b72e4cc1d` | `6d80924 RSI←R8`、`6d80927 RDI←RDX`；`6d809ad/b0` 令 RCX=RDI 调 `534480`，`6d809b8/bb` 令 RCX=RSI 再调同 helper。`6d809c3/c6` 才把两个已封送结果装入 native RCX/RDX。 |
| helper 完整 `534480..534522` / `507600..507748` | 代码 SHA 分别 `2050a5e1bc7d2cd740bc79304abcd34f3f4ca65c454e5bd495ffe62a394086cc`、`b6a18926616c8714228a0d92937933d8fed66a53947d6fafa516c7c6a599de87`。前者 `53449c` 加 `0x14`，后者 `507615/507634/50763e` 按 u16 NUL 扫描。没有读取 managed 长度或猜 header。新审计复用原 instructions JSON，无新 PE 窗。 |
| implementation 完整 `6e363b0..6e3643b`，SHA `e6c8424f6441c49b91c44e45c7464216af1823c7e126a2902bcf998792733d28` | `6e363e2 RDX←RDI`、`6e363e5 R8←RSI`、`6e363ee` tail-jump bridge，普通路径保留参数。 |
| producer 完整 `19806d70..19807e2e`，SHA `27138e600927d7df25c9b727cdb3650bf6228c480795b0c5b63e3f63a5e91562` | `1980793c StringBuilder.ToString`，`19807941 RSI←RAX`；`19807966` 装既证 literal7643 `local_config`；`1980797a R8←RSI`，恢复帧后 `19807989 jmp R10`。附近 `198079a0 call` 是另一控制流，不能混用。 |
| parent 完整 `198035c0..198037cc`，SHA `ba5a02091e6878c793fbaae9c651d1aa4df9de4b57e5583e7726afd03080b3b5` | `198035f1 call LocalConfigSync`，准确 return `198035f6` 随两个 tail-jump 保留；不是 SDK export 后 `6d809cf`。 |

新工具逐条断言上表关键指令，同时依靠固定完整窗口与既有原始元数据证明。正确 name 全部通过本身不能排除任意错误 payload 指针；这里还要结合原 R8 producer、两个 Marshal 调用、instance metadata 和同产物 owned ABI，而不是仅看 event5 猜 ABI 正确。原 helper 的完整编码/CRT/GC、IFix 自然分支及 native export 返回仍未执行/未证明。

## event5 的准确分类边界

early `inspect` 的 name 循环固定比较 `local_config\0` 的 13 个 UTF-16 单元。它只会返回 `NAME_READ=3` 或 `NON_TARGET_NAME=9`，通过后才进入 payload。它没有 name 长度扫描或 OVER_LIMIT 出口。复用的 `classify` 只产生2/6/7/8，也没有5。因此这个已绑定产物的 event5 证明 name 比较通过，失败发生在 payload 复制/解码预算层，JSON 分类未开始。

payload 的两个出口都返回5：

1. `0..32768` 的逐单元扫描没有在预算内遇到 NUL。上限包含首次 NUL；最后允许位置为 index32767。该状态只表示扫描预算已用完，不能区分下一槽才有 NUL、稍后才有 NUL或根本无 NUL。
2. 已在预算内遇到 NUL，strict UTF-16 解码成功，但自有 UTF-8 结果超过65535B。它发生在 JSON parser 之前，不能据此判断 JSON 合法性或 URL 形状。

旧9读账本只保留 code/72B DATA，没有保存这两个出口的区别。**离线材料不能还原自然调用到底经过哪个出口。** 当前准确首缺为“同一已证入口内，payload 的两个固定终态之一”，producer/时机就是原同步 `copy_payload` 的两个 return 点；不需要读取新的自然字符串、长度、header 或参数指针。

## 自有正反输入与实际执行边界

新工具是标准库 Python 的不可变 owned bytes 模型。高64地址仅为本机 bytes 的标签，没有 OS 读取、PID、ctypes、网络或 CPU 仿真 backend。模型独立构造 owned key/URL sentinel 和双层 JSON；不读资源配置原文。它验证分支区分力，不宣称与 serde 对任意 JSON 等价，更不冒充 Rust/原 Marshal 新执行。

| owned 输入 | 冻结语义结果及可区分假设 |
| --- | --- |
| 精确 name、合法双层 JSON | 2；env/reference 正例 |
| 未知 caller | 0；name/payload 零读 |
| 不可读、超长或大小写不同 name，以及交换两参数 | 3或9；payload 零读，无一产生5 |
| 不可读 payload / 已终止非法 surrogate | 4 / 10；与5独立 |
| 有效 JSON，NUL 在最后允许单元 | 2；读取恰到首次 NUL |
| 有效 JSON，真实 NUL 在预算后一槽；与完全相同前缀的无 NUL 输入 | 两者均5/unit-scan；读地址序列完全相同，证明 event5 不能推断缺 NUL |
| 有效 JSON，三字节字符 padding，自有 UTF-8 恰65535B / 65536B | 2 / 5-utf8；两者均在 UTF-16 单元预算内，证明两个5来源不同 |
| 未终止的 surrogate 前缀耗尽 unit 预算 | 5/unit-scan，尚未解码，不能冒称非法 UTF-16 已被检查 |
| `url` 直接 object | 8；双层序列化错误不会在已完成复制后变成5 |
| reference 不匹配 | 2、等值 false；不伪报分类失败或成功认证 |
| NUL 后附额外 owned sentinel；重复 terminal 调用 | 首次 NUL 后及终态后零新增读取 |

18 个 owned 模型 case、13 项定向测试通过。测试另核实际来源 join、混 epoch 拒绝、首尾 DATA 变动拒绝、actual caller/helper 指令、高64字符区和输出不含 sentinel。所有计数只属于 owned 输入；没有发布自然复制次数、内容长度或 hash。

## 交主线 Sol xhigh 的最小实现规格

旧 early source、verifier/adapter、accepted proof、artifact 和所有旧报告全部冻结。建议新增独立 `sdk72_config_early_split_proxy` example/observer/fixture，以及独立 verifier、Windows adapter 和专题。新输出在另一个私有目录。复用冻结 core、loader、References/classify、fixture.S、静态 binding/trampoline 逻辑；不得把旧 source pin 改为新 SHA 或让旧 proof 接受新产物。旧工具仍应能按本页全部 pin 重放旧 event5。

允许的行为差异只有固定终态，另有独立导出名以隔离产物：

| 路径 | 新状态 |
| --- | --- |
| payload 扫描退出循环 | 新 observer 常量 `PAYLOAD_UNIT_LIMIT=11`，label `payload-unit-scan-limit` |
| NUL 后 UTF-16 成功但 UTF-8 超限 | 新 observer 常量 `PAYLOAD_UTF8_LIMIT=12`，label `payload-utf8-limit` |
| 其余路径 | 沿用原0/1/2/3/4/6/7/8/9/10与全部成功/失败语义 |

旧 verifier 继续把5解释为历史 `over-limit`，不替它猜出口。**新 split verifier/collector 不接受5**，其合法枚举为0/1/2/3/4/6/7/8/9/10/11/12；新产物若出现5就拒绝固定状态，避免意外继续合并。11/12与其它失败一样只能输出 status 和两个事实位，env/两个等值位必须为0。无需新增阶段位、计数、时间序列或 DATA 字段。

DATA 保持72B、event仍offset56，字段顺序/Atomic与release发布、once及内存读取时机均保持。建议独立导出 `Sdk72ConfigEarlySplitProxyState @48 DATA`、active `sdk72_config_early_split_inner @49`，沿用明确 dormant `sdk72_config_inner @50`、`sdk72_key_input_inner @51`；原47个转发保持，共51项。callback符号也独立，只有一个新EXE hook。新导出、source epoch、.def、实际 DLL/fixture、manifest与每个旧依赖 pin 由新 accepted proof 全部绑定。新 example 的 Cargo 声明由主线统一处理，不覆盖旧 Cargo epoch 记录。

原 caller、三个代码窗/caller窗、20B patch、完整284B搬移/返回+20、38B wrapper、原执行一次和 ABI 夹具保留。不要添加 native/helper hook、header/length读取、默认env、NUL补写、成功桩、UTF16宽松替代、跳签或原参数替换。限额仍为32768 units（含NUL）、65535 UTF8B；同步回调最大32781次2B self-RPM/65562B，另有原CPU栈return的8B直接读。

新 fixture 保留原18例，原 `over-limit` 改断言11，原 `utf8-over-limit` 改断言12；另外至少加入以下精确 case，可按同一现有完整ABI harness执行：

- `unit-next-slot-real-nul`：有效 owned JSON，NUL 正好在扫描预算后一槽；11，拒绝读取该槽。
- `unit-identical-prefix-no-nul`：完全相同预算内前缀、没有该槽；11，读取轨迹和前例相同。
- `utf8-last-allowed-byte` / `utf8-first-rejected-byte`：有效 JSON 的 ignored 字段用 U+0800 和必要 ASCII padding，strict解码后的自有UTF8分别恰65535/65536B，且units均在预算内；分别2/12。
- `invalid-surrogate-unit-limit`：扫描预算内均非零surrogate且无NUL；11，不提前改报10。

原 `nul-boundary` 覆盖NUL在最后允许槽，继续2。未知caller、近似name零payload读、所有终态零进一步读和源对象不变继续核对。新 verifier测试须覆盖11/12的固定输出、分类数据为零约束，以及5在新split状态下被拒绝；同时保留旧 verifier的5纯重放。Python owned模型不能替代新真实Rust fixture的执行。

## 下一次自然实验合同

本卡不安装或启动。主线先完成新产物实际release/locked/offline构建、两fresh完整owned ABI/新正反例、准确导出/72B DATA/完整搬移恢复、独立第三审核与新的source/accepted/artifact join。随后准备新的安装/正常退出/stock恢复脚本、五stock/13保护/进程端口盘点和新实时ready，不复用旧PID、base、输出名或ready。

唯一新问题是“准确caller的payload停在11还是12，或在保持限额下出现其它明确终态”。继续原90秒自然monitor；外部collector仍仅9读/1450B/max512B，且只读code与自有72B DATA。同步字符串观察预算不增加，未知caller不读参数，首个准确caller只尝试一次，不清once/重arm。不得保存自然payload/key/URL、指针、hash、长度或实际复制次数。

任何来源/身份/代码/ABI不等、首次真实读取失败、未知状态、预算耗尽或终态出现都按原路径停止并保留固定失败；不安排同参数第二次试跑。11只证明扫描预算终态，12只证明UTF8结果预算终态；两者均不证明NUL缺失、JSON形状或SDK行为。只有2才可记录managed输入分类和允许env/等值bool。无论哪种结果，原Marshal、SDK export/writer返回、自然Init/route/认证与D-02均单独验收。实验结束由主线正常退出、恢复stock、删除仅自有alias，再核五stock/13保护和进程/端口。

## route/env 的准确剩余输入与复现

本次复用producer已证明：`19807756` 从ConfigModel+58装env，`19807759`置R8b=true，`1980775c`调用`LoadLocalConfig`；完整payload来自`1980793c`的StringBuilder.ToString，而非直接把ConfigUrlModel或nativechar*传入bridge。完整env/cp_app_id选择、自然env值、真正native writer返回和路由仍未知。自然准确caller已到达，因此解释本次event5不依赖继续UI RegisterTask producer；`R2-PLUGIN-UI-REGISTRATION`本卡没有扩展。

```powershell
python -m tools.client72_sdk_early_classification_audit --output local/migration72/d02-parallel-20261109/b-sdk-analysis/final-first.private.json
python -m unittest tools.test_client72_sdk_early_classification_audit -v
```

输出路径必须新建且位于本卡私有目录；任一冻结输入漂移立即拒绝。只修改离线工具/测试/文档，无需便携重启。主线统一更新Project/Harness、审核及本地提交；分支B没有提交或推送。
