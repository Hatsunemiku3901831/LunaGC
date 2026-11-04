# 7.2 map 的泛型 AOT 注册证据

输入固定为 [目标提取](CLIENT_72_EXTRACTION.md) 的三份SHA-256。`extract_client72_map_aot.py` 从本次客户端注册代码恢复MethodSpec与泛型函数表关系，不以相邻地址或字段形状选择map adapter。

| 数据 | 本目标计算方式 / 原生证据 |
| --- | --- |
| MethodSpec | global正文+`header[170] XOR 6347382B`；12字节为methodDefinition/classInstance/methodInstance。14052D240–290、14052D4B7验证字段含义；缓存容量按`(header[24] XOR 323BAE70)/12`向下取整，14053F10B–165为分配证据。容量不是独立验证过的完整方法实例数。 |
| GenericMethodFunctions | global正文+`header[EC] XOR 3CF0720C`；分配字节数u32(`header[110]+A3D9CC8B`)；14字节记录的+0是代码指针索引、+4是MethodSpec索引。1405417AE–7F2和14054193C–98B直接建立关联。表有4字节对齐尾部，不把它当额外记录。 |
| 泛型代码指针 | CodeRegistration+30，数量u32(`reg[64]+9EA91E3B`)；14052EAFC–EB13及14054197C–98B取指针。 |
| 实参 | classInstance索引MetadataRegistration+70的16字节argc/argv项；沿现有7.2 TypeResolver解析类型，不能把methodInstance和classInstance混用。 |

实际检查571,447个函数表记录的指针/MethodSpec/声明引用范围。缓存容量581,626，代码指针槽469,946。筛选出21条与三个map相关的注册，发布于 [map-aot-observations.json](../protocol/cn-7.2/map-aot-observations.json)。未声称恢复所有泛型方法的运行时上下文。

| map实参 | 具体读取入口 | 注册的共享adapter reader | 共享set_Item |
| --- | --- | --- | --- |
| string→int32，实例314 | 155F9BE70 | IAPJMJINMNK(Object,int32)，实例146，158DCC9B0 | 155F9DD30 |
| uint64→DKFJJMGPCMD，实例32153 | 158912430 | LDGOFDLJAEC(uint64,Object)，实例59，155F7FBA0 | 158911AE0 |
| uint32→ANDFHAFNBOJ，实例32160 | 156D35A40 | LDGOFDLJAEC(uint32,Object)，实例1541，157B22E00 | 156D350E0 |

每个具体实例的FNNADLCNGFO与对应共享实例实际绑定同一读取入口；adapter的声明、实参及构造/reader方法另由表定位。源码内的合成接口表/RGCTX仍是离线输入模型，不能将这些注册证据称作真实进程ABI或IFix验收。Object是共享泛型实参，不是消息业务名称。

```powershell
python tools/extract_client72_map_aot.py --client $env:GAME_PATH --output protocol/cn-7.2/map-aot-observations.json
python -m unittest tools.test_client72_map_aot -v
```

4项专项回归检查对齐、索引越界、错误声明/方法实参及不可映射代码；连同reader测试15项通过。目标表重复提取一致。本机日志`local/migration72/logs/a04d3c-aot-tests.log`，重复表`evidence/map-aot-repeat.json`；原始反汇编在analysis/，未提交。
