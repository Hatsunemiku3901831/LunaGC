# F-03d3s2 / LoadedBundle 单窗原件（2026-11-11C）

父项 `D02-R1`；本批为 `F-03d3s2/SCENE-READER-LOADED-BUNDLE-ONE-WINDOW` 完成两次固定来源采窗和纯原件审核。独立集成第三尚未由本批执行，交主线唯一集成执行者。这里只报告固定原件与边界，不解释新窗的业务语义；自然 reader/backend、Scene、resourceReady 和 D-02 均未验。

## 前置、来源与原始边界

主线确认 430B 调用者集成第三通过：其 `main-third/` 九件与双 fresh 一致，`main-third-audit.private.json` 为1366B、SHA `308096b48e49c8c19db58899b4d7bddc0d929d18d5bb1541c02d5fa3099c50ee`，审计 passed/nine-identical/currentPEReads=0；另有 `integration/acceptance1/independent-review1.private.json`。此前本批只实现代码并跑人工夹具，未采新 PE。

固定 request `local/migration72/d02-resume-20261111c/scene-backend-semantics/loaded-bundle-one-window-request.private.json` 为15563B、SHA `0174f4ee26b991b3c417dd0d9e2dfba2d283dcb6a6eb25632e09eca92551af05`。两次分别核 request 所列全部 source/tool pin、原工具的被依赖旧 helper pin、profile、禁止 globals 的 metadata 和 signatures；核唯一 owner11120/m78349/入口、flags/slot 和五条参数索引、名称、类型。两份旧调用者的九件原件逐 byte 一致；430B 原 body/110条连续指令重建、旧报告引用和冻结审计均核对。新 PE 仅另取 caller `14713f57e` 原5B `e91d47c0ff` 并计算 signed rel32 指向 `146d43ca0`。

每次通过 `tools/game_path.ps1` 解析本机路径，核五 stock 实际 hash 和 profile；核 AOT cell/slot 两 QWORD。固定 exception directory 14344080B、SHA `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d`；其 unique exact/containing 是同一真实12B记录，给出完整 `[146d43ca0,146d43d7c)`、220B。采 body 前保存 boundary、pre-body、pdata；读取范围只使用该记录，且经正值、≤8192B、可执行及 file-backed 门禁，没有预读邻域或依据下一方法猜末尾。

两次均无第二 body、callee、global 内容、xdata、构造器、字符串/data table、目标 CPU 或 live 读取；exception directory 另账。寄存器和参数的业务意义继续以[430B 语义专题](CLIENT_72_D02_SCENE_READER_BACKEND_SEMANTICS_20261111C.md)为界，不将两个零断言成 cursor0 或整文件。

## 双 fresh 与纯原件审计

私有根 `local/migration72/d02-resume-20261111c/scene-loaded-bundle-window/`，`fresh1`、`fresh2` 九件逐 byte 一致。`audit.private.json` SHA `1909e4981b904fa9b5edd198200adfdb28c1757c81196b942a4c88ebf07b5ca1`，passed、currentPEReads=0。完整 body 220B / SHA `9f1480af9cb4063240eb6445a62813f945f1ac73bfc0730e8edec0dbb4690e9e`，连续解码70条；15条 call/branch/RIP 引用、1个 RET、无外部 tail。`body.asm.private.json` 保存每条地址、原 hex、指令；`extraction.private.json` 保存全部引用和返回表，纯 audit 重新拼原 hex、解码并核输出账。

| 两份各自的文件 | SHA-256 |
| --- | --- |
| boundary.private.json / pre-body.private.json | `d7996dbef8f5f511b180d52d61461eb202f137d8dc973408f612a1016c69bd45` |
| caller-tail.bin | `4c7ddb5f8e92fa13f6a541fc8ba3c7c4083c98c63f01b993086082cebb28a3af` |
| cell.bin | `68f5f1908ac9f789c3fc0b5e71c5b8af00962605561f7b8ea2cc4718f00a83da` |
| slot.bin | `9e34d84de2a80474e796e7f44e486bb524a4f3c227540c4d0a381e5134e97fe5` |
| pdata.bin | `9fc62c33a5cdf83281d5ffdb6f2cda30d6534e17bcc916afac0ff89540d10af0` |
| body.bin | `9f1480af9cb4063240eb6445a62813f945f1ac73bfc0730e8edec0dbb4690e9e` |
| body.asm.private.json | `0291bca14a6058ec4c27ee2033dc548525667a6781770c5e86181d836240fc98` |
| extraction.private.json | `b0d913ad0c9dd5cd42be8a44155be8994c5fcbb2ceff4099955a208dd54b725b` |

直接 CALL 目标依次为 `159ad6130`、`159c1aeb0`、`141122e00`、`141122120`、`147021cf0`、`1404c2be0`。全部准确 site、RIP 与条件边保存在引用表；不跟进、不解释这些目标。公开 A allocator 目标 `1405336c0` 与 Default root `157d97698` 未出现在本窗直接引用表，这不证明其他未审后继独立。A 第二返回及依赖、B 组合 loader 继续冻结；若后续发现准确拒绝边，仅保留该边并停止对应依赖。

## 夹具门禁

工具 `tools/migration72/scene_reader_loaded_bundle_window.py` SHA `ec67e532f23fcefadeb6e59f0e87a666df906e01fc34661e0832c17dc2f0aa5a`；测试 `tools/migration72/test_scene_reader_loaded_bundle_window.py` SHA `1b70338860bd417d993a7d2f28e080e65641c709722a2149951cf130ba7758a7`。执行 `python -B -m unittest -v tools.migration72.test_scene_reader_loaded_bundle_window`：8项通过，日志保存在私有根 `tests.log`。测试包含 source/profile/stock 失败前不进入 PE、禁止 globals、metadata 重复与声明/五参数漂移、原 E9 及错误边界、正负 signed rel32 和高64位地址、错误 AOT、缺失/重复/冲突 pdata、非正/超8192、非执行/非 file-backed、短读、解码不完整与超过2048条。模拟无记录、超限及错误节时检查 pre-body 和 failure 留存且未读 body。纯审计人工夹具还验证同一件单份被改及两份相同篡改均拒绝，且不访问 GAME_PATH 或 PE。测试日志中列出的2B body 是人工 fixture，不是新原窗。

纯 audit 读取固定源及保存原件，从逐条 hex 重建原 body，重新独立解码与比对报告；不访问 GAME_PATH、当前 PE 或目标 CPU。它不代替第三次来源采集。

## 主线第三与后继

仅唯一集成执行者从 checkout 执行；输出名须预先不存在：

```powershell
python -B -m tools.migration72.scene_reader_loaded_bundle_window --output main-third
python -B -m tools.migration72.scene_reader_loaded_bundle_window --audit fresh1 main-third --output main-third-audit.private.json
```

第一条期望220B、上述 body SHA、70条指令、15条引用；第二条期望 `passed: true`、`twoFreshNineFilesByteIdentical: true` 和 `currentPEReads: 0`，九件 hash 均同上表。失败保留原 failure 并停止，不扩大窗口或改参数。集成验收后交 Sol max 消费固定 raw，区分观察、推导和未知，不从本页机械清单推断 backend/read/refill。

本批未操作 Git/index、JVM/Gradle/Cargo/protoc、数据库、端口、进程、客户端、调试器或部署；仅新增指定离线工具、测试、专题和忽略目录原件。共享 Project/Harness/HANDOFF 与本地提交由主线负责。
