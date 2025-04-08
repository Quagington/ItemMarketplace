package io.quagmire.itemmarketplace.manager;

import io.quagmire.itemmarketplace.ItemMarketplacePlugin;
import io.quagmire.itemmarketplace.databases.implementations.ListingsDatabase;
import io.quagmire.itemmarketplace.databases.implementations.TransactionHistoryDatabase;
import io.quagmire.itemmarketplace.model.MarketplaceListing;
import io.quagmire.itemmarketplace.model.MarketplaceTransaction;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ListingManager {
    private final ConcurrentHashMap<Long, MarketplaceListing> activeListings = new ConcurrentHashMap<>();
    
    @Getter private final ItemMarketplacePlugin plugin;
    
    public ListingManager(ItemMarketplacePlugin plugin) {
        this.plugin = plugin;
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
        
        return Optional.of(transaction);
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
} 