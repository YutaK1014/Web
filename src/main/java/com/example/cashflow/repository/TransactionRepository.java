package com.example.cashflow.repository;

import com.example.cashflow.entity.Transaction;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;

@Mapper
public interface TransactionRepository {

    // ログインなしのローカル試作用。公開前にログインユーザーでの絞り込みが必要。
    @Select("""
            SELECT id, item_name AS itemName, marketplace,
                   custom_marketplace AS customMarketplace,
                   selling_price AS sellingPrice, selling_fee AS sellingFee,
                   shipping_cost AS shippingCost, purchase_price AS purchasePrice,
                   sold_date AS soldDate, image_path AS imagePath
            FROM transactions
            ORDER BY sold_date DESC, id DESC
            """)
    List<Transaction> findAll();

    @Insert("""
            INSERT INTO transactions (item_name, marketplace, custom_marketplace,
                selling_price, fee_rate, selling_fee, shipping_cost, purchase_price,
                profit, sold_date, image_path)
            VALUES (#{itemName}, #{marketplace}, #{customMarketplace}, #{sellingPrice},
                0, 0, #{shippingCost}, #{purchasePrice}, #{profit}, #{soldDate}, #{imagePath})
            """)
    void insert(Transaction transaction);
}
