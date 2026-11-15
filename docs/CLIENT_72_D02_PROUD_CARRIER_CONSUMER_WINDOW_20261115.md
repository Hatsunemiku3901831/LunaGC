# Proud carrier consumer 单窗来源

`R3-PROUD-CARRIER-CONSUMER-WINDOW` 已完成限定来源验收：主线接受一次固定输入采集及其自身账本核对。该项仅为 source-only；未独立重采，也未新增或重跑测试。

## 固定来源与范围

已审规划从既有 typed 上下文选择 `69135.m572226`，flags `86h`、instance、5个显式逻辑参数；正式返回、隐藏参数及 native ABI 未恢复。规划材料保存在 `local/migration72/d02-resume-20261115/proud-carrier-consumer-plan/`，不以类型或表形状证明业务消费。

固定规格、声明/签名、profile、工具及五 stock 身份门通过；本次实际读取 ordinary AOT cell 与 slot，并核其目标。唯一 pdata、完整区间同一 file-backed 可执行段及正文前读取账通过后，一次读取 `[14c510e90,14c51157a)` 的完整1770B body，SHA-256 `d2b69db45bef46f14ce1eadd5db88ebba36b7919cf1f7fac12b9095e7637302f`。strict decoder 在1024条上限内连续覆盖410条；索引为44 call、80 branch、16 RIP、1 return、0外部tail。listing `capture/body.asm.private.json` 为68749B、SHA-256 `838735cfdb43ba831d0fec07776379620ed2017fb5e77858f6c09a447c663bc4`。

AOT 16B + pdata 12B + body 1770B，证据读取共1798B；header读取4616B、exception目录扫描14344080B与文件hash另账。已列冻结目标的直接call/branch索引命中为0；间接目标和传递用途未知。未采caller、callee、global、table、seed或xdata，未读取目标进程或执行目标代码。

## 证据与未验边界

私有根 `local/migration72/d02-resume-20261115/proud-carrier-consumer-window/` 保存 `source-only.zh.md`、`self-check.private.json`、正文前账本、`capture/body.asm.private.json` 和调用记录。主线验收的范围是单次来源及自身账本，不称独立第三或实际执行。

`R3-PROUD-CARRIER-CONSUMER-SEMANTICS` 已开始，字段与参数之间的值流尚未经语义审阅；actual M、三字段业务桥、row/current/epoch及resourceReady均未知。直接冻结目标未命中不证明间接无关，不解除A/B或preview专项冻结。D-02 / F-05 / D02-R6 客户端验收不因此晋级。
