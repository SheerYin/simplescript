# 项目协作提示

本文件仅供协作参考，不是强制规格或完整规范；实际情况以当前项目配置、Gradle 缓存、本机环境和用户最新要求为准。

## 编码与文件

- 文本文件使用 UTF-8（no BOM）。
- Windows 专用脚本或注册表文件按系统默认编码处理，例如 `.bat`、`.cmd`、`.reg`。
- 读取已有文件时保留原编码。

## 开发习惯

- 除非明确需要，一般不主动执行完整构建。
- 代码以可读性优先，避免过度使用难懂的语法糖。
- 服务端侧实现优先按 Folia 语义编写；Folia 兼容 Paper 时，不为 Paper 单独退回传统 Bukkit/Paper 主线程写法。
- 服务端侧命令优先按 Folia/Paper 的 Paper Brigadier API 和 lifecycle command registration 编写；Velocity 使用 `BrigadierCommand` / `CommandManager`。
- 涉及 `paper-plugin.yml`、插件依赖、Bootstrapper 或 `PluginLoader` 时优先参考 Paper 插件加载相关文档。
- 发送玩家可见文本时优先使用 Adventure Component/Audience API，避免 legacy color code 字符串。
- 修改物品数据时优先参考 Paper Data Component API；注意该 API 仍处于实验阶段，跨版本兼容性以实际目标版本为准。
- 保存插件自定义持久化数据或标记时优先使用 Persistent Data Container（PDC），避免依赖 lore、显示名或内部 NBT。
- 使用调度器时统一优先使用 Folia scheduler；即使目标是 Paper，也推荐按 Folia 的全局/区域线程语义编写，避免依赖传统 Bukkit 主线程假设。
- 不假设存在唯一主线程；涉及世界、实体、区块、玩家状态的操作必须回到对应的 Folia 全局/区域/实体调度器。
- IO、数据库、Redis、网络请求等阻塞操作放到协程 IO 线程或其它异步执行环境中；完成后再切回合适的 Folia scheduler 操作游戏对象。
- 不在区域线程或全局线程上阻塞等待 `Future`、数据库、网络或长时间计算；需要等待结果时使用 suspend/callback 组合。
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
- Paper Component API：<https://docs.papermc.io/paper/dev/component-api/introduction/>
- Paper Audiences：<https://docs.papermc.io/paper/dev/component-api/audiences/>
- Paper Event Listeners：<https://docs.papermc.io/paper/dev/event-listeners/>
- Paper Lifecycle API：<https://docs.papermc.io/paper/dev/lifecycle/>
- Paper Data Component API：<https://docs.papermc.io/paper/dev/data-component-api/>
- Paper Persistent Data Container（PDC）：<https://docs.papermc.io/paper/dev/pdc/>
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
- Paper Debugging：<https://docs.papermc.io/paper/dev/debugging/>
- Configurate：[SpongePowered/Configurate](https://github.com/SpongePowered/Configurate)
- Velocity 源码：[PaperMC/Velocity](https://github.com/PaperMC/Velocity)
- Velocity Command API：<https://docs.papermc.io/velocity/dev/command-api/>
- Velocity Plugin Messaging：<https://docs.papermc.io/velocity/dev/plugin-messaging/>
- Paper dev 文档索引：<https://docs.papermc.io/paper/dev/>
