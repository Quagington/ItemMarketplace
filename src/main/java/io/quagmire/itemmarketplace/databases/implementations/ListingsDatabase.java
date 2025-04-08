package io.quagmire.itemmarketplace.databases.implementations;

import io.quagmire.core.CorePlugin;
import io.quagmire.core.databases.Database;
import io.quagmire.core.databases.DatabaseConnectionPool;
import io.quagmire.core.utilities.item.ItemStackSerializer;
import io.quagmire.itemmarketplace.model.MarketplaceListing;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ListingsDatabase extends Database {
  private final DatabaseConnectionPool pool;
  
  public ListingsDatabase(CorePlugin plugin, DatabaseConnectionPool pool) {
    super(plugin, pool);
    this.pool = pool;
  }
  
  /**
   * Get all active listings from the database
   */
  public List<MarketplaceListing> getAllActiveListings() throws SQLException {
    List<MarketplaceListing> listings = new ArrayList<>();
    
    try (Connection connection = pool.getConnection();
         PreparedStatement stmt = connection.prepareStatement(
            "SELECT * FROM marketplace_listings WHERE is_active = TRUE")) {
        
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                listings.add(MarketplaceListing.deserialize(rs));
            }
        }
    }
    
    return listings;
  }
  
  /**
   * Create a new listing in the database
   */
  public MarketplaceListing createListing(UUID sellerUuid, ItemStack item, BigDecimal price, Timestamp expiryDate) throws SQLException {
    try (Connection connection = pool.getConnection();
         PreparedStatement stmt = connection.prepareStatement(
            "INSERT INTO marketplace_listings (seller_uuid, item_data, price, is_active, " +
            "create_date_utc, last_update_date_utc, expiry_date_utc) " +
            "VALUES (?, ?, ?, TRUE, UTC_TIMESTAMP, UTC_TIMESTAMP, ?)",
            Statement.RETURN_GENERATED_KEYS)) {
        
        stmt.setString(1, sellerUuid.toString());
        
        try {
            byte[] serializedItem = ItemStackSerializer.serializeItemStack(item);
            stmt.setBytes(2, serializedItem);
        } catch (IOException e) {
            throw new SQLException("Failed to serialize ItemStack", e);
        }
        
        stmt.setBigDecimal(3, price);
        
        if (expiryDate != null) {
            stmt.setTimestamp(4, expiryDate);
        } else {
            stmt.setNull(4, Types.TIMESTAMP);
        }
        
        int affectedRows = stmt.executeUpdate();
        if (affectedRows == 0) {
            throw new SQLException("Creating listing failed, no rows affected.");
        }
        
        try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                long listingId = generatedKeys.getLong(1);
                
                // Retrieve the full listing to return
                return getListingById(listingId);
            } else {
                throw new SQLException("Creating listing failed, no ID obtained.");
            }
        }
    }
  }
  
  /**
   * Deactivate a listing by its ID
   */
  public void deactivateListing(long listingId) throws SQLException {
    try (Connection connection = pool.getConnection();
         PreparedStatement stmt = connection.prepareStatement(
            "UPDATE marketplace_listings SET is_active = FALSE, last_update_date_utc = UTC_TIMESTAMP " +
            "WHERE listing_id = ?")) {
        
        stmt.setLong(1, listingId);
        stmt.executeUpdate();
    }
  }
  
  /**
   * Get a listing by its ID
   */
  public MarketplaceListing getListingById(long listingId) throws SQLException {
    try (Connection connection = pool.getConnection();
         PreparedStatement stmt = connection.prepareStatement(
            "SELECT * FROM marketplace_listings WHERE listing_id = ?")) {
        
        stmt.setLong(1, listingId);
        
        try (ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return MarketplaceListing.deserialize(rs);
            } else {
                return null;
            }
        }
    }
  }
  
  /**
   * Get all listings by a seller
   */
  public List<MarketplaceListing> getListingsBySeller(UUID sellerUuid) throws SQLException {
    List<MarketplaceListing> listings = new ArrayList<>();
    
    try (Connection connection = pool.getConnection();
         PreparedStatement stmt = connection.prepareStatement(
            "SELECT * FROM marketplace_listings WHERE seller_uuid = ?")) {
        
        stmt.setString(1, sellerUuid.toString());
        
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                listings.add(MarketplaceListing.deserialize(rs));
            }
        }
    }
    
    return listings;
  }
}
