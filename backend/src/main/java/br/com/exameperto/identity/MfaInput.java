package br.com.exameperto.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record MfaPasswordInput(@NotBlank @Size(max=128) String password) {}
record MfaCodeInput(@NotBlank @Pattern(regexp="[0-9]{6}") String code) {}
record MfaEnrollment(String secret, String provisioningUri) {}
record MfaStatus(boolean enrolled, boolean verifiedForSession) {}
