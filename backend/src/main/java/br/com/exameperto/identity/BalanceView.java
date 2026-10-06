package br.com.exameperto.identity;

import java.math.BigDecimal;
import java.util.UUID;

record BalanceView(UUID programId, String currency, BigDecimal available, BigDecimal reserved,
                   BigDecimal settled, long version) {}
