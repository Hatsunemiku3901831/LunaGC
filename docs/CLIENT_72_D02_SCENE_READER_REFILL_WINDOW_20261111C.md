# F-03d3s2 / Scene reader refill 单窗原件（2026-11-11C）

父项 `D02-R1`；本批为 `F-03d3s2/SCENE-READER-REFILL-ONE-WINDOW` 完成两次固定来源采窗和纯原件审计，独立集成第三交主线唯一执行者。这里只交原字节和引用，不推断状态桥、有效数组或 refill 成功；自然 reader/backend、Scene、Proud 当前采用、resourceReady 和 D-02 均未验。

## 来源、调用者与边界

主线已接受44B token producer 的第三次独立验收：七件与双 fresh 相同，6项测试、13条指令/3引用、caller、pdata 与 ordinary零命中/AOT零读已核。220B 语义已接受。此验收不证明自然 backend 或 reader。

唯一 request `local/migration72/d02-resume-20261111c/scene-token-semantics/reader-refill-one-window-request.private.json` 为16756B、SHA `ed0569bc4236b9bbd8c520104a0d444731571ba18cc09a502f6e68aabf161ada`。两次均核全部 source/tool、profile、metadata/signature 固定 pin；禁止 globals 解包 metadata；核唯一 owner42005/m350685/入口、flags/slot 和参数307541/type70/int32/非 byref。固定 caller `14b6accb0..14b6acd33` 为131B、SHA `c576218f5e911876ad28cce91c7e55df4dcc7ac468b7adbbcd56757fbf230cbe`，42条原 hex 连续拼接并独立解码。新 PE 仅再取 `14b6accc8..14b6accd5` 的13B `4889f9ba01000000e88b7af603`，核原指令边界和 E8 signed rel32 指向 `14f614760`。

每次通过固定工具解析 GAME_PATH、核五 stock 实际大小/hash，另核普通 AOT cell/slot 两 QWORD。完整 exception directory 14344080B、SHA `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d`；unique exact/containing 是同一真实12B记录，完整范围 `[14f614760,14f614946)`、486B。body 前保存 boundary/pre-body 与真实 pdata，核正值、≤8192B、可执行及 file-backed；只读取该完整范围，没有预读邻域或按相邻方法猜末尾。

两次每次1个 body、13B caller、16B AOT；exception目录另账。无新 callee/global 内容、xdata、constructor、字符串/data table、native CPU 或 live 读取；不访问通用registry、单独长度/read API。已保存的静态调用引用不等于这些目标已被读取或执行。

## 双 fresh 原件和纯审计

私有根 `local/migration72/d02-resume-20261111c/scene-reader-refill-window/`；`fresh1`、`fresh2` 九件逐 byte 一致。纯审计 `audit.private.json` SHA `cf6667ed8bebd014569fae0571017d71e13b0398a9fdec3bda2d12cd22de4833`，passed、twoFreshNineFilesByteIdentical、currentPEReads=0。完整 body 486B，SHA `dcf901e0952f9fa49adcf090c5b97510953019358c32f64bdf298840f8441999`；连续解码122条，45条 call/branch/RIP 引用、1 RET、无外部 tail。`body.asm.private.json` 每条保留地址和原 hex；`extraction.private.json` 保存全部引用与返回表。纯 audit 从 hex 重建 body 并独立解码，不调用 GAME_PATH、访问当前 PE 或执行目标 CPU。

| 双份各自文件 | SHA-256 |
| --- | --- |
| boundary.private.json / pre-body.private.json | `0ddb10a040d0e7444c69e32ddcb2bfd0faf445c2de4e6a06f126cca99ae7f899` |
| caller-setup.bin | `914a28d749391a976d800bd32861bf5599e093f642d992abff568352fb162d29` |
| cell.bin | `68f5f1908ac9f789c3fc0b5e71c5b8af00962605561f7b8ea2cc4718f00a83da` |
| slot.bin | `f5230329779362b678e559b384927f0db1aca9e454974023ab16f37e16368911` |
| pdata.bin | `18785dfee003692a9238c4ca2344cb752f2910e8ab6b34be861e192717ec7fbd` |
| body.bin | `dcf901e0952f9fa49adcf090c5b97510953019358c32f64bdf298840f8441999` |
| body.asm.private.json | `2f86e6324b5b82c4c3f5388144ed935d83e09180c8d1e006e30913184da18e48` |
| extraction.private.json | `db0b0e17a86fb3c482dbb4fe02132f1c423ee972f6207c5a7e6301bdda1301a0` |

直接 CALL 包括 `140514ee0`（两处）、`141123190`（一处）和 `1404c2be0`（两处）；另有8处间接 CALL，引用表保留 site、原指令和 RIP cell 地址，实际目标未知。全部准确 site 与分支以原件为准，未读任何目标或其数据。公开 A allocator `1405336c0` 和 Default root `157d97698` 未出现在本窗直接引用表，但间接目标未知，不能据此断言后继独立。A 第二返回及依赖、B 组合 loader 继续冻结；准确拒绝边只保存并停止对应依赖。

## 测试与主线复核

工具 `tools/migration72/scene_reader_refill_window.py` SHA `32c945ffdcc14385af7d170e672c3e15bccc36f30e62ccd4c466ff7f3801a45a`；测试 `tools/migration72/test_scene_reader_refill_window.py` SHA `04b0b1f1e6e094f463de0ae334eacfa53e3579c6758fad16ae4b1db7d2b0598a`。`python -B -m unittest -v tools.migration72.test_scene_reader_refill_window` 8项通过，日志在私有根 `tests.log`。覆盖 source/profile/stock 失败前不进 PE、禁止 globals、声明及参数错误、caller 原 bytes/边界/13B错误、正负signed rel32与高64位目标、错误 AOT、缺失/重复/冲突pdata、非正/超预算、非执行/非file-backed、短读、不完整解码/超2048条；缺记录、超限与节错误的 fixture 检查 pre-body/failure 保留且未读 body。纯审计 fixture 验证单份与两份同样篡改均拒绝，同时禁止 GAME_PATH/PE。日志所列2B body是人工fixture，不是新原窗。

仅主线唯一集成执行者从 checkout 执行，输出名必须事先不存在：

```powershell
python -B -m tools.migration72.scene_reader_refill_window --output main-third
python -B -m tools.migration72.scene_reader_refill_window --audit fresh1 main-third --output main-third-audit.private.json
```

第一条期望486B、上述 body SHA、122条指令、45引用；第二条期望 `passed: true`、`twoFreshNineFilesByteIdentical: true`、`currentPEReads: 0`，九件 hash 均同上表。失败保留诊断并停，不扩大预算或改方式。主线验收后交 Sol max 消费 raw；能否建立字段状态、读/refill及短读合同仍须该语义审核，不能把 opaque token 当有利 buffer 或编造 newArray 成功。

本批未操作 Git/index、JVM/Gradle/Cargo/protoc、数据库、端口、进程、客户端、调试器或部署；仅新增指定离线工具/测试/专题及忽略目录产物。Project/Harness/HANDOFF 和本地提交由主线负责。
