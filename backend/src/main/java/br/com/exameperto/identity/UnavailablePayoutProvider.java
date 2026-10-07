package br.com.exameperto.identity;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class UnavailablePayoutProvider implements PayoutProvider {
    public boolean configured(){return false;}
    public String providerId(){return "unavailable";}
    public Outcome request(String reference,BigDecimal amount,String currency,String recipient){throw unavailable();}
    public Outcome query(String reference){throw unavailable();}
    private RuntimeException unavailable(){return OrderService.error(HttpStatus.SERVICE_UNAVAILABLE,"INTEGRATION_UNAVAILABLE","O provedor de repasse não está habilitado.");}
}
