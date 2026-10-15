package br.com.exameperto.identity;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/capabilities")
class CapabilitiesController {
    private final JdbcTemplate jdbc;

    CapabilitiesController(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @GetMapping
    CapabilitiesView get(Authentication authentication) {
        UUID user=((AuthService.SessionPrincipal)authentication.getDetails()).userId();
        List<String> global=jdbc.queryForList("SELECT papel FROM papel_global WHERE usuario_id=? AND revogado_em IS NULL ORDER BY papel",String.class,user);
        List<InstitutionCapability> institutions=jdbc.query("SELECT i.id,i.nome,i.estado,mi.papel FROM membro_instituicao mi JOIN instituicao i ON i.id=mi.instituicao_id WHERE mi.usuario_id=? AND mi.revogado_em IS NULL ORDER BY i.nome,i.id,mi.papel",(rs,n)->new InstitutionCapability(rs.getObject("id",UUID.class),rs.getString("nome"),rs.getString("estado"),rs.getString("papel")),user);
        boolean patient=exists("SELECT count(*) FROM paciente WHERE usuario_id=?",user);
        boolean deliverer=exists("SELECT count(*) FROM entregador WHERE usuario_id=?",user);
        boolean family=exists("SELECT count(*) FROM autorizacao_paciente WHERE familiar_id=? AND revogada_em IS NULL AND expira_em>clock_timestamp()",user);
        Set<String> areas=new LinkedHashSet<>(List.of("INICIO","CONTA","PRIVACIDADE"));
        if (patient) areas.add("PACIENTE");
        if (family) areas.add("FAMILIAR");
        if (deliverer) areas.add("ENTREGADOR");
        if (global.contains("ANALISTA_OPERACIONAL")) areas.add("OPERACAO");
        if (institutions.stream().anyMatch(i->"ANALISTA_BENEFICIO".equals(i.role()))) areas.add("ANALISE_BENEFICIO");
        if (global.contains("GESTOR_FINANCEIRO")&&institutions.stream().anyMatch(i->"GESTOR_FINANCEIRO".equals(i.role()))) areas.add("FINANCEIRO");
        if (global.contains("OPERADOR_PRIVACIDADE")) areas.add("OPERACAO_PRIVACIDADE");
        return new CapabilitiesView(global,institutions,List.copyOf(areas),patient,deliverer,family);
    }

    private boolean exists(String sql,UUID user){return jdbc.queryForObject(sql,Long.class,user)>0;}
}

record InstitutionCapability(UUID institutionId,String institutionName,String institutionStatus,String role) {}
record CapabilitiesView(List<String> globalRoles,List<InstitutionCapability> institutions,List<String> areas,boolean patientProfile,boolean delivererProfile,boolean activeFamilyAuthorization) {}
