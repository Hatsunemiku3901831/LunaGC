# E-01d1 / R3-OLD-TALENT-CATALOG-LOAD

已完成当前 checkout 的完整 OpenConfig loader 限定验收。以固定旧号的720个非空 Talent 查键为范围，核四目录的142份直属 JSON，并实际执行无参数的生产 `ResourceLoader.loadOpenConfig()`：720键全部安装，与单 AvatarTalents 来源的完整条目逐项相同，缺失与变更均0。不是目标7.2配置身份、有效 effect 语义、`loadAll`、完整 Player 恢复或客户端入场验收。

## 输入与实际路径

复用 [固定来源报告](CLIENT_72_D02_OLD_TALENT_SOURCES_20261111.md)（SHA `19be8f2bde7e36eddb7e2d844c946e8b7d78c877c35f43cda4593353ab8f2cb0`）的720个非空键；[六差异测试](CLIENT_72_D02_OLD_TALENT_ENTRY_NULLS_20261111B.md)已独立区分 null、空串、缺行与缺配置。

当前源码依次读取数据路径 `OpenConfig.json`，再读 EquipTalents、AvatarTalents、RelicTalents、TeamTalents。当前配置数据目录和 classpath 默认数据目录均没有该缓存；本轮隔离数据目录也保持相同的缺缓存输入。四目录实际直属 JSON 数量为10、129、2、1；720个必要键在全部142文件中均只有一份来源，无缺键、同键覆盖或 JSON 读取错误。此结论只针对固定输入，不推断将来的缓存和资源不会覆盖。

新增 `tools/client72_d02_old_talent_catalog_sources.py` 固定每个文件的相对路径、字节数、完整SHA、读取次序及必要键来源。最终规范化报告使用正斜杠路径，两次独立 CLI 均为 SHA `3bbd7497d6b916e41862214cbbe47d3ebb17df1c659e80484a3de64df7897cf7`。早期私有报告的来源列表使用 Windows 分隔符，仅用于 catalog2 探索；最终测试固定新的规范化报告，不混用哈希。

## 实际 Java 验证

`D02OldTalentCatalogLoadTest` 在独立 CWD/config/data/tmp/log 下，显式指向当前 checkout 的只读 resources。先核报告和142文件的完整大小/SHA及文件集合；确认生产入口实际使用该资源根和不存在的缓存后，调用原无参数 loader。对720个必要键检查全部存在，并将完整 `OpenConfigEntry` 与单目录真实 loader 结果逐条比较；确认 null/空键未安装、`loadedAll` 未变，结束恢复原 map。

最终 catalog3 以便携 JDK17、离线 Gradle 单 worker 实际编译运行，15秒 exit0，XML 1项通过、失败/错误/跳过均0。stdout 为 `OLD_TALENT_FULL_CATALOG checked=720 unresolved=0 changed=0 cacheAbsent=true`。class-init 未初始化 DatabaseManager、GameServer、HTTP/DispatchServer 或 PluginManager；没有启动 Mongo、服务、客户端或写入原数据。仅离线工具/测试/文档，无需部署。

首次 catalog1 因主线命令转义错误未传入必要 JVM 属性，在测试入口缺参数处失败；原日志/XML保留。catalog2 的旧报告测试通过；规范化报告和最终源码由 catalog3 重新编译验收，不将早期输出改写为成功。

## 复现与剩余缺口

```powershell
python -B tools/client72_d02_old_talent_catalog_sources.py --output <新文件名>.json
./gradlew.bat --no-daemon --offline --max-workers=1 --console=plain -PskipHandbook=1 --init-script '<隔离 init>' test --tests emu.grasscutter.data.D02OldTalentCatalogLoadTest
```

forked Test JVM 属性：`d02.talent.repo` 为当前 checkout，`d02.talent.overlay` 为最终规范化报告绝对路径。配置采用 version14、独立六根与日志，`LUNAGC_RUNTIME=portable`、资源环境覆盖指向已固定 checkout resources；不启动数据库。私有原件为 `local/migration72/d02-resume-20261111b/talent/catalog3/`，保留 init、Gradle日志、XML与class-init。

已关闭的是“当前四目录/缺缓存输入是否实际安装并保留720个必要 Talent 条目”。目标 Talent 行及 OpenConfig 的同源身份、目标 parser/字段/effect语义与revision仍缺输入；Proud门槛、装备配置和其他真实必经资源也不能由本项代验。必要输入齐备后，主线才在独立数据库推进同一旧号真实 Player、背包、装备及 postLoad。
