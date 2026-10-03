package br.com.exameperto.identity;

import java.util.UUID;

record DocumentView(UUID id, String category, String mime, long size, String status, long version) {}
