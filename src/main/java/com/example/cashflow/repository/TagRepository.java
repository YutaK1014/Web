package com.example.cashflow.repository;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TagRepository {
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
