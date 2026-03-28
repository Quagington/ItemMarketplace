package io.quagmire.itemmarketplace.integrations.accountmanagement;

import com.trappedmc.accountmanagement.api.AccountManagementAPI;
import io.quagmire.itemmarketplace.databases.implementations.ListingsDatabase;
import io.quagmire.itemmarketplace.databases.implementations.TransactionHistoryDatabase;
import io.quagmire.itemmarketplace.manager.ListingManager;

public class AccountManagementLoader {

    private static ItemMarketplaceAccountDataProvider provider;

    public static void load(ListingsDatabase listingsDatabase, TransactionHistoryDatabase transactionHistoryDatabase, ListingManager listingManager) {
        provider = new ItemMarketplaceAccountDataProvider(listingsDatabase, transactionHistoryDatabase, listingManager);
        AccountManagementAPI.getInstance().registerProvider(provider);
    }

    public static void unload() {
        if (provider != null) {
            AccountManagementAPI.getInstance().unregisterProvider(provider.getProviderId());
            provider = null;
        }
    }
}
