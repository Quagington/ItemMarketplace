package io.quagmire.itemmarketplace.messages;

import io.quagmire.core.messages.MessageInitializer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum Message {
  PREFIX,
  ERROR_GENERIC,
  NO_PERMISSIONS,
  PLAYER_NOT_FOUND,
  PLAYER_ONLY,
  INVALID_MODE,
  INVALID_OPERATION,
  INVALID_NUMBER,
  INVALID_PROPERTY,
  RELOAD_SUCCESS,
  RELOAD_FAILURE,
  
  // Marketplace listings menu messages
  SORT_NEWEST,
  SORT_OLDEST,
  SORT_PRICE_LOW,
  SORT_PRICE_HIGH,
  SORT_BUTTON_NAME,
  SORT_CURRENT,
  SORT_CLICK_TO_CHANGE,
  NO_LISTINGS_TITLE,
  NO_LISTINGS_LINE1,
  NO_LISTINGS_LINE2,
  NO_LISTINGS_LINE3,
  ERROR_LOADING_LISTINGS,
  ERROR_LOADING_LISTINGS_DESC,
  LISTING_SELLER,
  LISTING_PRICE,
  LISTING_LISTED,
  LISTING_EXPIRES,
  LISTING_EXPIRED,
  LISTING_NEVER_EXPIRES,
  LISTING_CLICK_TO_PURCHASE,
  LISTING_PURCHASE_SUCCESS,
  LISTING_PURCHASE_FAILED,
  LISTING_INACTIVE,
  ERROR_PROCESSING_PURCHASE,
  PAGE_PREVIOUS,
  PAGE_NEXT,
  PAGE_INFO;

  private static Map<Message, String> getDefaultValueMapping() {
    Map<Message, String> map = new HashMap<>();
    // Core messages
    map.put(Message.PREFIX, "&8| &e&lIM&8 |");
    map.put(Message.ERROR_GENERIC, "&cAn error has occurred, please try again. If the error persists please contact an administrator.!");
    map.put(Message.NO_PERMISSIONS, "&cYou do not have permission to execute this command!");
    map.put(Message.PLAYER_NOT_FOUND, "&cPlayer not found!");
    map.put(Message.INVALID_NUMBER, "&cInvalid number!");
    map.put(Message.INVALID_OPERATION, "&cInvalid operation!");
    map.put(Message.INVALID_MODE, "&cInvalid mode!");
    map.put(Message.INVALID_PROPERTY, "&cInvalid property!");
    map.put(Message.RELOAD_SUCCESS, "%prefix% &aConfiguration reloaded!");
    map.put(Message.RELOAD_FAILURE, "%prefix% &cConfiguration could not be reloaded!");
    map.put(Message.PLAYER_ONLY, "%prefix% &cOnly players can execute this command!");
    
    // Marketplace listings menu messages
    map.put(Message.SORT_NEWEST, "&aNewest First");
    map.put(Message.SORT_OLDEST, "&aOldest First");
    map.put(Message.SORT_PRICE_LOW, "&aPrice: Low to High");
    map.put(Message.SORT_PRICE_HIGH, "&aPrice: High to Low");
    map.put(Message.SORT_BUTTON_NAME, "&eSorting Options");
    map.put(Message.SORT_CURRENT, "&7Current: %sort_type%");
    map.put(Message.SORT_CLICK_TO_CHANGE, "&eClick to change sorting");
    map.put(Message.NO_LISTINGS_TITLE, "&cNo Listings Available");
    map.put(Message.NO_LISTINGS_LINE1, "&7There are currently no items");
    map.put(Message.NO_LISTINGS_LINE2, "&7listed in the marketplace.");
    map.put(Message.NO_LISTINGS_LINE3, "&eCheck back later or list your own items!");
    map.put(Message.ERROR_LOADING_LISTINGS, "&cError Loading Listings");
    map.put(Message.ERROR_LOADING_LISTINGS_DESC, "&7Please try again later");
    map.put(Message.LISTING_SELLER, "&7Seller: &f%seller%");
    map.put(Message.LISTING_PRICE, "&7Price: &f%price%");
    map.put(Message.LISTING_LISTED, "&7Listed: &f%time_listed%");
    map.put(Message.LISTING_EXPIRES, "&7Expires: &f%expiry_time%");
    map.put(Message.LISTING_EXPIRED, "Expired");
    map.put(Message.LISTING_NEVER_EXPIRES, "Never");
    map.put(Message.LISTING_CLICK_TO_PURCHASE, "&eClick to purchase");
    map.put(Message.LISTING_PURCHASE_SUCCESS, "&aYou've purchased &f%amount% &aitem(s) for &f%price%&a!");
    map.put(Message.LISTING_PURCHASE_FAILED, "&cCouldn't purchase this item. It may have been sold already.");
    map.put(Message.LISTING_INACTIVE, "&cThis listing is no longer active.");
    map.put(Message.ERROR_PROCESSING_PURCHASE, "&cAn error occurred while processing your purchase.");
    map.put(Message.PAGE_PREVIOUS, "&aPrevious Page");
    map.put(Message.PAGE_NEXT, "&aNext Page");
    map.put(Message.PAGE_INFO, "&7Page %current_page% of %max_page%");
    
    return map;
  }

  public static String getDefaultValue(Message message) {
    return getDefaultValueMapping().get(message);
  }

  public static List<MessageInitializer> getInitializers() {
    List<MessageInitializer> initializers = new ArrayList<>();
    for (Message message : Message.values()) {
      initializers.add(new MessageInitializer(message.name().toLowerCase(), getDefaultValueMapping().get(message)));
    }
    return initializers;
  }

  public static Message getMessage(String str) {
    for (Message message : Message.values()) {
      if (message.toString().equalsIgnoreCase(str)) {
        return message;
      }
    }
    return null;
  }
}

