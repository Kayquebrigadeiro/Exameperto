package br.com.exameperto.identity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
final class PrivateObjectStore {
    private final Path root;
    PrivateObjectStore(@Value("${storage.private-root:${java.io.tmpdir}/exame-perto-private}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }
    String put(UUID id, MultipartFile file) {
        try {
            Files.createDirectories(root);
            Path temporary = Files.createTempFile(root, "quarantine-", ".upload");
            try (InputStream in = file.getInputStream()) { Files.copy(in, temporary, StandardCopyOption.REPLACE_EXISTING); }
            Path target = root.resolve("quarantine").resolve(id + ".bin").normalize();
            if (!target.startsWith(root)) throw new IllegalStateException("Objeto fora do armazenamento privado.");
            Files.createDirectories(target.getParent());
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return "quarantine/" + id + ".bin";
        } catch (IOException ex) { throw new IllegalStateException("Armazenamento privado indisponível.", ex); }
    }
    InputStream open(String key) {
        try {
            Path path = root.resolve(key).normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path)) throw new IllegalArgumentException();
            return Files.newInputStream(path);
        } catch (IOException | IllegalArgumentException ex) { throw new IllegalStateException("Objeto não encontrado."); }
    }
    void remove(String key) {
        try { Files.deleteIfExists(root.resolve(key).normalize()); } catch (IOException ignored) { }
    }
}
