package br.com.exameperto.identity;

public interface EmailGateway {
    boolean configured();
    default void send(String recipient, String subject, String text) { throw new IllegalStateException("e-mail indisponível"); }
}
