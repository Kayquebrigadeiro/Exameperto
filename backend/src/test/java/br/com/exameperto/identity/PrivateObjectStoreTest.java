package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class PrivateObjectStoreTest {
    @TempDir Path root;

    @Test void writesAndReadsRealPrivateObjectWithGeneratedKey() throws Exception {
        PrivateObjectStore store = new PrivateObjectStore(root.toString());
        UUID id=UUID.randomUUID(); byte[] bytes="%PDF-synthetic".getBytes();
        String key=store.put(id,new MockMultipartFile("file","original-name.pdf","application/pdf",bytes));
        assertThat(key).isEqualTo("quarantine/"+id+".bin");
        assertThat(Files.exists(root.resolve(key))).isTrue();
        if (Files.getFileStore(root).supportsFileAttributeView("posix")) {
            assertThat(Files.getPosixFilePermissions(root.resolve(key))).containsExactlyInAnyOrder(
                java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
            assertThat(Files.getPosixFilePermissions(root.resolve("quarantine"))).containsExactlyInAnyOrder(
                java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE);
        }
        try(var in=store.open(key)){assertThat(in.readAllBytes()).containsExactly(bytes);}
        assertThatThrownBy(() -> store.open("../outside.bin")).isInstanceOf(IllegalStateException.class);
        store.remove(key); assertThat(Files.exists(root.resolve(key))).isFalse();
    }
}
