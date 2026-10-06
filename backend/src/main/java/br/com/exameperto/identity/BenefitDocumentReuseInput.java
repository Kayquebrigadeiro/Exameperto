package br.com.exameperto.identity;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

record BenefitDocumentReuseInput(@NotNull UUID documentId, @NotNull BenefitDimension purpose, long requestVersion) {}
