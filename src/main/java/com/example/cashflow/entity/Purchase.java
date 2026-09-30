package com.example.cashflow.entity;

import java.time.LocalDate;
import lombok.Data;

@Data
public class Purchase {
    private Long id;
    private String itemName;
    private LocalDate purchasedDate;
    private int amount;
    private String store;
    private String memo;
    private java.util.List<String> tags = java.util.List.of();
}
