package KitBattle;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class WaitGame {
    private KitBattle plugin;
    private Set<Player> waitingPlayers = new HashSet<>();
    private Map<Player, String> playerTeams = new HashMap<>();  // 记录玩家和他们队伍的关系
    protected String modeName;
    private int minPlayers;
    private int teamNumber;
    private int teamSize;
    private int countdown = 30;  // 30秒倒计时
    private int currentPlayers = 0;
    private boolean gameStarting = false;
    private Set<Player> unselectedPlayers = new HashSet<>(); // 等待中未选队伍的玩家
    private Set<Player> selectedPlayers = new HashSet<>();   // 已选队伍的玩家
    private Set<Player> gamePlayers = new HashSet<>();      // 游戏中的玩家
    private FileConfiguration modeConfig;

    public WaitGame(KitBattle plugin, String modeName, int minPlayers, int teamNumber, int teamSize) {
        this.plugin = plugin;
        this.modeName = modeName;
        this.minPlayers = minPlayers;
        this.teamNumber = teamNumber;
        this.teamSize = teamSize;
        File modeFile = new File(plugin.getDataFolder() + "/Game", modeName + ".yml");
        this.modeConfig = YamlConfiguration.loadConfiguration(modeFile);
    }
    // 以下代码添加到 WaitGame 类中
    public Map<Player, String> getPlayerTeams() {
        return this.playerTeams;
    }
    final Set<Player> inGamePlayers = new HashSet<>();

    public Set<Player> getInGamePlayers() {
        return Collections.unmodifiableSet(inGamePlayers); // 返回不可修改集合增加安全性
    }
   
    public boolean containsPlayer(Player player) {
        return waitingPlayers.contains(player) ||
                unselectedPlayers.contains(player) ||
                selectedPlayers.contains(player);
    }


    public int getPlayerCount() {
        return this.waitingPlayers.size();
    }

    public Set<Player> getWaitingPlayers() {
        return this.waitingPlayers;
    }

    public void cancelCountdown() {
        Bukkit.getScheduler().cancelTasks(plugin);
        gameStarting = false;
        countdown = 30;
        // 清空所有等待玩家状态
        new ArrayList<>(waitingPlayers).forEach(this::leaveGame);
    }

    public int getMinPlayers() {
        return this.minPlayers;
    }

    // 玩家加入游戏
    public void joinGame(Player player) {

        // 全局状态检查
        BattleManager battleManager = ((KitBattle) plugin).getBattleManager();
        // 检查是否已在其他游戏模式中
        for (WaitGame wg : battleManager.getActiveGames().values()) {
            if (wg != this && wg.containsPlayer(player)) {
                player.sendMessage(ChatColor.RED + "你已经在其他游戏中！");
                return;
            }
        }

        // 检查是否已经在本局游戏中
        if (gameStarting || waitingPlayers.contains(player)) {
            player.sendMessage(ChatColor.RED + "你已经在队列中！");
            return;
        }


        if (gameStarting) {
            player.sendMessage(ChatColor.RED + "游戏已经开始，无法加入!");
            return;
        }

        // 传送逻辑
        File modeFile = new File(plugin.getDataFolder() + "/Game", modeName + ".yml");
        FileConfiguration modeConfig = YamlConfiguration.loadConfiguration(modeFile);

        if (modeConfig.contains("wait-lobby")) {
            Location waitLobby = parseLocation(modeConfig, "wait-lobby");
            player.teleport(waitLobby);
        }

        player.getInventory().clear();
        waitingPlayers.add(player);
        currentPlayers++;

        // 给玩家一个床来选择队伍
        giveTeamSelectionItem(player);
        unselectedPlayers.add(player); // 加入未选队伍集合

        // 先添加床
        giveTeamSelectionItem(player);

        // 再添加退出箭矢（第8格，索引从0开始）
        ItemStack leaveItem = new ItemStack(Material.ARROW);
        ItemMeta meta = leaveItem.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "退出游戏");
        leaveItem.setItemMeta(meta);
        player.getInventory().setItem(8, leaveItem);

        // 检查是否达到最小玩家数
        if (currentPlayers >= minPlayers && !gameStarting) {
            startCountdown();
        }

        player.sendMessage(ChatColor.GREEN + "你已经加入了游戏模式: " + modeName);
    }
    
    public void leaveGame(Player player) {
        if (waitingPlayers.remove(player)) {
            currentPlayers--;
            player.getInventory().clear();
            unselectedPlayers.remove(player);
            selectedPlayers.remove(player);

            // 清除物品并传送
            player.getInventory().clear();
            // 传送回大厅
            Location lobby = ((KitBattle) plugin).getLobbyLocation();
            if (lobby != null) {
                player.teleport(lobby);
            }

            // 检查并停止倒计时
            if (currentPlayers < minPlayers && gameStarting) {
                cancelCountdown();
            }
        }
        // 当玩家重新加入时触发检查
        new BukkitRunnable() {
            @Override
            public void run() {
                if (currentPlayers >= minPlayers && !gameStarting) {
                    startCountdown();
                }
            }
        }.runTaskLater(plugin, 1L);
    }


    // 添加位置解析方法
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

    // -------------------------------
    // 给玩家发放用于选择队伍的床物品（自定义名称）
    // -------------------------------
    private void giveTeamSelectionItem(Player player) {
        ItemStack bedItem = new ItemStack(Material.BED);
        // 获取物品元数据
        ItemMeta meta = bedItem.getItemMeta();
        if(meta != null) {
            // 设置自定义显示名称，颜色为 AQUA
            meta.setDisplayName(ChatColor.AQUA + "选择队伍");
            bedItem.setItemMeta(meta);
        }
        // 将床放在玩家物品栏第一格
        player.getInventory().setItem(0, bedItem);
    }

    // 修改 startCountdown 方法
    private void startCountdown() {
        plugin.getServer().broadcastMessage(ChatColor.YELLOW + "游戏将在 " + countdown + " 秒后开始!");
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (currentPlayers < minPlayers) {
                    cancel();
                    return;
                }

                tick++;
                if (tick % 5 == 0) { // 每5秒（100 ticks）提示一次
                    plugin.getServer().broadcastMessage(ChatColor.YELLOW + "游戏开始倒计时: " + countdown + "秒");
                }

                if (countdown-- <= 0) {
                    startGame();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // 仍然每秒检查一次
    }


    // 游戏开始
    private void startGame() {

        gameStarting = true;
        plugin.getServer().broadcastMessage(ChatColor.GREEN + "游戏开始!");
        // 分配未选择队伍的玩家
        List<Player> toAssign = new ArrayList<>(unselectedPlayers);
        Collections.shuffle(toAssign);

        // 获取各队当前人数
        Map<String, Integer> teamCounts = new HashMap<>();
        for (String team : modeConfig.getStringList("team.ids")) {
            int count = (int) playerTeams.values().stream().filter(t -> t.equals(team)).count();
            teamCounts.put(team, count);
        }

        // 智能分配算法
        for (Player p : toAssign) {
            String leastTeam = findLeastTeam(teamCounts, modeConfig.getInt("team.size"));
            playerTeams.put(p, leastTeam);
            teamCounts.put(leastTeam, teamCounts.get(leastTeam) + 1);
        }

        // 更新玩家状态
        gamePlayers.addAll(playerTeams.keySet());
        // 调用 GameManager 启动游戏
        GameManager gameManager = new GameManager(plugin, modeName, playerTeams);
        plugin.getServer().getPluginManager().registerEvents(gameManager, plugin); // 注册事件
        gameManager.startGame();

        // 将玩家传送到队伍出生点
        teleportPlayersToTeams();
    }

    // 修改后的 findLeastTeam 方法
    private String findLeastTeam(Map<String, Integer> counts, int maxPerTeam) {
        Optional<Map.Entry<String, Integer>> leastEntry = counts.entrySet().stream()
                .filter(e -> e.getValue() < maxPerTeam)
                .min(Map.Entry.comparingByValue());

        // 如果没有可用队伍，尝试返回第一个队伍（即使已满）
        return leastEntry.map(Map.Entry::getKey)
                .orElseGet(() -> {
                    if (!counts.isEmpty()) {
                        return counts.keySet().iterator().next();
                    } else {
                        plugin.getLogger().warning("没有可用的队伍进行分配！");
                        return "default_team"; // 添加默认队伍处理
                    }
                });
    }




    // 取消游戏
    private void cancelGame() {
        gameStarting = false;
        for (Player player : waitingPlayers) {
            player.sendMessage(ChatColor.RED + "游戏已取消，人数不足！");
        }
    }

    private void teleportPlayersToTeams() {
        File modeFile = new File(plugin.getDataFolder() + File.separator + "Game", modeName + ".yml");
        FileConfiguration modeConfig = YamlConfiguration.loadConfiguration(modeFile);
// 创建玩家列表副本
        List<Player> players = new ArrayList<>(waitingPlayers);

        // 获取配置参数
        int maxTeamSize = modeConfig.getInt("team.size", 5);
        List<String> teamIDs = modeConfig.getStringList("team.ids");
        if (teamIDs.isEmpty()) {
            plugin.getLogger().warning("模式 " + modeName + " 未配置队伍ID！");
            return;
        }

        // 初始化队伍人数统计
        Map<String, Integer> teamCounts = new HashMap<>();
        for (String team : teamIDs) {
            teamCounts.put(team, 0);
        }

        // 先处理已选择队伍的玩家
        Map<Player, String> tempTeams = new HashMap<>(playerTeams);
        for (Map.Entry<Player, String> entry : tempTeams.entrySet()) {
            Player p = entry.getKey();
            String team = entry.getValue();

            // 如果队伍不存在则移除选择
            if (!teamIDs.contains(team)) {
                playerTeams.remove(p);
                p.sendMessage(ChatColor.RED + "无效队伍，已重置选择");
            }
        }

        // 剩余玩家随机分配
        List<Player> unassigned = waitingPlayers.stream()
                .filter(p -> !playerTeams.containsKey(p))
                .collect(Collectors.toList());
        Collections.shuffle(unassigned);

        // 使用迭代器分配
        Iterator<Player> it = unassigned.iterator();
        while (it.hasNext()) {
            for (String team : teamIDs) {
                if (!it.hasNext()) break;
                if (teamCounts.get(team) < maxTeamSize) {
                    Player p = it.next();
                    playerTeams.put(p, team);
                    teamCounts.put(team, teamCounts.get(team)+1);
                }
            }
        }
    }


    // 在 WaitGame.java 中修改 openTeamSelectionGUI 方法
    public void openTeamSelectionGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 9 * 5, "选择你的队伍");

        File modeFile = new File(plugin.getDataFolder() + File.separator + "Game", modeName + ".yml");
        FileConfiguration modeConfig = YamlConfiguration.loadConfiguration(modeFile);
        List<String> teamIDs = modeConfig.getStringList("team.ids");

        for (String teamID : teamIDs) {
            ItemStack teamItem = new ItemStack(Material.WOOL);
            ItemMeta meta = teamItem.getItemMeta();


            String displayName = teamID;
            meta.setDisplayName(ChatColor.YELLOW + displayName);

            // 显示当前人数
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "当前人数: " + playerTeams.values().stream()
                    .filter(t -> t.equals(teamID)).count());
            meta.setLore(lore);

            teamItem.setItemMeta(meta);
            gui.addItem(teamItem);
        }

        player.openInventory(gui);
    }

    // 处理玩家选择队伍的逻辑
    public void selectTeam(Player player, String teamName) {
        if (!waitingPlayers.contains(player)) {
            player.sendMessage(ChatColor.RED + "你不在等待玩家队列中！");
            return;
        }

        File modeFile = new File(plugin.getDataFolder() + "/Game", modeName + ".yml");
        FileConfiguration modeConfig = YamlConfiguration.loadConfiguration(modeFile);
        List<String> validTeams = modeConfig.getStringList("team.ids");

        // 验证队伍有效性
        if (!validTeams.contains(teamName)) {
            player.sendMessage(ChatColor.RED + "无效的队伍！");
            return;
        }

        // 更新玩家状态
        if (unselectedPlayers.contains(player)) {
            unselectedPlayers.remove(player);
            selectedPlayers.add(player);
        }

        // 存储玩家的队伍信息
        playerTeams.put(player, teamName);
        player.sendMessage(ChatColor.GREEN + "你已成功加入 " + teamName);
    }


}
