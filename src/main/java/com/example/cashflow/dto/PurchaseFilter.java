package com.example.cashflow.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.validation.Errors;

@Getter
@Setter
public class PurchaseFilter extends DateRangeFilter {
    private String tag = "";

    @Override
    public void validate(Errors errors) {
        super.validate(errors);
        tag = tag == null ? "" : tag.strip();
        if (tag.length() > 30) errors.rejectValue("tag", "invalid", "検索タグは30文字以内で入力してください。");
    }
}
