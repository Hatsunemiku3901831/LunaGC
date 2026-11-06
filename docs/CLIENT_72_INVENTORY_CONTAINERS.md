# CN7.2库存primitive map/list raw切片

2026-11-06，F-03d2a2。六个目标类型22个raw字段已恢复，包含五个`map<uint32,uint32>`和五个packed/unpacked uint32 list。业务身份/字段语义未晋级；剩余嵌套oneof组合图属于F-03d2a3，完整PlayerStore raw闭包仍未完成。

工具[trace_client72_inventory_containers.py](../tools/trace_client72_inventory_containers.py)每次校验并重读`extract_client72.HASHES`固定三输入、重新提取metadata及选定AOT表。它固定依赖[outer摘要](../protocol/cn-7.2/player-store-outer-observations.json)SHA-256 `2c14b416e5e18d868bde7d645939f6ea9c2a936761967e555e92538c2d11e20f`，并用新提取的实例字段逐条复核其15类审计。原始目标文件哈希见[初始化专题](CLIENT_72_INITIALIZATION_CANDIDATES.md)。没有修改公共GraphReader/MapRuntime、生产Proto或包号。

正式摘要为[inventory-container-observations.json](../protocol/cn-7.2/inventory-container-observations.json)。两轮完整原生报告和摘要逐字节相同；摘要SHA-256 `352f094fec870074abb70207af317870525b025c9b68805500c6410347feb72d`，本机证明在`local/migration72/evidence/inventory-containers-determinism.json`。8项新增与包含它们的46项关联研究回归通过。

## 新专用原生map路径

目标AOT注册独立确认`classInstance3723=<uint32,uint32>`，具体reader `0x15893d460`、adapter reader `0x158935d30`、adapter ctor `0x158935cd0`、setter `0x15893e4d0`。这些是具体primitive map body，不能套用先前uint32/message共享map的`0x156d35a40`。

专用`UInt32MapRuntime72`只为这组具体函数提供托管储存fixture。实际执行流程为具体消息reader → primitive map reader → 实际ReadMessage/limits → primitive adapter的key/value dispatch与primitive delegate调用 → 原生setter → 返回。新key的dictionary lookup/insertion和linked-node分配为明确fixture；已有key的覆盖由原生`0x15893e565`将`(value<<32)|key`写入node+0x28完成。该8字节packed pair布局与此前消息值map的16字节布局不同。

map codec的key/value原始tag为8/16，default为零。六个实际`.cctor`均运行到返回，工厂是显式primitive-codec fixture，目标`.cctor`原生执行传参、组合codec对象及static store；摘要记录实际工厂参数和全部static目标tag。示例14095捕获`[18,8,16]`并得到list tag2、map tag3；74063捕获`[42,58,66]`并得到三个list tag5/7/8。没有把工厂fixture称为完整原生codec工厂恢复。

## 六类raw字段

所有map均为uint32 key/value；所有list均为uint32，目标原生分支同时接受wire0与packed wire2。标量均wire0。字段名字保留目标混淆名，不命名为武器等级、精炼、套装或其他业务。

| 类型 | map字段（tag/对象偏移） | list字段（tag/对象偏移） | 其他标量（tag/对象偏移） |
| --- | --- | --- | --- |
| 14095/JLAGHMMLDCA | JMGKMMFNABP 3/+18 | EIBDIEHEAKF 2/+20 | 无 |
| 28473/PEMOFGMDGHP | ALNBHHEJLKP 1/+18 | 无 | MNBPLFPCJLG 2/+20 u32 |
| 28475/DFPLPBLIEEK | ALNBHHEJLKP 1/+18 | 无 | MNBPLFPCJLG 3/+20、PGDAIKELICG 2/+24 u32 |
| 61085/MCBNNKGKJCI | ALNBHHEJLKP 1/+18 | 无 | 无 |
| 64947/FNECPDFLEHN | JMGKMMFNABP 4/+18 | FLGGIMBHFOO 6/+20 | NILMBAGDEAL 2/+28、KFDCBKBPPGL 3/+2c、NJAGFJINHMA 1/+30 u32；MNCFEDPDEAG 5/+34 bool |
| 74063/JBBEOMJDLFM | 无 | FOFCJMPCEBB 5/+18、IMEGOOADEBF 7/+20、IHKPPDPBEML 8/+28 | KFDCBKBPPGL 3/+30、OJDKGGHBKKK 4/+34、NILMBAGDEAL 2/+38、NJAGFJINHMA 1/+40 u32；CGKIGPFDFOF 6/+3c bool |

768次有界扫描覆盖六类tag1..32/wire0/1/2/5，字段覆盖逐项对照全部22个metadata实例字段。另29组图含五个map各自独立非零/已有key显式零覆盖/u32 key与value上界/default空entry、遗漏key或value、entry内部重复及unknown tag；含packed/unpacked交错append、列表0/u32上界、三个list独立值、六字段非零/非canonical bool、重复标量/错wire/显式false，以及六类空输入。

独立手写map wire `0a040807100b`在61085得到`{7:11}`；追加`0a0408071000`由实际setter覆盖为`{7:0}`，保留零值entry。`100112048001ff011000`在14095通过packed/unpacked实际append得到`[1,128,255,0]`。三个错误分别为map截断、packed varint截断和非法tag，最终都由显式WireInput模型拒绝，不称客户端原生错误合同。

## 复现、限制与后续

```powershell
python tools/trace_client72_inventory_containers.py --client $env:GAME_PATH --output local/migration72/evidence/inventory-containers-native.json --observations local/migration72/evidence/inventory-containers-summary.json
python -m unittest tools.test_client72_inventory_containers -v
```

托管分配、metadata初始化/具体method-info上下文、primitive input/delegates、固定容量64的dictionary lookup/insertion及packed-pair节点分配为明确fixture。原生具体map adapter/setter、ReadMessage/limits、uint32 list append及六个codec initializer执行。未覆盖扩容、IFix/live ABI、handler/UI或客户端；这六类没有uint64字段，64位GUID由d2a1另验，不能把本卡写成额外64位覆盖。

首非空map夹具在`0x15893e5b3`暴露method-info需额外间接层；依据目标连续load修正synthetic上下文后才到真实dictionary插入/覆盖。一次新增scalar-duplicate夹具被误套map覆盖断言，缩小为仅map重复key回归要求原生mapStores后通过。失败证据保留本机分析目录，未伪造原生返回或跳过setter。

后续a3应将这些六类非空内容接回Item的多个分支，并恢复63442/63899/28471/44169等nested/oneof、28474叶子和同分支merge/切换/列表组合。primitive map只解决容器内容，不证明Item/武器/圣遗物等业务身份。
