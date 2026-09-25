package com.example.cashflow.dto;

import lombok.Data;
import org.springframework.validation.Errors;
import java.util.List;

@Data
public class SettingsForm {
    private String mercari = "";
    private String rakuma = "";
    private String yahoo = "";
    private String rounding = "";
    private String monthlyGoal = "";

    public void validate(Errors errors) {
        if (!TransactionForm.validRate(mercari)) errors.rejectValue("mercari", "invalid", "メルカリの手数料率を0〜100%で入力してください。");
        if (!TransactionForm.validRate(rakuma)) errors.rejectValue("rakuma", "invalid", "ラクマの手数料率を0〜100%で入力してください。");
        if (!TransactionForm.validRate(yahoo)) errors.rejectValue("yahoo", "invalid", "Yahoo!フリマの手数料率を0〜100%で入力してください。");
        if (!List.of("DOWN", "HALF_UP", "UP").contains(rounding))
            errors.rejectValue("rounding", "invalid", "手数料の端数処理を選択してください。");
        if (monthlyGoal != null && !monthlyGoal.isBlank()
                && (!monthlyGoal.matches("[0-9]{1,10}") || Long.parseLong(monthlyGoal) > 1_000_000_000L))
            errors.rejectValue("monthlyGoal", "invalid", "月間利益目標は0〜1,000,000,000円で入力してください。");
    }
}
