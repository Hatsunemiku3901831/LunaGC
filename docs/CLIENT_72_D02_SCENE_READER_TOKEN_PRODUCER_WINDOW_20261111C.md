# F-03d3s2 / Token producer 单窗原件（2026-11-11C）

父项 `D02-R1`；本批为 `F-03d3s2/SCENE-READER-TOKEN-PRODUCER-ONE-WINDOW` 完成两次固定来源采窗和纯原件审计，集成第三交主线唯一执行者。此处只报告原件和门禁，不分析 token 编码或业务语义；自然 reader/backend、Scene、resourceReady 和 D-02 均未验。

## 输入、调用者与边界

主线已确认220B LoadedBundle 窗口集成第三通过：`main-third` 九件与双 fresh 相同，8项测试和70条/15引用独立核对，审计 SHA `1909e4981b904fa9b5edd198200adfdb28c1757c81196b942a4c88ebf07b5ca1`；独立复核为 `integration/acceptance2/loaded-bundle-independent1.private.json`。

唯一 request `local/migration72/d02-resume-20261111c/scene-loaded-bundle-semantics/token-producer-one-window-request.private.json` 为15731B、SHA `eb57e978e7792002c4f8589529621add7080c00093e49125622e6144494a0ba7`。两次分别核所有 request source/tool pin、profile、metadata/signature 完整 pin；metadata 以禁止 globals 的方式解析，目标 `141139040` 的 ordinary registeredAddress 命中为零，AOT 不适用且读取0字节。复用已保存的 native caller `141122e00..141122fa2`：418B、SHA `e7ba3606c5c884e05c5e9e0b9e2068adc20c143f3a29edac1b10aef715e06097`；旧 report 指令连续拼接和独立99条解码与原 body 相同，并核 `141122f32` setup、`f35` E8 的准确指令边界。新 PE 只另核8B `488bcbe806610100`、signed rel32 指向 `141139040`。旧不同报告的三输入/五输入来源差异按 request 保存，不把旧报告冒称新五 stock 采集。

每次按固定工具解析本机 GAME_PATH、核五 stock 实际大小/hash。完整 exception directory 14344080B、SHA `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d`，unique exact/containing 为同一真实12B记录，完整 `[141139040,14113906c)`、44B。body 前保存 boundary/pre-body、所选 pdata，并经正值、≤1024B、可执行及 file-backed 门禁；仅取原记录完整范围，未猜 leaf 前缀、邻方法末尾或扩大窗口。

各次新 body 数1；AOT、其他 body、constructor、registry、length helper、global 内容、xdata、字符串/data table、目标 CPU、live 均为0；exception directory 另账。两次各自7件逐 byte 一致；独立纯 audit 只处理已存原件和固定源，不访问 GAME_PATH、当前 PE 或目标 CPU。

## 原件与引用

私有根 `local/migration72/d02-resume-20261111c/scene-token-producer-window/`；`audit.private.json` SHA `47a99cd8ca3ba43c9e3772289485f2d58c2ab6bd9abadce4cbf37971925d4154`，passed、twoFreshSevenFilesByteIdentical、currentPEReads=0。body 44B、SHA `67c5fed7ca83adc556a1eac06b479a740fb9ff38c428ff1e04d012f834933621`；连续解码13条，3条 branch/RIP/CALL 引用、2个 RET、无外部 tail。原 hex、完整引用和返回分别保存于 asm 和 extraction；pure audit 按 hex 重建 body 并独立解码。

| 双份各自文件 | SHA-256 |
| --- | --- |
| boundary.private.json / pre-body.private.json | `908aef3a8d9bd83e39aea37d57a6455dee12b143fcebca932b8d364bec7df4ca` |
| caller-setup.bin | `6d65774de7837d1d357000f25bcd2875d547ce1f3ec3e430a2f096453ee312fc` |
| pdata.bin | `5d48a007bde5416819d10df6d0a2b85cc6c88fcb8ad51aad759e84f0a51f4610` |
| body.bin | `67c5fed7ca83adc556a1eac06b479a740fb9ff38c428ff1e04d012f834933621` |
| body.asm.private.json | `543aa4cc505f6c216216ce14ebab48af109d9a76e86bb4b30728b0987bce344d` |
| extraction.private.json | `21f11c2b9b128d1987a00a451c2d7fb137a8b74fa98ec2f09eab6769e31f3db4` |

原引用为 `141139047 → 141139050` 条件分支、`141139058 → 145746908` RIP、`14113905f → 1415cb9f0` CALL；不读取或解释目标。公开 A allocator 目标 `1405336c0` 和 Default root `157d97698` 不在本窗直接引用表中；这不证明其余未审后继独立。A 第二返回及依赖、B 组合 loader 保持冻结；准确拒绝边只保存并停止对应依赖。

## 测试和复核

工具 `tools/migration72/scene_reader_token_producer_window.py` SHA `38d728c4ea7a438ccd138aeca9707b5ce52da96a064effbfab4e84315663b9bd`；测试 `tools/migration72/test_scene_reader_token_producer_window.py` SHA `d407d668e0b65deb980814bce0014e27aeb71eb47c42804fef2d7d8c5b3347d7`。`python -B -m unittest -v tools.migration72.test_scene_reader_token_producer_window` 6项通过，日志在私有根 `tests.log`。覆盖输入/profile/stock失败前不进PE、禁止globals、ordinary方法非零命中拒绝、418B caller及原指令边界/错误8B、正负signed rel32及高64位目标、缺失/重复/冲突pdata、非正/超1024、不可执行/非file-backed、短读、不完整解码/超过256指令；人工缺失/超限/错误节夹具验证 pre-body 与 failure 留存且 bodyBytesRead=0。纯审计夹具检验单份及两份同样篡改均拒绝，并禁止GAME_PATH/PE访问。测试日志中2B body为人工fixture，不是新原窗。

主线唯一集成执行者从 checkout 执行，输出名必须事先不存在：

```powershell
python -B -m tools.migration72.scene_reader_token_producer_window --output main-third
python -B -m tools.migration72.scene_reader_token_producer_window --audit fresh1 main-third --output main-third-audit.private.json
```

第一条期望44B、上述body SHA、13条指令、3引用；第二条期望 `passed: true`、`twoFreshSevenFilesByteIdentical: true`、`currentPEReads: 0`，七件hash均同上表。失败保留实际证据并停，不更换取窗方式或预算。第三验收后仅交 Sol max 消费原件，观察/推导/未知分别记录。

本批未操作 Git/index、JVM/Gradle/Cargo/protoc、数据库、端口、进程、客户端、调试器或部署；仅新增指定离线工具/测试/专题与忽略目录产物。共享 Project/Harness/HANDOFF 与本地提交由主线处理。
