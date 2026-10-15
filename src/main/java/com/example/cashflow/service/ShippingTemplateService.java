package com.example.cashflow.service;

import com.example.cashflow.dto.ShippingTemplateForm;
import com.example.cashflow.entity.ShippingTemplate;
import com.example.cashflow.repository.ShippingTemplateRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;

@Service
public class ShippingTemplateService {
    private final ShippingTemplateRepository repository;
    public ShippingTemplateService(ShippingTemplateRepository repository) { this.repository = repository; }
    public List<ShippingTemplate> all() { return repository.findAll(); }
    public ShippingTemplate get(long id) {
        var row = repository.findById(id);
        if (row == null) throw missing();
        return row;
    }
    @Transactional
    public void save(Long id, ShippingTemplateForm form) {
        var errors = new BeanPropertyBindingResult(form, "templateForm");
        form.validate(errors);
        if (errors.hasErrors()) throw new IllegalArgumentException(errors.getAllErrors().getFirst().getDefaultMessage());
        var row = form.toEntity();
        if (id == null) repository.insert(row);
        else {
            checkVersion(id, form.getVersion());
            row.setId(id);
            if (repository.update(row) == 0) throw missing();
            repository.incrementVersion(id);
        }
    }
    @Transactional
    public void delete(long id, Long version) {
        checkVersion(id, version);
        if (repository.delete(id) == 0) throw missing();
    }
    private void checkVersion(long id, Long version) {
        if (repository.lockById(id) == null) throw missing();
        if (version == null || version != get(id).getVersion())
            throw new SaveConflictException("別の画面でテンプレートが更新されたか、確認情報が失われています。変更・削除はしていません。最新の画面を開き直して確認してください。");
    }
    private ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "テンプレートが見つかりません。");
    }
}
