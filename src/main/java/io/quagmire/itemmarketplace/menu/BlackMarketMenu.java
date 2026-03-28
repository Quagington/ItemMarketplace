package io.quagmire.itemmarketplace.menu;

import io.quagmire.itemmarketplace.ItemMarketplacePlugin;
import io.quagmire.itemmarketplace.messages.Message;
import io.quagmire.itemmarketplace.model.MarketplaceListing;
import io.quagmire.itemmarketplace.model.MarketplaceTransaction;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * BlackMarketMenu - Displays random items from the marketplace with 50% discount
 */
public class BlackMarketMenu extends ListingsMenu {
    private static final double DISCOUNT_RATE = 0.5; // 50% discount
    private static final int SELLER_BONUS_MULTIPLIER = 2; // 2x for sellers
    
    // Match these with the parent class
    private static final int NEXT_PAGE_SLOT = 53;
    private static final int PREV_PAGE_SLOT = 45;
    private static final int SORT_SLOT = 49;
    
    private final Random random = new Random();
    private List<MarketplaceListing> discountedListings;
    private final Map<UUID, Timestamp> lastRefreshed = new HashMap<>();
    private static final long REFRESH_COOLDOWN_MS = 3600000; // 1 hour cooldown
    
    public BlackMarketMenu(ItemMarketplacePlugin plugin, String name) {
        super(plugin, name);
        discountedListings = new ArrayList<>();
    }
    
    /**
     * Refreshes the black market with new random items
     */
    public void refreshBlackMarket(Player player) {
        UUID playerUuid = player.getUniqueId();
        Timestamp now = new Timestamp(System.currentTimeMillis());
        
        // Check cooldown
        if (lastRefreshed.containsKey(playerUuid)) {
            Timestamp lastRefresh = lastRefreshed.get(playerUuid);
            long timeDiff = now.getTime() - lastRefresh.getTime();
            
            if (timeDiff < REFRESH_COOLDOWN_MS) {
                long remainingMinutes = (REFRESH_COOLDOWN_MS - timeDiff) / 60000;
                player.sendMessage(plugin.getMessagesManager().get(Message.BLACKMARKET_COOLDOWN.name().toLowerCase())
                    .replace("%time%", String.valueOf(remainingMinutes)));
                return;
            }
        }
        
        try {
            // Get all listings and select random ones
            List<MarketplaceListing> allListings = plugin.getListingManager().getAllListings();
            if (allListings.isEmpty()) {
                player.sendMessage(plugin.getMessagesManager().get(Message.BLACKMARKET_NO_ITEMS.name().toLowerCase()));
                return;
            }
            
            // Randomly select up to 45 items
            int itemCount = Math.min(45, allListings.size());
            Set<Integer> selectedIndices = new HashSet<>();
            
            while (selectedIndices.size() < itemCount) {
                selectedIndices.add(random.nextInt(allListings.size()));
            }
            
            // Create discounted versions of the selected listings
            discountedListings = selectedIndices.stream()
                .map(allListings::get)
                .map(this::createDiscountedListing)
                .collect(Collectors.toList());
            
            // Update last refreshed time
            lastRefreshed.put(playerUuid, now);
            
            // Open menu
            player.sendMessage(plugin.getMessagesManager().get(Message.BLACKMARKET_REFRESHED.name().toLowerCase()));
            openInventory(player, player);
            
        } catch (Exception e) {
            plugin.getLogger().severe("Error refreshing black market: " + e.getMessage());
            player.sendMessage(plugin.getMessagesManager().get(Message.ERROR_GENERIC.name().toLowerCase()));
        }
    }
    
    /**
     * Creates a discounted copy of a listing for the black market
     */
    private MarketplaceListing createDiscountedListing(MarketplaceListing original) {
        // Create new discounted listing using the full constructor
        MarketplaceListing discounted = new MarketplaceListing(
            original.getListingId(),
            original.getSellerUuid(),
            original.getItemStack().clone(),
            original.getPrice().multiply(BigDecimal.valueOf(DISCOUNT_RATE)),
            original.isActive(),
            original.getCreateDateUtc(),
            original.getLastUpdateDateUtc(),
            original.getExpiryDateUtc(),
            true  // Mark as black market item
        );
        
        return discounted;
    }
    
    @Override
    protected Map<Integer, MenuItem> getAdditionalItems(Player player, OfflinePlayer target) {
        // Override the method to use our discounted listings
        // The base implementation will be used but we'll modify the listings source
        
        // Get the original method's implementation but substitute our listings
        Map<Integer, MenuItem> items = super.getAdditionalItems(player, target);
        
        // Add our special indicators to the items
        for (Map.Entry<Integer, MenuItem> entry : items.entrySet()) {
            // Skip navigation buttons
            if (entry.getKey() == NEXT_PAGE_SLOT || entry.getKey() == PREV_PAGE_SLOT || entry.getKey() == SORT_SLOT) {
                continue;
            }
            
            // If we can't directly access itemstack from MenuItem, we'll use the inventory instead
            // to add our black market tag in the handleClick method
        }
        
        return items;
    }
    
    @Override
    public void handleClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        
        // Verify inventory is valid
        if (event.getClickedInventory() == null || 
            !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }
        
        int slot = event.getSlot();
        
        try {
            // For navigation buttons, use parent implementation
            if (slot == NEXT_PAGE_SLOT || slot == PREV_PAGE_SLOT || slot == SORT_SLOT) {
                super.handleClick(event);
                return;
            }
            
            // For items, implement our own black market purchase logic
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                return;
            }
            
            // Modify the clicked item to add black market tag if it doesn't already have it
            if (clickedItem.hasItemMeta()) {
                ItemMeta meta = clickedItem.getItemMeta();
                List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                
                // Check if we need to add the black market tag
                String blackMarketTag = ChatColor.translateAlternateColorCodes('&', 
                    plugin.getMessagesManager().get(Message.BLACKMARKET_DISCOUNT_INFO.name().toLowerCase()));
                
                if (!lore.contains(blackMarketTag)) {
                    lore.add(blackMarketTag);
                    meta.setLore(lore);
                    clickedItem.setItemMeta(meta);
                }
            }
            
            // Find the listing that corresponds to this item
            MarketplaceListing selectedListing = null;
            for (MarketplaceListing listing : discountedListings) {
                if (listing.getItemStack().isSimilar(clickedItem) || 
                    listing.getItemStack().getType() == clickedItem.getType()) {
                    selectedListing = listing;
                    break;
                }
            }
            
            if (selectedListing == null) {
                super.handleClick(event);
                return;
            }
            
            // Show purchase confirmation dialog
            showPurchaseConfirmation(player, selectedListing, clickedItem);
            
        } catch (Exception e) {
            plugin.getLogger().severe("Error handling menu click: " + e.getMessage());
            player.sendMessage(plugin.getMessagesManager().get(Message.ERROR_GENERIC.name().toLowerCase()));
            player.playSound(player.getLocation(), "entity.villager.no", 1.0f, 1.0f);
        }
    }
    
    /**
     * Shows a confirmation dialog for black market purchases
     */
    private void showPurchaseConfirmation(Player player, MarketplaceListing listing, ItemStack displayItem) {
        // Create placeholders for confirmation dialog
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("price", new java.text.DecimalFormat("#,##0.00").format(listing.getPrice()));
        OfflinePlayer seller = plugin.getServer().getOfflinePlayer(listing.getSellerUuid());
        String sellerName = seller.getName() != null ? seller.getName() : "Unknown";
        placeholders.put("seller", sellerName);
        placeholders.put("discount", (int)(DISCOUNT_RATE * 100) + "%");
        
        // Create a copy of the item for confirmation
        ItemStack confirmItem = displayItem.clone();
        ItemMeta meta = confirmItem.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.translateAlternateColorCodes('&', 
                plugin.getMessagesManager().get(Message.PURCHASE_CONFIRM_PRICE.name().toLowerCase())
                    .replace("%price%", placeholders.get("price"))));
            lore.add(ChatColor.translateAlternateColorCodes('&', 
                plugin.getMessagesManager().get(Message.PURCHASE_CONFIRM_SELLER.name().toLowerCase())
                    .replace("%seller%", sellerName)));
            lore.add(ChatColor.GRAY + "Black Market Discount: " + ChatColor.RED + placeholders.get("discount"));
            meta.setLore(lore);
            confirmItem.setItemMeta(meta);
        }
        
        // Setup confirmation actions
        Consumer<Player> onConfirm = p -> {
            try {
                processBlackMarketPurchase(p, listing);
            } catch (Exception e) {
                plugin.getLogger().severe("Error processing black market purchase: " + e.getMessage());
                p.sendMessage(plugin.getMessagesManager().get(Message.ERROR_PROCESSING_PURCHASE.name().toLowerCase()));
                p.playSound(p.getLocation(), "entity.villager.no", 1.0f, 1.0f);
            }
        };
        
        Consumer<Player> onCancel = p -> {
            // Return to black market menu
            plugin.getScheduler().runAtEntity(p, (task) -> {
                openInventory(p, p);
            });
        };
        
        // Create confirmation menu
        ConfirmationMenu confirmationMenu = new ConfirmationMenu(
            plugin,
            plugin.getMessagesManager().get(Message.PURCHASE_CONFIRM_TITLE.name().toLowerCase()),
            confirmItem,
            onConfirm,
            onCancel,
            placeholders
        );
        
        // Register the menu temporarily
        String menuName = "blackmarket_confirm_" + player.getUniqueId().toString().substring(0, 8);
        plugin.getMenuManager().register(menuName, confirmationMenu);
        
        // Open the confirmation menu
        plugin.getMenuManager().openMenu(player, player, menuName);
    }
    
    /**
     * Process the black market purchase after confirmation
     */
    private void processBlackMarketPurchase(Player player, MarketplaceListing listing) throws SQLException {
        Optional<MarketplaceTransaction> transaction = plugin.getListingManager()
            .purchaseBlackMarketItem(player, listing.getListingId(), SELLER_BONUS_MULTIPLIER);
        
        if (transaction.isPresent()) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("amount", String.valueOf(listing.getItemStack().getAmount()));
            placeholders.put("price", new java.text.DecimalFormat("#,##0.00").format(listing.getPrice()));
            
            player.sendMessage(replacePlaceholders(
                plugin.getMessagesManager().get(Message.LISTING_PURCHASE_SUCCESS.name().toLowerCase()),
                placeholders));
            player.playSound(player.getLocation(), "entity.player.levelup", 1.0f, 1.0f);
            
            // Send discord webhook if configured
            plugin.getListingManager().sendDiscordWebhook(player, listing, transaction.get());
        } else {
            player.sendMessage(plugin.getMessagesManager().get(Message.LISTING_PURCHASE_FAILED.name().toLowerCase()));
            player.playSound(player.getLocation(), "entity.villager.no", 1.0f, 1.0f);
        }
        
        // Refresh the black market after purchase
        plugin.getScheduler().runAtEntity(player, (task) -> {
            refreshBlackMarket(player);
        });
    }
    
    @Override
    protected Map<String, String> getPlaceholders(Player player, OfflinePlayer target, int slot) {
        Map<String, String> placeholders = super.getPlaceholders(player, target, slot);
        placeholders.put("blackmarket_discount", (int)(DISCOUNT_RATE * 100) + "%");
        placeholders.put("seller_bonus", String.valueOf(SELLER_BONUS_MULTIPLIER) + "x");
        return placeholders;
    }
    
    // Helper method to replace placeholders in text
    private String replacePlaceholders(String text, Map<String, String> placeholders) {
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return ChatColor.translateAlternateColorCodes('&', result);
    }
} 