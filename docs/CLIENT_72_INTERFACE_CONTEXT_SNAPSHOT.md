# 国服 7.2 接口定义上下文最小采样

| 项 | 有界合同 |
| --- | --- |
| ID | R1-SAMPLE-INTERFACES |
| 目标 | 新进程独立取得定义1169/1249的准确cache/header/metadata及条件descriptor，并取Pool首缺的loader TLS索引DWORD；不执行构造或allocator。 |
| 现证 | R1 fresh-ancestor第三报告SHA `dc738c4f3f3d80cdb72bb852d2250de685657fa489818f436ce8e8cc9933256b`；两类固定metadata parent=null/interfaceCount0。原生首缺reader `15a4d6bf1`；Pool独立首缺reader `14050a8ae`。 |
| 未知 | 自然cache槽是否非零、两头身份与metadata、实际compact字段及descriptor、loader TLS索引值均未知。索引不证明分配线程或GS/TLS数组。 |
| 依赖 | 固定五profile文件、原90秒monitor源码 `e0b4134e…3c6e`、旧sampler源码 `ecab38c9…9c7c`、fresh PID/creation/image/模块、当前PE准确来源窗。私有next合同须匹配指定SHA。 |
| 范围 | 只新增本专题、独立sampler与mock测试；私有产物限 `local/migration72/d02-resume-20261108/r1-interface-sampling/`。不改旧sampler，不读Array/Type/371/1234/Pool slot/Nullable cell，不读线程或对象数组。 |
| 产物 | 可审核CLI、pure collector、逐读地址/请求/实际/error记录、静态来源核验、合成测试与源hash；当前阶段不live、不launch、不加载DLL、不调用目标函数或网络。 |
| 验收 | 高64位/非零/零parent/nullable槽/短读/读错/身份及PE漂移/精确预算回归；主线审核后用新user-ready的90秒monitor执行，原始结果保持unknown≠null。 |
| 停止 | 五输入、源码、合同、PE窗、monitor或进程身份不符时，在第一次RPM前拒绝；每个失败/空指针只停止其依赖链，独立TLS读取仍执行；最多14读664B、单读208B，无重试或扩展。 |

## 精确读取

同次主模块基址记为B。`B+5bc8f18`读8B cache根；分别读cache+2488/2708的8B槽。非零槽各读208B头，严格核`+10`的QWORD等于1169/1249、`+18` DWORD的kind为18。只在准确身份后，从`+58`非零指针读70B，与当前固定metadata file `a21026` / `a22606`逐字节比较；SHA分别`a120af6afc25d7d683dbdcc306919a244fe9877f05698d0839836122f7d2a4ac`、`ba00239c8db9e1caa6ae10e9a6a7921f7ec31242f8aeb07a1c89ca286e4846e4`。

只有头和metadata通过、且`+b0` parent / `+a8` declaring compact U32非零，才按同次`B+5bc8ad0 -> slot -> registryBase`的两次8B读建立共享根，各读`registryBase+compact+10`的16B。零compact明确跳过；非零descriptor只留原始数据，语义身份不猜、不递归。静态parent=null不替代自然compact值。

独立最后读`B+5eef080`的4B。当前PE TLS目录RVA `3a8dab0`、40B，其AddressOfIndex为首选VA `145eef080`；准确消费者`14050a8ae`。该DWORD仅为loader索引观测，不延伸到GS:[58]、线程或TLS数组。[Pool来源](CLIENT_72_SCENE_POOL_CONTINUATION.md)与[接口来源](CLIENT_72_AVATAR_FRESH_ANCESTOR.md)分别保留范围。

预算：cache根1读8B；两类各最多5读318B（槽8+头208+metadata70+两个descriptor32）；registry最多2读16B；TLS1读4B，总14读664B。两类compact皆零时8读584B。多个RPM不是原子快照，也不证明自然消费时序、cold comparer完成、类初始化完成或Handler完成。

## 当前状态

实现与离线检查完成，三个新文件冻结，待主线独立复验；未执行live、OpenProcess、RPM、launch、构建、部署或Git。源码为 `tools/sample_client72_interface_context.py`，测试为 `tools/test_sample_client72_interface_context.py`。复用原monitor的只读backend声明与五输入gate，不调用其launch/observe；不导入旧sampler或带emulator依赖的R1/Pool工具，只核其冻结源hash。

入口复核曾发现原私有合同为6614B/SHA `c84ae1e0965e855ffb09cab0ace252fd96182f2fe9749ae8ca952c169da000a4`，与原声明`bac006269d333ebe5ca88d25f6ac84c3b25f1bbadea72e95d571655366ffea33`不同。主线独立定位为149个CRLF，原文件及副本保留；从冻结第三报告`nextSamplingContract`新建精确LF投影 `r1-fresh-ancestor/main-sampling-contract.lf.private.json`，实际6465B/SHA恰为`bac006…ea33`。采样器只接受此新文件，另核来源报告SHA和完整投影内容，不把写前hash当作原文件已验、不放宽gate。审计见同目录`sampling-contract-newline-audit.private.json`。

五个当前固定文件、冻结源码、四个PE代码窗、两个16B静态descriptor、两个70B固定metadata及TLS目录/AddressOfIndex已实际离线核对；私有证明为本卡raw目录`offline-source-proof.json`，SHA `98c6d4eec1d998017c16e913f095cae24d5036e5db04f5d74cc7a2fa61cdef31`。这是磁盘来源核验，不声称任何运行内存已核。

| 文件/代码来源 | SHA-256 |
| --- | --- |
| sampler | `02ffaeab9c5e2f96bd135f7007b1f964c2863fb16471611c03867b3aefdda251` |
| test | `83e646fd077bede7039a9c20c9f2378ec5c1515f880d38578471ae6605cb0439` |
| cache reader `15a4d6b60..15a4d6c21`，193B | `5245ce48b3f06b60e060ab85ba8d28e0dc98a43bf112b515c74a14ea1cb610a0` |
| construction `140517980..140517c88`，776B | `ddc12c9ec67a6803dce0cef1f7bd991cf594ea0dd40442bda42292f67be53f4e` |
| interface producer `14051f370..14051f554`，484B | `9eaa3b22e3e226c9ef985250c29d3514d605a09e58e0b6e21bc8af4acfd0f363` |
| TLS consumer function `14050a890..14050ad50`，1216B | `7b3f2a829a31d0f3aaeaa16dc46628a996c41d8431779f9d06bddc108234c73b` |

## 实际测试及主线执行

```powershell
python -m unittest tools.test_sample_client72_interface_context -v
```

23项通过，全部使用自有稀疏内存、合成PE或模拟backend，没有真实目标进程。包括高64位指针与精确地址/宽度、双零parent/declaring的8读584B、四非零compact共14读664B、registry仅一次2读、null与unknown区别、失败/短读/metadata错行/精确kind与id拒绝、未知descriptor不递归、独立TLS的零值及错误、单读/总字节/总次数拒绝、源码/合同/投影/PE/TLS漂移、同一文件的Windows扩展前缀路径、PID creation/image/module base/size/path漂移的首次RPM前拒绝、进程末尾退出保留partial和句柄关闭。纯fixture不等于实机验收。

主线重新运行上述测试、审源码与私有proof后，由**新的user-ready**安排原冻结90秒monitor，不能复用上一轮ready。原monitor仍在其已审自有raw目录输出；本sampler仅把该新报告作为只读输入。主线取得新报告后执行：

```powershell
python -m tools.sample_client72_interface_context --observation $freshMonitor --output local/migration72/d02-resume-20261108/r1-interface-sampling/live-first.private.json
```

`$freshMonitor`必须是该次新90秒自然启动报告的准确路径。CLI只接受新输出，不覆盖已有文件，并保存同目录`.source.py`副本；stdout只打印status和`targetProtocolReady=false`。使用`OpenProcess(0x101010)`含SYNCHRONIZE；创建时间、当前image与主模块文件身份、base/size必须与新monitor相同。路径比较采用文件身份，允许`\\?\`与普通路径表示同一文件，绝不仅按字符串去前缀后放行。

原始结构字节/错误仅在专属忽略目录；共享摘要只记录成功/空/未知/失败与预算，不把null cache当头地址、不把metadata相符当自然构造完成。若两类metadata均匹配且所有实际非零descriptor跨度及TLS读成功，`status=completed`仅表示本卡固定采样范围完成；descriptor语义仍为unknown，所有上层ready/comparer/类初始化/Handler/D-02布尔均false。其余情况保留partial，后继按准确实际首缺另立合同。

主线独立23项检查通过，重新从当前stock五文件核4代码窗、2 descriptor、2 metadata和TLS目录，source/hash及预算均符；证明 `main-preparation-proof.private.json`、日志 `tests-main-review.log`，执行源原字节副本在 `executed-source/`。私有准备helper初次因脚本目录sys.path找不到tools，在接触输入前失败；只修该忽略helper导入路径，sampler源不变。新wrapper只执行冻结90秒monitor与本sampler，启动前重核源、无既有客户端/调试器及新输出；新user-ready仍待主线取得，未沿用上轮。

## 本轮实际结果与主线验收

上述准备之后，已取得新的用户在场确认；主线重新核五stock文件、配置、脚本身份及进程/端口，再执行原始90秒monitor（44项模块观察）和一次采样。未安装DLL、未输入凭据。实际 **8读584B，零错误/短读**：1169/1249均有准确kind18头，70B metadata各与固定文件匹配；两个compact字段均实读0，因此未读registry或descriptor。该零值没有被推成新的父类/declaring语义。

模块`+5eef080`的DWORD实读 **0**。这是实际loader TLS索引值，未读取或证明分配线程、`GS:[58]`、TLS数组或allocator内容。Pool后继仍须准确线程上下文；不能任取登录线程、复用旧绝对指针或把空TLS模型当自然输入。

原始采样 `live-first.private.json` 的SHA-256为 `3c08c1f72e181084bc3d22e7199cf08ca306083322d0638cd6322ef42064d040`；绑定本次monitor SHA `8136125522ddc6983a0c22e37bffe5556da58fbb981416493e5b1a6442d7e6fc`。主线标准库独立审核29项，逐条复核实际地址推导、原始bytes/宽度、typed头、metadata、预算、当前五stock文件及配置；证明 `main-live-review.private.json`，退出核对 `exit-main.private.json`，均在本卡忽略目录。用户确认SDK登录页正常并正常退出，随后OS核游戏进程不存在。

本卡固定取证范围已完成；保留非原子、消费前时序未证、完整comparer/类初始化/Handler未完成及全部ready=false。后继复用本轮有身份的两个头，跨轮对象只能显式重绑定为受控输入；不得把多个自然快照拼成同一次自然内存图。本项不代表本服SDK登录、完整资源或D-02验收。
