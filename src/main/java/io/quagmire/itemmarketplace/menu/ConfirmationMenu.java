package io.quagmire.itemmarketplace.menu;

import io.quagmire.core.menu.linked.LinkedMenu;
import io.quagmire.itemmarketplace.ItemMarketplacePlugin;
import io.quagmire.itemmarketplace.messages.Message;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Generic confirmation menu for purchases or other actions
 */
public class ConfirmationMenu extends LinkedMenu<ItemMarketplacePlugin> {
    private static final int CONFIRM_SLOT = 11;
    private static final int CANCEL_SLOT = 15;
    private static final int INFO_SLOT = 13;
    
    private final String title;
    private final ItemStack displayItem;
    private final Consumer<Player> onConfirm;
    private final Consumer<Player> onCancel;
    private final Map<String, String> placeholders;
    
    /**
     * Creates a new confirmation menu with the specified options
     * 
     * @param plugin The plugin instance
     * @param title The title of the menu
     * @param displayItem The item to display in the middle
     * @param onConfirm Action to perform on confirmation
     * @param onCancel Action to perform on cancellation (or null)
     * @param placeholders Placeholders to use in messages
     */
    public ConfirmationMenu(
        ItemMarketplacePlugin plugin, 
        String title, 
        ItemStack displayItem, 
        Consumer<Player> onConfirm,
        Consumer<Player> onCancel,
        Map<String, String> placeholders
    ) {
        super(plugin, "confirmation");
        this.title = ChatColor.translateAlternateColorCodes('&', title);
        this.displayItem = displayItem;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.placeholders = placeholders;
    }
    
    @Override
    protected Map<String, String> getPlaceholders(Player player, OfflinePlayer target, int slot) {
        return placeholders != null ? placeholders : new HashMap<>();
    }
    
    @Override
    protected Map<Integer, MenuItem> getAdditionalItems(Player player, OfflinePlayer target) {
        Map<Integer, MenuItem> items = new HashMap<>();
        
        // Add confirmation button
        ItemStack confirmItem = new ItemStack(Material.LIME_WOOL);
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        confirmMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "CONFIRM");
        List<String> confirmLore = new ArrayList<>();
        confirmLore.add(ChatColor.GRAY + "Click to confirm");
        confirmMeta.setLore(confirmLore);
        confirmItem.setItemMeta(confirmMeta);
        
        // Add cancel button
        ItemStack cancelItem = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "CANCEL");
        List<String> cancelLore = new ArrayList<>();
        cancelLore.add(ChatColor.GRAY + "Click to cancel");
        cancelMeta.setLore(cancelLore);
        cancelItem.setItemMeta(cancelMeta);
        
        // Add the display item in the middle
        items.put(INFO_SLOT, new MenuItem(displayItem.clone(), null));
        
        // Add confirm and cancel buttons
        items.put(CONFIRM_SLOT, new MenuItem(confirmItem, null));
        items.put(CANCEL_SLOT, new MenuItem(cancelItem, null));
        
        // Add border items
        ItemStack borderItem = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = borderItem.getItemMeta();
        borderMeta.setDisplayName(" ");
        borderItem.setItemMeta(borderMeta);
        
        for (int i = 0; i < 27; i++) {
            if (i != CONFIRM_SLOT && i != CANCEL_SLOT && i != INFO_SLOT && !items.containsKey(i)) {
                items.put(i, new MenuItem(borderItem, null));
            }
        }
        
        return items;
    }
    
    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        
        if (slot == CONFIRM_SLOT) {
            if (onConfirm != null) {
                onConfirm.accept(player);
            }
            player.closeInventory();
            return;
        }
        
        if (slot == CANCEL_SLOT) {
            if (onCancel != null) {
                onCancel.accept(player);
            }
            player.closeInventory();
            return;
        }
        
        // Let parent handle other clicks
        super.handleClick(event);
    }
} 