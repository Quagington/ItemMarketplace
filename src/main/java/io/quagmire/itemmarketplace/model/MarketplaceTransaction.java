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
  private final BigDecimal price;
  private final Timestamp transactionDateUtc;

  public MarketplaceTransaction(
    long transactionId,
    long listingId,
    UUID sellerUuid,
    UUID buyerUuid,
    ItemStack itemStack,
    BigDecimal price,
    Timestamp transactionDateUtc
  ) {
    this.transactionId = transactionId;
    this.listingId = listingId;
    this.sellerUuid = sellerUuid;
    this.buyerUuid = buyerUuid;
    this.itemStack = itemStack;
    this.price = price;
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

      return new MarketplaceTransaction(
        rs.getLong("transaction_id"),
        rs.getLong("listing_id"),
        UUID.fromString(rs.getString("seller_uuid")),
        UUID.fromString(rs.getString("buyer_uuid")),
        itemStack,
        rs.getBigDecimal("price"),
        rs.getTimestamp("transaction_date_utc")
      );
    } catch (SQLException e) {
      throw new SQLException("Error deserializing MarketplaceTransaction", e);
    }
  }
} 