# 03: Cadastrar, autenticar e recuperar conta

**What to build:** Permitir que uma pessoa use a web para criar conta real, confirmar e-mail, entrar, sair e recuperar acesso.

**Blocked by:** 03A — Cadastro local; 02 apenas para e-mail, privacidade e procedimentos de conta usados nesta fatia

**Status:** ready-for-human — implementação local validada; homologação de e-mail pendente

- [x] Fatias incluem persistência/migração, REST e tela; fixar versões da stack e ambiente local reproduzível neste primeiro fluxo.
- [x] Cadastro não aceita promoção de papel; e-mail/provedor ausente retorna indisponível e UI explica o bloqueio.
- [x] Rotação/reutilização de refresh, logout e recuperação revogam sessões conforme contrato; CSRF/Origin e rate limit verificados pela interface pública.
- [x] Dados sintéticos só em testes isolados; reinício preserva conta no PostgreSQL isolado autorizado, sem seeds predefinidos.

Implementação autorizada pelo autor sobre `604487b9040d78aff89a1e68c2edc6f079a39a89`; trabalho local preexistente retomado. Base de revisão documental: `4ff66bf2332de25e48aa2bc884825b12a8b35f11`.

## Ajuste D01–D12 — 30/09/2026

D10–D12: cadastro e segurança independem de programa subsidiado. 03A cobre primeiro o bloqueio verificável; este ticket completa persistência de conta, confirmação por e-mail real, login/logout/recuperação e UI. Login básico não libera privilégios sem MFA. Habilitação de dados reais exige os itens pertinentes de 02; não exige concluir contratos de rotas/pagamentos.

- [x] Verificar os comportamentos e bloqueios acima nas interfaces públicas da fatia, conforme [decisões](../../../docs/DECISOES-PENDENTES.md).

## Comments — implementação 03

Implementados conta pendente, desafios de confirmação/reenvio, login, access JWT, refresh rotativo, logout, recuperação e telas. Mantidos bloqueios de política/integração e rejeição de privilégios de 03A. Sem concessão de identidade/elegibilidade/entregador verificados. Contrato, modelo e diagrama atualizados juntos.

Evidências e resultados finais: [STATUS](../../../docs/STATUS.md). Configuração e limites: [conta e e-mail](../../../docs/CONTA-EMAIL.md). Provedor não configurado no ambiente recebido; SMTP implementado, mas entrega real ainda não homologada. Ensaio real depende de configuração privada, endereço controlado pelo autor e autorização de envio. Não iniciar o ticket 04 nem deploy.
