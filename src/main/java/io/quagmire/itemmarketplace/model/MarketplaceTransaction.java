package io.quagmire.itemmarketplace.model;

import io.quagmire.core.utilities.item.ItemStackSerializer;
import lombok.Getter;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

@Getter
public class MarketplaceTransaction {
  private final long transactionId;
  private final long listingId;
  private final UUID sellerUuid;
  private final UUID buyerUuid;
  private final ItemStack itemStack;
  private final BigDecimal pricePaid;
  private final BigDecimal sellerPayout;
  private final boolean isBlackMarketTransaction;
  private final Timestamp transactionDateUtc;

  /**
   * Legacy constructor that keeps the same price for both buyer and seller
   */
  public MarketplaceTransaction(
    long transactionId,
    long listingId,
    UUID sellerUuid,
    UUID buyerUuid,
    ItemStack itemStack,
    BigDecimal price,
    Timestamp transactionDateUtc
  ) {
    this(transactionId, listingId, sellerUuid, buyerUuid, itemStack, price, price, false, transactionDateUtc);
  }
  
  /**
   * Full constructor that supports different prices for buyer and seller (for black market)
   */
  public MarketplaceTransaction(
    long transactionId,
    long listingId,
    UUID sellerUuid,
    UUID buyerUuid,
    ItemStack itemStack,
    BigDecimal pricePaid,
    BigDecimal sellerPayout,
    boolean isBlackMarketTransaction,
    Timestamp transactionDateUtc
  ) {
    this.transactionId = transactionId;
    this.listingId = listingId;
    this.sellerUuid = sellerUuid;
    this.buyerUuid = buyerUuid;
    this.itemStack = itemStack;
    this.pricePaid = pricePaid;
    this.sellerPayout = sellerPayout;
    this.isBlackMarketTransaction = isBlackMarketTransaction;
    this.transactionDateUtc = transactionDateUtc;
  }

  public static MarketplaceTransaction deserialize(ResultSet rs) throws SQLException {
    try {
      byte[] itemData = rs.getBytes("item_data");
      ItemStack itemStack = null;
      try {
        itemStack = ItemStackSerializer.deserializeItemStack(itemData);
      } catch (IOException e) {
        throw new SQLException("Failed to deserialize ItemStack", e);
      }

      // Try to read black market columns, fall back to legacy if not available
      try {
        boolean isBlackMarket = rs.getBoolean("is_black_market");
        BigDecimal sellerPayout = rs.getBigDecimal("seller_payout");
        BigDecimal pricePaid = rs.getBigDecimal("price_paid");
        
        return new MarketplaceTransaction(
          rs.getLong("transaction_id"),
          rs.getLong("listing_id"),
          UUID.fromString(rs.getString("seller_uuid")),
          UUID.fromString(rs.getString("buyer_uuid")),
          itemStack,
          pricePaid,
          sellerPayout,
          isBlackMarket,
          rs.getTimestamp("transaction_date_utc")
        );
      } catch (SQLException e) {
        // Fallback to legacy column names
        return new MarketplaceTransaction(
          rs.getLong("transaction_id"),
          rs.getLong("listing_id"),
          UUID.fromString(rs.getString("seller_uuid")),
          UUID.fromString(rs.getString("buyer_uuid")),
          itemStack,
          rs.getBigDecimal("price"),
          rs.getTimestamp("transaction_date_utc")
        );
      }
    } catch (SQLException e) {
      throw new SQLException("Error deserializing MarketplaceTransaction", e);
    }
  }
  
  /**
   * Get the price paid by the buyer
   * @deprecated Use getPricePaid() instead
   */
  @Deprecated
  public BigDecimal getPrice() {
    return pricePaid;
  }
} 