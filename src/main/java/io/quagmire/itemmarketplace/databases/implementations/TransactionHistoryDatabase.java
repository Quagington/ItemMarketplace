package io.quagmire.itemmarketplace.databases.implementations;

import io.quagmire.core.CorePlugin;
import io.quagmire.core.databases.Database;
import io.quagmire.core.databases.DatabaseConnectionPool;
import io.quagmire.core.utilities.item.ItemStackSerializer;
import io.quagmire.itemmarketplace.model.MarketplaceTransaction;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TransactionHistoryDatabase extends Database {
  private final DatabaseConnectionPool pool;
  
  public TransactionHistoryDatabase(CorePlugin plugin, DatabaseConnectionPool pool) {
    super(plugin, pool);
    this.pool = pool;
  }
  
  /**
   * Record a new transaction
   */
  public MarketplaceTransaction recordTransaction(long listingId, UUID sellerUuid, UUID buyerUuid, 
                                                ItemStack item, BigDecimal price) throws SQLException {
    try (Connection connection = pool.getConnection();
         PreparedStatement stmt = connection.prepareStatement(
            "INSERT INTO marketplace_transactions (listing_id, seller_uuid, buyer_uuid, " +
            "item_data, price, transaction_date_utc) " +
            "VALUES (?, ?, ?, ?, ?, UTC_TIMESTAMP)",
            Statement.RETURN_GENERATED_KEYS)) {
        
        stmt.setLong(1, listingId);
        stmt.setString(2, sellerUuid.toString());
        stmt.setString(3, buyerUuid.toString());
        
        try {
            byte[] serializedItem = ItemStackSerializer.serializeItemStack(item);
            stmt.setBytes(4, serializedItem);
        } catch (IOException e) {
            throw new SQLException("Failed to serialize ItemStack", e);
        }
        
        stmt.setBigDecimal(5, price);
        
        int affectedRows = stmt.executeUpdate();
        if (affectedRows == 0) {
            throw new SQLException("Recording transaction failed, no rows affected.");
        }
        
        try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                long transactionId = generatedKeys.getLong(1);
                
                // Retrieve the full transaction to return
                return getTransactionById(transactionId);
            } else {
                throw new SQLException("Recording transaction failed, no ID obtained.");
            }
        }
    }
  }
  
  /**
   * Get a transaction by its ID
   */
  public MarketplaceTransaction getTransactionById(long transactionId) throws SQLException {
    try (Connection connection = pool.getConnection();
         PreparedStatement stmt = connection.prepareStatement(
            "SELECT * FROM marketplace_transactions WHERE transaction_id = ?")) {
        
        stmt.setLong(1, transactionId);
        
        try (ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return MarketplaceTransaction.deserialize(rs);
            } else {
                return null;
            }
        }
    }
  }
  
  /**
   * Get all transactions by a seller
   */
  public List<MarketplaceTransaction> getTransactionsBySeller(UUID sellerUuid) throws SQLException {
    List<MarketplaceTransaction> transactions = new ArrayList<>();
    
    try (Connection connection = pool.getConnection();
         PreparedStatement stmt = connection.prepareStatement(
            "SELECT * FROM marketplace_transactions WHERE seller_uuid = ? " +
            "ORDER BY transaction_date_utc DESC")) {
        
        stmt.setString(1, sellerUuid.toString());
        
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                transactions.add(MarketplaceTransaction.deserialize(rs));
            }
        }
    }
    
    return transactions;
  }
  
  /**
   * Get all transactions by a buyer
   */
  public List<MarketplaceTransaction> getTransactionsByBuyer(UUID buyerUuid) throws SQLException {
    List<MarketplaceTransaction> transactions = new ArrayList<>();
    
    try (Connection connection = pool.getConnection();
         PreparedStatement stmt = connection.prepareStatement(
            "SELECT * FROM marketplace_transactions WHERE buyer_uuid = ? " +
            "ORDER BY transaction_date_utc DESC")) {
        
        stmt.setString(1, buyerUuid.toString());
        
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                transactions.add(MarketplaceTransaction.deserialize(rs));
            }
        }
    }
    
    return transactions;
  }
  
  /**
   * Get most recent transactions, with limit
   */
  public List<MarketplaceTransaction> getRecentTransactions(int limit) throws SQLException {
    List<MarketplaceTransaction> transactions = new ArrayList<>();
    
    try (Connection connection = pool.getConnection();
         PreparedStatement stmt = connection.prepareStatement(
            "SELECT * FROM marketplace_transactions " +
            "ORDER BY transaction_date_utc DESC LIMIT ?")) {
        
        stmt.setInt(1, limit);
        
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                transactions.add(MarketplaceTransaction.deserialize(rs));
            }
        }
    }
    
    return transactions;
  }
}
