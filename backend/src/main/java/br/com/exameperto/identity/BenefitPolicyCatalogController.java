package br.com.exameperto.identity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/benefit-policies")
class BenefitPolicyCatalogController {
    private final JdbcTemplate jdbc;

    BenefitPolicyCatalogController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    List<BenefitPolicyOption> list(Authentication authentication) {
        // A sessão autenticada é exigida pela configuração de segurança; a
        // listagem contém apenas catálogo público de instituições habilitadas.
        authentication.getDetails();
        return jdbc.query("""
                SELECT p.id,p.instituicao_id,i.nome,p.codigo,p.versao,p.criterios::text,p.percentual_maximo
                FROM politica_beneficio p
                JOIN instituicao i ON i.id=p.instituicao_id AND i.estado='HABILITADA'
                WHERE p.estado='ATIVA'
                  AND p.vigencia_inicio<=clock_timestamp()
                  AND (p.vigencia_fim IS NULL OR p.vigencia_fim>clock_timestamp())
                ORDER BY i.nome,p.codigo,p.versao DESC,p.id
                """, (rs, n) -> new BenefitPolicyOption(
                rs.getObject("id", UUID.class),
                rs.getObject("instituicao_id", UUID.class),
                rs.getString("nome"),
                rs.getString("codigo"),
                rs.getInt("versao"),
                rs.getString("criterios"),
                rs.getBigDecimal("percentual_maximo")));
    }
}

record BenefitPolicyOption(UUID id, UUID institutionId, String institutionName,
                           String code, int version, String criteria,
                           BigDecimal maximumPercentage) {}
