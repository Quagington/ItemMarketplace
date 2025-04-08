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
public class MarketplaceListing {
  private final long listingId;
  private final UUID sellerUuid;
  private final ItemStack itemStack;
  private final BigDecimal price;
  private final boolean isActive;
  private final Timestamp createDateUtc;
  private final Timestamp lastUpdateDateUtc;
  private final Timestamp expiryDateUtc;

  public MarketplaceListing(
    long listingId,
    UUID sellerUuid,
    ItemStack itemStack,
    BigDecimal price,
    boolean isActive,
    Timestamp createDateUtc,
    Timestamp lastUpdateDateUtc,
    Timestamp expiryDateUtc
  ) {
    this.listingId = listingId;
    this.sellerUuid = sellerUuid;
    this.itemStack = itemStack;
    this.price = price;
    this.isActive = isActive;
    this.createDateUtc = createDateUtc;
    this.lastUpdateDateUtc = lastUpdateDateUtc;
    this.expiryDateUtc = expiryDateUtc;
  }

  public static MarketplaceListing deserialize(ResultSet rs) throws SQLException {
    try {
      byte[] itemData = rs.getBytes("item_data");
      ItemStack itemStack = null;
      try {
        itemStack = ItemStackSerializer.deserializeItemStack(itemData);
      } catch (IOException e) {
        throw new SQLException("Failed to deserialize ItemStack", e);
      }
      
      return new MarketplaceListing(
        rs.getLong("listing_id"),
        UUID.fromString(rs.getString("seller_uuid")),
        itemStack,
        rs.getBigDecimal("price"),
        rs.getBoolean("is_active"),
        rs.getTimestamp("create_date_utc"),
        rs.getTimestamp("last_update_date_utc"),
        rs.getTimestamp("expiry_date_utc")
      );
    } catch (SQLException e) {
      throw new SQLException("Error deserializing MarketplaceListing", e);
    }
  }
}