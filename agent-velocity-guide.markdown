# Velocity 协作提示

本文件仅供 Velocity 代理端模块开发协作参考。通用规则见 `agent-guide.markdown`。

## 开发习惯

- Velocity 命令优先使用 `BrigadierCommand` / `CommandManager`。
- Velocity 插件消息优先按官方 Plugin Messaging API 编写，注意通道注册、来源校验和转发边界。
- 代理端逻辑不要假设存在服务端世界、区块、实体或区域调度语义；需要操作游戏世界时通过明确的服务端协议、插件消息或后端服务完成。
- 代理端 IO、数据库、Redis、网络请求等阻塞操作放到协程 IO 线程或其它异步执行环境中；完成后再回到合适的代理端执行环境。
- 玩家、控制台或管理员可见文本优先使用 Adventure Component/Audience API，避免 legacy color code 字符串。
- 配置文件读写优先使用 Configurate；Kotlin 项目优先结合 `configurate-yaml` 与 `configurate-extra-kotlin`。

## API 能力概览

以下内容仅用于快速判断 Velocity/Configurate API 大致覆盖范围，不保证覆盖最新版本的全部 API；具体能力、签名和边界以当前目标版本、官方文档和本机源码为准。

- Velocity API 主要覆盖代理端插件能力：插件基础结构和依赖管理、事件监听、代理端 scheduler、Brigadier/CommandManager 命令、Plugin Messaging、玩家与后端服务器连接管理、服务器注册信息、转发边界、权限和 Adventure Component/Audience 文本等。代理端没有服务端世界、区块、实体或区域线程语义；涉及游戏世界状态时，应通过后端插件、明确协议、插件消息或外部服务协作完成。
- Configurate 主要覆盖配置读写和结构化映射：以 node 表达配置树，支持 YAML/JSON/HOCON/XML 等格式 loader，支持对象映射、配置转换、默认值和 Kotlin data class 等扩展。Velocity/Kotlin 模块读写配置时优先考虑 `configurate-yaml` 与 `configurate-extra-kotlin`，再结合项目现有配置封装。
- 需要访问 Velocity 内部实现或判断 API 行为边界时，先确认公开 API 是否足够；确实不够再看 Velocity 源码，并尽量把对内部实现的依赖收窄。

## API 入口文档

- Velocity API：<https://docs.papermc.io/velocity/dev/>
- Configurate：<https://configurate.aoeu.xyz/>
