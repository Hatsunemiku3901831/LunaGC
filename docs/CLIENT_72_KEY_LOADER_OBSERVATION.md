# R2-KEY-LOAD-OBSERVE：实际窄hook的固定只读观察

## 实施前计划

任务归属R2-KEY/F-04，目标是在独立key入口构建及owned验收之后，准备一次新的无凭据自然启动，区分原转发绑定、实际key安装、准确代码变化和SDK页面表现。不是自然key caller或本服登录验收；原callback不增加计数/wrapper，不读认证输入。

现证：零hook透明入口已真实加载/恢复；新key生产DLL已由主线release/offline构建，578048B/SHA `ad18e741052a05850ff7b80b8043af0551981793dc7e3cca89be26e565a2139d`。实际EAT是stock47、数据`Sdk72KeyProxyState@48`和冻结核心既有`sdk72_key_input_inner@49`，后者由主线明确接受；不接受其它导出。新loader的自有模块/材料/真实ilhook失败拒绝尚由主线验收，未安装到客户端。

固定输入：原五stock及profile；冻结clean core/table/asm、key核心、Cargo.lock和ilhook2.3.0；本轮key构建/owned source-artifact proof。现observer `observe_client72_clean_loader.py` SHA `6ece909b020efd46a15124f20198c02b41c43f775637c50203a29ea2fb363dd7`只可复用backend/已验路径和PID合同，不修改其原四窗、零hook断言或旧证据。实际SDK原入口及两caller来源见[key实施](CLIENT_72_SDK_KEY_LOADER.md)。

未知：自然安装是否被目标拒绝、实际页面/校验反应、callback是否自然执行、准确Config选择/route和本服会话。只读状态installed不能填平这些未知。失败保存原始固定状态/代码，不绕校验、修改模块可见性、注入调用或扩大读取。

修改范围仅新`tools/observe_client72_key_loader.py`、对应test及本页，raw仅`local/migration72/d02-resume-20261108/key-loader/observation/`。禁止改任何旧source/core/SDK/runner/Java/Proto/config/资源、Git/build/DB/端口/客户端或安装。主线串行验收和实际启动。实施者先交pure collector/backend mock；不得自行OpenProcess真实客户端、launch或RPM。

观察合同先静态固定，再执行：沿准确EAT确定64B状态位置、47 target表和原callback的自有产物机器码。状态仅接受明确稳定终态；installed对应schema/size、所有guard/held标志及SDK base/size，无任意字符串、凭据或回调计数。读取首尾同一64B状态，只用于拒绝观察期间终态漂移，不声称原子快照或自然业务ready。

成功installed分支读取原47转发目标表376B、准确SDK入口253B、两caller各76B，再检查实际ilhook跳转及原callback。根据冻结ilhook2.3.0 `modify_jmp`只接受准确near E9或far FF25编码，其余原入口字节必须保持；trampoline只在其实际代码指针处读取静态算出的有限长度，核完整prolog/callback ABI/epilog、搬移的原始指令及回原入口。callback在本次自有DLL可执行节内按原assembly唯一精确机器码（包括到已知inner导出的rel32）确定，不猜SDK函数。userdata只检查非零指针，不追读core/材料/请求。

初步预算最多8次、1600B、单次不超过512B；实现须先算精确路径和长度，不能将1600用作任意扫描许可。若精确产物/依赖无法推导trampoline或callback，返回准确缺项给主线；不放宽为“跳到任意proxy地址即成功”。失败终态不跟随未知代码指针，仅保存状态和仍可独立审核的固定原窗/target表。没有状态终态、目标退出或任何绑定/短读/身份失败均保留partial，不做补读/重试/扩大范围。

backend先校验新monitor来源及创建时间、实际image、三个模块的samefile/SizeOfImage/磁盘身份；`OpenProcess(0x101010)`只读/query/wait，句柄关闭。没有debug attach、线程上下文/GS、目标调用、写内存或export远程调用。只在主线新ready和已备份/安装核对后执行90秒module monitor与本固定读取；结束提示用户正常退出，OS确认后恢复stock并核原配置/数据。

产物/验收：精确源/产物/依赖pin、实际预算/逐读证明、near/far及原始不变范围的独立owned样本、callback/返回边/状态/路径/PID/错误拒绝及句柄关闭测试；主线审查运行产物及pure tests后才准备真实wrapper。真实轮记录已装DLL、固定状态/代码、页面、正常退出和恢复，不自动标R2-KEY、信任或D-02完成。

停止条件：源/输入漂移、无法准确固定callback/trampoline、超预算/短读、未审模块、非终态或目标拒绝/异常。按工具、输入、实现、部署和实际目标行为分类；不重复旧SW/HW/ETW。普通已授权实现无需再设用户批准节点，实时客户端动作须新的在场确认。

## 具体实现与离线产物合同

主线新增有界编码证明：允许新增`tools/client72_key_trampoline_fixture.rs`，用当前已编译的同一ilhook2.3.0/iced-x86 rlib。精确18B前缀只写入自有VirtualAlloc页，真实安装/读回Hooker生成码、drop核恢复；不执行该callee、callback或cookie引用，不加载SDK。另路径引入冻结`move_inst.rs`，对合成近/远地址实际运行原`move_code_to_addr`，以独立编码器结果核Python预测，避免Python向量互证。callback/userdata均为owned dummy/合成值。主线独占rustc编译/运行，本代理只交源与argv；此证明未完成前不进入真实client轮，也不重做14项ABI。

已新增`tools/observe_client72_key_loader.py`与`tools/test_observe_client72_key_loader.py`，旧key/clean/core/HTTP/patch/配置/资源均未修改。本实现代理仅执行纯Python、磁盘PE和自有稀疏内存/backend mock；没有实例化真实WindowsBackend，没有OpenProcess/RPM、launch、DLL加载、构建、DB、Git或新REA session。主线另实际编译/运行下面新增owned emitter，尚无实际key hook安装/客户端增量。

当前固定生产DLL实际578048B/SHA `ad18e741052a05850ff7b80b8043af0551981793dc7e3cca89be26e565a2139d`；从该产物**重新解码**EAT/47 hot+cold thunk/初始指针表，不套旧clean RVA。结果：state DATA@48的RVA `8b000`；47目标表RVA `8b1e0`；核心inner@49为RVA `9050`。完整callback在可执行节中以原assembly前后缀和rel32目标共同匹配，只有一个候选，RVA `1000`、38B，准确call在callback+10（hex），返回callback+15，rel32唯一指向inner@49。磁盘code字节为：

```text
4883ec48 f30f7f642420 f30f7f6c2430 e83b800000
f30f6f642420 f30f6f6c2430 4883c448 c3
```

这是`sub rsp,48`、保存XMM4/5、call原inner、恢复XMM4/5、恢复栈和ret的完整原wrapper，不是新统计包装。live合同只检查这38B及到inner的rel32；inner完整机器码和所有其它DLL内存不在本次RPM范围，不能称全DLL内存已核。磁盘DLL身份、冻结core源与先前ABI/owned证明另列。

除任务指定x64.rs SHA `1b1350b1fc6b0a2cac0726bd3765c1dbc8bd90450f99439adca02ba208a9e8a7`，本卡补pin同一ilhook的`x64/move_inst.rs` SHA `f98e6d9f15faeaab12e00e81c912778b4f8e789f53058b5d3547e3abdcd30720`及iced-x861.21.0的encoder.rs、op_code_handler.rs、encoder_data.rs、ops.rs。Cargo.lock固定SHA `8482fc5d3e86ac5416038e446431e729a5e55c338d8824407f9aab878c323b2f`；其余clean/core、公钥材料、源码/构建/owned proof逐项核既有固定身份，不重新构建或把源码相同当二进制可重现。精确pin表见新工具`PINS`和私有offline proof。

## 准确入口patch与完整trampoline

`Hooker::new`固定`jmp_inst_size=14`；`get_moving_insts`按完整指令累积，实际SDK3950C0前18B为：

```text
4053 56 57 4881ec90000000 488b059ed13c00
```

分别是push RBX、RSI、RDI、sub RSP,90及MOV RAX,[RIP+3CD19E]。最后MOV结束在入口+12（hex），实际读取位置为SDK RVA `762270`；本卡只验证搬移指令的这一地址，不读取cookie值。不能截14B、不能假设18B原样搬到trampoline。近/远分支的确切规则：

| 环节 | 固定合同 |
| --- | --- |
| SDK patch | `modify_jmp`仅当`abs(trampoline-(entry+5)) <= 7fffffff`用E9 rel32，入口其余248B必须等原253B窗；否则严格FF25/disp0/绝对64位地址14B，其余239B不变。E9的-80000000不满足本依赖的abs门。 |
| prolog | 原ilhook139B完整字节；包括两种栈对齐分支、GPR/flags和XMM0..3保存。 |
| callback ABI | 接着33B：MOV RDX,userdata64；MOV RCX,RSP；SUB RSP,10；MOV RAX,原callback64；CALL RAX；ADD RSP,10。userdata只核非零，不追读/记录其值。 |
| epilog | epilog1的49B加epilog2_common的29B，完整核GPR/XMM/flags/两种栈恢复分支；以上合计250B。 |
| 近搬移 | `move_code_to_addr`独立判断`abs(entry-(trampoline+250)) < 7ffff000`，iced BlockEncoder重编码。无语义的REX40被丢弃，三push和SUB共10B，RIP MOV7B重算disp32，搬移共17B。REX来自EncoderFlags及操作数重新生成，不沿用原ignored前缀。 |
| 远搬移 | 该判断不成立时，普通指令仍重编码为10B；原RIP MOV未使用RBX，`relocate_ip_rel_memory_inst`保存RBX到[RSP-10]，MOV RBX,原cookie地址64，MOV RAX,[RBX]，从[RSP-10]恢复RBX，共23B；总搬移33B。 |
| 回原边 | 紧随搬移原始ilhook的FF25/disp0/QWORD绝对跳转，严格返回SDK `3950D2`，即入口+18（decimal）。 |

近trampoline精确281B，远297B。入口E9/FF25的距离门与搬移器近/远门不同，分别计算，不能用入口JMP类型代替RIP重编码选择。旧ilhook `move_inst` 的owned Rust编码例已展示同一RBX栈保存/恢复；本卡Python另外提供独立固定字节向量，逐段错位/篡改拒绝。初稿只有静态推导及mock，随后主线实际原emitter证明了近/远mover与真实Hooker近路，范围见下。第一次live只接受与准确源及已验mover合同相符的实际代码，分歧保持partial并返回准确入口，不扩大接受编码集合。

## 状态、读取顺序与精确预算

64B数据按`<6IQ8I`解析。installed仅接受schema1/bytes64/reserved0、phase5、failure/win32=0、installed1、实际已绑定SDK base/SizeOfImage、path/disk=1/windows=7、original/self/SDK held全1。failed只接受phase6、固定failure101..113、installed/SDK held=0、original_bound=1；self_held仅101可为0，其余为1；112/113在bound后失败，保留准确SDK base/size/三窗位，之前失败均为零。phase0..4始终nonterminal，不作为ready，也不跟随代码指针。

顺序固定为state.before64、47 target376、SDK入口253、两个caller各76、trampoline精确281或297、callback38、state.after64。首尾完整64B必须相等；只证明该观察区间无可见终态漂移，不保证原子快照或自然业务时序。

| 路径 | 实际请求 |
| --- | --- |
| installed，近搬移 | 8读1228B，最大单读376B |
| installed，远搬移 | 8读1244B，最大单读376B |
| failed，固定原窗/目标均相符 | 6读909B；无trampoline/callback追读 |
| 非终态/非法state | 仅首尾state，2读128B；首state短读/错误只1读64B |

硬上限仍是8读1600B、单读512B，不是任意扫描许可。target或caller不符时停止条件代码链；patch编码/未改尾部不符时不追其未知跳转。所有短读/Win32错误保留partial，绝不补零或重试。只保存读地址/请求与实际长度/固定error、state数字、代码匹配布尔；未知trampoline正文及其hash不落盘。没有userdata、公钥内容、请求/认证参数或callback计数读取。

backend沿新90秒module monitor，只接受该新observer源码SHA、实际image文件身份、准确PID creation以及三个模块在monitor/当前snapshot中的samefile/base/SizeOfImage/磁盘身份；`OpenProcess(0x101010)`含SYNCHRONIZE，finally关闭句柄。同一文件的`\\?\`与普通路径用samefile处理，不按字符串前缀猜等值。任一必要模块未加载或枚举/绑定失败时，保留partial且首次RPM前停止，不为了取failure state放宽未审模块门；此时module inventory就是准确缺项。

## 实际测试、冻结身份与主线CLI

首轮16项全部通过；增加原emitter报告校验、拒绝和独立offline CLI后，当前**19项全过**：`python -m unittest tools.test_observe_client72_key_loader -v`。范围为自有稀疏内存、独立近/远字节向量、mock backend及实际固定生产DLL的离线PE读取；没有Windows进程API或DLL加载。覆盖高64位/8次精确预算，prolog/ABI/epilog/搬移/cookie/回原边逐段篡改、callback rel32、userdata零值、不变尾部、两种距离门、failed/nonterminal/非法state、状态漂移、读错/短读、47目标/caller不符、source/PID/path/base/size/枚举漂移与句柄关闭。

当前源码SHA `44c37ecda27df5c2d0db9c00e2d1227b0314d1e444a0e2bd78a1491bcf54b5e2`；测试SHA `3800064dd76c510a02d1df59b5ee72390d6b72b1178724dce3d40df89bcd1605`。五stock、SDK三窗、公钥材料、冻结源/构建/owned proof和准确生产artifact均已实际离线核对；私有证明放本卡`observation/`，旧证据不覆盖。

## 原emitter证明的交付与独立入口

新增Rust源SHA `fc9cdaf151876ed2d54bb4cc7cd524e0d8f64d270b0c5b7013bce5db48136cc2`；声明的WinAPI只有自有页VirtualAlloc/VirtualFree，Hooker内部仍为原依赖页保护操作。它从18B前缀解码至至少14B，要求实际结果18B；直接路径引入冻结move_inst源码、`pub use ilhook::HookError`和同一iced rlib，对两个合成目的地实际编码；另在自有4096B页写同18B加NOP，真正hook并读取253B入口/精确trampoline，drop后确认253B恢复。自有dummy callback只作为代码地址，userdata为明确合成非零常量，均不调用/解引用，不加载任何SDK或stock DLL。stdout仅owned代码/地址/长度/固定布尔，允许保存在本卡忽略目录；没有认证输入或材料。

当前Cargo采用`release/build/<crate>/<hash>/out`，**没有**`release/deps`目录，不能套旧命令。主线读取并固定当前39个rlib及对应dll目录，以参数数组逐项传`-L dependency=...`，MSVC import lib用已核registry目录的`-L native=...`。原请求的准确两个入口是：

| 库 | 实际路径（相对key-loader/target/release） | SHA-256 |
| --- | --- | --- |
| ilhook | `build/ilhook/c53c2a35b7683e7d/out/libilhook-c53c2a35b7683e7d.rlib` | `4fc54fc59cd7fa4b27bb0fe3ae87242f6904a42a6e93a6644cafff55c2d4ac99` |
| iced_x86 | `build/iced-x86/a4681a1d1485434c/out/libiced_x86-a4681a1d1485434c.rlib` | `72fe2e24e2303f818c96a908677b156e10b5d485fb4b247a5e5cea75ad556a65` |

同目录对应full `.rmeta`的实际SHA分别为`20bb64053729adef396cd0b507c8fe31348481111aca40b64ca121ef8b71d74a`、`51421a4fc07e61dc9da415e4fc2169795d00f04fea2a95c361d5e6433a467a7d`，不能只给上表rlib，也不能只给rmeta。

rustc主体为`--edition=2021 --crate-name client72_key_trampoline_fixture tools/client72_key_trampoline_fixture.rs`，每个依赖同名分别给`--extern <name>=<rlib>`和`--extern <name>=<rmeta>`，其余`-L`使用上述实际搜索目录；另须与实际patch release profile一致的`-C lto=fat -C opt-level=3 -C codegen-units=1 -C strip=symbols`。单独`-O`不足以链接这批LTO产物。最终成功输出为`observation/owned-emitter-main4.exe`。

Rust官方允许为同一crate的不同格式重复给`--extern`；`embed-metadata=no`只在rlib/dylib留下stub，应另供full rmeta。这解释前两次输入失败；并不表示rmeta可独立链接，因为它没有机器码。[rustc --extern](https://doc.rust-lang.org/rustc/command-line-arguments.html#--extern-specify-where-an-external-library-is-located)、[Rust embed-metadata](https://doc.rust-lang.org/unstable-book/compiler-flags/embed-metadata.html)、[Rust libraries/metadata](https://rustc-dev-guide.rust-lang.org/backend/libs-and-metadata.html)。本批库的LTO输入需由rustc完成对应链接处理；`-O`只是opt-level3，不能替代LTO。[rustc -O](https://doc.rust-lang.org/rustc/command-line-arguments.html#-o-optimize-your-code)、[LTO/bitcode](https://doc.rust-lang.org/rustc/codegen-options/index.html#embed-bitcode)。

主线前三次尝试都在EXE运行前失败：main1缺full metadata、main2补metadata后缺rlib、main3双格式metadata通过后link.exe因漏同profile LTO报LNK1107。原build-main1/2/3日志和proof保留；fixture源、依赖、prefix和预期编码均未改。这些是构建输入/链接模式问题，不是native编码差异，不伪造deps目录或改冻结库。

main4使用两个crate各自rlib+rmeta、同release profile和原搜索目录后真正编译成功。准确109成员argv及源码/profile身份保存在`owned-emitter-build-main4.private.json`，SHA `3f06f94640722d7dcb1c33dee354226bbda01b280aac64ff826343c20d970432`；实际argv有41个`-L dependency`（39个rlib及其它编译依赖目录），两个`-L native`是当前registry中Windows MSVC0.52.6/0.53.1的lib目录。实际命令保留了前面的`-O`并另传精确四项`-C`，没有改库或Cargo配置。若以后确需重编，主线可读取这份实际argv，**仅把`-o`后的输出换成全新专属路径**，在同checkout/已核portable rustc与MSVC环境以PowerShell参数数组重放；不覆盖main4产物，不把其它机器的路径照搬。

| 实际主线结果 | 身份/范围 |
| --- | --- |
| emitter EXE | SHA `bed9035f58f9bcabef2ddb9457a344870d1f6ba3ff00cfbdcba46868a8be66d9` |
| 第一次真正运行 | exit0/stderr0；`owned-emitter-main4.private.json`为2290B，SHA `e33e6483735728ed5fdf5f3eb1e9dc7a4bb866cca49c31fe3ea61ddb6a76c7fc` |
| 原move_inst执行 | 合成near地址实际输出17B、far地址实际输出33B，与observer预测分别相同 |
| 真正Hooker安装/读回 | 此次实际选择**near**，完整281B、入口patch和drop恢复均通过；未执行callee/callback/cookie，不加载SDK |
| Python原编码校验 | `owned-emitter-check-main1.private.json` completed，SHA `b6b2cc9822449d6d5b37ae5a93be84f7d93e93bcf98cf6d531e918d474d40432` |

因此，**不能称真实Hooker far安装已跑**；far已有的是原搬移函数在合成地址上的真实执行及完整远trampoline的静态/mock审核。实际近Hooker与两个原mover结果已解除“只靠Python互证”缺口；不重做已过的14项ABI，也不重复本次原生证明。

获得真实emitter报告后，先执行只读离线校验：

```powershell
python -m tools.observe_client72_key_loader --client $client72Path --owned-emitter-report local/migration72/d02-resume-20261108/key-loader/observation/owned-emitter-main4.private.json --check-owned-emitter --output local/migration72/d02-resume-20261108/key-loader/observation/owned-emitter-check-review.private.json
```

此分支不要求ready、不创建backend/monitor、不接触目标。它分别核原move_code_to_addr两例（准确地址和17/33B），再核真实Hooker输出的完整入口JMP、不变尾部、281/297B原trampoline和drop恢复见证；任何原编码差异拒绝，不能靠Python向量通过绕行。live分支也强制给同一`--owned-emitter-report`并先完成这些核对。

主线先独立审上述源/实际DLL和测试，按主线计划备份/安装核身份，并取得**新的user-ready**。准确观察命令：

```powershell
python -m unittest tools.test_observe_client72_key_loader -v
python -m tools.observe_client72_key_loader --client $client72Path --owned-emitter-report local/migration72/d02-resume-20261108/key-loader/observation/owned-emitter-main4.private.json --user-ready --output local/migration72/d02-resume-20261108/key-loader/observation/live-main1.private.json
```

`$client72Path`必须是本机当次已核GAME_PATH。脚本只在此显式CLI启动90秒module monitor；实现阶段没有调用该路径。不远程调用state export或目标函数。预检要求四原文件、profile原stock身份、`Astrolabe_orig.dll`准确stock以及已安装`Astrolabe.dll`等于上述固定key产物；不重写profile或跳输入hash。一个产物固定一个callback/代码合同，无通用产物放行开关。

结果的`installed-code-bound`只表示实际稳定installed状态与本次固定8窗链相符；`failed-state-original-code-bound`表示固定失败终态和原窗/转发相符，失败没有变成hook接受。二者都不声称自然callback、key替换发生、Config选择、route、信任或登录成功。主线再记录页面/完整性表现、用户正常退出、OS无目标、stock与配置/数据恢复；不沿用此前ready，也不借本观察子项晋级R2-KEY/F-04/D-02。

主线已独立运行19项测试并审核源码、实际emitter/build及纯collector边界；同一source epoch为`44c37ec…4b5e2`、test`3800064…d1605`、owned Rust`fc9cdaf…136cc2`。真实emitter和原编码校验由主线实际执行，不以代理报告代验。私有 `main-preparation.private.json`记录新安装/恢复脚本语法核验与源/产物固定身份；本轮现场最多8读1244B，无目标调用或认证输入。此准备出口已验；实际客户端结果随后另记，父项仍进行中。
