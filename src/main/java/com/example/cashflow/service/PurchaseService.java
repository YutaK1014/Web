package com.example.cashflow.service;

import com.example.cashflow.dto.PurchaseForm;
import com.example.cashflow.entity.Purchase;
import com.example.cashflow.repository.PurchaseRepository;
import com.example.cashflow.repository.TagRepository;
import com.example.cashflow.dto.TagForm;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.validation.Errors;

@Service
public class PurchaseService {
    private final PurchaseRepository repository;
    private final TagRepository tags;

    public PurchaseService(PurchaseRepository repository, TagRepository tags) {
        this.repository = repository;
        this.tags = tags;
    }

    public List<Purchase> all() {
        var rows = repository.findAll();
        rows.forEach(row -> row.setTags(tags.purchaseTags(row.getId())));
        return rows;
    }

    public List<Purchase> search(String tag) {
        return all().stream().filter(row -> tag.isEmpty() || row.getTags().contains(tag)).toList();
    }

    public Purchase get(long id) {
        var row = repository.findById(id);
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "購入履歴が見つかりません。");
        row.setTags(tags.purchaseTags(id));
        return row;
    }

    @Transactional
    public void updateTags(long id, String value) {
        var parsed = TagForm.parse(value);
        get(id);
        tags.clearPurchase(id);
        for (String tag : parsed) tags.addPurchase(id, tag);
    }

    @Transactional
    public void save(PurchaseForm form, Errors errors) {
        form.validate(errors);
        if (errors.hasErrors()) return;
        Purchase purchase = new Purchase();
        purchase.setItemName(form.getItemName());
        purchase.setPurchasedDate(LocalDate.parse(form.getPurchasedDate()));
        purchase.setAmount(Integer.parseInt(form.getAmount()));
        purchase.setStore(form.getStore());
        purchase.setMemo(form.getMemo());
        repository.insert(purchase);
        for (String tag : TagForm.parse(form.getTags())) tags.addPurchase(purchase.getId(), tag);
    }
}
