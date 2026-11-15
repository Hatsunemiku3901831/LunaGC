# Proud 数组 carrier 构造方法的尾跳前引用复制

`R3-PROUD-ARRAY-CARRIER-CTOR-BINDING` 已完成限定语义验收：主线审阅[125B来源窗](CLIENT_72_D02_PROUD_ARRAY_CARRIER_CTOR_WINDOW_20261115.md)的38条指令，确认正常继续至尾跳前的条件引用复制；它不证明完整构造、尾跳后状态或实际对象。

以 `T=entry RCX`、`D=entry RDX`、`row=entry R8` 表示物理输入。数组来源选择检查 `Msel` 指针：非空时取 `26072.field127896/+10` 的 managed `54724[]` 引用，否则取 `59500.field295066/+18` 的 `NativeClassArray<54724>` 对象引用，再将所选QWORD原样存入 `33507.field164471/T+20`。若managed数组本身为null，仍复制null，不回退native wrapper；未解wrapper、读元素或执行Float getter。

`M0=row+20` 在 `5986` 读取；native getter路径在 `59a7` 再读 `M1=row+20`，数组选择使用M1。两次读取不能假设同一M或同epoch。另有可见物理store：String到 `T+10`、D到 `T+28`、间接调用正常返回后的EAX到 `T+34`、`T+30` 置0；不据此命名业务。未读取52802数组或Float元素。

未知虚调用 `597d` 及tail可能改变后续状态，所述仅是按条件正常继续时的pre-tail结果。固定语义记录 `local/migration72/d02-resume-20261115/proud-array-carrier-ctor-binding/binding.private.json` SHA-256 `3113e1df63f279d04d4955be9a5858252ddfdfde0e7fa1814553c7c79e5393eb`；本次未新取源、测试、执行目标或Git。

`R3-PROUD-BOUND-ARRAY-TAIL-SOURCE-PLAN` 已限定完成，选择flags81h、instance、零显参 `33507.m281259 / 1488a5750`，只问 `T+20` 及紧贴传递；单窗来源另项进行中，不预称消费或完整ctor。实际M、三字段业务、row/current/epoch及resourceReady未知，A/B与preview冻结不变，D-02 / F-05 / D02-R6 仍0/6。
