# E-01d1 旧号 121 条实际突破行字段及 Java 加载

复用[121 个实际组](CLIENT_72_D02_OLD_PROMOTE_GROUPS_20261111.md)及固定同源 payload。原 whole parser 完整读取 889 行，按已定的 121 个阶段键与原跨度读取 `addProps`、`costItems` 和 key。原报告逐项保留属性 enum、float32 bits 和有序材料槽。共 484 个非零属性、483 个正 ID 材料槽和一个零 ID 非零 count 槽。复用 12/6 的已验实际字段，三项升级字段与输入 presence 保持未知。

7 项离线测试和主线第三 CLI 通过。三个最终报告 SHA-256 `38c8d66b08ba8743b49a0bafaab88713494a82a3b84a3de94f3da22f84170a54`，原 whole 与读出共 1,495,243 / 2,000,000 hook visits。固定原 bytes 与 121 个跨度通过。报告留私有 `c-old-promote-fields/`。

独立 Gradle worker 运行 `D02OldSavedPromoteResourceLoadTest`，23 秒 exit 0，XML 1 项通过。真实 `ResourceLoader.loadFromResource` 将 121 行加载至独立 map，键、全部属性 enum/float32、正材料顺序与数量逐行一致。零 ID、count 20 的唯一槽由 Java `onLoad` 过滤。全局 `loadedAll` 保持原值。

离线入口为 `python -m tools.client72_d02_old_promote_fields --client $env:GAME_PATH --output <新文件名>.json`，测试为 `python -m unittest tools.test_client72_d02_old_promote_fields`。Java worker 配置参考[主角单行](CLIENT_72_D02_OLD_TRAVELER_PROMOTE_20261111.md)，本次私有产物在 `c-old-promote-java/java1/`。实际 Avatar 属性重算、材料的目标引用、其余入场资源及完整恢复待验，未改生产资源。
