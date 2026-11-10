# E-01/R3-PROUD-BASE-PREFIX-REFERENCE

2026-11-11。只完成一个选定 key 的条件文件前缀参考：base59500、52802数组header与元素token。不是完整Proud行、typed对象、自然backend/mode、资源ready或客户端验收。

## 输入与结果

复用[元素合同](CLIENT_72_D02_PROUD_ELEMENT_READER_CONTRACT_20261111.md)冻结的8283B规格，SHA `311bf6644f362d9aa602406b8dfe5b1e72485590924a37eb88437525a47e8a8d`；86源/216964461B清单固定SHA `df5a5476a0d2047cc07cb731027d0e443c19d68c109d3095ac50bd85ca56db85`。完整payload2132778B SHA `82a8017c6c2868e41e3ce8fc02f8bc04437cb641ec653ed3383c5cc109e25835`。key22101的A=4/B=330只给候选对照，reader物理end仍为完整文件长度。

两个新host进程与主线第三输出各27306B、逐byte相同，SHA `e01108b35842e1c2412e4343dd506b14cbf1715c3de285185154193c298267f6`。原始span/slice/连续推进由主线独立复核：

| 阶段 | 条件文件结果 |
| --- | --- |
| base前缀 | prefix31，25个4B项、22B块，到134 |
| 数组header | 原9b19ef7a、count2，到138 |
| 两元素 | 各a090，32位sum10000、低16位0，各消费2B，到140/142 |
| 缺省成员 | enum原direct条件写0；float输入+0.0，实际encoded DWORD仍unknown |
| 交接 | 消费138B，next63831InputCursor=142；仅预览[142,146)=e2fe8a1b，未解derived字段 |

前提分别保留derived+40-null、base+20-null、每个相关reader+20-inline，以及先前原调用正常返回的文件抽象。没有建立42005/59500/63831/数组对象、补allocator成功或把实际cursor写成142。原生存储与float叶未执行；header/成员只按原index与token描述，不命名group/level或Java公式。

## 实现与边界

主线Sol xhigh实现独有工具，Terra xhigh独立写8项人工测试。每次读取保留原PC、绝对span/raw/宽度、presence/default、运算宽度和推进；32位header carry及IEEE754原bits不丢失。块以文件抽象记录，明确原QWORD load集合、余数copy call与cursor提交PC，不冒充逐CPU读取事件或成功NativeCString。

原start检查、host全宽可读条件、候选334越界分开：原start通过而物理宽度不足时返回partial，不执行原错误helper，保留先前推进和enum条件direct写入。候选仅标记不截断。高位count的未批循环明确partial，未改写成原生成功；所有unknown/false状态保留。缺省token无伪造wire，失败读取显式cursorAfter仍为原cursor。

独立测试发现初版present enum只记resultBits、没有明确先前receiver+0条件写入元数据；主线修复并保留旧源码/结果。两个enum路径现均有conditionalReceiverWrite，nativeExecuted=false。随后主线将失败token cursorAfter由省略改为显式不变、缺省宽度/raw明确为0/null，旧测试相应一项先红后修正精确断言；旧源/测试/原件保留。没有改语法、输入身份或放宽错误门。

最终工具SHA `d98b1f3d643e17759940d66c557b82c9e3c3ffb240025c0b462441c3f5186522`，最终测试SHA `3bb41a888207ff6985b2107598f02a903331d42c384a35f02fdcdf8ef07d86a3`。主线最终8方法全部通过；覆盖零/缺省、2/6/10B及carry、非零高位、NaN/inf/-0、块独立组和余数、物理/候选边界、partial状态和输出拒绝。Terra的独立过程及原版本见[测试专题](CLIENT_72_D02_PROUD_BASE_PREFIX_REFERENCE_TESTS_20261111.md)；最终source epoch以本文为准。

## 复现与下一步

原件位于忽略目录`local/migration72/d02-resume-20261110b/c-proud-base-prefix-reference/`；fresh5/fresh6/main-third-v2为最终epoch，旧fresh1–4和初次第三不覆盖。

```powershell
python -B -m unittest tools.test_client72_d02_proud_base_prefix_reference -v
python -B tools/client72_d02_proud_base_prefix_reference.py --output new-first.private.json
python -B tools/client72_d02_proud_base_prefix_reference.py --output new-third.private.json --audit new-first.private.json
```

只用新basename，排他创建拒绝覆盖。无新PE/CPU/native/emulator/live/进程/网络/构建/DB，未改生产/Proto/配置/资源，不需部署或重启。下一项沿已有63831原体从条件142审核完整消费，当前工具不自动扩全6371行或旧号全Proud，完整R3/Avatar/D02仍未完成。
