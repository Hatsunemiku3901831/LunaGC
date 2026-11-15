# Proud String helper 单窗来源

`R3-PROUD-STRING-HELPER-WINDOW` 已完成限定来源验收：主线接受一次静态采集及自身账本，未双fresh、重采或新增测试。目标 `34315.m287516 / 151bbd710` 为flags `96h`、static、一个String显参；formal return、隐藏参数及native ABI未知，owner字典声明不代查找或业务合同。

17项pins、五stock、声明、当前AOT实读、唯一pdata及正文前账本通过；完整 `[151bbd710,151bbd802)` 位于同一file-backed可执行段，正文242B SHA-256 `f7bfb39b47230e13f71b16360e36353761a4608e08baf0faa51dc1d6ece1c97d`；strict连续覆盖62条，索引7 call、12 branch、6 RIP。AOT 16B + pdata 12B + body 242B = 270B；header 4616B、exception目录14344080B与文件hash另账。调用exit 0；未读取callee/global/RIP内容或执行目标。

私有 `local/migration72/d02-resume-20261115/proud-string-helper-window/` 保存 `来源摘要.zh.md`、`manifest.private.json` 及 `capture/`；manifest SHA-256 `17a38d9516d8ba4c087c019b302deb0feb655f55985da5d6d079bc750ff9ff96`。已列冻结目标直接索引0不证明间接或传递独立。`R3-PROUD-STRING-HELPER-SEMANTICS` 正进行有限String查询/判定审阅；来源不预称实际值、正式返回、业务或ready。A/B、preview与准确依赖冻结不变，D-02 / F-05 / D02-R6 仍0/6。
