package com.example.cashflow.repository;

import com.example.cashflow.entity.Purchase;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PurchaseRepository {
    @Select("SELECT * FROM purchases WHERE id=#{id}")
    Purchase findById(long id);
    @Select("SELECT * FROM purchases ORDER BY purchased_date DESC, id DESC")
    List<Purchase> findAll();

    @Insert("""
        INSERT INTO purchases(item_name, purchased_date, amount, store, memo)
        VALUES(#{itemName}, #{purchasedDate}, #{amount}, #{store}, #{memo})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Purchase purchase);
}
