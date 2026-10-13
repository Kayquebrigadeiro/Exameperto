package br.com.exameperto.identity;

public interface EmailGateway {
    boolean configured();
    void send(String recipient, String subject, String text);
}
