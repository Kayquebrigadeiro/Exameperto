package br.com.exameperto.identity;

import java.util.UUID;

record OperationalIdentityView(String displayName, String plate, String model, String color, UUID photoDocumentId) {}
