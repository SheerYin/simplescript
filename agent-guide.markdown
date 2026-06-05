# 项目协作提示

本文件仅供协作参考，不是强制规格或完整规范；实际情况以当前项目配置、Gradle 缓存、本机环境和用户最新要求为准。

## 编码与文件

- 文本文件使用 UTF-8（no BOM）。
- Windows 专用脚本或注册表文件按系统默认编码处理，例如 `.bat`、`.cmd`、`.reg`。
- 读取已有文件时保留原编码。

## 开发习惯

- 除非明确需要，一般不主动执行完整构建。
- 代码以可读性优先，避免过度使用难懂的语法糖。
- 实现命令时使用平台对应的 Brigadier 接入：Paper/Folia 参考 Paper Brigadier API 和 lifecycle command registration；Velocity 使用 `BrigadierCommand` / `CommandManager`。
- 涉及 `paper-plugin.yml`、`plugin.yml`、插件依赖、Bootstrapper 或 `PluginLoader` 时优先参考 Paper 插件加载相关文档。
- 发送玩家可见文本时优先使用 Adventure Component/Audience API，避免 legacy color code 字符串。
- 修改物品数据时优先参考 Paper Data Component API；注意该 API 仍处于实验阶段，跨版本兼容性以实际目标版本为准。
- 保存插件自定义持久化数据或标记时优先使用 Persistent Data Container（PDC），避免依赖 lore、显示名或内部 NBT。
- 使用调度器时统一优先使用 Folia scheduler；即使目标是 Paper，也推荐按 Folia 的全局/区域线程语义编写，避免依赖传统 Bukkit 主线程假设。
- 涉及跨区块/未加载区块传送时优先参考异步传送 API，不要在主线程阻塞等待 future。
- 配置文件读写优先使用 Configurate；Kotlin 项目优先结合 `configurate-yaml` 与 `configurate-extra-kotlin`。

## Paper/Folia 源码参考

需要查服务端源码、API 实现或补丁时，先看当前模块的 Gradle 依赖使用的是哪一种 paperweight userdev bundle：

- Paper：`paperweight.paperDevBundle(...)`
- Folia：`paperweight.foliaDevBundle(...)`

Paper 和 Folia 都通过 `paperweight-userdev` 插件接入开发依赖；区别在于使用的 dev bundle 不同。确认目标以后，优先看全局 Gradle 用户缓存里的 paperweight work 目录。

| 位置 | 内容 |
|------|------|
| Gradle 模块缓存里的 dev bundle zip | Paper/Folia dev bundle，包含对应服务端的补丁和元数据 |
| Windows：`%USERPROFILE%\.gradle\caches\paperweight-userdev\v2\work\setupMacheSources_*\output.zip` | 反编译、映射后的 vanilla/Mojang 源码包 |
| Windows：`%USERPROFILE%\.gradle\caches\paperweight-userdev\v2\work\applyDevBundlePatches_*\output.jar` | 应用当前 Paper/Folia dev bundle 补丁后的源码与产物 |
| Linux：`~/.gradle/caches/paperweight-userdev/v2/work/setupMacheSources_*/output.zip` | 反编译、映射后的 vanilla/Mojang 源码包 |
| Linux：`~/.gradle/caches/paperweight-userdev/v2/work/applyDevBundlePatches_*/output.jar` | 应用当前 Paper/Folia dev bundle 补丁后的源码与产物 |
| 项目 `.gradle/caches/paperweight/` | 当前项目的 paperweight 辅助任务缓存，通常不是完整源码入口 |

## 参考文档

- Paper API 索引：<https://docs.papermc.io/paper/dev/api/>
- Paper Brigadier 命令 API：<https://docs.papermc.io/paper/dev/command-api/basics/arguments-and-literals/>
- Paper Command API 参数：<https://docs.papermc.io/paper/dev/command-api/arguments/paper/>
- Paper Plugins / Bootstrapper / Loader：<https://docs.papermc.io/paper/dev/getting-started/paper-plugins/>
- Paperweight Userdev：<https://docs.papermc.io/paper/dev/userdev/>
- Paper plugin.yml：<https://docs.papermc.io/paper/dev/plugin-yml/>
- Paper Component API：<https://docs.papermc.io/paper/dev/component-api/introduction/>
- Paper Audiences：<https://docs.papermc.io/paper/dev/component-api/audiences/>
- Paper Event Listeners：<https://docs.papermc.io/paper/dev/event-listeners/>
- Paper Lifecycle API：<https://docs.papermc.io/paper/dev/lifecycle/>
- Paper Data Component API：<https://docs.papermc.io/paper/dev/data-component-api/>
- Paper Persistent Data Container（PDC）：<https://docs.papermc.io/paper/dev/pdc/>
- Paper Scheduler：<https://docs.papermc.io/paper/dev/scheduler/>
- Paper/Folia Plugin Messaging：<https://docs.papermc.io/paper/dev/plugin-messaging/>
- Paper Supporting Paper and Folia：<https://docs.papermc.io/paper/dev/folia-support/>
- Paper Plugin Configuration：<https://docs.papermc.io/paper/dev/plugin-configurations/>
- Paper Inventories：<https://docs.papermc.io/paper/dev/api/inventories/>
- Paper Display Entities：<https://docs.papermc.io/paper/dev/display-entities/>
- Paper Entity Teleportation：<https://docs.papermc.io/paper/dev/entity-teleport/>
- Paper Particles：<https://docs.papermc.io/paper/dev/particles/>
- Paper Registries（实验性）：<https://docs.papermc.io/paper/dev/registries/>
- Paper Dialog API（实验性）：<https://docs.papermc.io/paper/dev/dialogs/>
- Paper Recipes：<https://docs.papermc.io/paper/dev/recipes/>
- Paper Using Databases：<https://docs.papermc.io/paper/dev/using-databases/>
- Paper Debugging：<https://docs.papermc.io/paper/dev/debugging/>
- Paper Profiling：<https://docs.papermc.io/paper/profiling/>
- Configurate：[SpongePowered/Configurate](https://github.com/SpongePowered/Configurate)
- Velocity 源码：[PaperMC/Velocity](https://github.com/PaperMC/Velocity)
- Velocity Command API：<https://docs.papermc.io/velocity/dev/command-api/>
- Velocity Plugin Messaging：<https://docs.papermc.io/velocity/dev/plugin-messaging/>
- Paper Plugins 入门：<https://docs.papermc.io/paper/dev/getting-started/paper-plugins/>
- Paper dev 文档索引：<https://docs.papermc.io/paper/dev/>
