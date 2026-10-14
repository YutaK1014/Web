package com.example.cashflow.service;

import com.example.cashflow.repository.SubmissionRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmissionService {
    private final SubmissionRepository repository;

    public SubmissionService(SubmissionRepository repository) { this.repository = repository; }

    // The unique claim and the record/tags/photos must succeed or roll back together.
    @Transactional(propagation = Propagation.MANDATORY)
    public void claim(String id, String type) {
        if (id == null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new SaveConflictException("登録情報を確認できません。登録画面を開き直してください。");
        try {
            repository.insert(id, type);
        } catch (DuplicateKeyException exception) {
            throw new SaveConflictException("このフォームは登録済みです。一覧で登録内容を確認してください。別の取引を登録する場合は登録画面を開き直してください。");
        }
    }
}
