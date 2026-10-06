package br.com.exameperto.identity;

import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class UnavailablePaymentProvider implements PaymentProvider {
    public boolean configured(){return false;}
    public String providerId(){return "unavailable";}
    public PaymentEvent authenticateAndParse(byte[] body, Map<String,String> headers){
        throw OrderService.error(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"INTEGRATION_UNAVAILABLE","O provedor de pagamento não está habilitado.");
    }
}
