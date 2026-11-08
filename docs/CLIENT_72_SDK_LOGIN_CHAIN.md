# D02-R2：SDK登录调用链与只读状态入口

恢复后配置来源已补：准确`SDKDelegate.LoadFile→AssetUtil.LoadJsonFile`定位包内`server_pc`，原生Init的`url:string→map+860`与`app_key→ConfigModel+130`已有独立原生片段验证；主combo builder调用getter时明确R9=0，直接读初始化map。普通文件或native回调producer缺失不再作为该分支必需阻塞；完整Init、自然env、签名算法及本服请求仍未验。见[新配置合同](CLIENT_72_SDK_ROUTE_LOADER.md)，下文旧未知按对应卡范围读取。

## 独立攻坚计划（2026-11-08）

目标：承接已冻结 [SDK transport](CLIENT_72_SDK_TRANSPORT.md)，从固定输入恢复Invoke动作注册→login worker→HoYoNetworkEngine/transport；优先研究有明确producer的单例、全局或容器是否持有可供ReadProcessMemory只读采样的自然easy handle/URL副本。新卡不修改父卡三个冻结文件。

证据：profile五文件、父卡11模块磁盘表、父卡自然启动raw、父卡source及确定性raw全部固定SHA-256。AccountPlatNative `41E6F0`入口与`4284A0`分派、`+628/+7C0`存储只对已建立的easy handle有意义；不能反过来按这些偏移猜对象，磁盘静态字节也不等于当前内存身份。

未知：动作注册表与具体login worker的完整指向；请求/handle持有者和释放点；对象是否lazy、线程安全性与自然存活窗口；真实路径是否账号请求。动态GetProcAddress对象表以及未解析的间接调用单独保留，不以字符串或静态名提升为自然调用。

候选：MiHoYoSDKDll单例动作树、HoYoNetworkEngine单例/vtable、请求队列或异步任务对象、easy handle局部生命周期。优先核准确指针writer/reader/RTTI/构造和析构，再选只读链；若handle仅栈局部或已释放，拒绝把它说成持久全局。

最小实验：固定模块重新读取；从vtable/动作名实际交叉引用追具体注册和调用边；对可执行短段用合成非零64位、空/错误及边界输入做原生离线验证，并明确替代的分配/容器/线程模型。若找到可读链，交付moduleRVA→指针→字段的准确来源、类型/标志/大小、初始化条件和失败出口，供主线合并同一冷初始化只读批次，不要求无依据重复启动。

修改范围仅`tools/client72_sdk_login_chain.py`、对应test、本专题；raw在`local/migration72/d02-active/sdk-login-chain-astra/`。禁止live、attach、hook、inject、新DLL、断点、网络、服务、数据库、共享构建及Git提交。不能持久化真实URL、账号、token、query/header/body或其hash；拟议采样只在内存分类固定host/path，未知只留长度。

失败出口：固定输入或父卡身份变化、未知间接边、生命周期无法闭合时按精确缺口停该链，继续独立离线项；没有producer支持就不提供猜测对象地址。最小自然entry事件合同继续保留，但本卡不执行。验收为固定source/input哈希、独立原生样本/断言、双新进程确定性及第三命令，研究结果不等于D-02。

## 结果与证据等级

本卡闭合了**条件性的AccountPlat账号提交链**：原生动作注册和分派→LoginManager选择服务→AccountPlat模式1→AccountPlatAccountLoginJobber→RequestManager→NetworkManager→HoYoNetworkEngine异步POST任务→父卡固定的curl兼容setopt入口。动作注册、分派、模式选择、入队/出队及释放分别有原始机器码离线执行；完整登录、UI提交、加密与HTTP没有在夹具中执行，链的中段是固定文件反编译/RTTI/vtable/指令证据，不能说“已执行真实登录”。

找到三个有明确producer的只读全局根和线程池字段，可合入主线一次冷初始化采样；**未建立请求完成后仍保存easy handle或URL的稳定根**。原生线程池先出队再调用transport，完成后释放持有URL的任务；easy handle的`+628/+7C0`也随cleanup释放。因此到达SDK登录页后追旧task/handle不成立，队列count为0也不证明没有执行中的请求。

所有地址均为固定`AccountPlatNative.dll`的RVA，preferred base为`180000000`。自然读取必须加实际模块base。类名来源是准确MSVC RTTI/COL，而非附近字符串；curl兼容函数名仍是父卡已注明的语义推断，无符号名证据。自然调用、选择的服务、UI回调、`loginUrl`配置值、请求发送/响应和本服账号验收均未知。D02-R2和D-02仍未完成，客户端验收增量为0。

## 输入及REA边界

每次运行重新核profile五文件和父卡自然11模块，共13个唯一文件；完整相对路径/hash在最终报告`moduleSHA256`。五文件输入及11模块来源继承父卡，不重新用相近文件挑目标。profile SHA-256为`a3b9eda630942ba8479f713fc1692c7eb7b05198ae382dfe878ecc963051fd5d`，SDK为`adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25`。父自然raw为`plain/run-elevated2.json`，SHA-256 `2643bfe4006acaf3a7a97c1c0e681d5e71ae8a3c27f048cc31d87dc273adcf9f`；历史模块snapshot不证明磁盘字节等于当次loaded内存。

本卡应用`reverse-engineer-anything`技能，使用本机静态Ghidra。一次固定9MB SDK会话实际取得反编译，随后导出并关闭；未重复旧EXE超时任务，未调用live、attach或hook。完整Evidence在本卡忽略目录`rea-final.json`（74条记录，含承接记录），SHA-256 `94ff59a7900378914d1ee387616bf81412bc0be306090f6c9db563b5e0e091a6`；关闭快照`rea-snapshot.json`保留64条会话Evidence。最终报告`reaProcedureEvidence`索引59条分析/反编译记录的ID、参数与限制。

`analyze_function(1802F0940)`的value-flow受3000 operations/3600 def-use上限限制，21个调用未解析；其余多为指定函数的pseudocode，不把它们宣称成全程序已解调用图。Ghidra的security_check_cookie注入警告是反编译表示，不是对DLL打补丁；部分type propagation未收敛，PDB load state未知。关键类型/槽位另外从原始PE独立读取，不能只靠反编译猜测。

| 证据入口 | REA Evidence ID或独立原始记录 |
| --- | --- |
| NetworkManager/engine factory `39AC70` | `ev_71ead7aae6b272c94c34394ed3bf3257a2b83f0d1336c7f0bef1fb70e1c1f3f6` |
| Engine ctor `2EEE30` | `ev_983a0b743acd819429a041bca529b7da6bb21718b82561dfc9aa0f8911a54f59` |
| ThreadPool ctor `13C7E0` / worker `13CA70` | `ev_5372252a7966053386ac6ae623aa69063f5c98f26cbf7efab458eaf48a647fa2` / `ev_7b0c84d29bece0b44f12377072083c0b3ea6bd611993d240d4be61a1f431900d` |
| Combo动作注册 `3BF960` | `ev_f0ae5474159c7fcb680e767faaa4b0f1a7efe88595bcd30da0ddc6cf50cd7bb5`及`nativeRegistration` |
| Account提交 `34ECA0` | `ev_a0fca923c1181c33a7e26c0d78cd1a7aa0c8a65f3e023198547afdc053749186` |
| Async POST producer `2F4290` | `ev_ed3abd1b81fa7fe1f296e6e7fb57ba0476130d309c9100e851c689c0edcfbaf9`及`nativeQueues` |
| easy cleanup `427670` | `ev_34b6551c8dcfbbdae0bdad8ae7056b66d0b1a2d24557b93f4460932cdafd05f4`及`nativeEasyCleanup` |

## Invoke注册、回调及条件登录链

SDK factory`13CDD0`分配`140`字节，调用`131EF0`构造，`13CE3D`发布全局`7EAB80`。主vtable为`694980`，`+8`次级接口vtable为`6948E0`。离线cold/warm两次执行同一producer返回同一非零64位pointer；warm不再分配。这个根与下文网络根不同，未加入主线最小采样合同。

`3BF960`对合成MiHoYoSDKComboManager执行真实注册代码，插入24个动作至SDK`+48`树，计数在`+50`。树node的key std::string位于`+20`，function对象始于`+40`，其内部`+38`指针即node`+78`。bound functor vtable为`6D37A0`，native thunk`1BC610`读取functor`+8`函数指针和`+18`64位owner。准确注册结果：`login_login→3CBE30`、`login_account_plat→3CC220`；不能把其他action名称自动映射到HTTP。

注册走次级vtable`6948E0+10→133810`；`+20→135570`还调用`135720`插入SDK`+18`集合，后者参与日志/可见性条件，不是HTTP接口。export入口`13D0C0`清SDK`+DC`、包装用户callback/index，再经主vtable`+20→133020`查动作树；该分派检查回调是否存在及SDK字节`+DB`状态。离线unknown、empty和阻止状态均不调用动作目标。nested response functor vtable为`694A80`，原始callback wrapper为`6A84B0`，原始callback64在inner`+8`，index32在inner`+10`；样本分别使用`123456789ABCDEF0`和`12345678`，未截成32位。

以下是**满足所列分支后**的固定文件调用链；表中的槽位是字节偏移。最终报告`staticVtableEdges`对16条vtable边直接从PE取8字节目标，测试独立核对；中间函数具体控制流另由代码窗口/REA保留。

| 阶段 | 静态准确链及条件 |
| --- | --- |
| 动作到服务 | `3CBE30/3CC220`条件调用component aggregate`+18`的LoginManager；vtable`6C15E0+60→31B820` |
| 服务选择 | `31B820`询问aggregate`+40` ConfigManager的virtual`+2B0`；false/缺失选LoginManager`+368` MDK，true选`+370` AccountPlat，并写当前服务`+360` |
| 服务基类 | AccountPlat vtable`6C2CA8+8→3413E0`；`+120→335830`还有ticket/autologin分支，不能绕过这些条件将所有login等同密码提交 |
| 模式1 | `3433E0`按低32位模式分派；AccountPlat vtable`+B0→32F460`读service`+348` AccountPlatAccountLoginJobber，callsite`32F61B`经worker virtual`+8→34EA20` |
| UI submit | `34EA20→35B820`设UI；submit捕获thunk`34A67C`执行`mov rax,[rcx]; jmp [rax+70]`，Account worker vtable`6C5390+70→3508B0`；字节`+8`未ready时可经`35A890`延迟，正常分支到`35C190` |
| 账号请求构造 | `35C190`检查输入并经worker virtual`+60→34ECA0`；后者读取配置key `loginUrl`，经CryptoManager构造account/password JSON，随后调用aggregate`+8` RequestManager vtable`6D2218+B8→3A57D0` |
| Request/Network | `3A57D0`在this`+10` helper存在时到`3AA9D0`；其取aggregate`+10` NetworkManager，vtable`6D1F48+18→39C5E0`；后者取this`+B0` engine，经engine vtable`6BF300+58→2F4290` |
| 异步到transport | `2F4290`复制URL/body/header/回调并向engine`+70`池经`2F8530`入队；worker执行request functor`6BF1B0+10→2FA3E0`，再经engine vtable`+48→2F0940` |
| setopt/perform | `2F0940→41E620`获easy→`2F3360`通用配置→`41E6F0` option10002→`41E6E0` perform。ABI、完整46字节入口、真实错误分派及URL存储见父专题，未以模型替换 |

准确RTTI另固定LoginManager`7E50D8`根、ctor`317560`、size`400`、vtable`6C15E0`；MDK全局`7ACDB0`/vtable`6C2098`；AccountPlat全局`7ACDB8`/vtable`6C2CA8`。AccountPlatAccountLoginJobber全局`7ACDF0`、size`148`、ctor`33ECB0`中的`33EDF4..33EE73`发布分支、vtable`6C5390`、COL`6F2EB8`，TypeDescriptor为`.?AVAccountPlatAccountLoginJobber@mihoyo_sdk@@`。邻近vtable`6C5FC0`属于Phone jobber，不能按近名混用。

模式0→`32EFA0`读service`+340` Phone；模式3→`342A10`读`+350` QR；模式11→`32F930`；其它→`331570`。只用类型证据给Phone/Account/QR命名，不虚构整个enum。离线模式`100000001`与1选择同一目标，证明使用低32位；尚未证明本次自然UI采用哪个模式。`loginUrl`只是配置key，不能据此声明固定host/path，也不能把账号加密/请求payload写入观察日志。

## 只读冷初始化最小合同

机器可审合同`local/migration72/d02-active/sdk-login-chain-astra/rpm-roots-contract.json`，SHA-256 **`c2620c938c2d5be83c6e1dc96eeba862cc9a85886a8f1f2b08c678bdc97071da`**。最终报告原样嵌入`readOnlyRootContract`；该文件`codeWindows`包含以下producer/reader的完整选定机器码窗口、SHA和指令；`globalReferences`列已核指令边界的读取引用。不能仅凭只检MOV load/LEA的引用扫描声称发现store，store另外从完整窗口核定。

| 输入/宽度 | producer、reader及对象识别 |
| --- | --- |
| module`+7ACD88`，QWORD 8B | `39AC70` factory在`39AD56`读取；null时分配`140`，调用ctor`2EEE30`，`39AD8D`发布。对象首QWORD应为actualBase`+6BF300`，类为HoYoNetworkEngine；另有其它创建此类对象的代码，不能据一个全局断言进程只有一个engine |
| module`+7E90D0`，QWORD 8B | 同factory分配`B8`；`39ACDA/39ACE1`写vtable`6D1F48`，`+B0`绑定engine；`39AE47`发布、`39AE53`写out。首QWORD应为actualBase`+6D1F48`，类为MiHoYoSDKNetworkManager |
| module`+7ACC80`，QWORD 8B | getter`153820`在`153824`读取，分配/清零`A8`，`15385D`发布；`1538E0→39AC70`填aggregate`+10` NetworkManager。**不是多态类，无vtable**，头部是component指针，不能作vtable比对 |
| engine`+70`，QWORD 8B | ctor`2EEE30`：`2EEE9C lea rsi,[rdi+70]`、`2EEEA0 [rsi]=0`，调用`13C7E0`构造size`158`的ThreadPool，`2EEFA2 [rsi]=rbx`。ThreadPool**无vtable**，开始部分是mutex |
| pool`+70/+78/+80/+88`，各QWORD 8B，总32B | 依次为deque block-table pointer、map capacity、start offset、queued-not-popped count。ctor`13C818`令RBX=this`+68`，`13C824/828/82C/830`清`[rbx+8/10/18/20]`。writer`2F8530/2F8900`，consumer`13CA70` |
| pool`+128`，BYTE 1B（可省略） | ctor`13C865`置1，worker`13CA70`读循环条件，destroy`13D4A0`清0；含义为worker loop enabled，不是HTTP状态/成功标志，不按DWORD读取 |

主线首批仅三个global各8B、已初始化对象各头16B、engine`+70`指针、pool`+70..8F`描述32B，以及module`+41E6F0`完整46B与固定PE对照；不读node、URL、body/header和个人字段。aggregate头可读但不认vtable，pool头也不能认vtable。所有根允许lazy/null；对象内容和队列descriptor不是跨字段原子快照，读取失败/变化只留状态，不补猜地址。queue count排除正在执行的任务，不能当总请求计数。`+128`精确宽度已闭，仍非本轮必需输入。

入口`.pdata [41E6F0,41E71E)`，预期hex为`895424104c894424184c894c24204883ec284885c975088d412b4883c428c34c8d442440e8879d00004883c428c3`。上述静态合同只提供准确读数位置，不代替当次模块身份/内存字节核验，不给子代理live权限。

## 所有权与观测窗口

easy allocator`4281D0`请求`4D40`字节，magic DWORD在`+4D38`，值`C0DEDBAD`；easy没有C++vtable。`41E620`将handle放入调用者临时位置。父卡`+628/+7C0`属于这个easy对象，**不属于engine或ThreadPool**。请求后公共helper`2F1AE0`将R8 handle保存R12，`2F1D6D→41E5E0→427670`释放；第三builder`2F5AE0`也在`2F5D4F`cleanup。`427670`清magic和active URL pointer，调用`427CB0`清字符串存储并最终释放handle。受限native cleanup样本执行636条指令，URL和handle所有权账本归零；这是零初始化handle+native setopt的有限布局样本，不宣称真实连接对象完整析构测试。

队列中确有URL的独立native副本，但它只在任务等待/执行期间存活。为说明来源，以下为离线恢复链，**不是本轮live节点遍历合同**：令T=pool`+70`指针、N=pool`+78`、i=pool`+80`；第一个queued block为`[T+8*(i&(N-1))]`。本POST block首vtable`6BF258`，`block+38`为inline std::function self。P=`[block+8]`是packaged_task member地址；Ref=`[block+10]`=`P-10`，Ref size`28`/vtable`6A8450`；S=`[P]`是size`110`/vtable`6A8418`状态；L=`[S+108]`是size`180`/vtable`6BF1B0`请求lambda。其URL std::string在`L+10`，长度QWORD在`+20`，容量QWORD在`+28`；cap<16时inline，否则取`[L+10]`指针。

原生`13CA70`先把wrapper移到worker栈、清queue wrapper`+38`、减count并增offset，再调用任务；完成后析构任务及URL。夹具改变调用方buffer后副本不变，6个样本都证明queued count1、transport boundary时count0、返回后task释放。异步RPM多次读取可能遇到pop/free或地址重用；没有通过锁/无锁协议取得安全snapshot的证据，故不扩成猜测复杂的URL walker，不从UI结束时寻找已释放easy。当前稳定最小输入是初始化根/类型及描述字段，仍无法提供latched host/route。

## 动态解析的已闭部分与未知表

父卡18个未归类GetProcAddress点中的9个对象驱动点`1F976C..1F980B`由ctor`1F95C0`闭来源。准确类为MiHoYoSDKCrashManager：object`+10`指向RVA`6B5510` UTF16字面量**`metakeeper.dll`**，`+18`保存`LoadLibraryExW(...,0,8)`模块句柄，`+20..+60`九个名字为`MKPInit/MKPEnableLog/MKPEnableReport/MKPCrash/MKPDeleteUnsent/MKPSetUserId/MKPSetDeviceId/MKPSetRegion/MKPSetCustomData`，解析结果到`+68..+A8`。这证明一个静态crash组件路径，不能当自然加载/调用；不要误写成Mmoron。

另外9处`3B36A9,43C449,449AFE,4836D0,488CBB,4AA3B6,5863CA,5867D3,5868A8`仍保留unknown。父卡74处GetProcAddress只是其RIP绑定引用集合，不是所有间接解析调用的完备全集。SDK Invoke中本卡只确认实际注册24项和两个login目标；未执行动作目标后的全部业务分支，未知间接边不删减或用类名补齐。

Engine ctor还在engine`+F8`建立size`60`的fallback描述，字面量为`HoYoSDKNetworkFallback.dll`及`NetworkFallback_HttpGet/Post/Head/ReleaseResponse/ReleaseNetworkHandle`。它是声明的可选transport候选；加载、分支选择和自然调用均未知。历史11模块未含它，不能据snapshot缺席宣布永不加载或SDK只走curl。

## 原生夹具、模型与错误边界

仅在Unicorn执行固定SDK原始指令，映射PE、合成stack/object/heap/GS/TLS，指针均覆盖32位以上。真实进程、UI、OS线程及网络未执行。模型逐事件列在raw：

- 存储：C++/CRT allocation入口`4823A8/13A1B0/4978C0`及free`4823A0/48F4F0`提供有所有权账本的合成heap；没有模型替换原生std::string复制、树插入或队列操作。
- 单线程OS边界：mutex/condition的init/lock/unlock/signal/destroy入口`45E8F0,45E920,45E944,45EE7C,45EEC8,45EEF0,45EE54,45EE6C,45E8DC`；静态初始化epoch`482920/4828C0`使用合成TLS。合成log global`764CA0=3`避免未初始化sink。均仅存在于夹具内存。
- 动作目标`3CBE30/3CC220`及模式目标wrapper在记录原生转发参数后结束；**未执行完整login worker**。队列样本在transport`2F0940`边界记录参数/队列count并返回模型0；这个0不是HTTP成功、真实perform返回或客户端验证。
- 合成pool`+12C=1`表示已有idle worker，避免创建OS线程；实际执行入队和consumer`13CA70`。transport cut时合成`+128=0`使一次任务后停止；不把这个停止写入真实进程。
- stopped pool执行到真实C++throw入口`485540`即停止，未执行异常对象runtime/OS unwind。early exploration因缺condition broadcast/destroy模型停止后，补明确定义的OS边界模型，不跳过队列或释放代码。

最终覆盖：1组cold/warm SDK singleton（9次合成分配）；24项原生动作注册；5组Invoke（两个正例和unknown/empty/state阻止）；6组mode选择（包括`100000001`）；6组queue/consume（空串、标准、15字节inline、16字节heap、1024字节、UTF8）；1组stopped queue错误（原pending count1保持）；1组limited easy cleanup。13项测试核固定输入、RTTI、16条vtable边、64位callback/32位index、错误分支、SSO及所有权、pop-before-call和只读字段宽度。它们不是自然账号请求计数。

## 自然首缺输入及下一最小实验

第一步可由主线把已给根合同并入既定冷初始化只读批次，核当次SDK加载身份、入口字节与根/类型/queue描述；这是观察可行性输入，不会自行闭合natural transport。若只读结果仍不能提供请求事件，唯一首缺仍是**已核内存身份的`base+41E6F0`自然entry：caller相对RVA、EDX10002、预审固定host/route内存分类**。不要求盲目重复启动，不把root存在、counter0或任何无事件结果当作HTTP路径已验。

最小entry合同继承父专题：完整保持Win64 RCX/RDX/R8/R9及stack varargs、原调用次数/顺序、EAX返回、调用前后LastError；仅10002在调用有效期分类R8的NUL字节串，不改URL、返回或原生验证。持久化只允许固定白名单host/route代号，未知可留长度和unknown；禁止真实原文、query/header/body/账号/token及其hash。装载方式、校验影响、覆盖/回跳、unwind/线程退出须由主线另审具体实现；本卡未写/装DLL。setopt真实语法未验证、unknown option原生返回48等父卡事实保持，不让模型抢先成功。

更宽的自然login动作、服务选择、回调、perform返回、服务端响应和游戏dispatch是后继证据，不能用本卡链代验。配置key`loginUrl`自然取值、可选fallback分支与9个动态解析unknown目前明确保留；主线可在独立离线卡继续具体reader/producer分析，不修改本卡冻结source。

## D-02本服SDK接入的最小剩余合同

本节仅据当前文件补足主线接入清单，不扩大本卡工具或取得凭据。重读了`AuthenticationHandler.java`、`MaPassportAuthenticator.java`、`RegionHandler.java`、`Crypto.java`、`build.gradle`及patch `security.rs/native_sdk.rs/http.rs/lib.rs`；文件路径/hash另记本卡manifest。源码注释所谓“国服实际路径”没有本轮固定7.2自然证据，不能直接当route白名单批准。

| 合同 | 现有文件已经证明/可继续离线闭合 | 必须由自然调用或实际部署补的最小证据 |
| --- | --- | --- |
| route与请求/响应形状 | 本卡已闭AccountPlat模式1读取`loginUrl`并到native POST。服务端注册`/account/ma-cn-passport/app/loginByPassword`、`/account/ma-cn-session/app/verify`、`/account/ma-cn-session/app/exchange`、CN mdk login/verify及combo等；这仅是候选接收端。可离线继续追配置key的producer、固定JSON字段/类型/错误响应及token交接消费者，不必先采真实正文 | 实际选中的service/mode、固定host/path/method类别及先后；自然SDK返回成功后callback/后续combo触发。入口setopt事件只证明URL选项，perform发送/回调仍要独立证据。只按已证方法/route映射本服，不能将所有域名/URL重定向 |
| 签名/加密 | [HTTP路由](CLIENT_72_HTTP_ROUTE_CONTRACT.md)已执行真实server HTTP和目标原生解密/正文验签，正确签名的版本拒绝能解析，坏sign原生拒绝；[信任合同](CLIENT_72_TRUST_CONTRACT.md)将key签名与正文sign分开。可用固定独立非零/64位/坏签名样本继续核SDK请求及响应消费者 | 当次dispatch请求选用的版本/key_id/seed分支，以及真实返回进入原生key门、正文门、reader；自然HTTP200或登录UI错误不替代任一门。不得采请求query值来代替合法参数类型/状态观察 |
| SDK登录key | 现有server MaPassportAuthenticator把account/password分别交RSADecryptionUtil；旧patch `security.rs`在EXE托管函数假定RCX XML key/RDX plaintext，按128B modulus及117B上限选择替换。当前本卡走**AccountPlatNative CryptoManager**，尚未证明会进入该EXE hook。可离线追准确CryptoManager槽位、key producer、加密模式/编码/大小，并只在内存比较本地公钥与server解密者公钥数值；不需要真实密码 | 实际自然加密入口/所选key身份及返回生命周期，和本服可正确解密合成测试账号字段的结果；只输出固定身份/成功状态，不输出plaintext、ciphertext或其hash。SDK登录key不能以dispatch key4/SigningKey验过来代替 |
| 目标version | 本次重读`build.gradle`仍为7.1.0；RegionHandler按major/minor检查GameConstants，既有正式HTTP实测CNRELWin7.2.0返回retcode11。可在R1/R3/R4必经合同齐后做真实7.2生产实现、对应回归及新JAR身份核查 | 正式目标version的JAR实际加载，7.2自然请求通过准入并到完整初始化；只改字符串/guard、发送7.1伪请求或继续使用旧归档不是解决方案 |
| trust材料/继承 | [F-04b/c](CLIENT_72_TRUST_PATCH.md)已闭两调用点只供public/root/signature，保留原生验签；bundle只含公钥和预签材料，不含私钥。[launcher](CLIENT_72_TRUST_LAUNCHER.md)已有新子进程环境路径继承测试。可继续离线复核固定输入、bundle、精确site、错误材料/拒绝及部署产物 | 当次目标实际继承`LUNAGC_TRUST_BUNDLE`并加载受审窄模块，两site自然命中且原生接受/拒绝保持；旧进程环境、测试bridge或磁盘材料存在均不能替代 |
| 窄patch及ABI | 当前`native_sdk.rs`只对四类price路径改R8，其测试明确保留session verify原URL；旧注释41AE90不得沿用。可据本卡41E6F0固定身份做独立观察/后续精确route转换夹具，核GPR/XMM/flags/stack/LastError、原返回、原调用次数、复制所有权及拒绝 | 仍缺合法装载路径、实际module/入口匹配、自然原生校验与生命周期验证；旧整DLL含ccp/memguard/NEUTRALIZE/swap/异常处理等未验，不得整装。磁盘五文件未改也不证明内存hook无校验影响 |

首次本服账号试验前另有一个**已见源码的具体记录问题**：AuthenticationHandler的MaPassport login/verify/mobile captcha分支会debug记录`rawBodyData`，其中可包含账号/密码密文/token。主线须按本轮不持久化正文、凭据及其hash的合同移除或收窄这些现有记录，再执行账号试验；本卡不改共享生产文件。SDK观测器只能接固定标签/状态，不能因“日志在local”而放宽此限制。

这些合同完成后仍需主线独立测试数据、便携部署、端口/PID/运行JAR与DLL身份及本服新旧号全链验收；仅SDK显示已登录也不替代选人命名、入世界、心跳、退出重登及保存。当前cold-arrays批次只取已交稳定根，尚不能关闭上述natural项目。

## 冻结、双跑与独立复现

最终source `tools/client72_sdk_login_chain.py` SHA-256 **`53da43c970a179699d80a1eab570bff47b9b619d0424c83597766b96d670cad2`**；test **`424a8cc7204147e11933a869f582461572154e960cb27a9868fe5b3b0871853d`**。两次新Python进程`final-v2-first.json`和`final-v2-repeat.json`各672404字节，逐字节相同，SHA-256均 **`3d864c07b944ed63af4f69e07f5b4e9abafb8f2e30de47b716bd0f06f6c7fd96`**。日志`final-v2-first.log/final-v2-repeat.log/tests-final-v2.log`，13 tests通过。早期research和首轮final草稿保留，只认v2最终结果；`hashes-and-determinism.json`另记文件字节比较、输入/source/文档/hash清单。

父卡source/test/raw分别仍为`43b334be3040fa4d266b3462985313ab6fd99e29a148ec7dc682426a41881237`、`e90bb87414ee3b5c7159938ec3d38960b78eefdfe2b58123346df2c5e793b3db`、`3650c789f07778ee54a705d517e4ba88914231b0e426bfc53d9202af8edba9fb`。依赖extract_client72/signatures及Capstone5.0.9/Unicorn2.1.4 DLL哈希都在`fixedInputs`固定并每次重核，不仅记录版本名。

第三条独立命令如下；工具拒绝已存在输出和local目录之外的路径，不覆盖证据。需既有本机原始输入/REA Evidence，不需重新打开Ghidra。输出预期与上述双跑完全相同。

```powershell
$sdkRoot = & tools/game_path.ps1
python tools/client72_sdk_login_chain.py --client $sdkRoot `
  --output local/migration72/d02-active/sdk-login-chain-astra/third.json
$env:SDK72_LOGIN_CHAIN_REPORT = 'local/migration72/d02-active/sdk-login-chain-astra/third.json'
python -m unittest tools.test_client72_sdk_login_chain -v
Remove-Item Env:SDK72_LOGIN_CHAIN_REPORT
Get-FileHash -Algorithm SHA256 tools/client72_sdk_login_chain.py, tools/test_client72_sdk_login_chain.py, `
  local/migration72/d02-active/sdk-login-chain-astra/third.json
```

仅新增本卡工具/test/专题及忽略目录证据，没有生产、Proto、配置、资源或patch改动，无需构建部署。子代理未提交Git，Project/Harness及融合提交由主线处理。使用REA技能完成的全是固定文件静态分析；没有发送外部消息或执行live操作。

主线已审真实代码、cut/OS模型和原始证明，第三新进程 `main-third.json` 为同一672,404字节/SHA `3d864c07b944ed63af4f69e07f5b4e9abafb8f2e30de47b716bd0f06f6c7fd96`，与v2双跑逐字节一致；13项定向检查通过，日志 `main/sdk-login-chain-main-tests.log`。这一验收允许后继复用条件调用链和稳定根，完整自然请求、Crypto/key、route转换、信任装载及D-02不晋级。
