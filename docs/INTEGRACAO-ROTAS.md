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

O provedor de rota não é geocodificador. O contrato atual exige que cada
endereço tenha latitude/longitude confirmadas antes do pedido; sem elas,
o backend retorna `INVALID_ADDRESS` e não chama o provedor. Não foi
adotada geocodificação automática porque converter um endereço protegido
em coordenada sem confirmação criaria risco de localização incorreta e
enviaria dados pessoais a terceiro. Uma futura busca de endereço deverá
ser uma ação explícita, com candidatos confirmáveis, limites e política
de privacidade próprios; não será autocomplete a cada tecla.
Se essa capacidade for autorizada, a alternativa coerente é o mesmo
serviço Pelias de geocodificação do openrouteservice (`/pelias/v1/search`),
com busca explícita, poucos candidatos, confirmação humana e a mesma chave
privada. Ela não foi habilitada nesta etapa porque ainda não existe a tela
de confirmação nem uma decisão de retenção/uso de resultados.

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
```

O padrão permanece indisponível até o responsável configurar a chave.
O script local informa separadamente se o adaptador está configurado;
não consulta o provedor automaticamente na inicialização. Acessibilidade
do provedor e uma consulta homologatória exigem ensaio explícito com
coordenadas públicas, sem endereços do banco normal.
