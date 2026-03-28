package io.quagmire.itemmarketplace.manager;

import io.quagmire.itemmarketplace.ItemMarketplacePlugin;
import io.quagmire.itemmarketplace.databases.implementations.ListingsDatabase;
import io.quagmire.itemmarketplace.databases.implementations.TransactionHistoryDatabase;
import io.quagmire.itemmarketplace.model.MarketplaceListing;
import io.quagmire.itemmarketplace.model.MarketplaceTransaction;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ListingManager {
    private final ConcurrentHashMap<Long, MarketplaceListing> activeListings = new ConcurrentHashMap<>();
    private final DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");
    
    @Getter private final ItemMarketplacePlugin plugin;
    
    // Discord webhook URL from config
    private String discordWebhookUrl = null;
    
    public ListingManager(ItemMarketplacePlugin plugin) {
        this.plugin = plugin;
        this.discordWebhookUrl = plugin.getConfig().getString("discord-webhook-url", null);
    }
    
    /**
     * Initialize the manager by loading all active listings from the database
     */
    public void initialize() throws SQLException {
        List<MarketplaceListing> listings = plugin.getDatabaseCollection().getListingsDatabase().getAllActiveListings();
        for (MarketplaceListing listing : listings) {
            activeListings.put(listing.getListingId(), listing);
        }
    }
    
    /**
     * Create a new listing in both memory and database
     * 
     * @param seller The player creating the listing
     * @param item The item to sell
     * @param price The price for the item
     * @param expiryHours Number of hours until expiry (null for no expiry)
     * @return The created listing
     */
    public MarketplaceListing createListing(Player seller, ItemStack item, BigDecimal price, Integer expiryHours) throws SQLException {
        Timestamp expiryDate = null;
        if (expiryHours != null) {
            Instant expiry = Instant.now().plusSeconds(expiryHours * 3600L);
            expiryDate = Timestamp.from(expiry);
        }
        
        MarketplaceListing listing = plugin.getDatabaseCollection().getListingsDatabase().createListing(seller.getUniqueId(), item, price, expiryDate);
        activeListings.put(listing.getListingId(), listing);
        return listing;
    }
    
    /**
     * Purchase an item from a listing
     * 
     * @param buyer The player making the purchase
     * @param listingId The ID of the listing to purchase
     * @return The transaction record if successful
     */
    public Optional<MarketplaceTransaction> purchaseItem(Player buyer, long listingId) throws SQLException {
        MarketplaceListing listing = activeListings.get(listingId);
        if (listing == null || !listing.isActive()) {
            return Optional.empty();
        }
        
        // Process purchase
        MarketplaceTransaction transaction = plugin.getDatabaseCollection().getTransactionHistoryDatabase().recordTransaction(
            listing.getListingId(),
            listing.getSellerUuid(),
            buyer.getUniqueId(),
            listing.getItemStack(),
            listing.getPrice()
        );
        
        // Update or remove the listing depending on item amount
        activeListings.remove(listingId);
        plugin.getDatabaseCollection().getListingsDatabase().deactivateListing(listingId);
        
        // Send webhook notification
        sendDiscordWebhook(buyer, listing, transaction);
        
        return Optional.of(transaction);
    }
    
    /**
     * Purchase a black market item with discount for buyer and bonus for seller
     * 
     * @param buyer The player making the purchase
     * @param listingId The ID of the listing to purchase
     * @param sellerBonusMultiplier The multiplier for seller's payment (typically 2x)
     * @return The transaction record if successful
     */
    public Optional<MarketplaceTransaction> purchaseBlackMarketItem(Player buyer, long listingId, double sellerBonusMultiplier) throws SQLException {
        MarketplaceListing listing = activeListings.get(listingId);
        if (listing == null || !listing.isActive()) {
            return Optional.empty();
        }
        
        // Calculate bonus payment for seller
        BigDecimal originalPrice = listing.getPrice();
        BigDecimal sellerBonus = originalPrice.multiply(BigDecimal.valueOf(sellerBonusMultiplier));
        
        // Process purchase with the bonus for seller
        MarketplaceTransaction transaction = plugin.getDatabaseCollection().getTransactionHistoryDatabase().recordBlackMarketTransaction(
            listing.getListingId(),
            listing.getSellerUuid(),
            buyer.getUniqueId(),
            listing.getItemStack(),
            originalPrice,
            sellerBonus,
            true
        );
        
        // Update or remove the listing
        activeListings.remove(listingId);
        plugin.getDatabaseCollection().getListingsDatabase().deactivateListing(listingId);
        
        // Notify seller about the bonus if they're online
        Player seller = Bukkit.getPlayer(listing.getSellerUuid());
        if (seller != null && seller.isOnline()) {
            seller.sendMessage(plugin.getMessagesManager().get("blackmarket_bonus_seller"));
        }
        
        // Send webhook notification
        sendDiscordWebhook(buyer, listing, transaction);
        
        return Optional.of(transaction);
    }
    
    /**
     * Send purchase information to Discord webhook
     */
    public void sendDiscordWebhook(Player buyer, MarketplaceListing listing, MarketplaceTransaction transaction) {
        if (discordWebhookUrl == null || discordWebhookUrl.isEmpty()) {
            return;
        }
        
        // Run async to avoid blocking the main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Get seller name
                OfflinePlayer seller = Bukkit.getOfflinePlayer(listing.getSellerUuid());
                String sellerName = seller.getName() != null ? seller.getName() : "Unknown";
                
                // Get item name
                String itemName = "Unknown Item";
                if (listing.getItemStack().hasItemMeta() && listing.getItemStack().getItemMeta().hasDisplayName()) {
                    itemName = listing.getItemStack().getItemMeta().getDisplayName();
                } else {
                    itemName = listing.getItemStack().getType().toString();
                }
                
                // Format price
                String price = decimalFormat.format(listing.getPrice());
                
                // Create JSON payload
                String jsonPayload = String.format(
                    "{\"embeds\":[{\"title\":\"Item Purchased\",\"color\":65280,\"fields\":[" +
                    "{\"name\":\"Item\",\"value\":\"%s\",\"inline\":true}," +
                    "{\"name\":\"Amount\",\"value\":\"%s\",\"inline\":true}," +
                    "{\"name\":\"Price\",\"value\":\"%s\",\"inline\":true}," +
                    "{\"name\":\"Buyer\",\"value\":\"%s\",\"inline\":true}," +
                    "{\"name\":\"Seller\",\"value\":\"%s\",\"inline\":true}," +
                    "{\"name\":\"Black Market\",\"value\":\"%s\",\"inline\":true}" +
                    "]}]}",
                    itemName,
                    listing.getItemStack().getAmount(),
                    price,
                    buyer.getName(),
                    sellerName,
                    listing.isBlackMarketItem() ? "Yes" : "No"
                );
                
                // Send HTTP request
                URL url = new URL(discordWebhookUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("User-Agent", "ItemMarketplace/1.0");
                connection.setDoOutput(true);
                
                // Write payload
                try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
                    outputStream.writeBytes(jsonPayload);
                    outputStream.flush();
                }
                
                // Get response
                int responseCode = connection.getResponseCode();
                if (responseCode != 204) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                    String inputLine;
                    StringBuilder response = new StringBuilder();
                    
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();
                    
                    plugin.getLogger().warning("Discord webhook error: " + responseCode + " " + response);
                }
                
                connection.disconnect();
                
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send Discord webhook: " + e.getMessage());
            }
        });
    }
    
    /**
     * Cancel a listing by its ID
     * 
     * @param listingId The ID of the listing to cancel
     * @param playerUuid The UUID of the player attempting to cancel
     * @return True if canceled successfully
     */
    public boolean cancelListing(long listingId, UUID playerUuid) throws SQLException {
        MarketplaceListing listing = activeListings.get(listingId);
        if (listing == null || !listing.isActive()) {
            return false;
        }
        
        // Only the seller can cancel their listing
        if (!listing.getSellerUuid().equals(playerUuid)) {
            return false;
        }
        
        activeListings.remove(listingId);
        plugin.getDatabaseCollection().getListingsDatabase().deactivateListing(listingId);
        return true;
    }
    
    /**
     * Remove all cached listings for a given seller UUID
     */
    public void removeListingsBySeller(UUID sellerUuid) {
        activeListings.values().removeIf(listing -> listing.getSellerUuid().equals(sellerUuid));
    }

    /**
     * Get all active listings
     */
    public List<MarketplaceListing> getAllListings() {
        return new ArrayList<>(activeListings.values());
    }
    
    /**
     * Get listings by seller
     */
    public List<MarketplaceListing> getListingsBySeller(UUID sellerUuid) {
        return activeListings.values().stream()
            .filter(listing -> listing.getSellerUuid().equals(sellerUuid))
            .collect(Collectors.toList());
    }
    
    /**
     * Clean expired listings
     */
    public void cleanExpiredListings() throws SQLException {
        Timestamp now = Timestamp.from(Instant.now());
        List<Long> expiredIds = new ArrayList<>();
        
        for (MarketplaceListing listing : activeListings.values()) {
            if (listing.getExpiryDateUtc() != null && listing.getExpiryDateUtc().before(now)) {
                expiredIds.add(listing.getListingId());
            }
        }
        
        for (Long id : expiredIds) {
            activeListings.remove(id);
            plugin.getDatabaseCollection().getListingsDatabase().deactivateListing(id);
        }
    }
    
    /**
     * Find listings that match a search term
     */
    public List<MarketplaceListing> searchListings(String searchTerm) {
        String lowercaseSearch = searchTerm.toLowerCase();
        return activeListings.values().stream()
            .filter(listing -> {
                String itemName = listing.getItemStack().getItemMeta() != null && 
                                listing.getItemStack().getItemMeta().hasDisplayName() ? 
                                listing.getItemStack().getItemMeta().getDisplayName().toLowerCase() : 
                                listing.getItemStack().getType().name().toLowerCase();
                return itemName.contains(lowercaseSearch);
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Get player's transaction history
     */
    public List<MarketplaceTransaction> getPlayerTransactionHistory(UUID playerUuid) throws SQLException {
        return plugin.getDatabaseCollection().getTransactionHistoryDatabase().getTransactionsByPlayer(playerUuid);
    }
} 