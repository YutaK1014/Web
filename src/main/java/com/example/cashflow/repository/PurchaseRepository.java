package com.example.cashflow.repository;

import com.example.cashflow.entity.Purchase;
import java.util.List;
import java.time.LocalDate;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PurchaseRepository {
    @Select("SELECT * FROM purchases WHERE id=#{id}")
    Purchase findById(long id);
    default List<Purchase> findAll() { return search("", null, null); }

    @Select("""
        <script>
        SELECT p.* FROM purchases p WHERE 1=1
        <if test="from != null">AND purchased_date &gt;= #{from}</if>
        <if test="to != null">AND purchased_date &lt;= #{to}</if>
        <if test="tag != null and tag != ''">
          AND EXISTS (SELECT 1 FROM purchase_tags ft WHERE ft.purchase_id=p.id AND ft.tag=#{tag})
        </if>
        ORDER BY purchased_date DESC, id DESC
        </script>
        """)
    List<Purchase> search(@Param("tag") String tag, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Insert("""
        INSERT INTO purchases(item_name, purchased_date, amount, store, memo)
        VALUES(#{itemName}, #{purchasedDate}, #{amount}, #{store}, #{memo})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Purchase purchase);
}
