package br.com.exameperto.identity;

import java.time.Instant;
import java.util.UUID;

record AssignmentView(UUID id, UUID orderId, UUID driverId, UUID vehicleLinkId, String status,
                      Instant acceptedAt, Instant closedAt, long version) {}
