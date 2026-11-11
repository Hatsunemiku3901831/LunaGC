# F-03d3s2 / Read core 正常续接 region（2026-11-11C）

任务 `F-03d3s2/SCENE-READ-CORE-NORMAL-CONTINUATION-REGION`，父项 D02-R1/R3。主线已接受204B前置的独立第三验收，入口 `integration/acceptance11/core-source-independent1.private.json`：七件一致、6测试、52指令/12引用、24B caller、pdata与ordinary零/AOT0已核。该验收保留0 RET、d3c正常落入与三条f85窗外边，未升级逻辑读取完成。

本批完成一个 containing region 的双 fresh 和纯原件审计；独立第三交主线唯一集成执行者。`141138d3c` 是同一调用帧的正常续接PC，不是新callee或声明入口；本页不执行该帧、推断实际读取或宣称自然reader/Scene/Proud ready。

## 固定输入与读取前门禁

唯一 request `local/migration72/d02-resume-20261111c/scene-read-core-semantics/normal-continuation-region-request.private.json` 为17425B / SHA `7c91fa80af6ac8beecdff1f6e531470b861a51f42182ae71e30c802ffed08a89`。两次均核 request 的源、工具、profile、metadata/signatures及实际五 stock 完整pin；复核前段204B/52条连续原hex与报告，末 `141138d3a:8b0f` 正常结束于d3c。新PE仅核原前缀末26B `33d2498bc548f777284c8bfd488bf04885c00f884b0200008b0f`；未重读完整旧region。AOT不适用于continuation，读取0字节。

固定 exception directory 14344080B / SHA `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d`。先按 `Begin≤d3c<End` 求 unique containing，exact separately recorded、可以为空；不把缺exact当失败。实际唯一 containing 恰也为唯一 exact，真实原12B记录定义 `[141138d3c,141138edb)`：415B，sourcePC offset0，覆盖sourcePC。与旧 `[141138c70,141138d3c)` 交集0B，distinct-new为整个415B；正值且≤8192B，可执行/file-backed。读取前已保存完整匹配列表、真实extent/覆盖、交叠及distinct ledger、预算/节决定、pdata和pre-body。没有读取邻域、拼旧段、聚合xdata或另取f85。

## 原件、边界与纯审计

私有根 `local/migration72/d02-resume-20261111c/scene-read-core-continuation-region/`；双 fresh 七件逐byte一致。完整region 415B / SHA `d837f527ce2dec86c4cbdf0464d772c989040d072cc8a232921114e193a865c2`，从真实Begin连续解码95条、19引用；sourcePC 为已解码指令边界。纯audit SHA `a7584f55cb7d0de9c53552fd1ed02f217f3c049dc9096321f61e630fb1c7ba03`，passed/currentPEReads=0/logicalReadComplete=false；以保存的逐条hex重建完整region并重新核重叠、pdata与来源，不访问GAME_PATH、当前PE或目标CPU。

| 双份各自文件 | SHA-256 |
| --- | --- |
| boundary.private.json / pre-body.private.json | `36ebd981e596dea823029152327986fa4771991a6035fb49752f8805ea430327` |
| predecessor-context.bin | `5722b9a3ead02ce42d5be72bed0dc06d9f84447b0dcb116b1239288c4f011075` |
| pdata.bin | `2575fbdb076d584f58d55aa669ff2112fe437d368322b8e327bcfdaeffc05366` |
| region.bin | `d837f527ce2dec86c4cbdf0464d772c989040d072cc8a232921114e193a865c2` |
| region.asm.private.json | `e55760cab2dbbd3c3870f5ac7469bba8f868ce68170bf0d7b4d54d90c30160dc` |
| extraction.private.json | `2573d6447ba7f5f3ea084ffaeed1de931d715e625604237f92936db3159fae39` |

原region内无RET；末指令 `141138ed3` 正常可fallthrough至窗外 `141138edb`。外部条件分支目标包括 `141138f06` 和 `141138efa`；前段的 `141138f85` 不在此region。另有直接和间接CALL，原site、原hex及未知间接目标均保存在引用表；不跟进或解释其目标。没有读取任何新callee/global内容、constructor、xdata、字符串/data table、目标CPU或live；exception directory另账。完整单个pdata region不等于完整逻辑read，后续字节、EOF、错误与返回由Sol max基于证据区分；A/B拒绝依赖继续冻结。

## 测试和第三次复核

工具 `tools/migration72/scene_read_core_continuation_region.py` SHA `0ab48f47e6f241c2102b5840ac926bc4ec1446b00fea7fef992497eee69e78f7`；测试 `tools/migration72/test_scene_read_core_continuation_region.py` SHA `886d06bb5ecfb545ab76def8a746ef7232638f810baea780cf13bc1bada5de5f`。`python -B -m unittest -v tools.migration72.test_scene_read_core_continuation_region` 7项通过，日志在私有根 `tests.log`。覆盖来源/profile/stock漂移、前段raw/context/末指令、Begin<PC而exact空的containing识别和真实交叠、缺失/重复/冲突、超限/错误节在region前停止并保留pre-body、短读/不完整解码/2048上限、sourcePC指令边界、外部条件边/末fallthrough及纯audit篡改拒绝。测试日志内2B为人工fixture。

仅主线唯一集成执行者第三次从checkout执行，输出名先确认不存在：

```powershell
python -B -m tools.migration72.scene_read_core_continuation_region --output main-third
python -B -m tools.migration72.scene_read_core_continuation_region --audit fresh1 main-third --output main-third-audit.private.json
```

期望415B、上述SHA、95指令/19引用；audit `passed=true`、`twoFreshSevenFilesByteIdentical=true`、`currentPEReads=0`、`logicalReadComplete=false`，七件hash同上表。失败保留实际诊断并停止，不改selector、扩窗或换方式。

本批未操作Git/index、构建、Java、DB、端口、进程、客户端、调试器或部署；未修改已接受projection及旧原件。Project/Harness/HANDOFF与本地提交由主线负责。
