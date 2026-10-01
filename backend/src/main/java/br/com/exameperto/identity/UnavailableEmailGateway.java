package br.com.exameperto.identity;

import org.springframework.stereotype.Component;

@Component
final class UnavailableEmailGateway implements EmailGateway {
    @Override public boolean configured() { return false; }
}
