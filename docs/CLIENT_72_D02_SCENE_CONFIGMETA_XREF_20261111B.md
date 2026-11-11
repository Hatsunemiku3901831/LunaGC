# F-03d3s2 / CONFIGMETA-LOADCONFIG-XREF：raw 候选门禁停止

冻结规格 `local/migration72/d02-resume-20261111b/scene/configmeta-xref-spec.zh.md` SHA-256 `3a1e31ddbf8f57cf2ee841ffd57ba2d69e790d1dba3c9d786a561ab45200f85f`。首轮扫描发现 **8 个 raw 候选，超过最多 4 个的规格门禁**，因此停在完整候选/pdata 清单。没有读取、反汇编或分析任何候选函数体，也没有选出 caller 或推断 filePath；8 个 raw 命中不是已验证的指令边界或有效调用。规格要求收缩范围前停止，故本次没有发起第二轮 fresh。该状态不是安全审查拒绝。

来源检查先核对 profile、完整五 stock 文件、受限元数据缓存、签名、两份资源索引和工具 pins，之后才解析。缓存使用拒绝 `find_class` 的 PureData Unpickler。固定 usage 758408 在原 global group 内；原始 source `0xc0016e00` 为 kind 6 / MethodSpec 93696，原 spec 为 `(80455,-1,66311)`，目标 cell 为 `0x145a5ee48`。方法声明、参数来源与已登记的唯一实例实参重新对应 `MoleMole.Config.ConfigMetaConfig`。这些是静态身份与限定形式的候选，不是自然调用事实。

PE 可执行 raw section 完整扫描：`.text` 35,394,048 字节、`il2cpp` 325,725,184 字节、`.upx0` 15,493,632 字节，合计 **376,612,864** 字节；唯一允许形状为前缀 48/4c、操作码 8b/8d/89、RIP ModRM 及精确位移。8 个候选均为 `4c 8b` load。异常目录完整扫描 **14,344,080** 字节、1,195,340 记录，SHA-256 `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d`；每个候选均有唯一 containing、其 begin 在异常目录也唯一。只保存对应 12 字节记录，未读取其 unwind RVA 指向的 xdata。完整 stock 文件 hash 自然覆盖全部文件字节；此处未分析其他 code body 或 xdata。

| raw site | 7 字节原件 | containing begin | end exclusive | 窗口字节 |
| --- | --- | --- | --- | ---: |
| `0x148c1ba16` | `4c8b052b34e4fc` | `0x148c1b910` | `0x148c1bac9` | 441 |
| `0x14b299cf2` | `4c8b054f517cfa` | `0x14b299c20` | `0x14b299e56` | 566 |
| `0x14d1dad92` | `4c8b05af4088f8` | `0x14d1dad60` | `0x14d1daded` | 141 |
| `0x14d1db67f` | `4c8b05c23788f8` | `0x14d1db620` | `0x14d1db785` | 357 |
| `0x14d1db7ec` | `4c8b05553688f8` | `0x14d1db790` | `0x14d1de1f3` | 10,851 |
| `0x14f486e16` | `4c8b052b805df6` | `0x14f486da0` | `0x14f487c32` | 3,730 |
| `0x1528b9352` | `4c8b05ef5a1af3` | `0x1528b92e0` | `0x1528b95b4` | 724 |
| `0x1531e44d9` | `4c8b0568a987f2` | `0x1531e4400` | `0x1531e4c49` | 2,121 |

第五项此外超过单函数 8192 字节限额；不能据此跳过该项并声称完成扫描。必须等待主线根据完整清单制定独立、准确且符合现有限制的下一规格；没有自动挑选候选。

新增离线工具 [scene_mplevel_actual_xref.py](../tools/migration72/scene_mplevel_actual_xref.py) SHA-256 `1e462537e4fc2b37a288c0d8cc15c13e61775e00a2d891e77bc4a88d90630355`；[门禁测试](../tools/migration72/test_scene_mplevel_actual_xref.py) SHA-256 `1e609d6cced6175ffa8d0585b8a8d58e333ace9ce326f728e4a8feddab68d133`。测试实际 **9 项通过**：固定源漂移、禁止 pickle 类、tuple/实参、六种形状、非指令边界假命中、pdata 不唯一及大小/数量门禁、多个有效 caller 不选、普通 AOT 槽漂移和完整解码上限。测试为合成输入。

```powershell
python -X utf8 -B -m unittest tools.migration72.test_scene_mplevel_actual_xref -v
python -X utf8 -B -m tools.migration72.scene_mplevel_actual_xref --output <新的私有运行名>
```

本次原件位于 `local/migration72/d02-resume-20261111b/scene-xref/fresh1/`：候选清单 `candidates.private.json` SHA-256 `71941994bf6924c59da6cc593f540537bbfc31a874aee8915ab8edac74ea95bc`，预算前快照 `pre-body.private.json` SHA-256 `ad14f660fd4a12ebe49f135c62dc64830a7ab032612136639c983ec2f9868c0c`，停止报告 `extraction.private.json` SHA-256 `730fbd30297e88086fc1c2d303e0550b50696d144fc36143974c41877516f75a`。本次未运行目标 CPU、客户端或其他进程，未启动 JVM、DB、端口或部署；未改运行代码、Proto、资源和配置，A/B 既有阻塞保持。
