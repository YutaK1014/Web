package com.example.cashflow.service;

import com.example.cashflow.dto.ShippingTemplateForm;
import com.example.cashflow.entity.ShippingTemplate;
import com.example.cashflow.repository.ShippingTemplateRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
    public void save(Long id, ShippingTemplateForm form) {
        var row = form.toEntity();
        if (id == null) repository.insert(row);
        else {
            row.setId(id);
            if (repository.update(row) == 0) throw missing();
        }
    }
    public void delete(long id) { if (repository.delete(id) == 0) throw missing(); }
    private ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "テンプレートが見つかりません。");
    }
}
