package com.example.cashflow.dto;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record HistoryTag(long historyId, String tag) {
    public static Map<Long, List<String>> group(List<HistoryTag> tags) {
        return tags.stream().collect(Collectors.groupingBy(HistoryTag::historyId,
            Collectors.mapping(HistoryTag::tag, Collectors.toList())));
    }
}
