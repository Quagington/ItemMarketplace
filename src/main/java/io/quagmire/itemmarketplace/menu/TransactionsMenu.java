package io.quagmire.itemmarketplace.menu;

import io.quagmire.core.menu.linked.LinkedMenu;
import io.quagmire.itemmarketplace.ItemMarketplacePlugin;
import io.quagmire.itemmarketplace.messages.Message;
import io.quagmire.itemmarketplace.model.MarketplaceTransaction;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * TransactionsMenu - Displays a player's transaction history
 */
public class TransactionsMenu extends LinkedMenu<ItemMarketplacePlugin> {
    private static final int ITEMS_PER_PAGE = 45;
    private static final int NEXT_PAGE_SLOT = 53;
    private static final int PREV_PAGE_SLOT = 45;
    
    private final Map<UUID, Integer> playerPages = new HashMap<>();
    private final DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    private ItemStack noTransactionsItem;
    private ItemStack errorItem;
    private ItemStack prevPageButton;
    private ItemStack nextPageButton;

    public TransactionsMenu(ItemMarketplacePlugin plugin, String name) {
        super(plugin, name);
    }
    
    /**
     * Gets the current page for a player
     */
    private int getCurrentPage(Player player) {
        return playerPages.getOrDefault(player.getUniqueId(), 0);
    }
    
    /**
     * Sets the current page for a player
     */
    private void setCurrentPage(Player player, int page) {
        playerPages.put(player.getUniqueId(), page);
    }
    
    @Override
    public void openInventory(Player player, OfflinePlayer target) {
        // Reset to first page when opening
        setCurrentPage(player, 0);
        super.openInventory(player, target);
    }
    
    @Override
    protected Map<String, String> getPlaceholders(Player player, OfflinePlayer target, int slot) {
        Map<String, String> placeholders = new HashMap<>();
        
        // Add page information
        int currentPage = getCurrentPage(player);
        placeholders.put("current_page", String.valueOf(currentPage + 1));
        
        return placeholders;
    }
    
    @Override
    protected Map<Integer, MenuItem> getAdditionalItems(Player player, OfflinePlayer target) {
        Map<Integer, MenuItem> items = new HashMap<>();
        int currentPage = getCurrentPage(player);
        
        try {
            // Get player's transactions
            List<MarketplaceTransaction> transactions = plugin.getListingManager().getPlayerTransactionHistory(player.getUniqueId());
            
            // Handle empty transaction history
            if (transactions.isEmpty()) {
                ItemStack noTransactionsStack = new ItemStack(Material.BARRIER);
                ItemMeta meta = noTransactionsStack.getItemMeta();
                meta.setDisplayName(ChatColor.RED + "No Transactions");
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "You don't have any transaction history yet.");
                meta.setLore(lore);
                noTransactionsStack.setItemMeta(meta);
                
                items.put(22, new MenuItem(noTransactionsStack, null));
                return items;
            }
            
            // Calculate max pages
            int maxPage = (int) Math.ceil(transactions.size() / (double) ITEMS_PER_PAGE) - 1;
            maxPage = Math.max(0, maxPage); // Ensure at least one page
            
            // Ensure current page is valid
            if (currentPage > maxPage) {
                currentPage = maxPage;
                setCurrentPage(player, currentPage);
            }
            
            // Calculate start and end indices for current page
            int startIndex = currentPage * ITEMS_PER_PAGE;
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, transactions.size());
            
            // Add transactions for current page
            for (int i = startIndex; i < endIndex; i++) {
                MarketplaceTransaction transaction = transactions.get(i);
                if (transaction == null) continue;
                
                int slot = (i - startIndex);
                
                try {
                    // Create item display for the transaction
                    ItemStack displayItem = transaction.getItemStack().clone();
                    if (displayItem == null || displayItem.getType() == Material.AIR) {
                        plugin.getLogger().warning("Invalid item in transaction ID: " + transaction.getTransactionId());
                        continue;
                    }
                    
                    // Add lore with transaction details
                    ItemMeta meta = displayItem.getItemMeta();
                    List<String> lore = new ArrayList<>();
                    if (meta.hasLore()) {
                        lore.addAll(meta.getLore());
                        lore.add("");
                    }
                    
                    // Format transaction info
                    String date = dateFormat.format(transaction.getTransactionDateUtc());
                    String price = decimalFormat.format(transaction.getPricePaid());
                    
                    // Add transaction details
                    lore.add(ChatColor.GRAY + "Transaction ID: " + ChatColor.WHITE + transaction.getTransactionId());
                    lore.add(ChatColor.GRAY + "Date: " + ChatColor.WHITE + date);
                    
                    // Show whether player was buyer or seller
                    if (transaction.getBuyerUuid().equals(player.getUniqueId())) {
                        lore.add(ChatColor.GRAY + "Type: " + ChatColor.GREEN + "Purchased");
                        OfflinePlayer seller = plugin.getServer().getOfflinePlayer(transaction.getSellerUuid());
                        String sellerName = seller.getName() != null ? seller.getName() : "Unknown";
                        lore.add(ChatColor.GRAY + "Seller: " + ChatColor.WHITE + sellerName);
                    } else {
                        lore.add(ChatColor.GRAY + "Type: " + ChatColor.YELLOW + "Sold");
                        OfflinePlayer buyer = plugin.getServer().getOfflinePlayer(transaction.getBuyerUuid());
                        String buyerName = buyer.getName() != null ? buyer.getName() : "Unknown";
                        lore.add(ChatColor.GRAY + "Buyer: " + ChatColor.WHITE + buyerName);
                    }
                    
                    lore.add(ChatColor.GRAY + "Price: " + ChatColor.WHITE + price);
                    
                    // Add black market indicator if applicable
                    if (transaction.isBlackMarketTransaction()) {
                        lore.add(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "[BLACK MARKET TRANSACTION]");
                    }
                    
                    meta.setLore(lore);
                    displayItem.setItemMeta(meta);
                    
                    // Add to menu
                    items.put(slot, new MenuItem(displayItem, null));
                } catch (Exception e) {
                    plugin.getLogger().warning("Error processing transaction ID " + transaction.getTransactionId() + ": " + e.getMessage());
                }
            }
            
            // Add navigation buttons if needed
            if (maxPage > 0) {
                // Previous page button
                if (currentPage > 0) {
                    ItemStack prevButton = new ItemStack(Material.ARROW);
                    ItemMeta meta = prevButton.getItemMeta();
                    meta.setDisplayName(ChatColor.GREEN + "Previous Page");
                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.GRAY + "Page " + (currentPage) + " of " + (maxPage + 1));
                    meta.setLore(lore);
                    prevButton.setItemMeta(meta);
                    
                    items.put(PREV_PAGE_SLOT, new MenuItem(prevButton, null));
                }
                
                // Next page button
                if (currentPage < maxPage) {
                    ItemStack nextButton = new ItemStack(Material.ARROW);
                    ItemMeta meta = nextButton.getItemMeta();
                    meta.setDisplayName(ChatColor.GREEN + "Next Page");
                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.GRAY + "Page " + (currentPage + 2) + " of " + (maxPage + 1));
                    meta.setLore(lore);
                    nextButton.setItemMeta(meta);
                    
                    items.put(NEXT_PAGE_SLOT, new MenuItem(nextButton, null));
                }
            }
            
        } catch (SQLException e) {
            plugin.getLogger().severe("Error generating transactions menu: " + e.getMessage());
            e.printStackTrace();
            
            // Show error item
            ItemStack errorStack = new ItemStack(Material.BARRIER);
            ItemMeta meta = errorStack.getItemMeta();
            meta.setDisplayName(ChatColor.RED + "Error Loading Transactions");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "There was an error loading your transactions.");
            lore.add(ChatColor.GRAY + "Please try again later.");
            meta.setLore(lore);
            errorStack.setItemMeta(meta);
            
            items.put(22, new MenuItem(errorStack, null));
        }
        
        return items;
    }
    
    @Override
    public void handleClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        int currentPage = getCurrentPage(player);
        
        // Handle pagination
        if (slot == NEXT_PAGE_SLOT) {
            setCurrentPage(player, currentPage + 1);
            refresh(player);
            return;
        }
        
        if (slot == PREV_PAGE_SLOT) {
            setCurrentPage(player, currentPage - 1);
            refresh(player);
            return;
        }
        
        // Let parent handle other clicks
        super.handleClick(event);
    }
} 