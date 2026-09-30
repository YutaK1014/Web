package com.example.cashflow.dto;

import java.time.DateTimeException;
import java.time.LocalDate;
import lombok.Data;
import org.springframework.validation.Errors;

@Data
public class PurchaseForm {
    private String itemName = "";
    private String purchasedDate = LocalDate.now().toString();
    private String amount = "";
    private String store = "";
    private String memo = "";
    private String tags = "";

    public void validate(Errors errors) {
        TagForm.validate(tags, errors);
        itemName = itemName == null ? "" : itemName.strip();
        store = store == null ? "" : store.strip();
        if (itemName.isEmpty() || itemName.length() > 100)
            errors.rejectValue("itemName", "invalid", "商品名は1〜100文字で入力してください。");
        if (amount == null || !amount.matches("[0-9]{1,10}") || Long.parseLong(amount) > 1_000_000_000L)
            errors.rejectValue("amount", "invalid", "購入金額は0〜1,000,000,000円の整数で入力してください。");
        try {
            var date = LocalDate.parse(purchasedDate == null ? "" : purchasedDate);
            if (date.getYear() < 1000 || date.getYear() > 9999) throw new DateTimeException("range");
        } catch (DateTimeException exception) {
            errors.rejectValue("purchasedDate", "invalid", "購入日を正しく入力してください。");
        }
        if (store.length() > 100)
            errors.rejectValue("store", "invalid", "購入先は100文字以内で入力してください。");
        if (memo != null && memo.length() > 10000)
            errors.rejectValue("memo", "invalid", "メモは10,000文字以内で入力してください。");
    }
}
