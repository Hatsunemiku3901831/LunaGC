# E-01/R3-WEAPON-GADGET-BRIDGE

## 有界计划

目标只解除出生11101的 `Item88968/+2c → Gadget` 职责缺口及必要引用，不重做原已验Weapon/affix、Curve/Promote或真实Gadget50011101行。Java入场从Avatar.equipItem创建EntityWeapon，必须有Gadget/config/ability；相同ID或空实体分支仍不批准alias。

复用 [Item entry](CLIENT_72_D02_ITEM_ENTRY_RESOURCE.md) 已有getter148446d40和41个Gadget直接caller/7个inline cache候选的有界结果。新路线从**实际Weapon87097参数**的消费者入手，避免仅寻找已被内联的getter调用。固定签名缓存SHA `28377e7a8d3c891cd61be97ddb74f8502a22f2f2f039d6a91dba3b092d7822fe` 的准确typeReferences给12个Weapon参数方法；它们与旧41个Gadget直接caller的entry没有交集，旧8个完整新窗也未包含这些入口。

先只核具名 `ShowCodexWeapon/ChangeCodexWeaponModel` 两个准确PE exception窗（`14c424890/14c4248e0`），再按其有源直接边至多两窗，总≤4；UI预览能证明资源字段职责，但不代验实际玩家实体生命周期。只有真实Weapon字段值被送入具名Gadget资源查询/创建职责时才准备原生非零、独立行和缺引用对照。无此桥就止准确缺口，不按Prefab/数值推语义，不新增通用兜底或全量搜索。

本静态阶段主线只写本页和忽略目录 `local/migration72/d02-resume-20261109/r3-gadget-bridge/`，不改运行代码/Proto/resources/patch/config，不启动客户端、DB或服务。输入为当前固定EXE/metadata和已验缓存/报告；源字节、extent/hash、参数和实际消费链分别记录。没有CPU/Java/部署成果前不更新字段完成标记。

## 首两窗及下一直接边

首两窗75B/2073B。普通路径中原Weapon参数进入R14且未被替换，`14c424b78..b84`从R14+10的managed backing取+2c，经原uint getter14aef9db0解出到EBX；`14c424d7e`传EDX=EBX给`15153dd80`。该入口尚不能只据uint参数叫Gadget职责，因此第三窗只审这个准确直接callee；若它继续传给已知Gadget创建者，再核至多一个完整准确目标窗。未运行清理旧预览对象、分配或新实体生命周期。
