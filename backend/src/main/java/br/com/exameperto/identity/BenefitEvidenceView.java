package br.com.exameperto.identity;

import java.util.UUID;

record BenefitEvidenceView(UUID id, BenefitDimension purpose, String mime, long size, String status, boolean current) {}
