package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named="EXAME_PERTO_ROUTE_API_KEY",matches=".+")
class ConfiguredGeocoderExternalTest {
    @Test void searchesPublicPlaceOnly(){
        String geocodeKey=System.getenv("EXAME_PERTO_GEOCODE_API_KEY"); if(geocodeKey==null||geocodeKey.isBlank())geocodeKey=System.getenv("EXAME_PERTO_ROUTE_API_KEY");
        ConfiguredGeocoder geocoder=new ConfiguredGeocoder(true,geocodeKey,URI.create(System.getenv().getOrDefault("EXAME_PERTO_GEOCODE_ENDPOINT","https://api.heigit.org/pelias/v1/search")),Duration.ofSeconds(15),65536,new ObjectMapper(),new DataProtector(System.getenv("EXAME_PERTO_DATA_KEY"),System.getenv("EXAME_PERTO_SEARCH_KEY")));
        Address publicPlace=new Address("Avenida Paulista","1578","Bela Vista","São Paulo","SP","01310200",null,null);
        var candidates=geocoder.search(publicPlace);
        assertThat(candidates).isNotEmpty(); assertThat(candidates).allSatisfy(c->{assertThat(c.provider()).isEqualTo("openrouteservice-geocoding");assertThat(c.latitude()).isBetween(BigDecimal.valueOf(-90),BigDecimal.valueOf(90));assertThat(c.longitude()).isBetween(BigDecimal.valueOf(-180),BigDecimal.valueOf(180));});
        System.out.printf("GEOCODE_HOMOLOGATION provider=%s candidates=%d first_candidate_coordinates_present=true%n",candidates.get(0).provider(),candidates.size());
    }
}
