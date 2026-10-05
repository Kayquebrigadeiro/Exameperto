package br.com.exameperto.identity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.LinkOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
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
            privateDirectory(root);
            Path temporary = Files.createTempFile(root, "quarantine-", ".upload");
            privateFile(temporary);
            try (InputStream in = file.getInputStream()) { Files.copy(in, temporary, StandardCopyOption.REPLACE_EXISTING); }
            Path target = root.resolve("quarantine").resolve(id + ".bin").normalize();
            if (!target.startsWith(root)) throw new IllegalStateException("Objeto fora do armazenamento privado.");
            Files.createDirectories(target.getParent());
            privateDirectory(target.getParent());
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            privateFile(target);
            return "quarantine/" + id + ".bin";
        } catch (IOException ex) { throw new IllegalStateException("Armazenamento privado indisponível.", ex); }
    }
    InputStream open(String key) {
        try {
            Path path = resolve(key);
            if (!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) throw new IllegalArgumentException();
            return Files.newInputStream(path);
        } catch (IOException | IllegalArgumentException ex) { throw new IllegalStateException("Objeto não encontrado."); }
    }
    void remove(String key) {
        try { Files.deleteIfExists(resolve(key)); } catch (IOException | IllegalArgumentException ignored) { }
    }
    private Path resolve(String key) throws IOException { Path path=root.resolve(key).normalize();if(!path.startsWith(root)||Files.isSymbolicLink(path)||Files.isSymbolicLink(path.getParent()))throw new IllegalArgumentException();return path; }
    private void privateDirectory(Path path)throws IOException{try{Files.setPosixFilePermissions(path,Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE,PosixFilePermission.OWNER_EXECUTE));}catch(UnsupportedOperationException ignored){}}
    private void privateFile(Path path)throws IOException{try{Files.setPosixFilePermissions(path,Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE));}catch(UnsupportedOperationException ignored){}}
}
