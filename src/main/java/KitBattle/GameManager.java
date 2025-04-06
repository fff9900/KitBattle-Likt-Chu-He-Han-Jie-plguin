package KitBattle;

import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

// 导入Citizens API相关类
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;


public class GameManager implements Listener {

    // -------------------------------
    // 【成员变量】
    // -------------------------------
    private KitBattle plugin;
    private String modeName;                          // 当前游戏模式名称，对应 Game 文件夹下的 <modeName>.yml 文件
    private Map<Player, String> playerTeams;          // 玩家所属队伍映射（键：Player，值：队伍ID）
    private final Set<Player> inGamePlayers = new HashSet<>(); // 当前游戏中的玩家集合

    // 用于任务NPC提交的CD管理（记录玩家最后一次提交时间）
    private Map<String, Long> taskNpcCooldown = new HashMap<>();
    // ------------------------------
// 新增：记录各队积分和游戏参与玩家集合
// ------------------------------
    private Map<String, Integer> teamPoints = new HashMap<>();  // 记录各队已提交的积分
    private Set<Player> gamePlayers = new HashSet<>();            // 记录当前参加游戏的所有玩家
    // 在GameManager类中添加成员变量
    private BukkitRunnable gentorTask;
    // 在GameManager类中新增以下成员变量
    private ShopItem shopManager;
    private Map<Inventory, String> shopInventoryMap = new HashMap<>();
    // ==== 新增访问方法 ====
    public Set<Player> getInGamePlayers() {
        return this.inGamePlayers;
    }
    // -------------------------------
    // 【构造方法】
    // -------------------------------
    public GameManager(KitBattle plugin, String modeName, Map<Player, String> playerTeams) {
        this.plugin = plugin;
        this.modeName = modeName;
        this.playerTeams = playerTeams;
        
        // 判断plugin是否已初始化shopManager，如果已初始化则使用插件中的实例，否则创建新实例
        if (plugin.getShopManager() != null) {
            this.shopManager = plugin.getShopManager();
        } else {
            this.shopManager = new ShopItem(plugin); // 初始化商店管理器
        }
        
        // 注册事件监听器
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    // 在GameManager.java中添加
    public String getPlayerTeam(Player player) {
        return playerTeams.get(player);
    }
    public List<Player> getTeamPlayers(String teamName) {
        return playerTeams.entrySet().stream()
                .filter(entry -> entry.getValue().equals(teamName))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    // 在 GameManager.java 中添加方法
    public void handleGameLeave(Player player) {
        inGamePlayers.remove(player);
        player.getInventory().clear();

        // 新增传送保护逻辑
        new BukkitRunnable() {
            @Override
            public void run() {
                Location lobby = getLobbyLocation();
                if (lobby != null && player.isOnline()) {
                    player.teleport(lobby);
                }
            }
        }.runTaskLater(plugin, 1L);

        // 检查游戏是否结束
        if (inGamePlayers.isEmpty()) {
            endGame("人数不足，游戏结束！",false);
        }
    }

    // -------------------------------
    // 【游戏开始逻辑】
    // -------------------------------
    /**
     * ==================================================
     * 开始游戏方法
     * 1. 根据玩家选择的队伍，从模式配置中读取队伍出生点，并传送玩家
     * 2. 启动Gentor生产点物品生成任务
     * 3. 生成配置中的NPC（任务NPC或商人NPC）
     * ==================================================
     */
    public void startGame() {
        plugin.getServer().broadcastMessage(ChatColor.GREEN + "================================");
        plugin.getServer().broadcastMessage(ChatColor.GREEN + "游戏模式 " + ChatColor.YELLOW + modeName + ChatColor.GREEN + " 开始!");
        plugin.getServer().broadcastMessage(ChatColor.GREEN + "================================");

        inGamePlayers.addAll(playerTeams.keySet());

        File modeFile = new File(plugin.getDataFolder() + File.separator + "Game", modeName + ".yml");
        if (!modeFile.exists()) {
            plugin.getLogger().severe(ChatColor.RED + "模式配置文件 " + modeName + ".yml 不存在！");
            return;
        }
        FileConfiguration modeConfig = YamlConfiguration.loadConfiguration(modeFile);

        for (Player player : playerTeams.keySet()) {
            String team = playerTeams.get(player);
            if (team.startsWith(ChatColor.stripColor("队伍"))) {
                team = "Team" + team.substring(2);
            }
            String path = "team.spawns." + team;
            if (modeConfig.contains(path)) {
                String worldName = modeConfig.getString(path + ".world");
                double x = modeConfig.getDouble(path + ".x");
                double y = modeConfig.getDouble(path + ".y");
                double z = modeConfig.getDouble(path + ".z");
                float yaw = (float) modeConfig.getDouble(path + ".yaw");
                float pitch = (float) modeConfig.getDouble(path + ".pitch");
                Location spawnLocation = new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
                player.teleport(spawnLocation);
                player.sendMessage(ChatColor.GREEN + "你已传送到所属队伍 " + ChatColor.YELLOW + team + ChatColor.GREEN + " 的出生点！");
            } else {
                player.sendMessage(ChatColor.RED + "队伍 " + team + " 的出生点未设置！");
            }
        }
        // 新增代码：清除玩家物品栏中的床
        for (Player player : playerTeams.keySet()) {
            ItemStack[] contents = player.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (item != null && item.getType() == Material.BED) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null && meta.getDisplayName() != null &&
                            meta.getDisplayName().equals(ChatColor.AQUA + "选择队伍")) {
                        player.getInventory().setItem(i, null); // 移除床
                    }
                }
            }
        }

        // 在 startGame() 方法中添加清除逻辑
        for (Player p : inGamePlayers) {
            p.getInventory().remove(Material.ARROW); // 清除所有箭矢

            // 清除特定名称的箭矢
            for (ItemStack item : p.getInventory()) {
                if (item != null && item.getType() == Material.ARROW) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta.getDisplayName().equals(ChatColor.RED + "退出游戏")) {
                        p.getInventory().remove(item);
                    }
                }
            }
        }

        startGentorTask(modeConfig);
        spawnConfiguredNPCs(modeConfig);
    }

    // -------------------------------
    // 【Gentor生产点相关】
    // -------------------------------
    /**
     * ==================================================
     * 根据modeConfig中配置的Gentor生产点定时生成物品
     * 配置格式示例：
     *   gentors:
     *     gentor1:
     *       world: world
     *       x: 100.5
     *       y: 64
     *       z: 200.5
     *       yaw: 0.0
     *       pitch: 0.0
     *       type: 铁
     * ==================================================
     */
    private void startGentorTask(FileConfiguration modeConfig) {
        if (!modeConfig.contains("gentors")) {
            plugin.getLogger().info(ChatColor.YELLOW + "当前模式未设置任何Gentor生产点！");
            return;
        }
        this.gentorTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (String gentorName : modeConfig.getConfigurationSection("gentors").getKeys(false)) {
                    String path = "gentors." + gentorName;
                    String worldName = modeConfig.getString(path + ".world");
                    double x = modeConfig.getDouble(path + ".x");
                    double y = modeConfig.getDouble(path + ".y");
                    double z = modeConfig.getDouble(path + ".z");
                    Location loc = new Location(Bukkit.getWorld(worldName), x, y, z);
                    String type = modeConfig.getString(path + ".type");
                    Material dropMaterial = null;
                    if (type != null) {
                        if (type.equals("铁")) {
                            dropMaterial = Material.IRON_INGOT;
                        } else if (type.equals("金")) {
                            dropMaterial = Material.GOLD_INGOT;
                        } else if (type.equals("钻石")) {
                            dropMaterial = Material.DIAMOND;
                        }
                    }
                    if (dropMaterial != null) {
                        loc.getWorld().dropItemNaturally(loc, new ItemStack(dropMaterial, 1));
                        loc.getWorld().playEffect(loc, org.bukkit.Effect.MOBSPAWNER_FLAMES, 0);
                    }
                }
            }
        };gentorTask.runTaskTimer(plugin, 0L, 200L);
    }



    /**
     * ==================================================
     * 管理员指令处理：设置Gentor生产点
     * 指令格式: /battle Gentor <GentorName> <铁|金|钻石>
     * 将当前玩家位置保存到模式配置中
     * ==================================================
     */
    public void handleGentorCommand(Player player, String modeName, String gentorName, String type) {
        // 检查游戏模式文件是否存在

        File modeFile = new File(plugin.getDataFolder() + File.separator + "Game", modeName + ".yml");
        if (!modeFile.exists()) {
            player.sendMessage(ChatColor.RED + "该模式 " + modeName + " 不存在！");
            return;
        }

        // 加载模式配置文件
        FileConfiguration modeConfig = YamlConfiguration.loadConfiguration(modeFile);

        // 验证资源类型是否合法
        Material materialType = null;
        if (type.equalsIgnoreCase("铁")) {
            materialType = Material.IRON_INGOT;
        } else if (type.equalsIgnoreCase("金")) {
            materialType = Material.GOLD_INGOT;
        } else if (type.equalsIgnoreCase("钻石")) {
            materialType = Material.DIAMOND;
        } else {
            player.sendMessage(ChatColor.RED + "无效的资源类型！请使用: 铁, 金, 钻石");
            return;
        }

        // 获取玩家当前的位置作为 gentor 的坐标
        Location location = player.getLocation();
        String path = "gentors." + gentorName + ".";

        // 保存 gentor 的信息到配置文件中
        modeConfig.set(path + "world", location.getWorld().getName());
        modeConfig.set(path + "x", location.getX());
        modeConfig.set(path + "y", location.getY());
        modeConfig.set(path + "z", location.getZ());
        modeConfig.set(path + "yaw", location.getYaw());
        modeConfig.set(path + "pitch", location.getPitch());
        modeConfig.set(path + "type", type);

        // 保存配置文件
        try {
            modeConfig.save(modeFile);
            player.sendMessage(ChatColor.GREEN + "Gentor资源点 " + ChatColor.YELLOW + gentorName + ChatColor.GREEN +
                    " 已成功设置为 " + type + " 类型资源！");
        } catch (IOException e) {
            e.printStackTrace();
            player.sendMessage(ChatColor.RED + "保存Gentor资源点时出错！");
        }
    }


    /**
     * ==================================================
     * 生成配置中的所有NPC（新增模式名称检查）
     * ==================================================
     */
    // 修改 spawnConfiguredNPCs 方法，修复并发修改异常
    private void spawnConfiguredNPCs(FileConfiguration modeConfig) {
        if (!modeConfig.contains("npcs")) {
            plugin.getLogger().info("模式 " + modeName + " 未配置NPC");
            return;
        }

        // 修复并发修改异常的代码：先收集再删除
        List<NPC> toRemove = new ArrayList<>();
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (isGameNPC(npc) && npc.getName().contains(modeName)) {
                toRemove.add(npc);
            }
        }
        // 在新循环中删除
        for (NPC npc : toRemove) {
            npc.destroy();
        }
        // 生成新NPC
        for (String npcType : modeConfig.getConfigurationSection("npcs").getKeys(false)) {
            for (String name : modeConfig.getConfigurationSection("npcs." + npcType).getKeys(false)) {
                String path = "npcs." + npcType + "." + name;
                Location loc = parseLocation(modeConfig, path);

                NPC npc = CitizensAPI.getNPCRegistry().createNPC(
                        org.bukkit.entity.EntityType.PLAYER,
                        ChatColor.AQUA + modeName + "-" +
                                (npcType.equalsIgnoreCase("riskName") ? "任务NPC-" : "商人NPC-") + name
                );
                npc.spawn(loc);
                plugin.getLogger().info("生成NPC: " + npc.getName() + " 在 " + loc);
            }
        }
    }


    // 辅助方法：从配置解析Location
    private Location parseLocation(FileConfiguration config, String path) {
        return new Location(
                Bukkit.getWorld(config.getString(path + ".world")),
                config.getDouble(path + ".x"),
                config.getDouble(path + ".y"),
                config.getDouble(path + ".z"),
                (float) config.getDouble(path + ".yaw"),
                (float) config.getDouble(path + ".pitch")
        );
    }


    /**
     * ==================================================
     * 处理管理员设置的NPC指令
     * 指令格式: /battle NPC <游戏名称> <NPC名称> <riskName|BuyName>
     * ==================================================
     */
    public void handleNPCCommand(Player player, String gameName, String npcName, String npcType) {

        // 验证NPC类型是否合法
        if (!npcType.equalsIgnoreCase("riskName") && !npcType.equalsIgnoreCase("BuyName")) {
            player.sendMessage(ChatColor.RED + "无效的NPC类型! 必须为 riskName(任务NPC) 或 BuyName(商人NPC)");
            return;
        }

        // 设置当前模式名称
        this.modeName = gameName; // 动态设置当前处理的模式名称

        File modeFile = new File(plugin.getDataFolder() + File.separator + "Game", modeName + ".yml");
        if (!modeFile.exists()) {
            try {
                modeFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                player.sendMessage(ChatColor.RED + "创建模式配置文件失败!");
                return;
            }
        }

        FileConfiguration modeConfig = YamlConfiguration.loadConfiguration(modeFile);
        Location loc = player.getLocation();

        // 保存NPC信息到配置中
        String path = "npcs." + npcType + "." + npcName + ".";
        modeConfig.set(path + "world", loc.getWorld().getName());
        modeConfig.set(path + "x", loc.getX());
        modeConfig.set(path + "y", loc.getY());
        modeConfig.set(path + "z", loc.getZ());
        modeConfig.set(path + "yaw", loc.getYaw());
        modeConfig.set(path + "pitch", loc.getPitch());

        try {
            modeConfig.save(modeFile);
            player.sendMessage(ChatColor.GREEN + "==============================");
            player.sendMessage(ChatColor.GREEN + "NPC '" + ChatColor.YELLOW + npcName
                    + ChatColor.GREEN + "' 已成功设置为 " + npcType + " 类型!");
            player.sendMessage(ChatColor.GREEN + "位置: " + ChatColor.YELLOW + locToString(loc));
            player.sendMessage(ChatColor.GREEN + "==============================");

            // 立即生成NPC
            spawnConfiguredNPCs(modeConfig);
        } catch (IOException e) {
            e.printStackTrace();
            player.sendMessage(ChatColor.RED + "保存NPC信息时出错!");
        }
    }

    // 辅助方法：将Location转换为可读字符串
    private String locToString(Location loc) {
        return String.format("世界: %s, X: %.1f, Y: %.1f, Z: %.1f",
                loc.getWorld().getName(),
                loc.getX(),
                loc.getY(),
                loc.getZ());
    }




    // -------------------------------
    // 【积分（下届之星）相关】
    // -------------------------------
    /**
     * ==================================================
     * 创建自定义物品——下届之星
     * ==================================================
     *
     * @param amount 数量
     * @return 下届之星物品堆
     */
    private ItemStack createXiajieStar(int amount) {
        ItemStack star = new ItemStack(Material.NETHER_STAR, amount);
        ItemMeta meta = star.getItemMeta();
        if (meta != null) {
            // 修改中文名称为正确的"下界之星"
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "下界之星");
            star.setItemMeta(meta);
        }
        return star;
    }

    /**
     * ==================================================
     * 判断某个物品是否为下届之星
     * ==================================================
     *
     * @param item 检查的物品
     * @return 是返回 true，否则 false
     */
    private boolean isXiajieStar(ItemStack item) {
        return item != null &&
                item.getType() == Material.NETHER_STAR &&
                item.hasItemMeta() &&
                item.getItemMeta().hasDisplayName() &&
                item.getItemMeta().getDisplayName().equals(ChatColor.LIGHT_PURPLE + "下界之星");
    }

    /**
     * ==================================================
     * 尝试从玩家物品栏中消耗指定数量的下届之星
     * ==================================================
     */
    private boolean consumeXiajieStar(Player player, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (isXiajieStar(item)) {
                int available = item.getAmount();
                if (available >= remaining) {
                    item.setAmount(available - remaining);
                    if (item.getAmount() <= 0) {
                        player.getInventory().setItem(i, null);
                    }
                    remaining = 0;
                    break;
                } else {
                    remaining -= available;
                    player.getInventory().setItem(i, null);
                }
            }
        }
        return remaining == 0;
    }


    @EventHandler
    public void onPlayerDeathInGame(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        ((KitBattle) plugin).chosenKitPlayers.remove(victim.getName());
        // 新增游戏状态检查
        if (!inGamePlayers.contains(victim)) return;
        // 取消死亡事件（防止掉落物品和死亡界面）
        //event.setCancelled(true);
        // 立即复活玩家
        victim.setHealth(20);
        victim.setFoodLevel(20);
        victim.setFireTicks(0);
        // 新增虚空死亡检测
        if (victim.getLastDamageCause() != null &&
                victim.getLastDamageCause().getCause() == EntityDamageEvent.DamageCause.VOID) {
            // 虚空死亡特殊处理
            handleVoidDeath(victim);
            //event.setCancelled(true);
            return;
        }
        // 清除负面状态
        victim.getActivePotionEffects().forEach(e -> victim.removePotionEffect(e.getType()));

        event.setDeathMessage("");

        Player killer = victim.getKiller();
        // 在击杀处理部分添加config判断
        if (killer != null && inGamePlayers.contains(killer)) {
            boolean lootEnabled = plugin.getConfig().getBoolean("loot-system", true);
            int stolenStars = 0;

            if (lootEnabled) {
                List<ItemStack> toRemove = new ArrayList<>();
                for (ItemStack item : victim.getInventory()) {
                    if (isXiajieStar(item)) {
                        stolenStars += item.getAmount();
                        toRemove.add(item);
                    }
                }
                toRemove.forEach(item -> victim.getInventory().remove(item));
            }

            killer.getInventory().addItem(createXiajieStar(1 + stolenStars));
            killer.sendMessage(ChatColor.GOLD + "你获得了 " + (1 + stolenStars) + " 个下界之星！");
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            String team = playerTeams.get(victim);
            if (team != null) {
                if (team.startsWith(ChatColor.stripColor("队伍"))) {
                    team = "Team" + team.substring(2);
                }
                File modeFile = new File(plugin.getDataFolder() + File.separator + "Game", modeName + ".yml");
                FileConfiguration modeConfig = YamlConfiguration.loadConfiguration(modeFile);
                String path = "team.spawns." + team;
                if (modeConfig.contains(path)) {
                    String worldName = modeConfig.getString(path + ".world");
                    double x = modeConfig.getDouble(path + ".x");
                    double y = modeConfig.getDouble(path + ".y");
                    double z = modeConfig.getDouble(path + ".z");
                    float yaw = (float) modeConfig.getDouble(path + ".yaw");
                    float pitch = (float) modeConfig.getDouble(path + ".pitch");
                    Location respawnLoc = new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
                    victim.teleport(respawnLoc);
                    victim.sendMessage(ChatColor.GREEN + "你已被传送回所属队伍 " + ChatColor.YELLOW + team + ChatColor.GREEN + " 的出生点！");
                } else {
                    victim.sendMessage(ChatColor.RED + "队伍 " + team + " 的出生点未设置！");
                }
            }
        }, 1L);
    }

    private void handleVoidDeath(Player victim) {
        // 直接传送回队伍出生点，不触发击杀奖励
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            String team = playerTeams.get(victim);
            Location spawn = getTeamSpawn(team);
            if (spawn != null) {
                victim.teleport(spawn);
                victim.sendMessage(ChatColor.RED + "你掉入了虚空！");
            }
            victim.getInventory().clear();
        }, 1L);
    }

    @EventHandler
    public void onPlayerRespawnInGame(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!inGamePlayers.contains(player)) {
            // 非游戏玩家传回大厅
            Location lobby = getLobbyLocation();
            if (lobby != null) {
                event.setRespawnLocation(lobby);
            }
        }
        // 强制设置队伍出生点
        String team = playerTeams.get(player);
        Location TeamRespawnLoc = getTeamSpawn(team); // 新增方法获取队伍出生点
        if (TeamRespawnLoc != null) {
            event.setRespawnLocation(TeamRespawnLoc);
        }
        if (team != null) {
            if (team.startsWith(ChatColor.stripColor("队伍"))) {
                team = "Team" + team.substring(2);
            }
            File modeFile = new File(plugin.getDataFolder() + File.separator + "Game", modeName + ".yml");
            FileConfiguration modeConfig = YamlConfiguration.loadConfiguration(modeFile);
            String path = "team.spawns." + team;
            if (modeConfig.contains(path)) {
                String worldName = modeConfig.getString(path + ".world");
                double x = modeConfig.getDouble(path + ".x");
                double y = modeConfig.getDouble(path + ".y");
                double z = modeConfig.getDouble(path + ".z");
                float yaw = (float) modeConfig.getDouble(path + ".yaw");
                float pitch = (float) modeConfig.getDouble(path + ".pitch");
                Location respawnLoc = new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
                event.setRespawnLocation(respawnLoc);
            }
        }
    }
    // 获取队伍出生点的方法
    private Location getTeamSpawn(String teamID) {
        /*============================================
         * 功能：从当前模式配置获取指定队伍的出生点
         * 参数：teamID - 中文队伍ID（如"队伍1"）
         * 返回值：出生点坐标（若未设置返回null）
         *============================================*/
        File modeFile = new File(plugin.getDataFolder() + File.separator + "Game", modeName + ".yml");
        FileConfiguration modeConfig = YamlConfiguration.loadConfiguration(modeFile);

        String path = "team.spawns." + teamID;
        if (modeConfig.contains(path)) {
            String worldName = modeConfig.getString(path + ".world");
            // 检查世界有效性
            if (Bukkit.getWorld(worldName) == null) {
                plugin.getLogger().warning("队伍 " + teamID + " 的出生点世界 " + worldName + " 未加载！");
                return null;
            }
            return new Location(
                    Bukkit.getWorld(worldName),
                    modeConfig.getDouble(path + ".x"),
                    modeConfig.getDouble(path + ".y"),
                    modeConfig.getDouble(path + ".z"),
                    (float) modeConfig.getDouble(path + ".yaw"),
                    (float) modeConfig.getDouble(path + ".pitch")
            );
        }
        return null;
    }



    @EventHandler
    public void onNPCLeftClick(NPCLeftClickEvent event) {
        Player player = event.getClicker();
        if (!inGamePlayers.contains(player)) return;

        NPC npc = event.getNPC();
        if (npc.getName().contains("任务NPC-")) {
            String playerId = player.getUniqueId().toString();
            long now = System.currentTimeMillis();

            // 检查CD
            if (taskNpcCooldown.containsKey(playerId) && (now - taskNpcCooldown.get(playerId)) < 5000) {
                player.sendMessage(ChatColor.RED + "请等待5秒后再提交！");
                return;
            }

            // 扣除并提交
            if (consumeXiajieStar(player, 1)) { // 此处确保调用扣除方法
                taskNpcCooldown.put(playerId, now);
                submitTeamPoints(playerTeams.get(player), 1);
                player.sendMessage(ChatColor.GREEN + "成功提交1个下界之星！");
            } else {
                player.sendMessage(ChatColor.RED + "提交失败，你还没有下界之星！");
            }
        }
    }
    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        Player player = event.getClicker();
        NPC npc = event.getNPC();
        String npcName = npc.getName();

        // 商人NPC逻辑（右键点击）
        if (npcName.contains("商人NPC-")) {
            openShopGUI(player);
        }
    }

    // 修改openShopGUI方法
    private void openShopGUI(Player player) {
        Inventory shopGUI = Bukkit.createInventory(null, 54, ChatColor.GOLD + "职业商店");

        // 添加预设职业
        shopManager.PRESET_KITS.keySet().forEach(id -> {
            if (shopManager.getShopConfig().getBoolean("presets." + id + ".enabled")) {
                ItemStack item = shopManager.buildPresetItem(id);
                int slot = shopManager.getShopConfig().getInt("presets." + id + ".slot");
                shopGUI.setItem(slot, item);
            }
        });

        // 添加自定义职业
        if (shopManager.getShopConfig().contains("kits")) {
            shopManager.getShopConfig().getConfigurationSection("kits").getKeys(false).forEach(kit -> {
                if (shopManager.getShopConfig().getBoolean("kits." + kit + ".enabled")) {
                    ItemStack item = shopManager.buildShopItem(kit);
                    shopGUI.setItem(shopManager.getShopConfig().getInt("kits." + kit + ".slot"), item);
                }
            });
        }

        shopInventoryMap.put(shopGUI, "shop");
        player.openInventory(shopGUI);
    }


    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // 处理游戏中的玩家退出
        if (inGamePlayers.contains(player)) {
            handleGameLeave(player);
            if (inGamePlayers.isEmpty()) {
                endGame("玩家退出导致游戏终止",false);
            }
        }
        // 处理等待中的玩家退出
        WaitGame waitGame = ((KitBattle) plugin).getBattleManager().getWaitGameByPlayer(player);
        if (waitGame != null) {
            waitGame.leaveGame(player);
            if (waitGame.getPlayerCount() < waitGame.getMinPlayers()) {
                waitGame.cancelCountdown();
                Bukkit.broadcastMessage(ChatColor.RED + "玩家人数不足，倒计时已取消！");
            }
        }
    }

    public boolean rejoinPlayer(Player player) {
        if (inGamePlayers.isEmpty() || !playerTeams.containsKey(player)) return false;

        player.getInventory().clear();
        Location spawn = getTeamSpawn(playerTeams.get(player));
        if (spawn != null) {
            player.teleport(spawn);
            inGamePlayers.add(player);
            return true;
        }
        return false;
    }


    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();

        if (!(event.getWhoClicked() instanceof Player)) return;

        Inventory inv = event.getInventory();
        if (inv == null || !shopInventoryMap.containsKey(inv)) return;

        if (clicked == null || !clicked.hasItemMeta()) return;

        if (shopInventoryMap.containsKey(event.getInventory())) {
            event.setCancelled(true);
            if (clicked == null) return;
            // 新增：检查是否已选择职业
            if(((KitBattle)plugin).chosenKitPlayers.contains(player.getName())) {
                player.sendMessage(ChatColor.RED + "你已选择职业，无法再次购买！");
                event.setCancelled(true);
                return;
            }
            ShopItem.ShopItemData data = getItemData(clicked); 

            if (data != null) {
                if (shopManager.canAfford(player, data)) {
                    shopManager.purchaseItem(player, data);
                    player.sendMessage(ChatColor.GREEN + "购买成功！");
                } else {
                    player.sendMessage(ChatColor.RED + "资源不足！");
                }
            }
        }

    }

    private ShopItem.ShopItemData getItemData(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        String displayName = ChatColor.stripColor(meta.getDisplayName());

        // 检查预设职业
        for (ShopItem.ShopItemData data : ShopItem.PRESET_KITS.values()) {
            if (data.getName().equalsIgnoreCase(displayName)) {
                return data;
            }
        }

        // 检查自定义职业
        FileConfiguration shopConfig = shopManager.getShopConfig();
        if (shopConfig.contains("kits")) {
            for (String key : shopConfig.getConfigurationSection("kits").getKeys(false)) {
                String kitName = shopConfig.getString("kits." + key + ".name");
                if (displayName.equalsIgnoreCase(ChatColor.stripColor(kitName))) {
                    return new ShopItem.ShopItemData(
                            kitName,
                            Material.valueOf(shopConfig.getString("kits." + key + ".material")),
                            shopConfig.getInt("kits." + key + ".price_iron"),
                            shopConfig.getInt("kits." + key + ".price_gold"),
                            shopConfig.getInt("kits." + key + ".price_diamond"),
                            shopConfig.getInt("kits." + key + ".x"),
                            shopConfig.getInt("kits." + key + ".y"),
                            new ArrayList<>(), // 加载物品数据
                            null
                    );
                }
            }
        }
        return null;
    }

    public void submitTeamPoints(String teamName, int points) {
        int current = teamPoints.getOrDefault(teamName, 0);
        current += points;
        teamPoints.put(teamName, current);

        Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "【Asteroids】" + ChatColor.GREEN
                + teamName + " 当前积分: " + current);

        if (current >= 20) {
            endGame(teamName,false);
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) return;

        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();

        if (playerTeams.get(victim).equals(playerTeams.get(attacker))) {
            event.setCancelled(true);
            attacker.sendMessage(ChatColor.RED + "不能攻击队友！");
        }
    }



    public void endGame(String reason, boolean isPluginDisabling) {
        Location lobby = getLobbyLocation();
        FileConfiguration spawnConfig = ((KitBattle) plugin).getSpawnConfig();
        // 确保大厅世界已加载
        if (lobby != null && lobby.getWorld() == null) {
            lobby.setWorld(Bukkit.getWorld(spawnConfig.getString("lobby.world")));
        }
        // 判断是否是正常胜利
        if (reason != null && teamPoints.containsKey(reason)) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "==================================");
            Bukkit.broadcastMessage(ChatColor.RED + "【游戏结束】" + ChatColor.GREEN
                    + " 队伍 " + reason + " 累计提交积分达到20分，游戏结束！");
        } else {
            Bukkit.broadcastMessage(ChatColor.GOLD + "==================================");
            Bukkit.broadcastMessage(ChatColor.RED + "【游戏结束】" + ChatColor.GREEN + reason);
        }

        // 停止所有正在运行的任务
        if (gentorTask != null) {
            gentorTask.cancel();
            gentorTask = null;
        }
        // 添加保护判断
        if (!isPluginDisabling) {
            // 只有在插件未卸载时执行需要调度的代码
            new BukkitRunnable() {
                @Override
                public void run() {
                    // 传送玩家回大厅的代码
                }
            }.runTask(plugin);
        }
        // 添加清除Gentor掉落物品的循环
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Item item : world.getEntitiesByClass(org.bukkit.entity.Item.class)) {
                Material type = item.getItemStack().getType();
                if (type == Material.IRON_INGOT || type == Material.GOLD_INGOT || type == Material.DIAMOND) {
                    item.remove();
                }
            }
        }

        // 使用inGamePlayers确保所有玩家被处理
        for (Player player : inGamePlayers) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]); // 清空装备
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            if (lobby != null) {
                player.teleport(lobby);
                player.sendMessage(ChatColor.GREEN + "已传送回大厅！");
            } else {
                player.sendMessage(ChatColor.RED + "大厅位置未设置！");
            }
        }
        // 清除游戏中的玩家集合
        inGamePlayers.clear();

        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Item item : world.getEntitiesByClass(org.bukkit.entity.Item.class)) {
                Material type = item.getItemStack().getType();
                if (type == Material.IRON_INGOT || type == Material.GOLD_INGOT || type == Material.DIAMOND) {
                    item.remove();
                }
            }
        }
        // 仅在插件未卸载时清理NPC
        if (!isPluginDisabling) {
            try {
                // 安全移除NPC的逻辑
                List<NPC> npcsToRemove = new ArrayList<>();
                if (CitizensAPI.getNPCRegistry() != null) {
                    for (NPC npc : CitizensAPI.getNPCRegistry()) {
                        if (isGameNPC(npc)) {
                            npcsToRemove.add(npc);
                        }
                    }
                    npcsToRemove.forEach(npc -> {
                        npc.despawn();
                        npc.destroy();
                    });
                }
            } catch (IllegalStateException e) {
                plugin.getLogger().info("Citizens未加载，跳过NPC清除");
            }
        }

        // 清除所有玩家的游戏状态
        for (Player player : gamePlayers) {
            plugin.getBattleManager().getGameManager().getInGamePlayers().remove(player);
            player.getInventory().clear();
            player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
        }

        // 添加传送保护机制
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : gamePlayers) {
                    if (player.isOnline()) {
                        player.teleport(lobby != null ? lobby : Bukkit.getWorlds().get(0).getSpawnLocation());
                        player.sendTitle(ChatColor.GOLD + "游戏结束", "已传送回大厅");
                    }
                }
            }
        }.runTask(plugin);

        // 新增：重置游戏状态
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            BattleManager battleManager = ((KitBattle)plugin).battleManager;
            battleManager.getActiveGames().remove(modeName); // 从活跃游戏中移除
            battleManager.loadActiveGames(); // 重新加载可用游戏
        }, 20L);
        resetGame();
    }

    /**
     * 判断 NPC 是否为游戏中的 NPC
     */
    public boolean isGameNPC(NPC npc) {
        // 你可以在此根据 NPC 的名字或者其他标识来判断 NPC 是否是游戏相关的 NPC
        String npcName = npc.getName();
        return npcName.contains("任务NPC-") || npcName.contains("商人NPC-");
    }


    private Location getLobbyLocation() {
        File spawnFile = new File(plugin.getDataFolder(), "Spawn.yml");
        // 通过插件实例获取 spawnConfig
        FileConfiguration spawnConfig = ((KitBattle) plugin).getSpawnConfig();
        if (spawnConfig.contains("lobby")) {
            String worldName = spawnConfig.getString("lobby.world");
            double x = spawnConfig.getDouble("lobby.x");
            double y = spawnConfig.getDouble("lobby.y");
            double z = spawnConfig.getDouble("lobby.z");
            float yaw = (float) spawnConfig.getDouble("lobby.yaw");
            float pitch = (float) spawnConfig.getDouble("lobby.pitch");
            return new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
        }
        return null;
    }

    private void resetGame() {
        playerTeams.clear();
        gamePlayers.clear();
        teamPoints.clear();
        inGamePlayers.clear();
    }

    public void handleGameRespawn(Player player) {
        String team = playerTeams.get(player);
        Location spawn = getTeamSpawn(team);
        if (spawn != null) {
            player.teleport(spawn);
        }
        player.getInventory().clear();
        player.setHealth(20);
        player.setFoodLevel(20);
    }
}
