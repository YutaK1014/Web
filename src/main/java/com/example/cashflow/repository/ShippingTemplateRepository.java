package com.example.cashflow.repository;

import com.example.cashflow.entity.ShippingTemplate;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ShippingTemplateRepository {
    @Select("SELECT t.*, COALESCE(v.version, 0) AS version FROM shipping_templates t LEFT JOIN shipping_template_versions v ON v.template_id=t.id ORDER BY t.id")
    List<ShippingTemplate> findAll();

    @Select("SELECT t.*, COALESCE(v.version, 0) AS version FROM shipping_templates t LEFT JOIN shipping_template_versions v ON v.template_id=t.id WHERE t.id=#{id}")
    ShippingTemplate findById(long id);

    @Select("SELECT id FROM shipping_templates WHERE id=#{id} FOR UPDATE")
    Long lockById(long id);

    @Insert("INSERT INTO shipping_template_versions(template_id, version) VALUES(#{id}, 1) ON DUPLICATE KEY UPDATE version=version+1")
    void incrementVersion(long id);

    @Insert("INSERT INTO shipping_templates(name,shipping_method,packaging,shipping_cost,packaging_cost) VALUES(#{name},#{shippingMethod},#{packaging},#{shippingCost},#{packagingCost})")
    void insert(ShippingTemplate row);

    @Update("UPDATE shipping_templates SET name=#{name},shipping_method=#{shippingMethod},packaging=#{packaging},shipping_cost=#{shippingCost},packaging_cost=#{packagingCost} WHERE id=#{id}")
    int update(ShippingTemplate row);

    @Delete("DELETE FROM shipping_templates WHERE id=#{id}")
    int delete(long id);
}
