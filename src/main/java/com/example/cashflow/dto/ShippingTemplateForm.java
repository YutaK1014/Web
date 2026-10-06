package com.example.cashflow.dto;

import com.example.cashflow.entity.ShippingTemplate;
import lombok.Data;
import org.springframework.validation.Errors;

@Data
public class ShippingTemplateForm {
    private String name = "";
    private String shippingMethod = "";
    private String packaging = "";
    private String shippingCost = "";
    private String packagingCost = "0";

    public void validate(Errors errors) {
        name = name == null ? "" : name.strip();
        shippingMethod = shippingMethod == null ? "" : shippingMethod.strip();
        packaging = packaging == null ? "" : packaging.strip();
        if (name.isEmpty() || name.length() > 100)
            errors.rejectValue("name", "invalid", "テンプレート名は1〜100文字で入力してください。");
        if (shippingMethod.isEmpty() || shippingMethod.length() > 100)
            errors.rejectValue("shippingMethod", "invalid", "配送方法は1〜100文字で入力してください。");
        if (packaging.length() > 100)
            errors.rejectValue("packaging", "invalid", "梱包内容は100文字以内で入力してください。");
        boolean shippingValid = validAmount(shippingCost);
        boolean packagingValid = validAmount(packagingCost);
        if (!shippingValid) errors.rejectValue("shippingCost", "invalid", "送料は0〜1,000,000,000円の整数で入力してください。");
        if (!packagingValid) errors.rejectValue("packagingCost", "invalid", "梱包費は0〜1,000,000,000円の整数で入力してください。");
        if (shippingValid && packagingValid && Long.parseLong(shippingCost) + Long.parseLong(packagingCost) > 1_000_000_000L)
            errors.rejectValue("packagingCost", "invalid", "送料と梱包費の合計は1,000,000,000円以内にしてください。");
    }

    private boolean validAmount(String value) {
        return value != null && value.matches("[0-9]{1,10}") && Long.parseLong(value) <= 1_000_000_000L;
    }

    public ShippingTemplate toEntity() {
        var row = new ShippingTemplate();
        row.setName(name); row.setShippingMethod(shippingMethod); row.setPackaging(packaging);
        row.setShippingCost(Integer.parseInt(shippingCost)); row.setPackagingCost(Integer.parseInt(packagingCost));
        return row;
    }

    public static ShippingTemplateForm from(ShippingTemplate row) {
        var form = new ShippingTemplateForm();
        form.setName(row.getName()); form.setShippingMethod(row.getShippingMethod()); form.setPackaging(row.getPackaging());
        form.setShippingCost(Integer.toString(row.getShippingCost())); form.setPackagingCost(Integer.toString(row.getPackagingCost()));
        return form;
    }
}
