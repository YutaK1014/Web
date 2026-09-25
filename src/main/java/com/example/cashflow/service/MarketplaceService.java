package com.example.cashflow.service;

import com.example.cashflow.dto.SettingsForm;
import com.example.cashflow.entity.MarketplaceSetting;
import com.example.cashflow.repository.MarketplaceSettingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketplaceService {
    private final MarketplaceSettingRepository repository;
    public MarketplaceService(MarketplaceSettingRepository repository) { this.repository = repository; }

    public Map<String, BigDecimal> rates() {
        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        for (MarketplaceSetting setting : repository.findAll()) rates.put(setting.getMarketplaceName(), setting.getFeeRate());
        return rates;
    }

    public BigDecimal rate(String marketplace, String customRate) {
        if ("その他".equals(marketplace)) return new BigDecimal(customRate);
        MarketplaceSetting setting = repository.findByMarketplaceName(marketplace);
        if (setting == null) throw new IllegalArgumentException("設定画面でフリマサイトの手数料率を設定してください。");
        return setting.getFeeRate();
    }

    public String rounding() { return repository.getOption("fee_rounding"); }

    public int calculateFee(int price, BigDecimal rate) {
        String mode = rounding();
        if (mode == null || !List.of("DOWN", "HALF_UP", "UP").contains(mode))
            throw new IllegalArgumentException("設定画面で手数料の端数処理を選択してください。");
        return BigDecimal.valueOf(price).multiply(rate).movePointLeft(2).setScale(0, RoundingMode.valueOf(mode)).intValueExact();
    }

    public SettingsForm settings() {
        SettingsForm form = new SettingsForm();
        Map<String, BigDecimal> rates = rates();
        form.setMercari(rates.containsKey("メルカリ") ? rates.get("メルカリ").toPlainString() : "");
        form.setRakuma(rates.containsKey("ラクマ") ? rates.get("ラクマ").toPlainString() : "");
        form.setYahoo(rates.containsKey("Yahoo!フリマ") ? rates.get("Yahoo!フリマ").toPlainString() : "");
        form.setRounding(rounding() == null ? "" : rounding());
        form.setMonthlyGoal(repository.getOption("monthly_goal"));
        return form;
    }

    @Transactional
    public void save(SettingsForm form) {
        saveRate("メルカリ", form.getMercari());
        saveRate("ラクマ", form.getRakuma());
        saveRate("Yahoo!フリマ", form.getYahoo());
        saveOption("fee_rounding", form.getRounding());
        saveOption("monthly_goal", form.getMonthlyGoal() == null ? "" : form.getMonthlyGoal());
    }

    public Long monthlyGoal() {
        String value = repository.getOption("monthly_goal");
        return value == null || value.isBlank() ? null : Long.valueOf(value);
    }

    private void saveRate(String name, String value) {
        BigDecimal rate = new BigDecimal(value);
        if (repository.update(name, rate) == 0) repository.insert(name, rate);
    }

    private void saveOption(String key, String value) {
        if (repository.updateOption(key, value) == 0) repository.insertOption(key, value);
    }
}
