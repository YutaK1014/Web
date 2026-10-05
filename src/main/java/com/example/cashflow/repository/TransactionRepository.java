package com.example.cashflow.repository;

import com.example.cashflow.entity.Transaction;
import java.util.List;
import java.time.LocalDate;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TransactionRepository {
    @Select("""
        <script>
        SELECT t.*, r.return_cost FROM transactions t
        LEFT JOIN transaction_returns r ON r.transaction_id=t.id WHERE 1=1
        <if test="keyword != null and keyword != ''">AND LOCATE(#{keyword}, item_name) &gt; 0</if>
        <if test="marketplace != null and marketplace != ''">AND marketplace = #{marketplace}</if>
        <if test="from != null">AND sold_date &gt;= #{from}</if>
        <if test="to != null">AND sold_date &lt;= #{to}</if>
        ORDER BY sold_date DESC, id DESC
        </script>
        """)
    List<Transaction> search(@Param("keyword") String keyword, @Param("marketplace") String marketplace,
                            @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("SELECT t.*, r.return_cost FROM transactions t LEFT JOIN transaction_returns r ON r.transaction_id=t.id WHERE t.id=#{id}")
    Transaction findById(long id);

    @Select("SELECT id FROM transactions WHERE id=#{id} FOR UPDATE")
    Long lockById(long id);

    @Insert("""
        INSERT INTO transaction_returns(transaction_id, return_cost) VALUES(#{id}, #{cost})
        ON DUPLICATE KEY UPDATE return_cost=#{cost}
        """)
    void saveReturn(@Param("id") long id, @Param("cost") int cost);

    @Select("SELECT COUNT(*) FROM transactions WHERE image_path=#{path}")
    long countImageReferences(String path);

    @Insert("""
        INSERT INTO transactions(item_name,marketplace,custom_marketplace,selling_price,fee_rate,
          selling_fee,shipping_cost,purchase_price,profit,sold_date,image_path,memo)
        VALUES(#{itemName},#{marketplace},#{customMarketplace},#{sellingPrice},#{feeRate},
          #{sellingFee},#{shippingCost},#{purchasePrice},#{profit},#{soldDate},#{imagePath},#{memo})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Transaction transaction);

    @Update("""
        UPDATE transactions SET item_name=#{itemName},marketplace=#{marketplace},custom_marketplace=#{customMarketplace},
          selling_price=#{sellingPrice},fee_rate=#{feeRate},selling_fee=#{sellingFee},shipping_cost=#{shippingCost},
          purchase_price=#{purchasePrice},profit=#{profit},sold_date=#{soldDate},image_path=#{imagePath},memo=#{memo},
          updated_at=CURRENT_TIMESTAMP WHERE id=#{id}
        """)
    int update(Transaction transaction);

    @Delete("DELETE FROM transactions WHERE id=#{id}")
    int delete(long id);
}
