package br.com.exameperto.identity;

import java.util.UUID;

record VehicleDocumentView(UUID id, String purpose, String mime, long size, String status, boolean current) {}
