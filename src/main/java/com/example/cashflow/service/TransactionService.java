package com.example.cashflow.service;

import com.example.cashflow.entity.Transaction;
import com.example.cashflow.dto.TransactionForm;
import com.example.cashflow.dto.TransactionFilter;
import com.example.cashflow.repository.TransactionRepository;
import com.example.cashflow.repository.TagRepository;
import com.example.cashflow.dto.TagForm;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.slf4j.LoggerFactory;

@Service
public class TransactionService {
    private final TransactionRepository repository;
    private final PhotoStorage photos;
    private final MarketplaceService marketplaces;
    private final TagRepository tags;

    public TransactionService(TransactionRepository repository, PhotoStorage photos, MarketplaceService marketplaces,
                              TagRepository tags) {
        this.repository = repository;
        this.photos = photos;
        this.marketplaces = marketplaces;
        this.tags = tags;
    }

    public List<Transaction> search(TransactionFilter filter) {
        var rows = repository.search(filter.getKeyword(), filter.getMarketplace(), filter.start(), filter.end());
        rows.forEach(row -> row.setTags(tags.transactionTags(row.getId())));
        String tag = filter.getTag() == null ? "" : filter.getTag().strip();
        return tag.isEmpty() ? rows : rows.stream().filter(row -> row.getTags().contains(tag)).toList();
    }

    public Transaction get(long id) {
        Transaction row = repository.findById(id);
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "取引が見つかりません。");
        row.setTags(tags.transactionTags(id));
        return row;
    }

    @Transactional(rollbackFor = IOException.class)
    public void save(Long id, TransactionForm form) throws IOException {
        var errors = new BeanPropertyBindingResult(form, "form");
        form.validate(errors);
        if (errors.hasErrors()) throw new IllegalArgumentException(errors.getAllErrors().getFirst().getDefaultMessage());
        Transaction row = id == null ? new Transaction() : get(id);
        String oldPhoto = row.getImagePath();
        row.setItemName(form.getItemName());
        row.setMarketplace(form.getMarketplace());
        row.setCustomMarketplace("その他".equals(form.getMarketplace()) ? form.getCustomMarketplace() : null);
        row.setSellingPrice(Integer.parseInt(form.getSellingPrice()));
        row.setFeeRate(marketplaces.rate(form.getMarketplace(), form.getFeeRate()));
        row.setSellingFee(marketplaces.calculateFee(row.getSellingPrice(), row.getFeeRate()));
        row.setShippingCost(Integer.parseInt(form.getShippingCost()));
        row.setPurchasePrice(form.purchaseAmount());
        row.setSoldDate(LocalDate.parse(form.getSoldDate()));
        row.setMemo(form.getMemo());
        String newPhoto = photos.save(form.getPhoto());
        row.setImagePath(newPhoto != null ? newPhoto : form.isRemovePhoto() ? null : oldPhoto);
        // New files are removed even if the SQL transaction fails at commit time.
        if (newPhoto != null && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) cleanup(newPhoto);
                }
            });
        }
        try {
            if (id == null) repository.insert(row);
            else if (repository.update(row) == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            tags.clearTransaction(row.getId());
            for (String tag : TagForm.parse(form.getTags())) tags.addTransaction(row.getId(), tag);
        } catch (RuntimeException exception) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) cleanup(newPhoto);
            throw exception;
        }
        if (oldPhoto != null && !oldPhoto.equals(row.getImagePath())) afterCommitCleanup(oldPhoto);
    }

    @Transactional
    public void delete(long id) {
        Transaction row = get(id);
        if (repository.delete(id) == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        afterCommitCleanup(row.getImagePath());
    }

    private void afterCommitCleanup(String path) {
        if (path == null) return;
        Runnable remove = () -> {
            if (repository.countImageReferences(path) == 0) cleanup(path);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { remove.run(); }
            });
        } else remove.run();
    }

    private void cleanup(String path) {
        try { photos.delete(path); }
        catch (IOException exception) { LoggerFactory.getLogger(getClass()).warn("画像ファイルを削除できませんでした: {}", path, exception); }
    }
}
