package com.example.cashflow.controller;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/healthz")
    public ResponseEntity<Map<String, String>> health() {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT 1")) {
            statement.setQueryTimeout(2);
            try (var result = statement.executeQuery()) {
                if (result.next()) return ResponseEntity.ok(Map.of("status", "UP"));
            }
        } catch (SQLException exception) {
            // Expose readiness only, never database credentials or exception details.
        }
        return ResponseEntity.status(503).body(Map.of("status", "DOWN"));
    }
}
