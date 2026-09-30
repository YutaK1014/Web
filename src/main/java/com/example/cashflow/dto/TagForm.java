package com.example.cashflow.dto;

import java.util.Arrays;
import java.util.List;
import lombok.Data;
import org.springframework.validation.Errors;

@Data
public class TagForm {
    private String tags = "";

    public static List<String> parse(String value) {
        if (value == null || value.isBlank()) return List.of();
        if (value.length() > 1000) throw new IllegalArgumentException("タグの入力は1,000文字以内にしてください。");
        var tags = Arrays.stream(value.split("[,、，\\r\\n]"))
            .map(String::strip).filter(s -> !s.isEmpty()).distinct().toList();
        if (tags.size() > 10 || tags.stream().anyMatch(s -> s.length() > 30))
            throw new IllegalArgumentException("タグは1つ30文字以内、10個まで入力してください。");
        return tags;
    }

    public static void validate(String value, Errors errors) {
        try { parse(value); }
        catch (IllegalArgumentException exception) { errors.rejectValue("tags", "invalid", exception.getMessage()); }
    }
}
