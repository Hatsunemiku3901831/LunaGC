# D02-R1/R2：无断点初始化状态只读采样

本项活动计划：在主线负责的非调试器原始启动仍存活时，读取已经由固定原生指令证明的少量初始化指针。目标是取得enum选项、准确MethodInfo/RGCTX、defaultComparer和RuntimeType缓存的自然来源；不需要Avatar/model已创建，不主动调用客户端函数，也不把UI阶段空缓存当损坏。

证据为[enum来源与warm构造](CLIENT_72_AVATAR_LIVE_CONTRACT.md)及后继cold原生路径。未知是这些状态在真实启动时是否就绪，以及实际缓存对象/类型。候选是启动已初始化共享类型，或该闭合实例仍是metadata token/尚未用到。最小实验只对同一个已核PID、映像、创建时间和模块基址，读取下列固定链，每批记录UTC/原始bytes/hash/读取成功或错误；不追不可读或null后代，不扫全表。

- options cell：`B+5bc8ef0`→8字节选项记录，对照固定只读记录`B+22f9988`及`0101000007000000`。
- canonical enum class：`B+5bc9638`→class+10的16字节及class+c8字节。
- 精确构造上下文：`B+5a30968`→MethodInfo前16字节→class+30的rgctx→slot10/18；slot18的构造MethodInfo前16字节，对照共享callee `B+16fbc0b0`。
- comparer getter：slot10→MethodInfo/class/rgctx→slot10 comparerClass、slot18 typed descriptor；comparerClass+38→staticArea→defaultComparer。只记录对象头/class+10，不凭指针地址命名未知泛型。
- RuntimeType缓存：`B+5bc9068`→头0x68。仅descriptor实际kind17且ID20060时按已核hash读取首候选bucket/packed entry/value/RuntimeType；碰撞未匹配即保留unknown，不扫描表或制造miss。

失败出口是固定输入/映像/归属不符就拒绝；目标退出、读权限不足、metadata token或不可读指针保留错误，依赖链停止。单次读取不是原子全进程快照；非null缓存不等于自然Avatar构造或完整handler已运行。修改范围只新研究采样工具/测试/本页，原始内容留`local/migration72/d02-active/plain/`，不写目标进程、不attach、不设点、不改线程标志、配置或校验。验收需固定hash/自然原字节与来源相符、独立错误/空/碰撞边界，实际执行由主线独占。

## 已执行的扩展链与实际结果（2026-11-08）

主线已在第二次非调试器自然启动完成6批读取，当前用户已正常退出，OS无target。普通权限首次OpenProcess报5，失败保留；提升后使用同一PID/映像/创建FILETIME/模块核对，访问权限`0x101010`仅QUERY/SYNCHRONIZE/VM_READ，未附加调试器、执行target代码或写内存。每逻辑span≤256B，文件名逐字节直到NUL，合并span不等于一次系统调用或原子读取。

后继所需扩展均先有固定原生reader/metadata依据：

- 注册enum descriptor `B+2f1e560`补准确kind17/20060；首cache bucket只按occupied判断，不能加tombstone排除。旧错误先由独立原生反例再补红绿回归；不匹配仍不称miss。
- `B+5bc8f18`的class20060及class+10/+c8/+ca/+ac、`B+5bc8ad0`registry和RuntimeType对象class，确认实际underlying251/kind8、wrapper284及canonical351。getter/ctor RGCTX槽均0，defaultComparer未读，不能写成null。
- `B+5756990`router三个provider、18槽vtable和9代码窗、已核类型的前缀；`B+5756980`cache+558及`B+5766598`选择字节。前缀archive:/、选择1；默认/注册/special的vtable/代码与PE一致，不以模块存在证明自然资源调用。
- 新manager是静态object `B+55bc4b8`，+8指向状态`B+57aa100`，+10另一个状态`B+57aa170`。只读前4个有界list node→fileID→静态descriptor `B+5756ec0`的+48/+c0→deque block→filename；map count38与+58的internal-loadcounter425/437不同。获得四真实文件名，后由[blob合同](CLIENT_72_RESOURCE_BLOB.md)独立核全SHA/manifest/原生头。
- resource storage cell `B+586b600`，七槽Avatar+30870、depot+212b8、skill+16c70、Group主+13b70/byAvatar+13b78、Ornament+1cd98、Weapon+34900均0，表示当时未创建，不代表缺行/空业务许可。decoder byte[]位于storage+e970，只在实际length≥2048读对象+420起四段256B和`B+5872d30`的u32 seed，不读前1024B。确切getter/literal和受控原生对照见[自然资源合同](CLIENT_72_LIVE_RESOURCE_CONTRACT.md)。
- `--scope archive-state`仅`B+5eed350` cell及其256B自然context，与managed permutation不同；不主动执行解码。

| raw报告（`plain/`） | span数 | SHA-256 |
| --- | ---: | --- |
| `initialization-elevated1.json` | 14 | `80fa85e8329e23ee15021ee932867641a7c655182f0c97757667907f9284438c` |
| `initialization-elevated2.json` | 20 | `9e430bb2588dd97972f5683d9b4896764e79fce3bf91c3a3a3fa2593d1eeb526` |
| `initialization-router-elevated3.json` | 42 | `45635f11eac0bc958682948c75a35c0c8d49d518f4fdd1553bbe403be5ee9f8f` |
| `initialization-provider-elevated4.json` | 57 | `5a233bd0048f38f449e890268510e00d74870c770f58a416a82ceffe9faf0b9b` |
| `initialization-resources-elevated5.json` | 93 | `c5e6b05778f6326891065f120b966f4a8135b8601f097c30f6477724066a765d` |
| `initialization-archive-elevated6.json` | 2 | `63025b39285dca6890ae84e5a4cb6f304f44c7ddf46c19911b6a930351429c0d` |

主线逐span核长度/错误/hash及唯一执行源，v0..v5归档`plain/source-snapshots/`，对应SHA和报告的证明为`executed-source-proof-all.json`；不能用当前v5源冒充前5批。当前工具SHA`5adcd7d1981aa074d7266693bfca710a76dbf16d0bac5b3c32bdd84339e16f8b`。初始化/缓存后继原生合同由Astra双跑、主线独立第三回放与断言审核后分别提交。

主线7项采样器回归通过，含满64位/共享callee偏移、partial/error/null停止、occupied+tombstone原生反例、high-bit fileID/deque边界、filename首NUL停止与decode长度2047/2048边界。与监测器11项合跑18通过，日志`main/plain-sampler-final-tests.log`。只读研究工具/测试/文档变更，无服务器/Proto/config/patch/资源改动，无需服务重启；完整cold、真实资源row/revision、SDK与D-02仍未完成。不为已确认空的七槽重复新启动。
