# F-03d3f4d2 / E-01-R3-ITEM-ENTRY：fresh 原生后继

本卡只消费主线新自然联合采集的同一 epoch 图。原 GetDefault 已连续执行 25 条原指令并返回该轮实际 Default；完整 Avatar 入口连续经过 allocator、普通 definition1255 查询和比较单元，106 次执行前观察、105 条完成，准确止于 `1405337b9` 读取未采的 loader TLS index。没有分配器返回、锁/线程/GC 成功模型、Default 创建或完整构造结论。

实际 Equals 已由该轮槽、伴随 MethodInfo 和固定 AOT 绑定到 ordinary1243 的 method10339、preferred entry `159b6f300`。当前没有该函数的原字节或真实 extent，故 Equals 体执行为零；没有另提取、猜边界或按名称替换函数。Dictionary FindEntry slot13 仍为零，getter slot2 非零只用于独立 warm getter 消费。

## 冻结输入和代码来源

输入与所有下游完整 64 位指针均来自 `combined-successor-inputs/live-plan.private.json` 的 `outputPath`。plan SHA `e4c319402fc56bd33b19b0935c77bbda0304255e04570c7c50c29a2b648f9b4a`；实际 raw SHA `1a2e7509f6149f995cc0cc5c4ddc8f0c3f8acd297afcfc27c1eb9d5caee9c63e`；主线 15 项审核 SHA `d2b0a9a3389f7f0b8d0947efd6b72cfb6e54bf33731369e61ba3614f8daccd22`。完整 collector 账本、原时钟、35 首遍/34 unique 终读、69 请求/2704B 在离线再次逐项重放相等。

所有原窗绑定固定 EXE SHA `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`。新工具不读 GAME_PATH、不读当前五 stock、不调用依赖当前 stock 的 verify 入口，也不读取旧捕获的 heap 来填新图。代码按本轮实际 module base 映射，原 captured 函数指针和全部 pointee 保持，没有 preferred root alias。

| 原范围 | 字节 / SHA-256 | 冻结来源 |
| --- | --- | --- |
| `15746c870..15746c9ef` GetDefault | 383 / `fb37ad9d367cb55a85501b2ad6d8520a095aa0c40d9feaf37ade46cf8c103ef1` | `r3-string-comparer-input/getdefault-and-cache-windows.private.json`，SHA `fd1ea590071f564817a28c2a5017858fd8b1b46428c716f4b69dd2ddd43ba64f`；逐条已有原 bytes 连续重组再核全窗 hash |
| `1405336c0..140533864` allocator | 420 / `c6b7251e6025e0accc5fc41e2083852d59a67671875a338738bd3c922250f1a2` | `r1-allocator-successor/allocator-full1.private.json`，SHA `ee0e3d4b24cbd235b177b8ee7e6edfd20bd49feac89e1bda79b8d759d08274e2` |
| `1405269f0..1405269f5` trampoline | 5 / `bb3bd836fbc8d866dc73ea8958b74331d207192b579c09e99d87e8637454f8ad` | 主线 `main-frozen-reused-code/manifest.private.json`，SHA `ba2d6bacf6574cd62f0cffd3e50e85200bbd7c3613b858e10c63c2b4ce67cc1e` |
| `15a4d6b60..15a4d7671` ordinary lookup | 2833 / `2ae927cf60f8d3e5a4a1ba9595f41e61bc9a6d325d84bfc1bace8c6c27d2fff1` | 同上 manifest，绑定旧批准 SHA `03f2942396db30ec17e776288c8f3c2ed29d3c0e32c7387d84c74f24aeb19080`；只复用原代码 |
| `15183fe60..1518404dd` 完整 Avatar entry | 1661 / `1b6d394af556f8647e6ee911cd32de784078ab64e29fb9fa37d3368019cd0f5e` | 主线 `main-frozen-avatar-entry1/manifest.private.json`，SHA `4acdd657015a45c7ae8982271406333b3caf88325610b088a5627a89e0bac19d`；绑定 initializer 固定声明 |

共 5 个既有准确原范围、5302B；本代理新增原范围和提取均为零。上述路径均相对 `local/migration72/d02-parallel-20261109/`，`r3-string-comparer-input/` 则相对 `local/migration72/d02-resume-20261109/`。String ordinary1243/10339 的固定父链与 AOT 来源为原 `sampling-contract.private.json`，SHA `7722ea05b8fabc04527fafec20585b31f6a3d985ab683037c05e90b15e9df20a`。旧工具与相关 tools source pin 只做 hash 守卫；不导入其运行模型或旧 captured 图。

## 实际执行与准确边界

String warm getter 的 RCX 是本轮真实 MethodInfo10288。原指令读取真实 owner、两次真实 flag、rgctx slot0、static area 和 Default cell；25 条完整返回本轮实际 Default，无 captured 写入、无冷 helper 调用、无 host Default 返回。这不证明 Default 原创建、初始化或 factory 选择经过本次执行。

Avatar 前 10 条 preferred 地址与旧完整入口已验前缀完全相同，但读取的是本轮 List usage 和本轮所有 class/GenericClass 输入。原 `14053371c` 传 definition1255、返回地址 `140533721`，真实进入 trampoline 与 lookup。`15a4d6bd4` 成功读取本轮普通 root，`15a4d6bf1` 成功读取 `root+1255*8`；原 lookup 经原 ret 连续返回 allocator，随后原 `140533721` 读取本轮比较单元。普通槽不等于 closed List，比较单元也不等于普通槽；没有把 closed backref 当普通查询结果。

之后原 List `+ca=62` 和 `+b4=20` 分支真实继续，准确停止在 `1405337b9`，请求 **本轮 B+5eef080/4B**，错误保留为 `UC_ERR_READ_UNMAPPED`。这条 mov 未完成。最小首缺为 loader TLS index4B；下一条 `GS:[58]` 及后续 TEB/TLS/线程/GC 输入仍未知，单补 index 不能宣称后继足够。本卡不设计自然采集或 OS 成功模型。

Equals 的最小代码请求已放在 `fresh-successor-replay/minimum-successor-gaps.private.json`：仅一个覆盖 `159b6f300` 的真实 `.pdata` 函数及其准确 12B 记录，extent 目前未知，不跟随任何 callee。当前没有 owned 字符串原生 case；若主线后续批准代码并实际消费自有字符串，必须明确为 owned，不能称自然业务键。

## 共同 String 输入的可消费范围

`fresh-successor-replay/string-common-input-boundary.private.json` 等待主线审核冻结给 R3。可审核的共同输入是同轮 getter MI/owner/String canonical、slot0/static cell/实际 Default、ordinary1243 和 selected10339 函数/伴随 MI；实际 warm getter 已返回这份 Default。R3 原消费者仍需核自己的原代码和 live-in，不能把本次 getter 返回当作 FindEntry、配置或 ability 成功。

不可消费的结论包括：FindEntry MI 非零、Equals 体已执行、自然业务键、自然 caller、Default 创建、完整 Dictionary 查询、配置/ability、managed allocation、完整 Avatar/Handler、正式 g2 或 D-02。`function slot == MI+8` 只是本轮实际观测，原合同并不要求两者始终相等；本卡不改写它们。

## 复现和当前验收

独占源码为 `tools/client72_fresh_avatar_string_successor.py`，测试为对应 `tools/test_client72_fresh_avatar_string_successor.py`。测试只读取实际报告、冻结源及执行 host 负例，不重复 native CPU：

```powershell
python -m unittest tools.test_client72_fresh_avatar_string_successor -v
```

13 项通过，覆盖同账本/时钟、五准确原窗、byte/extent 漂移拒绝、缺比较字节不可借页零、真实 getter 返回及七 captured 读、完整 Avatar 前缀/两个 caller return、普通查询实际读和返回/比较、TLS 失败、Equals 未执行边界、预算、ASLR 及两处 owned Avatar 写。每个 case 一次连续 call 硬限 2M，没有切片重置。

两正式 fresh 组 `first.private.json` / `repeat.private.json` 均 **97274B/SHA `73e55971e1ab3785e9a13b7619bedda89da9a81fbcd548c740bfec90e60a6754`**，逐 byte 相同。每组 2 calls、131 次观察/130 条完成，host 224 请求/8194B（含实际失败4B及 2×34 精确终读），caller 合计 3 次/24B；分别限 4096/65536/单读208 和 16/128。

本代理累计恰 **4 个 native CPU call、262 次观察/260 条完成、2 个实际 TLS 失败**，理论指令上限合计8M；host448/16388、caller6/48。没有开发 native preview、丢弃失败或切片重置，Equals 体调用为零；每组实际结果和源码副本均保留。13 项定向测试分别针对首组和重复组通过，不增加 CPU 调用。`determinism.private.json` 记录累计数值和相等检查，重复组 `.execution` / stdout / stderr 和测试 provenance 一并保留；首组开始 UTC 未单独捕获，明确为空，只记录真实工具退出结果与报告写入时间，不倒填时间。

源码 18471B/SHA `80d41fb8c5d4c317bf17d9d3c0a2a84cd0abd88b379a614d4a3f4b47922c30ad`，测试 11113B/SHA `829d40ee1554298f8d2dbfd19750025660e05ef5dbaa9cd77f995e5682952daf`。共同输入边界 SHA `b0475a9154e3bd5a5213c41648dfe0f0a0bc7d8737cbd55a55a2cfcbc763dd13` 与最小后继缺口 SHA `7ed799658d2b517467673fc037e62131dc1e78b78bc23d1b0975db8a5574a488` 均为主线待审 proposal；本卡不自动冻结 R3 消费许可或执行新窗。

本卡只新增独占 tool/test、本页和 `fresh-successor-replay/` 忽略证据；旧工具、共有 helper、协议、冻结合同、Project/Harness/交接及既有输出不改。未执行 Git、构建/JVM/DB、进程端口、客户端/REA/网络或部署。Git/索引/交接由主线处理；纯离线工具和文档无需 GS 重启。

## 主线验收与共同输入冻结

主线审阅实际差异与双fresh原件，独立13项只读测试及13项证据审核通过；没有再执行CPU，累计仍为上方4call/262观察/260完成。审核`main-review.private.json` SHA `df49f401b2004d10510cbb3bbc916cfc72d38270d3f9cf5086701fbcf078ca78`核完整来源、同epoch、五原窗、真实返回/失败、预算及全部父项false。

在保留原proposal逐byte不动的前提下，主线新增`main-approved-string-common-input.private.json`，SHA `07494634d3d46ee26807e0f96672b3378bd50fa7451786801c988a86b0bea08e`，批准上述限定String图/真实warm getter返回供R3消费。原proposal仍为`b0475a91…dd13`，不能把wrapper批准外推到FindEntry、Equals体、Default创建或配置/ability。下一增量单独提取实际10339唯一原窗，原两组消费者与失败不重跑；allocator的TLS/TEB/GC仍需准确输入/语义合同。
