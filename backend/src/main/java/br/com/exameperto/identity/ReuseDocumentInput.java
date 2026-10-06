package br.com.exameperto.identity;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

record ReuseDocumentInput(@NotNull UUID documentId, @NotNull Purpose purpose, long linkVersion) {
    enum Purpose { CRLV, FOTO, USO_AUTORIZADO }
}
