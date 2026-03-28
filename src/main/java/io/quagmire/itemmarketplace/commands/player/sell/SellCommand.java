package io.quagmire.itemmarketplace.commands.player.sell;

import io.quagmire.itemmarketplace.ItemMarketplacePlugin;
import io.quagmire.itemmarketplace.commands.player.PlayerCommand;
import io.quagmire.itemmarketplace.menu.ConfirmationMenu;
import io.quagmire.itemmarketplace.messages.Message;
import io.quagmire.itemmarketplace.model.MarketplaceListing;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.ChatColor;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.ArrayList;

public class SellCommand extends PlayerCommand {
    public SellCommand(ItemMarketplacePlugin plugin, Command command, String[] args, CommandSender sender) {
        super(plugin, command, args, sender);
        setDescription("List the item in your hand for sale.");
        setPermission(getPermissionPrefix() + ".sell");
        setSyntax("<price>");
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
        
        if (args.length < 1) {
            messageSender(Message.INVALID_NUMBER);
            return false;
        }
        
        try {
            double price = Double.parseDouble(args[0]);
            if (price <= 0) {
                messageSender(Message.SELL_INVALID_PRICE);
                return false;
            }
        } catch (NumberFormatException e) {
            messageSender(Message.INVALID_NUMBER);
            return false;
        }
        
        // Check if player is holding an item
        Player player = (Player) sender;
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            messageSender(Message.SELL_NO_ITEM);
            return false;
        }
        
        return true;
    }

    @Override
    public void execute() {
        try {
            Player player = (Player) sender;
            ItemStack itemToSell = player.getInventory().getItemInMainHand().clone();
            double price = Double.parseDouble(args[0]);
            
            // Create confirmation dialog for selling
            plugin.getScheduler().runAtEntity(player, (task) -> {
                // Create placeholders for the confirmation dialog
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("price", String.format("%.2f", price));
                
                // Build the confirmation menu with the item
                ItemStack confirmItem = itemToSell.clone();
                ItemMeta meta = confirmItem.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                    lore.add("");
                    lore.add(ChatColor.translateAlternateColorCodes('&', 
                        plugin.getMessagesManager().get(Message.SELL_CONFIRM_PRICE.name().toLowerCase())
                            .replace("%price%", placeholders.get("price"))));
                    lore.add(ChatColor.translateAlternateColorCodes('&', 
                        plugin.getMessagesManager().get(Message.SELL_CONFIRM_DESCRIPTION.name().toLowerCase())));
                    meta.setLore(lore);
                    confirmItem.setItemMeta(meta);
                }
                
                // Create confirmation and cancel actions
                Consumer<Player> onConfirm = p -> {
                    try {
                        // List the item for sale
                        MarketplaceListing listing = plugin.getListingManager().createListing(
                            p, 
                            itemToSell, 
                            BigDecimal.valueOf(price),
                            null  // No expiry for now
                        );
                        
                        // Remove the item from the player's inventory
                        p.getInventory().setItemInMainHand(null);
                        
                        // Send confirmation message
                        String message = plugin.getMessagesManager().get(Message.SELL_SUCCESS.name().toLowerCase())
                            .replace("%price%", placeholders.get("price"));
                        p.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                        
                        // Play success sound
                        p.playSound(p.getLocation(), "entity.experience_orb.pickup", 1.0f, 1.0f);
                        
                    } catch (SQLException e) {
                        p.sendMessage(plugin.getMessagesManager().get(Message.SELL_FAILURE.name().toLowerCase()));
                        e.printStackTrace();
                    }
                };
                
                Consumer<Player> onCancel = p -> {
                    // Just close the menu and do nothing
                };
                
                // Create and register the confirmation menu
                ConfirmationMenu confirmationMenu = new ConfirmationMenu(
                    plugin,
                    plugin.getMessagesManager().get(Message.SELL_CONFIRM_TITLE.name().toLowerCase()),
                    confirmItem,
                    onConfirm,
                    onCancel,
                    placeholders
                );
                
                // Register the menu temporarily
                String menuName = "sell_confirm_" + player.getUniqueId().toString().substring(0, 8);
                plugin.getMenuManager().register(menuName, confirmationMenu);
                
                // Open the confirmation menu
                plugin.getMenuManager().openMenu(player, player, menuName);
            });
        }
        catch (Exception e) {
            e.printStackTrace();
            messageSender(Message.ERROR_GENERIC);
        }
    }

    @Override
    public List<String> tab() {
        if (args.length == 1) {
            return Arrays.asList("10", "50", "100", "500", "1000");
        }
        return Collections.emptyList();
    }

    @Override
    public String subcommand() {
        return "sell";
    }
} 