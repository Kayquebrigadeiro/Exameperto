package br.com.exameperto.identity;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
final class AccessTokens {
    private final byte[] key;
    AccessTokens(@Value("${security.signing-key:}") String configured,
                 @Value("${security.data-key:}") String data,
                 @Value("${security.search-key:}") String search) {
        key = DataProtector.decode(configured);
        if (key != null && (configured.equals(data) || configured.equals(search)))
            throw new IllegalStateException("Use uma chave de assinatura independente.");
    }
    boolean configured() { return key != null; }
    String issue(UUID user, UUID session, Instant expiry) {
        try {
            var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                .issuer("exame-perto").audience("exame-perto-api").subject(user.toString())
                .jwtID(session.toString()).issueTime(new Date()).expirationTime(Date.from(expiry)).build());
            jwt.sign(new MACSigner(key)); return jwt.serialize();
        } catch (JOSEException ex) { throw new IllegalStateException("Assinatura indisponível."); }
    }
    UUID session(String raw) {
        try {
            if (key == null || raw.length() > 2048) return null;
            SignedJWT jwt = SignedJWT.parse(raw);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(key))) return null;
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!"exame-perto".equals(claims.getIssuer()) || !claims.getAudience().equals(java.util.List.of("exame-perto-api"))
                || claims.getExpirationTime() == null || !claims.getExpirationTime().toInstant().isAfter(Instant.now())) return null;
            return UUID.fromString(claims.getJWTID());
        } catch (Exception ex) { return null; }
    }
}
