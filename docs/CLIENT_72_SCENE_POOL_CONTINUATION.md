# F-03d3s2-pool：fresh Pool绑定的原生后继

## 有界计划（2026-11-08）

目标是把第二轮已取得的实际Pool class/slot4/开关/type46314头接到准确原生Allocate路径，明确下一处必需运行输入或原生分配结果。该卡由主线执行，不与代理共享源文件；不重复已完成的Gadget索引/三行parser、能力hash、Array/Type或既有Scene模型。

固定输入为本轮五文件、第二轮snapshot SHA `119f8a0b3c3ecc8829eda9bf7422c3646c643afa34ac041ea69b5607a02a7384` 与monitor SHA `5ca5b6e5af9bba74889495426e983d5b8d55048f82091fb10dcd3ed08d732fb2`。已知Pool class11008/kind18、slot4绑定模块+70687c0、原32B代码SHA `98a93ee052b474915ef1212a25986b49326f99a9f91f23530bb52b0196432861`，独立pool mode1、type46314头id46314/kind18。实际对象/头是非原子读取，不证明调用时序、分配、构造、注册或生命周期。

范围仅新增 `tools/client72_scene_pool_continuation.py`、对应测试和本专题，原始证据放 `local/migration72/d02-resume-20261108/scene-pool/`。不改旧工具/断言/raw、生产Java/Proto、patch、资源、配置或ready/required；不读活进程、调用客户端、启动服务/DB、安装或部署。

先从固定PE读取精确Allocate及两转发目标的函数边界/字节/指令，固定type46314 metadata来源；再按明确地址重定位模型导入本轮已采头。只提供有来源字节，class指针字段不当作可直接重用的内存；所有未提供runtime读必须守卫，不能读到已映射页的零默认。入口执行原`1470687c0`，RDX为已绑定type46314模型地址；R8=0按既有工厂实际caller，Pool对象身份仅在入口是否消费的范围内建模。

已知底层allocator/OS依赖仅在准确caller、输入尺寸、owner与原生写入均有来源时才可补有界存储模型；不以返回对象成功桩代替IL2CPP分配器。若实际分支需要未采状态、未闭间接边或新类型/metadata，保存原失败，先查固定文件/既有样本；真实缺输入交准确reader/链/跨度/预算，不自行扩大采样。

验收要求固定源和五输入、双新进程确定性、准确原生分支与模型边界，独立非零/64位/错误身份/未提供读守卫；若分配完成须查对象klass/大小/脏存储写入与所有权。未完成时交可复现的第一缺口，不称完整factory/Scene handler或世界入场通过。无运行代码变更，不需要服务重启；后续生产变更再按AGENTS构建/部署。

## 当前限定结果

准确原`1470687c0`前缀已执行。fresh mode1选择真实`14050a890`；首个未提供输入是其`14050a8ae`从模块RVA`5eef080`读4B loader TLS索引。工具在该指令执行前停止，不将映射PE的零当作实际索引。PE TLS目录RVA`3a8dab0`、40B，其AddressOfIndex恰为首选VA`145eef080`，不是猜测字段；随后原指令才将读取该调用线程的GS:[58]及对应TLS slot。当前SDK登录页main thread没有被证明是该allocator caller，不能随意取一个线程补零。

Microsoft规定AddressOfIndex指向由loader写入的TLS索引位置，线程有各自TLS区域；本卡只据此解释该输入的OS来源，x64的实际GS偏移仍以本PE指令为准，不套文档x86示意偏移。[PE format / TLS directory](https://learn.microsoft.com/en-us/windows/win32/debug/pe-format#the-tls-directory)。本卡没有给TLS分支、profiling返回或底层IL2CPP allocator建成功模型。

另一独立mode0控制只验真实前缀转向`14050ad90`，在该入口执行前停止，明确不是自然模式。两例RCX都由原`mov rcx,rdx`取得完整高于32位的type46314模型指针；该指针之后原样转入R12。fresh类头复制到模型存储，但到首缺前没有消费其字段；Pool this为不解引用的opaque owned storage。唯一已提供的runtime global读是各例1B pool mode，模型调用数0；不声称类初始化、分配返回、对象所有权或生命周期通过。

三精确来源窗分别为上文32B leaf、`14050a890..14050ad50` 1216B SHA `7b3f2a829a31d0f3aaeaa16dc46628a996c41d8431779f9d06bddc108234c73b`、`14050ad90..14050ae3e` 174B SHA `4ebcea02f7a4568b35fc295eb99d1cb93f1d01d1cef2a74124b0998d76db07d2`。初次边界读取错误地要求leaf必须出现在PE exception目录，KeyError保留于`initial-boundary-failure.private.json`及原脚本副本；随后该leaf采用已验精确32B窗，两个大函数仍按真实PE边界。不是放开未知执行范围。

## 复现、检查和下一输入

```powershell
$poolClient = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/game_path.ps1
python -m tools.client72_scene_pool_continuation --client $poolClient --output local/migration72/d02-resume-20261108/scene-pool/first.json
python -m tools.client72_scene_pool_continuation --client $poolClient --output local/migration72/d02-resume-20261108/scene-pool/repeat.json
python -m unittest tools.test_client72_scene_pool_continuation -v
```

两新进程报告各4,174B，逐字节相同，SHA `3a650294fbd979a775f98e01db480a85bc6784edff157113c98aa0e10ebcc811`；6项独立检查通过，覆盖两个准确分支、五输入/固定来源、未消费TLS指令、无allocator模型、错id/kind/短读/错误/mode拒绝及未晋级结论。证明`determinism.private.json`和`tests-final.log`。只读离线工具，无服务/JAR/DLL部署或客户端验收增量。

下一最小固定输入仅模块+5eef080的4B，适合与后继已规划的自然采样合并；它不自动补齐随后分配线程的TLS/引用状态。当前透明加载实验固定4窗781B，不临时扩大该轮预算。先保留准确分支和读缺口，同时推进注册/MPLevel/实体组件的独立固定来源及R3必需字段；不因这一输入停止整个D-02。
