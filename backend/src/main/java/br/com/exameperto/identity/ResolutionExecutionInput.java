package br.com.exameperto.identity;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

record ResolutionExecutionInput(@NotNull UUID evidenceDocumentId) {}
