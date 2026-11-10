# E-01：两出生 Avatar 已批准字段的同源合并

两个出生 Avatar 的五份已批准片段现已合成后续构造可消费的部分输入。主线独立核对五份完整报告、32个片段值及 float32 位值，第三次输出与前两次逐字节一致；七项转换与拒绝测试通过。这不是完整 Avatar、资源就绪或新的 Java loader 验收。

## 固定输入与输出

| 报告（均在 `local/migration72/` 下） | 完整 SHA-256 |
| --- | --- |
| `d02-resume-20261109/r3-avatar/main-third.json` | `32cf0c85dd4df7976a78a33fca92464b6b6bdb01f1fd7e29dace9852de1c222e` |
| `d02-resume-20261109/r3-critical-installer/main-third.json` | `713bc5816eb2e9cca7dc23167e2b14ecf5e890d36286a135c66d1fa23cf0d8a1` |
| `d02-resume-20261108/r3-mapping/main-third.json` | `d990c2c469e5a9d3ee40351e4dfe5223ae49afaa855abe5d912427139c66307e` |
| `d02-resume-20261108/r3-equipment/main-third.json` | `50aaea9406152e1ec9e0c423ec532618b8fe635e3d0cf758d24fccc6403eb5bc` |
| `d02-resume-20261108/r3-promote/main-third.json` | `546b95ce69f5ace87761d2fe83c287da6a91ad9e9d19cbcb146147caed70fef1` |

只合并 `10000005/10000007` 的 `id/hpBase/attackBase/defenseBase/propGrowCurves/critical/criticalHurt/skillDepotId/initialWeapon/avatarPromoteId`。默认 depot 分别501/701、初始武器11101、突破group12；这些引用不覆盖旧号保存中的当前depot、其他武器或完整角色。

每份报告先核原始字节 SHA，再选择唯一片段、核 `fragmentOnly` 和已批字段集。同id必须对应同一canonical源行；critical沿用既验的特殊schema，按唯一 `javaFields.id` 和逐字段source/目标属性/float bits校验，没有伪造table/key。重叠字段必须类型、值和float32位相同。未知键、来源冲突、缺失或重复片段均拒绝。

独立目录为 `local/migration72/d02-resume-20261110/c-birth-avatar-join/`：

- `join_avatar.py`：`b32165d693cb6373112793fe4dc0fc2b8a9a7f174f9ef5f28532ff5c3126fc3f`；`test_join_avatar.py`：`7ae98a7eef3ae61e7d10b8e4ba7a11fbefcf68474a16b20812e25f88497ca349`。
- 三份输出各1211B，SHA `2ba724156e1716b81a129b81eb3a6333a0be069e8df28dba20257a7cce9cb3a6`；三份逐字段来源manifest各20940B，SHA `5fc97224c06167be3e3ec0bcdb0993d6a4a46508c0c8779de06aefd5db50ab2b`。
- Terra交付 `verification.private.json`：`96276a2be9a4aece94e9230c109b06680e84015c8cbcdfea58440d4b801eb769`；主线独立 `main-review.private.json`：`a4eb40dfbd9d5433690b743439e91584c18658874ce9cd4bac817f27cb7f70e0`。

转换器只允许独立目录中的新输出，不覆盖原件。复现时指定新的文件名：

```powershell
python -B local/migration72/d02-resume-20261110/c-birth-avatar-join/join_avatar.py --output local/migration72/d02-resume-20261110/c-birth-avatar-join/replay.json --manifest local/migration72/d02-resume-20261110/c-birth-avatar-join/replay-manifest.json
python -B -m unittest discover -s local/migration72/d02-resume-20261110/c-birth-avatar-join -p test_join_avatar.py -v
```

## 尚未解除的边界

manifest明确保留 `completeAvatar=false`、`partial=true`、`resourceReady=false`。Depot talents/proud输入、角色ability引用、完整构造及自然modifier、active revision/loadAll与保存、客户端D-02验收均未由合并通过。省略字段不是目标缺省许可。

没有新PE、CPU、Windows API、数据库、JVM或客户端运行，没有修改生产资源；既有Java局部验收只复用，不重计。本项无需重启或GS部署。完整Avatar的实际资源前置继续按[最新缺口](CLIENT_72_D02_AVATAR_ITEM_GAPS_20261110.md)及新的typed消费者证据收敛。
