package io.quagmire.itemmarketplace.menu;

import io.quagmire.core.menu.linked.LinkedMenu;
import io.quagmire.core.utilities.item.ItemStackConfiguration;
import io.quagmire.itemmarketplace.ItemMarketplacePlugin;
import io.quagmire.itemmarketplace.messages.Message;
import io.quagmire.itemmarketplace.model.MarketplaceListing;
import io.quagmire.itemmarketplace.model.MarketplaceTransaction;
import io.quagmire.itemmarketplace.sort.ListingSortType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ListingsMenu extends LinkedMenu<ItemMarketplacePlugin> {
    private final NumberFormat numberFormat = NumberFormat.getInstance();
    private final DecimalFormat decimalFormat;
    private final Map<Integer, MarketplaceListing> pageListings;
    
    // Store current page by player UUID
    private final Map<UUID, Integer> playerPages;
    
    private int maxPage = 0;
    private static final int ITEMS_PER_PAGE = 45; // Adjust as needed
    private static final int NEXT_PAGE_SLOT = 53; // Bottom right corner
    private static final int PREV_PAGE_SLOT = 45; // Bottom left corner
    private static final int SORT_SLOT = 49; // Middle bottom
    
    // Menu configuration items
    private ItemStack noListingsItem;
    private ItemStack errorItem;
    private ItemStack prevPageButton;
    private ItemStack nextPageButton;
    private ItemStack sortingButton;
    
    private ListingSortType currentSort = ListingSortType.NEWEST;
    
    public ListingsMenu(ItemMarketplacePlugin plugin, String name) {
        super(plugin, name);
        
        pageListings = new ConcurrentHashMap<>();
        playerPages = new ConcurrentHashMap<>();
        decimalFormat = new DecimalFormat("#,##0.00");
        decimalFormat.setMaximumFractionDigits(2);
    }
    
    @Override
    public void reload(FileConfiguration config) {
        try {
            super.reload(config);
            
            // Clear current page listings
            pageListings.clear();
            
            // Load menu items from configuration
            ConfigurationSection menuItems = config.getConfigurationSection("menu-items");
            if (menuItems != null) {
                loadMenuItems(menuItems);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error reloading listings menu: " + e.getMessage());
        }
    }
    
    private void loadMenuItems(ConfigurationSection menuItems) {
        // Load item configurations with fallbacks handled by ItemStackConfiguration
        noListingsItem = ItemStackConfiguration.getItemStack(menuItems.getConfigurationSection("no-listings"));
        errorItem = ItemStackConfiguration.getItemStack(menuItems.getConfigurationSection("error"));
        prevPageButton = ItemStackConfiguration.getItemStack(menuItems.getConfigurationSection("prev-page"));
        nextPageButton = ItemStackConfiguration.getItemStack(menuItems.getConfigurationSection("next-page"));
        sortingButton = ItemStackConfiguration.getItemStack(menuItems.getConfigurationSection("sort"));
        
        // Update the sort button's lore to show current sort type
        if (sortingButton != null) {
            ItemMeta meta = sortingButton.getItemMeta();
            if (meta != null && meta.hasLore()) {
                List<String> lore = meta.getLore();
                for (int i = 0; i < lore.size(); i++) {
                    lore.set(i, lore.get(i).replace("%sort_type%", 
                        plugin.getMessagesManager().get(currentSort.getMessageKey().name().toLowerCase())));
                }
                meta.setLore(lore);
                sortingButton.setItemMeta(meta);
            }
        }
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
    protected Map<String, String> getPlaceholders(Player player, OfflinePlayer target, int slot) {
        Map<String, String> placeholders = new HashMap<>();
        
        // Add page information
        int currentPage = getCurrentPage(player);
        placeholders.put("current_page", String.valueOf(currentPage + 1));
        placeholders.put("max_page", String.valueOf(maxPage + 1));
        placeholders.put("sort_type", plugin.getMessagesManager().get(currentSort.getMessageKey().name().toLowerCase()));
        
        // Get the listing for the slot if it exists
        MarketplaceListing listing = pageListings.get(slot);
        if (listing == null) return placeholders;
        
        try {
            // Add listing specific placeholders
            placeholders.put("price", decimalFormat.format(listing.getPrice()));
            
            OfflinePlayer seller = plugin.getServer().getOfflinePlayer(listing.getSellerUuid());
            String sellerName = seller.getName() != null ? seller.getName() : "Unknown";
            placeholders.put("seller", sellerName);
            
            placeholders.put("amount", numberFormat.format(listing.getItemStack().getAmount()));
            placeholders.put("time_listed", formatTimeListed(listing.getCreateDateUtc()));
            placeholders.put("expiry_time", listing.getExpiryDateUtc() != null ? 
                formatExpiryTime(listing.getExpiryDateUtc()) : 
                plugin.getMessagesManager().get(Message.LISTING_NEVER_EXPIRES.name().toLowerCase()));
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting placeholders for listing: " + e.getMessage());
        }
        
        return placeholders;
    }
    
    @Override
    protected Map<Integer, MenuItem> getAdditionalItems(Player player, OfflinePlayer target) {
        Map<Integer, MenuItem> items = new HashMap<>();
        int currentPage = getCurrentPage(player);
        
        try {
            // Get all listings from manager
            List<MarketplaceListing> listings = getSortedListings(plugin.getListingManager().getAllListings());
            
            // Handle empty listings
            if (listings.isEmpty() && noListingsItem != null) {
                items.put(22, new MenuItem(noListingsItem.clone(), null));
                return items;
            }
            
            // Calculate max pages
            maxPage = (int) Math.ceil(listings.size() / (double) ITEMS_PER_PAGE) - 1;
            maxPage = Math.max(0, maxPage); // Ensure at least one page
            
            // Ensure current page is valid
            if (currentPage > maxPage) {
                currentPage = maxPage;
                setCurrentPage(player, currentPage);
            }
            
            // Calculate start and end indices for current page
            int startIndex = currentPage * ITEMS_PER_PAGE;
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, listings.size());
            
            // Clear current page listings
            pageListings.clear();
            
            // Add listings for current page
            for (int i = startIndex; i < endIndex; i++) {
                MarketplaceListing listing = listings.get(i);
                if (listing == null || !listing.isActive()) continue;
                
                int slot = (i - startIndex);
                
                try {
                    // Create item display for the listing
                    ItemStack displayItem = listing.getItemStack().clone();
                    if (displayItem == null || displayItem.getType() == Material.AIR) {
                        plugin.getLogger().warning("Invalid item in listing ID: " + listing.getListingId());
                        continue;
                    }
                    
                    // Add lore with listing details
                    List<String> lore = new ArrayList<>();
                    if (displayItem.hasItemMeta() && displayItem.getItemMeta().hasLore()) {
                        lore.addAll(displayItem.getItemMeta().getLore());
                    }
                    
                    // Add listing information to lore
                    OfflinePlayer seller = plugin.getServer().getOfflinePlayer(listing.getSellerUuid());
                    String sellerName = seller.getName() != null ? seller.getName() : "Unknown";
                    
                    // Prepare placeholders for replacement
                    Map<String, String> listingPlaceholders = new HashMap<>();
                    listingPlaceholders.put("seller", sellerName);
                    listingPlaceholders.put("price", decimalFormat.format(listing.getPrice()));
                    listingPlaceholders.put("time_listed", formatTimeListed(listing.getCreateDateUtc()));
                    
                    // Add listing information lines
                    lore.add(replacePlaceholders("§7Seller: §f" + sellerName, listingPlaceholders));
                    lore.add(replacePlaceholders("§7Price: §f" + decimalFormat.format(listing.getPrice()), listingPlaceholders));
                    lore.add(replacePlaceholders("§7Listed: §f" + formatTimeListed(listing.getCreateDateUtc()), listingPlaceholders));
                    
                    // Add expiry info if exists
                    if (listing.getExpiryDateUtc() != null) {
                        listingPlaceholders.put("expiry_time", formatExpiryTime(listing.getExpiryDateUtc()));
                        lore.add(replacePlaceholders("§7Expires: §f" + formatExpiryTime(listing.getExpiryDateUtc()), listingPlaceholders));
                    }
                    
                    lore.add("§eClick to purchase");
                    
                    ItemMeta meta = displayItem.getItemMeta();
                    meta.setLore(lore);
                    displayItem.setItemMeta(meta);
                    
                    // Add to menu and track in pageListings
                    items.put(slot, new MenuItem(displayItem, null));
                    pageListings.put(slot, listing);
                } catch (Exception e) {
                    plugin.getLogger().warning("Error processing listing ID " + listing.getListingId() + ": " + e.getMessage());
                }
            }
            
            // Add navigation buttons if needed
            if (maxPage > 0) {
                // Previous page button
                if (currentPage > 0 && prevPageButton != null) {
                    ItemStack prevButton = prevPageButton.clone();
                    ItemMeta meta = prevButton.getItemMeta();
                    
                    if (meta != null && meta.hasLore()) {
                        List<String> lore = new ArrayList<>(meta.getLore());
                        for (int i = 0; i < lore.size(); i++) {
                            lore.set(i, lore.get(i).replace("%current_page%", String.valueOf(currentPage + 1))
                                                  .replace("%max_page%", String.valueOf(maxPage + 1)));
                        }
                        meta.setLore(lore);
                        prevButton.setItemMeta(meta);
                    }
                    
                    items.put(PREV_PAGE_SLOT, new MenuItem(prevButton, null));
                }
                
                // Next page button
                if (currentPage < maxPage && nextPageButton != null) {
                    ItemStack nextButton = nextPageButton.clone();
                    ItemMeta meta = nextButton.getItemMeta();
                    
                    if (meta != null && meta.hasLore()) {
                        List<String> lore = new ArrayList<>(meta.getLore());
                        for (int i = 0; i < lore.size(); i++) {
                            lore.set(i, lore.get(i).replace("%current_page%", String.valueOf(currentPage + 1))
                                                  .replace("%max_page%", String.valueOf(maxPage + 1)));
                        }
                        meta.setLore(lore);
                        nextButton.setItemMeta(meta);
                    }
                    
                    items.put(NEXT_PAGE_SLOT, new MenuItem(nextButton, null));
                }
            }
            
            // Add sort button
            if (sortingButton != null) {
                ItemStack sortButton = sortingButton.clone();
                ItemMeta sortMeta = sortButton.getItemMeta();
                
                if (sortMeta != null && sortMeta.hasLore()) {
                    List<String> lore = new ArrayList<>(sortMeta.getLore());
                    for (int i = 0; i < lore.size(); i++) {
                        lore.set(i, lore.get(i).replace("%sort_type%", plugin.getMessagesManager().get(currentSort.getMessageKey().name().toLowerCase())));
                    }
                    sortMeta.setLore(lore);
                    sortButton.setItemMeta(sortMeta);
                }
                
                items.put(SORT_SLOT, new MenuItem(sortButton, null));
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("Error generating listings menu: " + e.getMessage());
            e.printStackTrace();
            
            // Show error item if available
            if (errorItem != null) {
                items.put(22, new MenuItem(errorItem.clone(), null));
            }
        }
        
        return items;
    }
    
    private String replacePlaceholders(String text, Map<String, String> placeholders) {
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return ChatColor.translateAlternateColorCodes('&', result);
    }
    
    private List<MarketplaceListing> getSortedListings(List<MarketplaceListing> listings) {
        if (listings == null || listings.isEmpty()) return new ArrayList<>();
        
        switch (currentSort) {
            case NEWEST:
                return listings.stream()
                    .sorted(Comparator.comparing(MarketplaceListing::getCreateDateUtc).reversed())
                    .collect(Collectors.toList());
            case OLDEST:
                return listings.stream()
                    .sorted(Comparator.comparing(MarketplaceListing::getCreateDateUtc))
                    .collect(Collectors.toList());
            case PRICE_LOW:
                return listings.stream()
                    .sorted(Comparator.comparing(MarketplaceListing::getPrice))
                    .collect(Collectors.toList());
            case PRICE_HIGH:
                return listings.stream()
                    .sorted(Comparator.comparing(MarketplaceListing::getPrice).reversed())
                    .collect(Collectors.toList());
            default:
                return listings;
        }
    }
    
    private String formatTimeListed(Timestamp timestamp) {
        if (timestamp == null) return "Unknown";
        
        try {
            LocalDateTime listed = timestamp.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            LocalDateTime now = Instant.now().atZone(ZoneId.systemDefault()).toLocalDateTime();
            
            long seconds = ChronoUnit.SECONDS.between(listed, now);
            if (seconds < 60) {
                return seconds + " seconds ago";
            }
            
            long minutes = ChronoUnit.MINUTES.between(listed, now);
            if (minutes < 60) {
                return minutes + " minutes ago";
            }
            
            long hours = ChronoUnit.HOURS.between(listed, now);
            if (hours < 24) {
                return hours + " hours ago";
            }
            
            long days = ChronoUnit.DAYS.between(listed, now);
            return days + " days ago";
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    private String formatExpiryTime(Timestamp timestamp) {
        if (timestamp == null) return plugin.getMessagesManager().get(Message.LISTING_NEVER_EXPIRES.name().toLowerCase());
        
        try {
            LocalDateTime expiry = timestamp.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            LocalDateTime now = Instant.now().atZone(ZoneId.systemDefault()).toLocalDateTime();
            
            if (expiry.isBefore(now)) {
                return plugin.getMessagesManager().get(Message.LISTING_EXPIRED.name().toLowerCase());
            }
            
            long hours = ChronoUnit.HOURS.between(now, expiry);
            if (hours < 24) {
                return hours + " hours";
            }
            
            long days = ChronoUnit.DAYS.between(now, expiry);
            return days + " days";
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    @Override
    public void openInventory(Player player, OfflinePlayer target) {
        // Reset to first page when opening
        setCurrentPage(player, 0);
        super.openInventory(player, target);
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
            // Handle menu navigation buttons
            if (handleNavigationButtons(player, slot)) {
                return;
            }
            
            // Handle listing purchase if clicked on a listing item
            if (handleListingPurchase(player, slot)) {
                return;
            }
            
            // If none of the above, delegate to parent menu handler
            super.handleClick(event);
            
        } catch (Exception e) {
            plugin.getLogger().severe("Error handling menu click: " + e.getMessage());
            player.sendMessage(plugin.getMessagesManager().get(Message.ERROR_GENERIC.name().toLowerCase()));
            player.playSound(player.getLocation(), "entity.villager.no", 1.0f, 1.0f);
        }
    }
    
    /**
     * Handles clicks on navigation buttons (sort, previous page, next page)
     * @return true if a navigation button was clicked and handled
     */
    private boolean handleNavigationButtons(Player player, int slot) {
        int currentPage = getCurrentPage(player);
        
        // Handle sorting button
        if (slot == SORT_SLOT && sortingButton != null) {
            currentSort = currentSort.next();
            player.playSound(player.getLocation(), "block.note_block.hat", 0.5f, 1.0f);
            refresh(player);
            return true;
        }
        
        // Handle pagination buttons
        if (slot == NEXT_PAGE_SLOT && currentPage < maxPage && nextPageButton != null) {
            setCurrentPage(player, currentPage + 1);
            refresh(player);
            return true;
        }
        
        if (slot == PREV_PAGE_SLOT && currentPage > 0 && prevPageButton != null) {
            setCurrentPage(player, currentPage - 1);
            refresh(player);
            return true;
        }
        
        return false;
    }
    
    /**
     * Handles purchase attempt when clicking on a listing
     * @return true if a listing was clicked and purchase was attempted
     */
    private boolean handleListingPurchase(Player player, int slot) {
        MarketplaceListing listing = pageListings.get(slot);
        if (listing == null) {
            return false;
        }
        
        // Check if listing is still active
        if (!listing.isActive()) {
            player.sendMessage(plugin.getMessagesManager().get(Message.LISTING_INACTIVE.name().toLowerCase()));
            player.playSound(player.getLocation(), "entity.villager.no", 1.0f, 1.0f);
            refresh(player);
            return true;
        }
        
        // Process purchase
        try {
            Optional<MarketplaceTransaction> transaction = plugin.getListingManager().purchaseItem(player, listing.getListingId());
            
            if (transaction.isPresent()) {
                sendPurchaseSuccessMessage(player, listing);
            } else {
                player.sendMessage(plugin.getMessagesManager().get(Message.LISTING_PURCHASE_FAILED.name().toLowerCase()));
                player.playSound(player.getLocation(), "entity.villager.no", 1.0f, 1.0f);
            }
        } catch (SQLException e) {
            player.sendMessage(plugin.getMessagesManager().get(Message.ERROR_PROCESSING_PURCHASE.name().toLowerCase()));
            plugin.getLogger().severe("Error processing purchase: " + e.getMessage());
            player.playSound(player.getLocation(), "entity.villager.no", 1.0f, 1.0f);
        }
        
        // Always refresh after a purchase attempt
        refresh(player);
        return true;
    }
    
    /**
     * Sends success message and plays sound for successful purchase
     */
    private void sendPurchaseSuccessMessage(Player player, MarketplaceListing listing) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.valueOf(listing.getItemStack().getAmount()));
        placeholders.put("price", decimalFormat.format(listing.getPrice()));
        
        player.sendMessage(replacePlaceholders(
            plugin.getMessagesManager().get(Message.LISTING_PURCHASE_SUCCESS.name().toLowerCase()),
            placeholders));
        player.playSound(player.getLocation(), "entity.player.levelup", 1.0f, 1.0f);
    }
} 