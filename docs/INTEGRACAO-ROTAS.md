# Integração de rotas

## Escolha

O adaptador configurado usa o openrouteservice hospedado pela HeiGIT,
com o perfil `driving-car`. A documentação oficial descreve o endpoint
de Directions, autenticação por `Authorization` e os campos de resumo de
distância/duração: <https://openrouteservice.org/dev/>.

O plano Standard é gratuito, mas exige uma chave criada pelo responsável
em uma conta externa. Os limites publicados são 2.000 consultas de
Directions por dia e 40 por minuto; para condução, a distância máxima
publicada é 6.000 km: <https://openrouteservice.org/plans/> e
<https://openrouteservice.org/restrictions/>. O endpoint configurado usa
`api.heigit.org`, pois a documentação oficial informa a migração do host
antigo `api.openrouteservice.org`: <https://ask.openrouteservice.org/t/deprecating-api-openrouteservice-org-in-favour-of-api-heigit-org/7912>.

Isso atende ao ensaio local e portfólio sem cartão ou contratação, mas
não é uma garantia de disponibilidade. Os dados derivam de OpenStreetMap;
uso e atribuição devem continuar conforme os termos do provedor. Não foi
criada conta nem solicitada chave em nome do autor.

## Dados enviados e persistidos

O backend envia somente duas coordenadas já confirmadas, no formato
`[longitude, latitude]`, para o endpoint configurado. Não envia e-mail,
nome, paciente, documento ou endereço textual. A requisição usa
`instructions=false` e `geometry=false`; o orçamento persiste apenas
provedor, referência hash, distância em metros, duração em segundos,
instante de cálculo e `transito_incluido=false`.

O provedor de rota não é geocodificador. A tela agora faz uma busca
explícita no Pelias do mesmo provedor (`/pelias/v1/search`), sem
autocomplete: envia rua, número, bairro, cidade, UF e CEP, apresenta até
cinco candidatos e exige confirmação humana. O complemento e a referência
ficam fora da busca. O backend cifra um token temporário vinculado à busca,
coordenadas, provedor e referência; ao criar ou alterar o pedido ele
valida esse token e persiste apenas os metadados da confirmação junto ao
endereço protegido. Alterar qualquer campo pesquisável invalida a seleção.
Sem confirmação, o pedido/orçamento permanece bloqueado. A estimativa não
é comprovação de residência. A interface oferece a alternativa textual
acessível; mapa não foi incluído, pois não é necessário para confirmar e
introduziria uma dependência separada de biblioteca/tiles e seus termos.

O plano Standard do provedor publica 1.000 consultas de Geocoding por dia
e 100 por minuto, além dos limites de Directions; a busca só ocorre por
ação do usuário e a resposta é limitada a cinco candidatos. O endereço
textual informado pode ser enviado ao provedor de geocodificação, nunca é
registrado em logs, e não são enviados dados de paciente/documentos.

## Guardas do adaptador

Chamadas ocorrem exclusivamente no Spring. A chave e o endpoint ficam em
configuração de servidor. Há timeout de 3 segundos, limite de 64 KiB,
uma repetição no máximo somente para 429/502/503/504, validação de
coordenadas, distância máxima de 6.000 km, duração positiva e resposta
JSON com rota. Timeout, limite, resposta inválida, rota inexistente e
indisponibilidade impedem a cotação; não há linha reta de fallback.

A duração é estimada pela rede viária e não é apresentada como trânsito
ao vivo nem como horário garantido. A tarifa continua sendo a política
vigente local; o provedor não calcula o preço.

## Configuração e verificação

Variáveis privadas opcionais:

```text
EXAME_PERTO_ROUTE_ENABLED=false
EXAME_PERTO_ROUTE_API_KEY=
EXAME_PERTO_ROUTE_ENDPOINT=https://api.heigit.org/openrouteservice/v2/directions/driving-car
EXAME_PERTO_ROUTE_TIMEOUT=PT3S
EXAME_PERTO_ROUTE_MAX_RESPONSE_BYTES=65536
EXAME_PERTO_ROUTE_MAX_RETRIES=1
EXAME_PERTO_GEOCODE_ENABLED=false
EXAME_PERTO_GEOCODE_API_KEY=
EXAME_PERTO_GEOCODE_ENDPOINT=https://api.heigit.org/pelias/v1/search
EXAME_PERTO_GEOCODE_TIMEOUT=PT3S
EXAME_PERTO_GEOCODE_MAX_RESPONSE_BYTES=65536
```

O padrão permanece indisponível até o responsável configurar a chave.
O script local informa separadamente se o adaptador está configurado;
não consulta o provedor automaticamente na inicialização. Acessibilidade
do provedor e uma consulta homologatória exigem ensaio explícito com
coordenadas públicas, sem endereços do banco normal.
