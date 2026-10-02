package br.com.exameperto.identity;

import java.util.UUID;

record PatientView(UUID id, String identityStatus, long version) {}
