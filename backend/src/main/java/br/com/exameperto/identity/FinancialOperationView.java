package br.com.exameperto.identity;

import java.math.BigDecimal;
import java.util.UUID;

record FinancialOperationView(UUID id,UUID orderId,String type,BigDecimal amount,String currency,String status,long version) {}
