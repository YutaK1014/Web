package com.example.cashflow.repository;

import java.util.List;
import com.example.cashflow.dto.HistoryTag;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TagRepository {
    @Select("""
        <script>
        SELECT transaction_id AS history_id, tag FROM transaction_tags
        WHERE transaction_id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY transaction_id, tag
        </script>
        """)
    List<HistoryTag> transactionTagsByIds(@Param("ids") List<Long> ids);

    @Select("""
        <script>
        SELECT purchase_id AS history_id, tag FROM purchase_tags
        WHERE purchase_id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY purchase_id, tag
        </script>
        """)
    List<HistoryTag> purchaseTagsByIds(@Param("ids") List<Long> ids);

    @Select("SELECT tag FROM transaction_tags WHERE transaction_id=#{id} ORDER BY tag")
    List<String> transactionTags(long id);
    @Select("SELECT tag FROM purchase_tags WHERE purchase_id=#{id} ORDER BY tag")
    List<String> purchaseTags(long id);
    @Delete("DELETE FROM transaction_tags WHERE transaction_id=#{id}")
    void clearTransaction(long id);
    @Delete("DELETE FROM purchase_tags WHERE purchase_id=#{id}")
    void clearPurchase(long id);
    @Insert("INSERT INTO transaction_tags(transaction_id,tag) VALUES(#{id},#{tag})")
    void addTransaction(@Param("id") long id, @Param("tag") String tag);
    @Insert("INSERT INTO purchase_tags(purchase_id,tag) VALUES(#{id},#{tag})")
    void addPurchase(@Param("id") long id, @Param("tag") String tag);
}
