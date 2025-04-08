package io.quagmire.itemmarketplace.databases;

import io.quagmire.core.databases.DatabaseConnectionPool;
import io.quagmire.itemmarketplace.ItemMarketplacePlugin;
import io.quagmire.itemmarketplace.databases.implementations.ListingsDatabase;
import io.quagmire.itemmarketplace.databases.implementations.TransactionHistoryDatabase;
import lombok.Getter;

public class DatabaseCollection {
  @Getter private final ListingsDatabase listingsDatabase;
  @Getter private final TransactionHistoryDatabase transactionHistoryDatabase;

  public DatabaseCollection(ItemMarketplacePlugin plugin, DatabaseConnectionPool databaseConnectionPool) {
    this.listingsDatabase = new ListingsDatabase(plugin, databaseConnectionPool);
    this.transactionHistoryDatabase = new TransactionHistoryDatabase(plugin, databaseConnectionPool);
  }
}
