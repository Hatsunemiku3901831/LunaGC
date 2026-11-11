# F-03d3s2 / Scene read 控制尾区原件（2026-11-11C）

任务 `F-03d3s2/SCENE-READ-CONTROL-TAIL-REGIONS`，父项 D02-R1/R3。主线已接受31B前置独立第三：`integration/acceptance14/return31-independent1.private.json`。本批按已固定的三个控制目标批量诊断，完成双 fresh 和纯原件审计；第三留主线唯一集成执行者。本页不推断读取/错误语义，不升级完整logical read或自然reader/Scene/Proud。

唯一request `local/migration72/d02-resume-20261111c/scene-read-return-semantics/control-tail-regions-request.private.json` 为24276B / SHA `0ed83005c2fee73f13e1e1bbf0b59f33df9dbe1d89b1ec92a25d4ace09fd5614`；语义来源检查11284B / `91c421ebfa773d9e63a63723da4b0b095beb80b2e09c0fed84ad87003c9ff745`。每次核全部固定源、工具、profile、metadata/signature、实际五stock pin；重建既存204B/415B/31B原件的连续指令，另从当前PE只核8个已固定Jcc各6B共48B及signed rel32目标。控制目标是原frame内的已证分支，不创建method/AOT身份；AOT读取0B。

固定exception目录14344080B / SHA `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d`，每次一次读取。所有目标先求unique containing并记录exact；真实row按原record RVA+12B去重。原三段分别是`[141138c70,141138d3c)`、`[141138d3c,141138edb)`、`[141138edb,141138efa)`，共650B；它们只是既存记录台账。真实结果：

| 目标 | 真实 containing 原记录 | 原记录 RVA / 目标偏移 | exact |
| --- | --- | --- | --- |
| `141138f06` | `[141138efa,141138f85)` 139B | `1a605f84` / 12 | 空，允许 |
| `141138efa` | 同一记录，去重 | `1a605f84` / 0 | 同containing |
| `141138f85` | `[141138f85,141138f8c)` 7B | `1a605f90` / 0 | 同containing |

两条真实新记录分别与全部三条旧记录交叠0B，相互交叠0B，完整总量和distinct-new均146B；两记录可执行/file-backed，全部目标、节及8192B总预算门禁通过后才读任何body。全部记录、目标覆盖/offset、各交叠、两两交叠、去重、总预算/节与原pdata先写入boundary/pre-body。若任一目标不唯一、旧或新记录交叠、超总额/节失败，整批0 body保存实际诊断，不挑子集、不猜邻域。instruction总预算2048；实际43条。exception目录另账；没有新callee/global内容、codec/copy/constructor、xdata、目标CPU或live读取。

## 原件和纯审计

私有根 `local/migration72/d02-resume-20261111c/scene-read-control-tail-regions/`。两 fresh 十件逐byte一致，纯audit SHA `b80eaf62580416c95f46b449c6be3d90a9ba85f7c61247296f4afaaa584e9e7b`，passed / currentPEReads=0 / logicalReadComplete=false。每个body从真实Begin完整解码，各目标均为原指令边界；所有原hex、入边条件及不同frame、RET、外条件/无条件边和末fallthrough保存在原件，不执行frame或跟进callee。

| 两份各自文件 | 字节 / SHA-256 |
| --- | --- |
| boundary.private.json / pre-body.private.json | 各6997 / `c2e56603b9a005a659fd76676a8bf0d3c7deb7eb235780ae740c586887c658a6` |
| branch-sources.private.json | 1909 / `8bef6d984f20094738d368bb8788eea3ed6aeaf2f5a5c597cf321a713414b54e` |
| extraction.private.json | 15352 / `318030656753c39b6d3bc1c51a4acfc209d7be47adf7cd09c7009ff7bb196060` |
| pdata-0x141138efa.bin | 12 / `b54367c7de3baf533256b20dcf59cf706ce17e8acaf8f0dc98b4bdb759b6faca` |
| region-0x141138efa.bin | 139 / `8b8b3d39ad3f9f00400a33edb611af2ff9bc405f8ef27697f9b1cc85c4605582` |
| region-0x141138efa.asm.private.json | 4686 / `3ebfd100359f3382fcce550d27bbc140cb84f6e3b51dfde4ba8a787ad59bc6b9` |
| pdata-0x141138f85.bin | 12 / `239d403b5993fb39e7f3d89c15f4d1f1d89a13bfe14241fa53df576a2e763ac2` |
| region-0x141138f85.bin | 7 / `a44e8acdbed9e8c0d389b74d3080a123d8925904fb28996f8fcee064b384f110` |
| region-0x141138f85.asm.private.json | 228 / `625066e970cffe25e40f24b20afac3fc0df61e18f94da057e35b9baa97bc0d4b` |

两区域各无RET/末fallthrough；第一条有原外部无条件边`f04→e8b`、`f80→ed3`，第二条有`f87→edb`；原CALL `f54→141fdf310` 只保留边，未读copy helper。各目标不同incoming及其他边在 extraction 的完整原表中。源region完整不自动等于逻辑读取/字节/EOF/error闭合；A/B已拒依赖继续冻结。

## 测试、命令与主线复核

工具 `tools/migration72/scene_read_control_tail_regions.py` SHA `d70537367ea78c50d2b37382367cfb1620271da8e8b1be51453ecac290fd39d8`；测试 `tools/migration72/test_scene_read_control_tail_regions.py` SHA `92c1a347b7ed4c2312497ab57137f2c05a48d1a377086746820daa3ac4a7b17b`。`python -B -m unittest -v tools.migration72.test_scene_read_control_tail_regions` 6组测试通过，私有 `tests.log`：覆盖固定源/stock漂移、8条真实Jcc边界与正负rel32/高64位、一/二/三row去重和exact可空、每个旧区及不同新区交叠、单区不超但总量超8192、节/短读、指令预算/目标边界/外边/末fallthrough、失败前整批零body并保存诊断、纯audit篡改拒绝及无PE。日志中的1B body为人工fixture。

本次实际四条命令的SHA-256按**UTF-8命令文本、不含换行**计算：

| 命令 | SHA-256 |
| --- | --- |
| `python -B -m tools.migration72.scene_read_control_tail_regions --output fresh1` | `cd942fc9b21ad9d182e26b6102e99e164c3826110e8f681da111e606910fd432` |
| `python -B -m tools.migration72.scene_read_control_tail_regions --output fresh2` | `d7066acb144114e62f596160806cb7b5e7097adb0a8889ec9b46e1f0486f98c7` |
| `python -B -m tools.migration72.scene_read_control_tail_regions --audit fresh1 fresh2 --output audit.private.json` | `28a84e55d4a8f198ac3984e4ed27d7af9409aa481d2ee9e46831527e486c69c0` |
| `python -B -m unittest -v tools.migration72.test_scene_read_control_tail_regions` | `3b1303ce8135e3ec9ef2d7a2507ce6ddfd93c57e34fb33fa68cafd4f45b545d9` |

唯一主线集成执行者第三次（先确认输出名不存在）：

```powershell
python -B -m tools.migration72.scene_read_control_tail_regions --output main-third
python -B -m tools.migration72.scene_read_control_tail_regions --audit fresh1 main-third --output main-third-audit.private.json
```

期望2 region、146B、43条指令，两个body SHA同表；audit `passed=true`、`twoFreshAllFilesByteIdentical=true`、`currentPEReads=0`、`logicalReadComplete=false`，十件hash同表。任一失败保存实际证据并停止，不扩大范围或换方式。第三验收后交Sol max依据各真实incoming分别分析，不从本机械包假定读成功。

本批未操作Git/index、构建、Java、DB、端口、进程、客户端、调试器或部署；Project/Harness/HANDOFF与本地提交由主线处理。
