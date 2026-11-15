# Proud 参数元素 consumer 限定语义

`R3-PROUD-PARAM-ELEMENT-CONSUMER-SEMANTICS` 已完成限定静态语义验收：主线审阅[384B来源窗](CLIENT_72_D02_PROUD_PARAM_ELEMENT_CONSUMER_WINDOW_20261115.md)的98条指令及物理值流。本体读取 `33507.field164470/+18` 的 `List<16681>`，不是原typed候选理由中的 `field164471/+20 IList<54724>`；没有新增三字段数组消费桥。

元素为空时跳过；经固定metadata usage729426绑定的类型为13510。代码比较元素class指针**精确相等**，不包含派生类型；只有精确13510元素才调用 `13510.m99921 / 151643810`。本体将两个String及两个Float引用原样向该方法传递，自身没有Float读取、写入或算术；callee内部及目标数组业务未知。

正常路径中两个bool输出先置0；每次callee返回后分别执行 `*O_q |= (u & entryR9b)` 和 `*O_p |= v`。局部u/v只在入口置0，不能假定每次遍历都重置，也不能猜测callee写入值。IFix分支和guard不补正常路径的未知，formal return、隐藏参数和native ABI仍未知。

固定语义记录 `local/migration72/d02-resume-20261115/proud-param-element-consumer-semantics/semantics.private.json` SHA-256 `eed316cafd6b5c84dfdf1bda0295ea285ee81e539d5f9c5540cf3010b98a7cd4`。其中未知和反例未执行，不是测试结果或Java实现许可；本次未新取源、执行目标或测试，也不因数值链未闭合继续追取 `151643810`。

`R3-PROUD-ARRAY-CONSUMER-PLAN` 正以既有声明/listing有限分析 `33507+20` 输入/ctor候选和69135剩余方法，结果未知；不新读PE、不追codec或冻结动作。实际M/数组、三字段业务桥、row/current/epoch与resourceReady仍未知。A/B、preview冻结继续有效，D-02 / F-05 / D02-R6 仍0/6。
