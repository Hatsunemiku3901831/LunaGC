# E-01d1 / R3-PROUD-ACTUAL-ADOPTION：TryInsert 单函数静态窗口

## 范围与结论

按冻结请求，只做 Store 原调用、封闭泛型身份和一个被调用函数的离线静态提取。两次新提取及独立离线审计一致：函数 `[0x1593e7bf0, 0x1593e819f)`，1455 字节、380 条连续 x86-64 指令；源码机械记录 24 个 call 指令、63 个跳转指令，不代表每条路径执行或已理解每个目标。没有执行目标 CPU、附加客户端、读取邻接 callee 函数体或解码 xdata。这里只建立窗口，不断言重复键处理、最终写入语义、自然实例、资源通过或 D-02 完成。具体功能解释和第三次复核交主线。

冻结请求 `local/migration72/d02-resume-20261111b/proud/tryinsert-window-request.json` SHA-256 `820a132304496e5f6af3e8f215c3f9b5958394b3c122c0a476d568415ba6238c`；其列出的全部来源文件、两个相同的静态来源报告、继承 pins、便携 capstone 和路径工具均核对。五个客户端 stock 文件完整 SHA-256 按请求逐一核验；完整文件散列自然覆盖文件全部字节，所称不读取 xdata/邻函数是指没有专门提取、解码或解释这些区域。

- 原始 Store 调用点 `0x154d400ab` 的原件 E8 为 `e8407b6a04`，相对位移目标 `0x1593e7bf0`，与已固定 Store 副本一致。此边仅针对 wrapper 的 Dictionary 已非空路径，不证明冷分配、Default 或自然调度已运行。
- 独立来源中的实际类型为 Dictionary `1222`、封闭 class instance `50461`，键为 value type `80067`、值为 U32；依据已验 metadata 表固定原始字节，再次确认唯一 TryInsert tuple、所选 14 字节表记录、12 字节 spec 记录与 `0x1425fc4a0` 的 QWORD 指向该入口。metadata 表的 offset 为 **global-metadata 文件绝对偏移**，指针槽是 **PE VA**；不能把文件偏移套到 PE 基址。
- 先扫描原异常目录 14,344,080 字节以判断唯一 exact 与 containing 条目，结果两者均为同一条；仅保存该 12 字节记录 `f07b3e199f813e19503f3a05`，SHA-256 `5bdda9a523f3d9ec60d90f4f1ecfb99d922137a732bcaeb3acdba494a019bd2c`。读取函数体前已确认范围小于 4096 字节，解码后确认全程无间隙且不超过 1024 条。未读取 unwind RVA 所指 xdata 内容。

## 固定结果与验证

实现 [proud_actual_adoption_window.py](../tools/migration72/proud_actual_adoption_window.py)，SHA-256 `d80cb92c6a72199d4aaea19218f1d659fb462e0552ea84d493b02b6e682e33f0`。独立门禁测试 [test_proud_actual_adoption_window.py](../tools/migration72/test_proud_actual_adoption_window.py) 检查源哈希/大小漂移、E8、唯一及重叠 pdata、越界、绝对文件偏移、AOT 重复/错误记录、解码缺尾和指令数量上限；6 项实际通过。测试使用合成数据，不执行目标。

```powershell
python -X utf8 -B -m unittest tools.migration72.test_proud_actual_adoption_window -v
python -X utf8 -B -m tools.migration72.proud_actual_adoption_window --output <新的私有运行名>
python -X utf8 -B -m tools.migration72.proud_actual_adoption_window --audit <新运行名一> <新运行名二> --output <新的私有审计名>.json
```

私有目录 `local/migration72/d02-resume-20261111b/proud-insert-window/` 中，`fresh1` 与 `fresh2` 的七个产物逐字节相同；`code.bin` SHA-256 `d7572088110eaf89adc9dd34d52a5db9d20e5f904f394592f09ecfeeb7b1985c`。`audit1.private.json` SHA-256 `3c245f7c13df7662ea9bd243023adf6afe2e91ba65f3c368a301bb051cb875ed`；审计不重读当前 PE，而对两套产物、固定来源、边界、槽、代码哈希及再次完整反汇编进行比较。主线第三轮 `main-third` 与双fresh相同，`main-audit.private.json` 为同一42871B/SHA；6项门禁测试通过。只验原窗口及来源，不代替插入/命中/目标资源的运行验证。

A-SECOND-RETURN-CONTRACT 与 B 组合 loader 的拒绝阻塞保持不变，本包没有触碰其草稿。未改运行代码、Proto、资源或配置，未运行共享构建、JVM、数据库、网络服务及客户端，不需要部署。窗口中调用目标、分支和内存操作仅是静态指令记录，不读取它们指向的其他函数；重复 flag 2、key/value 布局、冲突路径与热依赖的业务含义仍须由主线依据证据另行审读。
