package br.com.exameperto.identity;

import java.util.UUID;

record DelivererView(UUID id, String status, long version) {}
