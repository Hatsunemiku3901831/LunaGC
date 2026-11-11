# Proud 热字典 Store → TryInsert 离线样本（2026-11-11）

任务 `E-01d1/R3-PROUD-ACTUAL-ADOPTION/HOT-INSERT`。三组自有缓冲区样本在离线 CPU 中执行两段固定原代码，并从原 Store 的 RET 返回 sentinel。主线第三次独立复核已通过；仅关闭条件热写入子项，不能晋级父项或 ready。

冻结输入：178 B Store SHA-256 `dee346d2bb53b96567d9de97db6ffca4c7cc6354d8f3e3033fd64a4aedfe2389`；1455 B TryInsert `d7572088110eaf89adc9dd34d52a5db9d20e5f904f394592f09ecfeeb7b1985c`；私有合同 8369 B `016a317ab409a0f755d665a8e2b539be5d54fd622185225557858a1755605018`。入口核验合同、规格、Store 双份与 TryInsert 三份的完整代码/汇编/pdata/boundary/extraction、静态来源三份、原条件输入摘要、离线 CPU 包和五个客户端输入的 SHA；逐字节确认汇编完整覆盖两 body，唯一允许 E8 为 `154d400ab → 1593e7bf0`。来源清单在私有报告中，不读取被拒 A/B 草稿或产物。

运行 `python tools/migration72/proud_actual_adoption_hot_insert.py fresh3` 和 `fresh4` 的独立新进程结果完全一致，报告 SHA-256 `cb3bb72bafce6dce81f313f98523ac9a022bed1f5813def514cc4f482e1064fb`。每例原执行 122 条指令、67 次数据访问，原入口、唯一原调用、原 RET 和 sentinel 均到达，模型调用为空。热路径逐 PC 限定；任何偏离、未授权调用或读写、预算耗尽立即停并保留记录。两例为人工宽度边界，第一例只取旧报告条件输入 `(221,1)` 和索引 ID `22101`，并不表明实际资源行已构造。

独立门禁 `python -m unittest tools.migration72.test_proud_actual_adoption_hot_insert -v` 五项通过，按另一套计算核 64 位 pair、U32 ID、hash、20 B entry、其余 40 B、12 B buckets、count/freeCount/freeList/version；逐条核七项非栈原写及两次完整报告一致。输入漂移在启动 CPU 前拒绝。测试初次把 Unicorn 回调的有符号 64 位值直接与无符号值比较而失败；对按访问宽度归一化后通过，原始负例与最终日志保存在私有目录，内存字节始终独立比较。

首轮 `fresh1` 未执行目标指令：装入 TryInsert 时发现其跨两页而只映射一页；保留失败报告，修正映射并根据原汇编修正 RET 位置后执行。`fresh2` 为收紧逐 PC 门禁前的成功结果，最终两次确定性核验使用 `fresh3`/`fresh4`。报告中的写值保留 Unicorn 原始有符号显示，高位值以相应宽度解释；原内存字节与完整键均已独立检查。

主线实际运行 `main-third`，报告与最终两fresh逐字节相同、同SHA；另独立5门禁通过。主线审读实际原PC与七项非栈写、完整pair/ID与原RET，接受的局部结论仅为 `originalHotPairInsertExecuted`。`actualProudRowConstructed`、`actualProudMapProducerExecuted`、`actualPairLookupHit`、自然 interface dispatch、门槛、Java 公式、resourceReady、完整 Avatar 与 D-02 均未证明。未 attach 或操作真实客户端、执行目标 Win32 DLL、启动 JVM/数据库/端口、构建或部署。lookup 必须另行确定原 body 与自然来源，不由本例推断。
