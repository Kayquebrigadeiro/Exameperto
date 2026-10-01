package br.com.exameperto.identity;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistrationServiceTest {
    @Test void privacyPolicyBlocksBeforeAnyProviderAction() {
        var service = new RegistrationService(() -> { throw new AssertionError("provider must not be touched"); }, false, 5, 100);
        assertThatThrownBy(() -> service.register(
            new RegistrationRequest("Pessoa de Teste", "policy@example.test", "Somente-teste-123"), "test"))
            .isInstanceOf(RegistrationService.RegistrationException.class)
            .hasMessageContaining("política de privacidade");
    }
}
