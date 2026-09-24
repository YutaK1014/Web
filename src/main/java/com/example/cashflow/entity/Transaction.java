package com.example.cashflow.entity;

import java.time.LocalDate;

/** 取引一覧で表示するデータ。金額の単位は円。 */
public record Transaction(
        long id,
        String itemName,
        String marketplace,
        String customMarketplace,
        int sellingPrice,
        int sellingFee,
        int shippingCost,
        Integer purchasePrice,
        LocalDate soldDate,
        String imagePath) {

    public long getProfit() {
        return (long) sellingPrice - sellingFee - shippingCost
                - (purchasePrice == null ? 0 : purchasePrice);
    }

    public String getPlatformName() {
        return "その他".equals(marketplace) && customMarketplace != null
                && !customMarketplace.isBlank() ? customMarketplace : marketplace;
    }

    /** 商品画像には、アプリ内の画像保存先だけを使用する。 */
    public String getPhotoUrl() {
        if (imagePath == null || !imagePath.matches("/images/[a-zA-Z0-9_-]+\\.(?i:jpg|jpeg|png|webp)")) {
            return null;
        }
        return imagePath;
    }
}
