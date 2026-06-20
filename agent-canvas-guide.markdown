# Canvas 协作提示

本文件仅供 Canvas 服务端模块开发协作参考。通用规则见 `agent-guide.markdown`。

## 开发习惯

### 运行期线程归属

- 服务端侧实现统一按 Canvas 的区域线程语义编写，不假设存在唯一主线程。
- 运行期修改或读取游戏世界、实体、区块、区域归属状态、打开的库存等游戏状态时，通常必须回到对应的 Canvas 全局/区域/实体调度器，并在该调度上下文中继续后续逻辑。
- 使用调度器时优先使用 Canvas 的全局/区域/实体调度模型，避免依赖传统单主线程假设。
- Canvas 调度器更适合作为 ownership/continuation 投递点：把后续逻辑送到正确的全局/区域/实体上下文继续执行。实践上尽量避免把它封装成通用 request/response 或同步取值通道，例如“提交调度任务 -> 在任务里 complete `Future` -> 外层 `get`/`join`/`await`”；调度任务被取消、丢弃或不执行是正常生命周期边界，这类桥接很难覆盖所有关闭、禁用和实体失效场景。
- 普通实体业务通常直接投递到 entity scheduler 即可，预期能执行；如果实体已 retired、插件已禁用或调度未发生，多数业务没有可恢复动作，可以视为该实体上下文自然结束。只有涉及外部资源、锁、事务、严格状态收敛或资源释放的特殊场景，才重点处理 retired callback、提交失败和取消路径。

### 生命周期与卸载

- 脚本、协程或异步回调中把逻辑切回 Canvas 全局/区域/实体调度器前，通常先考虑 `plugin.isEnabled` 和插件生命周期。
- 正常运行时，修改或读取世界、实体、区块、区域绑定数据等游戏状态仍应回到对应调度器；玩家 `sendMessage`、title、actionbar 等单纯可见输出通常不需要因此切换调度器。如果输出内容依赖世界或实体状态，取数据那一步仍按对应归属处理。
- 插件禁用、`onDisable()` 或服务器 stop 后，已排队任务会被取消/丢弃，新提交任务会抛出异常或无法可靠进入执行队列。延迟任务、协程 `delay` 后恢复、数据库/Redis/网络回调、`onUnload { ... }` 等异步完成后再调度，建议把“插件已经不可调度”作为自然分支处理。
- 进入 `onDisable()`、插件已禁用或服务器 stop 后，通常已经处在禁用/卸载流程里，大部分保存或清理所需的现有数据可以直接从当前对象、模块缓存、脚本状态或 Bukkit 当前状态中读取，不宜为了“取一次数据”再新提交调度任务。
- 本项目的 `Celadon.globalRegionScheduler { ... }` 是生命周期友好的包装：插件启用时投递到 global region scheduler，插件禁用后直接执行 `block`，便于卸载流程继续清理当前状态。`Celadon.regionScheduler(...)` 和 `Celadon.entityScheduler(...)` 在插件禁用后会直接返回，不会执行新的区域/实体任务。

### 异步与阻塞

- IO、数据库、Redis、网络请求等阻塞操作放到协程 IO 线程或其它异步执行环境中；完成后再切回合适的 Canvas scheduler 操作游戏对象。
- 一般不在区域线程或全局线程上阻塞等待 `Future`、数据库、网络或长时间计算；需要继续处理结果时优先使用异步 continuation，并在恢复到 Canvas 调度器前重新检查生命周期。
- 涉及跨区块/未加载区块传送时优先参考异步传送 API，不要在调度线程上阻塞等待 future。

### API 使用偏好

- 服务端侧命令优先按 Canvas 的 Brigadier API 和 lifecycle command registration 编写。
- 涉及插件描述文件、插件依赖、Bootstrapper 或 `PluginLoader` 时优先参考 Canvas 插件加载相关文档与当前模块示例。
- 发送玩家可见文本时优先使用 Adventure Component/Audience API，避免 legacy color code 字符串。
- 修改物品数据时优先参考 Data Component API；注意该 API 仍处于实验阶段，跨版本兼容性以实际目标版本为准。
- 保存插件自定义持久化数据或标记时优先使用 Persistent Data Container（PDC），避免依赖 lore、显示名或内部 NBT。

## Canvas 源码参考

需要查服务端源码、API 实现或补丁时，先看当前模块的 Gradle 依赖与 Canvas 开发包配置。Canvas 服务端模块应使用 `libs.plugins.canvas.weaver.userdev`，依赖通过 `paperweight.canvasDevBundle(...)` 引入 Canvas dev bundle。

`weaver.userdev` 是 Canvas 维护的插件开发/userdev Gradle 插件，用来配置反混淆、开发包依赖和服务端源码访问。`paperweight` 是 Paper 生态常用的开发包处理工具链，负责下载 dev bundle、应用补丁、生成映射后的开发源码和依赖产物；在 Canvas 项目里它仍作为底层工具和 Gradle extension 名称出现。配置好 userdev/dev bundle 后，开发时通常可以用映射后的可读名称直接引用和查看服务端 API 或必要的内部实现，而不是面对混淆名。

Canvas 的 Weaver userdev 是 Canvas 维护的 userdev 插件；它沿用了 paperweight 的扩展名、任务名和部分缓存目录名，所以本机源码产物通常仍会出现在 `paperweight-userdev` work 目录下。这里的 `paperweight` 名称是工具实现细节，不表示目标平台改成其它服务端。

| 位置 | 内容 |
|------|------|
| Gradle 模块缓存里的 `io.canvasmc.canvas:dev-bundle` | Canvas 开发包，包含对应服务端的补丁和元数据 |
| Windows：`%USERPROFILE%\.gradle\caches\paperweight-userdev\v2\work\setupMacheSources_*\output.zip` | 反编译、映射后的 vanilla/Mojang 源码包 |
| Windows：`%USERPROFILE%\.gradle\caches\paperweight-userdev\v2\work\applyDevBundlePatches_*\output.jar` | 应用当前 Canvas 开发包补丁后的源码与产物 |
| Linux：`~/.gradle/caches/paperweight-userdev/v2/work/setupMacheSources_*/output.zip` | 反编译、映射后的 vanilla/Mojang 源码包 |
| Linux：`~/.gradle/caches/paperweight-userdev/v2/work/applyDevBundlePatches_*/output.jar` | 应用当前 Canvas 开发包补丁后的源码与产物 |
| 项目 `.gradle/caches/paperweight/` | 当前项目的辅助任务缓存，通常不是完整源码入口 |

## API 能力概览

以下内容仅用于快速判断 Canvas/Paper API 大致覆盖范围，不保证覆盖最新版本的全部 API；具体能力、签名和边界以当前目标版本、官方文档和本机源码为准。

- Canvas API 主要处理 Paper/Folia 在区域线程模型下的边界问题：全局/区域/实体/异步调度器、tick thread ownership 检查、Canvas 修复或新增的区域线程事件、运行时世界创建和异步卸载、随区域 split/merge 转移的 regionized data、全局 tick rate 与区域 pause/play/sprint/walk 状态等。遇到线程归属、跨区域传送、世界卸载、区域本地数据或停服生命周期问题时，优先按 Canvas API 和当前服务端源码确认语义。
- Paper API 覆盖普通插件的大部分玩法和工程能力：插件声明、Bootstrapper/PluginLoader、lifecycle 注册、Brigadier 命令树和参数、Adventure Component/Audience/国际化/签名消息、事件监听/自定义事件/聊天事件、配置、PDC、Data Component 物品数据、插件消息、库存和菜单、Dialog UI、实体传送、展示实体、Mob Goal、Pathfinder、粒子、配方、注册表和 datapack 发现等。普通功能优先查 Paper API；涉及世界、实体、区块线程归属时再套用 Canvas 调度模型。
- 需要访问 Minecraft 或 Canvas 内部实现时，先确认 API 是否足够；确实不够再参考 userdev/dev bundle。内部实现跨版本变化快，优先把依赖范围收窄在必要模块。

## API 入口文档

- Canvas API：<https://docs.canvasmc.io/canvas/developers/api/overview/>
- Paper API：<https://docs.papermc.io/paper/dev/api/>
