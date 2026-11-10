# E-01d1 旧号 Talent 固定资源来源对照

对照[已验 726 个查键](CLIENT_72_D02_OLD_TALENT_REFS_20261111.md)、固定 AnimeGameData 候选与当前 resources gitlink。两表均有全部 726 行。6 个差异为 621–626，候选缺字段，Java 对应 null，当前表给显式空字符串。其余 720 个非空值在 checkout 的 `BinOutput/Talent/AvatarTalents` 各有一个精确键，未发现多来源。候选 commit、根树、Excel 树和 blob 均核对。当前 129 个 AvatarTalents 文件经规范化换行与固定 blob 匹配。

Java Avatar 的相关应用路径跳过空字符串或 null。场景通知代码只过滤 null，会查询空键。上述命中只约束当前 checkout 中的源条目，目标行、自然加载、其他 OpenConfig 目录及缓存的行为仍需核对。定位目标来源时先检查这 6 个差异和相关实际 sender。

主线 7 项定向测试和独立 CLI 通过。最终双报告与主线报告均 823,590 B，SHA-256 `19be8f2bde7e36eddb7e2d844c946e8b7d78c877c35f43cda4593353ab8f2cb0`。证据留私有 `c-talent-sources/`。

```powershell
python -X utf8 -B -m unittest tools.test_client72_d02_old_talent_sources -v
python -X utf8 -B -m tools.client72_d02_old_talent_sources --output <新文件名>.json
```
