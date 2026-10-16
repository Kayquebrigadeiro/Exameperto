package br.com.exameperto.identity;

import java.time.LocalDate;
import java.util.UUID;

record DelivererView(UUID id, LocalDate birthDate, String status, long version) {}
