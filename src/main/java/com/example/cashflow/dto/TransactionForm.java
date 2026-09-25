package com.example.cashflow.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.List;
import lombok.Data;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import com.example.cashflow.entity.Transaction;

@Data
public class TransactionForm {
    public static final List<String> PLATFORMS = List.of("メルカリ", "ラクマ", "Yahoo!フリマ", "その他");
    private String itemName = "";
    private String sellingPrice = "";
    private String purchasePrice = "";
    private String shippingCost = "";
    private String marketplace = "";
    private String customMarketplace = "";
    private String feeRate = "";
    private String soldDate = "";
    private String memo = "";
    private boolean removePhoto;
    private MultipartFile photo;

    public void validate(Errors errors) {
        itemName = itemName == null ? "" : itemName.strip();
        customMarketplace = customMarketplace == null ? "" : customMarketplace.strip();
        if (itemName.isEmpty() || itemName.length() > 100)
            errors.rejectValue("itemName", "invalid", "商品名は1〜100文字で入力してください。");
        validateAmount(errors, "sellingPrice", sellingPrice, false);
        validateAmount(errors, "shippingCost", shippingCost, false);
        validateAmount(errors, "purchasePrice", purchasePrice, true);
        if (marketplace == null || !PLATFORMS.contains(marketplace))
            errors.rejectValue("marketplace", "invalid", "フリマサイトを選択してください。");
        if (customMarketplace.length() > 100)
            errors.rejectValue("customMarketplace", "invalid", "その他のサイト名は100文字以内で入力してください。");
        if ("その他".equals(marketplace)) {
            if (customMarketplace.isBlank())
                errors.rejectValue("customMarketplace", "required", "その他のサイト名を入力してください。");
            if (!validRate(feeRate))
                errors.rejectValue("feeRate", "invalid", "手数料率は0〜100%（小数点以下2桁まで）で入力してください。");
        }
        try {
            var date = LocalDate.parse(soldDate == null ? "" : soldDate);
            if (date.getYear() < 1000 || date.getYear() > 9999) throw new DateTimeException("range");
        } catch (DateTimeException exception) {
            errors.rejectValue("soldDate", "invalid", "販売日を正しく入力してください。");
        }
        if (memo != null && memo.length() > 10000)
            errors.rejectValue("memo", "invalid", "メモは10,000文字以内で入力してください。");
    }

    public static boolean validRate(String value) {
        if (value == null || !value.matches("[0-9]{1,3}(\\.[0-9]{1,2})?")) return false;
        return new BigDecimal(value).compareTo(new BigDecimal("100")) <= 0;
    }

    private void validateAmount(Errors errors, String field, String value, boolean optional) {
        if (optional && (value == null || value.isBlank())) return;
        if (value == null || !value.matches("[0-9]{1,10}") || Long.parseLong(value) > 1_000_000_000L)
            errors.rejectValue(field, "invalid", "金額は0〜1,000,000,000円の整数で入力してください。");
    }

    public int purchaseAmount() {
        return purchasePrice == null || purchasePrice.isBlank() ? 0 : Integer.parseInt(purchasePrice);
    }

    public static TransactionForm from(Transaction row) {
        TransactionForm form = new TransactionForm();
        form.setItemName(row.getItemName());
        form.setMarketplace(row.getMarketplace());
        form.setCustomMarketplace(row.getCustomMarketplace());
        form.setSellingPrice(String.valueOf(row.getSellingPrice()));
        form.setShippingCost(String.valueOf(row.getShippingCost()));
        form.setPurchasePrice(row.getPurchasePrice() == null ? "" : row.getPurchasePrice().toString());
        form.setFeeRate(row.getFeeRate().toPlainString());
        form.setSoldDate(row.getSoldDate().toString());
        form.setMemo(row.getMemo());
        return form;
    }
}
