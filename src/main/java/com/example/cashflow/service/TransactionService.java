package com.example.cashflow.service;

import com.example.cashflow.entity.Transaction;
import com.example.cashflow.dto.TransactionForm;
import com.example.cashflow.dto.ReturnForm;
import com.example.cashflow.dto.TransactionFilter;
import com.example.cashflow.repository.TransactionRepository;
import com.example.cashflow.repository.TagRepository;
import com.example.cashflow.dto.TagForm;
import com.example.cashflow.dto.HistoryTag;
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
    private final SubmissionService submissions;

    public TransactionService(TransactionRepository repository, PhotoStorage photos, MarketplaceService marketplaces,
                              TagRepository tags, SubmissionService submissions) {
        this.repository = repository;
        this.photos = photos;
        this.marketplaces = marketplaces;
        this.tags = tags;
        this.submissions = submissions;
    }

    public List<Transaction> search(TransactionFilter filter) {
        String tag = filter.getTag() == null ? "" : filter.getTag().strip();
        var rows = repository.searchWithTag(filter.getKeyword(), filter.getMarketplace(), filter.start(), filter.end(), tag);
        if (rows.isEmpty()) return rows;
        var groupedTags = HistoryTag.group(tags.transactionTagsByIds(rows.stream().map(Transaction::getId).toList()));
        rows.forEach(row -> row.setTags(groupedTags.getOrDefault(row.getId(), List.of())));
        // SQL narrows candidates; preserve exact matching even with a case-insensitive DB collation.
        return tag.isEmpty() ? rows : rows.stream().filter(row -> row.getTags().contains(tag)).toList();
    }

    @Transactional
    public Transaction get(long id) {
        repository.lockById(id);
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
        if (row.isReturned()) throw new IllegalArgumentException("返品済みの取引は「返品費用を編集」から変更してください。");
        if (id == null) submissions.claim(form.getSubmissionId(), "sale");
        else if (form.getVersion() == null || form.getVersion() != row.getVersion())
            throw new SaveConflictException("別の画面で取引が更新されています。入力内容を控え、最新の編集画面を開き直して変更内容を確認してください。");
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
            if (id != null) repository.incrementVersion(id);
            tags.clearTransaction(row.getId());
            for (String tag : TagForm.parse(form.getTags())) tags.addTransaction(row.getId(), tag);
        } catch (RuntimeException exception) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) cleanup(newPhoto);
            throw exception;
        }
        if (oldPhoto != null && !oldPhoto.equals(row.getImagePath())) afterCommitCleanup(oldPhoto);
    }

    @Transactional
    public void saveReturn(long id, ReturnForm form) {
        var errors = new BeanPropertyBindingResult(form, "form");
        form.validate(errors);
        if (errors.hasErrors()) throw new IllegalArgumentException(errors.getAllErrors().getFirst().getDefaultMessage());
        if (repository.lockById(id) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "取引が見つかりません。");
        Transaction row = get(id);
        if (form.getVersion() == null || form.getVersion() != row.getVersion())
            throw new SaveConflictException("別の画面で取引または返品費用が更新されたか、確認情報が失われています。入力内容を控え、最新の返品画面を開き直して確認してください。返品費用は保存していません。");
        repository.saveReturn(id, Integer.parseInt(form.getReturnCost()));
        repository.incrementVersion(id);
    }

    @Transactional
    public void delete(long id, Long version) {
        // Read the photo only after any in-flight update has committed.
        if (repository.lockById(id) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "取引が見つかりません。");
        Transaction row = get(id);
        if (version == null || version != row.getVersion())
            throw new SaveConflictException("削除確認後に取引が更新されたか、確認情報が失われています。取引は削除していません。最新の内容を確認してから、もう一度削除してください。");
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
