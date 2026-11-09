# A：F-03d3f4d2 / R1-R3 String 最小后继

本卡已完成保存原字节的有界分析和6项Python检查，交付准确自然输入合同；没有新增native CPU、现场读取、PE提取、构建或部署。主线已恢复D-02，历史暂停不再限制本卡；本卡不改旧报告/旧pin或共享String合同。完整Avatar、Item、FindEntry、配置/ability、g2和D-02均未由本卡通过。

## 选择与直接必要的consumer

优先收敛已有必要性的Gadget String查询，而不实现通用TLS/GC。初始武器11101→Gadget50011101和实际Java EntityWeapon/Ability局部生命周期沿用[必要性成果](CLIENT_72_D02_GADGET_NECESSITY.md)，本卡没有重跑其Java验收，也没有把248个未映射叶全部列为P0。

当前直接必需的目标字段仍是type40472的String字段199649、`+58`，经原`14aa9c630 → 14aa9b8a0`进入string→u64 cache。已验旧独立slice使用`Weapon_Sword_Metal`，字段`+50`的`Lv1_Weapon`具有不同hash和用途，不能替换`+58`。以下实际消费者来源均为已有保存原窗，不是新地址猜测：

| 原消费者 | 准确根/输入与用途 | 尚缺 |
| --- | --- | --- |
| `14aa9b8a9..8c4` | IFix guard `B+57d6f89/1B`；normal分支读取class17586 usage `B+589b558/8B`并查klass+c7 | 本轮fresh未采这些状态；guard非零或c7为零保留不同路径，不改flag/调用initializer |
| `14aa9b8ca..8d1` | 读取`B+586b600/8B`得到实际global storage pointer，再取该pointer`+213e8/8B`得到key→asset字典 | 字段83175、instance3744、`Dictionary<String,UInt64>/1222`；真实字典当前未知，不使用旧GLOBAL模拟地址 |
| `14aa9b8e1..902` | `B+59ae108`的TryGetValue MI10200 → owner+30 RGCTX → +78 slot13的FindEntry MI，以`RCX=真实字典,RDX=原key,R8=真实MI`直接调用`158b48be0` | 当前联合fresh图slot13实际为0；不补槽、不以TryGetValue MI或getter MI替代10194 |
| `14aa9b907..923` | FindEntry非负index→真实entries数组→`entries+30+24*index`读完整u64 asset key | 这是直接可解除R3必要asset-key缺口的consumer；真实key/字典命中尚缺，不能预填成功项 |
| `14aa9b93c..959` | 同global storage pointer的`+213f0`是asset→owner字典，继而17591 owner+30才是string→25270配置字典 | 该后继与实际reader/asset、对象内容仍独立未闭，本卡没有取得17591/25270对象或许可生产alias |

`B`须来自新采集的模块身份。前两行可形成**仅根诊断**：guard1B、class usage8B、class+c7的1B、global pointer8B、字典cell8B，最多首遍5读26B；成功unique全部末尾复读时最多10读52B、单读8B。这只是新proposal，未执行/批准，也不表示自然caller或完整字典已取得。null/short/error/分支不符停止；不能在旧collector末尾追加并沿用其原final或cap。

## Hash的已验范围与新准确约束

保存String2922/slot2原窗`159a8da20..159a8dab0`为144B/SHA `3f0da425909eb1ae65e34d69ec879c612bfd6355a306b7b1394c066579566e97`，extent来源是`metadata-next-registered-method`，**不是已核PE exception extent**。本卡仅审字节，没有据此新执行整个函数。原FindEntry1093B及consumer379B分别为`cfe5db886ab9aeed283b61c4b91736a3177960faef816c492b60d47c157adfc9`、`29134aabb510d6a0bd74978c8516c6a3624212709ddcc5c659984aa61bad50ce`。

原hash先读`query+14/uint16`，交替更新两个5381 seed，最终按`first + second*5d588b65 (mod 2^32)`组合；**不读取String+10长度，遇首个NUL即结束，包括嵌入NUL**。旧受控fixture中五次真实hash已经执行，三种内容的实际u32分别为Metal `3705035964`、MPLevel `1870589383`、Lv1_Weapon `867824404`。本卡只用新的纯host推导核对旧实际数值，不重复这些native调用，不把旧受控String class/字典模型当自然对象。

纯host边界检查证明`A\0B`、`A\0C`的预测hash均与`A`相同；这不是新的原生测试。与已验Equals按明确长度比较的规则分开，hash碰撞不意味着字符串相等。自然String的终止字节仍缺；不能由owned Helper2908的显式NUL推定自然分配器存储。

## FindEntry需要哪一份自然输入

原`158b48be0`读取dictionary+28 buckets、+30 entries、+10 comparer。**只有实际comparer为null的分支**先从query实际String klass的+e0函数及伴随MI调用String hash，再取真实bucket，调用GetDefault并沿24B entry检查hash/Equals/next。非null comparer走不同的interface lookup/间接调用，不允许把它改为null或替成当前Default。

当前同epoch共同图只含EqualityComparer<String>/1234和实际Default普通1243；**1243不是System.String293**。自然query klass、其+e0/伴随MI2922、实际bucket/entry/key和NUL均不在该图内。已有GetDefault25条与owned Equals字符路径继续有效，但没有可直接执行完整FindEntry的自然输入。

若主线确实取得自然调用，最小事件位置是`158b48be0`入口，真实caller return必须是本轮ASLR的`14aa9b907`；保留实际RCX dictionary、RDX String、R8 MI10194、RSP。第一次候选事件须先占唯一尝试，错caller结束，不在过滤前无限读取/重试。当前**没有已证自然触发阶段**，不建议重复同一登录页等待来碰运气；也不能用任意手造对象推进。

新工具报告列完整初始typed/null-comparer诊断图：caller8、dictionary klass8、MI56、owner208、GenericClass32、classInst16、两argument pointer16、两type descriptor各16、实际slot13的8、comparer/buckets/entries各8，共**首遍13读408B**；全部成功unique统一末尾精确复读上界**26读816B、单读208B**。必须核owner1222、String14/UInt64-11且byref0、methodInst0/backref、字典klass/MI owner一致及真实slot13==R8；异常保留partial。这是尚未批准的新proposal，不挤入旧76/3264或任何旧cap，也不含后续字符串/bucket/chain读取。

后续必须另审同epochString293/hash选择、真实query长度/内容/NUL、getter/Default/Equals和实际bucket/有界chain。最终要证明Gadget自然caller，仍需query与真实Gadget50011101/+58的来源关联；只手选已捕获key不能晋级该结论。没有必要额外提取已存在Hash/FindEntry窗口。

## TLS比较与独立未闭项

fresh完整Avatar的105条实际完成与`1405337b9`失败保持；旧cold普通10294是另一输入/路径，不混用。保存420B allocator静态字节另外证明后继是`GS:[58] → TLSVector[index] → moduleTLS+19d0`；状态null时原代码会把`B+5699688`写入该TLS字段，两状态分支最终都在`1405337d8`调用`140563cd0`。本卡没有该callee原窗，也没有真实线程/GS/TEB数据。故只采TLS index4B不能解决分配器，更不能造TLS/对象、借页零或host返回成功。未提取该callee或其后`14055ecf0/140525e00`。

## 实际验证、文件与交付

独占工具[client72_d02_a_string_frontier.py](../tools/client72_d02_a_string_frontier.py)和[测试](../tools/test_client72_d02_a_string_frontier.py)只读10个完整SHA固定输入，不导入旧运行模型或emulator、不读当前stock/GAME_PATH。完整三窗连续原bytes/hash、28个关键opcode、3个RIP根、fresh epoch/slot13零、旧5个实际hash、TLS420B/6个site及全部边界通过。6项测试涵盖字节漂移/断裂拒绝、独立旧hash数值、嵌入NUL、准确根/epoch、图预算/未授权边界与fresh/cold区分。

忽略目录为`local/migration72/d02-resume-20261110/a/`。最终`final-first.private.json`与`final-repeat.private.json`均8600B、逐byte相同，SHA `30da1ab2cf59a916b1ff1e363c83bf46dd7dc64709be595bcac0d1a6f8b86717`。工具SHA `6cbcffbbfda66417cde2eb555d6b68dc9d457309bbd20f2da9000919788d1b9c`，测试SHA `98347a50d7377a257d0481fc92794a16595383f2dcfad2e1250e10d02fba3b86`；执行argv/UTC/exit/stdout/stderr和最终源码副本在同目录。最初9pin开发审计原报告保留，补TLS源核对后才形成这两份10pin正式报告；实际总共3个分析进程、2个unittest进程，最后6项全通过，没有原生CPU/read预算增长。

```powershell
python tools/client72_d02_a_string_frontier.py --output local/migration72/d02-resume-20261110/a/main-third.private.json
python -m unittest tools.test_client72_d02_a_string_frontier -v
```

复用外部检索的固定commit/branch计划和partial事实见本卡私有`plan.zh.md`；本小后继没有新重大runtime实现、网络检索或clone。新增文件仅本tool/test/专题及A独立忽略输出，旧工具/String专题、AGENTS/resources与共享索引未写。没有本地Git操作；主线负责审核/提交/Project/Harness/交接，无运行材料变化，无需GS重启。

本卡分析规格已明确，返回释放Sol max。下一最小可执行项由主线选择：在真实资源producer/自然调用阶段已明确时按上述根/事件合同取新输入；否则先推进其他已有输入的完整Avatar/Item实际必读字段，不将本卡准确缺输入改为Hash/FindEntry或D-02完成。
