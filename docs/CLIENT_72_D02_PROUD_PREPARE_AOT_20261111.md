# E-01/R3-PROUD-PREPARE-AOT-D（2026-11-11）

## 执行前有界计划

只执行root批准的D：固定spec、lookup两份原236B caller/第三审、AOT三报告、metadata/signatures与原header/tables的完整SHA，每次fresh重新解GAME_PATH、核五完整文件，再核caller原 `157ffc4d3 e878010000` 的真实E8和原16行；仅按这16个已验pointerIndex逐槽读取≤16 QWORD/128B，其中84537属复核、净增≤15 QWORD/120B。扫描固定generic/methodSpec表以保留全部同pointerIndex共享注册行，分列class/method实例；目标157ffc650零或多命中原样partial，不选第一、不扩tuple或全指针表。跨其他pointerIndex同code全局仍未知。只证明managed注册身份，不推方法语义；0 pdata/body/其他callee/loader/hash/suffix/第三literal/CPU/live/REA/进程/网络/构建/DB/Git。安全或Daybreak门禁停止不重试，普通歧义交root；W只待root另批。本卡仅写本文、同名工具/必要test及独立私有根。

## 结果及限定

原236B caller的 `157ffc4d3 e878010000` 精确解码到 `157ffc650`。已验field400190限定的18192/classInstance5071 `<U32,75077,47647,25030>` 的16个注册槽中，实际QWORD只有一个等于该地址：

| 原项 | 原数据 |
| --- | --- |
| 方法声明 | owner18192 / method138529 `IFIKEDMJMLC`，flags `0x86`，virtualSlot65535，metadata registeredAddress `0x0` |
| 声明参数 | parameterCount=0；签名索引中按旧格式缺省零参数项，返回类型未由本包恢复 |
| 绑定 | tableIndex289813 / methodSpec175101 / classInstance5071 / methodInstance=-1 / pointerIndex84545 |
| 原记录 | 14B `414a0100fdab0200ffffb2650000`；12B `211d0200cf130000ffffffff` |
| 原槽 | `142515eb8`；8B `50c6ff5701000000` → `157ffc650` |

这是先核16行、再读各真实QWORD所得到的唯一**本tuple注册身份**，不是由方法名、相邻地址或指针间距猜测。完整固定generic表中pointerIndex84545的共享行也仅有这一行；所有16个选定pointerIndex各自的共享行总共16条，均已保存在私有报告。其它pointerIndex未读，跨不同index是否同code仍未知。目标方法的body、loader动作、参数语义和实际index填充均未验，不将其命名为Load或EnsureLoaded。此次只执行D；W没有被本包执行或自动批准。

## 16个原槽与预算

以下所有行的owner均18192、classInstance均5071、methodInstance均-1。全部methodSpec、table行、原14B/12B、声明与参数类型在私有报告中保留；此表的QWORD是小端原bytes。

| method | pointerIndex | 原slot | 原QWORD | 解码地址 |
| ---: | ---: | --- | --- | --- |
| 138531 | 84556 | `142515f10` | `a0caff5701000000` | `157ffcaa0` |
| 138523 | 84519 | `142515de8` | `50c1ff5701000000` | `157ffc150` |
| 138530 | 84548 | `142515ed0` | `80c7ff5701000000` | `157ffc780` |
| 138533 | 84564 | `142515f50` | `c0caff5701000000` | `157ffcac0` |
| 138527 | 84537 | `142515e78` | `c0c4ff5701000000` | `157ffc4c0` |
| 138528 | 84541 | `142515e98` | `b0c5ff5701000000` | `157ffc5b0` |
| 138522 | 84515 | `142515dc8` | `70c0ff5701000000` | `157ffc070` |
| 138521 | 84508 | `142515d90` | `20bfff5701000000` | `157ffbf20` |
| 138519 | 84493 | `142515d18` | `f0bdff5701000000` | `157ffbdf0` |
| 138524 | 84525 | `142515e18` | `60c2ff5701000000` | `157ffc260` |
| 138518 | 84505 | `142515d78` | `00bfff5701000000` | `157ffbf00` |
| 138532 | 84557 | `142515f18` | `b0caff5701000000` | `157ffcab0` |
| 138526 | 84533 | `142515e58` | `00c4ff5701000000` | `157ffc400` |
| **138529** | **84545** | **`142515eb8`** | **`50c6ff5701000000`** | **`157ffc650`** |
| 138525 | 84532 | `142515e50` | `00c3ff5701000000` | `157ffc300` |
| 138520 | 84503 | `142515d68` | `00beff5701000000` | `157ffbe00` |

每fresh只逐槽读取这16个QWORD=128B，其中84537为旧槽复核、净新增15个/120B；没有把相邻槽组成连续区间或读全pointer表。完整固定generic/methodSpec表可扫描以列同pointerIndex共享行，不将该扫描说成其他指针已读。两个完整表另存各fresh私有目录供离线复算，尾填充包含在原SHA内。

## 固定来源及验证

两fresh先分别解析当前GAME_PATH，核五文件完整大小/SHA：EXE `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`；global metadata `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`；startup metadata `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`；SDK `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25`；Astrolabe `87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442`。完整文件读取只作身份门禁，另于报告记录选中pointer逻辑预算。

原caller两份236B全SHA `479e010ba8e0b7fbea184c024408f2a1ce266aa519b92445bf03279672f2fb4e`，lookup第三SHA `9e48e1dfb3bc27073e04934572dc21dc57b124ba7d7bed74cbf00701c6376f6c`；旧AOT三份报告完整SHA `ab9f56b6d782430261d195a2b21cf245340e86b01663483cb9e676c483b06b5f`。source规格SHA `ef4109fa7efc3ffa2a284302ed55d494423f33fa3ed017fcf6c524603ee1f62a`；metadata/signature、旧算法、profile及resolver均在输出中逐项固定。没有将可能追加的公共专题作为数据pin。

原header六字段共28B与旧报告一致，解码pointerBase `142470cb0`、pointerCount469946；methodSpec表6979513B/SHA `94c576e22b94971933613fef1855ac9223b505e74e2c78974d370e0109df57c8`，generic表8000260B/SHA `1af68b22c3f8f66ac55e64440f9a83d0a4aabd112bb587c427fbd0442c707b96`。先验证全部16行原table/spec及声明一致，再读取目标槽。

```powershell
python -B tools/client72_d02_proud_prepare_aot.py --output <新目录名>
python -B tools/client72_d02_proud_prepare_aot.py --audit <fresh目录1> <fresh目录2> --output <新审计文件名>.json
python -B -m unittest tools.test_client72_d02_proud_prepare_aot -v
```

最终fresh `final3/4`各报告33680B/SHA `69ab19ff424019a5d38d02a42ae6573a4731788519b136a38a74378cd19f02b6`，两完整表及报告逐byte一致。离线audit `audit3/4.private.json`各24456B/SHA `0bff021d1171cde4c1248370da22718887765e472c788ecfba619aa41e5ff5e3`，不读当前PE，重新从两完整表复算16行和共享行，核原slot数据、声明、唯一目标及范围。5项测试通过：零/一/多目标不丢失、16槽集合/重复拒绝、其他class/method实例共享行保留、表引用越界、声明参数保留；人工夹具不代表客户端执行。

工具12842B/SHA `4b5049aef4f869c9c843249a3bf168e5f2c9ae4ff104004b55d96ecb3cf14984`；测试2162B/SHA `d9ccf18be4e554b5df0bb56b7badd17f9c25b47d407c4d2ed9bc85a21e9d8a6c`。初轮成功后补充audit对header的显式复核，再为零/多目标测试抽取原筛选逻辑，最终重新双fresh和双audit；历史结果保留。没有安全/Daybreak门禁或取数失败，客户端验收增量0；未读pdata/body/callee、loader/hash/suffix/第三literal，未CPU/live/REA/进程/网络/构建/DB/Git。请root第三审后决定是否另批W；未完成的真实index/row、资源ready及D-02不因此晋级。
