package io.quagmire.itemmarketplace.integrations.accountmanagement;

import com.trappedmc.accountmanagement.api.*;
import io.quagmire.itemmarketplace.databases.implementations.ListingsDatabase;
import io.quagmire.itemmarketplace.databases.implementations.TransactionHistoryDatabase;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ItemMarketplaceAccountDataProvider implements AccountDataProvider {

    private static final String PROVIDER_ID = "ItemMarketplace";

    private final ListingsDatabase listingsDatabase;
    private final TransactionHistoryDatabase transactionHistoryDatabase;

    public ItemMarketplaceAccountDataProvider(ListingsDatabase listingsDatabase, TransactionHistoryDatabase transactionHistoryDatabase) {
        this.listingsDatabase = listingsDatabase;
        this.transactionHistoryDatabase = transactionHistoryDatabase;
    }

    @Override
    public @NotNull String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Item Marketplace";
    }

    @Override
    public int getPriority() {
        return 80;
    }

    @Override
    public @NotNull CompletableFuture<DataSnapshot> snapshot(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                int listingCount = listingsDatabase.countPlayerListings(uuid);
                int transactionCount = transactionHistoryDatabase.countPlayerTransactions(uuid);
                int total = listingCount + transactionCount;

                if (total <= 0) {
                    return DataSnapshot.empty(PROVIDER_ID);
                }

                List<String> summaryLines = new ArrayList<>();
                if (listingCount > 0) summaryLines.add(listingCount + " listing(s)");
                if (transactionCount > 0) summaryLines.add(transactionCount + " transaction(s)");

                List<String> backupLines = List.of(
                        "player_uuid:" + uuid + ",listings:" + listingCount + ",transactions:" + transactionCount
                );
                return new DataSnapshot(PROVIDER_ID, total, summaryLines, backupLines);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to snapshot item marketplace data", e);
            }
        });
    }

    @Override
    public @NotNull CompletableFuture<OperationResult> copy(@NotNull UUID source, @NotNull UUID target) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Update seller_uuid on active listings from source to target
                int rows = listingsDatabase.transferActiveListings(source, target);
                // Transactions are audit/snapshot data and are not copied
                return OperationResult.success(PROVIDER_ID, rows);
            } catch (SQLException e) {
                return OperationResult.failure(PROVIDER_ID, "Failed to copy active marketplace listings", e);
            }
        });
    }

    @Override
    public @NotNull CompletableFuture<OperationResult> transfer(@NotNull UUID source, @NotNull UUID target) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Update seller_uuid on active listings from source to target
                int rows = listingsDatabase.transferActiveListings(source, target);
                // Transactions are audit/snapshot data and are not transferred
                return OperationResult.success(PROVIDER_ID, rows);
            } catch (SQLException e) {
                return OperationResult.failure(PROVIDER_ID, "Failed to transfer active marketplace listings", e);
            }
        });
    }

    @Override
    public @NotNull CompletableFuture<OperationResult> wipe(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                int listingRows = listingsDatabase.deletePlayerListings(uuid);
                int transactionRows = transactionHistoryDatabase.deletePlayerTransactions(uuid);
                return OperationResult.success(PROVIDER_ID, listingRows + transactionRows);
            } catch (SQLException e) {
                return OperationResult.failure(PROVIDER_ID, "Failed to delete marketplace data", e);
            }
        });
    }

    @Override
    public @NotNull CompletableFuture<VerificationResult> verify(@NotNull OperationType type, @NotNull UUID source, @NotNull UUID target) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<String> discrepancies = new ArrayList<>();

                switch (type) {
                    case COPY, TRANSFER -> {
                        // Active listings should have moved from source to target
                    }
                    case WIPE -> {
                        if (listingsDatabase.hasPlayerListings(source)) {
                            discrepancies.add("Player still has marketplace listings after wipe");
                        }
                        if (transactionHistoryDatabase.hasPlayerTransactions(source)) {
                            discrepancies.add("Player still has marketplace transactions after wipe");
                        }
                    }
                }

                return discrepancies.isEmpty()
                        ? VerificationResult.ok(PROVIDER_ID)
                        : VerificationResult.failed(PROVIDER_ID, discrepancies);
            } catch (SQLException e) {
                return VerificationResult.failed(PROVIDER_ID, List.of("Verification failed: " + e.getMessage()));
            }
        });
    }
}
