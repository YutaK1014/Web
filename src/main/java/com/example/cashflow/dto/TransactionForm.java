package com.example.cashflow.dto;

import java.util.List;
import lombok.Data;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;

@Data
public class TransactionForm {
    public static final List<String> PLATFORMS = List.of("メルカリ", "ヤフーオークション", "ラクマ", "その他");
    private String itemName = "";
    private String sellingPrice = "";
    private String purchasePrice = "";
    private String shippingCost = "";
    private String marketplace = "";
    private String customMarketplace = "";
    private MultipartFile photo;

    public void validate(Errors errors) {
        itemName = itemName == null ? "" : itemName.strip();
        customMarketplace = customMarketplace == null ? "" : customMarketplace.strip();
        if (itemName.isEmpty() || itemName.length() > 100) {
            errors.rejectValue("itemName", "invalid", "商品名は1〜100文字で入力してください。");
        }
        validateAmount(errors, "sellingPrice", sellingPrice, false);
        validateAmount(errors, "shippingCost", shippingCost, false);
        validateAmount(errors, "purchasePrice", purchasePrice, true);
        if (marketplace == null || !PLATFORMS.contains(marketplace)) {
            errors.rejectValue("marketplace", "invalid", "取引プラットフォームを選択してください。");
        }
        if (customMarketplace.length() > 100) {
            errors.rejectValue("customMarketplace", "invalid", "その他の名前は100文字以内で入力してください。");
        }
    }

    private void validateAmount(Errors errors, String field, String value, boolean optional) {
        if (optional && (value == null || value.isBlank())) return;
        try {
            if (value == null || !value.matches("[0-9]{1,10}") || Long.parseLong(value) > 1_000_000_000L) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            errors.rejectValue(field, "invalid", "金額は0〜1,000,000,000円の整数で入力してください。");
        }
    }

    public int purchaseAmount() {
        return purchasePrice == null || purchasePrice.isBlank() ? 0 : Integer.parseInt(purchasePrice);
    }
}
