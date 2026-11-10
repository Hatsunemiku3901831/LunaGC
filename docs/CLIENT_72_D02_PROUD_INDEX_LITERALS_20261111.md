# E-01/R3-PROUD-INDEX-LITERALS（2026-11-11）

## 有界计划和结果

本包按root批准的[冻结规格](../local/migration72/d02-resume-20261110b/c-proud-factory-index-contract/next-literals-spec.zh.md)只解`145b29758`与`145b29760`，不碰第三cell。先对原221B caller、两条RIP指令、前卡literal collector/报告与算法源码作完整SHA门禁；每次fresh重新解析GAME_PATH，核五输入完整SHA，再核原32B header、8B group边界及固定90115行注册区。对两个目的cell分别要求唯一usage，再各按独立sourceIndex读取8B prefix和密文：每项usage+prefix+cipher≤256B、两项≤512B。注册扫描一次且≤100000行/800000B，保存其原bytes供不读当前PE的离线复算。

两目标的编译期UTF-8 literal身份已核：

| 目标cell | 唯一usage row | sourceIndex | destination slot | 原UTF-8明文 |
| --- | ---: | ---: | ---: | --- |
| `145b29758` | 780186 | 48935 | 48935 | `Data/_ExcelBinOutput/ProudSkillExcelConfigDataPrimaryIndex` |
| `145b29760` | 780187 | 48936 | 48936 | `Data/_ExcelBinOutput/ProudSkillExcelConfigDataIndexGroupId` |

sourceIndex与slot经不同原公式独立解出，本例数值相等不成为一般规则。字符串本身不证明文件存在、业务角色、loader执行、hash命中、真实index填充、实际row边界或资源ready；名称不批准字段alias。未解第三literal或任何其他literal，也未读取新code/pdata/xdata、操作目标进程、执行CPU/live、REA、网络、构建、DB或Git。客户端验收增量0。

## 原指令和固定来源

caller固定221B，SHA `80ae3deeedfcc0287f634b115e03e22bb2a7d489621615890697a254394f06d9`；两冻结原件相同。`14d7adbc6 488b358bbb37f8`按nextPC加有符号disp指向`145b29758`，旧静态窗将其值写入field400190所关联壳的+20；`14d7adc10 488b3549bb37f8`同法指向`145b29760`，写入field400189关联壳的+20。这是原指令中的值流，不是自然内存实读。旧两literal报告SHA `9f271ead7fcefa2ff89ed197d0604ee644692f688690fef7da5b994904f8bbe9`；collector SHA `eaefb23db004ee58fe3ba52b8f7ef2bc2eefddebd756706035555617ab6eeea8`；复用解码源码SHA `865c6f7f8fbcea164fa535a12eabc3c9d67ebb1fd0ef3f12c5ec7a1898dce6ed`。冻结规格SHA `7e79ee4975aa68c0e5fb69951f32cb41809505df72c535fa45c61796b548fd02`；全部其他pin和SHA在两份报告中列明。

两fresh的当前GAME_PATH五输入与旧报告逐项大小及完整SHA一致：EXE `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`；global metadata `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`；startup metadata `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`；SDK `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25`；Astrolabe `87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442`。完整文件哈希读取不计入下列选定数据预算。

原header七项按地址顺序为 `ab320a67 7d50ee37 0e352b27 6be11d40 209eac4501000000 5ace9161 ebbffd2d`，合计32B，SHA `fe95282b60260178bca5811c4b08be4147c202c9b3d2ff9b22d9432ee68c2cbf`。group边界原8B `205b562343d6af7b`，SHA `59d0b16743863b55bcc5af262014cd2ec7f13aaeea56fa08dd7669c201c38ed1`。按固定旧公式得到slotsBase `145ac9e20`、slotCount87252、globalGroup314557、usage区间 `[698581,788696)`。该区720920B、90115行，SHA `cd57845c591f2da107930a0d88fbd7d86b3b6f06031f386f8e5db41540d03bb7`；只筛两个目的cell，未输出其他注册项。扫描原件保存在各fresh私有group文件，不是全metadata复制。

## 两项原编码和预算

| 项 | cell `145b29758` | cell `145b29760` |
| --- | --- | --- |
| usage 8B | `02ee99b8346f37f6` | `85435820b1d9765e` |
| usage SHA-256 | `f0caf03d4e6cc1ff535debaa230e2069d2618f90b4e9e8d17ebf644c8122d640` | `f2e5d7b5ff550e0ce6abae60e45a9e2f78260ad20ba72fad9bc3aa3363825044` |
| prefix offset / 8B | `4f8bafc` / `d5ed692e2232522e` | `4f8bb00` / `2232522e302ed72e` |
| prefix SHA-256 | `34e08c1053496059e192be92af549052afe2cda5320a4adfbf03022117c2773e` | `e5638782a5d6a6fa67e959b7ee10f4a05e9b0e466c6dd9445817738f92838731` |
| cipher offset / bytes | `1405f20` / 58 | `1405f5a` / 58 |
| cipher SHA-256 | `cf773f0e65e7b862d868364d426cb60b714c10dd3cfd1a8e710f004de72171fe` | `f3683e76e26d3f594139a5906bbe38c58846c3ed31b4e24544f781b924be4af0` |
| 明文SHA-256 | `058b4298fbe652d492fcc60b5c10a0214bb87e4ada40a4247a3f04c6a79f344a` | `df7c8f9d25fe85551b96e99f2d2683a9833b26c01eb9e7182066454ecc32f48b` |

每项8+8+58=74B，合计148B，分别低于256B及512B。原cipher/plain hex、原header地址、选中usage记录、源和目标公式及UTF-8往返结果均保存于私有报告；报告含原cipher hex，未将完整注册区混入共享文档。

## 验证和交接

```powershell
python -B tools/client72_d02_proud_index_literals.py --output <新私有basename>.json
python -B tools/client72_d02_proud_index_literals.py --audit <fresh1>.json <fresh2>.json --output <新私有审计basename>.json
python -B -m unittest tools.test_client72_d02_proud_index_literals -v
```

最终`final1/2.private.json`各7507B、SHA `ab5e41ca8b95a4d8ff610d5d96140583c9c5f8e8ad34399c93259817dc447c68`，两次新进程逐byte一致；各group原件720920B、上述同SHA。两个离线audit各2429B/SHA `d6e1160cc1eccd1564df174798a7571a6feb41f048a3a105b161dcc07c3d9727`，独立重算固定header、group边界、完整registration唯一性、sourceIndex/slot、prefix、cipher及解码，不打开当前PE。6项测试通过：source/slot不同的人工夹具、重复/缺失、100000行上限、slot越界、group长度和密文漂移拒绝；人工夹具不是客户端执行。

工具13872B/SHA `5f0d70c0bc5bf72918b4f36dc6050f6818ec98368a335ca4d4d9562c38c78f0b`；测试2313B/SHA `972b8b04a7c0b528682026dc5813ee625d9847f4891dc91909cdf83c70becf54`。最初两fresh/audit成功后补充header、group boundary及选中usage/prefix各自完整SHA，最终重新运行两fresh和两audit；旧报告保留但不作为最终交付。没有安全或Daybreak门禁触发，无生产修改、部署或重启。主线第三审后可将冻结结果交C Sol max判断下一必要输入；本包不自行定位payload或执行loader。

主线第三已按本卡最终原件/报告通过，测试及限定验收见Project/Harness；六出口汇合proof SHA `0431793b8b8310d027d08d9de6761fe2164ce6f748eca039bb18a6b26b40c151`。只验本文静态/机械范围，真实执行、row/loader/语义及各父项不因该接受段晋级。
