# Useless-Generators

Minecraft **1.20.1**, **Forge**, mod ID `useless_generators`, current source version **1.4.14**.

本仓库为该模组的独立源码仓库。请查看 [构建说明 / Building](BUILDING.md)
了解四个仓库的联合构建方式；发布更新记录见 [CHANGELOG.md](CHANGELOG.md)。

<!-- generator-overview:start -->
## 发电设备是做什么的？

**无用之物：发电设备**为无用之物提供流体燃料发电、后期多方块发电及 ME 网络自动化。
它把无用物质、有用物质等流体燃料转换为 Forge Energy（FE），并可向 AE2 网络供电。
本模组需要 Useless Mod、Useless Core 和 AE2，**不要求安装 Stretcher**；最低版本见下方依赖表。

### 主要内容

| 内容 | 作用 |
| --- | --- |
| 十档单方块发电机 | 从 1K 到 1T，按实际供液和输出空间发电，支持六面能量配置、流体注入和图形界面。 |
| 九级无用物质与有用物质 | 作为液体燃料，通过万象炉直接产出流体；有用物质的制备限定完整万象炉多方块。保留各自材质，并带流动动画和表面光点。 |
| 宇宙之心 | 80×80 格、高 32 格的后期发电多方块，使用有用物质燃料；额定功率为 9.2×10¹⁸ FE/t，满功率消耗 1000 mB/t。 |
| ME 物品／流体输入仓 | 从 AE 网络按标记和目标数量补货，设置标记与实际库存分开显示，供结构和外部设备读取资源。 |
| ME 通用输出仓 | 将接收的资源提交到相连的 ME 网络，提供物品、流体及通用 AEKey 存储入口。 |
| ME 能源仓与网络连接装置 | 提供多方块的能量接入和共享 ME 网络连接。 |
| 可选能量联动 | 安装 Applied Flux 后可把 FE 存入对应 ME 能量存储；安装 Flux Networks 后可使用原生长整数能量传输接口。 |

### 单方块发电功率

设备名称使用 **FE/秒**，下表按 20 TPS 换算为 **FE/t**；供液或输电不足时实际输出会降低。

| 发电机 | 最大发电功率（FE/t） |
| --- | ---: |
| 1K | 50 |
| 16K | 800 |
| 256K | 12,800 |
| 1M | 50,000 |
| 16M | 800,000 |
| 256M | 12,800,000 |
| 1G | 50,000,000 |
| 16G | 800,000,000 |
| 256G | 12,800,000,000 |
| 1T | 50,000,000,000 |

### 基本使用

1. 安装依赖后，用万象炉制作对应物质燃料，向发电机的 16,000 mB 储罐供液。
2. 普通非 T 档发电机接受各级物质燃料，三个 K 档还接受岩浆；1T 档只接受有用物质。
3. 右键设备设置能量面，通过 FE 管道、已启用的 AE 接口或已安装的可选联动输出能量。
4. 宇宙之心需要先完成结构，再连接 ME 舱室及燃料供应。它不会自动强加载区块。

满电时停止耗料，设备只转换实际供给的燃料。普通 FE 接口的单次调用受 `int` 上限限制，
高档设备的实际输电速度仍取决于接收设备与线缆；可选原生长整数接口用于高功率传输。

详细内容见 [宇宙之心结构与使用](COSMIC-HEART.md)、[ME 舱室说明](ME-HATCHES.md)
和 [燃料设计](FUEL-DESIGN.md)。
<!-- generator-overview:end -->

<!-- releases:start -->
## 下载 / Releases

最新可安装 JAR、对应源码标签及校验文件见 [GitHub Releases](https://github.com/mybong9/Useless-Generators/releases/latest)。
安装前请查看下方依赖版本表；构建用第三方 API JAR 不属于本模组发布文件。
<!-- releases:end -->

<!-- runtime-dependencies:start -->
## 运行环境与依赖版本

对应发布版本：**1.4.14**。本工程目标为 **Minecraft 1.20.1 / Forge**，使用 **Java 17+**（编译目标为 Java 17）。
以下依赖范围直接依据该版本发布 JAR 的 `META-INF/mods.toml`，不是编译时所用依赖版本。

### 必需前置

| 依赖 | Mod ID | 最低版本 / 加载范围 | 适用端 |
| --- | --- | --- | --- |
| Minecraft Forge | `forge` | 47 或更高` | 客户端、服务端 |
| Applied Energistics 2（AE2） | `ae2` | 15.0.0 或更高 | 客户端、服务端 |
| Minecraft | `minecraft` | 1.20.1 | 客户端、服务端 |
| [Useless Core](https://github.com/mybong9/Useless-Core) | `useless_core` | 1.0.14 或更高 | 客户端、服务端 |
| [Useless CE Unofficial](https://github.com/mybong9/Useless-CE-Unofficial) | `useless_mod` | 1.3.23 或更高| 客户端、服务端 |

### 可选联动

| 依赖 | Mod ID | 最低版本 / 加载范围 | 适用端 |
| --- | --- | --- | --- |
| Just Enough Items（JEI） | `jei` | 15.12.3 或更高 | 仅客户端 |
| EMI | `emi` | 1.1.0 或更高 | 仅客户端 |
| Applied Flux | `appflux` | 未限定最低版本 | 客户端、服务端 |
| Flux Networks | `fluxnetworks` | 未限定最低版本| 客户端、服务端 |

可选联动不要求安装；安装时须符合上表版本范围，并安装该联动模组自身要求的前置。未声明最低版本的其他动态联动不在此处编造版本下限。

MixinExtras 由 Useless Core 内置提供，通常无需单独安装。

Minecraft 的宽松加载声明不代表本工程已支持其他 Minecraft 主版本。开发构建及本地编译 API 清单见 [BUILDING.md](BUILDING.md)。
<!-- runtime-dependencies:end -->

## Source and attribution

The Useless Mod/Stretcher port retains the original authors' credit and license notices.
Upstream credit is recorded in the source, existing license files and mod metadata.
Third-party resource licenses remain attached to their respective assets.

Source code, resources and build scripts are included. Game saves, runtime folders,
crash reports, local configuration, caches, credentials and third-party dependency JARs are excluded.

<!-- project-license:start -->
## 许可证 / License

本项目采用 [MIT License](LICENSE)。再分发时请保留许可证和版权声明。
第三方代码、资源及依赖仍保留各自已有的许可证和版权声明，本许可证不替代这些声明。
<!-- project-license:end -->
