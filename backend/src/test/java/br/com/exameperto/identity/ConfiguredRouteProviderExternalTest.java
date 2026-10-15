package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "EXAME_PERTO_ROUTE_API_KEY", matches = ".+")
class ConfiguredRouteProviderExternalTest {
    @Test void queriesPublicCoordinatesThroughConfiguredProvider() {
        ConfiguredRouteProvider provider = new ConfiguredRouteProvider(
                true,
                System.getenv("EXAME_PERTO_ROUTE_API_KEY"),
                URI.create(System.getenv().getOrDefault("EXAME_PERTO_ROUTE_ENDPOINT", "https://api.heigit.org/openrouteservice/v2/directions/driving-car")),
                Duration.ofSeconds(5),
                65536,
                0,
                new ObjectMapper());
        Address saoPaulo = new Address("Avenida Paulista", "1578", "Bela Vista", "São Paulo", "SP", "01310200", new BigDecimal("-23.5613"), new BigDecimal("-46.6565"));
        Address rio = new Address("Cristo Redentor", "1", "Alto da Boa Vista", "Rio de Janeiro", "RJ", "22241125", new BigDecimal("-22.9519"), new BigDecimal("-43.2105"));

        RouteProvider.RouteResult result = provider.route(saoPaulo, rio);

        assertThat(result.provider()).isEqualTo("openrouteservice");
        assertThat(result.reference()).startsWith("ors:").hasSize(68);
        assertThat(result.distanceMeters()).isPositive();
        assertThat(result.durationSeconds()).isPositive();
        assertThat(result.trafficIncluded()).isFalse();
        System.out.printf("ROUTE_HOMOLOGATION provider=%s distance_m=%d duration_s=%d traffic_included=%s%n", result.provider(), result.distanceMeters(), result.durationSeconds(), result.trafficIncluded());
    }
}
