# R1-METHOD-CACHE-REPLAY：同轮方法缓存采样图的原生回放

## 实施前计划

本卡只消费主线新 ready/stock90秒观察后的准确只读原件，不启动/附加/采样客户端，不改旧前缀、collector 或 live 原件。原件 `local/migration72/d02-resume-20261109/r1-method-cache/live/cache-live1.private.json` 为400,995B/SHA `ab042d29b67aff6735b498680e74b6152e6c72285cdeb3cc799ceef57a730998`，source SHA `402336ad19e97245089ee61e1a6869db908960f1780986ce8ff5dd8c4fa8a73c`；monitor SHA `860c571ec1277538e269d7fc1663906bfd7b9d2853876cf3e3e3602949d31914`。主线已核15项、用户正常页/正常退出和stock/保护文件恢复，本卡再独立核source、identity投影、roots、逐读跨度与重复一致性。

先单独执行原 GenericInst `14051fba3` 到 `14051fd12`，再执行八个 MethodInfo query `1404eea9c` 到 hit `1404efb1c` 或 miss `14052d590`，各2M指令。只有六根cell的存储地址从fresh B+RVA别名到固定PE，所有captured canonical/packed/key/MethodInfo的64位值与pointee地址原样保留；逐字节合并相邻/重叠有来源span，矛盾重叠/未提供字节/外写立即停。对比原始读地址/顺序、查询路径与返回，不建writer/resize/allocator成功桩，不把受控回放升级为自然caller或完整cold。

只有上述输入与回放足够时，才在本独占新工具另加连续case：旧六前缀独立保留，原factory必须通过本fresh intern cache取得本fresh canonical，不能以替换返回值或旧hash强迫命中。先记录准确接入点/预算，再执行到新的真实首缺；56B MethodInfo之外没有字节就停止。完整Avatar/default/Handler/g2继续未验。

只新增本页、`tools/replay72_avatar_method_cache.py`及对应test，私有产物仅 `local/migration72/d02-resume-20261109/r1-method-cache/replay/`。不改Project/Harness/README/HANDOFF、旧工具/旧测试、Java/Proto/resources/patch/config；不Git提交、构建、DB、进程/调试器/REA共享会话操作。原collector/source冻结不变。交付独立正反case、双新进程报告、主线第三命令及准确最小后继输入。

## 当前状态

限定回放已完成双新进程、主线第三及各20项检查，三份报告逐字节相同。原件 source/identity、141读3048B、首遍88读及53个精确复读跨度已独立核对；由raw构建的稀疏内存重新执行冻结collector，全字段投影相同。原GenericInst和八方法的9次isolated回放准确到约定停止点；新增连续case保留旧六前缀，由原factory自行取得本fresh canonical，八次方法查找返回和64B方法表写回均与raw一致，止于16B hierarchy storage请求前。无captured外写、无新增成功模型；完整cold/default/Handler/g2未验。

## 连续case新增前的小计划

输入现已足够尝试原第七次factory后继，仍不提供任何miss writer输入。新工具独占扩展层将在**第二次进入原factory `159b6eb50`（第七原生调用）之前**，把六根alias cell按raw内容写入受控内存；此写入是显式输入绑定，不是native writer或自然观测。前六次已验producer/registered-intern前缀仍原样执行并与冻结报告逐项比较path SHA/寄存器/failure/error，不能为了新canonical去改它们。

所有fresh非root字节通过原snapshot准确known-span白名单加入，完整64位pointer值不变；不得补相邻页。第七次仍由原 `14051fb70`内容查询fresh intern图，自行得到fresh canonical，并继续原方法构造。只许可已审 `1404f0640`直接窗；新窗口、未知读、任何写入captured字节/alias cell、未声明模型回调或2M触限立即停。旧锁/typed storage模型仅复用，新增成功模型为0。每次原调用2M，连续case共7调用；加上9 isolated，总16调用。新case不声称同一自然图、自然caller、default或完整cold；最终以实际停止点为准。

`probe2` 已完成上述八次真实 lookup 连续后继，准确停在新的直接 `14051f280..14051f36f`，239B/SHA `2660d47b018f78042ed87c42acc90df893fdb2064c988ad32d39b31dcd98fed3`。这是旧 cold-interfaces 的 NATIVE 清单中已有 hierarchy producer，但本连续分支尚未许可，原失败保留。现只补此准确 PE/exception extent 的核对，执行其已采父层级/owned class读取到 `14051f2ff` 返回地址对应的原16B pool请求；在allocator入口前停止，不新增allocator/清零/复制成功模型，不扩后续窗口或storage。

验收另加四个独立原生负例：缺key的一个字节、把canonical误截为32位、超出末MethodInfo56B的尾读、向captured输入尝试写入。它们只执行上述已审窗口，各2M，明确不接受为自然输入；连同9 isolated及7连续调用，总20调用，不新增窗口或成功模型。

## 本次输入审计和真实候选范围

只读核对固定raw、实际执行source sidecar、monitor和当前五stock哈希。monitor与raw的PID/creation、最后模块path/base/size、五输入/profile/config投影一致；raw末尾身份已核。原件共有141读3048B，其中首遍88读、53个unique跨度复读，全部无错/短读且重复一致。新工具用这141条有源byte构建稀疏存储，重新调用原冻结 `collect(read,B)`，所有pure输出字段（含paths、roots、records、budget、false边界）与原件相同；没有丢弃第一次读或只挑末遍。

同轮 canonical 候选是count1/kind17/data20060/byref0，methodInst=null。实际指针在本次样本**恰为本次 B+2377940**；这个等式来自本次有源entry及header，不能从旧受控注册指针普遍推断。GenericInst在首个probe命中，rank6。八个method的候选如下；表中的hit仍是重复采样候选图的分类，不能称自然caller曾调用/命中。

| method index | probe数 | 原候选路径 | 56B返回记录 |
| --- | --- | --- | --- |
| 10287 | 2 | 先错index，再内容相同context | `+18`与index一致 |
| 10288 | 2 | 先错index，再内容相同context | 同上 |
| 10289 | 1 | 首个候选相同 | 同上；两个函数入口字段为0，不猜函数或调用0 |
| 10290 | 1 | 首个候选相同 | 同上；两个函数入口字段为0 |
| 10291 | 1 | 首个候选相同 | `+18`与index一致 |
| 10292 | 2 | 先错index，再内容相同context | 同上 |
| 10293 | 2 | 先错index，再内容相同context | 同上 |
| 10294 | 2 | 先错index，再内容相同context | 同上 |

MethodInfo的class/descriptor/函数等指针只是已采字节；尤其 `+0` owner类的pointee没有提供，不能根据它补class头、改为owned类或推断整个自然对象图。原raw有 `candidateGraphOnly=true`、`naturalLookupObserved=false`、`atomicSnapshot=false`、`naturalWriterExcluded=false`；复读/末身份一致不能排除writer/ABA，也不证明消费时序。本卡保留这些边界，不重新采样，不把2.5ms整体采集耗时当逐read原子性。

主线另完成用户正常SDK页/正常退出、五stock/13保护文件保持与进程不存在的核对，证据在 `../live/main-review.private.json`、`../live/normal-exit.private.json`。这是本轮采集的生命周期收尾，不是本卡代理启动/操作过客户端，更不是本服登录或D-02。

## 原生isolated与连续case结果

原141个跨度仅按byte一致性合并成32个有源连续区间，缺口保持unknown。六个root-cell只新增首选PE地址alias，内容值、所有heap/canonical/type/key/entry/MethodInfo地址都保持本轮raw的完整64位；没有递归补pointee、整页0或重算成旧bucket。owned查询存储与raw来源分别标注。任何成功native read必须可落到原首遍88条中一个准确包含它的跨度，并逐byte相符；root alias记录其原始B+RVA来源。

原GenericInst fragment从 `14051fba3` 实际执行162步，到 `14051fd12` 解锁前停止；`r12&rbx` occupied匹配，`rdi`返回与采样canonical相同。八个MethodInfo查询从 `1404eea9c` 执行，steps为246/246/204/204/204/246/246/246，全部到 `1404efb1c` 解锁前，返回指针一致。每条block occupied、packed entry、key index及最后value读取的地址和先后顺序，分别与该key采样path逐项相同；这九次共有237条external read，包含owned查询读取，非owned每条都有准确raw read序号。

native按字段读取block的8B字段，collector曾合为24B请求；native也会再次读canonical/type。因此不声称237条native read与141条RPM一一同宽同序。验收的是：完整有序native读取记录全部有精确byte来源，原probe/entry/key/value序列及返回吻合，原collector又可完整再现141条账本。两种读取粒度保留，不把host采集算法当作原native执行轨迹。

连续case复用原三类壳、两接口和不可变method-index投影。在第二次factory入口才显式绑定fresh根；前六个冻结producer/registered-intern case的path SHA、寄存器、failure/error逐项相同。第七次原 `14051fb70` 按type内容查fresh图返回本次canonical，原八个 `1404eea50` 调用参数均使用该值，八个实际返回与raw相同。原 `14051e2c0` 把它们连续写入owned64B方法数组，8个QWORD逐一相等，原清零/写回及尾哨兵 `fedcba9876543210`保持；三个GenericClass/owned class壳400/400/208B与接口数组继续保留。

连续第七次实际6,620步，额外227条captured byte/alias read都有原首遍来源；新未知read=0、captured write attempt=0、miss writer调用=0、callback failure=0、未审窗口=0。hierarchy函数先由原指令读取已采Object父层级状态并把owned1234深度写为2，随后到原pool入口 `1405091d0`，其返回地址 `14051f2ff`、width16、owner为当前已验registry pool。**在运行allocator模型前停止**；该16B storage未分配，不能称hierarchy发布。此时critical depth1仍持有，shared未持有；没有执行异常展开/解锁或默认comparer写回。

此连续case仍是旧有ancestor/显式空GenericClass cache/owned类壳模型与本fresh method-cache跨度的明确受控组合。它不对应同一自然进程的完整图；尤其自然MethodInfo owner没有改成新owned类。方法lookup和指针写回成立，不证明完整class/vtable/实例/default一致性。

## 当前最小后继：准确16B owned hierarchy storage

独立机器可读合同为 `first.next-input.private.json`，3,133B/SHA `7aeba6e51bdebd0295c763aab0e98062f28c2acbf68068613539dde10de3a98a`，与正式报告 `nextInputContract` 投影相同。这里是原producer要求新的**受控typed storage**，不是还缺自然cache/父类头；解除本停止点需要新增自然字节为0，也不需要新live。当前卡未实现以下成功边界。

| 精确入口/return caller | 最小行为及验收 |
| --- | --- |
| `1405091d0` / `14051f2ff` | 仅当前registry pool、第二个owned400B class的definition1234、depth2、`+48`仍null、width16，分配dirty16B和独立非零8B尾哨兵；不授予其它caller/owner/size |
| `141fdf9c0` / `14051f30f` | 原第一次清零，仅上述owned16B；检查值0、宽16、尾哨兵 |
| `141fdf9c0` / `14051f321` | 原条件内第二次同范围清零，保留原重复调用和尾哨兵 |
| `141fdf310` / `14051f35c` | 只复制已pin原99-span的Object hierarchy8B到owned数组前8B；不授予任意memcpy |
| `14051f333`、`14051f361` | 保留原指令写最终槽的当前owned类、xchg发布到class+48；前8B应是准确Object父指针、后8B是当前类 |

下一卡若获准，按这些边界实现后只执行到下一真实未知；不预批后面可能出现的24B数组，不复制普通1234的自然hierarchy冒充当前泛型类数组，不扩大读MethodInfo owner或其它pointee。本合同不是完整Avatar/default/Handler/g2实现许可。

## 独立负例、正式双跑和主线第三

四个原生负例各保留真实失败：缺key一字节在 `1404eec48` 的4B读停止；把canonical误截32位后，在 `1404eebbc` 的另一个未采block首读停止并保留原Uc unmapped error；读末MethodInfo56B之外在 `1404f0760` 拒绝页内未知字节；在 `140527095` 尝试向captured输入写8B时guard拒绝。负例只改自有投影或寄存器，没有改raw/正例/目标进程；不把失败隐藏成零值或native miss。

正式 `first.json`、`repeat.json` 是两个新Python进程，各 **1,329,181B/SHA `02f1182f1a1d7a042e08bcef400a832d5ead0b946f97527214b6de25eda38bbc`**，逐字节相同，exit0/stderr0；各20项独立unittest全过。验证覆盖raw/source/identity/budget/repeat漂移、source byte冲突/短读/页洞、六根alias及完整64位、原九查询read序列与返回、全部read原始序号、旧六前缀、原8个pointer写回/尾哨兵、16B首缺及四负例、每调用2M、全false边界和下一storage合同。19项preview通过后仅增加第20项合同检查，正式两次均20项；无未记录测试失败。

`probe1`为只做isolated的原始开发报告；`probe2`准确保留未审hierarchy入口；`probe3..5`为逐步加入精确storage停止、read来源和负例的开发产物，连同各实际source sidecar保留，未覆盖。第一次私有monitor形状查看把`modules`的dict当list，抛AttributeError后改为其`items`读取；未修改raw或协议逻辑。该只读表示错误不影响正式source/输入门禁。

冻结工具27,550B/SHA `1ac41682fd129a9951fa1d9560b27b44afe8780193ff609664aeadc3f0fa1945`，测试14,382B/SHA `952838479b9b5aed4b7d9cf1a2df59fda2ce887723218e38c69086aea9023151`。实际argv/exit/stdout/stderr及test源SHA在 `*-command.private.json`/`*-tests-command.private.json`；`determinism.private.json`记录两新进程直接byte比较。原始source、地址、参数字节和证明均在本卡专属忽略目录；五stock和旧原件未写。

主线实际第三 `main-third.json` 与正式双跑逐字节相同（1,329,181B、同SHA），20项检查通过。主线另逐项核17来源pin、source sidecar、9个原查询停止点、8个完整64位返回、原连续64B写回、227条有源读取、旧六前缀、16B准确首缺、四个真实负例及父项false边界，共12项，见 `main-review.private.json`。本卡限定验收完成；后继 `R1-HIERARCHY-STORAGE` 只执行上表的16B自有存储边界，不需要再次启动客户端。

主线第三复现命令如下；已有文件冻结，重跑须另取新文件名：

```powershell
python tools/replay72_avatar_method_cache.py --client $env:GAME_PATH --output local/migration72/d02-resume-20261109/r1-method-cache/replay/main-third.json
$env:CLIENT72_AVATAR_METHOD_CACHE_REPLAY_REPORT = 'local/migration72/d02-resume-20261109/r1-method-cache/replay/main-third.json'
python -m unittest tools.test_replay72_avatar_method_cache -v
Remove-Item Env:\CLIENT72_AVATAR_METHOD_CACHE_REPLAY_REPORT
```

本卡只改三个新独占文件，不改旧METHOD-CACHE工具/测试/专题或旧前缀，不操作Git/构建/DB/进程/调试器/REA。原METHOD-CACHE专题与实际live结果由主线维护；主线负责第三复验、集成、共享索引及分项提交。下一可执行项是准确16B hierarchy storage边界，完整Avatar/default/Handler/g2与D-02未验。
