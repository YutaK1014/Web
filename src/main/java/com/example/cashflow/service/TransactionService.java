package com.example.cashflow.service;

import com.example.cashflow.entity.Transaction;
import com.example.cashflow.dto.TransactionForm;
import java.io.IOException;
import java.time.LocalDate;
import com.example.cashflow.repository.TransactionRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TransactionService {
    private final TransactionRepository repository;
    private final PhotoStorage photos;

    public TransactionService(TransactionRepository repository, PhotoStorage photos) {
        this.repository = repository;
        this.photos = photos;
    }

    public List<Transaction> getTransactions() {
        return repository.findAll();
    }

    public void create(TransactionForm form) throws IOException {
        String imagePath = photos.save(form.getPhoto());
        try {
            repository.insert(new Transaction(0, form.getItemName(), form.getMarketplace(),
                    "その他".equals(form.getMarketplace()) ? form.getCustomMarketplace() : null,
                    Integer.parseInt(form.getSellingPrice()), 0, Integer.parseInt(form.getShippingCost()),
                    form.purchaseAmount(), LocalDate.now(), imagePath));
        } catch (RuntimeException exception) {
            try {
                photos.delete(imagePath);
            } catch (IOException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }
}
