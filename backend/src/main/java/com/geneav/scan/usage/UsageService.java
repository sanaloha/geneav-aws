package com.geneav.scan.usage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Reads and increments per-account monthly usage counters. Uses plain SQL (an
 * atomic {@code INSERT ... ON CONFLICT ... +1}) so metering is a single round trip
 * with no read-modify-write race.
 */
@Service
public class UsageService {

    private final JdbcTemplate jdbc;

    public UsageService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Current calendar month in UTC, e.g. "2026-07" — the counter partition key. */
    public String currentPeriod() {
        return YearMonth.now(ZoneOffset.UTC).toString();
    }

    /** Usage so far this month for the given account and endpoint (0 if none). */
    public long currentCount(UUID accountId, String endpoint) {
        Long count = jdbc.queryForObject(
                "SELECT COALESCE((SELECT count FROM usage_counter "
                        + "WHERE account_id = ? AND period_month = ? AND endpoint = ?), 0)",
                Long.class, accountId, currentPeriod(), endpoint);
        return count == null ? 0 : count;
    }

    /** Records one billable request, returning the new running total for the month. */
    public long record(UUID accountId, String endpoint) {
        return jdbc.queryForObject(
                "INSERT INTO usage_counter (account_id, period_month, endpoint, count) VALUES (?, ?, ?, 1) "
                        + "ON CONFLICT (account_id, period_month, endpoint) "
                        + "DO UPDATE SET count = usage_counter.count + 1 RETURNING count",
                Long.class, accountId, currentPeriod(), endpoint);
    }
}
