package br.com.exameperto.identity;

import java.time.Instant;

record FinancialHistoryView(String event, String status, String detail, Instant occurredAt) {}
