# E-01/R3-ITEM-ENTRY 和 F-03d3f4d2：String 共同输入采集器

本卡实现独立只读候选图，依次取实际 Dictionary `B+59ae108`、TryGetValue 10200、Dictionary 1222 的 String/UInt64 泛型参数、实际 FindEntry 10194 与同 owner 的 getter 10288，再沿旧 String 采样图读取实际 Default 和 Equals。零槽只读实际 RGCTX 前缀和两个定义字后停止；不调用 resolver、不写目标、不使用旧全局 String usage 或稀疏后备。采集不证明自然调用、比较器完整、配置/Ability 或 D-02 完成。

首遍上限 27 读/1352 字节，统一末尾精确复读后上限 54 读/2704 字节，单次最多 208 字节。失败、短读、无效地址、重叠矛盾、拒绝和末尾漂移保持 partial；一致复读不是原子快照。

冻结批准、旧来源及窗口验证使用 `verify_sources`。固定五 stock 只读文件校验和来源证明在忽略目录 `local/migration72/d02-parallel-20261109/r3-string-consumer/sourceproof.private.json`；五项、60 个合同 source pin、9 个旧窗口通过。本地 Python 依赖位于 `local/migration72/python`，系统 Python 未加载该目录时缺 Capstone。源证明没有访问活进程或执行原生 CPU。

人工内存的确定性 owned CLI（输入/输出必须位于本卡忽略目录，新输出不得覆盖）：

```powershell
python -m tools.collect72_string_consumer_inputs --owned-input local/migration72/d02-parallel-20261109/r3-string-consumer/owned-input.private.json --output local/migration72/d02-parallel-20261109/r3-string-consumer/owned-next.private.json
python -m unittest tools.test_collect72_string_consumer_inputs tools.test_sample_client72_string_consumer_inputs -v
```

主线第三与两份 owned 普通 1243 图逐字节相同，均31654B，SHA-256 `0f64dc4bbf6d0b3777e7fd1dea89e8150fed6ba2e1578ac1e55207800d73d1bf`。各首遍24读/1080字节，总计48读/2160字节，完整精确末尾复读一致。它们是人工内存，不是实机证据；17项测试另覆盖泛型有限父链、错误参数/owner/MI、错误或短读、冲突和漂移、预算、旧凭据及进程生命周期 mock。

真实适配器 `python -m tools.sample_client72_string_consumer_inputs` 只读取主线封存的本卡 `live-plan.private.json` 和新确认后生成的 `live-ready.private.json`，要求 stock 监视器、唯一 runId、四源pin、进程创建时间和限定时间窗一致；不接受旧 monitor 或布尔 ready。尚无ready或新monitor，实机gate未验，不能启动采样。主线审查后负责现场调度。新采样结果即使是candidate也仅为非原子只读输入候选。

适配器在打开后及全部读取后分别核实际创建时间、主模块基址/大小、image与alive，保留每次观察，最后重新核五stock/profile/config并再查alive；任何身份/输入变化保持partial，finally关闭handle。OSError只记固定类型/代码，原始只读账本保留每次请求与实际返回；无写进程权限。code pointer与MI pointer分别保留，owned中故意不相等，不改指针或制造可执行成功。

主线直接重新执行来源门禁并核当前五stock、6份冻结批准/原执行来源、60合同pin与9旧窗，未新增原生窗/CPU；完整来源JSON与原sourceproof相等。16项独立审核还核三报告、真实schema的48次账本、carrier/Default/ordinary1243/Equals10288→10339、预算和所有未知标记。证明为 `main-review.private.py/json`，SHA-256 `2142fdee38161aa4908314ababd174bac5e0ff556dab459cf77d09a7ef85e0f8`。主线曾把record.requested与finalRepeats.width混用、以及把native tuple直接比较已序列化JSON数组，纠正前的脚本/失败proof保留；逐字段JSON复核无来源漂移，未改旧报告或重复采集。

新的stock90秒现场计划已准备（SHA-256 `311fcc882494a7bd1380c1ad450cff9c443e5723f0b5586fa5aa2a35502299be`），仅固定四源/独立输出与54读2704B；外层proof另绑定helper/清单。AGENTS外部文字更新后清单/helper续版单独保留，不修改旧件。下一项由主线核当前五stock/保护/进程端口并实际CheckOnly，随后请求本轮新实时ready；与SDK临时DLL实验分次串行。完整比较器消费者、配置命中/ability和D-02仍未验，无GS部署。
