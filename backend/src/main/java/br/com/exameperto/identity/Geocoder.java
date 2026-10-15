package br.com.exameperto.identity;

import java.math.BigDecimal;
import java.util.List;

interface Geocoder {
    List<Candidate> search(Address address);
    record Candidate(String label, BigDecimal latitude, BigDecimal longitude, String provider, String reference, String confirmationToken) {}
}
