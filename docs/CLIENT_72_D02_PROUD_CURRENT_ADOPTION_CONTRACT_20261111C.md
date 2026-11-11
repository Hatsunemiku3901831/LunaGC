# D02-R3 Proud 当前采用：两原窗合同与既存 handle 借用首缺

任务：`R3-PROUD-CURRENT-ADOPTION-CONTRACT-SEMANTICS`，父项 `D02-R3`。本包只消费固定原件，未读新 PE/body/global，未执行客户端或原 helper。新产物位于 `local/migration72/d02-resume-20261111c/proud-current-adoption-contract/`。Project、Harness、HANDOFF、旧专题和原库存均未修改，由主线集成。

## 来源与结论边界

正式来源为 `proud-current-adoption-source-plan/source-windows/fresh3,fresh4`；fresh1/2 不补记验收。主线 `integration/acceptance20/proud-independent1.private.json` 已接受第三副本、18 件相同、4 tests、285 条指令、85 个控制引用、26 个 RIP 引用及 AOT/pdata/预算前门。该验收只成立来源，不成立业务采用或 lease。

| 原窗 | 大小 / 条数 | code SHA-256 |
| --- | --- | --- |
| `[14d7ac240,14d7ac4f1)`，ordinary 663528 | 689B / 143 | `fffb1b4e89e14d5aecda054670e3228eff8b146f8c2bcda2f27361795a38cb5a` |
| `[151846d90,151846fbc)`，ordinary 138266 | 556B / 142 | `41fef8abc06c6c742b11b82720baa879e8429174058b0fa5b3a799666d023ee5` |

来源专题为 `CLIENT_72_D02_PROUD_CURRENT_ADOPTION_SOURCE_WINDOWS_20261111C.md`；完整输入版本、原字节复核和推导见 `semantics-review.private.json`。这里明确修正“没有 caller”的范围：那只是此前限定输入的结论；既存 AvatarInfo→同 Avatar 重建→选择后 append 的非预览静态链现已成立。它不等于已观察网络实参、缓存 hit 或自然当前采用。

## 原 k、AL、out P 与缓存

`14d7ac240` 保存原 `ECX=k`、`RDX=&out`、`R8D`。注册/初始化正常返回后先清 `*out=0`；后端由 `U8[14587c2c0]` 选择，不由调用 bool 选择。没有本地 `k==0` 排除。所有以下正常返回结论仍要求有关 class-init/callee 正常返回。

- 标准后端使用 root+21f60 的 typed `Dictionary<U32,63831>`。首次缓存空路径返回 AL0/out0；另有初始化重载后的原错误路径，不能把所有空缓存折成 miss。`1578b66f0(cache,k)` 给负 index 时 out0/AL0；非负 index 须 entries 非空且 unsigned index 在界内，随后 `P=QWORD[array+30h+24*index]`、AL1。P 可以为零；原 caller 另做空值检查。entries/null/越界走原错误，不造正常缺键值。
- 自定义后端使用 root+21f68，空缓存为原错误。仅低 8 位 bool 经 `MOVZX EBP,BL` 传给 `154db6ef0`。该 helper 返回 AL 非零时外层返回 AL1，out 保留 helper 输出，外层不证明非空。miss 时再清 out，然后调用既存 `14d7ad8e0(k,&out)`；其 AL0 不触发 cache store。外层本身不能保证 producer 返回后 out 仍为零。
- AL 是调用者使用的 bool。整 EAX 不是规范 0/1：标准后端 `SETNS BL` 保留缓存指针低 32 位的高 24 位；自定义 hit 保留原 R8D 的高 24 位。不得用整 EAX 是否为零替代 AL。也不得将 AL1 和 P 非零合成一个事实。

复用的 606B producer 在 `14d7ad9c4` 具有准确 `e8f75cd8f2 → 1405336c0` 边。依主线冻结该处的新 row、allocator 返回、构造和寿命依赖；不解读其返回，不请求其后 callee，不借 warm cache 推导该对象。外层冷 miss 后续 cache-store 只保留控制边。删除这条被拒依赖后，既存缓存的已给 out P 消费仍可独立分析。

## 同 Avatar 的选择与 append

既存 caller 的原 k 来自 raw 48178 的 tag14/+68 或 tag35/+80 repeated U32 输入，两者分别进入同 Avatar 的列表重建。这里只证明原字段/列表关系，未批准 inherent/special 等语义别名及真实 sender 映射。

首次查找 AL0 跳过；AL1/out P 空走原空值错误。group0 跳过。同一 P 的 managed +40 表示为 41105 时，+90/203376 给 group、+84/203373 给基础值；native 表示使用 `QWORD[P+8h] & ~ffh` 的 handle，经已有 435B reader 读取 native 30h/38h 后走既有 scalar 解码合同。真实 P、表示模式、encoded 和读回值都未取得。

`151846d90(this Avatar, group, original R8D)` 的 ordinary 分支为：

1. Avatar+1f8 的 encoded32758 group 查找给基础值；负 index 走原装箱/ThrowKeyNotFound，无正常零回退。
2. Avatar+200 必须存在。第一次负 index 显式调用编码器生成 encoded32758(0)，不是把编码内存清零后假定它解出零。第一次命中后又查同一个字典；第二次仍须有效、非负且在界内，不能把第二次 unknown 当 miss。真实两次结果未观察。
3. `151846f2c ADD EBX,EDI` 计算 `sum=(base+extra) mod 2^32`，无 phase 比较或 clamp。`ffffffffh+1→0` 只说明机器运算边界，不是业务准入。IFix 另路 tail 未解，其状态须作为 ordinary 合同前置。
4. 同 group、sum、原 R8D 传给已存 351B `14d7ae900`；caller 只写 R8B=1，不能宣称已清高 24 位。下游使用低 8 位。pair 为 `(uint64(sum)<<32)|group`；接口 lookup 返回 AL0 则 D 空，AL 非零时 out U32 k2 送 `14d7adf70(k2,bool)`。k、k2 不可混同，没有 `group*100+1`、本地 k2 零排除或 Java `<=` 证明。

同 Avatar 的 +1c8、+40 两表在已有空表/无重复/容量路径存入同一个非空 selected D 引用；不能扩成所有重复情形均覆盖旧项。+50 是另一列表，不混入此链。generic 插入/去重 helper 的内部行为、真实 pair hit、自然 Equals 输入和真实 D 都未建立。

## 435B getter 的局部顺序及唯一必要后继

已存 `c-proud-getter-window/first,second` 的完整 435B raw SHA 为 `7f9905cee0b667965999c65de05ab2f7f0b7b104ffdd2c097aafcc2e13553442`。对已给非零、非负 existing handle h：

`[148ba97e6,148ba97f3)` 的 13B 为 `488d4c24204889fae8fd9925fe`，即 `RCX=&local[rsp+20h]`、`RDX=h`，`148ba97ee` 的 E8 准确调用 `146e031f0`。随后 B=out 第一个 QWORD、O=第二个 QWORD；`148ba9800` 先把 `DWORD[B+signext32(offset)]` 复制到 EAX。O0 直接退出；O 非零须 `QWORD[O+10h]` 非空，随后 `148ba9817 LOCK DEC DWORD[O+34h]`，正常退出不改已复制 EAX。

因此本地读先于本地 release 形态减计数；这没有证明 B/O 合法、base 在读前得到保活、计数配对或整体线程寿命，也没有证明返回结构总长度。managed wrapper 存在不能替代 native base 寿命。零/负 handle 路径分开。

现成限定来源中未找到 `146e031f0` 完整 raw/pdata；旧 row-bridge request 的 `existingBridge/commonQueryWrapper` 仅是入口/调用声明。固定 metadata/signature 精确关联 ordinary owner11707 `NativeDataUtils`、method83889 `QueryWrapperInternal`、flags96h、一个 logical uint64 handle；声明返回类型未恢复，不能用物理 out-buffer 伪造 managed 返回类型。

唯一后继为 `querywrapper-source-request.private.json`。它独立服务既存缓存 P 的 handle 消费，即使删去 A 新建 row 分支仍有必要。由主线另审/派 Terra，本包未取源：

- 先核所有 pins、五 stock、AMD64/PE32+/base、ordinary Pcell `1422f9950` 与当前 `P+83889*8` 槽准确指向 `146e031f0`；历史槽址仅提示。
- 先保留 prebody/bodyRead0，再诊断当前真实 unique exact/containing/overlap pdata。仅完整 executable/file-backed body 可进门；实际 code extent 未知，预算为最多 512B/256 条。AOT16B+pdata12B+code512B 合计最多540B；复用既存 caller，fresh caller0B。PE header≤8192B、exception14344080B、五 stock 完整哈希550661784B 分别记账，不能隐藏为540B总读取。
- 边界缺失/歧义/超预算保留普通技术诊断并停，不自动 prefix/leaf/邻函数 fallback。除列明 AOT 指针/槽外，不读取 global/callback cell 内容、B/O、callee 或实际 handle；不跟 generic/refcount/manager 树。
- 只问 B/O 写出、保护条件、是否 hold/retain 及与 getter 本地 decrement 的关系。若准确依赖 A allocator/second-return/Default 归属或 B loader，立即冻结对应枝并保留边，不推导其返回；新安全拒绝保留原提示/停止点，不换方法。

Terra 可写文件、两个 fresh 输出、原字节重组/当前 pdata/已存 E8 独立 oracle、前门失败和真实累计读取测试均列于 request。取得该 body 也不会自动提供实际 P/k/同 epoch 或证明当前采用。

## 库存纠正、剩余输入与调度

原库存 `hasFunctionsDictionary` 命名/实现有误：默认空 dict 使 true 实际仅反映 top-level 是 dict。独立 acceptance19 已确认 89 个固定源为 85 topdict/4 list，其中仅49个有 functions dict、共754 keys，另36个无该成员；其余 dict/list 的真实 body/header 层亦已核，无两目标 exact raw。该否定只覆盖封存库存，由独立审核支撑，不能扩成全项目无来源。原库存/review/request 保持原 hash，首失败、快照和私有 diff 保留在19；本包未重查89或改旧 pins。新两原窗是另一已验来源 epoch。说明详见 `inventory-correction.private.json`。

不排全 unknown 的121 manifest或重复 fixture 投影。实际仍需同 epoch 的原 supplied k/列表上下文、既存 typed cache P、其表示/原 handle、真实 group/base encoded、同 Avatar 基础/附加项状态、pair hit/out k2/selected D，以及 native B/O 及保护条件。unknown 不作 missing；没有这些输入，不能推定121角色必要资源、补空 addProps/paramList/OpenConfig 或声称自然当前采用。准确83889来源是可继续的普通静态首缺，不将所有技术链写成外部 blocked。

`actualEncoded/actualGetterU32/actualRow/actualPairMapHit/actualCurrentAdoption` 均保持 null；证据观察标志为 false。resourceReady、D02-R3、父项 D-02 均未晋级。

本包实际核查为固定原件 pins、两体连续原字节重组与16处关键字面指令、435B 原件重组/E8及复制后 decrement 顺序；源作者4 tests和第三验收按来源记录，未在本包重跑 collector 或机械测试。交回后只建议审派这个单 helper 原窗。

| 新私有产物 | 字节 | SHA-256 |
| --- | ---: | --- |
| `semantics-review.private.json` | 17969 | `09fe13176b71a08b9c880227da58577f0cbba96f36efafd64fdc22ed33956826` |
| `inventory-correction.private.json` | 2011 | `86cc4016052cc53528d1706b6dd82255fa54fe85d50567c5b1556fc922aa92f9` |
| `querywrapper-source-request.private.json` | 9578 | `c7e72fc58428962497b0b0aa9186a558a87bfa62640d67bab42d321c4cbd3582` |
