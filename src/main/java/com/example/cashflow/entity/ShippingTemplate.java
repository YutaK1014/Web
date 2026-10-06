package com.example.cashflow.entity;

import lombok.Data;

@Data
public class ShippingTemplate {
    private Long id;
    private String name;
    private String shippingMethod;
    private String packaging;
    private int shippingCost;
    private int packagingCost;

    public int getTotalCost() { return shippingCost + packagingCost; }
}
