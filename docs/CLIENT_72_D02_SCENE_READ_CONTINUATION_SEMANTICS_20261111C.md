# F-03d3s2 / Read循环、候选进度与返回续段（2026-11-11C）

任务：`F-03d3s2/SCENE-READ-CONTINUATION-SEMANTICS`，父项`D02-R1/R3`。沿[204B同frame来源](CLIENT_72_D02_SCENE_READ_CORE_SEMANTICS_20261111C.md)消费[415B正常续region](CLIENT_72_D02_SCENE_READ_CORE_CONTINUATION_REGION_20261111C.md)，不重设栈、不把d3c当callee入口。主线已通知本region第三独立验收通过（`acceptance12/continuation-independent1.private.json`）；本页复用该来源验收，局部语义不升级自然reader/Scene/Proud或resourceReady。

## 结论与实际D02优先级

This DWORD`+0`为零时，`141138d46`直接跳到窗外`141138f06`。上游managed compressionType数值0经构造器传入，但尚未绑定This字段；**不能据此替D02选择零字段或非零字段路径**。

非零字段路径在本region内有循环、状态推进和复制CALL，但没有直接dst字节store。每块须通过虚槽AL和输出长度检查，才调用`141fdf310(dst,temp+within,chunk)`；之后减remaining、加dst/O并递增表索引。循环停止后准备`RAX=原K−remaining`，清理临时值并恢复原R14；**仍无RET，正常fallthrough到`141138edb`**。故此时只能确认进度表达式，实际函数返回/full/partial/EOF/error未全闭。

唯一后继优先edb的真实containing region：它直接承接准备好的RAX与cleanup，可闭正常返回或给准确控制首缺。先展开codec、1415c20c0或整个native库不能代供这个返回。f06/efa/f85若不在下一实际region中，就继续未知，不另取一窗。

## 已证循环与字段角色

沿用This=RDI、原请求量K=RBP、当前位置O=R13、表索引q=RSI、remaining=R15，RSP一直为原入口S−c8h。前段已用`This+28`作无符号除数取得q，并以`This+30`作O的unsigned界限；它们的构造来源及业务名字仍未知。

| 原PC | 观察和原关系 | 边界 |
| --- | --- | --- |
| `d3c..d52` | 保存原R14；This DWORD0非零时，以该DWORD及`[1421fec24]` DWORD调用`1415c20c0` | 返回对象D未知，不猜算法/codec类型；零字段路径去f06 |
| `d57..d88` | D存`[rsp+d0]`；栈`+60/+70/+78`QWORD置0，`+68`DWORD置1，以临时地址、`This+28`及1调用`140971460` | 只证临时描述符输入，不代答成功buffer或容量 |
| `d8d..daa` | RCX=K；K signed≤0跳进度准备。否则R14=16*q，remaining起始K；unsigned q≥QWORD`[This+18]`退出循环 | 当前208B调用链已筛K>0；本callee其它输入仍不能泛化 |
| `db0..dbe` | 表基址为QWORD`[This+8]`，每项16B；DWORD项`+c`零扩展为E。若E unsigned大于QWORD`[rsp+70]`，跳efa | 只记录界限形态，不假称真实可用temp容量 |
| `dc4..de1` | within=wrap64(O−q*QWORD[This+28])；候选n=wrap64(E−within)，再按signed64 min(remaining,n)选择chunk；chunk符号位1跳efa | 不是随意按全局K复制，也不把负值夹成0 |
| `de7..e19` | 栈QWORD`+50`先放E，`+58`先放项DWORD`+8`零扩展值；虚槽调用的RCX=D，RDX=项QWORD`+0`，R8=&`+58`，R9=QWORD临时`+60`，第五参=&`+50`；目标为`[QWORD[D]+18h]` | 实际槽目标及输入/输出内存效果未审，不用算法名或有利回调补全 |
| `e1c..e32` | AL必须非零；随后重读该项DWORD`+c`，与完整QWORD`[rsp+50]`相等，否则跳efa | 只证调用者检查AL/完整输出长度；不证明数据内容正确或自然调用成功 |
| `e38..e4e` | SRC=QWORD`[rsp+60]`+within，COUNT=chunk，DST来自`[rsp+d8]`；原CALL`141fdf310` | 本region本身未写DST字节。复制调用的本次实参有效性/实际输出未观察 |
| `e53..e77` | 若CALL正常返回，remaining−=chunk、dst+=chunk、O+=chunk，dst写回`[rsp+d8]`，q++、表地址+=16；remaining signed>0回da6（重做表界比较） | chunk=0没有额外拒绝；不能加原窗不存在的进度保护或把算术进度当实际读数 |
| `e7d..e88` | q越表界或remaining不再正数时，取回D；RBX=K−RCX，其中RCX是该出口的remaining；初始K≤0出口为K−K=0 | 表界可产生部分进度值，但不等于已经确认资源EOF |

由此只可按条件说明完整/部分进度：若检查及复制CALL正常完成，remaining降到0时准备K；在表项界限处提前结束则准备K−remaining。调用者并未检查复制CALL的返回值，不能把这个表达式升级成实际byte数。

四条efa外边分别由E超过临时界限、chunk负值、虚槽AL为0、输出长度不相等触发。它们跳出时R12/volatile/temp状态并不相同；efa的cleanup/错误/返回尚未知，不统一代为0或异常。

## 复制旧成果和cleanup边界

已复用固定旧`resource-index-astra/native-final-1.json`：55885B / SHA `6a24ef69ded74323049144849764ce4e727f99c5090e8e3d508e8f167402915c`。它记录`141fdf310..141fdf985`、code SHA `703631b63977fff08c47945b49ec3b488b5ded421e8c50090a39979119ba3168`，与[既有native复制样本](CLIENT_72_RESOURCE_INDEX_NATIVE.md)对应。只读复用该限定历史行为，不重采helper，不把当前未知虚槽的输出和当前SRC/DST/chunk自动当成已成功复制。

`e8b..ead`按临时指针是否非零及`[rsp+78]`的bit63条件调用`141a2e5a0`。`eb2..ecb`在D非零时先调用D虚表首槽（EDX0），再以D及label39h调用同helper。只证原CALL/条件，清理内部和D寿命未闭，不命名成功析构/释放。

`ed0`才设置RAX=RBX；`ed3`从`[rsp+98]`恢复原R14，指令结束为edb。正常续段已知：RAX/RBX为候选进度，RDI=This、RBP=原K、R15=remaining、RSI=当前q、R13=当前O；`[rsp+d8]=[S+10]`为更新后的dst。R12保留D数值，但经过清理CALL后不称仍有效对象；其余volatile和RFLAGS不从早前TEST继承。原RSI/R15保存位置仍为`[rsp+e0]`/`[rsp+90]`，不能在edb另起栈或调用frame。

零字段f06边发生在D创建前，R12仍为原dst，不能套用edb的D/cleanup状态。前段f85三边也不在本region，继续保留不同来源状态。

## 原件与唯一新region规格

本轮核七件双fresh逐byte相同、415B完整hash和95条原hex连续重建、19引用/0RET、真实oldOverlap0及五stock同源。region`[141138d3c,141138edb)` SHA **`d837f527ce2dec86c4cbdf0464d772c989040d072cc8a232921114e193a865c2`**；pureaudit SHA `a7584f55cb7d0de9c53552fd1ed02f217f3c049dc9096321f61e630fb1c7ba03`，原7tests和第三验收复用，不重复执行。

来源检查：`local/migration72/d02-resume-20261111c/scene-read-continuation-semantics/semantic-source-check.private.json`，**8836B / SHA `12430f9acb39d1472cef79a3b2010471640f26a3430e3d64c6525319c1146612`**。定向旧resource/scene/astra原件中没有找到edb/f06/efa完整raw；只记录所查范围，不称本机穷尽或外部blocked。

唯一request：`local/migration72/d02-resume-20261111c/scene-read-continuation-semantics/return-continuation-region-request.private.json`，**18700B / SHA `04e9bd904df2559bffda87b7b7d5d5c3281cf6edefbb27ee4633dbbca73705da`**。

| 项目 | 规则 |
| --- | --- |
| 前置和控制源 | 415B来源第三已由主线接收；源PC为同frame normal fallthrough`141138edb`，不是新函数/方法入口。固定完整415B/95条；新PE仅核ed0..edb原11B `488bc34c8bb42498000000` |
| pdata | 固定exception目录14344080B及SHA；unique containing Begin≤PC<End，exact单独记录、可为空；不造AOT/entry或用邻方法定end |
| 先诊断后body | 保存实际范围/覆盖/12B pdata、与旧204B及415B两region的交集、distinct-new ledger、完整预算/节决定。交叠或歧义先0body返回，不重读旧段/猜拼/伪报缺exact |
| 单region预算 | 仅真正新增、无交叠、正值、可执行/file-backed的完整region≤8192B/2048条才读；body前pre-body留存；不聚合xdata/多个region |
| 验收与解释 | 全部RET/外条件边/无条件边/末fallthrough保留，双fresh、纯audit、主线第三和来源/context/containing/交叠/节/短读/解码门禁。f06/efa/f85只有被同一实际region覆盖才按各自incoming状态解释，否则仍未知 |
| 停止 | 真实return/cleanup后若仍缺关键callee/data/control，交准确首缺；0新codec/copy/registry/constructor或独立f06/efa/f85采集，不实现model/driver或造byte成功 |

本页和request足以交Terra下一有界source包。完整logical read、实际字节、full/short/EOF/error、自然This字段/对象、arrayLenHelper和初始base继续分别保留未知，不用本地循环/长度检查填空。

本轮仅新增本页及独占忽略目录检查/request；其它tools/专题、Project/Harness/HANDOFF/source/config/Proto/patch/resources/Git只读。0新PE/callee/global/runtime/model/CPU/live/build/Java/DB/端口/client/调试器/部署，无需重启。无新安全拒绝；准确A/B拒绝CALL/data边保存并停对应依赖，新拒绝原文停，不换模型/工具/方式间接重试，也不以World边泛封reader。主线负责后继、共享状态和提交，本有界项交回即释放槽位。
