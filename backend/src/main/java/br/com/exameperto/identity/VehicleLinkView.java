package br.com.exameperto.identity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

record VehicleLinkView(UUID id, UUID vehicleId, String plate, String make, String model, String color,
    int manufacturingYear, int modelYear, String linkType, LocalDate validUntil, String status,
    long version, boolean evidenceComplete, List<VehicleDocumentView> documents) {}
