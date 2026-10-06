package br.com.exameperto.identity;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrations/payments")
class PaymentEventController {
    private final OrderService orders;
    PaymentEventController(OrderService orders){this.orders=orders;}
    @PostMapping(value="/events",consumes="application/octet-stream")
    ResponseEntity<Void> event(HttpServletRequest request) throws IOException {
        Map<String,String> headers=new LinkedHashMap<>();
        for(String name:Collections.list(request.getHeaderNames()))headers.put(name.toLowerCase(java.util.Locale.ROOT),request.getHeader(name));
        boolean created=orders.paymentEvent(request.getInputStream().readAllBytes(),headers);
        return created?ResponseEntity.accepted().build():ResponseEntity.ok().build();
    }
}
