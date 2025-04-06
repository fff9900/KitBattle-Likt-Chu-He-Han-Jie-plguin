package KitBattle;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static javax.swing.UIManager.put;

public class ShopItem implements Listener{
    private JavaPlugin plugin;
    private File shopFile;
    private FileConfiguration shopConfig;
    private Map<UUID, Long> lastMagicBallUse = new HashMap<>();

    // 预设职业映射表
    public static final Map<Integer, ShopItemData> PRESET_KITS = new HashMap<>();
    static {
        PRESET_KITS.put(1, new ShopItemData(
                "铁匠", Material.IRON_CHESTPLATE,
                10, 5, 3,  // 铁、金、钻石价格
                0, 0,      // x=1, y=0 (slot = 1 + 0*9 = 1)
                Arrays.asList(
                        new ItemStack(Material.IRON_HELMET),
                        new ItemStack(Material.IRON_CHESTPLATE),
                        new ItemStack(Material.IRON_LEGGINGS),
                        new ItemStack(Material.IRON_BOOTS),
                        new ItemStack(Material.IRON_AXE)
                ),
                Collections.singletonMap(Enchantment.DAMAGE_ALL, 1)
        ));
        PRESET_KITS.put(2, new ShopItemData(
                "射手", Material.BOW,
                20, 10, 5, 1, 0,
                Arrays.asList(
                        new ItemStack(Material.IRON_HELMET),
                        new ItemStack(Material.IRON_CHESTPLATE),
                        new ItemStack(Material.IRON_LEGGINGS),
                        new ItemStack(Material.IRON_BOOTS),
                        createEnchantedBow(),
                        new ItemStack(Material.ARROW, 1),
                        createPotion(Material.POTION, 8206, 1) // 速度 II
                ),
                null
        ));

        // 新增 ID3: 法师
        PRESET_KITS.put(3, new ShopItemData(
                "法师", Material.GOLD_CHESTPLATE,
                50, 30, 15, 2, 0,
                Arrays.asList(
                        new ItemStack(Material.GOLD_HELMET),
                        new ItemStack(Material.GOLD_CHESTPLATE),
                        new ItemStack(Material.GOLD_LEGGINGS),
                        new ItemStack(Material.GOLD_BOOTS),
                        createMagicBall(),
                        createPotion(Material.POTION, 8197, 5) // 瞬间治疗
                ),
                null
        ));
        PRESET_KITS.put(4, new ShopItemData(
                "坦克",
                Material.DIAMOND_CHESTPLATE,
                100, 0, 0,
                0, 0,
                Arrays.asList(
                        new ItemStack(Material.DIAMOND_HELMET),
                        new ItemStack(Material.DIAMOND_CHESTPLATE),
                        new ItemStack(Material.DIAMOND_LEGGINGS),
                        new ItemStack(Material.DIAMOND_BOOTS),
                        new ItemStack(Material.STONE_SWORD)
                ),
                null
        ));
        PRESET_KITS.put(5, new ShopItemData(
                "刺客",
                Material.CHAINMAIL_CHESTPLATE,
                200, 0, 0,
                0, 0,
                Arrays.asList(
                        new ItemStack(Material.CHAINMAIL_HELMET),
                        new ItemStack(Material.CHAINMAIL_CHESTPLATE),
                        new ItemStack(Material.CHAINMAIL_LEGGINGS),
                        new ItemStack(Material.CHAINMAIL_BOOTS),
                        new ItemStack(Material.DIAMOND_SWORD),
                        new ItemStack(Material.POTION, 6, (short) 8270) // 隐身药水
                ),
                null
        ));
        PRESET_KITS.put(6, new ShopItemData(
                "战士",
                Material.DIAMOND_SWORD,
                500, 0, 0,
                0, 0,
                Arrays.asList(
                        new ItemStack(Material.DIAMOND_HELMET),
                        new ItemStack(Material.DIAMOND_CHESTPLATE),
                        new ItemStack(Material.DIAMOND_LEGGINGS),
                        new ItemStack(Material.DIAMOND_BOOTS),
                        new ItemStack(Material.DIAMOND_SWORD)
                ),
                Collections.singletonMap(Enchantment.DAMAGE_ALL, 1)
        ));
        // 在 ShopItem.java 中修改 PRESET_KITS 的全体加速配置
        PRESET_KITS.put(7, new ShopItemData(
                "加速石", // 修改名称
                Material.EMERALD, // 修改物品类型为绿宝石
                150, 75, 30,
                3, 0,
                Collections.emptyList(),
                null
        ));
    }



    private static ItemStack createEnchantedBow() {
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta meta = bow.getItemMeta();
        meta.addEnchant(Enchantment.ARROW_INFINITE, 1, true);
        bow.setItemMeta(meta);
        return bow;
    }

    private static ItemStack createMagicBall() {
        ItemStack ball = new ItemStack(Material.SLIME_BALL);
        ItemMeta meta = ball.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "魔法球"); // 使用绿色字体.
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "右键使用：获得速度II和治疗效果");
        lore.add(ChatColor.GRAY + "冷却时间: 30秒");
        meta.setLore(lore);
        ball.setItemMeta(meta);
        return ball;
    }

    private static ItemStack createPotion(Material type, int data, int amount) {
        ItemStack potion = new ItemStack(type, amount);
        potion.setDurability((short) data);
        return potion;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
                event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();


        // 添加更精确的物品检查
        if (item != null
                && item.getType() == Material.SLIME_BALL
                && item.hasItemMeta()) {

            ItemMeta meta = item.getItemMeta();
            String displayName = meta.getDisplayName();

            // 使用 contains 代替精确匹配，避免颜色代码问题
            if (displayName != null && displayName.contains("魔法球")) {

                long now = System.currentTimeMillis();
                long lastUse = lastMagicBallUse.getOrDefault(player.getUniqueId(), 0L);

                if (now - lastUse < 30000) {
                    player.sendMessage(ChatColor.RED + "技能冷却中，剩余时间: " +
                            (30 - (now - lastUse) / 1000) + "秒");
                    event.setCancelled(true);
                    return;
                }

                // 应用效果
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HEAL, 1, 1));

                // 对周围玩家施加减速
                player.getWorld().getPlayers().stream()
                        .filter(p -> p != player && p.getLocation().distance(player.getLocation()) < 10)
                        .forEach(p -> p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 300, 0)));

                lastMagicBallUse.put(player.getUniqueId(), now);
                event.setCancelled(true);
            }
        }
    }

    // 添加 shopConfig 的 Getter
    public FileConfiguration getShopConfig() {
        return shopConfig;
    }

    public ShopItem(JavaPlugin plugin) {
        this.plugin = plugin;
        shopFile = new File(plugin.getDataFolder(), "shop.yml");
        
        if (!shopFile.exists()) {
            try {
                shopFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        shopConfig = YamlConfiguration.loadConfiguration(shopFile);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        initializePresets();
    }

    private void initializePresets() {
        for (Map.Entry<Integer, ShopItemData> entry : PRESET_KITS.entrySet()) {
            String path = "presets." + entry.getKey();
            if (!shopConfig.contains(path)) {
                ShopItemData data = entry.getValue();
                shopConfig.set(path + ".name", data.getName());
                shopConfig.set(path + ".price_iron", data.getPriceIron());
                shopConfig.set(path + ".price_gold", data.getPriceGold());
                shopConfig.set(path + ".price_diamond", data.getPriceDiamond());
                shopConfig.set(path + ".slot", data.getSlot());
                shopConfig.set(path + ".enabled", true);
            }
        }
        saveConfig();
    }

    public void toggleShopItem(String identifier, boolean enable) {
        if (identifier.matches("\\d+")) {
            int id = Integer.parseInt(identifier);
            shopConfig.set("presets." + id + ".enabled", enable);
        } else {
            shopConfig.set("kits." + identifier + ".enabled", enable);
        }
        saveConfig();
    }


    public boolean canAfford(Player player, ShopItemData data) {
        return player.getInventory().contains(Material.IRON_INGOT, data.getPriceIron()) &&
                player.getInventory().contains(Material.GOLD_INGOT, data.getPriceGold()) &&
                player.getInventory().contains(Material.DIAMOND, data.getPriceDiamond());
    }

    // 修改购买执行逻辑
    public void purchaseItem(Player player, ShopItemData data) {
        // 扣除资源
        player.getInventory().removeItem(
                new ItemStack(Material.IRON_INGOT, data.getPriceIron()),
                new ItemStack(Material.GOLD_INGOT, data.getPriceGold()),
                new ItemStack(Material.DIAMOND, data.getPriceDiamond())
        );

        // 在发放装备前添加判断
        if (data != PRESET_KITS.get(7)) { // ID7 是全体效果
            ((KitBattle) plugin).chosenKitPlayers.add(player.getName());
        }

        // 发放装备并添加标记
        for (ItemStack item : data.getItems()) {
            ItemStack clone = item.clone();
            // 添加附魔
            if (data.getEnchants() != null) {
                for (Map.Entry<Enchantment, Integer> entry : data.getEnchants().entrySet()) {
                    clone.addUnsafeEnchantment(entry.getKey(), entry.getValue());
                }
            }
            // 添加Lore标记
            ItemMeta meta = clone.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.getLore() == null ? new ArrayList<>() : meta.getLore();
                lore.add(ChatColor.GRAY + "职业装备"); // 添加标记
                meta.setLore(lore);
                clone.setItemMeta(meta);
            }
            player.getInventory().addItem(clone);
        }

        // 在 purchaseItem 方法中修改加速石发放逻辑
        if (data.getName().equals("加速石")) {
            ItemStack speedStone = createSpeedStone();
            GameManager gameManager = ((KitBattle) plugin).getBattleManager().getGameManager();
            String team = gameManager.getPlayerTeam(player);

            if (team != null) {
                List<Player> teammates = gameManager.getTeamPlayers(team);
                for (Player mate : teammates) {
                    mate.getInventory().addItem(speedStone.clone());
                    mate.sendMessage(ChatColor.GREEN + "获得队伍加速石！");
                }
            }
        } else {
            ((KitBattle) plugin).chosenKitPlayers.add(player.getName());
        }

        // 新增：防止丢弃职业装备
        player.getInventory().forEach(item -> {
            if(item != null && isKitItem(item)) {
                ItemMeta meta = item.getItemMeta();
                item.setItemMeta(meta);
            }
        });
    }

    // 新增创建加速石的方法
    private ItemStack createSpeedStone() {
        ItemStack stone = new ItemStack(Material.EMERALD);
        ItemMeta meta = stone.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "加速石");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "右键使用获得速度 II 效果（15秒）");
        lore.add(ChatColor.GRAY + "冷却时间：30秒");
        meta.setLore(lore);
        stone.setItemMeta(meta);
        return stone;
    }

    // 在 ShopItem.java 中添加右键使用监听
    @EventHandler
    public void onPlayerUseSpeedStone(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
                event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item != null && item.getType() == Material.EMERALD && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.getDisplayName().equals(ChatColor.GREEN + "加速石")) {
                event.setCancelled(true);

                // 冷却检查
                long now = System.currentTimeMillis();
                if (lastMagicBallUse.containsKey(player.getUniqueId()) &&
                        now - lastMagicBallUse.get(player.getUniqueId()) < 30000) {
                    player.sendMessage(ChatColor.RED + "技能冷却中，剩余时间：" +
                            (30 - (now - lastMagicBallUse.get(player.getUniqueId())) / 1000) + "秒");
                    return;
                }

                meta.setLore(meta.getLore()); // 刷新物品meta
                item.setItemMeta(meta);

                // 应用效果
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SPEED,
                        300, // 15秒
                        1,   // 速度 II
                        true
                ));

                lastMagicBallUse.put(player.getUniqueId(), now);
                player.sendMessage(ChatColor.GREEN + "已获得速度提升！");
            }
        }
    }

    // 修改isKitItem方法，检查Lore标记
    private boolean isKitItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasLore() && meta.getLore().contains(ChatColor.GRAY + "职业装备");
    }

    public ItemStack buildShopItem(String kitName) {
        if (shopConfig.contains("kits." + kitName)) {
            return createItem(
                    shopConfig.getString("kits." + kitName + ".name"),
                    Material.valueOf(shopConfig.getString("kits." + kitName + ".material")),
                    shopConfig.getInt("kits." + kitName + ".price_iron", 0),
                    shopConfig.getInt("kits." + kitName + ".price_gold", 0),
                    shopConfig.getInt("kits." + kitName + ".price_diamond", 0),
                    shopConfig.getInt("kits." + kitName + ".slot", 0),
                    shopConfig.getStringList("kits." + kitName + ".lore")
            );
        }
        return null;
    }

    public ItemStack buildPresetItem(int presetId) {
        ShopItemData data = PRESET_KITS.get(presetId);
        return createItem(data);
    }

    private ItemStack createItem(ShopItemData data) {
        ItemStack item = new ItemStack(data.getMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + data.getName());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "价格:");
        lore.add(ChatColor.WHITE + " 铁锭: " + data.getPriceIron());
        lore.add(ChatColor.WHITE + " 金锭: " + data.getPriceGold());
        lore.add(ChatColor.WHITE + " 钻石: " + data.getPriceDiamond());
        lore.add(ChatColor.YELLOW + "点击购买");

        meta.setLore(lore);
        item.setItemMeta(meta);

        if (data.getEnchants() != null) {
            for (Map.Entry<Enchantment, Integer> entry : data.getEnchants().entrySet()) {
                item.addUnsafeEnchantment(entry.getKey(), entry.getValue());
            }
        }

        return item;
    }

    private ItemStack createItem(String name, Material material, int priceIron, int priceGold, int priceDiamond, int slot, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + name);

        List<String> fullLore = new ArrayList<>();
        fullLore.add(ChatColor.GRAY + "价格:");
        fullLore.add(ChatColor.WHITE + " 铁锭: " + priceIron);
        fullLore.add(ChatColor.WHITE + " 金锭: " + priceGold);
        fullLore.add(ChatColor.WHITE + " 钻石: " + priceDiamond);
        fullLore.addAll(lore);

        meta.setLore(fullLore);
        item.setItemMeta(meta);
        return item;
    }


    public void saveConfig() {
        try {
            shopConfig.save(shopFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static class ShopItemData {
        private final String name;
        private final Material material;
        private final int priceIron;
        private final int priceGold;
        private final int priceDiamond;
        private final int x;
        private final int y;
        private final int slot;
        private final List<ItemStack> items;
        private final Map<Enchantment, Integer> enchants;

        public ShopItemData(String name, Material material,
                            int priceIron, int priceGold, int priceDiamond,
                            int x, int y,
                            List<ItemStack> items, Map<Enchantment, Integer> enchants) {
            this.name = name;
            this.material = material;
            this.priceIron = priceIron;
            this.priceGold = priceGold;
            this.priceDiamond = priceDiamond;
            this.x = x;
            this.y = y;
            this.items = items;
            this.enchants = enchants;
            this.slot = x + y * 9;
        }

        public String getName() { return name; }
        public Material getMaterial() { return material; }
        public int getPriceIron() { return priceIron; }
        public int getPriceGold() { return priceGold; }
        public int getPriceDiamond() { return priceDiamond; }
        public int getX() { return x; }
        public int getY() { return y; }
        public int getSlot() { return slot; }
        public List<ItemStack> getItems() { return items; }
        public Map<Enchantment, Integer> getEnchants() { return enchants; }
    }
}