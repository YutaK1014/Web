package com.example.cashflow.repository;

import com.example.cashflow.entity.MarketplaceSetting;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MarketplaceSettingRepository {
    @Select("SELECT id, marketplace_name, fee_rate FROM marketplace_settings ORDER BY id")
    List<MarketplaceSetting> findAll();

    @Select("SELECT id, marketplace_name, fee_rate FROM marketplace_settings WHERE marketplace_name=#{name}")
    MarketplaceSetting findByMarketplaceName(String name);

    @Update("UPDATE marketplace_settings SET fee_rate=#{rate}, updated_at=CURRENT_TIMESTAMP WHERE marketplace_name=#{name}")
    int update(@Param("name") String name, @Param("rate") BigDecimal rate);

    @Insert("INSERT INTO marketplace_settings(marketplace_name,fee_rate) VALUES(#{name},#{rate})")
    void insert(@Param("name") String name, @Param("rate") BigDecimal rate);

    @Select("SELECT setting_value FROM app_settings WHERE setting_key=#{key}")
    String getOption(String key);

    @Update("UPDATE app_settings SET setting_value=#{value} WHERE setting_key=#{key}")
    int updateOption(@Param("key") String key, @Param("value") String value);

    @Insert("INSERT INTO app_settings(setting_key,setting_value) VALUES(#{key},#{value})")
    void insertOption(@Param("key") String key, @Param("value") String value);
}
