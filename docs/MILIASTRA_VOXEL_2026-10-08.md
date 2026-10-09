# Blender 表面体素导出与 GIA 生成

## 使用现有 Blender

此工具在 Blender 中执行，服务端无需安装 Blender。本机已有版本为 **5.2.0 LTS**，
实际位置是 `C:\Program Files\Blender Foundation\Blender 5.2\blender.exe`，未加入 PATH。
该位置仅用于本机示例，项目脚本未写死安装目录或输出盘符。

1. 在 Blender 中载入或导入模型，选中要导出的网格。
2. 在 Scripting 工作区打开并运行 `tools/blender_miliastra_voxels.py`。
3. 默认在当前 `.blend` 所在目录写入 `miliastra-voxels.json`；未保存场景时使用 Blender 当前工作目录。
4. GM「千星资产」展开生成器，选择「体素 JSON」，选择文件，点击「读取体素文件」。
5. 将「体素边长」设为导出时的值，设置原点／透明度，点击「生成并归档」，再导出 GIA。

默认边长为 `0.01`，复杂或较大的模型建议先用较大边长预览。
出现候选／方块数量上限时增大边长，或在 Blender 中简化模型。
原始 OBJ、FBX、STL 等模型须先由 Blender 导入，不直接上传到这个 JSON 接口。
没有选中网格时可使用当前活动网格；多选按对象名称排序，重叠格取较近的不透明表面。

后台运行示例（PowerShell，从项目根目录执行）：

```powershell
& 'C:\Program Files\Blender Foundation\Blender 5.2\blender.exe' `
  --background --factory-startup --disable-autoexec `
  'D:\Models\example.blend' --python-exit-code 1 `
  --python '.\tools\blender_miliastra_voxels.py' -- `
  --output 'D:\Models\example-voxels.json' --voxel-size 0.05
```

使用自己的 Blender 和模型路径即可。CLI 可传 `--alpha`（0–1，默认 0.5）、
`--max-voxels`（1–100000）、`--max-candidates`（1–10000000，默认 5000000）。
后台模式使用保存的选择状态；贴图需保持可读取，临时生成／修改的图像应先在 Blender 中保存或打包。
输出使用 UTF-8 和临时文件替换，出错时不覆盖既有输出。

## 数据与坐标

输入与参考库体素工具兼容，是对象数组：

```json
[
  {"x": -2, "y": 3, "z": 4, "color": "#877264"},
  {"x": 0, "y": 0, "z": 0, "color": [149, 160, 145, 128]}
]
```

`x/y/z` 为 int32 整数，不能重复；不默默截断小数。颜色支持 6 位 RGB、8 位 RGBA 十六进制
（`#` 可省略）或 3／4 项的 0–255 整数数组。RGB 默认完全不透明，RGBA 最后一项为透明度。
GM 阈值为 0–255，默认 128，低于阈值的格子不生成，不占自动 GUID。
重复 JSON 属性、未知字段、非法 UTF-8、非法颜色和溢出坐标均拒绝，并保留编辑内容便于修改。

导出器以世界坐标采样候选格中心，将 Blender `(x,y,z)` 转成游戏 `(x,z,y)` 网格坐标。
**GM 不再交换轴，也不增加半格偏移**。GIA 与参考库的整数格摆放一致：

- 位置 = 原点 + 网格坐标 × 边长。
- 缩放 = 模板默认缩放 ÷ 模板标准尺寸 × 边长。
- 旋转为 0，名称 `Block_x_y_z`，静态方块。

21 种模板使用既有方块调色板与参考 HSV 距离匹配。不是任意 RGB 材质着色，颜色会近似匹配。
位置／缩放须为有限 float32，缩放须为正；不同格转换后位置重合时拒绝。
输入 1–100000 格、JSON 最多 8 MiB；GIA 最多 32 MiB，归档沿用既有数量／容量限制。
自动编号避开已归档、可解析的主资产、镜头详情及依赖编号，包括镜头配置 JSON 中的来源编号。
外部未归档文件及不透明的未知引用仍需另行检查。

## 表面采样范围

使用评估后的网格，包含修改器，并在世界坐标构建三角形 BVH。
候选格使用最近表面距离与半格对角线筛选，属于近似表面采样；不填充内部，不声称精确体积求交。
世界坐标距离避免非均匀缩放导致采样过密或缺失，负网格使用 floor／ceil 边界。
UV 按实际命中的三角形插值，颜色取命中面的材质图像，透明面不占多对象共享网格。
临时评估网格始终通过 `to_mesh_clear()` 释放。

优先读取 Principled Base Color 直接连接的 Image Texture，否则取材质中的首个图像节点。
图像像素按最近像素采样、UV 重复环绕；无图像／无 UV 使用基础颜色，未指定材质使用白色。
不烘焙程序纹理、节点组、复杂着色器、光照、动画或不同 UV 节点的变换；此类效果应先在 Blender 烘焙成贴图。
5.x 避免已弃用的 `material.use_nodes`，旧版本保留该开关判断；官方变更见
[Blender 5.0 Python API](https://developer.blender.org/docs/release_notes/5.0/python_api/)。

总计最多 100 万顶点、50 万三角形；单图最多 400 万像素、总图像最多 1600 万像素。
候选预算包含重复尝试，避免高度重叠面无限消耗；格子、输出 JSON 和整数坐标也有独立边界。

## 来源、验证与边界

适配 `luern0313/UGC-File-Generate-Utils` 固定提交
`fba7c267a3752d3f0108b30020c46868d56acdd5` 的 Blender→数组→方块流程。
导出器保留 Apache-2.0 来源说明与 `tools/LICENSE.blender-miliastra-voxels`。
原始 MIT 资产 proto、已有调色板来源保持不变，没有执行参考库的生成器或其 protoc.exe。

旧接口先用真实 HTTP 测试复现 404。新增 7 项 Java 测试；专项 167 项／24 套件、
全量 1243 项／161 套件通过，失败、错误和跳过均为零。
覆盖 21 色模板、负坐标、缩放、RGBA 边界、严格 JSON、重复格、float32 溢出／重合、
100000 格上限、归档持久化、内部编号避让、鉴权、可下载输出与 HTTP 413。

本机 Blender 5.2.0 LTS 完成 25 项导出检查；含负坐标、非均匀缩放与已应用变换等价、
四边面 UV、最近材质、多对象透明遮挡、修改器、确定性、网格释放及边界错误。
后台 CLI 重新加载保存的 `.blend`，打包贴图后与已验证的 140 格修改器输出一致。
首次安装检查只查 PATH，后续定位到已有 Windows 版本并改用它验证，没有安装新的本机版本。

隔离 GM `http://127.0.0.1:18980/gm`，Chrome 154.0.8037.98／Playwright 1.62.1，
桌面 1365×900、手机 390×844 完成 42 项检查：文件读取、实际 Blender 样本、生成、下载、
透明度、错误恢复、无效隐藏输入切换、GUID 冲突和手机布局，无未解释的控制台／网络错误。
独立使用未修改的原始四份 MIT proto 解码五个浏览器下载的 GIA，共 830 个实体，
核对参考 HSV 选色、float32 位置／尺寸、静态属性、名称、元数据、模板引用和无重合 GUID。

证据目录：`C:\Users\InvokerBot\AppData\Local\Temp\lunagc-miliastra-voxel-20261008\`。
测试编译拼写、下溢样本、未打包贴图和浏览器辅助脚本等待问题均修正并保留原失败记录。
当前 7.1 客户端实际导入／显示尚待实机验证；此功能不等于 UGC 发布、玩法执行、任务和奖励已完成。
本批未提交、推送或部署，日常游戏服务与 Mongo 未重启。
