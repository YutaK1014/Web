package com.example.cashflow.repository;

import com.example.cashflow.entity.Purchase;
import java.util.List;
import java.time.LocalDate;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PurchaseRepository {
    @Select("SELECT p.*, COALESCE(v.version, 0) AS version FROM purchases p LEFT JOIN purchase_versions v ON v.purchase_id=p.id WHERE p.id=#{id}")
    Purchase findById(long id);
    @Select("SELECT id FROM purchases WHERE id=#{id} FOR UPDATE")
    Long lockById(long id);

    @Insert("INSERT INTO purchase_versions(purchase_id, version) VALUES(#{id}, 1) ON DUPLICATE KEY UPDATE version=version+1")
    void incrementVersion(long id);

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
