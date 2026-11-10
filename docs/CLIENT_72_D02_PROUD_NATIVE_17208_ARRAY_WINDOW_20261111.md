# E-01/R3-PROUD-NATIVE-17208-ARRAY-WINDOW（2026-11-11）

## 范围与门禁

本卡只采集和机械解码一个已批准原生体，供 root 第三次独立审核；不是目标执行、文件参考或协议验收。上游私有请求 5887B / SHA-256 `3c61c935475bd1979685b51dce5d2689795c4e0ce9b2bbc4881984a7458ac327`，root 第三合同和两份 fresh 各 576784B / SHA-256 `4849718ddb2679ad26d821d2c74b867968e31ed11b5593d4abeb903f03eb355f`；审批限定 8192B、2048 条及唯一 `147e91d10`。每个 fresh 新 Python 进程重新解析当前 GAME_PATH，完整核五输入尺寸与 SHA；完整 7674B caller 对照两份原体和当前 PE，核 rawH mask、分支目标、reader/setup、E8 及唯一 17208/m130225 三参数 metadata/signature。上游元数据和 signature 先核完整 hash，再反序列化。

新读 ordinaryAOT cell `1422f9950` 八字节后，以本次 QWORD + `130225*8` 算槽并新读八字节，值为 `147e91d10`。本次 table/slot 数值恰与旧记录相同，来源是本次读取而非旧 P。固定 PE 异常目录实际读取 14344080B，SHA-256 `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d`，扫描 1195340 条，记录真实文件 offset/请求与实际字节；exact 与 containing 各唯一且同一记录。先存 boundary、pdata 与 pre-body；完整范围 `[147e91d10,147e91e59)` 为 329B，未超预算；完整连续解码 90 条。未读 xdata、邻体或 literal，未顺取其他 callee。

## 机械结果与首未闭边

入口保存 RCX 到 RDI、RDX 到 R15、R8 到 R14。`147e91d56` 判断 reader+20；非空时 `147e91d65` 以同 reader 和 EDX=4 调 `14f614760`。inline 路径检查 reader+10 及起始 cursor 后，`147e91d89` 从 `[rax+rcx+20]` 取 DWORD，与 `2bedd918` 做 32 位加法；`147e91d90` 写回 cursor+4；`147e91d93` 再 xor `33f5051c`，`147e91da0` 依零标志分支。这里是指令关系，并未计算当前样本值或推断 count/元素宽度。

非零分支执行分配相关调用，循环体在 `147e91ddf` 调 `147e917c0`，此前 `147e91dd9` 设置 RCX 为栈上槽、`147e91ddc` 设置 RDX 为保存的同 reader。该条件调用是**首个尚未闭合的后继 reader 边**：本卡未取其体，不证明其是否或如何消费文件，也不证明该分支实际发生。reader+20 非空的 `147e91d65` 同样是未展开的条件边；普通文件假设下才走 inline。其他 call、branch、RIP、显式 memory 与隐式栈清单均在确定性报告中，仅为机械清单。

上游 cursor 318 与 caller 在 nested 正常返回后再 +4 仍是条件关系；不能推断 nested 长度、候选 334 终点、元素 8B wire、58209 编码或业务别名。无 actual cursor、typed 对象成功、自然 backend/mode 或完整 Proud reference 证明；客户端新增验收 0，资源/完整 Avatar/D-02 状态不升级。

## 复核与产物

私有根为 `local/migration72/d02-resume-20261110b/c-proud-native-17208-array-window/`。双 fresh 的八原件逐字节相同：

| 原件 | 字节 | SHA-256 |
| --- | ---: | --- |
| boundary.private.json | 7249 | `b57eb991062e51dd1f3a989c19bddbbecfbb78c02edd53a33ddbf96a96627f64` |
| cell.bin | 8 | `68f5f1908ac9f789c3fc0b5e71c5b8af00962605561f7b8ea2cc4718f00a83da` |
| slot.bin | 8 | `96d5b8cf817264c765926909890c44c2265405bf7280d3a55dbdc542cfd1b755` |
| pdata.bin | 12 | `62389a665eb964b0fbdd2851350ff4d8572f11e48f3f2d1ebf7af76445c30c15` |
| pre-body.private.json | 7455 | `cad2576bbee545a1921ac7ef28904c30d555bc5ce2df3771efee7565944f74d3` |
| code.bin | 329 | `9a3e1976af758bf71514b1f2af1addc3b712d69ac6456f75330addc7751ab3bf` |
| code.asm.private.json | 10432 | `a77109620279d3047130899e91c7e8fe25736b9994b3da082baa56e3e493b79a` |
| extraction.private.json | 20036 | `01a94534e35fbb791f7ccaefbf5f23f81f1f3568cf37f4d0bc8c714395df81a3` |

离线 audit 17885B / SHA-256 `5e046a7b8876ce779617908d937d39be267beffde9adbf68d28a22025552cb04`；脚本 `tools/client72_d02_proud_native_17208_array_window.py` 17413B / SHA-256 `33bc7dde2533c77235438408ec2876dd76f594aa2a5ede533c9658248b2471d5`；测试 2458B / SHA-256 `a84d329128490252d4d9d6ac8a9a0f1e9a72b19f0d8eace6f635e8ffd7e73c93`。

```powershell
python -B tools/client72_d02_proud_native_17208_array_window.py --output fresh1
python -B tools/client72_d02_proud_native_17208_array_window.py --output fresh2
python -B tools/client72_d02_proud_native_17208_array_window.py --output audit.private.json --audit fresh1 fresh2
python -B -m unittest tools.test_client72_d02_proud_native_17208_array_window -v
```

实际 fresh 与 audit 均退出 0；5 项测试通过，覆盖 pdata 缺失/重叠/不对齐、预算、slot、连续解码上限及双份一致但 pre-body 被改的拒绝。初次运行因私有根目录尚不存在在 mkdir 前退出，未采集；建目录后用原 fresh1 首次实际采集，未发生安全/Daybreak 事件。本卡没有 CPU/native/emulator/live/REA、进程、网络、Git、共享构建、DB 或部署操作；离线工具和文档无需重启。root 独占第三审、集成、文档索引和本地提交，下一步是 root 复核本卡，再决定是否为条件 reader 边单独申请 source。

## 主线验收增量

root已独立运行5项门禁并生成第三audit，17885B / SHA-256 `5e046a7b8876ce779617908d937d39be267beffde9adbf68d28a22025552cb04`与双fresh机械结论一致。后续仅静态消费合同另行验收，见[数组合同](CLIENT_72_D02_PROUD_NATIVE_17208_ARRAY_CONTRACT_20261111.md)；源窗本身不代文件消费、自然parser或完整row。
