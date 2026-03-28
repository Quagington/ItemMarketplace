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
import java.time.Instant;
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
   * Record a marketplace transaction
   *
   * @param listingId   The listing ID
   * @param sellerUuid  The seller's UUID
   * @param buyerUuid   The buyer's UUID
   * @param itemStack   The item purchased
   * @param price       The price paid
   * @return The created transaction record
   */
  public MarketplaceTransaction recordTransaction(
    long listingId,
    UUID sellerUuid,
    UUID buyerUuid,
    ItemStack itemStack,
    BigDecimal price
  ) throws SQLException {
    return recordTransaction(listingId, sellerUuid, buyerUuid, itemStack, price, price, false);
  }
  
  /**
   * Record a black market transaction with a bonus for the seller
   *
   * @param listingId   The listing ID
   * @param sellerUuid  The seller's UUID
   * @param buyerUuid   The buyer's UUID
   * @param itemStack   The item purchased
   * @param buyerPrice  The price paid by the buyer (discounted)
   * @param sellerPayout The amount paid to the seller (with bonus)
   * @param isBlackMarket Whether this is a black market transaction
   * @return The created transaction record
   */
  public MarketplaceTransaction recordBlackMarketTransaction(
    long listingId,
    UUID sellerUuid,
    UUID buyerUuid,
    ItemStack itemStack,
    BigDecimal buyerPrice,
    BigDecimal sellerPayout,
    boolean isBlackMarket
  ) throws SQLException {
    return recordTransaction(listingId, sellerUuid, buyerUuid, itemStack, buyerPrice, sellerPayout, isBlackMarket);
  }

  /**
   * Record a transaction with detailed parameters
   *
   * @param listingId    The listing ID
   * @param sellerUuid   The seller's UUID
   * @param buyerUuid    The buyer's UUID
   * @param itemStack    The item purchased
   * @param buyerPrice   The price paid by the buyer
   * @param sellerPayout The amount paid to the seller
   * @param isBlackMarket Whether this is a black market transaction
   * @return The created transaction record
   */
  private MarketplaceTransaction recordTransaction(
    long listingId,
    UUID sellerUuid,
    UUID buyerUuid,
    ItemStack itemStack,
    BigDecimal buyerPrice,
    BigDecimal sellerPayout,
    boolean isBlackMarket
  ) throws SQLException {
    String sql = "INSERT INTO transactions (listing_id, seller_uuid, buyer_uuid, item_data, price_paid, seller_payout, is_black_market, transaction_date_utc) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    Timestamp now = Timestamp.from(Instant.now());

    try (Connection connection = pool.getConnection();
         PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      statement.setLong(1, listingId);
      statement.setString(2, sellerUuid.toString());
      statement.setString(3, buyerUuid.toString());

      // Serialize item data
      byte[] itemData;
      try {
        itemData = ItemStackSerializer.serializeItemStack(itemStack);
        statement.setBytes(4, itemData);
      } catch (IOException e) {
        throw new SQLException("Failed to serialize item data", e);
      }

      statement.setBigDecimal(5, buyerPrice);
      statement.setBigDecimal(6, sellerPayout);
      statement.setBoolean(7, isBlackMarket);
      statement.setTimestamp(8, now);

      statement.executeUpdate();

      try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
        if (generatedKeys.next()) {
          long transactionId = generatedKeys.getLong(1);
          return new MarketplaceTransaction(
            transactionId,
            listingId,
            sellerUuid,
            buyerUuid,
            itemStack,
            buyerPrice,
            sellerPayout,
            isBlackMarket,
            now
          );
        } else {
          throw new SQLException("Failed to get transaction ID after insert");
        }
      }
    }
  }
  
  /**
   * Get transactions by player UUID (either as buyer or seller)
   *
   * @param playerUuid The player's UUID
   * @return List of transactions involving the player
   */
  public List<MarketplaceTransaction> getTransactionsByPlayer(UUID playerUuid) throws SQLException {
    String sql = "SELECT * FROM transactions WHERE buyer_uuid = ? OR seller_uuid = ? ORDER BY transaction_date_utc DESC";
    
    try (Connection connection = pool.getConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, playerUuid.toString());
      statement.setString(2, playerUuid.toString());
      
      try (ResultSet resultSet = statement.executeQuery()) {
        List<MarketplaceTransaction> transactions = new ArrayList<>();
        while (resultSet.next()) {
          transactions.add(deserializeTransaction(resultSet));
        }
        return transactions;
      }
    }
  }
  
  /**
   * Get a transaction by ID
   *
   * @param transactionId The transaction ID
   * @return The transaction record, if found
   */
  public MarketplaceTransaction getTransaction(long transactionId) throws SQLException {
    String sql = "SELECT * FROM transactions WHERE transaction_id = ?";
    
    try (Connection connection = pool.getConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, transactionId);
      
      try (ResultSet resultSet = statement.executeQuery()) {
        if (resultSet.next()) {
          return deserializeTransaction(resultSet);
        } else {
          return null;
        }
      }
    }
  }
  
  // --- AccountManagement DAO methods ---

  public boolean hasPlayerTransactions(UUID playerUuid) throws SQLException {
    String sql = "SELECT 1 FROM transactions WHERE buyer_uuid = ? OR seller_uuid = ? LIMIT 1";
    try (Connection connection = pool.getConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, playerUuid.toString());
      statement.setString(2, playerUuid.toString());
      try (ResultSet rs = statement.executeQuery()) {
        return rs.next();
      }
    }
  }

  public int countPlayerTransactions(UUID playerUuid) throws SQLException {
    String sql = "SELECT COUNT(*) FROM transactions WHERE buyer_uuid = ? OR seller_uuid = ?";
    try (Connection connection = pool.getConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, playerUuid.toString());
      statement.setString(2, playerUuid.toString());
      try (ResultSet rs = statement.executeQuery()) {
        if (rs.next()) return rs.getInt(1);
        return 0;
      }
    }
  }

  public int deletePlayerTransactions(UUID playerUuid) throws SQLException {
    String sql = "DELETE FROM transactions WHERE buyer_uuid = ? OR seller_uuid = ?";
    try (Connection connection = pool.getConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, playerUuid.toString());
      statement.setString(2, playerUuid.toString());
      return statement.executeUpdate();
    }
  }

  /**
   * Deserialize a transaction from a result set
   */
  private MarketplaceTransaction deserializeTransaction(ResultSet rs) throws SQLException {
    try {
      byte[] itemData = rs.getBytes("item_data");
      ItemStack itemStack = null;
      try {
        itemStack = ItemStackSerializer.deserializeItemStack(itemData);
      } catch (IOException e) {
        throw new SQLException("Failed to deserialize ItemStack", e);
      }
      
      return new MarketplaceTransaction(
        rs.getLong("transaction_id"),
        rs.getLong("listing_id"),
        UUID.fromString(rs.getString("seller_uuid")),
        UUID.fromString(rs.getString("buyer_uuid")),
        itemStack,
        rs.getBigDecimal("price_paid"),
        rs.getBigDecimal("seller_payout"),
        rs.getBoolean("is_black_market"),
        rs.getTimestamp("transaction_date_utc")
      );
    } catch (SQLException e) {
      throw new SQLException("Error deserializing MarketplaceTransaction", e);
    }
  }
}
