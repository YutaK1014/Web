package com.example.cashflow.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

@Data
public class Transaction {
    private long id;
    private String itemName;
    private String marketplace;
    private String customMarketplace;
    private int sellingPrice;
    // 10.00 means 10%.
    private BigDecimal feeRate;
    private int sellingFee;
    private int shippingCost;
    private Integer purchasePrice;
    private LocalDate soldDate;
    private String imagePath;
    private String memo;
    private List<String> tags = List.of();

    public long getProfit() {
        return (long) sellingPrice - sellingFee - shippingCost - (purchasePrice == null ? 0 : purchasePrice);
    }

    public String getPlatformName() {
        return "その他".equals(marketplace) && customMarketplace != null && !customMarketplace.isBlank()
                ? customMarketplace : marketplace;
    }

    public String getPhotoUrl() {
        return imagePath != null && imagePath.matches("/images/[a-zA-Z0-9_-]+\\.(?i:jpg|jpeg|png|webp)") ? imagePath : null;
    }
}
