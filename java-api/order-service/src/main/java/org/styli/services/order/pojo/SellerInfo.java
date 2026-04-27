package org.styli.services.order.pojo;

/**
 * Immutable class to hold seller identification information.
 * Replaces the use of String[] for returning seller ID and name together.
 */
public class SellerInfo {
    
    private final String sellerId;
    private final String sellerName;
    
    public SellerInfo(String sellerId, String sellerName) {
        this.sellerId = sellerId;
        this.sellerName = sellerName;
    }
    
    public String getSellerId() {
        return sellerId;
    }
    
    public String getSellerName() {
        return sellerName;
    }
    
    @Override
    public String toString() {
        return "SellerInfo{" +
                "sellerId='" + sellerId + '\'' +
                ", sellerName='" + sellerName + '\'' +
                '}';
    }
}
