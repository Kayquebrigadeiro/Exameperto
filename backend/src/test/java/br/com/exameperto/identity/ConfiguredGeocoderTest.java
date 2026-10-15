package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConfiguredGeocoderTest {
    private HttpServer server;
    @AfterEach void stop(){if(server!=null)server.stop(0);}

    @Test void returnsCandidatesAndBindsConfirmationToSearch() throws Exception {
        AtomicReference<String> request=new AtomicReference<>();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); server.createContext("/search",exchange->{request.set(exchange.getRequestURI().getQuery()); byte[] body="{\"features\":[{\"geometry\":{\"coordinates\":[-46.63,-23.55]},\"properties\":{\"label\":\"Rua pública, 10, São Paulo - SP\",\"id\":\"public-1\"}}]}".getBytes();exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
        ConfiguredGeocoder geocoder=provider(); Address input=address(null,null); var candidates=geocoder.search(input);
        assertThat(candidates).hasSize(1); assertThat(request.get()).contains("text=Rua,+10,+Centro,+São+Paulo,+SP,+01001000").contains("boundary.country=BR").contains("size=5");
        assertThat(geocoder.confirm(candidates.get(0).confirmationToken(),input)).contains("public-1");
        assertThatThrownBy(()->geocoder.confirm(candidates.get(0).confirmationToken(),new Address("Rua diferente","10","Centro","São Paulo","SP","01001000",null,null))).isInstanceOf(OrderService.OrderException.class);
    }

    @Test void noResultIsAValidEmptySearch() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/search",exchange->{byte[] body="{\"features\":[]}".getBytes();exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
        assertThat(provider().search(address(null,null))).isEmpty();
    }

    @Test void invalidProviderResponseDoesNotCreateCandidate() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/search",exchange->{byte[] body="{\"features\":[{\"properties\":{\"label\":\"sem coordenadas\"}}]}".getBytes();exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
        assertThat(provider().search(address(null,null))).isEmpty();
    }

    @Test void providerUnavailableAndExpiredOrMismatchedTokensAreRejected() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/search",exchange->{byte[] body="{}".getBytes();exchange.sendResponseHeaders(503,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
        assertThatThrownBy(()->provider().search(address(null,null))).isInstanceOf(OrderService.OrderException.class).hasMessageContaining("503");
        DataProtector protector=new DataProtector("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="); ConfiguredGeocoder geocoder=provider(); Address input=address(null,null);
        String expired=protector.seal("{\"query\":\"Rua, 10, Centro, São Paulo, SP, 01001000\",\"label\":\"public\",\"latitude\":-23.55,\"longitude\":-46.63,\"provider\":\"openrouteservice-geocoding\",\"reference\":\"public-1\",\"expiresAt\":0}");
        assertThatThrownBy(()->geocoder.confirm(expired,input)).isInstanceOf(OrderService.OrderException.class);
    }

    private ConfiguredGeocoder provider(){return new ConfiguredGeocoder(true,"test-key",URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/search"),Duration.ofSeconds(2),4096,new ObjectMapper(),new DataProtector("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="));}
    private static Address address(BigDecimal lat,BigDecimal lon){return new Address("Rua","10","Centro","São Paulo","SP","01001000",lat,lon);}
}
