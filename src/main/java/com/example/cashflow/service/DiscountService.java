package com.example.cashflow.service;

import com.example.cashflow.dto.DiscountForm;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class DiscountService {
    private static final int MAX_PRICE = 1_000_000_000;
    private final MarketplaceService marketplaces;

    public DiscountService(MarketplaceService marketplaces) { this.marketplaces = marketplaces; }

    public record Result(BigDecimal rate, String rounding, int currentFee, long currentProfit,
                         Integer minimumPrice, long discount, long shortfall, long desiredProfit,
                         Integer minimumFee, Long minimumProfit) {}

    public Result calculate(DiscountForm form) {
        int price = Integer.parseInt(form.getSellingPrice());
        long costs = Long.parseLong(form.getShippingCost()) + Long.parseLong(form.getPurchasePrice())
            + Long.parseLong(form.getOtherCost());
        long desired = Long.parseLong(form.getDesiredProfit());
        BigDecimal rate = marketplaces.rate(form.getMarketplace(), form.getFeeRate());
        String rounding = marketplaces.rounding();
        int fee = marketplaces.calculateFee(price, rate, rounding);
        long profit = (long) price - fee - costs;
        Integer minimum = minimumPrice(costs + desired, rate, rounding);
        Integer minimumFee = minimum == null ? null : marketplaces.calculateFee(minimum, rate, rounding);
        Long minimumProfit = minimum == null ? null : (long) minimum - minimumFee - costs;
        return new Result(rate, rounding, fee, profit, minimum,
            minimum == null ? 0 : Math.max(0L, (long) price - minimum),
            Math.max(0L, desired - profit), desired, minimumFee, minimumProfit);
    }

    private Integer minimumPrice(long required, BigDecimal rate, String rounding) {
        if ((long) MAX_PRICE - marketplaces.calculateFee(MAX_PRICE, rate, rounding) < required) return null;
        int low = 0, high = MAX_PRICE;
        // 手数料の丸めを含む手取り額は単調増加する。条件を満たす最初の1円を探す。
        while (low < high) {
            int middle = low + (high - low) / 2;
            if ((long) middle - marketplaces.calculateFee(middle, rate, rounding) >= required) high = middle;
            else low = middle + 1;
        }
        return low;
    }
}
