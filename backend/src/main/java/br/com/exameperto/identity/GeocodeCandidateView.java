package br.com.exameperto.identity;
import java.math.BigDecimal;
record GeocodeCandidateView(String label, BigDecimal latitude, BigDecimal longitude, String provider, String reference, String confirmationToken) {}
