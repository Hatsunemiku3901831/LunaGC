# F-03d3s2 / Read 返回续接 region（2026-11-11C）

任务 `F-03d3s2/SCENE-READ-RETURN-CONTINUATION-REGION`，父项 D02-R1/R3。主线已接受415B前置的独立第三验收，入口 `integration/acceptance12/continuation-independent1.private.json`：七件一致、7测试、95指令/19引用、26B context、pdata与旧区域零交叠已核。本批完成一个 containing region 的双 fresh 与纯原件审计；独立第三交主线唯一集成执行者。本页不执行frame、推断实际字节或宣称逻辑read及自然reader/Scene/Proud完成。

## 固定来源与真实门禁

唯一 request `local/migration72/d02-resume-20261111c/scene-read-continuation-semantics/return-continuation-region-request.private.json` 为18700B / SHA `04e9bd904df2559bffda87b7b7d5d5c3281cf6edefbb27ee4633dbbca73705da`。两次核全部固定源、工具、profile、metadata/signature 及实际五 stock 大小/hash；完整核前段415B/95条连续hex、报告、末 `141138ed0` 和 `ed3` 指令以及落入 `141138edb`；新PE只取末11B `488bc34c8bb42498000000`。同frame来源和条件见[前段语义](CLIENT_72_D02_SCENE_READ_CONTINUATION_SEMANTICS_20261111C.md)；edb不是新方法或callee入口，AOT不适用且读取0字节。

固定 exception directory 14344080B / SHA `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d`；对edb按唯一containing选择，exact separately记录、可为空。实际唯一 containing 恰也是唯一 exact，原12B记录给出 `[141138edb,141138efa)`，31B，sourcePC offset0。分别与旧 `[141138c70,141138d3c)`、`[141138d3c,141138edb)` 的交叠均0B，distinct-new为完整31B；正值且≤8192B、可执行/file-backed。读取前保存实际匹配列表、范围/覆盖、两旧段各自及总交叠、distinct ledger、预算/节、pdata和pre-body。未读旧body邻域或聚合xdata。

## 双 fresh 原件与审计

私有根 `local/migration72/d02-resume-20261111c/scene-read-return-continuation-region/`；双 fresh 七件逐byte一致。完整region 31B / SHA `3ce6e2cb3e27002912cb0d2e42c8c2e318fa1ddcc5eea18a7e7499f053a40c03`，从真实Begin连续解码9条、0引用，sourcePC是原指令边界；末 `141138ef9` 为原RET，无末fallthrough。纯audit SHA `5fffe3d39c053a599469542dc3d0af43168548028810024d068068c40fb392ea`，passed/currentPEReads=0/logicalReadComplete=false；从保存hex重建并核两段交叠、pdata和固定来源，不访问GAME_PATH、当前PE或目标CPU。

| 双份各自文件 | SHA-256 |
| --- | --- |
| boundary.private.json / pre-body.private.json | `08fc46db7a0830389aa74eb502e0fb8b4f9bf579e93fa97cdb96b923c13d3b18` |
| predecessor-context.bin | `aef2e5e2ea6f9a45cf98a86d577d457552ecd746d07fc17ccc283356a686667f` |
| pdata.bin | `6891eb344ac8d478fcb19e6b896ab6ca04a47cda1c0f9f43565980da0b3be404` |
| region.bin | `3ce6e2cb3e27002912cb0d2e42c8c2e318fa1ddcc5eea18a7e7499f053a40c03` |
| region.asm.private.json | `54bc29917c4584f9511852c74979a0e5fd501657bef4fed373079698888cdaac` |
| extraction.private.json | `a8e555e956eb387c0d7283172d45ffcc1c29e9456a5528e870ffc79fcaae05ff` |

`f06`、`efa`、`f85` 均不在所选区域，保留各自来边及未知状态，未另采。RET 是该完整region内的原控制事实；不同来边的错误/EOF/完整逻辑read未由机械产物闭合。没有读取新callee/global内容、codec/copy/constructor、xdata、字符串/data table、目标CPU或live；exception directory另账。A/B拒绝依赖继续冻结。

## 测试与第三次复核

工具 `tools/migration72/scene_read_return_continuation_region.py` SHA `1324cac2bac188c4ec223b1e792978ef8528d7ccbf9f3ea4d70b891158eea49e`；测试 `tools/migration72/test_scene_read_return_continuation_region.py` SHA `fb50225a2487194127ebba25f9e99eb359f610a8a5b6dad729586e2095d73c53`。`python -B -m unittest -v tools.migration72.test_scene_read_return_continuation_region` 7项通过，私有根有 `tests.log`。覆盖source/profile/stock漂移、415B/context/末指令、Begin<PC而exact空、与两旧段各自交叠及跨两段总交叠、缺失/重复/冲突、超限/错误节先停止并留pre-body、短读/不完整解码/2048上限、sourcePC边界、外条件边/末fallthrough和纯audit篡改拒绝。测试日志中的2B是人工fixture。

仅主线唯一集成执行者第三次从checkout执行，输出名先确认不存在：

```powershell
python -B -m tools.migration72.scene_read_return_continuation_region --output main-third
python -B -m tools.migration72.scene_read_return_continuation_region --audit fresh1 main-third --output main-third-audit.private.json
```

期望31B、上述SHA、9指令/0引用；audit `passed=true`、`twoFreshSevenFilesByteIdentical=true`、`currentPEReads=0`、`logicalReadComplete=false`，七件hash同上表。失败保留诊断并停，不改selector、扩窗或换方式。第三验收后交Sol max按不同incoming状态消费raw；不得把此局部RET升级完整逻辑read。

本批未操作Git/index、构建、Java、DB、端口、进程、客户端、调试器或部署；未修改已接受projection及旧原件。Project/Harness/HANDOFF与本地提交由主线负责。
