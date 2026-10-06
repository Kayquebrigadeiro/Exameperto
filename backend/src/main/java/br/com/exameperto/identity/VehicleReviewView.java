package br.com.exameperto.identity;

import java.util.List;
import java.util.UUID;

record VehicleReviewView(UUID id, UUID linkId, long linkVersion, UUID analystId, String status,
    String decision, String reasonCode, List<VehicleDocumentView> documents) {}
