package com.example.cashflow.entity;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class MarketplaceSetting {
    private long id;
    private String marketplaceName;
    private BigDecimal feeRate;
}
