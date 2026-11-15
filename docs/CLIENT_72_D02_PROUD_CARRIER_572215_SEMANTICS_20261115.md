# Proud carrier 572215 普通路径限定语义

`R3-PROUD-CARRIER-572215-SEMANTICS` 已完成限定静态验收：主线审阅[582B来源](CLIENT_72_D02_PROUD_CARRIER_572215_WINDOW_20261115.md)的152条指令。normal body没有直接读取59500或26072的两个目标数组，也没有将原List整体传作normal callee参数；这不能证明其他方法不消费它们。

以 `T=entry RCX`、`L=entry RDX` 表示物理输入。初始count取一次后由末至首反向遍历，本体没有List版本比较。先取rowA，经未知virtual `+110` 正常返回的EAX为U；跨调用重查count/backing并从同index取rowB。rowB的managed String或native String getter结果与U传给 `69135.m572220(UInt32,String)`；不假设rowA与rowB相同或同epoch，lookup及业务合同未知。

返回非−1时，按结果定位 `T+48` 的 `List<33507>` 元素C。仅当C+30原值非零时先将其清0，再以两个bool 0调用 `33507.m281258`；其后重新取owner List，count减1，按参数请求同一backing区间移位、末槽置null并递增version。callee内部效果及完整清理合同未知，不命名字段业务；IFix分支不作替代。

私有 `local/migration72/d02-resume-20261115/proud-carrier-572215-semantics/semantics.private.json` SHA-256 `17a59285cce54a6bc4f189e2486bffea09a1511c47366359586129940091c573` 保存物理指令和值版本。本项未新取源、测试或执行目标，不追lookup/cleanup helper。

`R3-PROUD-CARRIER-572222-SOURCE-PLAN` 已限定完成，最后未关闭候选 `69135.m572222 / 14c510820` 的单窗来源进行中；结果未知。三字段业务桥、actual M/row/current/epoch与resourceReady仍未知；A/B、preview及准确tail冻结不变，D-02 / F-05 / D02-R6 仍0/6。
