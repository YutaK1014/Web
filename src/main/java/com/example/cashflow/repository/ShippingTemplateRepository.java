package com.example.cashflow.repository;

import com.example.cashflow.entity.ShippingTemplate;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ShippingTemplateRepository {
    @Select("SELECT * FROM shipping_templates ORDER BY id")
    List<ShippingTemplate> findAll();

    @Select("SELECT * FROM shipping_templates WHERE id=#{id}")
    ShippingTemplate findById(long id);

    @Insert("INSERT INTO shipping_templates(name,shipping_method,packaging,shipping_cost,packaging_cost) VALUES(#{name},#{shippingMethod},#{packaging},#{shippingCost},#{packagingCost})")
    void insert(ShippingTemplate row);

    @Update("UPDATE shipping_templates SET name=#{name},shipping_method=#{shippingMethod},packaging=#{packaging},shipping_cost=#{shippingCost},packaging_cost=#{packagingCost} WHERE id=#{id}")
    int update(ShippingTemplate row);

    @Delete("DELETE FROM shipping_templates WHERE id=#{id}")
    int delete(long id);
}
