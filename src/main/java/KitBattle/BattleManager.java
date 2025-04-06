package KitBattle;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class BattleManager {

    private JavaPlugin plugin;
    private File gameFolder;
    // 修改：初始化 activeGames，之前未初始化导致后续 put 操作可能出现空指针异常
    private Map<String, WaitGame> activeGames = new HashMap<>();  // 存储每个模式对应的 WaitGame 实例
    private GameManager gameManager; // 需要在此处初始化


    public Map<String, WaitGame> getActiveGames() {
        return this.activeGames;
    }
    public GameManager getGameManager() {
        return this.gameManager;
    }

    // 构造器
    public BattleManager(JavaPlugin plugin) {
        this.plugin = plugin;
        gameFolder = new File(plugin.getDataFolder(), "Game");

        if (!gameFolder.exists()) {
            gameFolder.mkdirs();  // 如果文件夹不存在，创建它
        }

        // 在插件启动时检查并加载所有启用的游戏模式
        loadActiveGames();
        this.gameManager = new GameManager((KitBattle) plugin, "default", new HashMap<>());
    }
    // 加载启用的游戏模式
    public void loadActiveGames() {
        File[] modeFiles = gameFolder.listFiles();
        if (modeFiles != null) {
            for (File modeFile : modeFiles) {
                FileConfiguration modeConfig = YamlConfiguration.loadConfiguration(modeFile);
                if (modeConfig.getBoolean("enabled", false)) {
                    String modeName = modeFile.getName().replace(".yml", "");
                    int minPlayers = modeConfig.getInt("min-players", 2);
                    int teamNumber = modeConfig.getInt("team.number", 2);
                    int teamSize = modeConfig.getInt("team.size", 5);

                    // 如果模式已经启用，则加载并初始化 WaitGame 实例
                    activeGames.put(modeName, new WaitGame((KitBattle) plugin, modeName, minPlayers, teamNumber, teamSize));
                }
            }
        }
    }

    // -------------------------------------
// ========== 根据玩家获取其所在的等待游戏实例 ==========
// -------------------------------------
    public WaitGame getWaitGameByPlayer(Player player) {
        for (WaitGame wg : activeGames.values()) {
            if (wg.containsPlayer(player)) {  // 调用 WaitGame 中新增的 containsPlayer 方法
                return wg;
            }
        }
        return null;
    }
    // ==================================================
    // 创建新模式并初始化 WaitGame 实例
    // ==================================================
    public void addMode(Player player, String modeName) {
        File modeFile = new File(gameFolder, modeName + ".yml");

        if (modeFile.exists()) {
            player.sendMessage(ChatColor.RED + "模式 " + modeName + " 已经存在!");
            return;
        }
        if (activeGames.containsKey(modeName)) {
            player.sendMessage(ChatColor.RED + "该模式已存在！");
            return;
        }

        try {
            modeFile.createNewFile();
            FileConfiguration modeConfig = YamlConfiguration.loadConfiguration(modeFile);
            modeConfig.set("min-players", 2);  // 默认最小玩家数
            modeConfig.set("max-players", 10); // 默认最大玩家数
            modeConfig.set("time", 300);       // 默认时间（单位秒）
            modeConfig.set("team.number", 2);  // 默认队伍数
            modeConfig.set("team.size", 5);    // 默认每队人数
            modeConfig.save(modeFile);

            activeGames.put(modeName, new WaitGame((KitBattle) plugin, modeName, 2, 2, 5));

            player.sendMessage(ChatColor.GREEN + "==============================");
            player.sendMessage(ChatColor.GREEN + "模式 " + modeName + " 已创建!");
            player.sendMessage(ChatColor.GREEN + "默认设置：最小玩家=2, 最大玩家=10, 时间=300秒, 队伍数=2, 每队人数=5");
            player.sendMessage(ChatColor.GREEN + "==============================");

        } catch (IOException e) {
            e.printStackTrace();
            player.sendMessage(ChatColor.RED + "创建模式时出错!");
        }
    }

    // 获取指定模式的WaitGame实例
    public WaitGame getWaitGame(String modeName) {
        return activeGames.get(modeName);
    }

    // 设置最小玩家数
    public void setMinPlayers(Player player, String modeName, int minPlayers) {
        File modeFile = new File(gameFolder, modeName + ".yml");
        if (!modeFile.exists()) {
            player.sendMessage(ChatColor.RED + "模式 " + modeName + " 不存在!");
            return;
        }

        try {
            FileConfiguration modeConfig = YamlConfiguration.loadConfiguration(modeFile);
            modeConfig.set("min-players", minPlayers);
            modeConfig.save(modeFile);

            player.sendMessage(ChatColor.GREEN + "模式 " + modeName + " 的最小玩家数已设置为 " + minPlayers);
        } catch (IOException e) {
            e.printStackTrace();
            player.sendMessage(ChatColor.RED + "设置最小玩家数时出错!");
        }
    }

    // 设置最大玩家数
    public void setMaxPlayers(Player player, String modeName, int maxPlayers) {
        File modeFile = new File(gameFolder, modeName + ".yml");
        if (!modeFile.exists()) {
            player.sendMessage(ChatColor.RED + "模式 " + modeName + " 不存在!");
            return;
        }

        try {
            FileConfiguration modeConfig = YamlConfiguration.loadConfiguration(modeFile);
            modeConfig.set("max-players", maxPlayers);
            modeConfig.save(modeFile);

            player.sendMessage(ChatColor.GREEN + "模式 " + modeName + " 的最大玩家数已设置为 " + maxPlayers);
        } catch (IOException e) {
            e.printStackTrace();
            player.sendMessage(ChatColor.RED + "设置最大玩家数时出错!");
        }
    }

    // 设置模式时间
    public void setModeTime(Player player, String modeName, int time) {
        File modeFile = new File(gameFolder, modeName + ".yml");
        if (!modeFile.exists()) {
            player.sendMessage(ChatColor.RED + "模式 " + modeName + " 不存在!");
            return;
        }

        try {
            FileConfiguration modeConfig = YamlConfiguration.loadConfiguration(modeFile);
            modeConfig.set("time", time);
            modeConfig.save(modeFile);

            player.sendMessage(ChatColor.GREEN + "模式 " + modeName + " 的时间已设置为 " + time + " 秒");
        } catch (IOException e) {
            e.printStackTrace();
            player.sendMessage(ChatColor.RED + "设置时间时出错!");
        }
    }

    // 查看所有模式
    public void listModes(Player player) {
        File[] modeFiles = gameFolder.listFiles();
        if (modeFiles != null && modeFiles.length > 0) {
            player.sendMessage(ChatColor.YELLOW + "===== 当前所有游戏模式 =====");
            for (File modeFile : modeFiles) {
                String modeName = modeFile.getName().replace(".yml", "");
                player.sendMessage(ChatColor.AQUA + "模式名称: " + ChatColor.GREEN + modeName);
            }
            player.sendMessage(ChatColor.YELLOW + "===========================");
        } else {
            player.sendMessage(ChatColor.RED + "当前没有任何游戏模式！");
        }
    }


    // 修改 handleNPCCommand 方法
    public void handleNPCCommand(Player player, String gameName, String npcName, String npcType) {
        // 创建新的GameManager实例时传入正确的模式名称
        GameManager gameManager = new GameManager((KitBattle) plugin, gameName, new HashMap<>());
        gameManager.handleNPCCommand(player, gameName, npcName, npcType);
    }

    // 指令处理
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "只有玩家才能执行此命令！");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            return false; // 返回给KitBattle类进行处理
        }

        // 处理 /battle Gentor 指令
        if (args.length >= 4 && args[0].equalsIgnoreCase("gentor")) {
            String modeName = args[1];
            String gentorName = args[2];
            String type = args[3];

            // 动态更新 GameManager 的模式名称
            this.gameManager = new GameManager((KitBattle) plugin, modeName, new HashMap<>());
            this.gameManager.handleGentorCommand((Player) sender, modeName, gentorName, type);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "addmode":
                if (args.length == 2) {
                    String modeName = args[1];
                    addMode(player, modeName);
                    return true;
                }
                break;

            case "minplayers":
                if (args.length == 3) {
                    String modeName = args[1];
                    try {
                        int minPlayers = Integer.parseInt(args[2]);
                        setMinPlayers(player, modeName, minPlayers);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "玩家人数必须是一个数字！");
                    }
                    return true;
                }
                break;

            case "maxplayers":
                if (args.length == 3) {
                    String modeName = args[1];
                    try {
                        int maxPlayers = Integer.parseInt(args[2]);
                        setMaxPlayers(player, modeName, maxPlayers);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "玩家人数必须是一个数字！");
                    }
                    return true;
                }
                break;

            case "time":
                if (args.length == 3) {
                    String modeName = args[1];
                    try {
                        int time = Integer.parseInt(args[2]);
                        setModeTime(player, modeName, time);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "时间必须是一个数字！");
                    }
                    return true;
                }
                break;

            case "list":
                listModes(player);
                return true;
            // BattleManager.java 中修改 onCommand 的 team 分支
            // BattleManager.java 修改后的 team 分支处理
            case "team":
                if (args.length >= 4) {
                    String subCmd = args[1].toLowerCase();
                    String modeName = args[2];
                    String param = args[3];

                    if (subCmd.equals("size")) {
                        try {
                            int size = Integer.parseInt(param);
                            setTeamSize(player, modeName, size);
                        } catch (NumberFormatException e) {
                            player.sendMessage(ChatColor.RED + "人数必须为数字！");
                        }
                    } else if (subCmd.equals("add")) {
                        String teamName = (args.length >= 4) ? args[3] : "默认队伍";
                        addTeam(player, modeName, teamName);
                    }
                    return true;
                }
                break;


            case "spawn":
                if (args.length == 3) {
                    String modeName = args[1];
                    String teamName = args[2];
                    setTeamSpawn((Player) sender, modeName,teamName);
                    return true;
                }
                break;
            case "check":
                if (args.length == 2) {
                    String modeName = args[1];
                    checkMode(player, modeName);
                    return true;
                }
                break;

            case "enable":
            case "disable":
                if (args.length == 2) {
                    String modeName = args[1];
                    toggleMode(player, modeName, args[0].toLowerCase());
                    return true;
                }
                break;
            case "join":
                if (args.length == 2) {
                    String modeName = args[1];
                    WaitGame waitGame = getWaitGame(modeName);
                    if (waitGame != null) {
                        waitGame.joinGame(player);
                    } else {
                        player.sendMessage(ChatColor.RED + "该游戏模式不存在或未启用!");
                    }
                    return true;
                }
                break;
            // 在 onCommand 方法中修改 NPC 指令处理部分
            case "npc":
                if (args.length == 4) {
                    String modeName = args[1];  // 第一个参数是模式名称
                    String npcName = args[2];   // 第二个参数是NPC名称
                    String npcType = args[3];   // 第三个参数是NPC类型
                    handleNPCCommand(player, modeName, npcName, npcType);
                    return true;
                }
                break;
            case "wait":
                if (args.length == 2) {
                    setWaitLobby(player, args[1]);
                    return true;
                }
                break;
            case "leave":
                boolean inGame = gameManager.getInGamePlayers().contains(player);
                WaitGame waitGame = getWaitGameByPlayer(player);
                if (gameManager.getInGamePlayers().contains(player)) {
                    player.sendMessage(ChatColor.RED + "游戏中不能离开！");
                    return true;
                }
                if (inGame) {
                    // 游戏中使用指令
                    player.sendMessage(ChatColor.RED + "游戏中不能使用此指令！");
                } else if (waitGame != null) {
                    // 等待阶段离开
                    waitGame.leaveGame(player);
                    player.sendMessage(ChatColor.YELLOW + "已退出等待队列！");

                    // 仅当人数不足时广播
                    if (waitGame.getPlayerCount() < waitGame.getMinPlayers()) {
                        waitGame.cancelCountdown();
                        Bukkit.broadcastMessage(ChatColor.RED + "玩家人数不足，倒计时已取消！");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "你未加入任何游戏！");
                }
                return true;
            case "rejoin":
                player = (Player) sender;
                if (gameManager.rejoinPlayer(player)) {
                    player.sendMessage(ChatColor.GREEN + "已重新加入游戏！");
                } else {
                    player.sendMessage(ChatColor.RED + "没有可重新加入的游戏或游戏已结束！");
                }
                return true;

            default:
                return false;
        }

        return false;
    }

    // 在 BattleManager.java 中添加
    private Location getLobbyLocation() {
        FileConfiguration spawnConfig = ((KitBattle) plugin).getSpawnConfig();
        if (spawnConfig.contains("lobby")) {
            String worldName = spawnConfig.getString("lobby.world");
            if (Bukkit.getWorld(worldName) == null) {
                plugin.getLogger().warning("大厅世界未加载: " + worldName);
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

    // BattleManager.java 新增方法
    public void addTeam(Player player, String modeName, String teamName) {
        File modeFile = new File(gameFolder, modeName + ".yml");
        if (!modeFile.exists()) {
            player.sendMessage(ChatColor.RED + "模式 " + modeName + " 不存在!");
            return;
        }

        try {
            FileConfiguration modeConfig = YamlConfiguration.loadConfiguration(modeFile);
            List<String> teams = modeConfig.getStringList("team.ids");

            // 检查队伍是否已存在
            if (teams.contains(teamName)) {
                player.sendMessage(ChatColor.RED + "队伍 " + teamName + " 已存在!");
                return;
            }

            teams.add(teamName);
            modeConfig.set("team.ids", teams);
            modeConfig.save(modeFile);

            player.sendMessage(ChatColor.GREEN + "成功添加队伍: " + teamName);
        } catch (IOException e) {
            player.sendMessage(ChatColor.RED + "保存配置失败!");
            e.printStackTrace();
        }
    }


    // BattleManager.java 修改 setTeamSize 方法
    public void setTeamSize(Player player, String modeName, int teamSize) {
        File modeFile = new File(gameFolder, modeName + ".yml");
        if (!modeFile.exists()) {
            player.sendMessage(ChatColor.RED + "模式 " + modeName + " 不存在!");
            return;
        }

        try {
            FileConfiguration modeConfig = YamlConfiguration.loadConfiguration(modeFile);
            modeConfig.set("team.size", teamSize);
            modeConfig.save(modeFile);
            player.sendMessage(ChatColor.GREEN + "每队人数已设置为 " + teamSize);
        } catch (IOException e) {
            player.sendMessage(ChatColor.RED + "保存配置失败!");
            e.printStackTrace();
        }
    }


    // 修改后的 setTeamSpawn 方法
    public void setTeamSpawn(Player player, String modeName, String teamName) {
        File modeFile = new File(gameFolder, modeName + ".yml");
        if (!modeFile.exists()) {
            player.sendMessage(ChatColor.RED + "模式 " + modeName + " 不存在!");
            return;
        }

        try {
            FileConfiguration modeConfig = YamlConfiguration.loadConfiguration(modeFile);
            Location loc = player.getLocation();
            String path = "team.spawns." + teamName;

            modeConfig.set(path + ".world", loc.getWorld().getName());
            modeConfig.set(path + ".x", loc.getX());
            modeConfig.set(path + ".y", loc.getY());
            modeConfig.set(path + ".z", loc.getZ());
            modeConfig.set(path + ".yaw", loc.getYaw());
            modeConfig.set(path + ".pitch", loc.getPitch());

            modeConfig.save(modeFile);
            player.sendMessage(ChatColor.GREEN + "队伍 " + teamName + " 出生点已设置!");
        } catch (IOException e) {
            player.sendMessage(ChatColor.RED + "保存配置失败!");
            e.printStackTrace();
        }
    }

    // 检查模式设置完整性
    public void checkMode(Player player, String modeName) {
        File modeFile = new File(gameFolder, modeName + ".yml");
        if (!modeFile.exists()) {
            player.sendMessage(ChatColor.RED + "模式 " + modeName + " 不存在!");
            return;
        }

        FileConfiguration modeConfig = YamlConfiguration.loadConfiguration(modeFile);
        StringBuilder checkResult = new StringBuilder();
        checkResult.append(ChatColor.GREEN + "模式 " + modeName + " 的设置情况:\n");

        // 检查每个参数是否设置
        checkResult.append(checkConfigParam(modeConfig, "min-players", "最小玩家数"));
        checkResult.append(checkConfigParam(modeConfig, "max-players", "最大玩家数"));
        checkResult.append(checkConfigParam(modeConfig, "time", "模式时间"));
        checkResult.append(checkConfigParam(modeConfig, "team.number", "队伍数量"));
        checkResult.append(checkConfigParam(modeConfig, "team.size", "每队人数"));
        checkResult.append(checkConfigParam(modeConfig, "team.ids", "队伍ID"));

        // 输出检查结果
        player.sendMessage(checkResult.toString());
    }

    // 检查配置文件中的某个参数是否已设置
    private String checkConfigParam(FileConfiguration config, String path, String displayName) {
        return config.contains(path)
                ? ChatColor.GREEN + displayName + ": " + config.get(path) + "\n"
                : ChatColor.RED + displayName + "未设置\n";
    }

    // -----------------------------------------------------
// ========== 启用或禁用模式 (修复 /battle join bug) ==========
// -----------------------------------------------------
    public void toggleMode(Player player, String modeName, String action) {
        File modeFile = new File(gameFolder, modeName + ".yml");
        if (!modeFile.exists()) {
            player.sendMessage(ChatColor.RED + "模式 " + modeName + " 不存在!");
            return;
        }

        FileConfiguration modeConfig = YamlConfiguration.loadConfiguration(modeFile);

        // 检查模式是否已经设置完整
        if (isModeConfigured(modeConfig)) {
            boolean enable = action.equalsIgnoreCase("enable");
            modeConfig.set("enabled", enable);
            try {
                modeConfig.save(modeFile);
                player.sendMessage(ChatColor.GREEN + "模式 " + modeName + " 已 " + (enable ? "启用" : "禁用"));

                // ================================
                // 新增逻辑：更新 activeGames 集合
                // ================================
                if (enable) {
                    // 如果模式启用且未在 activeGames 中，则创建并添加 WaitGame 实例
                    if (!activeGames.containsKey(modeName)) {
                        int minPlayers = modeConfig.getInt("min-players", 2);
                        int teamNumber = modeConfig.getInt("team.number", 2);
                        int teamSize = modeConfig.getInt("team.size", 5);
                        activeGames.put(modeName, new WaitGame((KitBattle) plugin, modeName, minPlayers, teamNumber, teamSize));
                        player.sendMessage(ChatColor.GREEN + "成功加载模式 " + modeName + " 的等待游戏实例！");
                    }
                } else {
                    // 禁用时从 activeGames 中移除该模式
                    activeGames.remove(modeName);
                }

            } catch (IOException e) {
                e.printStackTrace();
                player.sendMessage(ChatColor.RED + "保存模式设置时出错！");
            }
        } else {
            player.sendMessage(ChatColor.RED + "请先检查模式设置是否完整！");
        }
    }
    // 检查模式的配置是否完整
    private boolean isModeConfigured(FileConfiguration config) {
        return config.contains("min-players")
                && config.contains("max-players")
                && config.contains("time")
                && config.contains("team.number")
                && config.contains("team.size")
                && config.contains("team.ids");
    }

    // 添加设置等待大厅的方法
    public void setWaitLobby(Player player, String modeName) {
        File modeFile = new File(gameFolder, modeName + ".yml");
        if (!modeFile.exists()) {
            player.sendMessage(ChatColor.RED + "模式不存在！");
            return;
        }

        try {
            FileConfiguration modeConfig = YamlConfiguration.loadConfiguration(modeFile);
            Location loc = player.getLocation();

            modeConfig.set("wait-lobby.world", loc.getWorld().getName());
            modeConfig.set("wait-lobby.x", loc.getX());
            modeConfig.set("wait-lobby.y", loc.getY());
            modeConfig.set("wait-lobby.z", loc.getZ());
            modeConfig.set("wait-lobby.yaw", loc.getYaw());
            modeConfig.set("wait-lobby.pitch", loc.getPitch());

            modeConfig.save(modeFile);
            player.sendMessage(ChatColor.GREEN + "等待大厅已设置！");
        } catch (IOException e) {
            player.sendMessage(ChatColor.RED + "保存失败！");
        }
    }

}
