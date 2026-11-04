# 7.2 方法参数与回调签名

本次目标固定哈希见 [提取记录](CLIENT_72_EXTRACTION.md)。`tools/extract_client72_signatures.py` 使用标准库重新解析三份输入；输出只留本机 `local/migration72/evidence/client72-signatures.json`。返回类型、泛型方法实例替换及运行时热修复不在当前确认范围内。

## 原生解码证据

参数访问器 `0x140532210` 的 `0x140532349–0x14053254C` 分支读取方法索引及26字节方法记录，再逐项建立参数。全程来自7.2指令，没有沿用旧profile。

- 参数首项：`u32(method[4] - methodKey + C05565F9)`，methodKey沿用已从目标确认的方法密钥。
- 参数表：`210 + u32(header[174] + AE6BCD68)`，每项8字节。
- 参数key：`u32(((u64(index*74B075FDA13+3362D8F50E637A)>>22) XOR 599EEE90)+214E875D)`。
- 名称索引：`u32(word0+A587B2CD) XOR key`；类型索引：`u32(word1+DB1D6B24) XOR key`。类型FFFFFFFF显式保留未知。
- 类型引用的bit30为BYREF，独立于类型kind和低16位属性；`1404EDCE4–1404EDD28`的inflate路径保留此位，不能把ref/out参数当值参数。
- VAR/MVAR参数序号：表`210+u32(header[94]+D97E8A5C)`，每项14字节；序号`u16((word[6]-41F0) XOR ((u32(index*42BB72A)>>12)*-1980+5C80))`。证据`1404ED9EE–1404EDA43`（类）和`1404EDC8B–1404EDCE0`（方法）。两种上下文分开，不互相代入。

参数区实际为`4371204–47AEE7C`，后端边界由独立恢复的字段表交叉确认。326,354个有参方法的555,919个参数逐项连续、无重叠/空洞，所有名称及44,140种参数类型引用校验通过；其中30,839个参数带BYREF。泛型变量位置实际0–9，不据此限制未来输入。

System.Action的901种已引用闭合类型完成Invoke**参数**替换：先选同一declaring type的唯一Invoke声明，再按VAR位置代入类实参，保留参数属性/BYREF；嵌套泛型/数组递归处理。拒绝MVAR、缺失序号和越界实参。没有宣称恢复Action返回类型，也没有把没有方法的协议泛型标记类当作委托。

例如网络管理器DHABIHPMOAH的AJMHPFDKFEA（15120E260）含`Action<bool>`；NNDBPEJEPAC（15120F0D0）含`Action<IENBACJFBGI>`。Invoke声明的VAR序号及具体实参来自两张不同的目标表，未通过旧名称猜测。

## P0响应定位

| 声明类型 / 方法 VA | 参数类型 | 用途与边界 |
| --- | --- | --- |
| DHABIHPMOAH / 15120DEA0 | MJGOAHFNCGC | 心跳响应回调；CmdID25763注册/分派与getter一致，wire见网络研究 |
| DHABIHPMOAH / 1512160D0 | NDENMDAIAMC、bool | PlayerLoginRsp候选进入实际响应回调；bool尚不命名 |
| DHABIHPMOAH / 151221D70 | CLDBKDNJHFG、bool | GetPlayerTokenRsp候选进入实际响应回调；bool尚不命名 |
| HBIJFOJIFAH / 1507B7BE0 | NDENMDAIAMC | 更上游响应处理入口 |
| HBIJFOJIFAH / 1507BE0E0、1507C0B60 | CLDBKDNJHFG | 更上游响应处理入口 |

静态参数表恢复不等于执行回调；Token/Login业务名称仍需结合完整流程核实，保护读写入口另行追踪。方法有参数但注册指针为0（例如泛型Invoke）是声明，不捏造可执行地址。

## 验证与接续

本项12个参数/结构测试通过（含此前结构回归）：独立目标方法/参数密文字、参数表边界和类型错误、VAR序号、嵌套替换、MVAR拒绝及BYREF保留。目标全量提取成功，详见`signatures-summary.json`、`logs/signatures-tests.log`；本机筛选结果`analysis/net-method-parameters.json`、`p0-signature-references.json`、`p0-generic-contexts.json`只作索引，可由完整输出重建。

```powershell
python -m unittest tools.test_client72_signatures tools.test_client72_structures -v
python tools/extract_client72_signatures.py --client $env:GAME_PATH --output local/migration72/evidence/client72-signatures.json
```

接续先按method.index连接参数，按typeIndex查共享typeReferences；不要把genericParameterPosition与全局generic参数索引混用。可在actionCallbacks查看具体Invoke参数。原始指令`analysis/method-signature-accessor.txt`、`inflate-type.txt`绑定本页输入哈希；旧的全量metadata报告不含本项新增参数。
