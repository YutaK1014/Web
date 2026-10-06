package com.example.cashflow.dto;

import lombok.Data;
import org.springframework.validation.Errors;

@Data
public class DiscountForm {
    private String sellingPrice = "";
    private String shippingCost = "0";
    private String purchasePrice = "0";
    private String otherCost = "0";
    private String desiredProfit = "0";
    private String marketplace = "";
    private String feeRate = "";

    public void validate(Errors errors) {
        amount(errors, "sellingPrice", sellingPrice);
        amount(errors, "shippingCost", shippingCost);
        amount(errors, "purchasePrice", purchasePrice);
        amount(errors, "otherCost", otherCost);
        amount(errors, "desiredProfit", desiredProfit);
        if (marketplace == null || !TransactionForm.PLATFORMS.contains(marketplace))
            errors.rejectValue("marketplace", "invalid", "フリマサイトを選択してください。");
        if ("その他".equals(marketplace) && !TransactionForm.validRate(feeRate))
            errors.rejectValue("feeRate", "invalid", "手数料率は0〜100%（小数点以下2桁まで）で入力してください。");
    }

    private void amount(Errors errors, String field, String value) {
        if (value == null || !value.matches("[0-9]{1,10}") || Long.parseLong(value) > 1_000_000_000L)
            errors.rejectValue(field, "invalid", "金額は0〜1,000,000,000円の整数で入力してください。費用や希望利益がない場合は0を入力してください。");
    }
}
