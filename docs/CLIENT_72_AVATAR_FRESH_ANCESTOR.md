# F-03d3f4d2-fresh-ancestor：新父类输入与cold后继

本卡只读固定客户端文件、第二次fresh snapshot和既有冻结证据，运行离线CPU模拟器。旧工具、断言、raw、采样器和R3交付冻结；不改生产Java/Proto/patch/resources/配置/共享索引，不操作Git、构建、数据库、端口、客户端、调试器或安装。新raw仅 `local/migration72/d02-resume-20261108/r1-fresh-ancestor/`。

## 有界计划

| 项目 | 本卡范围 |
| --- | --- |
| 目标 | 先独立解释Object371的kind28及原native consumer是否需要其新metadata，再把已采1234/371输入按明确跨epoch合同接入cold后继，连续前进到完整成果或准确下一runtime首缺；不将构造片段作为Handler完成。 |
| 固定输入 | 五客户端hash沿当前主线inventory；新monitor SHA `5ca5b6e5af9bba74889495426e983d5b8d55048f82091fb10dcd3ed08d732fb2`；新snapshot SHA `119f8a0b3c3ecc8829eda9bf7422c3646c643afa34ac041ea69b5607a02a7384`。上卡源/第三原raw及旧99span全部核pin。 |
| 已有事实 | 1234完整头/id1234/kind18、70B metadata及parent descriptor已得；371完整头id371/kind28，因旧合同拒绝未取metadata；两declaring offset0、371parent0。NativeConfig typed index12184/2089、mode1不作为构造消费时序证明。 |
| 首先审查 | 上卡把Object371也要求kind18的假设必须重新核原始事实。只允许解释这个准确type/consumer，不能把18扩成普遍“18或28”。 |
| 最小实验 | 核固定Kind域与原native kind28分支/静态class来源；有界非零16B descriptor控制。验证fresh字段身份/来源和70B metadata后作显式重定位，复用旧Array/Type、vector/intern与原断言扩展层；每次新caller/宽度/源指令或真实缺字节完整记录。 |
| 模型约束 | 不猜metadata、未知runtime字段或页内零；不将新指针生硬放入旧epoch；新字段与静态可重建数据分别列明来源，保留首次失败及所有旧断言。 |
| 允许文件 | 新 `tools/client72_avatar_fresh_ancestor.py`、对应test、本页；所有probe/原始输出仅专属raw目录。无委派、Astra或新的REA整PE分析。 |
| 验收 | 两个全新进程确定性、独立非零/64位/拒绝/尾哨兵及真实首缺检查；明确default/构造/Handler各自状态，交主线第三命令与准确下一输入合同。需要live字节则只交reader/链/最小budget，不自行读取。 |

## 当前状态

已解释Object371的准确kind28，接入fresh1234后连续执行到新的runtime首缺：`15a4d6bf1`读取`cache[1169]`的8B，定义1169为`System.Collections.IEqualityComparer`。原指令已完成两个GenericClass（1239、1234）的400B类壳构造及`+18`cached-class发布，随后进入类初始化和实际接口producer。完整类初始化、cold comparer、default写回、Avatar Handler、协议ready及D-02全部仍未完成。

主线恢复stock后，两个全新Python进程均重新通过原五输入gate，各22项检查通过；两份1703202B报告逐字节相同，SHA `dc738c4f3f3d80cdb72bb852d2250de685657fa489818f436ce8e8cc9933256b`。主线临时proxy实验期间本卡仅做源码/夹具/文档，没有放宽gate或启动新回放。没有新的现场操作。

## Object371的准确表示与旧合同更正

上卡把两个普通类都要求kind18，是错误的具体合同假设。冻结旧99-span已明确记录Object371 kind28；第二次fresh头再次为id371/kind28。新卡不改旧合同、旧源或采样器，也不将kind18扩成通用的“18或28”。

固定PE reference1的16B是`730100000000000000001c0000000000`，解析为`data=371,kind=28`；固定metadata唯一名称是`System.Object`。1234的固定parent reference恰为1，fresh1234父descriptor与这16B完全相同。

| 原窗口 | 准确行为 | SHA-256 |
| --- | --- | --- |
| `1404ede18..1404ede48`，48B | inflation表kind28项指向`1404edd33`，由`1404edd40`原样返回descriptor | `8adc773c8dc549d86d7af68d1e41d08ae486a87a5f70adb1606478e1d92b892e` |
| `140517900..140517978`，120B | class-dispatch表kind28项指向`140517843`；普通kind18指向`1405176aa`定义索引lookup | `ceb7bb6b6a0ad51e73270046bc46f585007c7ffef0ead72ac240a9cf13e82739` |
| `140517843..14051784f`，12B | 只读canonical cell `145bc95e0`的8B并返回，未读371头或metadata | `baa2d561413af54257e840a514325a5d7fd4431721dd92fc75768d30d05fb758` |
| `1405427b2..1405427d6`，36B | 固定PE的`System`/`Object`字符串调用`14051f560`，`1405427cf`写同一canonical cell | `047ce1848ac2ffa204b83eb5055f3c344954a60e55507caf360e814902741615` |

原native独立控制分别使用data371和非零64位`fedcba9876543210`的kind28 descriptor；inflation均原样返回，class转换均返回控制canonical值`8000000100000001`，descriptor尾哨兵不变。kind18/data371单独确实进入`1405269f0`，与kind28分支区分。入口仍只接受这次准确id371/kind28；data372/kind28、Object改成18、1234/1239改成28均拒绝。

因此**当前父构造支不需要新371 metadata**。canonical cell的自然8B没有采到；本卡按准确named producer把它显式绑定到冻结、已核id371的Object类。这是模型身份绑定，不能称作fresh canonical cell观测。

## 跨epoch重定位合同

输入为第二次fresh snapshot（36读2232B、24窗口、零读错），其`status=partial`及准确`class-identity-mismatch`原因保持不变。源码SHA为`ecab38c9e39daf5ea48bb017cc7060c61c8b142c4f3844d37b54c3e7374f9c7c`；监视记录及snapshot的完整SHA在计划表中。工具同时核五客户端文件、冻结handler/parent源、旧99-span及上卡first报告，不依赖文件名或旧绝对地址猜版本。

1. 用本次fresh module的`B+5bc8f18`读cache根，逐项核1239/1234/371槽地址、非空指针、完整208B头与准确descriptor身份。registry也只用fresh `B+5bc8ad0 -> slot -> base`核链。
2. 以registry-relative compact位置把头和descriptor接入冻结旧模型。两次固定arena数值相同是实际事实；报告仍明确`sameProcessReplay=false`，不能因指针数字相等声称自然同进程回放。
3. 1234的70B fresh metadata逐字节等于固定file row1234，SHA `00b9a09dbe29589ee7a40b147d587b1cdbbd1f4c1e8eee101a9d886a500837f7`。头只改`+58`为旧模型已由13个metadata地址共同验证的body绑定；新1239/371头经同一`+58`投影后，其余208B全部与旧头相同。
4. fresh1239父descriptor严格为kind21/static405416，固定startup pair为instance54605/definition1234。fresh1234父descriptor严格为上述Object371 kind28。后者与已知Object头`+10`重叠，重叠任意一字节冲突即拒绝。
5. `+a8`两类为零，Object371的`+b0`为零，记录分支而不跟随。`+0/+48/+70/+88`等runtime指针仅保留头中的opaque字节，不推导其指向存储。后续若缺准确跨度，原read guard仍停止。

原Array/Type、注册GenericClass producer、intern、vector、SRW及CriticalSection片段全部复用。冻结`run_cold`及其旧断言未修改；新增层只捕获旧“父descriptor必缺”断言已过时的frame，并对新终点执行准确断言。原四个producer检查和ready-factory检查照常执行；原数组尾哨兵不变。旧“只允许一个未完成动态类”等终态条件已被新的两次真实发布/准确首缺条件明确替代，没有把旧断言删去求绿。

## 新增模型及静态解除

| 边界 | 原caller/宽度及限制 | 证据与控制 |
| --- | --- | --- |
| 受控空GenericClass缓存tombstone计数 | 只显式初始化`145bc7220+40`的8B为0；受控空缓存是冻结输入模型，不是自然缓存观测 | 原`1404f129d`将`+40`加到`+38`occupancy后与`+50`比较；`1404f16f6`有成对16B更新。初次新guard先拒绝该未声明值，补为明确空缓存模型，未把页零当自然输入。 |
| 原inflation临时vector释放 | `141cbfe48`，return `1404edbbf`；仅已拥有的8B、准确rdx8和尾哨兵 | `1404edb82..1404edbc8`原cleanup计算capacity-begin；错caller、错size、非owner、尾哨兵损坏和二次释放均拒绝。原8/16B growth/copy/free规则不改。 |
| Nullable canonical身份 | 原`140517c1c`只比较8B canonical指针。固定named producer `140542fe9..14054300a`的两字符串为``System/Nullable`1``，唯一定义368 | 单独保留未绑定时的真实8B首缺；继续支仅给与1234/1239不同的opaque身份，不给Nullable头/成员/自然地址。原native以低32位相同、高32位不同的两指针验证jne；相等控制走另一分支。 |
| generic接口storage | pool `1405091d0` return `14051f3ce`，准确rcx registry owner；只接受已拥有400B类壳的`(definition1239,width0)`或`(1234,width16)` | 原`14051f3ab`读固定metadata+43 XOR25，`*8`得到长度。0B不触碰字节、不前移cursor；16B先dirty，按准确原memset清零，另留8B尾哨兵。 |
| 接口memset | `141fdf9c0` return `14051f3de/14051f3f8`；edx0、精确owner及0/16B | 完整原pool allocator `1405091d0..1405092f5`，293B，SHA `ac16fc03b8d01353be4d6c2764dee25a7c5511902458adbc2fcce829d27e06c8`；独立native0/8/16B fast-path控制验证返回旧cursor、前移准确字节数、原payload不变。 |

没有新增OS模型。仍仅为同一受控线程、既有两CriticalSection及既有SRW序列；不作调度、竞争、死锁或全局锁序证明。

新global guard在旧cache1234首缺被解除处启用，仅保护选定`145bc7000..145bca000` runtime-global范围；只有显式模型或原native已写过的字节允许读取。它不是全客户端所有global的清单或就绪证明。未绑定Nullable支实际停`140517c1c`，不能使用PE初始零绕过比较。

固定metadata接口行也可静态准确解除：1239 count0；1234 count2、first535，file`2974198`的8B为`c1c7060097d70600`，SHA `978411c87efa257ff172f38cf8133be49fc7f9e9bf8ddbc985893c54a51d6dc4`。PE header+c8 XOR`1d590619`、row+36 add`6f6c`及+43 XOR`25`均沿既有parser与原`14051f462`。只映射这8B，其他mapped-page字节不能读取。

## 连续结果及真实首缺

固定startup/intern以及实际ready factory之后，原`140517980`递归构造1239/1234两个400B类壳；`140517c6f`执行两次，两个动态GenericClass的`+18`分别实际写入其新类指针，argument都为原intern instance5917。此处证明两次原构造发布已发生；类初始化仍未完成。

实际后继为`1404e0c70 -> 14051c9d0 -> 14051f370`。1239的空接口storage分支完成，1234分配并清零16B，实际读取第一条固定interface ref444353。它是普通kind18/data1169，通过原`140517680 -> 1405269f0`到cache lookup。

- 首缺：`15a4d6bf1`，rcx=`491`，读取`cache[1169]`的8B；旧模型地址`50000548c40`仅用于复现，不能用于新live。
- failure为`Uncaptured natural-address memory read`，error为null，非空字符串误判或模型assert逃出。没有写入第一个interface元素；16B仍为原memset的零，尾哨兵保留。
- 当次只剩一层class CriticalSection，SRW未持有；没有声称已unwind/释放。
- 新1169槽究竟为零或非零未知，未沿旧地址读进程、未补页零、未猜接口类头。

完整cold comparer、defaultComparer静态写回、完整Handler、自然NativeConfig消费时序及D-02均为false。本卡也不把用户登录页或typed NativeConfig头当完整原生选择/构造验收。

## 下一次最小合同

只给主线规格，不改当前已审sampler，不执行live。当前真实缺1169；若第一lookup完成，原固定循环下一条ref448407准确指向static405409/instance54600、``System.Collections.Generic.IEqualityComparer`1``定义1249，单参数VAR362。不是猜测额外type。

| 定义 | 准确普通descriptor证据 | 最小读取 | 可选metadata验证 |
| --- | --- | --- | --- |
| 1169 `System.Collections.IEqualityComparer` | ref444353，`143529ee0`，data1169/kind18，SHA `45fd4049af31fd4f63b4090cd8d9c1470779a801f9a1a31241d04828a70cd7d3` | fresh cache+`2488`读8B，非空读208B头 | header+58读70B，比fixed file`a21026`，SHA `a120af6afc25d7d683dbdcc306919a244fe9877f05698d0839836122f7d2a4ac` |
| 1249 ``System.Collections.Generic.IEqualityComparer`1`` | ref448461，`143539fa0`，data1249/kind18，SHA `2e4d2f910ff63f7c07ae3786aa7f6f8fdcc081a06db68dd464012e5d8edf1bed` | fresh cache+`2708`读8B，非空读208B头 | header+58读70B，比fixed file`a22606`，SHA `ba00239c8db9e1caa6ae10e9a6a7921f7ec31242f8aeb07a1c89ca286e4846e4` |

两固定metadata parent=null、interfaceCount0。当前已证cache/head consumer最小为新增4读432B；额外两metadata行是140B的严格输入验证，与最低runtime读取分开列明。若头+b0/+a8非零，复用同次fresh registry链仅取准确`base+compact+10`的16B descriptor；零值记录跳过，不递归跟猜测type。连metadata与四个条件descriptor的保守新增上限为10读636B，单读最大208B；假设主线联合合同已经取同次cache/registry根，根未取时须另计。未知tag/identity/null/error保留原状态。

VAR362的14B固定metadata已经足够，不需要再live：file`4fb2580`，SHA `6d1df52b90dc9d2aa7c4298132a074cc0c20e357f93a0aa63a6596eb3b938596`。当前未走到它，不能宣称其native后继已完成。未达到的method/member/vtable/hierarchy跨度继续未知，不为它们扩采样。新合同不包含Pool或NativeConfig，也不要求Nullable头/成员。

## 尝试及复现

raw集中于本卡专属目录，`probe1..6.json`依次保留：未声明空cache计数器、旧vector free caller拒绝、Nullable自然cell首缺、旧allocator拒绝interface零长度、真实1169首缺、带原native64位/零长度控制的同一首缺。`probe1..6.source.py`均与各报告记录的source SHA相符；1..4源字节由保存的edit epoch逆向恢复并与事先报告hash逐一匹配，恢复脚本也保留，不冒充当时即时source快照。5为原字节归档，6起工具自动保存source sidecar。所有原raw未覆盖。

最终命令（从checkout根，`GAME_PATH`按本机已核配置提供）：

```powershell
python -m tools.client72_avatar_fresh_ancestor --client $env:GAME_PATH --output local/migration72/d02-resume-20261108/r1-fresh-ancestor/first.json
$env:CLIENT72_AVATAR_FRESH_ANCESTOR_REPORT = 'local/migration72/d02-resume-20261108/r1-fresh-ancestor/first.json'
python -m unittest tools.test_client72_avatar_fresh_ancestor -v
python -m tools.client72_avatar_fresh_ancestor --client $env:GAME_PATH --output local/migration72/d02-resume-20261108/r1-fresh-ancestor/repeat.json
$env:CLIENT72_AVATAR_FRESH_ANCESTOR_REPORT = 'local/migration72/d02-resume-20261108/r1-fresh-ancestor/repeat.json'
python -m unittest tools.test_client72_avatar_fresh_ancestor -v
```

输出已存在即拒绝覆盖。独立第三将output换成新的`main-third.json`，并令report环境变量指向该文件，再运行同一全套检查。只改离线工具/测试和本页，无生产运行变更，无构建或部署，也无本卡Git操作；提交由主线独占。

最终源SHA：工具39402B为`3715861c8c6570a37d69a5de7967633a563671eba2b185e3e77759d4142c548c`，测试18487B为`544faff402e339f4d740f6d3a6ac371ac66866ad0900db801eebf6fcf30497fb`。两个完整运行的实际argv/exit code与22项结果分别在`first-command.private.json`、`repeat-command.private.json`、对应`*-tests-command.private.json`和`*-tests.log`；CLI source sidecar与当前源逐字节一致。`determinism.private.json`记录直接字节比较与所有六个probe源hash校验。独立合同`next-sampling-contract.private.json`为6465B，SHA `bac006269d333ebe5ca88d25f6ac84c3b25f1bbadea72e95d571655366ffea33`。

主线第三完整回放 `main-third.json` 退出0，同双跑逐字节一致，均1,703,202B/SHA `dc738c4f3f3d80cdb72bb852d2250de685657fa489818f436ce8e8cc9933256b`；第三22项检查通过，证明 `main-third-review.private.json`与`tests-main-third.log`。主线审核了新global范围守卫、原cachedclass写入、Nullable opaque模型与未绑定对照、接口存储caller/owner/宽度及首缺后，接受本卡限定后继；未来真实接口样本继续另取，未关闭default/Handler/g2或D-02。
