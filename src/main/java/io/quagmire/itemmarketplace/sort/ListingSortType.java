package io.quagmire.itemmarketplace.sort;

import io.quagmire.itemmarketplace.messages.Message;

public enum ListingSortType {
    NEWEST(Message.SORT_NEWEST),
    OLDEST(Message.SORT_OLDEST),
    PRICE_LOW(Message.SORT_PRICE_LOW),
    PRICE_HIGH(Message.SORT_PRICE_HIGH);
    
    private final Message messageKey;
    
    ListingSortType(Message messageKey) {
        this.messageKey = messageKey;
    }
    
    public Message getMessageKey() {
        return messageKey;
    }
    
    public ListingSortType next() {
        ListingSortType[] values = ListingSortType.values();
        return values[(this.ordinal() + 1) % values.length];
    }
} 