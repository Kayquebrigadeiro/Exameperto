package br.com.exameperto.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
final class ConfiguredGeocoder implements Geocoder {
    private final boolean enabled; private final String apiKey; private final URI endpoint; private final Duration timeout;
    private final int maxBytes; private final HttpClient client; private final ObjectMapper json; private final DataProtector protector;
    ConfiguredGeocoder(@Value("${orders.geocode.enabled:false}") boolean enabled,
                       @Value("${orders.geocode.api-key:}") String apiKey,
                       @Value("${orders.geocode.endpoint:https://api.heigit.org/pelias/v1/search}") URI endpoint,
                       @Value("${orders.geocode.timeout:PT3S}") Duration timeout,
                       @Value("${orders.geocode.max-response-bytes:65536}") int maxBytes,
                       ObjectMapper json, DataProtector protector) {
        this.enabled=enabled; this.apiKey=apiKey==null?"":apiKey.trim(); this.endpoint=endpoint; this.timeout=timeout;
        this.maxBytes=maxBytes; this.json=json; this.protector=protector; this.client=HttpClient.newBuilder().connectTimeout(timeout).build();
        if (maxBytes < 1024) throw new IllegalArgumentException("orders.geocode.max-response-bytes must be at least 1024");
        if (!endpoint.getScheme().equalsIgnoreCase("https") && !endpoint.getHost().equals("127.0.0.1") && !endpoint.getHost().equals("localhost"))
            throw new IllegalArgumentException("orders.geocode.endpoint must use HTTPS outside local tests");
    }
    @Override public List<Candidate> search(Address address) {
        validate(address); if (!enabled || apiKey.isBlank()) throw OrderService.error(HttpStatus.SERVICE_UNAVAILABLE,"INTEGRATION_UNAVAILABLE","A busca de localização não está habilitada.");
        String query = query(address); String url=endpoint+"?text="+URLEncoder.encode(query,StandardCharsets.UTF_8)+"&boundary.country=BR&size=5";
        HttpRequest request=HttpRequest.newBuilder(URI.create(url)).timeout(timeout).header("Authorization",apiKey).header("Accept","application/json").header("User-Agent","ExamePerto/geocoder-adapter (+local-development)").GET().build();
        try {
            HttpResponse<InputStream> response=client.send(request,HttpResponse.BodyHandlers.ofInputStream()); byte[] bytes;
            try(InputStream in=response.body()){bytes=in.readNBytes(maxBytes+1);} if(bytes.length>maxBytes) throw unavailable("A resposta de localização excedeu o limite permitido.");
            if(response.statusCode()==429) throw OrderService.error(HttpStatus.SERVICE_UNAVAILABLE,"ROUTE_RATE_LIMITED","O provedor de localização atingiu o limite de consultas.");
            if(response.statusCode()<200||response.statusCode()>=300) throw unavailable("O provedor de localização não respondeu normalmente.");
            JsonNode features=json.readTree(bytes).path("features"); if(!features.isArray()) throw invalid(); List<Candidate> out=new ArrayList<>();
            for(JsonNode feature:features){JsonNode coords=feature.path("geometry").path("coordinates"), props=feature.path("properties"); if(!coords.isArray()||coords.size()<2) continue; BigDecimal lon=decimal(coords.get(0)),lat=decimal(coords.get(1)); String label=text(props,"label"); if(lat==null||lon==null||label.isBlank()||!valid(lat,lon))continue; String ref=text(props,"id"); if(ref.isBlank())ref="candidate:"+out.size(); out.add(new Candidate(label,lat,lon,"openrouteservice-geocoding",ref,protector.seal(payload(query,label,lat,lon,ref)))); if(out.size()==5)break; }
            return List.copyOf(out);
        } catch(OrderService.OrderException e){throw e;} catch(InterruptedException e){Thread.currentThread().interrupt();throw unavailable("A busca de localização foi interrompida.");} catch(Exception e){throw unavailable("O provedor de localização não respondeu no prazo.");}
    }
    String confirm(String token, Address address) {
        try {JsonNode p=json.readTree(protector.unseal(token)); if(p.path("expiresAt").asLong()<System.currentTimeMillis()/1000L||!p.path("query").asText().equals(query(address))) throw new IllegalArgumentException(); return p.toString();}
        catch(Exception e){throw OrderService.error(HttpStatus.UNPROCESSABLE_ENTITY,"LOCATION_CONFIRMATION_INVALID","Confirme novamente a localização depois de revisar os campos do endereço.");}
    }
    private String payload(String query,String label,BigDecimal lat,BigDecimal lon,String ref){return "{\"query\":"+jsonText(query)+",\"label\":"+jsonText(label)+",\"latitude\":"+lat+",\"longitude\":"+lon+",\"provider\":\"openrouteservice-geocoding\",\"reference\":"+jsonText(ref)+",\"expiresAt\":"+(System.currentTimeMillis()/1000L+600)+"}";}
    private String query(Address a){return String.join(", ",a.street(),a.number(),a.district(),a.city(),a.state(),a.postalCode()).trim();}
    private static void validate(Address a){if(a==null||a.street().isBlank()||a.number().isBlank()||a.city().isBlank()||a.state().isBlank()||!a.postalCode().matches("\\d{8}"))throw OrderService.error(HttpStatus.BAD_REQUEST,"INVALID_ADDRESS","Informe rua, número, cidade, UF e CEP para buscar a localização.");}
    private static boolean valid(BigDecimal lat,BigDecimal lon){return lat.abs().compareTo(BigDecimal.valueOf(90))<=0&&lon.abs().compareTo(BigDecimal.valueOf(180))<=0;}
    private static BigDecimal decimal(JsonNode n){return n!=null&&n.isNumber()?n.decimalValue():null;}
    private static String text(JsonNode n,String k){return n.path(k).asText("").trim();}
    private static String jsonText(String v){try{return new ObjectMapper().writeValueAsString(v);}catch(Exception e){throw new IllegalStateException(e);}}
    private static OrderService.OrderException invalid(){return OrderService.error(HttpStatus.SERVICE_UNAVAILABLE,"INTEGRATION_UNAVAILABLE","A resposta de localização é inválida.");}
    private static OrderService.OrderException unavailable(String m){return OrderService.error(HttpStatus.SERVICE_UNAVAILABLE,"INTEGRATION_UNAVAILABLE",m);}
}
