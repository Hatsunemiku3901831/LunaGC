# E-01/R3-PROUD-INDEX-AOT（2026-11-11）

## 执行前中文有界计划及主线收敛

只处理原606B caller `14d7ad941 e87aeb840a → 157ffc4c0` 与field400190约束的18192/classInstance5071 `<U32,75077,47647,25030>`。先固定旧报告、源码、原caller、metadata/signature及原28B header和两张完整generic表的SHA；两次新进程重新解析GAME_PATH并完整核五输入。仅在原表中按owner/instance筛行，再按原声明参数`VAR0`按值、`VAR1`引用筛候选；不借用其他tuple的slot。读取候选原QWORD最多32个/256B，复核完整两表中该pointerIndex所有共享row，分列classInstance/methodInstance及原记录。无新callee/code/pdata/xdata/CPU/live/REA/网络/目标进程/构建/DB/Git，不修改共享helper。身份与方法语义分开验收；歧义、超预算留诊断并停止，安全/Daybreak门禁立即停不重试。B Daybreak冻结不碰。

执行时已明确告知主线：两表可以穷尽同pointerIndex共享row，但不同pointerIndex是否存有同一code地址无法在32 QWORD预算内穷尽。主线确认保留此边界，不扫指针表或扩预算；不能把code相同当方法语义。结果因此对所筛身份唯一，对全表跨slot同code范围为**partial**。本卡是现成单链小续项，复用前卡固定来源，不做GitHub新检索；只写本文、同名工具/测试与独立私有目录。

## 结果：声明身份唯一，行为未知

原606B caller SHA `c090061f8eaef3bce27411310d52b2811eab410fe73f772dbe76424d22fd570d`，固定字节 `e87aeb840a` 的有符号E8解码目标为 `157ffc4c0`。field400190属owner80063、rawOffset `4021f58`、attributes `0x31`；其声明类型给出上述class tuple。固定metadata对owner18192的声明筛选只命中一个参数形状：

| 项 | 原数据 |
| --- | --- |
| method definition | 138527 `NGNLGNHAKKO`，flags `0x1e6`，virtual slot 4 |
| 声明参数 | 126525/typeIndex500938：VAR0按值；126526/typeIndex500946：VAR1 byRef，attributes `0x2` |
| 闭合参数 | 由class tuple分别代入U32和75077引用；类型引用的generic parameter owner status原样为`not-recovered` |
| 泛型注册 | generic table index13207，methodSpec175093，classInstance5071，methodInstance=-1，pointerIndex84537 |
| 原记录 | 14B `394a0100f5ab0200ffff89660000`；12B `1f1d0200cf130000ffffffff` |
| 唯一读取slot | `142515e78`，QWORD `c0c4ff5701000000`，解码地址 `157ffc4c0` |

固定signature文件列出两个参数，未在本数据包恢复声明返回类型；attributes `0x2`按原数据记录，不单凭它或调用形状推断完整运行结果。声明身份不证明查询算法、成功、失败、写out的行为，也不命名TryGetValue或FindEntry。未读取方法体或执行，实际key、索引值、row span、资源ready、完整Avatar及D-02仍未知。

完整generic表中此owner/instance共16条table row，methodInstance均为-1。索引、方法与pointerIndex分别是：13203/138531/84556、13204/138523/84519、13205/138530/84548、13206/138533/84564、13207/138527/84537、124551/138528/84541、126925/138522/84515、132333/138521/84508、154983/138519/84493、169535/138524/84525、289810/138518/84505、289811/138532/84557、289812/138526/84533、289813/138529/84545、289814/138525/84532、289815/138520/84503。所有14B及12B原记录保留在报告；其余15个slot未读，不能以方法名、相邻slot或其他tuple推定地址。

扫描整个固定generic/methodSpec表对pointerIndex84537找共享行，**仅上述一行**，所以同index无其他class或method实例共享。未核其它pointerIndex所存QWORD；任何跨index指向同一code的行均未知。本包只证明经声明筛选的这一原注册绑定，绝不声称全表同code行已穷尽。

## 输入、预算与复现

两次fresh实际都重新解析GAME_PATH，五输入大小及完整SHA相同：EXE 450927000B `8b014f64b5dc12fbc476e0c362b053abfdd7a1378cd282e4ad81057621dbd6b5`；global metadata 83644188B `45dc7a44dc9fc187146c52f6c33de57e2d1c1dcfec57f8736431553460f6ef2a`；startup metadata 4116148B `b314810840ca6f495822c9c44483dde2394b9f09ed3c6b13c2782092f04d5105`；SDK 9360280B `adecdf0bd5442e321b1b66b0ca9e98bef76d5067326aa4add5b75266055a6c25`；Astrolabe 2614168B `87e0a005ac46515156636af2120d3f824bcd28d69253e7d7235b959b54a0b442`。完整文件读取用于hash门禁；在hash之外取原header字段28B和候选slot 1 QWORD/8B，不取任何新code。

header原字段及旧collector公式逐一固定：H+170 `5fb55063`、H+24 `c9d15132`、H+ec `2cbf7e3f`、H+110 `7946a05c`、CR+64 `7f0d5e61`、CR+30 `b00c474201000000`。解码methodSpec offset `178f84`/6979513B/581626行、尾`29`；generic table offset `38ecf30`/8000260B/571447行、尾`0000`；pointerBase `142470cb0`、pointerCount469946。两完整表SHA分别 `94c576e22b94971933613fef1855ac9223b505e74e2c78974d370e0109df57c8`、`1af68b22c3f8f66ac55e64440f9a83d0a4aabd112bb587c427fbd0442c707b96`。metadata cache、signature、resolver、profile、旧collector及其两份旧报告、next-input-spec、row-reader旧报告和原606B caller的完整SHA均以代码固定，并列于每份报告；旧来源不被改写。

```powershell
python -B tools/client72_d02_proud_index_aot.py --output <新的私有basename>.json
python -B -m unittest tools.test_client72_d02_proud_index_aot -v
```

`final3.private.json`及`final4.private.json`各12430B、SHA `ab9f56b6d782430261d195a2b21cf245340e86b01663483cb9e676c483b06b5f`，逐byte一致。工具11125B/SHA `43733eda2878637cf5506eb6aa9ce785a00c30fdbc0a28cfc9a30fd94043dc98`；测试2321B/SHA `8fc3fd3b944e7033a4d40776f00822e67873b67d83a647a656a0d1526bd16fcc`。5项测试实际通过：声明byRef与按值区分、field tuple漂移、class/method实例保留、33个候选在读取前超预算拒绝、表引用越界拒绝。fresh报告保留全部原行和source pin；没有将测试样本称为客户端执行。

起初两次入口启动在source gate因Python模块路径未配置失败，未读取当前客户端；修正路径后另两次成功。随后补充完整16行账、将跨slot未知显式标为partial，并补pin原606B caller/E8，最终重新两fresh及测试；先前报告和失败诊断保留，只有最终3/4为本交付。期间没有安全或Daybreak门禁触发。本包无生产修改，无需构建或重启，客户端验收增量0。下一步由主线将该身份数据交C Sol max，与独立factory/index语义结果合并；若其需要跨不同pointerIndex同code的穷尽或方法行为，须先另列最小必要来源和批准预算，不能把本partial升级为全表穷尽。

主线第三已按本卡最终原件/报告通过，测试及限定验收见Project/Harness；六出口汇合proof SHA `0431793b8b8310d027d08d9de6761fe2164ce6f748eca039bb18a6b26b40c151`。只验本文静态/机械范围，真实执行、row/loader/语义及各父项不因该接受段晋级。
