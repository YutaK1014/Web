package com.example.cashflow.service;

import com.example.cashflow.entity.Transaction;
import com.example.cashflow.repository.TransactionRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TransactionService {
    private final TransactionRepository repository;

    public TransactionService(TransactionRepository repository) {
        this.repository = repository;
    }

    public List<Transaction> getTransactions() {
        return repository.findAll();
    }
}
