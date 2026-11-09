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

真实适配器 `python -m tools.sample_client72_string_consumer_inputs` 只读取主线封存的本卡 `live-plan.private.json` 和新确认后生成的 `live-ready.private.json`，要求 stock 监视器、唯一 runId、四源pin、进程创建时间和限定时间窗一致；不接受旧 monitor 或布尔 ready。本轮已取得新确认并实际采集partial，见下节；即使后续结果为candidate，也仅为非原子只读输入候选。

适配器在打开后及全部读取后分别核实际创建时间、主模块基址/大小、image与alive，保留每次观察，最后重新核五stock/profile/config并再查alive；任何身份/输入变化保持partial，finally关闭handle。OSError只记固定类型/代码，原始只读账本保留每次请求与实际返回；无写进程权限。code pointer与MI pointer分别保留，owned中故意不相等，不改指针或制造可执行成功。

主线直接重新执行来源门禁并核当前五stock、6份冻结批准/原执行来源、60合同pin与9旧窗，未新增原生窗/CPU；完整来源JSON与原sourceproof相等。16项独立审核还核三报告、真实schema的48次账本、carrier/Default/ordinary1243/Equals10288→10339、预算和所有未知标记。证明为 `main-review.private.py/json`，SHA-256 `2142fdee38161aa4908314ababd174bac5e0ff556dab459cf77d09a7ef85e0f8`。主线曾把record.requested与finalRepeats.width混用、以及把native tuple直接比较已序列化JSON数组，纠正前的脚本/失败proof保留；逐字段JSON复核无来源漂移，未改旧报告或重复采集。

stock90秒现场计划SHA-256为 `311fcc882494a7bd1380c1ad450cff9c443e5723f0b5586fa5aa2a35502299be`，固定四源/独立输出与54读2704B；外层proof另绑定helper/清单。AGENTS外部文字更新后清单/helper续版单独保留，不修改旧件。主线实际CheckOnly、SDK恢复及严格stock重核通过后，另取得本轮实时确认才启动；与SDK临时DLL实验分次串行。完整比较器消费者、配置命中/ability和D-02仍未验，无GS部署。

## 新stock实测的lazy诊断与非零getter

本卡 `run-stock-string-live2.ps1` 在新确认后提权执行真实stock monitor90秒，monitor退出0；sampler在限定时间窗实际打开同一创建身份，只读26次816B，首遍13次408B，13个精确span末尾均不变。前后创建/模块/image、end-inputs及最后alive通过，handle关闭。实际结果为partial，sampler退出1，按原门禁保留真实停止，不改成candidate。

真实TryGetValue owner/Dictionary1222/String与UInt64参数成立；**FindEntry slot13为0，getter slot2非0**。actual RGCTX前缀count37、实际definition pointer与两个原始字 `0c08c554` / `0c08c559` 匹配。冻结v2在任一槽为0时诊断后停止，故非零getter指向的对象、Default/Equals都没有读取；不能把它称为已经验证的10288 MI，也不能把getter误记为0或与旧全局空usage合并。当前完整String图与自然caller仍未验。

主线用同次26次raw bytes及原collector时钟顺序纯重放，完整collection逐字段相同；15项独立审核通过，`main-live-acceptance.private.py/json` proof SHA-256 `3267595c47da192382ff84d474d30dc8ede8a004cba510ad32eb7bc3b9d31b0e`。原sample为 `live/r3-string-stock-live1-20261109T131508Z-82f70c24.private.json`，monitor在计划指定的 `d02-active/plain/`。主线初审误设“两槽均0”的断言，原失败proof保留；按真实一零一非零修正审核，没有改raw、重采或放宽collector。

用户新反馈页面正常且正常退出后，主线核五stock/13现存保护、配置缺省与alias均保持，相关进程/固定TCP/UDP为0。没有安装DLL或启动GS/DB。下一项由A维护准确的非零getter后继合同，保留FindEntry未知和v2输出；只能按新批准范围继续真实输入，不能补slot、flag或返回成功。
