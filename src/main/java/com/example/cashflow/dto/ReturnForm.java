package com.example.cashflow.dto;

import lombok.Data;
import org.springframework.validation.Errors;

@Data
public class ReturnForm {
    private String returnCost = "";

    public void validate(Errors errors) {
        if (returnCost == null || !returnCost.matches("[0-9]{1,10}")
                || Long.parseLong(returnCost) > 1_000_000_000L) {
            errors.rejectValue("returnCost", "invalid", "返品費用は0〜1,000,000,000円の整数で入力してください。費用がなければ0を入力してください。");
        }
    }
}
