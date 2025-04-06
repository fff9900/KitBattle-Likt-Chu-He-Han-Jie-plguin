# KitBattle 插件(Readme文件为AI创建)

![Minecraft版本](https://img.shields.io/badge/Minecraft-1.8.8-brightgreen.svg)
![Spigot API](https://img.shields.io/badge/Spigot--API-1.8.8--R0.1-orange.svg)
![构建状态](https://img.shields.io/badge/构建状态-稳定-success.svg)

## 简介

KitBattle 是一个功能丰富的 Minecraft Spigot 服务器插件，为服务器提供职业战斗系统。玩家可以选择不同的职业套装，获得特殊装备和能力，在各种战斗场景中互相对抗。
但该版本为Kitbattle老版本插件，具体新FFa玩法仍在测试中

## 特性

- **职业系统**：创建、编辑和使用自定义职业套装
- **传送点管理**：设置和管理多个战斗区域的传送点
- **玩家数据管理**：跟踪玩家的金币、经验值和等级
- **战斗奖励**：击杀或死亡时获得可配置的金币和经验值奖励
- **GUI界面**：直观的职业选择界面
- **商店系统**：玩家可使用金币购买物品
- **等级系统**：玩家随着经验值的累积可以提升等级

## 依赖项

- [Spigot](https://www.spigotmc.org/) 1.8.8-R0.1
- [Citizens](https://www.spigotmc.org/resources/citizens.13811/) 2.0.33+
- InstantRespawnV1.0.7.jar

## 安装

1. 下载最新版本的 KitBattle.jar
2. 将文件放入服务器的 `plugins` 文件夹中
3. 启动或重启服务器
4. 配置文件将自动生成在 `plugins/KitBattle/` 目录下

## 配置

### config.yml
```yaml
levels:
  '1':
    experienceRequired: 100
  '2':
    experienceRequired: 200
  '3':
    experienceRequired: 300
initial-coins: 100
initial-experience: 0
initial-level: 1
kill-coins: 10
kill-exp: 10
death-coins: 5
death-exp: 5
loot-system: true  # 是否开启击杀掠夺机制
```

### shop.yml
```yaml
presets:
  '1':
    name: 铁匠
    price_iron: 10
    price_gold: 5
    price_diamond: 3
    slot: 1
    enabled: true
```

### 其他配置文件
- `kit.yml` - 存储职业套装数据
- `Spawn.yml` - 存储传送点数据
- `playerdata.yml` - 存储玩家数据

## 命令

### 主要命令
- `/kit gui` - 打开职业选择界面
- `/kit help` - 显示帮助信息

### 传送点管理
- `/kit set spawn <战斗区域名>` - 设置传送点
- `/kit tp <战斗区域名>` - 传送到指定的传送点
- `/kit setlobby` - 设置游戏大厅位置
- `/kit rtp` - 随机传送到某个已设置的传送点
- `/kit spawn list` - 查看所有已设置的传送点

### 职业管理
- `/kit create <名称>` - 创建一个新的职业套装
- `/kit edit <名称>` - 编辑一个职业套装
- `/kit save <名称>` - 保存当前编辑的职业套装
- `/kit list` - 查看所有可用的职业套装

### 玩家数据管理
- `/kit add <玩家名> <金币|经验值|等级> <数量>` - 增加指定玩家的变量数值

### 战斗管理
- `/battle help` - 显示战斗模式的帮助信息

## 权限

- `kitbattle.use` - 使用KitBattle插件的基本功能
- `kitbattle.create` - 创建装备套装
- `kitbattle.edit` - 编辑装备套装
- `kitbattle.save` - 保存装备套装
- `kitbattle.gui` - 打开装备套装GUI
- `battle.admin` - 管理Battle模式

## 开发者信息

本插件可以作为其他插件的依赖项使用，提供与职业套装、战斗系统交互的API。

```java
// 获取KitBattle实例
KitBattle kitBattle = (KitBattle) Bukkit.getPluginManager().getPlugin("KitBattle");

// 获取BattleManager
BattleManager battleManager = kitBattle.getBattleManager();

// 获取ShopManager
ShopItem shopManager = kitBattle.getShopManager();
```

## 许可证

此插件采用 [MIT 许可证](LICENSE)。

## 贡献

欢迎提交问题报告和功能请求！如果您想为项目做出贡献，请：

1. Fork 本仓库
2. 创建一个新的特性分支
3. 提交您的更改
4. 提交合并请求

## 联系方式

如有问题或建议，请通过以下方式联系：

- GitHub Issues
- 电子邮件：[1416727282@qq.com]

感谢使用 KitBattle 插件！ 
