# D02-R3 NativeWrapper 构造器：正常借用配对与既存缓存输入准备

任务 `R3-PROUD-NATIVEWRAPPER-CTOR-BORROW-CONTRACT`。新私有根 `local/migration72/d02-resume-20261111c/proud-nativewrapper-ctor-borrow-contract/`。只消费290B ctor、544B QueryWrapper helper、435B getter、已验689B标准缓存分支及准确固定声明；不创建row、不解释A返回、不构造Default。

## 来源和结论

新原窗 `proud-querywrapper-borrow-semantics/nativewrapper-ctor-window/fresh1,fresh2` 为 `[146e03c70,146e03d92)`、290B/78条，code SHA `b047833a3ea77b249e05e8b6c9b1b2e1fad7463a0696490101c30b25f27fd3a0`；unique pdata RVA1a6ce8d0，12B SHA `d926f12dcdecebb562054cd17e2be5074c9c82d8ecd8b95e4614a605d6ae05d4`。作者6tests、双fresh八件及集成24独立第三一致，ctor来源已由主线24限定验收并提交 `51dace1422d489d0eb5d865ce9f68d6983dec9a1`；仅源，不代本合同或真实对象。544B此前的“第三待”是历史状态；其来源现已由主线23验收、提交71db38e3，依据 `integration/acceptance23/independent-source1.private.json`。

**正常局部合同已闭合：ctor 输出 O=原 page、由最终 buffer 生成 B，对同 page+34h 增一次计数；544B原样复制，435B先读DWORD再减同一地址。** 这不独立证明真实page布局、底层buffer受计数保护或自然lease。没有为此继续追Restore/CheckReset/manager/refcount/vtable树。

## 物理值流与全部32B输出

ordinary83916/owner11712 的 logical instance `.ctor(INativePage page,int32 offset,int32 size)` 与物理调用分开：在 `146e032d9 e892090000`，RCX=&已清零local32B、RDX=重载page、R8D=i、R9D=GetSize EAX位样。544B中的 i=`(h>>8)&1fffffffh`，故在此非负；它与 getter 后加的字段偏移30h/38h不同。ctor 的 RAX 返回位样不被544B消费。

令 P=原RDX，F0=`QWORD[P+10h]`；F0非空时 L0=`U32[F0+18h]`，否则L0=0。page非空是后续正常路径前提。可选调用结束后重新取 F1=`QWORD[P+10h]`，正常返回还要求F1非空。

| out偏移 | ctor原证 | 正常复合结果 |
| --- | --- | --- |
| +10h DWORD | 3c78写原R8D | i的32位原样，无边界检查 |
| +14h DWORD | 3c7c写原R9D | 原size位样；不检查正数、零、最小长度或可读范围 |
| +8h QWORD | 3c80写原RDX | O=P，构造器本体不重写此槽 |
| +0h QWORD | 3d1d signext32(i)，3d20加`QWORD[F1+20h]`，3d24写out | B=`QWORD[F1+20h]+signext32(i)`，按64位回绕 |
| +18h BYTE | 3d27写0 | 标志字节0，不凭名字命名enum用途 |
| +19h..+1fh | ctor未写 | 仅因544B先清零32B而保持0；独立ctor调用不能假定padding0 |

前三个字段在page空检查前已写。输入size只被保存，**不是**下面Restore的length，也没有被拿来验证B范围。独立ctor允许任意int32位样：例如ffffffffh的index会按-1加到最终base；544B的29bit i限制了这一情况，但没有补出size/row边界证明。

## 增计数、可选调用与失败

`146e03ca6 LOCK INC DWORD[P+34h]`（F0非空）与`146e03d36`同一指令（F0空）是互斥路径。每个正常路径恰执行一个本地32位原子增量，随后才读flags/调用可选处理。

- 先读P+2eh；非零去Restore。该读为零时再读P+25h，非零也去Restore。各次flag读后有MFENCE，不能把它扩成对象整体快照或保护证明。
- `146e03cee → 1470f3a80`，固定声明 owner11723/m84102 `Restore(int32 offset,int32 length)`；参数为P、i、`(L0-i_bits) mod 2^32`。它不使用传入size作length。未读取或执行callee，不给其有利效果。
- P+29h非零时，`146e03d0f → 1470f3ec0`，固定声明m84106 `CheckReset()`。未读取或执行其body。随后才重载F1，因此B来自处理后的buffer，不是初次读到的F0。

P空到3d61的原1404c2be0错误边，尚未增计数；F0空本身不会立即报错，仍先增计数并继续。F1空到3d76的原错误边，此时本地增量已发生，本体没有对应DEC。class-init形态140525e00、Restore或CheckReset失败/异常时也不在已读本体闭合清理；这不等于断言全局异常机制没有清理。失败/部分写出不作为正常row或零字段。

所有正常输出公式要求相关地址/布局合法、guard和可选callee正常完成及ABI成立。源码中没有counter overflow/underflow检查。32位RMW本身按位回绕，局部+1/-1的贡献可相消，不能由此证明实际计数有效或最终计数等于初值（其它操作/线程未被排除）。

## 与435B getter的配对及真实缺口

544B将同一个32B local复制给getter，故O仍为P、B为上述最终值。435B在148ba9800复制`DWORD[B+signext32(fieldOffset)]`，然后O非空时要求`QWORD[O+10h]`仍非空，并在148ba9817 `LOCK DEC DWORD[O+34h]`。正常复合调用因此具有同一地址的一次本地increment、读、一次decrement；这闭合了此前单靠“wrapper仍存在”无法证明的局部配对。

固定`NativePageBase`声明有_buffer+10h、_accessCount+34h及相应flag，作为布局对照保留；**实际P/O的类型、vtable和后端保护协议仍不能凭名字绑定。** 不从计数非零推断本调用持有合法borrow，也不靠只读采样增加计数来制造borrow。正常复合条件仍包括：page/两个buffer访问有效，B的指定DWORD区间可读，cleanup时P+10h有效，底层协议确实在该期间保留B，以及同epoch的真实encoded/codec状态。

删除 `14d7ad9c4 e8f75cd8f2→1405336c0` 冷newrow、返回和归属依赖后，上述既存page参数化合同仍可独立成立。其余未知callee未获默认允许，准确A allocator/second-return/Default归属或B边仍停对应枝。不用cache补读或解释被拒返回。

## 既存标准cache单key输入：管理判断，未授权采样

标准分支静态上独立于冷producer：固定声明 owner80063.field400191、rawOffset4021f60 是 `Dictionary<U32,63831>`，位于`QWORD[14586b600]+21f60h`。只读已有条目可以不调用14d7ac240、FindEntry或任何构造器，也不创建row。若地址/identity唯一来自被拒第二返回、Default归属或B产物则停止，不能换成“cache”名义续接。

但当前**尚无可执行的fixed22101命中准备包**。已验689B在14d7ac39d准确 `e84ea3100a→1578b66f0`，返回signed index，再按entries+30h+24*index取P。旧`analysis/disasm-1578b66f0.txt`仅是到6798的无hex前缀；其中buckets/comparer提示不能证明完整bucket、key槽、active-chain或命中。不能扫描出一个相同U32就报hit，更不能使用free/stale entry。

若主线另备新只读包，以下仅是规模建议，不是完整API合同或执行授权：

| 层级 | 最少/暂拟RPM负载 | 前置和停止点 |
| --- | --- | --- |
| 已有raw支持的cache前检 | 最多5读29B：backend1、root8、cache8、entries8、array length4 | backend必须0；冷/空/失效即停止，不调用helper补齐；不能因此取得key/P |
| FindEntry源验证后的候选lookup | `7+L`读、`53+24L`B；暂拟L≤4，则11读149B | 必须先验证hash/bucket/key/active链；只准comparer0路线，非零不追虚调用；碰撞超过cap为incomplete而非miss |
| managed表示两字段 | lookup之外P+40h指针8B、41105+84h/+90h各4B；L4时合计14读165B | 需真实合法typed P和非空41105表示；读值不以fixture221/1替代 |
| native负handle候选 | 加P+40h/P+8h各8B、Dcell14586e970 8B及两DWORD8B；L4时16读181B | 435/544已给D±54bit偏移公式；仍需真实负handle、合法稳定映射及新批准范围；这里只得到encoded候选，实际codec同epoch条件另缺 |
| native正handle | 先止于P表示和packed h；L4时13读165B | 不能靠h直接得到page/B。需要合法现存page/vtable/held B/O或受支持的独立解析上下文；不执行TryGetPage/GetSize/ctor，不增计数，不造base |

暂拟lookup的53B为backend1+root8+cache8+header24+两array length8+bucket4；每个可达entry24B。header后两指针及其它解析均须新完整raw复核后才能落adapter。上述数字不含尚未制定的进程/模块identity、同epoch观察支持、可读性与复核API预算，不能作为现在可执行的总预算。

已有热Equals **7读50B本轮已cold结束**；这些数不继承其授权，也不复用其live输入或自动新ready。主线需先独立审核新源、完整只读实现/预算/测试及观察支持，必要时向用户另申请新范围和实时ready；本包不实现、不采样。实际cache中存在key22101也只证明该cache条目，不能代同Avatar入站supplied k、pair hit或当前采用，不能据此排121全部必要资源。

## 唯一普通技术后继：FindEntry直接源

`findentry-source-request.private.json`只请求1578b66f0，用于准备上述existing-cache单key只读输入，不做一般Dictionary框架。

可复用旧`c-depot-fields/main-third.private.json`的 **approvedCodeManifest** 提示：entry1578b66f0、end1578b6987、663B、SHA `eb3bd9ba203b582c2e9b151b1ea8b369e0da775a6aeeccdf81f5b6450bff8b3b`。只消费此entry/end/hash，不消费模型、人工A结果或samples；旧hint不是实际raw或pdata。

固定全量声明中native entry的ordinary精确匹配为0。open generic owner1222/m10194 `FindEntry` 的registeredAddress为0，不能把它冒充native target的ordinary index；泛型inflated/shared槽尚未恢复。拟议新门是已验10B caller `4889d989fae84ea3100a`、typed字段、同profile五stock、当前真实unique pdata和旧code hash，**需主线先审这个无AOT绑定的取源门**。不读取P+10194*8或猜generic槽。若主线要求AOT先闭，应保留body0技术诊断，不伪造来源。

先保存prebody/bodyRead0；当前unique exact/containing/overlap pdata须给完整executable/file-backed `[1578b66f0,1578b6987)`，record RVA当前未知。拟议code cap663B、instruction cap384（条数未知）、pdata12B，bounded evidence675B，AOT0/freshcaller0。header≤8192B、exception14344080B、五stock哈希550661784B单列，pdata摘自一次索引不重复算PE读取。范围/边界/预算不符就body0普通停止；完整raw后hash不符保留drift诊断，不改hint、不补邻窗。

只核comparer0的U32 hash/bucket/key/active链/错误条件，非零比较器和CALL/RIP只索引，不读其内容，不跟任何callee/generic树。准确拒绝依赖、新安全拒绝按原提示/停止点冻结，不换方法。request列出Terra可写工具/测试/单新专题/独立输出、双fresh、独立E8/pdata/raw重组及读取账验收；未在本包执行。

## 实际核查与交回

本包核八件双fresh相同、290B/78条连续重组、23控制/4RIP位移、12处字面锚点及两个已存E8；全部新产物只在授权新专题/私有根。检索时仅返回的A封存路径名已排除，不纳pin或依据，边界记录在新planning说明中，不沿其内容继续。

未读新PE/global/callee/实际对象，未执行CPU/model/live、collector/tests、构建/DB/Git。actual k/P/page/vtable/B/O/counter/lease/encoded/getter/row/pair/current均null；resourceReady、D02-R3和D-02不晋级。共享索引/提交由主线处理。

| 新私有产物 | 字节 | SHA-256 |
| --- | ---: | --- |
| `borrow-contract.private.json` | 20533 | `57495062d3624628786048d30540c73e7775a1e460c1d2e0152a88da49c89418` |
| `cache-input-planning.private.json` | 6238 | `904d72ab59182e48a57c24f6ac79bfe5534b5086a71b425d4f8afeacb5fe9c88` |
| `findentry-source-request.private.json` | 12006 | `8bc6f7abafa33dce6df4010c226e212559323c564c310eb036ed999e14abb903` |
| `final-artifact-check.private.json` | 535 | `fe8d4c535be0e7ce5a4a04f007c7186a4b724142de5426774393d59447f9fd39` |
