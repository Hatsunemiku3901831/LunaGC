# D02-R1/R4：登录页入口上下文只读快照

此卡仅准备离线审核的采样器，尚未对客户端执行。自然模式、存储和场景根的当前值均未知；本工具不启动客户端。需由主线审核后，在用户新一轮就绪和唯一监测启动完成且目标仍存活时使用。

两份固定合同 SHA-256 分别为 `6503b65872f093d503c2db8668607542c0a5d3a336002e829e5aa82b924734a4`、`149c5ebcacf726009dbd028becf2d75f6caa1b2178ccf38e5566c3de26b4a45d`。五输入逐一核 profile 和文件 SHA；两合同共16个固定机器码窗口使用实际 PE 可执行文件内容复核，Scene窗口还逐字节核指令。合同不符、机器码漂移、监测器源不符或输入/配置变化均在打开目标前拒绝。监测器必须 completed/alive；PID、创建 FILETIME、映像及实际主模块与最后监测样本一致，访问权限为 `0x101010`。

模式与静态根独立读：`B+586ceb2` 一字节，0、1、其他、失败分别保留；`B+586b600` 为64位指针。根非空时读取 `+60e8` HashSet<int32> 指针及非空对象40字节头、`+6100` List<int32> 指针及非空对象32字节头。HashSet头 `+10` slots、`+18` comparer、`+20` buckets；不把这些字段当 count。List头 `+10` items、`+18` size、`+1c` version。字段来源为 GlobalVars owner11325 的53821、53824、53913；非空不证明动态泛型 class 身份。

场景四槽为 PoolCluster `+5d60`、两个注册接口 `+c608/+c6d0`、默认 String `+c698`，非空对象仅读8字节 klass；前三者 klass 非空再读 class `+0..cf`，String 不读取 class头、长度或字符。读错或部分读取停止该分支并标记 partial；未读取不是 null。最大17次、785字节、单次最多208字节。不读容器内容、接口列表、Allocate槽或字符串，不调用客户端函数。快照非原子，后续 writer 可变；即使非空也不能证明构造已执行、注册非空、World、实体或 SDK 请求。相应布尔结论固定 false，仅表示未证明。

离线验证：五输入、16窗口实际匹配，7项夹具通过，覆盖高64位指针、最大预算、根/槽/class空、部分读取、未知模式、输出限制与合同/机器码漂移。脱敏证据和测试日志在忽略目录 `local/migration72/d02-active/main/entry-context-tool/`。冻结旧采样器 SHA `2eb415af7e410b53df35340426310a52e72f7e9a69692c2158c4dadb92362aae`，其测试 SHA `184ccce26d606f11fbc19a1700cc4ca41e09cd6df52860fd21f8a78983e340b5`；监测器 SHA `e0b4134e6bb7be8c758b3a7bad3884a8246b25c6d65cd8ca3c91d0941ed33c6e`，均未修改。此工具 SHA `58148689977a1d2cbe863a4e2cf27d128d1d0199731b8f166599c55c6b8c7d7b`，测试 SHA `d320afe0ec53a4dc7d5cdf2e13e4c2a73aa1f06781664ed3e37ec9c4f165b0cb`。

仅供主线审核及满足现场条件后执行；`<新监测报告>` 和 `<新输出文件>` 必须替换为本轮实际路径，输出须位于专属忽略目录且不存在：

```powershell
python -m unittest tools.test_sample_client72_entry_context -v
python tools/sample_client72_entry_context.py --observation <新监测报告> --output <新输出文件>
```

报告含地址、原始字节、哈希和时间，只留忽略目录；进程退出或任何部分读取不返回成功。登录/入场、能力注册与 D-02 均未验收。

主线审核将旧原型的World/SDKrequest等固定false字段更名为明确的Verified结论标记，表示未证明，不把未知运行状态解释为false；除这些输出字段名外源码AST完全相同，原型源/测试已先备份，旧离线proof不冒称新执行源。新7项检查通过，未进入execute或现场；因用户随后要求本批收口后暂停，不启动下一轮实机。恢复须新ready与新monitor，不复用旧PID。
