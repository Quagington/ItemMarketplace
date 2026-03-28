package io.quagmire.itemmarketplace.commands.player.blackmarket;

import io.quagmire.itemmarketplace.ItemMarketplacePlugin;
import io.quagmire.itemmarketplace.commands.player.PlayerCommand;
import io.quagmire.itemmarketplace.menu.BlackMarketMenu;
import io.quagmire.itemmarketplace.messages.Message;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class BlackMarketCommand extends PlayerCommand {
    public BlackMarketCommand(ItemMarketplacePlugin plugin, Command command, String[] args, CommandSender sender) {
        super(plugin, command, args, sender);
        setDescription("Open the black market with discounted items.");
        setPermission(getPermissionPrefix() + ".blackmarket");
        setSyntax("");
    }

    @Override
    public boolean validate() {
        if (!(sender instanceof Player)) {
            messageSender(Message.PLAYER_ONLY);
            return false;
        }
        
        if (!sender.hasPermission(permission)) {
            messageSender(Message.NO_PERMISSIONS);
            return false;
        }
        
        return true;
    }

    @Override
    public void execute() {
        try {
            Player player = (Player) sender;
            plugin.getScheduler().runAtEntity(player, (task) -> {
                BlackMarketMenu blackMarketMenu = (BlackMarketMenu) plugin.getMenuManager().getMenu("blackmarket");
                if (blackMarketMenu == null) {
                    messageSender(Message.ERROR_GENERIC);
                    plugin.getLogger().severe("Black Market menu is not registered properly");
                    return;
                }
                
                // Refresh the black market with new items
                blackMarketMenu.refreshBlackMarket(player);
            });
        }
        catch (Exception e) {
            e.printStackTrace();
            messageSender(Message.ERROR_GENERIC);
        }
    }

    @Override
    public List<String> tab() {
        return Collections.emptyList();
    }

    @Override
    public String subcommand() {
        return "blackmarket";
    }
} 