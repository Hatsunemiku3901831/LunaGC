# F-03d3f4d2 CLEAR48 stock 条件自有执行

按[条件差异](CLIENT_72_D02_CLEAR48_STOCK_DELTA_20261111.md)固定同 epoch 的完整 H2 与三个控制量。复用已验自有 child 的缓冲和校验，只在私有构建中加入用例 259，测试 wrapper 去掉原 ARM 断点，保留真实 CALL。未映射 F 所在页，只写三个已知控制跨度，H2 前后各 32 B 为独立守卫。原 904 B helper 的源和地址关系保持。

MSVC 实际构建并核 PE wrapper 原字节。两个自有进程各执行一次原 helper，均退出 0、返回 destination、回到 C、释放成功。H2 payload 的 6 个非零字节全部清零，48 B 目标区末值全零。64 B 守卫及 16 B 头部保持。原 H2 以完整 SHA 关联到同 epoch 原件。

私有源、构建清单、运行与独立核验位于 `local/migration72/d02-resume-20261111-asc/a-stock-owned-helper/`。EXE SHA-256 `870e8e0ca0a5860f030bcf5ada8f7102b16d32e7a0f84b73867e96af50805336`。实际验收覆盖原 helper 的前后结果；36 条候选路径和两笔写顺序仍以静态源和人工逐步证据支持。原第二 allocator 返回、Default 消费、自然线程和 GC 所有权分别待验。
