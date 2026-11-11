# F-03d3s2 / Scene native read 单窗（2026-11-11C）

父项 D02-R1，同时供 Proud row 研究。主线已接收486B reader helper 的独立第三验收（`integration/acceptance7/scene-refill-independent1.private.json`）。本批完成 `F-03d3s2/SCENE-NATIVE-READ-ONE-WINDOW` 双 fresh 和纯原件审计；第三留主线唯一集成执行者。此页只记原件和边界，不推断 API 成功、可读字节或自然 reader/Scene/Proud ready。

固定 request `local/migration72/d02-resume-20261111c/scene-reader-refill-semantics/native-read-one-window-request.private.json` 为15024B / SHA `213de70fa850bc4762869a177a62ae6e0ac79f5ccf91793b9923d8eed8c21f3f`。每次核所有固定源、工具、profile、五 stock、metadata/signatures pin，禁止 globals 解包后目标 ordinary registeredAddress 零命中；AOT不适用，读取0字节。固定486B caller、122条原hex、完整SHA和调用者报告复核；只新核13B `48894424204531c0e862e8b0f1`，E8 signed rel32 指向 `141123190`。固定 exception directory 14344080B / SHA `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d`；unique exact/containing 同一原记录，完整范围 `[141123190,141123260)` 208B；先存 boundary/pre-body 与12B pdata，核正值、≤8192B、可执行/file-backed 后才读完整body。不读邻窗、callee/global/xdata/构造器、目标CPU或live。

私有输出根 `local/migration72/d02-resume-20261111c/scene-native-read-window/`。双 fresh 七件逐byte一致，body SHA `348c0faf52b09dd5046a3f80ffaacbe346ef59f03faab4446f80d277b9bf1467`，65条完整连续指令、15引用；pure audit SHA `543656d20f5b1e1df992012f8ce63f15d727aaf8059c0f295fb343c28ad0f5aa`，passed / currentPEReads=0。完整asm原hex和CALL/branch/RIP/RET/tail表均保存在原件；CALL目标 `141139030`、`1414de9f0`、`141138c70`、`1414d87c0` 与 RIP `143b227e8` 仅保存原边，不读或执行。不能由目标列表判断间接后继独立；A/B拒绝继续冻结。

| 双份各自文件 | SHA-256 |
| --- | --- |
| boundary.private.json / pre-body.private.json | `626686263f671933c76dfd1e42c5c1b929b8d159233c9fac0cbf9ec0f6830f51` |
| caller-setup.bin | `1a913253802593882fabbe66e851afc60ac5a6d3703024a8025d39b128fbf59b` |
| pdata.bin | `2443b73ba129f64e7596084f8faec1376cc8ca1b2167d0c214a89d865cbb6508` |
| body.bin | `348c0faf52b09dd5046a3f80ffaacbe346ef59f03faab4446f80d277b9bf1467` |
| body.asm.private.json | `244b97bced9e1ac0eed6d6682b802231f5563c51e2f5542cd933890e3a2d314a` |
| extraction.private.json | `db022aef74946e21a73ef1e6788ab31aca9a167634e4fe542d7ee4d40bb4c1fb` |

工具 `tools/migration72/scene_reader_native_read_window.py` SHA `8863c09ae1fc14fddbff734b6b1549e8e85829c586fb51d9aa6774f902e96d88`；测试 `tools/migration72/test_scene_reader_native_read_window.py` SHA `49845c05e9fedc7a68138eef5b5a2af1ce0de6f4cf8184885e27d1733ec2bead`，执行 unittest 6项通过，记录 `tests.log`。覆盖来源/profile/stock 漂移、无 globals、ordinary方法命中拒绝、caller/context/E8、正负rel32与高64位、缺失/重复/冲突pdata、范围/节/短读/不完整解码/指令预算、pre-body 保留和纯audit单份及同样篡改拒绝。日志里的2B是人工fixture。

主线第三（输出名先确认不存在）：

```powershell
python -B -m tools.migration72.scene_reader_native_read_window --output main-third
python -B -m tools.migration72.scene_reader_native_read_window --audit fresh1 main-third --output main-third-audit.private.json
```

期望208B、上述body SHA、65指令/15引用，纯audit `passed`、`twoFreshSevenFilesByteIdentical`、`currentPEReads=0`，七件hash同上表。失败保留诊断并停止，不扩大范围。之后由Sol max消费raw并区分观察、推导与未知。仅离线工具/测试/专题及忽略产物；未操作Git/index、构建、Java、DB、端口、客户端、调试器或部署，共享状态和本地提交归主线。
