package br.com.exameperto.identity;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

final class Passwords {
    private static final Argon2PasswordEncoder ENCODER = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private Passwords() {}
    static String hash(String password) { return ENCODER.encode(password); }
    static boolean matches(String password, String hash) { return ENCODER.matches(password, hash); }
}
