package KitBattle;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * ==================================================
 *  KitBattle 主插件类
 *  依赖: Citizens-2.0.33-b3360
 * ==================================================
 *
 * 插件功能:
 * 1. 传送点管理(Spawn.yml)
 *    /kit set spawn <battleName>  设置传送点
 *    /kit tp <battleName>         传送到对应传送点
 *    /kit setlobby                设置游戏大厅(lobby)位置
 *    /kit rtp                     随机传送到某个已设置的传送点
 *    /kit spawn list             查看所有已设置的传送点
 *
 * 2. 玩家变量(保存在playerdata.yml中):
 *    - coins (金币)
 *    - experience (经验值)
 *    - level (等级)
 *    /kit add <玩家名> <金币|经验值|等级> <数量> 增加指定玩家变量数值
 *    - 击杀、死亡都可获得金币、经验值(在config.yml中设置数值)
 *    - 若经验值达到升级条件(在config.yml的 levels.xx.experienceRequired)，
 *      则自动提升等级并重置经验值
 *
 * 3. 玩家复活后传送回lobby(Spawn.yml 中的lobby)
 * 4. 玩家死亡事件: 给予击杀者与死亡玩家相应奖励
 * 5. 首次进入服务器时初始化玩家数据
 *
 * 6. 职业管理(kit.yml):
 *    /kit create <name> 创建一个装备套装
 *    /kit edit <name>   标记一个装备套装的内容(进入编辑模式)
 *    /kit save <name>   保存当前背包物品(退出编辑模式)
 *    /kit gui           打开职业选择GUI
 *    /kit list          查看当前所有套装名称
 *
 * 7. : 当玩家在GUI中选择职业后:
 *    - 立即关闭GUI
 *    - 直到玩家死亡之前，无法再次选择职业
 */
public class KitBattle extends JavaPlugin implements Listener {

    // -------------------------------------
    // ========== 文件相关: spawn & playerdata ==========
    // -------------------------------------
    private File spawnFile;                    // Spawn.yml 文件对象
    private FileConfiguration spawnConfig;     // 读取/写入 Spawn.yml 的配置对象

    private File playerDataFile;               // playerdata.yml 文件对象
    private FileConfiguration playerDataConfig;// 读取/写入 playerdata.yml 的配置对象
    protected BattleManager battleManager;
    // -------------------------------------
    // ========== config.yml 相关(私有化成员变量) ==========
    // -------------------------------------
    private FileConfiguration config;          // 主配置文件对象
    private ShopItem shopManager;
    // -------------------------------------
    // ========== kit.yml 文件相关 ==========
    // -------------------------------------
    private File kitFile;
    private FileConfiguration kitConfig;
    // 添加 spawnConfig 的公共获取方法
    public FileConfiguration getSpawnConfig() {
        return this.spawnConfig;
    }
    
    // 添加 shopManager 的获取方法
    public ShopItem getShopManager() {
        return this.shopManager;
    }
    
    private GameManager gameManager; // 添加类变量
    // -------------------------------------
    // ========== 玩家当前是否已选择职业标记 ==========
    // -------------------------------------
    // 这里记录所有已经选择了职业、尚未死亡的玩家(使用玩家名称或UUID做标识都可以)
    protected Set<String> chosenKitPlayers = new HashSet<>();
    private Set<String> deathProcessed = new HashSet<>();
    private String modeName;  // 需要在插件启动时设置这个模式名称
    private Map<Player, String> playerTeamsMap = new HashMap<>();  // 玩家和他们队伍的映射

    // 添加getter方法
    public BattleManager getBattleManager() {
        return this.battleManager;
    }
    // 在类变量区添加
    private Map<Player, BukkitTask> deathTasks = new HashMap<>();
    /**
     * ==================================================
     *  插件启动时( onEnable() )
     * ==================================================
     */
    @Override
    public void onEnable() {
        // 确保配置目录和配置文件存在
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        
        // 初始化配置文件
        loadConfigFiles();
        
        // 先初始化商店管理器
        this.shopManager = new ShopItem(this);
        
        // 初始化战斗管理器，传入已初始化的插件实例和商店管理器
        this.battleManager = new BattleManager(this);
        
        // 初始化游戏管理器，传入已初始化的插件实例和商店管理器
        this.gameManager = new GameManager(this, "default", new HashMap<>());
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(this, this);
        
        // 输出加载成功信息
        getLogger().info("KitBattle 插件已成功加载!");
    }

    private void loadConfigFiles() {
        // 加载主配置文件
        saveDefaultConfig();
        this.config = getConfig();
        
        // 加载 kit.yml 文件
        this.kitFile = new File(getDataFolder(), "kit.yml");
        if (!kitFile.exists()) {
            try {
                kitFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.kitConfig = YamlConfiguration.loadConfiguration(kitFile);
        
        // 加载 Spawn.yml 文件
        this.spawnFile = new File(getDataFolder(), "Spawn.yml");
        if (!spawnFile.exists()) {
            try {
                spawnFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.spawnConfig = YamlConfiguration.loadConfiguration(spawnFile);
        
        // 加载 playerdata.yml 文件
        this.playerDataFile = new File(getDataFolder(), "playerdata.yml");
        if (!playerDataFile.exists()) {
            try {
                playerDataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (isKitItem(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "无法丢弃职业装备！");
        }
    }

    // 修改物品检查方法为检查Lore标记
    private boolean isKitItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.hasLore() && meta.getLore().contains(ChatColor.GRAY + "职业装备");
    }



    /**
     * ==================================================
     *  插件卸载时( onDisable() )
     * ==================================================
     */
    @Override
    public void onDisable() {
        // 先取消所有任务
        Bukkit.getScheduler().cancelTasks(this);

        // 再处理游戏结束（传递true表示插件正在卸载）
        if (gameManager != null) {
            gameManager.endGame("插件卸载", true);
        }

        // 最后保存数据
        saveConfig();
        saveSpawnConfig();
        savePlayerDataConfig();
        saveKitConfig();
        getLogger().info(ChatColor.RED + "============ KitBattle 插件已卸载 ============");
    }

    // -------------------------------------
    // ========== 保存 kit.yml ==========
    // -------------------------------------
    private void saveKitConfig() {
        try {
            kitConfig.save(kitFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    // -------------------------------------
    // ========== 保存 Spawn.yml ==========
    // -------------------------------------
    private void saveSpawnConfig() {
        try {
            spawnConfig.save(spawnFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // -------------------------------------
    // ========== 保存 playerdata.yml ==========
    // -------------------------------------
    private void savePlayerDataConfig() {
        try {
            playerDataConfig.save(playerDataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * ==================================================
     *  事件监听: 玩家首次进服, 初始化玩家数据
     * ==================================================
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        // 修改后（使用this引用当前插件实例）
        this.getBattleManager().getGameManager().getInGamePlayers().remove(player);
        WaitGame waitGame = this.getBattleManager().getWaitGameByPlayer(player);
        if (waitGame != null) {
            waitGame.leaveGame(player);
        }

        // 如果 playerdata.yml 中还没有这个玩家的信息，则初始化
        if (!playerDataConfig.contains(playerName)) {
            int initialCoins = config.getInt("initial-coins");
            int initialExp = config.getInt("initial-experience");
            int initialLevel = config.getInt("initial-level");

            playerDataConfig.set(playerName + ".coins", initialCoins);
            playerDataConfig.set(playerName + ".experience", initialExp);
            playerDataConfig.set(playerName + ".level", initialLevel);
            savePlayerDataConfig();

            // 提示玩家
            player.sendMessage(ChatColor.YELLOW + "==============================");
            player.sendMessage(ChatColor.GREEN + "欢迎首次加入KitBattle，已为你初始化数据!");
            player.sendMessage(ChatColor.GREEN + "金币: " + initialCoins
                    + " | 经验值: " + initialExp
                    + " | 等级: " + initialLevel);
            player.sendMessage(ChatColor.YELLOW + "==============================");
        }
    }

    private boolean isPlayerInGame(Player player) {
        // 通过BattleManager检查玩家是否在任意活动游戏中
        return battleManager.getActiveGames().values().stream()
                .anyMatch(waitGame -> waitGame.getPlayerTeams().containsKey(player));
    }

    // 替换原有的
    @EventHandler
    public void onPlayerDeathFix(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Player deadPlayer = event.getEntity();
        event.setDeathMessage("");

        // 如果玩家在游戏中，直接返回（由GameManager处理）
        if (isPlayerInGame(deadPlayer)) {
            return;
        }
        // 如果该玩家的死亡奖励已经处理过，则直接返回
        if (deathProcessed.contains(deadPlayer.getName())) {
            return;
        }
        if (battleManager.getGameManager().getInGamePlayers().contains(deadPlayer)) {
            return;
        }

        // 立即复活逻辑
        if (player.getHealth() <= 0) {
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setGameMode(GameMode.SPECTATOR);
            player.sendTitle(ChatColor.RED + "你已阵亡", "5秒后复活");

            // 创建复活任务
            BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {
                player.setGameMode(GameMode.ADVENTURE);
                player.setHealth(20);
                player.setFoodLevel(20);

                // 根据游戏状态传送
                if (battleManager.getGameManager().getInGamePlayers().contains(player)) {
                    battleManager.getGameManager().handleGameRespawn(player);
                } else {
                    Location lobby = getLobbyLocation();
                    if (lobby != null) player.teleport(lobby);
                }
                deathTasks.remove(player);
            }, 100L);
            deathTasks.put(player, task);
        }
        // 处理击杀奖励（仅给在线玩家）
        Player killer = deadPlayer.getKiller() instanceof Player ? (Player) deadPlayer.getKiller() : null;
        if (killer != null) {
            int killCoins = config.getInt("kill-coins");
            int killExp = config.getInt("kill-exp");
            modifyPlayerVariable(killer, "coins", killCoins);
            modifyPlayerVariable(killer, "experience", killExp);
            killer.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 1));
            killer.sendMessage(ChatColor.GOLD + "[击杀奖励]" + ChatColor.GREEN +
                    " 你获得了 " + killCoins + " 金币, " + killExp + " 经验, 并得到1个金苹果!");
        }

        // 死亡玩家也能得到奖励
        int deathCoins = config.getInt("death-coins");
        int deathExp = config.getInt("death-exp");
        modifyPlayerVariable(deadPlayer, "coins", deathCoins);
        modifyPlayerVariable(deadPlayer, "experience", deathExp);

        deadPlayer.sendMessage(ChatColor.RED + "[死亡奖励]"
                + ChatColor.GREEN + " 你获得了 " + deathCoins + " 金币, " + deathExp + " 经验.");


        // 清除选定职业
        chosenKitPlayers.remove(deadPlayer.getName());
    }






    // -------------------------------------
    // 获取存储在配置文件中的 Location 对象
    // -------------------------------------
    private Location getLocationFromConfig(FileConfiguration config, String path) {
        String worldName = config.getString(path + ".world");
        double x = config.getDouble(path + ".x");
        double y = config.getDouble(path + ".y");
        double z = config.getDouble(path + ".z");
        float yaw = (float) config.getDouble(path + ".yaw");
        float pitch = (float) config.getDouble(path + ".pitch");

        return new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
    }

    // ========================== battle help 指令 ==========================
    private void showBattleHelp(Player player, int page) {
        int maxPage = 1;  // 我们这里简单的设置为1页
        player.sendMessage(ChatColor.YELLOW + "===== Battle帮助 - 第 " + page + " 页 =====");
        player.sendMessage(ChatColor.GOLD + "/battle addmode <模式名>" + ChatColor.WHITE + " - 创建新模式");
        player.sendMessage(ChatColor.GOLD + "/battle minplayers <模式名> <人数>" + ChatColor.WHITE + " - 设置模式最小人数");
        player.sendMessage(ChatColor.GOLD + "/battle maxplayers <模式名> <人数>" + ChatColor.WHITE + " - 设置模式最大人数");
        player.sendMessage(ChatColor.GOLD + "/battle time <模式名> <时间>" + ChatColor.WHITE + " - 设置模式时间");
        player.sendMessage(ChatColor.GOLD + "/battle list" + ChatColor.WHITE + " - 查看所有模式");
        player.sendMessage(ChatColor.GOLD + "/battle team <teamNumber|TeamSize> <模式名> <数值>" + ChatColor.WHITE + " - 设置队伍信息");
        player.sendMessage(ChatColor.GOLD + "/battle spawn <模式名> <队伍ID名称>" + ChatColor.WHITE + "设置一个队伍的出生点");
        player.sendMessage(ChatColor.GOLD + "/battle check <模式名>" + ChatColor.WHITE + " - 检查模式设置");
        player.sendMessage(ChatColor.GOLD + "/battle enable <模式名>" + ChatColor.WHITE + " - 启用游戏模式");
        player.sendMessage(ChatColor.GOLD + "/battle disable <模式名>" + ChatColor.WHITE + " - 禁用游戏模式");
        player.sendMessage(ChatColor.GOLD + "/battle NPC <模式名> <NPC名称> <NPC类型>" + ChatColor.WHITE + " - 生成一个NPC");
        player.sendMessage(ChatColor.GOLD + "/battle Gentor <模式名> <资源点名称> <资源类型>" + ChatColor.WHITE + " - 生成一个资源点");
        player.sendMessage(ChatColor.YELLOW + "==============================");
        player.sendMessage(ChatColor.YELLOW + "当前页 " + page + "/" + maxPage);
    }

    /**
     * ==================================================
     *  事件监听: 玩家复活后传送至lobby
     * ==================================================
     */
    // 事件监听: 玩家受到致命伤害时清空背包并返回大厅
    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        // 新增游戏状态检查
        if (battleManager.getGameManager().getInGamePlayers().contains(player)) {
            return;
        }

        if (player.getHealth() - event.getFinalDamage() <= 0) {
            // 延迟1 tick处理
            Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
                // 清空背包和装备
                player.getInventory().clear();
                player.getInventory().setArmorContents(new ItemStack[4]);

                // 传送到大厅
                Location lobby = getLobbyLocation();
                if (lobby != null) {
                    player.teleport(lobby);
                }

                // 设置重生点
                player.setHealth(player.getMaxHealth());
                player.setFoodLevel(20);
            }, 1L);
        }
    }

    // 获取大厅位置的方法
    public Location getLobbyLocation() {
        /*============================================
         * 功能：从 Spawn.yml 中获取大厅位置
         * 返回值：大厅坐标（若未设置返回null）
         *============================================*/
        if (spawnConfig.contains("lobby")) {
            String worldName = spawnConfig.getString("lobby.world");
            // 检查世界是否加载
            if (Bukkit.getWorld(worldName) == null) {
                getLogger().warning("大厅所在世界 " + worldName + " 未加载！");
                return null;
            }
            return new Location(
                    Bukkit.getWorld(worldName),
                    spawnConfig.getDouble("lobby.x"),
                    spawnConfig.getDouble("lobby.y"),
                    spawnConfig.getDouble("lobby.z"),
                    (float) spawnConfig.getDouble("lobby.yaw"),
                    (float) spawnConfig.getDouble("lobby.pitch")
            );
        }
        return null;
    }

    // 事件监听: 玩家复活后传送至lobby
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // 跳过游戏中的玩家处理 (§新增)
        if (battleManager.getGameManager().getInGamePlayers().contains(player)) {
            return;
        }
        // 如果Spawn.yml中设置了lobby，就让玩家复活后直接去lobby
        if (spawnConfig.contains("lobby")) {
            String worldName = spawnConfig.getString("lobby.world");
            double x = spawnConfig.getDouble("lobby.x");
            double y = spawnConfig.getDouble("lobby.y");
            double z = spawnConfig.getDouble("lobby.z");
            float yaw = (float) spawnConfig.getDouble("lobby.yaw");
            float pitch = (float) spawnConfig.getDouble("lobby.pitch");

            Location lobbyLoc = new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
            event.setRespawnLocation(lobbyLoc);
        }
        // 清除死亡标记，允许下一次死亡触发奖励
        deathProcessed.remove(player.getName());
    }

    /**
     * ==================================================
     *  指令处理
     * ==================================================
     */
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // 仅允许玩家执行
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "只有玩家才能执行此命令!");
            return true;
        }
        if (label.equalsIgnoreCase("battle") || label.equalsIgnoreCase("bt")) {
            if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
                int page = args.length == 2 ? Integer.parseInt(args[1]) : 1;
                showBattleHelp((Player) sender, page);
                return true;
            }
            return battleManager.onCommand(sender, cmd, label, args);  // 将指令交给BattleManager处理
        }
        Player player = (Player) sender;

        // 如果不输入任何子命令，默认显示帮助
        if (args.length == 0) {
            showHelp(player, 1);
            return true;
        }

        switch (args[0].toLowerCase()) {

            // -------------------------
            // /kit set spawn <battleName>
            // -------------------------
            case "set":
                if (args.length >= 3 && args[1].equalsIgnoreCase("spawn")) {
                    String battleName = args[2];
                    setSpawn(player, battleName);
                    return true;
                }
                break;

            // -------------------------
            // /kit tp <battleName>
            // -------------------------
            case "tp":
                if (args.length == 2) {
                    String battleName = args[1];
                    teleportToSpawn(player, battleName);
                    return true;
                }
                break;

            // -------------------------
            // /kit setlobby
            // -------------------------
            case "setlobby":
                if (args.length == 1) {
                    setLobby(player);
                    return true;
                }
                break;

            // -------------------------
            // /kit rtp
            // -------------------------
            case "rtp":
                if (args.length == 1) {
                    randomTeleport(player);
                    return true;
                }
                break;

            // -------------------------
            // /kit spawn list
            // -------------------------
            case "spawn":
                if (args.length == 2 && args[1].equalsIgnoreCase("list")) {
                    listSpawns(player);
                    return true;
                }
                break;

            // -------------------------
            // /kit help <页码>
            // -------------------------
            case "help":
                if (args.length == 2) {
                    try {
                        int page = Integer.parseInt(args[1]);
                        showHelp(player, page);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "页码必须是一个数字！");
                        showHelp(player, 1);
                    }
                } else {
                    showHelp(player, 1);
                }
                return true;

            // -------------------------
            // /kit add <玩家名> <金币|经验值|等级> <数量>
            // -------------------------
            case "add":
                if (args.length == 4) {
                    String targetName = args[1];
                    String varType = args[2];
                    int amount;
                    try {
                        amount = Integer.parseInt(args[3]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "数量必须是数字！");
                        return true;
                    }

                    Player target = Bukkit.getPlayerExact(targetName);
                    if (target != null) {
                        modifyPlayerVariable(target, varType, amount);
                        player.sendMessage(ChatColor.GREEN + "已成功为玩家 " + targetName
                                + " 增加 " + amount + " 点 " + varType + "。");
                    } else {
                        player.sendMessage(ChatColor.RED + "玩家 " + targetName + " 未在线！");
                    }
                    return true;
                }
                break;

            // -------------------------
            // /kit create <name>
            // -------------------------
            case "create":
                if (args.length == 2) {
                    createKit(player, args[1]);
                    return true;
                }
                break;

            // -------------------------
            // /kit edit <name>
            // -------------------------
            case "edit":
                if (args.length == 2) {
                    editKit(player, args[1]);
                    return true;
                }
                break;

            // -------------------------
            // /kit save <name>
            // -------------------------
            case "save":
                if (args.length == 2) {
                    saveKit(player, args[1]);
                    return true;
                }
                break;

            // -------------------------
            // /kit gui
            // -------------------------
            case "gui":
                if (args.length == 1) {
                    // 如果玩家已经选择了职业，直到他死亡之前不能再选
                    if (chosenKitPlayers.contains(player.getName())) {
                        player.sendMessage(ChatColor.RED + "你已选择职业，死亡前无法再次选择！");
                        return true;
                    }
                    openKitGui(player);
                    return true;
                }
                break;
            case "battle":
                return battleManager.onCommand(sender, cmd, label, args);
            case "shop":
                if (args.length == 3) {
                    boolean enable = args[1].equalsIgnoreCase("enable");
                    String target = args[2];
                    shopManager.toggleShopItem(target, enable);
                    player.sendMessage(ChatColor.GREEN + "职业 " + target + " 已" + (enable ? "启用" : "禁用"));
                    return true;
                }
                break;
            // -------------------------
            // 其他未知指令
            // -------------------------
            default:
                player.sendMessage(ChatColor.RED + "未知子命令: " + args[0]);
                showHelp(player, 1);
                return true;
        }

        // 如果指令未被正确执行，默认显示帮助
        showHelp(player, 1);
        return true;
    }

    // -------------------------------------
    // ========== 设置传送点 (/kit set spawn <battleName>) ==========
    // -------------------------------------
    private void setSpawn(Player player, String battleName) {
        Location loc = player.getLocation();
        String path = "spawns." + battleName + ".";

        spawnConfig.set(path + "world", loc.getWorld().getName());
        spawnConfig.set(path + "x", loc.getX());
        spawnConfig.set(path + "y", loc.getY());
        spawnConfig.set(path + "z", loc.getZ());
        spawnConfig.set(path + "yaw", loc.getYaw());
        spawnConfig.set(path + "pitch", loc.getPitch());

        saveSpawnConfig();
        player.sendMessage(ChatColor.GREEN + "传送点 " + ChatColor.YELLOW + battleName
                + ChatColor.GREEN + " 已设置!");
    }

    // -------------------------------------
    // ========== 传送到指定传送点 (/kit tp <battleName>) ==========
    // -------------------------------------
    private void teleportToSpawn(Player player, String battleName) {
        String path = "spawns." + battleName;
        if (spawnConfig.contains(path)) {
            String worldName = spawnConfig.getString(path + ".world");
            double x = spawnConfig.getDouble(path + ".x");
            double y = spawnConfig.getDouble(path + ".y");
            double z = spawnConfig.getDouble(path + ".z");
            float yaw = (float) spawnConfig.getDouble(path + ".yaw");
            float pitch = (float) spawnConfig.getDouble(path + ".pitch");

            Location loc = new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
            player.teleport(loc);
            player.sendMessage(ChatColor.GREEN + "你已传送到 "
                    + ChatColor.YELLOW + battleName
                    + ChatColor.GREEN + "!");
        } else {
            player.sendMessage(ChatColor.RED + "未找到指定的传送点 " + battleName + "!");
        }
    }

    // -------------------------------------
    // ========== 设置游戏大厅 (/kit setlobby) ==========
    // -------------------------------------
    private void setLobby(Player player) {
        Location loc = player.getLocation();
        String path = "lobby.";

        spawnConfig.set(path + "world", loc.getWorld().getName());
        spawnConfig.set(path + "x", loc.getX());
        spawnConfig.set(path + "y", loc.getY());
        spawnConfig.set(path + "z", loc.getZ());
        spawnConfig.set(path + "yaw", loc.getYaw());
        spawnConfig.set(path + "pitch", loc.getPitch());

        saveSpawnConfig();
        player.sendMessage(ChatColor.GREEN + "游戏大厅(lobby)位置已设置!");
    }

    // -------------------------------------
    // ========== 随机传送 (/kit rtp) ==========
    // -------------------------------------
    private void randomTeleport(Player player) {
        if (!spawnConfig.contains("spawns")) {
            player.sendMessage(ChatColor.RED + "没有可用的传送点！");
            return;
        }
        Set<String> spawnNames = spawnConfig.getConfigurationSection("spawns").getKeys(false);
        if (spawnNames.isEmpty()) {
            player.sendMessage(ChatColor.RED + "没有可用的传送点！");
            return;
        }

        List<String> spawnsList = new ArrayList<>(spawnNames);
        int randomIndex = new Random().nextInt(spawnsList.size());
        String randomSpawn = spawnsList.get(randomIndex);

        teleportToSpawn(player, randomSpawn);
    }

    // -------------------------------------
    // ========== 查看所有传送点 (/kit spawn list) ==========
    // -------------------------------------
    private void listSpawns(Player player) {
        if (spawnConfig.contains("spawns")) {
            Set<String> spawnNames = spawnConfig.getConfigurationSection("spawns").getKeys(false);
            if (!spawnNames.isEmpty()) {
                player.sendMessage(ChatColor.YELLOW + "===== 所有传送点列表 =====");
                for (String battleName : spawnNames) {
                    double x = spawnConfig.getDouble("spawns." + battleName + ".x");
                    double y = spawnConfig.getDouble("spawns." + battleName + ".y");
                    double z = spawnConfig.getDouble("spawns." + battleName + ".z");
                    player.sendMessage(ChatColor.AQUA + "名称: "
                            + ChatColor.GREEN + battleName
                            + ChatColor.WHITE + " | 坐标: "
                            + ChatColor.GOLD + "X:" + x + " Y:" + y + " Z:" + z);
                }
                player.sendMessage(ChatColor.YELLOW + "========================");
            } else {
                player.sendMessage(ChatColor.RED + "暂无任何传送点！");
            }
        } else {
            player.sendMessage(ChatColor.RED + "没有任何传送点！");
        }
    }

    // -------------------------------------
    // ========== 显示帮助信息 (/kit help [页码]) ==========
    // -------------------------------------
    private void showHelp(Player player, int page) {
        player.sendMessage(ChatColor.YELLOW + "===== KitBattle 帮助 - 第 " + page + " 页 =====");
        player.sendMessage(ChatColor.GOLD + "/kit set spawn <battleName> "
                + ChatColor.WHITE + "- 设置传送点");
        player.sendMessage(ChatColor.GOLD + "/kit tp <battleName> "
                + ChatColor.WHITE + "- 传送到指定传送点");
        player.sendMessage(ChatColor.GOLD + "/kit setlobby "
                + ChatColor.WHITE + "- 设置游戏大厅");
        player.sendMessage(ChatColor.GOLD + "/kit rtp "
                + ChatColor.WHITE + "- 随机传送到一个传送点");
        player.sendMessage(ChatColor.GOLD + "/kit spawn list "
                + ChatColor.WHITE + "- 查看所有传送点");
        player.sendMessage(ChatColor.GOLD + "/kit help <页码> "
                + ChatColor.WHITE + "- 查看帮助信息");
        player.sendMessage(ChatColor.GOLD + "/kit add <玩家名> <金币|经验值|等级> <数量> "
                + ChatColor.WHITE + "- 增加玩家变量");
        player.sendMessage(ChatColor.GOLD + "/kit create <name> "
                + ChatColor.WHITE + "- 创建职业套装");
        player.sendMessage(ChatColor.GOLD + "/kit edit <name> "
                + ChatColor.WHITE + "- 编辑职业套装");
        player.sendMessage(ChatColor.GOLD + "/kit save <name> "
                + ChatColor.WHITE + "- 保存职业套装");
        player.sendMessage(ChatColor.GOLD + "/kit gui "
                + ChatColor.WHITE + "- 打开职业选择GUI");
        player.sendMessage(ChatColor.YELLOW + "==============================");
    }

    // -------------------------------------
    // ========== 修改玩家变量(coins, experience, level等) ==========
    // -------------------------------------
    private void modifyPlayerVariable(Player player, String varType, int amount) {
        String playerName = player.getName();

        // 若玩家数据不存在则先初始化
        if (!playerDataConfig.contains(playerName)) {
            int initialCoins = config.getInt("initial-coins");
            int initialExp = config.getInt("initial-experience");
            int initialLevel = config.getInt("initial-level");

            playerDataConfig.set(playerName + ".coins", initialCoins);
            playerDataConfig.set(playerName + ".experience", initialExp);
            playerDataConfig.set(playerName + ".level", initialLevel);
        }

        // 获取当前值并增加
        int currentValue = playerDataConfig.getInt(playerName + "." + varType, 0);
        int newValue = currentValue + amount;
        playerDataConfig.set(playerName + "." + varType, newValue);

        // 如果修改的是经验值，需要判断是否可以升级
        if (varType.equalsIgnoreCase("experience")) {
            checkLevelUp(player, newValue);
        }

        savePlayerDataConfig();
    }

    // -------------------------------------
    // ========== 检查并处理玩家升级逻辑 ==========
    // -------------------------------------
    private void checkLevelUp(Player player, int newExpValue) {
        String playerName = player.getName();
        int currentLevel = playerDataConfig.getInt(playerName + ".level", 1);

        // 根据当前等级从 config.yml 获取需要的经验值
        String levelPath = "levels." + currentLevel + ".experienceRequired";
        int requiredExp = config.getInt(levelPath, Integer.MAX_VALUE);

        if (newExpValue >= requiredExp) {
            int nextLevel = currentLevel + 1;
            playerDataConfig.set(playerName + ".level", nextLevel);
            // 升级后经验清零
            playerDataConfig.set(playerName + ".experience", 0);
            savePlayerDataConfig();

            player.sendMessage(ChatColor.YELLOW + "==============================");
            player.sendMessage(ChatColor.GREEN + "恭喜你，成功升到了等级 " + nextLevel + " ！");
            player.sendMessage(ChatColor.YELLOW + "==============================");
        }
    }

    // -------------------------------------
    // ========== 创建职业套装 (/kit create <name>) ==========
    // -------------------------------------
    private void createKit(Player player, String kitName) {
        String path = "kits." + kitName;
        if (kitConfig.contains(path)) {
            player.sendMessage(ChatColor.RED + "该职业已存在！");
            return;
        }

        // 保存当前玩家背包(仅作为初始存储示例)
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                items.add(item.clone());
            }
        }
        kitConfig.set(path, items);
        saveKitConfig();
        player.sendMessage(ChatColor.GREEN + "职业 " + kitName + " 已创建！");
    }

    // -------------------------------------
    // ========== 编辑职业套装 (/kit edit <name>) ==========
    // -------------------------------------
    private void editKit(Player player, String kitName) {
        String path = "kits." + kitName;
        if (!kitConfig.contains(path)) {
            player.sendMessage(ChatColor.RED + "该职业不存在！");
            return;
        }

        // 将玩家背包清空并替换为套装物品
        player.getInventory().clear();
        List<ItemStack> items = (List<ItemStack>) kitConfig.getList(path);
        if (items != null) {
            for (ItemStack item : items) {
                player.getInventory().addItem(item);
            }
        }
        player.sendMessage(ChatColor.GREEN + "你现在正处于职业编辑模式，背包已替换为 " + kitName + " 套装！");
    }

    // -------------------------------------
    // ========== 保存职业套装 (/kit save <name>) ==========
    // -------------------------------------
    // 修改保存套装的方法
    private void saveKit(Player player, String kitName) {
        String path = "kits." + kitName;
        List<Map<String, Object>> serializedItems = new ArrayList<>();

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                serializedItems.add(item.serialize());
            }
        }

        kitConfig.set(path, serializedItems);
        saveKitConfig();
        player.sendMessage(ChatColor.GREEN + "职业 " + kitName + " 已保存！");
    }

    // -------------------------------------
    // ========== 打开职业选择GUI (/kit gui) ==========
    // -------------------------------------
    private void openKitGui(Player player) {
        // 打开一个9格的GUI用于演示
        Inventory gui = Bukkit.createInventory(null, 9, "选择职业");

        // 如果没有职业，给出提示
        if (!kitConfig.contains("kits")) {
            player.sendMessage(ChatColor.RED + "暂无职业可供选择！");
            return;
        }
        Set<String> kitNames = kitConfig.getConfigurationSection("kits").getKeys(false);
        if (kitNames.isEmpty()) {
            player.sendMessage(ChatColor.RED + "暂无职业可供选择！");
            return;
        }

        // 将每一个职业以物品形式展示
        for (String kitName : kitNames) {
            ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW + kitName);
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "点击选择此职业");
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            gui.addItem(item);
        }
        player.openInventory(gui);
    }

    /**
     * ==================================================
     *  事件监听: 处理在GUI中点击职业物品
     * ==================================================
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getInventory();

        // 检查是否是我们打开的"选择职业"GUI
        if (inventory.getTitle().equals("选择职业")) {
            event.setCancelled(true); // 阻止物品被拿走

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            // 获取职业名称(去除颜色)
            String kitName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

            // 从 kit.yml 中加载该职业套装
            String path = "kits." + kitName;
            if (kitConfig.contains(path)) {
                // 清空玩家背包并为玩家添加该套装
                player.getInventory().clear();
                List<ItemStack> items = (List<ItemStack>) kitConfig.getList(path);
                if (items != null) {
                    for (ItemStack item : items) {
                        player.getInventory().addItem(item);
                    }
                }
                player.sendMessage(ChatColor.GREEN + "你已选择职业: " + kitName);

                // ========== 关键: 关闭GUI & 标记玩家不能再次选职业 ==========
                player.closeInventory();
                chosenKitPlayers.add(player.getName());
            } else {
                player.sendMessage(ChatColor.RED + "该职业不存在!");
            }
        }
        // 检查是否是我们打开的"选择你的队伍"GUI
        if (inventory.getTitle().equals("选择你的队伍")) {
            event.setCancelled(true); // 阻止物品被拿走
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            // 获取队伍名称（去除颜色）
            String teamName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

            // 通过 BattleManager 获取玩家所在的 WaitGame 实例，并调用选择队伍方法
            WaitGame waitGame = battleManager.getWaitGameByPlayer(player);
            if (waitGame != null) {
                waitGame.selectTeam(player, teamName);
                player.closeInventory();  // 关闭GUI
            } else {
                player.sendMessage(ChatColor.RED + "你没有加入任何等待中的游戏，无法选择队伍！");
            }
        }
    }
    // ========================== Tab 补全 ==========================
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return null;

        Player player = (Player) sender;
        List<String> suggestions = new ArrayList<>();

        // 针对 kit 指令的 Tab 补全
        if (label.equalsIgnoreCase("kit")) {
            if (args.length == 1) {
                suggestions.add("set");
                suggestions.add("tp");
                suggestions.add("setlobby");
                suggestions.add("rtp");
                suggestions.add("spawn");
                suggestions.add("help");
                suggestions.add("add");
                suggestions.add("create");
                suggestions.add("edit");
                suggestions.add("save");
                suggestions.add("gui");
            } else if (args[0].equalsIgnoreCase("spawn") && args.length == 2) {
                suggestions.add("list");
            }// 在tab补全逻辑中添加shop建议
            if (args[0].equalsIgnoreCase("shop")) {
                if (args.length == 2) {
                    suggestions.add("enable");
                    suggestions.add("disable");
                } else if (args.length == 3) {
                    shopManager.PRESET_KITS.keySet().forEach(k -> suggestions.add(String.valueOf(k)));
                    if (kitConfig.contains("kits")) {
                        suggestions.addAll(kitConfig.getConfigurationSection("kits").getKeys(false));
                    }
                }
            }
        }

        // 针对 battle 指令的 Tab 补全
        if (label.equalsIgnoreCase("battle") || label.equalsIgnoreCase("bt")) {
            if (args.length == 1) {
                suggestions.add("addmode");
                suggestions.add("minplayers");
                suggestions.add("maxplayers");
                suggestions.add("time");
                suggestions.add("team");
                suggestions.add("list");
                suggestions.add("help");
                suggestions.add("Gentor");
                suggestions.add("spawn");
                suggestions.add("join");
                suggestions.add("enable");
                suggestions.add("disable");
                suggestions.add("NPC");
            }else if (args[0].equalsIgnoreCase("team") && args.length == 2) {
                suggestions.add("teamNumber");
                suggestions.add("teamSize");
            }
            else if (args[0].equalsIgnoreCase("minplayers") || args[0].equalsIgnoreCase("maxplayers") || args[0].equalsIgnoreCase("time")) {
                if (args.length == 3) {
                    // Here we can add predefined player counts or time limits if needed.
                    suggestions.add("10");
                    suggestions.add("20");
                    suggestions.add("30");
                }
            }
        }
        return suggestions;
    }
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        // 如果放置的物品是床
        if (event.getItemInHand().getType() == Material.BED) {
            ItemMeta meta = event.getItemInHand().getItemMeta();
            // 如果该床有自定义显示名称且为"选择队伍"
            if (meta != null && meta.hasDisplayName() &&
                    meta.getDisplayName().equals(ChatColor.AQUA + "选择队伍")) {
                event.setCancelled(true);  // 取消放置
                event.getPlayer().sendMessage(ChatColor.RED + "该床不能被放置！");
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // 判断右键操作（包括右键空气和右键方块）
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Player player = event.getPlayer();
            ItemStack item = player.getItemInHand();
            if (item != null && item.getType() == Material.BED) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.hasDisplayName() &&
                        meta.getDisplayName().equals(ChatColor.AQUA + "选择队伍")) {
                    event.setCancelled(true);  // 取消默认操作

                    // ================================================
                    // 修复BUG：根据玩家获取实际的 WaitGame 实例并打开队伍选择GUI
                    // ================================================
                    WaitGame waitGame = battleManager.getWaitGameByPlayer(player);
                    if (waitGame != null) {
                        waitGame.openTeamSelectionGUI(player);
                    } else {
                        player.sendMessage(ChatColor.RED + "你当前没有加入任何等待中的游戏，无法选择队伍！");
                    }
                }
            }
            if (item.getType() == Material.ARROW) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.getDisplayName().equals(ChatColor.RED + "退出游戏")) {
                    WaitGame waitGame = battleManager.getWaitGameByPlayer(player);
                    if (waitGame != null) {
                        waitGame.leaveGame(player);
                    }
                }
            }
        }

    }

}
