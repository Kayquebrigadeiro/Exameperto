package br.com.exameperto.identity;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MfaService {
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private final JdbcTemplate jdbc;
    private final DataProtector protector;
    private final Duration elevationTtl;
    private final SecureRandom random = new SecureRandom();

    MfaService(JdbcTemplate jdbc, DataProtector protector,
               @Value("${security.mfa-elevation-ttl:PT5M}") Duration elevationTtl) {
        this.jdbc=jdbc; this.protector=protector; this.elevationTtl=elevationTtl;
    }

    @Transactional
    MfaEnrollment enroll(AuthService.SessionPrincipal principal, String password) {
        requirePrivileged(principal.userId());
        String hash=jdbc.queryForObject("SELECT senha_hash FROM usuario WHERE id=? FOR UPDATE",String.class,principal.userId());
        if (!Passwords.matches(password,hash)) throw AuthService.unauthorized();
        byte[] secret=new byte[20]; random.nextBytes(secret);
        String encoded=base32(secret);
        jdbc.update("INSERT INTO mfa_totp(usuario_id,segredo_cifrado) VALUES (?,?) ON CONFLICT (usuario_id) DO UPDATE SET segredo_cifrado=excluded.segredo_cifrado,confirmado_em=NULL,atualizado_em=clock_timestamp(),falhas=0,janela_falhas_em=NULL,ultimo_passo=NULL",principal.userId(),protector.encrypt(encoded));
        jdbc.update("UPDATE sessao SET mfa_verificada_ate=NULL WHERE usuario_id=?",principal.userId());
        String label=URLEncoder.encode("Exame Perto:"+principal.userId(),StandardCharsets.UTF_8);
        return new MfaEnrollment(encoded,"otpauth://totp/"+label+"?secret="+encoded+"&issuer=Exame%20Perto&algorithm=SHA1&digits=6&period=30");
    }

    @Transactional(noRollbackFor = DelivererService.DelivererException.class)
    MfaStatus confirm(AuthService.SessionPrincipal principal, String code) {
        verify(principal,code,true);
        jdbc.update("UPDATE mfa_totp SET confirmado_em=coalesce(confirmado_em,clock_timestamp()),atualizado_em=clock_timestamp() WHERE usuario_id=?",principal.userId());
        elevate(principal.sessionId());
        return status(principal);
    }

    @Transactional(noRollbackFor = DelivererService.DelivererException.class)
    MfaStatus verify(AuthService.SessionPrincipal principal, String code) {
        verify(principal,code,false); elevate(principal.sessionId()); return status(principal);
    }

    MfaStatus status(AuthService.SessionPrincipal principal) {
        boolean enrolled=jdbc.queryForObject("SELECT count(*) FROM mfa_totp WHERE usuario_id=? AND confirmado_em IS NOT NULL",Long.class,principal.userId())>0;
        return new MfaStatus(enrolled,isVerified(principal));
    }

    void requireVerified(AuthService.SessionPrincipal principal) {
        if (!isVerified(principal)) throw DelivererService.error(HttpStatus.FORBIDDEN,"MFA_REQUIRED","A ação exige MFA validado no servidor para esta sessão.");
    }

    private void verify(AuthService.SessionPrincipal principal,String code,boolean allowPending) {
        requirePrivileged(principal.userId());
        Map<String,Object> row;
        try { row=jdbc.queryForMap("SELECT segredo_cifrado,confirmado_em,falhas,janela_falhas_em,ultimo_passo FROM mfa_totp WHERE usuario_id=? FOR UPDATE",principal.userId()); }
        catch (EmptyResultDataAccessException ex) { throw DelivererService.error(HttpStatus.FORBIDDEN,"MFA_REQUIRED","Configure o fator MFA antes da ação privilegiada."); }
        if (!allowPending && row.get("confirmado_em")==null) throw DelivererService.error(HttpStatus.FORBIDDEN,"MFA_REQUIRED","Confirme o fator MFA antes da ação privilegiada.");
        Instant now=Instant.now(); Timestamp window=(Timestamp)row.get("janela_falhas_em"); int failures=((Number)row.get("falhas")).intValue();
        if (window!=null && window.toInstant().plus(Duration.ofMinutes(15)).isAfter(now) && failures>=5)
            throw DelivererService.error(HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMITED","Muitas tentativas de MFA; tente mais tarde.");
        String secret=protector.decrypt((byte[])row.get("segredo_cifrado")); long current=now.getEpochSecond()/30; Long used=row.get("ultimo_passo")==null?null:((Number)row.get("ultimo_passo")).longValue(); Long matched=null;
        for(long step=current-1;step<=current+1;step++) if (constantTime(code,totp(decodeBase32(secret),step))) { matched=step; break; }
        if (matched==null || (used!=null && matched<=used)) {
            boolean reset=window==null || window.toInstant().plus(Duration.ofMinutes(15)).isBefore(now);
            jdbc.update("UPDATE mfa_totp SET falhas=?,janela_falhas_em=?,atualizado_em=clock_timestamp() WHERE usuario_id=?",reset?1:failures+1,Timestamp.from(reset?now:window.toInstant()),principal.userId());
            throw DelivererService.error(HttpStatus.FORBIDDEN,"MFA_INVALID","Código MFA inválido ou já utilizado.");
        }
        jdbc.update("UPDATE mfa_totp SET falhas=0,janela_falhas_em=NULL,ultimo_passo=?,atualizado_em=clock_timestamp() WHERE usuario_id=?",matched,principal.userId());
    }

    private boolean isVerified(AuthService.SessionPrincipal p) {
        return jdbc.queryForObject("SELECT count(*) FROM sessao s JOIN usuario u ON u.id=s.usuario_id WHERE s.id=? AND s.usuario_id=? AND s.revogada_em IS NULL AND s.expira_em>clock_timestamp() AND s.mfa_verificada_ate>clock_timestamp() AND u.estado='ATIVO'",Long.class,p.sessionId(),p.userId())==1;
    }
    private void elevate(UUID session) { jdbc.update("UPDATE sessao SET mfa_verificada_ate=?,updated_at=clock_timestamp() WHERE id=? AND revogada_em IS NULL",Timestamp.from(Instant.now().plus(elevationTtl)),session); }
    private void requirePrivileged(UUID user) {
        if (jdbc.queryForObject("SELECT count(*) FROM papel_global WHERE usuario_id=? AND revogado_em IS NULL",Long.class,user)==0)
            throw DelivererService.error(HttpStatus.FORBIDDEN,"FORBIDDEN","Conta sem capacidade privilegiada nominal.");
    }
    private boolean constantTime(String a,String b) { return java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII),b.getBytes(StandardCharsets.US_ASCII)); }
    static String totp(byte[] secret,long step) {
        try { Mac mac=Mac.getInstance("HmacSHA1"); mac.init(new SecretKeySpec(secret,"HmacSHA1")); byte[] h=mac.doFinal(ByteBuffer.allocate(8).putLong(step).array()); int o=h[h.length-1]&15; int n=((h[o]&127)<<24)|((h[o+1]&255)<<16)|((h[o+2]&255)<<8)|(h[o+3]&255); return String.format(Locale.ROOT,"%06d",n%1_000_000); }
        catch (GeneralSecurityException ex) { throw new IllegalStateException("MFA indisponível.",ex); }
    }
    static String base32(byte[] bytes) { StringBuilder out=new StringBuilder(); int buffer=0,bits=0; for(byte value:bytes){buffer=(buffer<<8)|(value&255);bits+=8;while(bits>=5){out.append(BASE32[(buffer>>(bits-=5))&31]);}}if(bits>0)out.append(BASE32[(buffer<<(5-bits))&31]);return out.toString(); }
    static byte[] decodeBase32(String value) { java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream(); int buffer=0,bits=0; for(char c:value.toUpperCase(Locale.ROOT).toCharArray()){int v="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(c);if(v<0)throw new IllegalArgumentException();buffer=(buffer<<5)|v;bits+=5;if(bits>=8)out.write((buffer>>(bits-=8))&255);}return out.toByteArray(); }
}
