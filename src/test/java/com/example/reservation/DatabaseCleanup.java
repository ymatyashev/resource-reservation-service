package com.example.reservation;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

public abstract class DatabaseCleanup {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanupDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE idempotency_keys RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE reservations RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE resources RESTART IDENTITY CASCADE");
    }
}
