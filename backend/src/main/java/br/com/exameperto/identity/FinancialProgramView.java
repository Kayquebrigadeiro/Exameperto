package br.com.exameperto.identity;

import java.util.UUID;

record FinancialProgramView(UUID id, UUID institutionId, String institutionName,
                            String code, String name, String status) {}
