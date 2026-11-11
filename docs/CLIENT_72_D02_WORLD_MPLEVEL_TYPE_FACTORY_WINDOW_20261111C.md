# F-03d3s2 / World MPLevel 条件 factory 单窗：线性解码停止（2026-11-11C）

任务 `F-03d3s2/WORLD-MPLEVEL-CONDITIONAL-TYPE-FACTORY-WINDOW`，父项 D02-R1/R3。**首份采集在完整392B原body读取后，线性解码门禁报 `ValueError: incomplete decode`，已停止并保留原body和失败账。** 没有生成完整asm/extraction、第二fresh或纯artifact audit；这是普通来源解码缺口，不是外部或安全阻塞。不能将完整pdata原字节称为完整逻辑函数，也不能从失败猜测实际class/slot或自然MPLevel对象。

## 来源与实际读取

固定request `local/migration72/d02-resume-20261111c/world-mplevel-parent-consumer/conditional-factory-one-window-request.private.json` 为8942B / SHA `39f9b19b5fe5524d490389ee9e8f63665328cd8ba069aea98f7cb603bb27cc94`；[来源专题](CLIENT_72_D02_WORLD_MPLEVEL_PARENT_CONSUMER_20261111C.md) SHA `52eb50a301cfbbaa4e40f207ae46b04cc83aab3fe6f7a59fb959eaca746c9cda`。首份通过固定来源/profile/实际五stock pin、metadata禁止globals、唯一owner62017/m514570/static/flags93/slotFFFF及parameter410581/type124非byref uint32核验。selector1仍仅是旧hostkey游标模型下的条件值，不是自然reader帧或已执行factory。

冻结caller `[14b135cc0,14b135d67)` 167B / `9e635fb07bdbe93ddb1bb4b33c573d5d6eaa58d238dfbb2c2df9dc1f11379575` 与旧manifest的49条原hex及独立decode一致；当前PE仅另核20B `0fb6440820ffc1894e1c04350fb6c8e894277703`，原E8 signed rel32指向 `14e8a84a0`。两QWORD AOT cell/slot共16B实际相符。

固定exception目录14344080B / SHA `5895dbeef8fe13458720889e318ba64f3fc9c5d3ca03bf01b0adc70eee16c08d`；unique exact/containing同一真实记录，范围 `[14e8a84a0,14e8a8628)` 392B，可执行/file-backed且在8192B预算内。读取前已保存真实12B pdata、全部匹配和pre-body；PE header读取账 `headerBytes=328`、`sectionTableBytes=520`，合计848B。此后完整读取一次392B body并保存，真实失败报告为 `stage=single-body`、`newBodies=1`、`bodyBytesRead=392`。不把该失败说成body0；未增加邻窗/前缀或改解码方法。

## 保留证据与范围

私有根 `local/migration72/d02-resume-20261111c/world-mplevel-type-factory-window/`：

| 首份实际文件 | 字节 / SHA-256 |
| --- | --- |
| `fresh1/body.bin` | 392 / `780ba856fa671de842d525ab9dc0684d7f5a0267332cf532bbb902af3f83c9b8` |
| `fresh1/failure.private.json` | 7328 / `f7297f43301d34927f506559292dbf0122b10787d454924c1da005dba3d7f1c0` |
| `fresh1/boundary.private.json`、`pre-body.private.json` | 各7252 / `361f538d58b9e72c46b8ddb274bedfbff1e117282aaf816208d318caf5c3fcde` |
| `fresh1/caller-context.bin` | 20 / `b19908e13ea8a277521fd81f7a180f31cbcd6773ae6a3dd92281220ba0ec2e02` |
| `fresh1/aot-cell.bin` | 8 / `68f5f1908ac9f789c3fc0b5e71c5b8af00962605561f7b8ea2cc4718f00a83da` |
| `fresh1/aot-slot.bin` | 8 / `944f1c186b3fb5f70d2eea6269142b322f486766876750e37a3bfca8976d0818` |
| `fresh1/pdata.bin` | 12 / `99b20bde7601747645e24e9fdf53227854f72716795bc4d44f30bbcc274f3838` |

原控制台trace另存 `first-failure-trace.txt`。保存body没有执行该factory，也没有读取callee/global/callback/xdata或使用目标CPU、模型、live、实际对象或返回；不能依据元数据声明推断factory返回。原1343B parser的A allocator边及map分配/返回/初始化/out-map继续冻结，B loader亦保持拒绝停止。没有新安全审查拒绝。

## 测试及后续

工具 `tools/migration72/scene_world_mplevel_type_factory_window.py` SHA `29f09276e0d59e693244485440832f1758d89989d665a74fc08e2407869f06a5`；测试 `tools/migration72/test_scene_world_mplevel_type_factory_window.py` SHA `98da3cb3826ef56de724f8aefe86b31541ed11c0ad169dc78116c92524d32e43`。`python -B -m unittest -v tools.migration72.test_scene_world_mplevel_type_factory_window` 6组通过，覆盖固定pin/profile、声明/uint32、caller/E8及正负rel32、AOT、pdata/节/预算/短读、真实body读取后解码失败的字节账、纯audit篡改和无PE。测试日志中2B body仅为人工fixture。首份命令：

```powershell
python -B -m tools.migration72.scene_world_mplevel_type_factory_window --output fresh1
```

应由主线审核已保存raw与线性解码失败，必要时另行明确允许的有界审阅规格；不得自行重读新窗、扩大范围、补邻接字节、运行factory或把数据误判为代码。双fresh/纯audit/集成第三和实际class/slot语义均未完成。本批未操作Git/index、构建、Java、DB、端口、客户端、调试器或部署；Project/Harness/HANDOFF及本地提交由主线处理。

## 布局修正及 fresh2 入口停止（后续工作）

任务 `F-03d3s2/WORLD-TYPE-FACTORY-CODE-DATA-LAYOUT-FIX` 的已审规格为 9355B / SHA `802e711249e8cf5d75e30f20c7c582f4b3df1532c185c4a1b1944edfbe494e18`。首版工具、测试、本文基线和首份八件/trace 已保存在 `layout-v1-freeze/`；manifest SHA `14861c707002699a9326009b09e2c043aa6fc440ad97949ea2ffb0ea4bc22c50`。上文工具和测试 SHA 指首版冻结来源，不是当前版本。`fresh1` 原失败保持原样，不能追认为成功。

当前修正工具 SHA `3a3a09012e52217d5c5ac925d801836e9e9bd7ae19d0e3e0e71ec244e5cf8f61`，测试 SHA `1ef8904b4aa858dd6d159a4e2acc302ad5d0ffd93c020e835bddb8411bdbc953`。固定旧 body 的纯离线检查从入口 guard、索引、LEA、MOVSXD、ADD 和 JMP 推导表，得到 358B/81 条代码、2B `6690` NOP、32B/8 项 signed LE32 表；八个目标均为代码指令边界，覆盖 392B、重叠及遗漏均为零。代码后的语法 CALL 续边不证明调用返回；case 1 请求类 25270，`14e8a8595 -> 1405336c0` 的分配返回及构造、实际 klass、slot 和 MPLevel 对象依赖仍冻结。`logicalFunctionComplete=false`，条件 selector 不代表真实 reader 帧。

`python -B -m unittest tools.migration72.test_scene_world_mplevel_type_factory_window -v` 实际 8 组通过，包括旧来源门、精确 pdata/节/预算、short read、真实读取后失败字节账、原 body 布局及独立规格 oracle、调度指令/目标/NOP/表/覆盖/语义标志负例和纯 audit mock（不读 PE）。这是测试而非正式双 fresh 审计。

正式 fresh2 的本次启动命令及原结果：

```powershell
python -B tools/migration72/scene_world_mplevel_type_factory_window.py --output fresh2
# ModuleNotFoundError: No module named 'tools'
```

退出码 1，停在模块导入、进入 collector 之前；未创建 fresh2、未访问当前 PE、未读取 body。原 traceback 为 `fresh2-start-failure-trace.txt`（269B / SHA `10048addbfeeca3b4ad933fc6887595b3b7c3f64e1c4a3b602a0dee4d2397e5d`），命令与停止记录 `fresh2-start-failure.private.json`（453B / SHA `bbcd211d0045e4b33e25d802565b6066f80c5b2c9d94e7bc255e8ccf36dc3c71`）。依本包一项失败即停，未更换命令重试、未采 fresh3，故没有正式双 fresh 十件或纯 artifact audit，主线第三亦未执行。这是普通启动问题，不是安全审查拒绝；后续动作由主线依据原证据决定。

## 模块入口放行后的正式双 fresh（最新状态）

主线复核入口错误并明确授权使用仓库根模块入口；事前核对修正工具、测试、269B 原 trace 与失败记录 SHA 均不变，fresh2/fresh3 均不存在。两次采集均通过原来源、五 stock、caller 20B、AOT 16B、真实 pdata 12B、节与 392B 完整 body 门禁。每次 body SHA `780ba856fa671de842d525ab9dc0684d7f5a0267332cf532bbb902af3f83c9b8`，生成 81 条代码指令、39 条引用及 8 目标计算跳转记录。两次各十件逐字节相同；上节有关双 fresh 尚未完成的说法记录的是当时状态。

```powershell
python -B -m tools.migration72.scene_world_mplevel_type_factory_window --output fresh2
python -B -m tools.migration72.scene_world_mplevel_type_factory_window --output fresh3
python -B -m tools.migration72.scene_world_mplevel_type_factory_window --audit fresh2 fresh3 --output fresh2-fresh3-audit.private.json
```

`fresh2-fresh3-audit.private.json` 为 1578B / SHA `3ac6eb649b138ae513f7a345845353a26292ea591ed2230b23711b9aef3433b1`，`passed=true`、十件一致、当前 PE 读取 0、target CPU 0。新布局记录每份 14432B / SHA `edf15455d194a0d5fc90fd390529ee8d7ebf888991bc3d68ce6e4e10f815cf28`；代码表每份 9686B / SHA `3327a010673ad763a1ea3326918e37128517d02893c577f5de2c2184c14296a7`。另以标准库对保存 body、81 条原始指令和固定规格逐项核对入口字节、RIP 表基、八个 signed rel32、代码边界、CFG 与语义标志，结果 `layout-independent-oracle.private.json` 1634B / SHA `c27d24807cc7433a923ac7d002aa2ec5201578efbdb4b17f9872bcf2d32fc215`，不读取当前 PE。

预留唯一集成者独立第三，未在本包执行：

```powershell
python -B -m tools.migration72.scene_world_mplevel_type_factory_window --output main-third-v2
python -B -m tools.migration72.scene_world_mplevel_type_factory_window --audit fresh2 main-third-v2 --output main-third-v2-audit.private.json
```

期望第三份十件与 fresh2 相同，纯 audit 通过且当前 PE 读取为 0。第三采集本身按规格实际读取当前 stock/PE；纯 audit 不读当前 PE。此次仅确认源布局，未执行 factory、allocator、模型、reader 或目标 CPU；无法据此断言实际返回对象、slot、自然 reader 或 Scene ready。首份失败和入口错误均原样保留。
