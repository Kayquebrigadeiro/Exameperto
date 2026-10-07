package br.com.exameperto.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class PayoutDispatcher {
    private final PayoutService payouts; private final TransactionTemplate tx; private final ObjectMapper json;
    PayoutDispatcher(PayoutService payouts,TransactionTemplate tx,ObjectMapper json){this.payouts=payouts;this.tx=tx;this.json=json;}
    PayoutView dispatch(UUID id,boolean reconciliation){
        Map<String,Object> row=tx.execute(status->payouts.claim(id));
        PayoutProvider.Outcome outcome;
        try { outcome=reconciliation?payouts.provider().query(String.valueOf(row.get("referencia"))):payouts.provider().request(String.valueOf(row.get("referencia")),(java.math.BigDecimal)row.get("valor"),String.valueOf(row.get("moeda")),String.valueOf(row.get("destinatario_referencia"))); }
        catch(RuntimeException ex){outcome=new PayoutProvider.Outcome("timeout:"+UUID.randomUUID(),String.valueOf(row.get("referencia")),PayoutProvider.Result.UNCERTAIN,(java.math.BigDecimal)row.get("valor"),String.valueOf(row.get("moeda")),String.valueOf(row.get("destinatario_referencia")),Instant.now());}
        PayoutProvider.Outcome finalOutcome=outcome;byte[] hash=hash(outcome);
        tx.executeWithoutResult(status->payouts.applyOutcome(id,finalOutcome,hash));
        return tx.execute(status->payouts.getForSystem(id));
    }
    private byte[] hash(PayoutProvider.Outcome value){try{return MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(value));}catch(Exception ex){throw new IllegalStateException(ex);}}
}
