package com.oftx.soha;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.*;

public class Soha extends JavaPlugin implements CommandExecutor {

    private static final int ERR_NO_MIN_AMOUNT = -1;
    private static final int ERR_NO_ENOUGH_MONEY = -3;
    private static final int ERR_UNKNOWN = -4;
    private static final int OK_SUCCEED = 0;
    private static final int minAmount = 60;
    private static final int times = 3;

    private static Economy economy = null;

    private static Boolean enableBedrockSupport = false;

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("Vault未找到或未安装经济插件！插件将禁用...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!checkPlugins()) {
            getLogger().warning("Geyser或Floodgate未找到！无法为基岩版玩家发送表单。");
        } else {
            enableBedrockSupport = true;
        }

        this.getCommand("soha").setExecutor(this);
    }

    @Override
    public void onDisable() {
        getLogger().info("Soha 已禁用");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    private boolean checkPlugins() {
        if (getServer().getPluginManager().getPlugin("Geyser-Spigot") == null) {
            return false;
        }
        if (getServer().getPluginManager().getPlugin("floodgate") == null) {
            return false;
        }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(helpMsg());
            return true;
        }

        String action = args[0];
        double amount = 0;

        if (action.equalsIgnoreCase("put") && args.length == 2) {
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "请输入有效的金额!");
                return true;
            }
        }

        double  moneyAmount = getMoney(player);
        SohaResult resultData;

        switch (action.toLowerCase()) {
            case "put":
                resultData = startSoha(player.getUniqueId().toString(), moneyAmount, amount);
                sendResult(player, resultData, amount, false);
                break;
            case "allin":
                amount = (int) moneyAmount;
                resultData = startSoha(player.getUniqueId().toString(), moneyAmount, amount);
                sendResult(player, resultData, amount, false);
                break;
            case "gui":
                sendSohaForm(player, "");
                break;
            default:
                player.sendMessage(helpMsg());
                break;
        }
        return true;
    }

    private String helpMsg() {
        return ChatColor.YELLOW + "[ " + ChatColor.AQUA + "梭哈" + ChatColor.YELLOW + " ]\n" +
                "搏一搏，单车变摩托！\n" +
                "投入金币下注后随机获得投入总量的正负" + times + "倍范围数量的金币, 最小投入金额为" + minAmount + "。\n" +
                ChatColor.GRAY + "使用 " + ChatColor.GOLD + "/soha put <金币数量> " + ChatColor.GRAY + "投入金币进行下注\n" +
                ChatColor.GRAY + "使用 " + ChatColor.GOLD + "/soha allin " + ChatColor.GRAY + "一键全投";
    }

    private double getMoney(Player player) {
        return economy.getBalance(player);
    }

    private boolean setMoney(String uuid, double amount) {
        Player player = Bukkit.getPlayer(UUID.fromString(uuid));
        if (player == null) return false;

        double currentBalance = economy.getBalance(player);
        return economy.withdrawPlayer(player, currentBalance).transactionSuccess() &&
                economy.depositPlayer(player, amount).transactionSuccess();
    }

    private SohaResult startSoha(String uuid, double moneyAmount, double amount) {
        if (amount < minAmount) return new SohaResult(ERR_NO_MIN_AMOUNT, 0, 0, 0);
        if (moneyAmount < amount) return new SohaResult(ERR_NO_ENOUGH_MONEY, 0, 0, 0);

        Random random = new Random();
        double randomRate = random.nextDouble() * times * 2 - times;
        double result = amount * randomRate;
        double resultAmount = Math.max(moneyAmount + result, 0);

        if (!setMoney(uuid, resultAmount)) {
            return new SohaResult(ERR_UNKNOWN, 0, 0, 0);
        }

        return new SohaResult(OK_SUCCEED, result, randomRate * 100, resultAmount);
    }

    private void sendResult(Player player, SohaResult resultData, double amount, boolean isGUI) {
        int returnValue = resultData.status;
        double result = resultData.result;
        double randomRate = resultData.randomRate;
        double resultAmount = resultData.resultAmount;
        ChatColor color = randomRate >= 0 ? ChatColor.GREEN : ChatColor.RED;

        switch (returnValue) {
            case ERR_NO_ENOUGH_MONEY:
                if (!isGUI) player.sendMessage(ChatColor.RED + "下注失败： 您的金币不足！");
                break;
            case ERR_NO_MIN_AMOUNT:
                if (!isGUI) player.sendMessage(ChatColor.RED + "下注失败： 最小下注金额为 " + minAmount + " 金币");
                break;
            case ERR_UNKNOWN:
                if (!isGUI) player.sendMessage(ChatColor.RED + "遇到未知错误");
                break;
            case OK_SUCCEED:
                try {
                    player.sendMessage(ChatColor.GREEN + "下注成功!");
                    delayAndSendMessage(player, 500, "下注金币： " + String.format("%.0f", amount));
                    delayAndSendMessage(player, 2000, "抽到倍率： " + color + (result >= 0 ? '+' : "") + String.format("%.2f", randomRate) + "%");
                    delayAndSendMessage(player, 3500, "梭哈结果： " + color + (result >= 0 ? '+' : "") + String.format("%.0f", result));
                    delayAndSendMessage(player, 5000, "剩余金币： " + String.format("%.0f", resultAmount));
                    delayAndSendMessage(player, 5500, "欢迎再次下注！");
                    break;
                } catch (Exception e) {
                    // 玩家可能会在输出结果时退出
                    return;
                }
        }

        if (isGUI) {
            switch (returnValue) {
                case ERR_NO_ENOUGH_MONEY:
                    sendSohaForm(player, ChatColor.RED + "下注失败： 您的金币不足！");
                    break;
                case ERR_NO_MIN_AMOUNT:
                    sendSohaForm(player, ChatColor.RED + "下注失败： 最小下注金额为 " + minAmount + " 金币");
                    break;
                case ERR_UNKNOWN:
                    sendSohaForm(player, ChatColor.RED + "遇到未知错误");
                    break;
                case OK_SUCCEED:
                    delayAndSendSohaForm(player, 6000, "上局结果: " + (result >= 0 ? (ChatColor.GREEN + "+") : ChatColor.RED) + String.format("%.0f", result));
                    break;
            }
        }
    }

    private void sendSohaForm(Player player, String prompt) {
        if (!enableBedrockSupport) {
            player.sendMessage(ChatColor.RED + "不支持打开表单");
            return;
        }

        if (!FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "只有基岩版玩家才能打开表单GUI");
            return;
        }

        double money = getMoney(player);
        List<String> amounts = new ArrayList<>();
        int step = 24;
        int stepMoney = (int) Math.floor((money - minAmount + 1) / step);
        boolean hasEnoughMoney = money > minAmount;

        if (money - minAmount + 1 < step && hasEnoughMoney) {
            for (int i = 0; i < money - minAmount + 1; i++) {
                amounts.add(String.valueOf(i + minAmount));
            }
        } else {
            for (int i = 0; i < step; i++) {
                amounts.add(String.valueOf((stepMoney * i) + minAmount));
            }
            amounts.add(String.valueOf((int) money));
        }

        String title = "娱 · 乐 · 梭 · 哈";
        String content = "§7投入金币下注后, 可以随机获得投入总量的正负 " + times + " 倍范围数量的金币, 最小投入数量为 " + minAmount;

        CustomForm.Builder formBuilder = CustomForm.builder()
                .title(title)
                .label(content);

        if (!Objects.equals(prompt, "")) {
            formBuilder.label(prompt);
        }

        if (!hasEnoughMoney) {
            formBuilder.label("§c您没有足够的金币进行梭哈！");
        } else {
            formBuilder.stepSlider("\n§7滑动下面滑块以设置投入金币数量。§r\n投入数量", amounts);
        }

        formBuilder.validResultHandler((floodgatePlayer, response) -> {
            if (!hasEnoughMoney) {
                return;
            }

            int selectedIndex = response.next();
            int sohaAmount = Integer.parseInt(amounts.get(selectedIndex));
            SohaResult resultData = startSoha(player.getUniqueId().toString(), money, sohaAmount);
            sendResult(player, resultData, sohaAmount, true);
        });

        FloodgateApi.getInstance().getPlayer(player.getUniqueId()).sendForm(formBuilder.build());
    }

    private void delayAndSendMessage(Player player, long milliseconds, String message) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            player.sendMessage(message);
        }, milliseconds / 50); // 将毫秒转换为游戏ticks（1秒 = 20 ticks）
    }

    private void delayAndSendSohaForm(Player player, long milliseconds, String prompt) {
        Bukkit.getScheduler().runTaskLater(this, () -> sendSohaForm(player, prompt), milliseconds / 50);
    }
}

