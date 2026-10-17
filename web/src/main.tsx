import { useEffect, useRef, useState } from "react";
import type { ChangeEvent } from "react";
import type { FormEvent, KeyboardEvent, MouseEvent, ReactNode } from "react";
import { createRoot } from "react-dom/client";
import { parseApiResponse as parse, uploadWithProgress } from "./api";
import {
  appPath,
  appRouteFromPath,
  appRouteLabels as routeLabels,
  authPath,
  authRouteFromPath,
  isLegacyAppPath,
  isKnownPublicPath,
  navigate,
} from "./routing";
import type { AppRoute, AuthRoute as Mode } from "./routing";
import "./styles.css";

type Tokens = { accessToken: string; expiresIn: number };
type Notice = { tone: "error" | "info"; text: string };
type Profile = {
  id: string;
  email: string;
  name: string;
  phone: string | null;
  status: string;
  emailVerifiedAt: string | null;
  version: number;
};
type PrivacyRequest = {
  id: string;
  protocol: string;
  type: "ACESSO" | "CORRECAO" | "EXCLUSAO";
  status: string;
  createdAt: string;
  response: string | null;
  purgeStatus: string | null;
  executedAt: string | null;
  verifiedAt: string | null;
  procedurePending: number;
  preserved: number;
  version: number;
  closureStatus: string;
  anonymizationStatus: string;
  responsibleAssigned: boolean;
  respondedAt: string | null;
};
type Patient = { id: string; identityStatus: string; version: number };
type Invitation = {
  id: string;
  patientId: string;
  status: string;
  scopes: string[];
  expiresAt: string;
  version: number;
};
type Grant = {
  id: string;
  patientId: string;
  familyUserId: string;
  scopes: string[];
  expiresAt: string;
  revokedAt: string | null;
  version: number;
};
type Deliverer = { id: string; birthDate: string; status: string; version: number };
type Evidence = {
  id: string;
  category: string;
  mime: string;
  size: number;
  status: string;
  version: number;
  reason?: string | null;
  antimalwareStatus?: string;
};
type VehicleEvidence = Evidence & { purpose: string; current: boolean };
type VehicleLink = {
  id: string;
  plate: string;
  make: string;
  model: string;
  color: string;
  manufacturingYear: number;
  modelYear: number;
  linkType: string;
  validUntil: string | null;
  status: string;
  version: number;
  evidenceComplete: boolean;
  documents: VehicleEvidence[];
};
type VehicleReview = {
  id: string;
  linkId: string;
  linkVersion: number;
  analystId: string | null;
  status: string;
  documents: VehicleEvidence[];
};
type BenefitRequest = {
  id: string;
  patientId: string;
  institutionId: string;
  policyId: string;
  status: string;
  policyVersion: number;
  dimensions: string[];
  decision: string | null;
  percentage: number | null;
  reason: string | null;
  version: number;
  evidence: BenefitEvidence[];
  reviews: BenefitReview[];
  policyCriteria: Record<string, unknown>;
  policyMaximumPercentage: number;
  pendingDimensions: string[];
};
type BenefitPolicyOption = {
  id: string;
  institutionId: string;
  institutionName: string;
  code: string;
  version: number;
  criteria: string;
  maximumPercentage: number;
};
type BenefitProgramOption = {
  id: string;
  institutionId: string;
  institutionName: string;
  code: string;
  name: string;
  benefitRequestId: string;
  percentage: number;
};
type Funding = {
  id: string;
  programId: string;
  amount: number;
  currency: string;
  status: string;
  evidenceDocumentId: string;
  reconciliationEvidenceId: string | null;
  version: number;
};
type Order = {
  id: string;
  patientId: string;
  status: string;
  coverageStatus: string;
  createdAt: string;
  version: number;
  pickupAuthorizationId: string;
  pickupUnitId: string;
};
type FinancialOperation = { id: string; orderId: string; type: string; amount: number; currency: string; status: string; version: number };
type GeocodeCandidate = { label: string; latitude: number; longitude: number; provider: string; reference: string; confirmationToken: string };
type AvailableUnit = { id: string; label: string };
type CatalogProtocol = {
  id: string;
  number: number;
  rules: string;
  returnCoverageConfirmed: boolean;
  startsAt: string;
  endsAt: string | null;
  status: string;
};
type CatalogUnit = {
  id: string;
  name: string;
  city: string;
  state: string;
  registrationStatus: string;
  partnership: {
    status: string;
    evidenceReference: string;
    note: string | null;
    acceptedAt: string | null;
  } | null;
  protocols: CatalogProtocol[];
  pendingRequirements: string[];
  version: number;
};
type Quote = {
  id: string;
  orderId: string;
  status: string;
  grossAmount: number;
  patientAmount: number;
  subsidyAmount: number;
  programId: string | null;
  cancellationPolicyId: string | null;
  tariffVersion: number;
  routeProvider: string;
  routeReference: string;
  distanceMeters: number;
  durationSeconds: number;
  trafficIncluded: boolean;
  calculatedAt: string;
  expiresAt: string;
  version: number;
};
type Assignment = {
  id: string;
  orderId: string;
  status: string;
  acceptedAt: string;
  version: number;
};
type Custody = {
  id: string;
  orderId: string;
  status: string;
  destinationType?: string;
  pickedUpAt: string;
  closedAt?: string;
  version: number;
};
type ReceiptChallenge = { code: string; expiresAt: string };
type LiveLocation = {
  assignmentId: string;
  sequence: number;
  capturedAt: string;
  receivedAt: string;
  latitude: number;
  longitude: number;
  accuracyMeters: number;
  stale: boolean;
  lowAccuracy: boolean;
};
type Incident = {
  id: string;
  orderId: string;
  type: string;
  status: string;
  description?: string;
  createdAt: string;
};
type BrowserTrackingState = {
  taskId: string | null;
  status: "idle" | "starting" | "active" | "stopped" | "denied" | "error";
  message: string;
  pending: number;
  lastPosition: {
    capturedAt: string;
    accuracyMeters: number;
    stale: boolean;
    lowAccuracy: boolean;
  } | null;
};

const orderStatusLabels: Record<string, string> = {
  EM_VERIFICACAO: "Em verificação",
  AGUARDANDO_ACEITE: "Aguardando sua decisão",
  DISPONIVEL: "Pronto para seguir",
  ACEITA: "Aceito",
  RETIRADA: "Retirado",
  EM_ENTREGA: "Em entrega",
  OCORRENCIA: "Atenção necessária",
  CANCELADA: "Cancelado",
};

function orderStatusLabel(value: string) {
  return (
    orderStatusLabels[value] ??
    value.replaceAll("_", " ").toLocaleLowerCase("pt-BR")
  );
}

function formatMoney(value: number) {
  return value.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
}
type OperationalIdentity = {
  displayName: string;
  plate: string;
  model: string;
  color: string;
  photoDocumentId: string;
};
type Payout = {
  id: string;
  orderId: string;
  amount: number;
  currency: string;
  status: string;
  availability: string;
  divergenceCode: string | null;
  requestedAt: string | null;
  confirmedAt: string | null;
  version: number;
};
type PayoutPage = {
  items: Payout[];
  obligations: number;
  divergences: number;
  uncertain: number;
};
type FinancialProgram = {
  id: string;
  institutionId: string;
  institutionName: string;
  code: string;
  name: string;
  status: string;
};
type FinancialHistory = { event: string; status: string; detail: string; occurredAt: string };
type Capabilities = {
  globalRoles: string[];
  institutions: {
    institutionId: string;
    institutionName: string;
    institutionStatus: string;
    role: string;
  }[];
  areas: string[];
  patientProfile: boolean;
  delivererProfile: boolean;
  activeFamilyAuthorization: boolean;
};
type Offer = {
  orderId: string;
  originCity: string;
  destinationCity: string;
  distanceMeters: number;
  grossAmount: number;
  currency: string;
  orderVersion: number;
  cancellationPolicyId: string;
};
type TaskAssignment = Assignment & {
  driverId: string;
  vehicleLinkId: string;
  closedAt: string | null;
};
type TaskAddress = {
  street: string;
  number: string;
  district: string;
  city: string;
  state: string;
  postalCode: string;
  latitude: number;
  longitude: number;
};
type DelivererReview = {
  id: string;
  delivererId: string;
  documentId: string;
  analystId: string | null;
  status: string;
  decision: string | null;
  reason: string | null;
  category: string | null;
  mime: string | null;
  size: number;
  documentStatus: string | null;
  createdAt: string;
};
type BenefitReview = {
  id: string;
  requestId: string;
  type: string;
  requestVersion: number;
  analystId: string | null;
  status: string;
  decision: string | null;
  percentage: number | null;
  reason: string | null;
  createdAt: string;
  assignedAt: string | null;
  decidedAt: string | null;
};
type BenefitEvidence = {
  id: string;
  purpose: string;
  mime: string;
  size: number;
  status: string;
  current: boolean;
};
const scopes = ["PEDIDOS", "BENEFICIOS", "RASTREAMENTO", "RECEBIMENTO"];
const labels: Record<Mode, string> = {
  register: "Solicitar cadastro",
  verification: "Confirmar e-mail",
  "verification/resend": "Reenviar confirmação",
  login: "Entrar",
  recovery: "Recuperar acesso",
  "recovery/complete": "Trocar senha",
};

function PublicHome() {
  const startDelivererRegistration = (event: MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    window.sessionStorage.setItem("exameperto-registration-intent", "entregador");
    navigate("/cadastro");
  };
  return (
    <main className="public-home">
      <header className="public-nav">
        <a
          className="public-brand"
          href="/"
          onClick={(event) => {
            event.preventDefault();
            navigate("/");
          }}
        >
          <span className="brand-mark">EP</span>
          <span>exameperto</span>
        </a>
        <nav aria-label="Acesso público">
          <a href="/acesso">Entrar</a>
          <a className="nav-quiet" href="/cadastro" onClick={startDelivererRegistration}>
            Sou entregador
          </a>
          <a className="nav-cta" href="/cadastro">
            Criar conta
          </a>
        </nav>
      </header>
      <section className="public-hero" aria-labelledby="public-title">
        <div className="hero-copy">
          <p className="eyebrow public-eyebrow">
            RETIRADA AUTORIZADA · ENTREGA CUIDADOSA
          </p>
          <h1 id="public-title">
            Resultados de exames, <em>perto de você.</em>
          </h1>
          <p className="hero-lede">
            O Exameperto ajuda você a solicitar a retirada autorizada e receber
            seus resultados em envelope fechado, com acompanhamento em cada
            etapa.
          </p>
          <div className="hero-actions">
            <a className="button-link primary" href="/cadastro">
              Criar minha conta <span aria-hidden="true">↗</span>
            </a>
            <a className="button-link secondary" href="/acesso">
              Já tenho acesso
            </a>
          </div>
          <p className="project-note">
            <span className="note-dot" aria-hidden="true" /> Projeto em
            construção: a disponibilidade depende de unidades, políticas e
            integrações confirmadas.
          </p>
        </div>
        <div className="hero-visual" aria-label="Resumo das etapas do serviço">
          <div className="visual-orbit orbit-one" />
          <div className="visual-orbit orbit-two" />
          <div className="route-card route-card-top">
            <span>01</span>
            <strong>Pedido criado</strong>
            <small>Você acompanha o próximo passo</small>
          </div>
          <div className="route-card route-card-main">
            <span className="route-icon">✦</span>
            <strong>Envelope fechado</strong>
            <small>Privacidade desde a retirada até o recebimento</small>
            <div className="route-line">
              <i />
              <i />
              <i />
            </div>
          </div>
          <div className="route-card route-card-bottom">
            <span>03</span>
            <strong>Recebimento confirmado</strong>
            <small>Sem expor o conteúdo do exame</small>
          </div>
        </div>
      </section>
      <section className="how-it-works" aria-labelledby="how-title">
        <div className="section-intro">
          <p className="eyebrow dark">COMO FUNCIONA</p>
          <h2 id="how-title">Uma jornada clara, do pedido à porta.</h2>
        </div>
        <ol className="how-grid">
          <li>
            <span>01</span>
            <h3>Solicite</h3>
            <p>
              Informe a unidade de origem, o destinatário autorizado e o
              endereço de recebimento.
            </p>
          </li>
          <li>
            <span>02</span>
            <h3>Retirada autorizada</h3>
            <p>
              A operação só avança quando as autorizações e condições
              necessárias forem verificadas.
            </p>
          </li>
          <li>
            <span>03</span>
            <h3>Receba</h3>
            <p>
              Acompanhe o pedido e confirme a entrega do envelope no destino
              indicado.
            </p>
          </li>
        </ol>
      </section>
      <section className="public-footer-cta">
        <div>
          <p className="eyebrow dark">PARA QUEM ENTREGA</p>
          <h2>Quer fazer parte da operação?</h2>
          <p>
            O cadastro de entregador é separado e passa por análise de
            documentos, veículo e regras aplicáveis.
          </p>
        </div>
        <a className="button-link dark-button" href="/cadastro">
          Conhecer o cadastro <span aria-hidden="true">↗</span>
        </a>
      </section>
      <footer className="public-footer">
        <span>exameperto</span>
        <span>Resultados com cuidado, acesso com clareza.</span>
      </footer>
    </main>
  );
}

function ScopeFields() {
  return (
    <fieldset>
      <legend>Escopos autorizados</legend>
      <div className="scope-grid">
        {scopes.map((scope) => (
          <label className="check" key={scope}>
            <input type="checkbox" name="scopes" value={scope} />{" "}
            <span>{scope.toLocaleLowerCase("pt-BR")}</span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}

function ProfilePanel({ session }: { session: Tokens }) {
  const [profile, setProfile] = useState<Profile | null>(null);
  const [patient, setPatient] = useState<Patient | null | undefined>(undefined);
  const [cpf, setCpf] = useState("");
  const [birthDate, setBirthDate] = useState("");
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);

  async function api(init: RequestInit = {}) {
    return parse(
      await fetch("/api/v1/me/profile", {
        ...init,
        credentials: "same-origin",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${session.accessToken}`,
          ...(init.headers ?? {}),
        },
      }),
    );
  }
  async function load() {
    const [{ result }, patientResponse] = await Promise.all([
      api(),
      fetch("/api/v1/me/patient", { headers: { Authorization: `Bearer ${session.accessToken}` } }),
    ]);
    const value = result as Profile;
    setProfile(value);
    setName(value.name);
    setPhone(value.phone ?? "");
    if (patientResponse.ok) setPatient((await patientResponse.json()) as Patient);
    else if (patientResponse.status === 404) setPatient(null);
    else await parse(patientResponse);
  }
  async function createPatient(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setNotice(null);
    try {
      const { result } = await parse(await fetch("/api/v1/me/patient", {
        method: "POST",
        credentials: "same-origin",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${session.accessToken}`,
          Origin: window.location.origin,
        },
        body: JSON.stringify({ cpf, birthDate }),
      }));
      setPatient(result as Patient);
      setCpf("");
      setBirthDate("");
      setNotice({ tone: "info", text: "Perfil de paciente criado. A identidade permanece pendente até verificação autorizada." });
    } catch (error) {
      setNotice({ tone: "error", text: error instanceof Error ? error.message : "Não foi possível criar o perfil de paciente." });
    } finally {
      setBusy(false);
    }
  }
  useEffect(() => {
    load().catch((error) =>
      setNotice({
        tone: "error",
        text: error instanceof Error ? error.message : "Perfil indisponível.",
      }),
    );
  }, [session.accessToken]);
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!profile || busy) return;
    setBusy(true);
    setNotice(null);
    try {
      const { result } = await api({
        method: "PUT",
        headers: { "If-Match": `"${profile.version}"` },
        body: JSON.stringify({ name, phone: phone || null }),
      });
      const value = result as Profile;
      setProfile(value);
      setName(value.name);
      setPhone(value.phone ?? "");
      setNotice({
        tone: "info",
        text: "Perfil atualizado. O e-mail permanece confirmado e não é alterado por este formulário.",
      });
    } catch (error) {
      setNotice({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "Não foi possível atualizar o perfil.",
      });
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="family profile-panel" aria-labelledby="profile-title">
      <div className="section-heading">
        <span className="eyebrow dark">CONTA BÁSICA</span>
        <h2 id="profile-title">Seu perfil</h2>
        <p>
          Consulte e edite somente nome e telefone. E-mail e estado da conta são
          exibidos como informação protegida.
        </p>
      </div>
      {profile ? (
        <div className="work-grid">
          <form className="work-card" onSubmit={save}>
            <h3>Dados permitidos</h3>
            <label htmlFor="profile-name">Nome</label>
            <input
              id="profile-name"
              value={name}
              onChange={(event) => setName(event.target.value)}
              maxLength={160}
              required
            />
            <label htmlFor="profile-phone">Telefone (opcional)</label>
            <input
              id="profile-phone"
              value={phone}
              onChange={(event) => setPhone(event.target.value)}
              maxLength={40}
              inputMode="tel"
            />
            <button disabled={busy}>
              {busy ? "Salvando…" : "Salvar alterações"}
            </button>
          </form>
          <div className="work-card profile-facts">
            <h3>Estado da conta</h3>
            <p>
              <strong>E-mail</strong>
              <span>{profile.email}</span>
            </p>
            <p>
              <strong>Estado</strong>
              <span>{profile.status}</span>
            </p>
            <p>
              <strong>Confirmação</strong>
              <span>{profile.emailVerifiedAt ? "Confirmado" : "Pendente"}</span>
            </p>
            <p className="quiet-note">
              O encerramento revoga a sessão e transforma campos controlados.
              Isso não é anonimização integral.
            </p>
          </div>
          {patient === null && (
            <form className="work-card" onSubmit={createPatient}>
              <h3>Começar como paciente</h3>
              <p>Esta é uma adesão explícita; o cadastro não concede verificação de identidade nem habilitação de outros papéis.</p>
              <label htmlFor="patient-cpf">CPF</label>
              <input id="patient-cpf" value={cpf} onChange={(event) => setCpf(event.target.value)} inputMode="numeric" required />
              <label htmlFor="patient-birth-date">Data de nascimento</label>
              <input id="patient-birth-date" type="date" value={birthDate} onChange={(event) => setBirthDate(event.target.value)} required />
              <button disabled={busy}>Criar perfil de paciente</button>
            </form>
          )}
          {patient && <p className="feedback info">Perfil de paciente ativo para esta conta. Estado de identidade: {patient.identityStatus}.</p>}
        </div>
      ) : (
        <p className="feedback info" role="status">
          Carregando perfil…
        </p>
      )}
      {notice && (
        <p className={`feedback ${notice.tone}`} role="alert">
          {notice.text}
        </p>
      )}
    </section>
  );
}

function PrivacyPanel({ session }: { session: Tokens }) {
  const [requests, setRequests] = useState<PrivacyRequest[]>([]);
  const [type, setType] = useState<PrivacyRequest["type"]>("ACESSO");
  const [description, setDescription] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);
  async function api(path: string, init: RequestInit = {}) {
    return parse(
      await fetch(`/api/v1${path}`, {
        ...init,
        credentials: "same-origin",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${session.accessToken}`,
          ...(init.headers ?? {}),
        },
      }),
    );
  }
  async function load() {
    const { result } = await api("/me/privacy-requests");
    setRequests(result as PrivacyRequest[]);
  }
  useEffect(() => {
    load().catch((error) =>
      setNotice({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "Solicitações indisponíveis.",
      }),
    );
  }, [session.accessToken]);
  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || !description.trim()) return;
    setBusy(true);
    setNotice(null);
    try {
      await api("/me/privacy-requests", {
        method: "POST",
        headers: { "Idempotency-Key": crypto.randomUUID() },
        body: JSON.stringify({ type, description: description.trim() }),
      });
      setDescription("");
      await load();
      setNotice({
        tone: "info",
        text:
          type === "EXCLUSAO"
            ? "Solicitação de encerramento registrada. A execução depende de análise, política e verificação; esta tela não apaga conteúdo imediatamente."
            : "Solicitação registrada. O protocolo acompanha a análise e não confirma execução.",
      });
    } catch (error) {
      setNotice({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "Não foi possível registrar a solicitação.",
      });
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="family privacy-panel" aria-labelledby="privacy-title">
      <div className="section-heading">
        <span className="eyebrow dark">PRIVACIDADE</span>
        <h2 id="privacy-title">Solicitações e encerramento</h2>
        <p>
          Registre uma demanda e acompanhe o protocolo. Exclusão de conteúdo,
          conservação obrigatória e transformação de conta são estados
          diferentes.
        </p>
      </div>
      <div className="work-grid">
        <form className="work-card" onSubmit={create}>
          <h3>Nova solicitação</h3>
          <label htmlFor="privacy-type">Tipo</label>
          <select
            id="privacy-type"
            value={type}
            onChange={(event) =>
              setType(event.target.value as PrivacyRequest["type"])
            }
          >
            <option value="ACESSO">Consultar meus dados</option>
            <option value="CORRECAO">Corrigir meus dados</option>
            <option value="EXCLUSAO">
              Solicitar encerramento e exclusão conforme política
            </option>
          </select>
          <label htmlFor="privacy-description">Descrição</label>
          <textarea
            id="privacy-description"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            maxLength={4000}
            required
            rows={5}
          />
          <button disabled={busy}>
            {busy ? "Registrando…" : "Registrar solicitação"}
          </button>
        </form>
        <div className="work-card residual-note">
          <h3>Se pedir encerramento</h3>
          <p>
            <strong>TRANSFORMADA_NAO_INTEGRAL</strong>
          </p>
          <p>
            O conteúdo abrangido pela política pode ser expurgado. UUIDs,
            protocolos, auditoria, diário de expurgos, invariantes financeiras,
            deduplicação, backups e obrigações podem permanecer com acesso
            restrito.
          </p>
          <p>
            Custódia aberta, obrigação financeira, repasse incerto ou reserva
            ativa bloqueiam a conclusão sem apagar responsabilidades.
          </p>
          <p className="unavailable">
            Entrega, benefícios, rastreamento e pagamentos continuam
            indisponíveis.
          </p>
        </div>
      </div>
      <div className="registers" aria-live="polite">
        <article>
          <h3>Meus protocolos</h3>
          {requests.length ? (
            requests.map((item) => (
              <div className="privacy-record" key={item.id}>
                <p>
                  <code>{item.protocol}</code>
                  <strong>{item.status}</strong>
                  <span>{item.type}</span>
                </p>
                <dl>
                  <div>
                    <dt>Expurgo</dt>
                    <dd>{item.purgeStatus ?? "NÃO APLICÁVEL"}</dd>
                  </div>
                  <div>
                    <dt>Encerramento</dt>
                    <dd>{item.closureStatus}</dd>
                  </div>
                  <div>
                    <dt>Transformação</dt>
                    <dd>{item.anonymizationStatus}</dd>
                  </div>
                  <div>
                    <dt>Ressalvas</dt>
                    <dd>
                      {item.procedurePending} pendentes · {item.preserved}{" "}
                      preservados
                    </dd>
                  </div>
                </dl>
                {item.response && (
                  <p className="record-response">Resposta: {item.response}</p>
                )}
              </div>
            ))
          ) : (
            <p className="empty">Nenhuma solicitação registrada.</p>
          )}
        </article>
      </div>
      {notice && (
        <p className={`feedback ${notice.tone}`} role="alert">
          {notice.text}
        </p>
      )}
    </section>
  );
}

function PayoutPanel({ session }: { session: Tokens }) {
  const [data, setData] = useState<PayoutPage | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  async function api(path: string, init: RequestInit = {}) {
    return parse(
      await fetch(`/api/v1${path}`, {
        ...init,
        headers: {
          Authorization: `Bearer ${session.accessToken}`,
          ...(init.headers ?? {}),
        },
      }),
    );
  }
  async function reload() {
    const response = await api("/financial/payouts");
    setData(response.result as PayoutPage);
  }
  useEffect(() => {
    reload().catch((error) =>
      setNotice({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "Não foi possível carregar a conciliação.",
      }),
    );
  }, [session.accessToken]);
  async function act(item: Payout, action: "request" | "reconcile") {
    setBusy(item.id);
    setNotice(null);
    try {
      await api(`/payouts/${item.id}/${action}`, {
        method: "POST",
        headers: { "Idempotency-Key": `${action}-${item.id}` },
      });
      await reload();
      setNotice({
        tone: "info",
        text:
          action === "request"
            ? "Solicitação processada. Confirmação depende da conciliação externa."
            : "A mesma referência foi consultada e conciliada.",
      });
    } catch (error) {
      setNotice({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "O resultado não pôde ser confirmado.",
      });
    } finally {
      setBusy(null);
    }
  }
  return (
    <section className="family ledger" aria-labelledby="payout-title">
      <div className="section-heading">
        <span className="eyebrow dark">REPASSE E CONCILIAÇÃO</span>
        <h2 id="payout-title">Livro de obrigações</h2>
        <p>
          A entrega registra o valor devido. Transferência é um evento separado:
          referências incertas são consultadas antes de qualquer nova tentativa.
        </p>
        <button
          type="button"
          onClick={() => void reload()}
          disabled={busy !== null}
        >
          Atualizar livro
        </button>
      </div>
      {notice && (
        <p className={`feedback ${notice.tone}`} role="alert">
          {notice.text}
        </p>
      )}
      {data && (
        <>
          <div className="ledger-summary" aria-label="Resumo financeiro">
            <article>
              <strong>{data.obligations}</strong>
              <span>obrigações registradas</span>
            </article>
            <article>
              <strong>{data.uncertain}</strong>
              <span>resultados incertos</span>
            </article>
            <article>
              <strong>{data.divergences}</strong>
              <span>divergências</span>
            </article>
          </div>
          <div
            className="ledger-table"
            role="table"
            aria-label="Obrigações e repasses"
          >
            <div className="ledger-row ledger-head" role="row">
              <span>Pedido</span>
              <span>Valor devido</span>
              <span>Estado</span>
              <span>Último marco</span>
              <span>Ação</span>
            </div>
            {data.items.map((item) => (
              <div className="ledger-row" role="row" key={item.id}>
                <code>{item.orderId.slice(0, 8)}</code>
                <strong>
                  {new Intl.NumberFormat("pt-BR", {
                    style: "currency",
                    currency: item.currency,
                  }).format(item.amount)}
                </strong>
                <span className={`status status-${item.status.toLowerCase()}`}>
                  {item.status.replaceAll("_", " ")}
                </span>
                <time>
                  {item.confirmedAt
                    ? `Confirmado ${new Date(item.confirmedAt).toLocaleString("pt-BR")}`
                    : item.requestedAt
                      ? `Solicitado ${new Date(item.requestedAt).toLocaleString("pt-BR")}`
                      : "Ainda não solicitado"}
                </time>
                <div>
                  {item.status === "OBRIGACAO_REGISTRADA" ? (
                    <button
                      disabled={
                        busy === item.id || item.availability === "INDISPONIVEL"
                      }
                      onClick={() => act(item, "request")}
                    >
                      {item.availability === "INDISPONIVEL"
                        ? "Integração indisponível"
                        : "Solicitar repasse"}
                    </button>
                  ) : ["INCERTO", "DIVERGENTE"].includes(item.status) ? (
                    <button
                      disabled={busy === item.id}
                      onClick={() => act(item, "reconcile")}
                    >
                      Conciliar referência
                    </button>
                  ) : (
                    <span>—</span>
                  )}
                </div>
                {item.divergenceCode && (
                  <small className="ledger-alert">
                    Divergência: {item.divergenceCode}
                  </small>
                )}
              </div>
            ))}
          </div>
        </>
      )}
    </section>
  );
}

function FamilyPanel({ session }: { session: Tokens }) {
  const [patient, setPatient] = useState<Patient | null>(null);
  const [invitations, setInvitations] = useState<Invitation[]>([]);
  const [received, setReceived] = useState<Invitation[]>([]);
  const [grants, setGrants] = useState<Grant[]>([]);
  const [familyGrants, setFamilyGrants] = useState<Grant[]>([]);
  const [representedPatient, setRepresentedPatient] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);

  async function api(path: string, init: RequestInit = {}) {
    return parse(
      await fetch(`/api/v1${path}`, {
        ...init,
        credentials: "same-origin",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${session.accessToken}`,
          ...(init.headers ?? {}),
        },
      }),
    );
  }
  async function reload() {
    const own = await fetch("/api/v1/me/patient", {
      headers: { Authorization: `Bearer ${session.accessToken}` },
    });
    if (own.ok) {
      const current = (await own.json()) as Patient;
      setPatient(current);
      const [invitationData, grantData] = await Promise.all([
        api(`/patients/${current.id}/invitations`),
        api(`/patients/${current.id}/grants`),
      ]);
      setInvitations(invitationData.result as Invitation[]);
      setGrants((grantData.result as { items: Grant[] }).items);
    } else if (own.status === 404) {
      setPatient(null);
      setInvitations([]);
      setGrants([]);
    } else await parse(own);
    const [receivedData, familyData] = await Promise.all([
      api("/me/family-invitations"),
      api("/me/family-authorizations"),
    ]);
    setReceived(receivedData.result as Invitation[]);
    const effective = (familyData.result as { items: Grant[] }).items;
    setFamilyGrants(effective);
    setRepresentedPatient(
      (current) => current || effective[0]?.patientId || "",
    );
  }
  useEffect(() => {
    reload().catch((error) =>
      setNotice({ tone: "error", text: error.message }),
    );
  }, [session.accessToken]);

  async function run(action: () => Promise<void>, success: string) {
    if (busy) return;
    setBusy(true);
    setNotice(null);
    try {
      await action();
      await reload();
      setNotice({ tone: "info", text: success });
    } catch (error) {
      setNotice({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "O resultado não pôde ser confirmado.",
      });
    } finally {
      setBusy(false);
    }
  }
  const values = (form: HTMLFormElement) =>
    Object.fromEntries(new FormData(form).entries());
  const selected = (form: HTMLFormElement) =>
    new FormData(form).getAll("scopes");
  async function savePatient(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const fields = values(form);
    await run(
      async () => {
        await api("/me/patient", {
          method: patient ? "PUT" : "POST",
          headers: patient ? { "If-Match": `"${patient.version}"` } : {},
          body: JSON.stringify(fields),
        });
        form.reset();
      },
      patient
        ? "Perfil atualizado e identidade mantida como pendente."
        : "Perfil criado com identidade pendente.",
    );
  }
  async function sendInvite(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    await run(async () => {
      await api(`/patients/${patient!.id}/invitations`, {
        method: "POST",
        body: JSON.stringify({
          recipientEmail: new FormData(form).get("recipientEmail"),
          scopes: selected(form),
        }),
      });
      form.reset();
    }, "Convite registrado e encaminhado pelo canal privado. O aceite ainda não concede acesso.");
  }
  async function acceptInvite(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const fields = values(form);
    const id = String(fields.invitationId);
    await run(async () => {
      const current = await api(`/family-invitations/${id}`);
      await api(`/family-invitations/${id}/accept`, {
        method: "POST",
        headers: { "If-Match": current.etag! },
        body: JSON.stringify({ token: fields.token }),
      });
      form.reset();
    }, "Convite aceito. Aguarde a confirmação reautenticada do paciente; ainda não há acesso.");
  }
  async function confirmGrant(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const local = String(data.get("expiresAt"));
    await run(async () => {
      await api(`/patients/${patient!.id}/grants`, {
        method: "POST",
        body: JSON.stringify({
          invitationId: data.get("invitationId"),
          scopes: data.getAll("scopes"),
          expiresAt: new Date(local).toISOString(),
          password: data.get("password"),
        }),
      });
      form.reset();
    }, "Autorização ativada com os escopos e a expiração confirmados.");
  }
  async function revoke(grant: Grant) {
    await run(async () => {
      await api(`/patients/${grant.patientId}/grants/${grant.id}`, {
        method: "DELETE",
        headers: { "If-Match": `"${grant.version}"` },
      });
    }, "Autorização revogada imediatamente.");
  }

  return (
    <section className="family" aria-labelledby="family-title">
      <div className="section-heading">
        <span className="eyebrow dark">PERFIL E REPRESENTAÇÃO</span>
        <h2 id="family-title">Autorizações sob controle</h2>
        <p>
          Parentesco, idade ou deficiência não concedem acesso. O paciente
          escolhe escopos e prazo; o familiar aceita em sua própria conta.
        </p>
      </div>
      {familyGrants.length > 0 && (
        <aside className="acting-banner" aria-label="Contexto de representação">
          <label>
            Atuando em nome de
            <select
              value={representedPatient}
              onChange={(event) => setRepresentedPatient(event.target.value)}
            >
              {familyGrants.map((grant, index) => (
                <option value={grant.patientId} key={grant.id}>
                  Paciente autorizado {index + 1}
                </option>
              ))}
            </select>
          </label>
          <p>
            Escopos disponíveis:{" "}
            {familyGrants
              .find((grant) => grant.patientId === representedPatient)
              ?.scopes.join(" · ")}
            . A autorização expirada ou revogada desaparece desta seleção e o
            backend revalida cada ação.
          </p>
        </aside>
      )}
      <div className="work-grid">
        <form className="work-card" onSubmit={savePatient}>
          <h3>{patient ? "Atualizar perfil" : "Criar perfil de paciente"}</h3>
          <p>
            CPF e nascimento são protegidos. O perfil permanece com identidade
            pendente.
          </p>
          <label htmlFor="cpf">CPF</label>
          <input
            id="cpf"
            name="cpf"
            inputMode="numeric"
            pattern="[0-9]{11}"
            required
          />
          <label htmlFor="birthDate">Data de nascimento</label>
          <input id="birthDate" name="birthDate" type="date" required />
          <button disabled={busy}>
            {patient ? "Atualizar perfil" : "Criar perfil"}
          </button>
          {patient && (
            <small>
              ID do perfil: <code>{patient.id}</code> · identidade{" "}
              {patient.identityStatus.toLowerCase()}
            </small>
          )}
        </form>
        {patient && (
          <form className="work-card" onSubmit={sendInvite}>
            <h3>Convidar familiar</h3>
            <p>
              O segredo segue apenas pelo canal privado configurado e nunca
              aparece nesta tela.
            </p>
            <label htmlFor="recipientEmail">E-mail do destinatário</label>
            <input
              id="recipientEmail"
              name="recipientEmail"
              type="email"
              required
            />
            <ScopeFields />
            <button disabled={busy}>Enviar convite privado</button>
          </form>
        )}
        <form className="work-card" onSubmit={acceptInvite}>
          <h3>Aceitar convite</h3>
          <p>
            Entre na conta do destinatário e informe os dados recebidos. Aceitar
            não ativa acesso.
          </p>
          <label htmlFor="acceptInvitationId">Identificador do convite</label>
          <input id="acceptInvitationId" name="invitationId" required />
          <label htmlFor="inviteToken">Código de uso único</label>
          <input
            id="inviteToken"
            name="token"
            minLength={43}
            maxLength={43}
            autoComplete="off"
            required
          />
          <button disabled={busy}>Aceitar sem ativar</button>
        </form>
        {patient && (
          <form className="work-card" onSubmit={confirmGrant}>
            <h3>Confirmar autorização</h3>
            <p>
              Repita exatamente os escopos do convite aceito e confirme com sua
              senha.
            </p>
            <label htmlFor="confirmInvitationId">Identificador aceito</label>
            <input id="confirmInvitationId" name="invitationId" required />
            <ScopeFields />
            <label htmlFor="expiresAt">Expira em</label>
            <input
              id="expiresAt"
              name="expiresAt"
              type="datetime-local"
              required
            />
            <label htmlFor="patientPassword">Senha do paciente</label>
            <input
              id="patientPassword"
              name="password"
              type="password"
              autoComplete="current-password"
              required
            />
            <button disabled={busy}>Confirmar e ativar</button>
          </form>
        )}
      </div>
      <div className="registers" aria-live="polite">
        <article>
          <h3>Convites enviados</h3>
          {invitations.length ? (
            invitations.map((item) => (
              <p key={item.id}>
                <code>{item.id}</code>
                <strong>{item.status}</strong>
                <span>{item.scopes.join(" · ")}</span>
              </p>
            ))
          ) : (
            <p className="empty">Nenhum convite neste perfil.</p>
          )}
        </article>
        <article>
          <h3>Convites recebidos</h3>
          {received.length ? (
            received.map((item) => (
              <p key={item.id}>
                <code>{item.id}</code>
                <strong>{item.status}</strong>
                <span>{item.scopes.join(" · ")}</span>
              </p>
            ))
          ) : (
            <p className="empty">Nenhum convite para esta conta.</p>
          )}
        </article>
        <article>
          <h3>Autorizações concedidas</h3>
          {grants.length ? (
            grants.map((item) => (
              <p key={item.id}>
                <code>{item.id}</code>
                <strong>
                  {item.revokedAt
                    ? "REVOGADA"
                    : new Date(item.expiresAt) <= new Date()
                      ? "EXPIRADA"
                      : "VIGENTE"}
                </strong>
                <span>{item.scopes.join(" · ")}</span>
                {!item.revokedAt && (
                  <button
                    type="button"
                    className="danger"
                    disabled={busy}
                    onClick={() => revoke(item)}
                  >
                    Revogar
                  </button>
                )}
              </p>
            ))
          ) : (
            <p className="empty">Nenhuma autorização concedida.</p>
          )}
        </article>
        <article>
          <h3>Acessos como familiar</h3>
          {familyGrants.length ? (
            familyGrants.map((item) => (
              <p key={item.id}>
                <code>{item.patientId}</code>
                <strong>VIGENTE</strong>
                <span>
                  {item.scopes.join(" · ")} · até{" "}
                  {new Date(item.expiresAt).toLocaleString("pt-BR")}
                </span>
              </p>
            ))
          ) : (
            <p className="empty">Nenhum acesso familiar vigente.</p>
          )}
        </article>
      </div>
      {notice && (
        <p className={`feedback ${notice.tone}`} role="alert">
          {notice.text}
        </p>
      )}
    </section>
  );
}

function BenefitPanel({ session }: { session: Tokens }) {
  const [policyId, setPolicyId] = useState("");
  const [policies, setPolicies] = useState<BenefitPolicyOption[]>([]);
  const [patientId, setPatientId] = useState("");
  const [dimensions, setDimensions] = useState<string[]>(["IDADE"]);
  const [requests, setRequests] = useState<BenefitRequest[]>([]);
  const [patientOptions, setPatientOptions] = useState<
    { id: string; label: string }[]
  >([]);
  const [notice, setNotice] = useState<Notice | null>(null);
  async function api(path: string, init: RequestInit = {}) {
    const response = await fetch(path, {
      ...init,
      headers: {
        Authorization: `Bearer ${session.accessToken}`,
        "Content-Type": "application/json",
        ...(init.headers ?? {}),
      },
    });
    return parse(response);
  }
  async function load(showNotice = true) {
    try {
      const [requestsResponse, patient, family] = await Promise.all([
        api("/api/v1/benefit-requests"),
        api("/api/v1/me/patient").catch(() => null),
        api("/api/v1/me/family-authorizations").catch(() => null),
      ]);
      setRequests(requestsResponse.result as BenefitRequest[]);
      const options = [
        ...(patient
          ? [
              {
                id: (patient.result as Patient).id,
                label: "Meu perfil de paciente",
              },
            ]
          : []),
        ...((family?.result as { items: Grant[] } | undefined)?.items ?? [])
          .filter((grant) => grant.scopes.includes("BENEFICIOS"))
          .map((grant, index) => ({
            id: grant.patientId,
            label: `Paciente autorizado ${index + 1}`,
          })),
      ];
      setPatientOptions(options);
      if (!patientId && options[0]) setPatientId(options[0].id);
    } catch (e) {
      if (showNotice) setNotice({ tone: "error", text: (e as Error).message });
    }
  }
  useEffect(() => {
    void load(false);
    api("/api/v1/benefit-policies")
      .then(({ result }) => {
        const availablePolicies = result as BenefitPolicyOption[];
        setPolicies(availablePolicies);
        if (!policyId && availablePolicies[0]) setPolicyId(availablePolicies[0].id);
      })
      .catch((e) => setNotice({ tone: "error", text: (e as Error).message }));
  }, [session.accessToken]);
  const selectedPolicy = policies.find((policy) => policy.id === policyId);
  async function create(event: FormEvent) {
    event.preventDefault();
    setNotice(null);
    try {
      await api("/api/v1/benefit-requests", {
        method: "POST",
        body: JSON.stringify({ patientId, policyId, dimensions }),
      });
      setNotice({
        tone: "info",
        text: "Solicitação registrada para análise humana.",
      });
      await load();
    } catch (e) {
      setNotice({ tone: "error", text: (e as Error).message });
    }
  }
  async function upload(request: BenefitRequest, purpose: string, file: File) {
    setNotice(null);
    try {
      const data = new FormData();
      data.append("version", String(request.version));
      data.append("purpose", purpose);
      data.append("file", file);
      await parse(
        await fetch(`/api/v1/benefit-requests/${request.id}/documents`, {
          method: "POST",
          headers: { Authorization: `Bearer ${session.accessToken}` },
          body: data,
        }),
      );
      await load();
      setNotice({
        tone: "info",
        text: "Evidência recebida em quarentena; inspeção técnica e decisão humana continuam separadas.",
      });
    } catch (e) {
      setNotice({ tone: "error", text: (e as Error).message });
    }
  }
  async function appeal(request: BenefitRequest, reason: string) {
    try {
      await api(`/api/v1/benefit-requests/${request.id}/appeal`, {
        method: "POST",
        body: JSON.stringify({ reason, requestVersion: request.version }),
      });
      await load();
      setNotice({
        tone: "info",
        text: "Recurso aberto. A revisão exige outra pessoa elegível.",
      });
    } catch (e) {
      setNotice({ tone: "error", text: (e as Error).message });
    }
  }
  return (
    <section className="family panel" aria-label="Benefícios">
      <div className="section-heading">
        <span className="eyebrow dark">BENEFÍCIOS</span>
        <h2>Solicitações e recursos</h2>
        <p>
          Dimensões independentes; documentos e critérios permanecem sujeitos à
          política institucional vigente.
        </p>
      </div>
      <form className="work-card staged-form" onSubmit={create}>
        <span className="form-step">ETAPA 1 · QUEM</span>
        <label>
          Paciente representado
          <select
            required
            value={patientId}
            onChange={(e) => setPatientId(e.target.value)}
          >
            <option value="" disabled>
              Selecione um perfil autorizado
            </option>
            {patientOptions.map((option) => (
              <option value={option.id} key={option.id}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
        <p className="acting-context">
          O backend exige titularidade ou concessão BENEFICIOS vigente.
        </p>
        <span className="form-step">ETAPA 2 · POLÍTICA</span>
        <label>
          Política vigente
          <select required value={policyId} onChange={(e) => setPolicyId(e.target.value)}>
            <option value="" disabled>Selecione uma política autorizada</option>
            {policies.map((policy) => (
              <option value={policy.id} key={policy.id}>
                {policy.institutionName} · {policy.code} · versão {policy.version}
              </option>
            ))}
          </select>
        </label>
        {selectedPolicy && (
          <div className="dependency-note" aria-live="polite">
            <strong>Critérios publicados pela política</strong>
            <span>{selectedPolicy.criteria} · limite máximo registrado: {selectedPolicy.maximumPercentage}%.</span>
          </div>
        )}
        <p className="dependency-note">
          O catálogo mostra somente políticas ativas, vigentes e vinculadas a
          instituições habilitadas. Critérios e limite de cálculo são
          apresentados pelo servidor no detalhe da solicitação após o envio.
        </p>
        <span className="form-step">ETAPA 3 · CRITÉRIOS</span>
        <fieldset>
          <legend>Dimensões independentes</legend>
          {["IDADE", "DEFICIENCIA", "RENDA"].map((d) => (
            <label className="check" key={d}>
              <input
                type="checkbox"
                checked={dimensions.includes(d)}
                onChange={(e) =>
                  setDimensions(
                    e.target.checked
                      ? [...dimensions, d]
                      : dimensions.filter((x) => x !== d),
                  )
                }
              />{" "}
              {d}
            </label>
          ))}
        </fieldset>
        <div className="review-summary">
          <strong>Antes de enviar</strong>
          <span>
            {dimensions.length} dimensão(ões) para análise humana. Evidências
            serão anexadas depois, em armazenamento privado.
          </span>
        </div>
        <button
          type="submit"
          disabled={!patientId || !policyId || !dimensions.length}
        >
          Solicitar análise
        </button>
      </form>
      {notice && (
        <p className={`feedback ${notice.tone}`} role="alert">
          {notice.text}
        </p>
      )}
      <div className="registers">
        {requests.length ? (
          requests.map((r) => (
            <article key={r.id}>
              <h3>
                <code>{r.id}</code>
              </h3>
              <p>
                <strong>{r.status}</strong>
                <span>
                  Paciente selecionado · dimensões {r.dimensions.join(", ")} ·
                  política v{r.policyVersion}
                </span>
              </p>
              <p>{r.reason ?? "Aguardando análise humana."}</p>
              {r.status !== "ENCERRADA" &&
                r.dimensions.map((purpose) => (
                  <label key={purpose}>
                    Anexar evidência {purpose.toLocaleLowerCase("pt-BR")}
                    <input
                      type="file"
                      accept="application/pdf,image/png,image/jpeg"
                      onChange={(e) => {
                        const file = e.target.files?.[0];
                        if (file) void upload(r, purpose, file);
                      }}
                    />
                  </label>
                ))}
              {r.evidence?.map((document) => (
                <p key={document.id}>
                  <strong>{document.status}</strong>
                  <span>
                    {document.purpose} ·{" "}
                    {document.current ? "corrente" : "substituída"}
                  </span>
                </p>
              ))}
              {r.status === "DECIDIDA" && (
                <form
                  onSubmit={(e) => {
                    e.preventDefault();
                    void appeal(
                      r,
                      String(new FormData(e.currentTarget).get("reason")),
                    );
                  }}
                >
                  <label>
                    Motivo do recurso
                    <input name="reason" required />
                  </label>
                  <button>Abrir recurso independente</button>
                </form>
              )}
            </article>
          ))
        ) : (
          <article>
            <p className="empty">
              Nenhuma solicitação visível para esta conta ou representação.
            </p>
          </article>
        )}
      </div>
    </section>
  );
}

function FundingPanel({ session }: { session: Tokens }) {
  const [programId, setProgramId] = useState("");
  const [amount, setAmount] = useState("");
  const [sourceReference, setSourceReference] = useState("");
  const [evidenceId, setEvidenceId] = useState("");
  const [reconciliationId, setReconciliationId] = useState("");
  const [reason, setReason] = useState("");
  const [funding, setFunding] = useState<Funding[]>([]);
  const [balance, setBalance] = useState<{ available: number } | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [mfaSecret, setMfaSecret] = useState("");
  async function api(path: string, init: RequestInit = {}) {
    const response = await fetch(path, {
      ...init,
      headers: {
        Authorization: `Bearer ${session.accessToken}`,
        "Content-Type": "application/json",
        ...(init.headers ?? {}),
      },
    });
    return parse(response);
  }
  async function load() {
    if (!programId) return;
    try {
      const [{ result: list }, { result: current }] = await Promise.all([
        api(`/api/v1/programs/${programId}/funding`),
        api(`/api/v1/programs/${programId}/balance`),
      ]);
      setFunding(list);
      setBalance(current);
    } catch (e) {
      setNotice({ tone: "error", text: (e as Error).message });
    }
  }
  async function upload(
    event: FormEvent<HTMLInputElement>,
    reconciliation = false,
  ) {
    const file = event.currentTarget.files?.[0];
    if (!file) return;
    const data = new FormData();
    data.append("file", file);
    try {
      const response = await fetch("/api/v1/me/financial-documents", {
        method: "POST",
        headers: { Authorization: `Bearer ${session.accessToken}` },
        body: data,
      });
      const parsed = await parse(response);
      if (reconciliation) setReconciliationId(parsed.result.id);
      else setEvidenceId(parsed.result.id);
      setNotice({
        tone: "info",
        text: reconciliation
          ? "Conciliação anexada; inspecione o arquivo antes de revisar."
          : "Evidência privada anexada; ainda não comprova transferência.",
      });
    } catch (e) {
      setNotice({ tone: "error", text: (e as Error).message });
    }
  }
  async function record(event: FormEvent) {
    event.preventDefault();
    try {
      await api(`/api/v1/programs/${programId}/funding`, {
        method: "POST",
        headers: { "Idempotency-Key": crypto.randomUUID() },
        body: JSON.stringify({
          amount,
          currency: "BRL",
          evidenceDocumentId: evidenceId,
          sourceReference,
        }),
      });
      setNotice({
        tone: "info",
        text: "Aporte registrado como pendente de conciliação independente.",
      });
      await load();
    } catch (e) {
      setNotice({ tone: "error", text: (e as Error).message });
    }
  }
  async function enroll(event: FormEvent) {
    event.preventDefault();
    try {
      const { result } = await api("/api/v1/me/mfa/totp/enrollment", {
        method: "POST",
        body: JSON.stringify({
          password: new FormData(event.currentTarget as HTMLFormElement).get(
            "password",
          ),
        }),
      });
      setMfaSecret(result.secret);
      setNotice({
        tone: "info",
        text: "Segredo MFA emitido; confirme o código para elevar esta sessão.",
      });
    } catch (e) {
      setNotice({ tone: "error", text: (e as Error).message });
    }
  }
  async function confirmMfa(event: FormEvent) {
    event.preventDefault();
    try {
      await api("/api/v1/me/mfa/totp/confirmation", {
        method: "POST",
        body: JSON.stringify({
          code: new FormData(event.currentTarget as HTMLFormElement).get(
            "code",
          ),
        }),
      });
      setNotice({ tone: "info", text: "MFA confirmado nesta sessão." });
    } catch (e) {
      setNotice({ tone: "error", text: (e as Error).message });
    }
  }
  async function inspect(item: Funding) {
    try {
      await api(
        `/api/v1/funding/${item.id}/review/evidence/${reconciliationId}/inspection`,
        { method: "POST" },
      );
      setNotice({
        tone: "info",
        text: "Evidência de conciliação inspecionada.",
      });
    } catch (e) {
      setNotice({ tone: "error", text: (e as Error).message });
    }
  }
  async function decide(item: Funding, decision: "CONFIRMADO" | "REJEITADO") {
    try {
      await api(`/api/v1/funding/${item.id}/review`, {
        method: "POST",
        headers: {
          "If-Match": String(item.version),
          "Idempotency-Key": crypto.randomUUID(),
        },
        body: JSON.stringify({
          decision,
          reconciliationEvidenceId: reconciliationId,
          reasonCode:
            reason || (decision === "CONFIRMADO" ? "CONCILIADO" : "REJEITADO"),
        }),
      });
      setNotice({
        tone: "info",
        text:
          decision === "CONFIRMADO"
            ? "Aporte confirmado; disponibilidade atualizada."
            : "Aporte rejeitado; disponibilidade inalterada.",
      });
      await load();
    } catch (e) {
      setNotice({ tone: "error", text: (e as Error).message });
    }
  }
  return (
    <section className="family" aria-label="Aportes institucionais">
      <div className="section-heading">
        <span className="eyebrow dark">PAINEL FINANCEIRO</span>
        <h2>Aportes e disponibilidade</h2>
        <p>
          Exige gestor nominal, MFA e evidência de conciliação. Registro
          pendente não aumenta saldo.
        </p>
      </div>
      <div className="work-grid">
        <form className="work-card" onSubmit={enroll}>
          <h3>Configurar MFA</h3>
          <label>
            Senha para MFA
            <input name="password" type="password" required />
          </label>
          <button type="submit">Emitir segredo MFA</button>
          {mfaSecret && (
            <small>
              Segredo temporário: <code>{mfaSecret}</code>
            </small>
          )}
        </form>
        <form className="work-card" onSubmit={confirmMfa}>
          <h3>Elevar sessão</h3>
          <label>
            Código MFA
            <input
              name="code"
              inputMode="numeric"
              pattern="[0-9]{6}"
              required
            />
          </label>
          <button type="submit">Confirmar MFA</button>
        </form>
      </div>
      <form className="work-card" onSubmit={record}>
        <label>
          UUID do programa
          <input
            required
            value={programId}
            onChange={(e) => setProgramId(e.target.value)}
          />
        </label>
        <label>
          Valor em BRL
          <input
            required
            inputMode="decimal"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
          />
        </label>
        <label>
          Referência de origem
          <input
            required
            value={sourceReference}
            onChange={(e) => setSourceReference(e.target.value)}
          />
        </label>
        <label>
          Evidência privada
          <input
            required
            type="file"
            accept="application/pdf,image/png,image/jpeg"
            onChange={(e) => void upload(e)}
          />
        </label>
        <p>
          <code>{evidenceId || "Nenhuma evidência anexada"}</code>
        </p>
        <button type="submit" disabled={!evidenceId}>
          Registrar aporte
        </button>
        <button type="button" onClick={() => void load()}>
          Atualizar situação
        </button>
      </form>
      <div className="work-card">
        <h3>Conciliação do aporte pendente</h3>
        <label>
          Evidência de conciliação
          <input
            type="file"
            accept="application/pdf,image/png,image/jpeg"
            onChange={(e) => void upload(e, true)}
          />
        </label>
        <p>
          <code>{reconciliationId || "Nenhuma conciliação anexada"}</code>
        </p>
        <label>
          Motivo da revisão
          <input value={reason} onChange={(e) => setReason(e.target.value)} />
        </label>
      </div>
      {balance && (
        <p className="feedback info">
          Disponibilidade confirmada: {balance.available} BRL
        </p>
      )}
      {notice && (
        <p className={`feedback ${notice.tone}`} role="alert">
          {notice.text}
        </p>
      )}
      <div className="registers">
        {funding.map((item) => (
          <article key={item.id}>
            <strong>{item.status}</strong>
            <p>
              {item.amount} {item.currency} · versão {item.version}
            </p>
            {item.status === "PENDENTE" && (
              <>
                <button
                  type="button"
                  disabled={!reconciliationId}
                  onClick={() => void inspect(item)}
                >
                  Inspecionar conciliação
                </button>
                <button
                  type="button"
                  disabled={!reconciliationId}
                  onClick={() => void decide(item, "CONFIRMADO")}
                >
                  Confirmar aporte
                </button>
                <button
                  type="button"
                  disabled={!reconciliationId}
                  onClick={() => void decide(item, "REJEITADO")}
                >
                  Rejeitar aporte
                </button>
              </>
            )}
          </article>
        ))}
      </div>
    </section>
  );
}

function FinancialWorkspace({ session, capabilities }: { session: Tokens; capabilities: Capabilities }) {
  const [tab, setTab] = useState("overview");
  const [programs, setPrograms] = useState<FinancialProgram[]>([]);
  const [programId, setProgramId] = useState("");
  const [funding, setFunding] = useState<Funding[]>([]);
  const [balance, setBalance] = useState<{ available: number; reserved: number; settled: number } | null>(null);
  const [payouts, setPayouts] = useState<PayoutPage | null>(null);
  const [selectedFunding, setSelectedFunding] = useState<Funding | null>(null);
  const [selectedPayout, setSelectedPayout] = useState<Payout | null>(null);
  const [history, setHistory] = useState<FinancialHistory[]>([]);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("TODOS");
  const [page, setPage] = useState(0);
  const [amount, setAmount] = useState("");
  const [sourceReference, setSourceReference] = useState("");
  const [evidenceId, setEvidenceId] = useState("");
  const [reconciliationId, setReconciliationId] = useState("");
  const [reason, setReason] = useState("");
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  const institutionNames = capabilities.institutions.filter((item) => item.role === "GESTOR_FINANCEIRO").map((item) => item.institutionName);
  const institutionLabel = institutionNames.length === 1 ? institutionNames[0] : institutionNames.join(" · ");
  async function api(path: string, init: RequestInit = {}) { return parse(await fetch(`/api/v1${path}`, { ...init, headers: { Authorization: `Bearer ${session.accessToken}`, "Content-Type": "application/json", ...(init.headers ?? {}) } })); }
  async function loadPrograms() { try { const result = await api("/financial/programs"); setPrograms(result.result as FinancialProgram[]); } catch (error) { setNotice({ tone: "error", text: error instanceof Error ? error.message : "Não foi possível carregar os programas autorizados." }); } }
  async function loadProgram() { if (!programId) return; try { const [f, b] = await Promise.all([api(`/programs/${programId}/funding`), api(`/programs/${programId}/balance`)]); setFunding(f.result as Funding[]); setBalance(b.result as typeof balance); } catch (error) { setNotice({ tone: "error", text: error instanceof Error ? error.message : "Não foi possível carregar o programa." }); } }
  async function loadPayouts() { try { const result = await api("/financial/payouts"); setPayouts(result.result as PayoutPage); } catch (error) { setNotice({ tone: "error", text: error instanceof Error ? error.message : "Não foi possível carregar as obrigações autorizadas." }); } }
  useEffect(() => {
    void loadPrograms(); void loadPayouts();
    const refreshAfterMfa = () => { void loadPrograms(); void loadPayouts(); if (programId) void loadProgram(); };
    addEventListener("mfa-elevated", refreshAfterMfa);
    return () => removeEventListener("mfa-elevated", refreshAfterMfa);
  }, [session.accessToken]);
  useEffect(() => { void loadProgram(); setSelectedFunding(null); setHistory([]); }, [programId]);
  useEffect(() => { setPage(0); }, [query, statusFilter, tab]);
  async function upload(event: ChangeEvent<HTMLInputElement>, reconciliation = false) { const file = event.currentTarget.files?.[0]; if (!file) return; const data = new FormData(); data.append("file", file); try { const result = await parse(await fetch("/api/v1/me/financial-documents", { method: "POST", headers: { Authorization: `Bearer ${session.accessToken}` }, body: data })); if (reconciliation) setReconciliationId(result.result.id); else setEvidenceId(result.result.id); setNotice({ tone: "info", text: reconciliation ? "Evidência de conciliação anexada. A decisão continua pendente de inspeção." : "Comprovante anexado. O aporte ainda não está confirmado." }); } catch (error) { setNotice({ tone: "error", text: error instanceof Error ? error.message : "A evidência não pôde ser anexada." }); } }
  async function record(event: FormEvent) { event.preventDefault(); setBusy(true); try { await api(`/programs/${programId}/funding`, { method: "POST", headers: { "Idempotency-Key": crypto.randomUUID() }, body: JSON.stringify({ amount, currency: "BRL", evidenceDocumentId: evidenceId, sourceReference }) }); setNotice({ tone: "info", text: "Aporte registrado como pendente. Outro gestor deverá revisar." }); setAmount(""); setSourceReference(""); setEvidenceId(""); await loadProgram(); } catch (error) { setNotice({ tone: "error", text: error instanceof Error ? error.message : "O aporte não pôde ser registrado." }); } finally { setBusy(false); } }
  async function openFunding(item: Funding) { setSelectedFunding(item); setSelectedPayout(null); try { const result = await api(`/funding/${item.id}/history`); setHistory(result.result as FinancialHistory[]); } catch (error) { setNotice({ tone: "error", text: error instanceof Error ? error.message : "Histórico indisponível." }); } }
  async function openPayout(item: Payout) { setSelectedPayout(item); setSelectedFunding(null); try { const result = await api(`/payouts/${item.id}/history`); setHistory(result.result as FinancialHistory[]); } catch (error) { setNotice({ tone: "error", text: error instanceof Error ? error.message : "Histórico indisponível." }); } }
  async function review(decision: "CONFIRMADO" | "REJEITADO") { if (!selectedFunding || !reconciliationId) return; setBusy(true); try { await api(`/funding/${selectedFunding.id}/review/evidence/${reconciliationId}/inspection`, { method: "POST" }); await api(`/funding/${selectedFunding.id}/review`, { method: "POST", headers: { "If-Match": String(selectedFunding.version), "Idempotency-Key": crypto.randomUUID() }, body: JSON.stringify({ decision, reconciliationEvidenceId: reconciliationId, reasonCode: reason || (decision === "CONFIRMADO" ? "CONCILIADO" : "REJEITADO") }) }); setNotice({ tone: "info", text: decision === "CONFIRMADO" ? "Evidência inspecionada e aporte confirmado; saldo disponível atualizado pelo servidor." : "Evidência inspecionada e aporte rejeitado; saldo não foi alterado." }); await loadProgram(); setSelectedFunding(null); setHistory([]); } catch (error) { setNotice({ tone: "error", text: error instanceof Error ? error.message : "A decisão foi recusada." }); } finally { setBusy(false); } }
  async function payoutAction(action: "request" | "reconcile") { if (!selectedPayout) return; setBusy(true); try { await api(`/payouts/${selectedPayout.id}/${action}`, { method: "POST", headers: { "Idempotency-Key": `${action}-${selectedPayout.id}` } }); setNotice({ tone: "info", text: action === "request" ? "Obrigação enviada ao provedor; confirmação ainda depende da conciliação." : "Referência reenviada para conciliação. Isso não presume pagamento." }); await loadPayouts(); const current = (await api(`/payouts/${selectedPayout.id}`)).result as Payout; await openPayout(current); } catch (error) { setNotice({ tone: "error", text: error instanceof Error ? error.message : "A ação foi recusada." }); } finally { setBusy(false); } }
  const selectedProgram = programs.find((item) => item.id === programId);
  const visibleFunding = funding.filter((item) => (statusFilter === "TODOS" || item.status === statusFilter) && `${item.status} ${formatMoney(item.amount)}`.toLocaleLowerCase().includes(query.toLocaleLowerCase())).slice(page * 5, page * 5 + 5);
  const visiblePayouts = (payouts?.items ?? []).filter((item) => (statusFilter === "TODOS" || item.status === statusFilter) && `${item.status} ${item.orderId}`.toLocaleLowerCase().includes(query.toLocaleLowerCase())).slice(page * 5, page * 5 + 5);
  const tabs = [{ id: "overview", label: "Visão geral" }, { id: "programs", label: "Programas e aportes" }, { id: "balance", label: "Saldos e movimentações" }, { id: "obligations", label: "Obrigações e repasses" }, { id: "reconciliation", label: "Conciliação e divergências" }];
  return <section className="family financial-workspace" aria-label="Operação financeira">
    <div className="section-heading"><span className="eyebrow dark">OPERAÇÃO FINANCEIRA</span><h2>Financeiro</h2><p>Recursos com rastreabilidade. Somente registros autorizados para a instituição vinculada à sua conta aparecem nesta área.</p><strong className="institution-context">Instituição em atuação: {institutionLabel || "nenhuma instituição financeira autorizada"}</strong></div>
    <nav className="financial-tabs" aria-label="Navegação financeira">{tabs.map((item) => <button type="button" className={tab === item.id ? "active" : "secondary"} onClick={() => setTab(item.id)} key={item.id}>{item.label}</button>)}</nav>
    {notice && <p className={`feedback ${notice.tone}`} role="alert">{notice.text}</p>}
    {tab === "overview" && <div className="financial-overview"><article className="work-card"><span className="card-kicker">ESCOPO</span><h3>{programs.length} programa(s) autorizado(s)</h3><p>O backend filtra por vínculo institucional, papel financeiro e MFA. Nenhum saldo é criado para preencher esta visão.</p></article><article className="work-card"><span className="card-kicker">REPASSES</span><h3>{payouts?.items.length ?? 0} obrigação(ões) visível(is)</h3><p>{payouts?.uncertain ?? 0} incerta(s) · {payouts?.divergences ?? 0} divergência(s). Solicitação e confirmação são eventos separados.</p></article></div>}
    {(tab === "programs" || tab === "balance") && <><div className="work-card financial-selector"><label>Programa autorizado<select aria-label="Programa financeiro" value={programId} onChange={(event) => setProgramId(event.target.value)}><option value="">Selecione um programa</option>{programs.map((item) => <option value={item.id} key={item.id}>{item.institutionName} · {item.name} ({item.code})</option>)}</select></label>{selectedProgram && <p>Estado: <strong>{selectedProgram.status}</strong>. Os critérios e valores exibidos vêm do registro vigente.</p>}</div>{tab === "balance" && balance && <div className="ledger-summary" aria-label="Saldos do programa"><article><strong>{formatMoney(balance.available)}</strong><span>disponível</span></article><article><strong>{formatMoney(balance.reserved)}</strong><span>reservado</span></article><article><strong>{formatMoney(balance.settled)}</strong><span>liquidado</span></article></div>}{tab === "programs" && <form className="work-card" onSubmit={record}><h3>Registrar aporte</h3><p>O comprovante não confirma o aporte. A revisão exige outro gestor, MFA e evidência de conciliação inspecionada.</p><label>Valor em BRL<input required inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value)} /></label><label>Referência de origem<input required value={sourceReference} onChange={(event) => setSourceReference(event.target.value)} /></label><label>Comprovante privado<input required type="file" accept="application/pdf,image/png,image/jpeg" onChange={(event) => void upload(event)} /></label><button disabled={busy || !programId || !evidenceId}>Registrar como pendente</button></form>}{tab === "programs" && <FinancialList title="Aportes registrados" query={query} setQuery={setQuery} statusFilter={statusFilter} setStatusFilter={setStatusFilter} items={visibleFunding} statuses={["PENDENTE", "CONFIRMADO", "REJEITADO"]} empty="Selecione um programa para consultar os aportes." render={(item) => <button type="button" className="financial-record" onClick={() => void openFunding(item as Funding)}><strong>{formatMoney((item as Funding).amount)}</strong><span>{item.status === "PENDENTE" ? "Pendente de revisão" : item.status === "CONFIRMADO" ? "Confirmado" : "Rejeitado"}</span><small>Detalhar · versão {item.version}</small></button>} count={visibleFunding.length} page={page} setPage={setPage} />}</>}
    {(tab === "obligations" || tab === "reconciliation") && (
      <FinancialList
        title={tab === "obligations" ? "Obrigações e repasses" : "Conciliação e divergências"}
        query={query}
        setQuery={setQuery}
        statusFilter={statusFilter}
        setStatusFilter={setStatusFilter}
        items={visiblePayouts}
        statuses={["OBRIGACAO_REGISTRADA", "SOLICITADO", "CONFIRMADO", "FALHOU", "INCERTO", "DIVERGENTE"]}
        empty="Nenhuma obrigação autorizada foi registrada."
        render={(item) => <button type="button" className="financial-record" onClick={() => void openPayout(item as Payout)}><strong>{formatMoney((item as Payout).amount)}</strong><span>{item.status.replaceAll("_", " ")}</span><small>Pedido {(item as Payout).orderId.slice(0, 8)} · abrir detalhe</small></button>}
        count={visiblePayouts.length}
        page={page}
        setPage={setPage}
      />
    )}
    {selectedFunding && <aside className="financial-detail" aria-label="Detalhe do aporte"><button type="button" className="secondary" onClick={() => setSelectedFunding(null)}>Fechar detalhe</button><span className="card-kicker">DETALHE DO APORTE</span><h3>{formatMoney(selectedFunding.amount)} · {selectedFunding.status}</h3><p>Comprovante: anexado e autorizado. Saldo só muda em CONFIRMADO.</p>{selectedFunding.status === "PENDENTE" && <><label>Evidência de conciliação<input type="file" accept="application/pdf,image/png,image/jpeg" onChange={(event) => void upload(event, true)} /></label><label>Motivo da revisão<input value={reason} onChange={(event) => setReason(event.target.value)} /></label><div className="financial-actions"><button type="button" disabled={busy || !reconciliationId} onClick={() => void review("CONFIRMADO")}>Confirmar aporte</button><button type="button" className="secondary" disabled={busy || !reconciliationId} onClick={() => void review("REJEITADO")}>Rejeitar aporte</button></div></>}<HistoryList history={history} /></aside>}
    {selectedPayout && <aside className="financial-detail" aria-label="Detalhe da obrigação"><button type="button" className="secondary" onClick={() => setSelectedPayout(null)}>Fechar detalhe</button><span className="card-kicker">DETALHE DA OBRIGAÇÃO</span><h3>{formatMoney(selectedPayout.amount)} · {selectedPayout.status.replaceAll("_", " ")}</h3><p>Obrigação registrada não é pagamento confirmado. Resultado incerto exige conciliação da mesma referência.</p><div className="financial-actions">{selectedPayout.status === "OBRIGACAO_REGISTRADA" && <button type="button" disabled={busy || selectedPayout.availability === "INDISPONIVEL"} onClick={() => void payoutAction("request")}>{selectedPayout.availability === "INDISPONIVEL" ? "Integração indisponível" : "Solicitar repasse"}</button>}{["INCERTO", "DIVERGENTE", "SOLICITADO"].includes(selectedPayout.status) && <button type="button" disabled={busy} onClick={() => void payoutAction("reconcile")}>Conciliar mesma referência</button>}</div>{selectedPayout.divergenceCode && <p className="ledger-alert">Divergência: {selectedPayout.divergenceCode}</p>}<HistoryList history={history} /></aside>}
  </section>;
}

function FinancialList({ title, query, setQuery, statusFilter, setStatusFilter, statuses, items, render, empty, count, page, setPage }: { title: string; query: string; setQuery: (v: string) => void; statusFilter: string; setStatusFilter: (v: string) => void; statuses: string[]; items: Array<Funding | Payout>; render: (item: Funding | Payout) => ReactNode; empty: string; count: number; page: number; setPage: (v: number) => void }) {
  return <section className="financial-list"><div className="card-heading"><div><span className="card-kicker">LISTAGEM</span><h3>{title}</h3></div><span>{items.length} nesta página</span></div><div className="review-toolbar"><label>Buscar<input aria-label={`Buscar em ${title}`} value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Status ou referência" /></label><label>Status<select aria-label={`Filtrar ${title}`} value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}><option value="TODOS">Todos</option>{statuses.map((status) => <option key={status}>{status}</option>)}</select></label></div>{items.length ? <div className="financial-records">{items.map((item) => <article key={item.id}>{render(item)}</article>)}</div> : <div className="empty-state"><strong>{empty}</strong><p>Escolha um registro autorizado para ver detalhes e o próximo passo permitido.</p></div>}<div className="pagination"><button type="button" className="secondary" disabled={page === 0} onClick={() => setPage(Math.max(0, page - 1))}>Anterior</button><span>Página {page + 1}</span><button type="button" className="secondary" disabled={count < 5} onClick={() => setPage(page + 1)}>Próxima</button></div></section>;
}

function HistoryList({ history }: { history: FinancialHistory[] }) { return <section className="financial-history"><span className="card-kicker">HISTÓRICO AUDITÁVEL</span>{history.length ? history.map((item, index) => <div key={`${item.occurredAt}-${index}`}><strong>{item.event.replaceAll("_", " ")}</strong><span>{item.status.replaceAll("_", " ")}</span><small>{item.detail || "Sem motivo adicional"} · {new Date(item.occurredAt).toLocaleString("pt-BR")}</small></div>) : <p className="empty-state">Nenhum marco registrado para este item.</p>}</section>; }

void PayoutPanel;
void FundingPanel;

function DelivererPanel({ session }: { session: Tokens }) {
  const [profile, setProfile] = useState<Deliverer | null>(null);
  const [docs, setDocs] = useState<Evidence[]>([]);
  const [birthDate, setBirthDate] = useState("");
  const [category, setCategory] = useState("HABILITACAO");
  const [vehicles, setVehicles] = useState<VehicleLink[]>([]);
  const [selectedVehicle, setSelectedVehicle] = useState<VehicleLink | null>(
    null,
  );
  const [vehiclePurpose, setVehiclePurpose] = useState("CRLV");
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  const [uploadProgress, setUploadProgress] = useState<number | null>(null);
  const [section, setSection] = useState<"overview" | "profile" | "upload" | "history" | "vehicles">("overview");
  const [offerAccess, setOfferAccess] = useState<"loading" | "allowed" | string>("loading");
  async function load() {
    const h = { Authorization: `Bearer ${session.accessToken}` };
    const p = await fetch("/api/v1/me/deliverer", { headers: h });
    if (p.ok) {
      const current = (await p.json()) as Deliverer;
      setProfile(current);
      setBirthDate(current.birthDate);
      const d = await fetch("/api/v1/me/deliverer/documents", { headers: h });
      if (d.ok) setDocs(await d.json());
      const v = await fetch("/api/v1/me/vehicle-links", { headers: h });
      if (v.ok) {
        const links = (await v.json()) as VehicleLink[];
        setVehicles(links);
        if (selectedVehicle)
          setSelectedVehicle(
            links.find((item) => item.id === selectedVehicle.id) ?? null,
          );
      }
      const offers = await fetch("/api/v1/offers", { headers: h });
      if (offers.ok) setOfferAccess("allowed");
      else {
        const body = await offers.json().catch(() => ({}));
        setOfferAccess(body.message ?? "A habilitação para receber ofertas ainda não foi confirmada.");
      }
    } else if (p.status === 404) {
      setProfile(null);
      setOfferAccess("Crie o cadastro para consultar a habilitação de ofertas.");
    } else
      throw new Error("Não foi possível consultar o cadastro.");
  }
  useEffect(() => {
    load().catch((e) => setNotice({ tone: "error", text: e.message }));
  }, [session.accessToken]);
  async function save(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setNotice(null);
    try {
      const r = await fetch(
        `/api/v1/me/deliverer${profile ? "?version=" + profile.version : ""}`,
        {
          method: profile ? "PUT" : "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${session.accessToken}`,
          },
          body: JSON.stringify({ birthDate }),
        },
      );
      const b = await r.json();
      if (!r.ok) throw new Error(b.message ?? "Cadastro não salvo.");
      setProfile(b);
      setNotice({
        tone: "info",
        text: "Cadastro salvo. Isto não aprova o entregador.",
      });
    } catch (e) {
      setNotice({
        tone: "error",
        text: e instanceof Error ? e.message : "Resultado não confirmado.",
      });
    } finally {
      setBusy(false);
    }
  }
  async function upload(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const file = (form.elements.namedItem("file") as HTMLInputElement)
      .files?.[0];
    if (!file) return;
    const allowedTypes = new Set(["application/pdf", "image/jpeg", "image/png"]);
    if (!allowedTypes.has(file.type) || file.size > 10 * 1024 * 1024) {
      setNotice({ tone: "error", text: "Escolha um PDF, JPEG ou PNG de até 10 MiB. O arquivo não foi enviado." });
      return;
    }
    if (!profile) {
      setNotice({ tone: "error", text: "Salve o cadastro de entregador antes de enviar documentos." });
      return;
    }
    const data = new FormData();
    data.append("category", category);
    data.append("file", file);
    setBusy(true);
    setUploadProgress(0);
    setNotice(null);
    try {
      const b = await uploadWithProgress<Evidence>(
        "/api/v1/me/deliverer/documents",
        data,
        session.accessToken,
        setUploadProgress,
      );
      setNotice({
        tone: "info",
        text: `Arquivo em ${b.status.toLowerCase()}. A inspeção e aprovação profissional são separadas.`,
      });
      (form as HTMLFormElement).reset();
      await load();
    } catch (e) {
      setNotice({
        tone: "error",
        text: e instanceof Error ? e.message : "Resultado não confirmado.",
      });
    } finally {
      setBusy(false);
      window.setTimeout(() => setUploadProgress(null), 800);
    }
  }
  async function saveVehicle(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const data = new FormData(form);
    setBusy(true);
    setNotice(null);
    try {
      const payload = {
        plate: data.get("plate"),
        make: data.get("make"),
        model: data.get("model"),
        color: data.get("color"),
        manufacturingYear: Number(data.get("manufacturingYear")),
        modelYear: Number(data.get("modelYear")),
        linkType: data.get("linkType"),
        validUntil: data.get("validUntil") || null,
      };
      const path = selectedVehicle
        ? `/api/v1/me/vehicle-links/${selectedVehicle.id}?version=${selectedVehicle.version}`
        : "/api/v1/me/vehicle-links";
      const r = await fetch(path, {
        method: selectedVehicle ? "PUT" : "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${session.accessToken}`,
        },
        body: JSON.stringify(payload),
      });
      const b = await r.json();
      if (!r.ok) throw new Error(b.message ?? "Vínculo não salvo.");
      setSelectedVehicle(b);
      await load();
      setNotice({
        tone: "info",
        text: "Vínculo salvo como pendente. Nenhuma consulta oficial foi simulada.",
      });
    } catch (e) {
      setNotice({
        tone: "error",
        text: e instanceof Error ? e.message : "Resultado não confirmado.",
      });
    } finally {
      setBusy(false);
    }
  }
  async function uploadVehicle(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!selectedVehicle) return;
    const form = e.currentTarget;
    const file = (form.elements.namedItem("vehicleFile") as HTMLInputElement)
      .files?.[0];
    if (!file) return;
    const data = new FormData();
    data.append("version", String(selectedVehicle.version));
    data.append("purpose", vehiclePurpose);
    data.append("file", file);
    setBusy(true);
    setNotice(null);
    setUploadProgress(0);
    try {
      const b = await uploadWithProgress<VehicleLink>(
        `/api/v1/me/vehicle-links/${selectedVehicle.id}/documents`,
        data,
        session.accessToken,
        setUploadProgress,
      );
      setSelectedVehicle(b);
      await load();
      form.reset();
      setNotice({
        tone: "info",
        text: "Arquivo em quarentena. A substituição abriu uma revisão nova, sem herdar aprovação.",
      });
    } catch (e) {
      setNotice({
        tone: "error",
        text: e instanceof Error ? e.message : "Resultado não confirmado.",
      });
    } finally {
      setBusy(false);
      window.setTimeout(() => setUploadProgress(null), 800);
    }
  }
  const tabs = [
    ["overview", "Visão geral"],
    ["profile", "Cadastro"],
    ["upload", "Enviar documento"],
    ["history", "Situação dos documentos"],
    ["vehicles", "Veículos e vínculos"],
  ] as const;
  function moveTab(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
    event.preventDefault();
    const next = event.key === "Home" ? 0 : event.key === "End" ? tabs.length - 1 : (index + (event.key === "ArrowRight" ? 1 : -1) + tabs.length) % tabs.length;
    setSection(tabs[next][0]);
    window.requestAnimationFrame(() => document.getElementById(`deliverer-tab-${tabs[next][0]}`)?.focus());
  }
  return (
    <section className="family" aria-labelledby="deliverer-title">
      <div className="section-heading">
        <span className="eyebrow dark">ENTREGADOR</span>
        <h2 id="deliverer-title">Cadastro e comprovações</h2>
        <p>
          Cadastro, inspeção do arquivo e aprovação profissional são etapas
          distintas. Nenhum upload habilita entregas.
        </p>
      </div>
      <div className="tabs" role="tablist" aria-label="Área do entregador">
        {tabs.map(([value, label], index) => (
          <button type="button" className="tab" role="tab" id={`deliverer-tab-${value}`} aria-controls={`deliverer-panel-${value}`} aria-selected={section === value} tabIndex={section === value ? 0 : -1} key={value} onClick={() => setSection(value)} onKeyDown={(event) => moveTab(event, index)}>
            {label}
          </button>
        ))}
      </div>
      {section === "overview" && (
        <>
          <div className="deliverer-status-card">
            <div>
              <span className="card-kicker">SITUAÇÃO DO CADASTRO</span>
              <h3>{profile ? profile.status.replaceAll("_", " ") : "Ainda não iniciado"}</h3>
              <p>{profile ? "Acesso à área, cadastro enviado, análise documental e habilitação para ofertas são estados diferentes." : "Sua conta tem acesso à área. Comece o cadastro quando quiser; nenhum documento ou oferta foi presumido."}</p>
            </div>
            <strong className={offerAccess === "allowed" ? "status-ok" : "status-pending"}>
              {offerAccess === "loading" ? "Consultando habilitação…" : offerAccess === "allowed" ? "Habilitado para receber ofertas" : "Ofertas ainda bloqueadas"}
            </strong>
          </div>
          <div className="progress-track" aria-label="Progresso do cadastro">
            {[["Acesso à área", true], ["Cadastro enviado", Boolean(profile)], ["Documentos analisados", docs.some((item) => item.status === "INSPECAO_APROVADA")], ["Ofertas liberadas", offerAccess === "allowed"]].map(([label, done]) => (
              <div className={done ? "is-done" : ""} key={String(label)}><span>{done ? "✓" : "○"}</span><strong>{label}</strong></div>
            ))}
          </div>
          <div className="empty-state">
            <strong>Próximas ações</strong>
            <p>{!profile ? "Informe sua data de nascimento para enviar o cadastro." : !docs.length ? "Envie documentos e aguarde a análise profissional." : !vehicles.length ? "Cadastre um veículo e seu vínculo." : offerAccess === "allowed" ? "Nenhuma pendência de habilitação foi informada pelo servidor." : "A habilitação para ofertas continua bloqueada por requisitos do backend."}</p>
            <button type="button" onClick={() => setSection(profile ? "history" : "profile")}>{profile ? "Ver situação dos documentos" : "Começar cadastro"}</button>
          </div>
        </>
      )}
      {section === "profile" && (
        <div id="deliverer-panel-profile" role="tabpanel" aria-labelledby="deliverer-tab-profile" tabIndex={0} className="work-grid">
        <form className="work-card" onSubmit={save}>
          <h3>{profile ? "Atualizar cadastro" : "Criar cadastro"}</h3>
          <p>Dados básicos e situação atual do cadastro. Salvar alterações mantém o processo em rascunho e não concede aprovação profissional.</p>
          <label htmlFor="delivererBirthDate">Data de nascimento</label>
          <input
            id="delivererBirthDate"
            type="date"
            value={birthDate}
            onChange={(e) => setBirthDate(e.target.value)}
            required
          />
          <button disabled={busy}>
            {profile ? "Atualizar" : "Salvar cadastro"}
          </button>
          {profile && (
            <small>
              Estado: {profile.status.toLowerCase()} · versão {profile.version}
            </small>
          )}
        </form>
        <aside className="work-card">
          <h3>Situação atual</h3>
          <p>{profile ? `Cadastro ${profile.status.toLowerCase()}. Documentos e habilitação são analisados separadamente.` : "Nenhum cadastro foi persistido ainda."}</p>
          <strong>{docs.length ? `${docs.length} documento(s) registrado(s)` : "Nenhum documento registrado"}</strong>
        </aside>
        </div>
      )}
      {section === "upload" && (
        <div id="deliverer-panel-upload" role="tabpanel" aria-labelledby="deliverer-tab-upload" tabIndex={0}>
        <form className="work-card" onSubmit={upload}>
          <h3>Enviar documento privado</h3>
          <label htmlFor="documentCategory">Categoria</label>
          <select
            id="documentCategory"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
          >
            <option>IDENTIDADE</option>
            <option>HABILITACAO</option>
            <option>VEICULO</option>
            <option>FOTO_OPERACIONAL</option>
          </select>
          <label htmlFor="documentFile">
            Arquivo PDF, JPEG ou PNG (até 10 MiB)
          </label>
          <input
            id="documentFile"
            name="file"
            type="file"
            accept="application/pdf,image/jpeg,image/png"
            required
          />
          <button disabled={busy || !profile}>Enviar para quarentena</button>
          {!profile && <small>O cadastro básico é necessário antes do upload.</small>}
        </form>
        </div>
      )}
      {uploadProgress !== null && (
        <div className="upload-progress" role="status" aria-live="polite">
          <span>Enviando arquivo privado</span>
          <progress max="100" value={uploadProgress}>
            {uploadProgress}%
          </progress>
          <strong>{uploadProgress}%</strong>
        </div>
      )}
      {section === "history" && <article id="deliverer-panel-history" role="tabpanel" aria-labelledby="deliverer-tab-history" tabIndex={0} className="registers">
        <h3>Histórico mínimo</h3>
        {docs.length ? (
          docs.map((d) => (
            <p key={d.id}>
              <strong>{d.status}</strong>
              <span>
                {d.category} · {d.mime} · {Math.round(d.size / 1024)} KiB · antimalware {d.antimalwareStatus ?? "PENDENTE"}
              </span>
              {d.reason && <small>Motivo informado: {d.reason}</small>}
              {d.status === "REJEITADO" && (
                <>
                  <small>Envie um novo arquivo da mesma categoria para substituir este documento.</small>
                  <button type="button" className="secondary" onClick={() => { setCategory(d.category); setSection("upload"); }}>Substituir documento</button>
                </>
              )}
            </p>
          ))
        ) : (
          <p className="empty">Nenhum arquivo enviado.</p>
        )}
      </article>}
      {section === "vehicles" && (
        <>
      <div className="section-heading vehicle-heading">
        <span className="eyebrow dark">VEÍCULO PRIVADO</span>
        <h2>Vínculo comprovável</h2>
        <p>
          Propriedade não é requisito universal. Registre veículo próprio,
          alugado ou autorizado; CRLV, fotos e autorização ficam privados e
          sujeitos a nova revisão quando substituídos.
        </p>
      </div>
      <div className="work-grid">
        <form
          key={selectedVehicle?.id ?? "new-vehicle"}
          className="work-card"
          onSubmit={saveVehicle}
        >
          <h3>
            {selectedVehicle
              ? "Alterar veículo selecionado"
              : "Cadastrar veículo"}
          </h3>
          <label htmlFor="vehiclePlate">Placa</label>
          <input
            id="vehiclePlate"
            name="plate"
            defaultValue={selectedVehicle?.plate}
            pattern="[A-Za-z0-9 -]{7,10}"
            required
          />
          <label htmlFor="vehicleMake">Marca</label>
          <input
            id="vehicleMake"
            name="make"
            defaultValue={selectedVehicle?.make}
            maxLength={80}
            required
          />
          <label htmlFor="vehicleModel">Modelo</label>
          <input
            id="vehicleModel"
            name="model"
            defaultValue={selectedVehicle?.model}
            maxLength={80}
            required
          />
          <label htmlFor="vehicleColor">Cor</label>
          <input
            id="vehicleColor"
            name="color"
            defaultValue={selectedVehicle?.color}
            maxLength={40}
            required
          />
          <div className="inline-fields">
            <label htmlFor="vehicleManufacturingYear">
              Ano de fabricação
              <input
                id="vehicleManufacturingYear"
                name="manufacturingYear"
                type="number"
                min="1886"
                defaultValue={selectedVehicle?.manufacturingYear}
                required
              />
            </label>
            <label htmlFor="vehicleModelYear">
              Ano do modelo
              <input
                id="vehicleModelYear"
                name="modelYear"
                type="number"
                min="1886"
                defaultValue={selectedVehicle?.modelYear}
                required
              />
            </label>
          </div>
          <label htmlFor="vehicleLinkType">Vínculo</label>
          <select
            id="vehicleLinkType"
            name="linkType"
            defaultValue={selectedVehicle?.linkType ?? "PROPRIEDADE"}
          >
            <option value="PROPRIEDADE">Próprio</option>
            <option value="LOCACAO">Alugado</option>
            <option value="AUTORIZACAO">Uso autorizado</option>
          </select>
          <label htmlFor="vehicleValidUntil">Validade, quando aplicável</label>
          <input
            id="vehicleValidUntil"
            name="validUntil"
            type="date"
            defaultValue={selectedVehicle?.validUntil ?? ""}
          />
          <button disabled={busy}>
            {selectedVehicle
              ? "Salvar alteração e revisar novamente"
              : "Criar vínculo pendente"}
          </button>
        </form>
        <form className="work-card" onSubmit={uploadVehicle}>
          <h3>Evidência do vínculo</h3>
          <p>
            Selecione um vínculo abaixo. Cada substituição preserva o histórico
            e invalida a revisão anterior.
          </p>
          <label htmlFor="vehicleEvidencePurpose">Finalidade</label>
          <select
            id="vehicleEvidencePurpose"
            value={vehiclePurpose}
            onChange={(e) => setVehiclePurpose(e.target.value)}
          >
            <option value="CRLV">CRLV</option>
            <option value="FOTO">Foto do veículo</option>
            {selectedVehicle?.linkType !== "PROPRIEDADE" && (
              <option value="USO_AUTORIZADO">
                Locação ou autorização de uso
              </option>
            )}
          </select>
          <label htmlFor="vehicleEvidenceFile">
            PDF, JPEG ou PNG (até 10 MiB)
          </label>
          <input
            id="vehicleEvidenceFile"
            name="vehicleFile"
            type="file"
            accept="application/pdf,image/jpeg,image/png"
            required
          />
          <button disabled={busy || !selectedVehicle}>
            Enviar para quarentena
          </button>
          <small>
            Inspeção estrutural não comprova autenticidade e não substitui
            varredura antimalware.
          </small>
        </form>
      </div>
      <div className="registers vehicle-registers">
        {vehicles.length ? (
          vehicles.map((vehicle) => (
            <article key={vehicle.id}>
              <h3>
                {vehicle.plate} · {vehicle.model}
              </h3>
              <p>
                <strong>{vehicle.status}</strong>
                <span>
                  {vehicle.linkType} · versão {vehicle.version} · evidências{" "}
                  {vehicle.evidenceComplete ? "completas" : "incompletas"}
                </span>
                <button
                  type="button"
                  onClick={() => setSelectedVehicle(vehicle)}
                >
                  Selecionar
                </button>
              </p>
              {vehicle.documents.map((document) => (
                <p key={`${document.id}-${document.purpose}`}>
                  <strong>{document.status}</strong>
                  <span>
                    {document.purpose}
                    {document.current ? "" : " · substituído"}
                  </span>
                </p>
              ))}
            </article>
          ))
        ) : (
          <article>
            <p className="empty">Nenhum vínculo cadastrado.</p>
          </article>
        )}
      </div>
        </>
      )}
      {notice && (
        <p className={`feedback ${notice.tone}`} role="alert">
          {notice.text}
        </p>
      )}
    </section>
  );
}

function VehicleReviewPanel({ session }: { session: Tokens }) {
  const [reviews, setReviews] = useState<VehicleReview[]>([]);
  const [available, setAvailable] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  async function call(path: string, init: RequestInit = {}) {
    const r = await fetch(`/api/v1${path}`, {
      ...init,
      headers: {
        ...(init.body ? { "Content-Type": "application/json" } : {}),
        Authorization: `Bearer ${session.accessToken}`,
        ...(init.headers ?? {}),
      },
    });
    if (r.status === 403 && path === "/analyst/vehicle-reviews") {
      setAvailable(false);
      return null;
    }
    const b = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(b.message ?? "Ação operacional não concluída.");
    return b;
  }
  async function load() {
    const b = await call("/analyst/vehicle-reviews");
    if (b) {
      setAvailable(true);
      setReviews(b as VehicleReview[]);
    }
  }
  useEffect(() => {
    load().catch((e) => setNotice({ tone: "error", text: e.message }));
  }, [session.accessToken]);
  async function run(action: () => Promise<void>, text: string) {
    setBusy(true);
    setNotice(null);
    try {
      await action();
      await load();
      setNotice({ tone: "info", text });
    } catch (e) {
      setNotice({
        tone: "error",
        text: e instanceof Error ? e.message : "Resultado não confirmado.",
      });
    } finally {
      setBusy(false);
    }
  }
  async function action(review: VehicleReview, path: string, message: string) {
    await run(async () => {
      await call(`/analyst/vehicle-reviews/${review.id}${path}`, {
        method: "POST",
        body:
          path === "/decision"
            ? JSON.stringify({
                decision: "APROVAR",
                reason: "revisão operacional",
              })
            : undefined,
      });
    }, message);
  }
  async function download(review: VehicleReview, evidence: VehicleEvidence) {
    setBusy(true);
    try {
      const r = await fetch(
        `/api/v1/analyst/vehicle-reviews/${review.id}/documents/${evidence.id}/download`,
        { headers: { Authorization: `Bearer ${session.accessToken}` } },
      );
      if (!r.ok) {
        const b = await r.json();
        throw new Error(b.message ?? "Download não autorizado.");
      }
      const url = URL.createObjectURL(await r.blob());
      const a = document.createElement("a");
      a.href = url;
      a.download = `evidencia-${evidence.id}`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      setNotice({
        tone: "error",
        text: e instanceof Error ? e.message : "Download não confirmado.",
      });
    } finally {
      setBusy(false);
    }
  }
  if (!available) return null;
  const selected = reviews.find((review) => review.id === selectedId) ?? null;
  return (
    <section
      className="family review-panel"
      aria-labelledby="vehicle-review-title"
    >
      <div className="section-heading">
        <span className="eyebrow dark">PAINEL OPERACIONAL</span>
        <h2 id="vehicle-review-title">Revisão atribuída</h2>
        <p>
          A fila exige atribuição nominal e MFA. O analista não pode revisar o
          próprio vínculo; inspeção estrutural e aprovação profissional
          continuam independentes.
        </p>
      </div>
      {selected ? (
        <article className="review-detail-card">
          <div className="card-heading"><div><span className="card-kicker">DETALHE DA REVISÃO</span><h3>Vínculo de veículo</h3></div><button type="button" className="secondary" onClick={() => setSelectedId(null)}>Voltar à fila</button></div>
          <div className="review-detail-grid"><div><strong>Situação</strong><span>{selected.status}</span><small>Snapshot da versão {selected.linkVersion}.</small></div><div><strong>Documentos autorizados</strong><span>{selected.documents.length ? `${selected.documents.length} documento(s)` : "Nenhum documento"}</span><small>Quarentena, inspeção e decisão permanecem separados.</small></div></div>
          {selected.documents.map((document) => <div className="review-evidence-row" key={document.id}><strong>{document.status}</strong><span>{document.purpose}</span>{selected.status === "ATRIBUIDA" && <><button type="button" disabled={busy || document.status !== "QUARENTENA"} onClick={() => void action(selected, `/documents/${document.id}/inspection`, "Inspeção estrutural registrada.")}>Inspecionar estrutura</button><button type="button" disabled={busy || document.status !== "INSPECAO_APROVADA"} onClick={() => void download(selected, document)}>Download autorizado</button></>}</div>)}
          <div className="journey-actions">{selected.status === "PENDENTE" && <button type="button" disabled={busy} onClick={() => void action(selected, "/assign", "Revisão atribuída.")}>Assumir revisão</button>}{selected.status === "ATRIBUIDA" && <button type="button" disabled={busy} onClick={() => void action(selected, "/decision", "Decisão registrada.")}>Solicitar decisão</button>}</div>
        </article>
      ) : <div className="registers vehicle-registers">{reviews.length ? reviews.map((review) => <article key={review.id}><h3>Vínculo de veículo</h3><p><strong>{review.status}</strong><span>snapshot da versão {review.linkVersion}</span></p><button type="button" onClick={() => setSelectedId(review.id)}>Abrir detalhe</button></article>) : <article><p className="empty">Nenhuma revisão de veículo na fila.</p></article>}</div>}
      {notice && (
        <p className={`feedback ${notice.tone}`} role="alert">
          {notice.text}
        </p>
      )}
    </section>
  );
}

function OrderPanel({ session }: { session: Tokens }) {
  const draftKey = "exameperto-order-draft";
  const [patientId, setPatientId] = useState("");
  const [recipientId, setRecipientId] = useState("");
  const [unitId, setUnitId] = useState("");
  const [unitOptions, setUnitOptions] = useState<AvailableUnit[]>([]);
  const [patientOptions, setPatientOptions] = useState<
    { id: string; label: string }[]
  >([]);
  const [recipientOptions, setRecipientOptions] = useState<
    { id: string; label: string }[]
  >([]);
  const [origin, setOrigin] = useState({
    street: "",
    number: "",
    district: "",
    city: "",
    state: "",
    postalCode: "",
    latitude: "", longitude: "", complement: "", reference: "", locationConfirmationToken: "",
  });
  const [destination, setDestination] = useState({
    street: "",
    number: "",
    district: "",
    city: "",
    state: "",
    postalCode: "",
    latitude: "", longitude: "", complement: "", reference: "", locationConfirmationToken: "",
  });
  const [documentId, setDocumentId] = useState("");
  const [orders, setOrders] = useState<Order[]>([]);
  const [quotes, setQuotes] = useState<Record<string, Quote[]>>({});
  const [financialOperations, setFinancialOperations] = useState<Record<string, FinancialOperation[]>>({});
  const [checkout, setCheckout] = useState<Record<string, { url: string; expiresAt: string; operationId: string }>>({});
  const [benefitPrograms, setBenefitPrograms] = useState<BenefitProgramOption[]>([]);
  const [coverageProgramId, setCoverageProgramId] = useState("");
  const [assignments, setAssignments] = useState<Record<string, Assignment>>(
    {},
  );
  const [drivers, setDrivers] = useState<Record<string, OperationalIdentity>>(
    {},
  );
  const [custodies, setCustodies] = useState<Record<string, Custody>>({});
  const [incidents, setIncidents] = useState<Record<string, Incident[]>>({});
  const [receiptCodes, setReceiptCodes] = useState<
    Record<string, ReceiptChallenge>
  >({});
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  const [step, setStep] = useState(1);
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null);
  const [locations, setLocations] = useState<
    Record<string, LiveLocation | null>
  >({});
  const [editingAddresses, setEditingAddresses] = useState(false);
  const [detailOrigin, setDetailOrigin] = useState<typeof origin | null>(null);
  const [detailDestination, setDetailDestination] = useState<
    typeof destination | null
  >(null);
  const [originCandidates, setOriginCandidates] = useState<GeocodeCandidate[]>([]);
  const [destinationCandidates, setDestinationCandidates] = useState<GeocodeCandidate[]>([]);
  const [searchingLocation, setSearchingLocation] = useState<"origin" | "destination" | null>(null);
  const socket = useRef<WebSocket | null>(null);
  const reconnect = useRef<number | undefined>(undefined);
  const activeOrders = useRef<string[]>([]);
  const activeOrderObjects = useRef<Order[]>([]);
  useEffect(() => {
    try {
      const saved = JSON.parse(sessionStorage.getItem(draftKey) ?? "null") as {
        patientId?: string;
        recipientId?: string;
        unitId?: string;
        documentId?: string;
        origin?: typeof origin;
        destination?: typeof destination;
        step?: number;
      } | null;
      if (!saved) return;
      if (saved.patientId) setPatientId(saved.patientId);
      if (saved.recipientId) setRecipientId(saved.recipientId);
      if (saved.unitId) setUnitId(saved.unitId);
      if (saved.documentId) setDocumentId(saved.documentId);
      if (saved.origin) setOrigin(saved.origin);
      if (saved.destination) setDestination(saved.destination);
      if (saved.step) setStep(saved.step);
    } catch {
      sessionStorage.removeItem(draftKey);
    }
  }, []);
  const hasDraft = Boolean(
    patientId ||
      recipientId ||
      unitId ||
      documentId ||
      Object.values(origin).some(Boolean) ||
      Object.values(destination).some(Boolean) ||
      step > 1,
  );
  useEffect(() => {
    if (!hasDraft) {
      sessionStorage.removeItem(draftKey);
      return;
    }
    sessionStorage.setItem(
      draftKey,
      JSON.stringify({
        patientId,
        recipientId,
        unitId,
        documentId,
        origin,
        destination,
        step,
      }),
    );
  }, [patientId, recipientId, unitId, documentId, origin, destination, step, hasDraft]);
  async function api(path: string, init: RequestInit = {}) {
    return parse(
      await fetch(`/api/v1${path}`, {
        ...init,
        credentials: "same-origin",
        headers: {
          Authorization: `Bearer ${session.accessToken}`,
          ...(init.body instanceof FormData
            ? {}
            : { "Content-Type": "application/json" }),
          ...(init.headers ?? {}),
        },
      }),
    );
  }
  async function load() {
    const [b, profile, patient, family, units] = await Promise.all([
      api("/orders"),
      api("/me/profile").catch(() => null),
      api("/me/patient").catch(() => null),
      api("/me/family-authorizations").catch(() => null),
      api("/order-options/units").catch(() => ({ result: [] })),
    ]);
    const availableUnits = (units?.result ?? []) as AvailableUnit[];
    setUnitOptions(availableUnits);
    const familyGrants =
      (family?.result as { items: Grant[] } | undefined)?.items ?? [];
    const patients = [
      ...(patient
        ? [
            {
              id: (patient.result as Patient).id,
              label: "Meu perfil de paciente",
            },
          ]
        : []),
      ...familyGrants
        .filter((grant) => grant.scopes.includes("PEDIDOS"))
        .map((grant, index) => ({
          id: grant.patientId,
          label: `Paciente autorizado ${index + 1}`,
        })),
    ];
    setPatientOptions(patients);
    if (!patientId && patients[0]) setPatientId(patients[0].id);
    const representedCanReceive = familyGrants.some(
      (grant) =>
        grant.patientId === (patientId || patients[0]?.id) &&
        grant.scopes.includes("RECEBIMENTO"),
    );
    const recipients =
      profile && (patient || representedCanReceive)
        ? [{ id: (profile.result as Profile).id, label: "Minha conta" }]
        : [];
    setRecipientOptions(recipients);
    if (!recipientId && recipients[0]) setRecipientId(recipients[0].id);
    const current = b.result as Order[];
    setOrders(current);
    const entries = await Promise.all(
      current.map(
        async (order) =>
          [
            order.id,
            (await api(`/orders/${order.id}/quotes`)).result as Quote[],
          ] as const,
      ),
    );
    setQuotes(Object.fromEntries(entries));
    const financialEntries = await Promise.all(current.map(async (order) => [order.id, (await api(`/orders/${order.id}/financial-operations`).catch(() => ({ result: [] }))).result as FinancialOperation[]] as const));
    setFinancialOperations(Object.fromEntries(financialEntries));
    const active = current.filter((order) =>
      ["ACEITA", "RETIRADA", "EM_ENTREGA", "OCORRENCIA"].includes(order.status),
    );
    activeOrders.current = active.map((order) => order.id);
    activeOrderObjects.current = active;
    const details = await Promise.all(
      active.map(async (order) => {
        const [assignment, driver, custody, incidentList] = await Promise.all([
          api(`/orders/${order.id}/assignment`).catch(() => null),
          api(`/orders/${order.id}/driver`).catch(() => null),
          api(`/orders/${order.id}/custody`).catch(() => null),
          api(`/orders/${order.id}/incidents`).catch(() => null),
        ]);
        return [
          order.id,
          assignment?.result as Assignment,
          driver?.result as OperationalIdentity,
          custody?.result as Custody,
          (incidentList?.result ?? []) as Incident[],
        ] as const;
      }),
    );
    setAssignments(
      Object.fromEntries(
        details.filter(([, a]) => a).map(([id, a]) => [id, a]),
      ),
    );
    setDrivers(
      Object.fromEntries(
        details.filter(([, a, d]) => a && d).map(([id, , d]) => [id, d]),
      ),
    );
    setCustodies(
      Object.fromEntries(
        details.filter(([, , , , c]) => c).map(([id, , , c]) => [id, c]),
      ),
    );
    setIncidents(Object.fromEntries(details.map(([id, , , , i]) => [id, i])));
    await Promise.all(active.map(latest));
    connectTracking();
  }
  async function latest(order: Order) {
    const response = await fetch(
      `/api/v1/orders/${order.id}/locations/latest`,
      { headers: { Authorization: `Bearer ${session.accessToken}` } },
    );
    if (response.ok) {
      const value = (await response.json()) as LiveLocation;
      setLocations((current) => ({ ...current, [order.id]: value }));
    } else if (response.status === 404 || response.status === 422)
      setLocations((current) => ({ ...current, [order.id]: null }));
  }
  function connectTracking() {
    socket.current?.close();
    window.clearTimeout(reconnect.current);
    const protocol = location.protocol === "https:" ? "wss" : "ws";
    const ws = new WebSocket(`${protocol}://${location.host}/ws`, [
      "v12.stomp",
    ]);
    socket.current = ws;
    ws.onopen = () =>
      ws.send(
        `CONNECT\naccept-version:1.2\nAuthorization:Bearer ${session.accessToken}\nheart-beat:10000,10000\n\n\0`,
      );
    ws.onmessage = (event) => {
      for (const frame of String(event.data).split("\0")) {
        if (!frame.trim()) continue;
        const split = frame.indexOf("\n\n");
        const headers = Object.fromEntries(
          frame
            .slice(0, split)
            .split("\n")
            .slice(1)
            .map((line) => {
              const i = line.indexOf(":");
              return [line.slice(0, i), line.slice(i + 1)];
            }),
        );
        if (frame.startsWith("CONNECTED")) {
          activeOrders.current.forEach((order) =>
            ws.send(
              `SUBSCRIBE\nid:${order}\ndestination:/user/queue/orders/${order}/locations\nack:auto\n\n\0`,
            ),
          );
        } else if (frame.startsWith("MESSAGE") && headers.subscription) {
          const value = JSON.parse(frame.slice(split + 2)) as LiveLocation;
          setLocations((current) => ({
            ...current,
            [headers.subscription]: value,
          }));
        } else if (frame.startsWith("ERROR")) ws.close();
      }
    };
    ws.onclose = () => {
      if (socket.current === ws) {
        socket.current = null;
        reconnect.current = window.setTimeout(() => {
          void Promise.all(activeOrderObjects.current.map(latest)).then(
            connectTracking,
          );
        }, 1000);
      }
    };
  }
  useEffect(() => {
    load().catch((e) => setNotice({ tone: "error", text: e.message }));
    return () => {
      window.clearTimeout(reconnect.current);
      socket.current?.close();
      socket.current = null;
    };
  }, [session.accessToken]);
  useEffect(() => {
    if (!selectedOrderId) {
      setDetailOrigin(null);
      setDetailDestination(null);
      setEditingAddresses(false);
      return;
    }
    api(`/orders/${selectedOrderId}/addresses`)
      .then((response) => {
        const addresses = response.result as {
          origin: typeof origin;
          destination: typeof destination;
        };
        const normalize = (value: typeof origin) => ({
          ...value,
          latitude: String(value.latitude ?? ""),
          longitude: String(value.longitude ?? ""),
          complement: value.complement ?? "", reference: value.reference ?? "", locationConfirmationToken: "",
        });
        setDetailOrigin(normalize(addresses.origin));
        setDetailDestination(normalize(addresses.destination));
      })
      .catch(() => {
        setDetailOrigin(null);
        setDetailDestination(null);
      });
    const selected = orders.find((item) => item.id === selectedOrderId);
    setCoverageProgramId("");
    setBenefitPrograms([]);
    if (selected) {
      api(`/order-options/benefit-programs?patientId=${encodeURIComponent(selected.patientId)}`)
        .then((response) => setBenefitPrograms((response.result ?? []) as BenefitProgramOption[]))
        .catch(() => setBenefitPrograms([]));
    }
  }, [selectedOrderId]);
  async function upload(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    setBusy(true);
    try {
      const f = new FormData();
      f.append("file", file);
      const b = await api("/order-documents/pickup-authorization", {
        method: "POST",
        body: f,
      });
      setDocumentId((b.result as Evidence).id);
      setNotice({
        tone: "info",
        text: "Evidência privada anexada; ela ainda depende da verificação da unidade.",
      });
    } catch (err) {
      setNotice({
        tone: "error",
        text: err instanceof Error ? err.message : "Upload não confirmado.",
      });
    } finally {
      setBusy(false);
    }
  }
  function address(value: typeof origin) {
    return {
      ...value,
      latitude: value.latitude ? Number(value.latitude) : null,
      longitude: value.longitude ? Number(value.longitude) : null,
    };
  }
  async function searchLocation(kind: "origin" | "destination", valueOverride?: typeof origin) {
    const value = valueOverride ?? (kind === "origin" ? origin : destination);
    setSearchingLocation(kind);
    try {
      const response = await api("/order-options/geocode", { method: "POST", body: JSON.stringify({ address: { ...address(value), latitude: null, longitude: null, locationConfirmationToken: null } }) });
      const candidates = (response.result ?? []) as GeocodeCandidate[];
      (kind === "origin" ? setOriginCandidates : setDestinationCandidates)(candidates);
      setNotice({ tone: "info", text: candidates.length ? "Escolha explicitamente um resultado. Nenhum local foi selecionado automaticamente." : "Nenhum resultado encontrado. Corrija a busca e tente novamente; seus dados foram preservados." });
    } catch (err) { setNotice({ tone: "error", text: err instanceof Error ? err.message : "Não foi possível buscar a localização." }); }
    finally { setSearchingLocation(null); }
  }
  function confirmLocation(kind: "origin" | "destination", candidate: GeocodeCandidate, valueOverride?: typeof origin, setterOverride?: (value: typeof origin) => void) {
    const setter = setterOverride ?? (kind === "origin" ? setOrigin : setDestination);
    const value = valueOverride ?? (kind === "origin" ? origin : destination);
    setter({ ...value, latitude: String(candidate.latitude), longitude: String(candidate.longitude), locationConfirmationToken: candidate.confirmationToken });
    (kind === "origin" ? setOriginCandidates : setDestinationCandidates)([]);
    setNotice({ tone: "info", text: `Local confirmado: ${candidate.label}. A estimativa de rota ainda não é comprovação de residência.` });
  }
  async function create(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      await api("/orders", {
        method: "POST",
        headers: { "Idempotency-Key": crypto.randomUUID() },
        body: JSON.stringify({
          patientId,
          recipientUserId: recipientId,
          pickupUnitId: unitId,
          pickupAuthorizationDocumentId: documentId,
          origin: address(origin),
          destination: address(destination),
        }),
      });
      setNotice({
        tone: "info",
        text: "Pedido registrado e enviado para verificação. A unidade ainda não foi considerada aceita.",
      });
      sessionStorage.removeItem(draftKey);
      setStep(1);
      await load();
    } catch (err) {
      setNotice({
        tone: "error",
        text: err instanceof Error ? err.message : "Pedido não confirmado.",
      });
    } finally {
      setBusy(false);
    }
  }
  async function quote(order: Order) {
    setBusy(true);
    try {
      const b = await api(`/orders/${order.id}/quotes`, {
        method: "POST",
        headers: {
          "If-Match": String(order.version),
          "Idempotency-Key": crypto.randomUUID(),
        },
        body: JSON.stringify({ programId: coverageProgramId || null }),
      });
      setQuotes((current) => ({
        ...current,
        [order.id]: [b.result as Quote, ...(current[order.id] ?? [])],
      }));
      setNotice({
        tone: "info",
        text: "Orçamento calculado com rota e tarifa versionadas.",
      });
      await load();
    } catch (err) {
      setNotice({
        tone: "error",
        text: err instanceof Error ? err.message : "Orçamento não confirmado.",
      });
    } finally {
      setBusy(false);
    }
  }
  async function updateAddresses(order: Order, event: FormEvent) {
    event.preventDefault();
    if (!detailOrigin || !detailDestination) return;
    setBusy(true);
    try {
      await api(`/orders/${order.id}/addresses`, {
        method: "PUT",
        body: JSON.stringify({
          origin: address(detailOrigin),
          destination: address(detailDestination),
          version: order.version,
        }),
      });
      setEditingAddresses(false);
      setNotice({
        tone: "info",
        text: "Endereço atualizado. O orçamento anterior foi substituído; solicite uma nova cotação quando a unidade confirmar a retirada.",
      });
      await load();
    } catch (err) {
      setNotice({
        tone: "error",
        text: err instanceof Error ? err.message : "Endereço não atualizado.",
      });
    } finally {
      setBusy(false);
    }
  }
  async function accept(order: Order, q: Quote) {
    setBusy(true);
    try {
      if (!q.cancellationPolicyId)
        throw new Error(
          "O aceite aguarda uma política de cancelamento validada.",
        );
      await api(`/quotes/${q.id}/accept`, {
        method: "POST",
        headers: {
          "If-Match": String(q.version),
          "Idempotency-Key": crypto.randomUUID(),
        },
        body: JSON.stringify({
          acceptedGrossAmount: q.grossAmount,
          acceptedPatientAmount: q.patientAmount,
          acceptedCurrency: "BRL",
          acceptedCancellationPolicyId: q.cancellationPolicyId,
          acceptedOrderVersion: order.version,
        }),
      });
      setNotice({
        tone: "info",
        text:
          q.patientAmount > 0
            ? "Condições aceitas. A cobertura aguarda confirmação autenticada do pagamento."
            : "Condições aceitas e subsídio reservado.",
      });
      await load();
    } catch (err) {
      setNotice({
        tone: "error",
        text: err instanceof Error ? err.message : "Aceite não confirmado.",
      });
    } finally {
      setBusy(false);
    }
  }
  async function openCheckout(operation: FinancialOperation) {
    setBusy(true);
    try {
      const response = await api(`/financial-operations/${operation.id}/checkout`, { method: "POST" });
      const value = response.result as { url: string; expiresAt: string; operationId: string };
      setCheckout((current) => ({ ...current, [operation.id]: value }));
      setNotice({ tone: "info", text: "Checkout oficial de teste preparado. Nenhuma cobrança real será feita; a confirmação depende do fornecedor." });
    } catch (err) {
      setNotice({ tone: "error", text: err instanceof Error ? err.message : "O checkout não pôde ser preparado." });
    } finally { setBusy(false); }
  }
  async function reconcilePayment(operation: FinancialOperation) {
    setBusy(true);
    try {
      await api(`/financial-operations/${operation.id}/reconcile`, { method: "POST" });
      setNotice({ tone: "info", text: "Situação consultada no provedor. A cobertura só muda após estado autenticado." });
      await load();
    } catch (err) {
      setNotice({ tone: "error", text: err instanceof Error ? err.message : "A situação não pôde ser consultada." });
    } finally { setBusy(false); }
  }
  async function issueReceiptCode(order: Order) {
    setBusy(true);
    try {
      const b = await api(`/orders/${order.id}/receipt-code`, {
        method: "POST",
        headers: { "If-Match": String(order.version) },
      });
      setReceiptCodes((current) => ({
        ...current,
        [order.id]: b.result as ReceiptChallenge,
      }));
      setNotice({
        tone: "info",
        text: "Código temporário emitido somente nesta sessão do destinatário.",
      });
    } catch (err) {
      setNotice({
        tone: "error",
        text: err instanceof Error ? err.message : "Código não emitido.",
      });
    } finally {
      setBusy(false);
    }
  }
  const fields = (
    label: string,
    value: typeof origin,
    setter: (value: typeof origin) => void,
    kind: "origin" | "destination",
  ) => (
    <fieldset>
      <legend>{label}</legend>
      {(
        [
          "street",
          "number",
          "district",
          "city",
          "state",
          "postalCode",
          "complement",
          "reference",
        ] as const
      ).map((key) => (
        <label key={key}>
          {
            (
              {
                street: "Rua",
                number: "Número",
                district: "Bairro",
                city: "Cidade",
                state: "Estado",
                postalCode: "CEP",
                complement: "Complemento (opcional)",
                reference: "Referência (opcional)",
              } as Record<string, string>
            )[key]
          }
          <input
            aria-label={key}
            required={!["complement", "reference"].includes(key)}
            value={value[key]}
            onChange={(e) => setter({ ...value, [key]: e.target.value, ...(key !== "complement" && key !== "reference" ? { latitude: "", longitude: "", locationConfirmationToken: "" } : {}) })}
          />
        </label>
      ))}
      <div className="journey-actions">
        <button type="button" className="secondary" disabled={searchingLocation !== null} onClick={() => void searchLocation(kind, value)}>
          {searchingLocation === kind ? "Buscando localização…" : "Buscar localização"}
        </button>
        {value.locationConfirmationToken && <span role="status">Local confirmado para cotação.</span>}
      </div>
      {(kind === "origin" ? originCandidates : destinationCandidates).map((candidate) => (
        <div className="candidate-card" key={candidate.confirmationToken}>
          <strong>{candidate.label}</strong>
          <small>Estimativa de localização pelo {candidate.provider}.</small>
          <button type="button" onClick={() => confirmLocation(kind, candidate, value, setter)}>Confirmar este local</button>
        </div>
      ))}
    </fieldset>
  );
  const selectedOrder =
    orders.find((order) => order.id === selectedOrderId) ?? null;
  const canSubmit = Boolean(documentId && patientId && recipientId && unitId);
  const stepNames = [
    "Paciente",
    "Unidade e retirada",
    "Destinatário",
    "Endereço",
    "Documentos",
    "Revisão",
  ];
  const nextStep = () => setStep((value) => Math.min(6, value + 1));
  const previousStep = () => setStep((value) => Math.max(1, value - 1));
  const discardDraft = () => {
    sessionStorage.removeItem(draftKey);
    setPatientId("");
    setRecipientId("");
    setUnitId("");
    setDocumentId("");
    setOrigin({
      street: "",
      number: "",
      district: "",
      city: "",
      state: "",
      postalCode: "",
      latitude: "", longitude: "", complement: "", reference: "", locationConfirmationToken: "",
    });
    setDestination({
      street: "",
      number: "",
      district: "",
      city: "",
      state: "",
      postalCode: "",
      latitude: "", longitude: "", complement: "", reference: "", locationConfirmationToken: "",
    });
    setStep(1);
    setNotice({ tone: "info", text: "Rascunho local descartado." });
  };
  const nextAction = (order: Order, currentQuotes: Quote[]) => {
    const proposal = currentQuotes.find((quoteItem) => quoteItem.status === "PROPOSTO");
    if (order.status === "EM_VERIFICACAO") return "A unidade precisa verificar a retirada antes da cotação.";
    if (proposal) return "Revise a composição e aceite o orçamento antes do vencimento.";
    if (currentQuotes.some((quoteItem) => quoteItem.status === "EXPIRADO")) return "O orçamento venceu; solicite uma nova cotação.";
    if (order.status === "AGUARDANDO_ACEITE") return "Aguarde a atualização das condições do orçamento.";
    if (order.coverageStatus === "PENDENTE_PAGAMENTO") return "A confirmação do pagamento ainda depende do provedor configurado.";
    if (order.coverageStatus === "PAGAMENTO_FALHOU") return "A confirmação de pagamento falhou; aguarde orientação para tentar novamente.";
    return "Acompanhe as atualizações do pedido.";
  };
  const dependencyMessage = unitOptions.length
    ? "Selecione uma unidade com parceria aceita e protocolo de custódia habilitado."
    : "Ainda não há uma unidade de retirada disponível para seleção. O catálogo só mostra unidades ativas com parceria aceita, protocolo de custódia habilitado e cobertura de retorno vigente; nenhum identificador foi inventado.";
  const renderOrderSummary = (order: Order) => (
    <button
      key={order.id}
      className="order-list-item"
      type="button"
      onClick={() => setSelectedOrderId(order.id)}
    >
      <span className="order-list-icon" aria-hidden="true">
        ↗
      </span>
      <span>
        <strong>Pedido de entrega</strong>
        <small>
          {new Date(order.createdAt).toLocaleDateString("pt-BR")} ·{" "}
          {orderStatusLabel(order.status)}
        </small>
      </span>
      <span className="order-status-pill">
        {orderStatusLabel(order.status)}
      </span>
    </button>
  );
  return (
    <section className="patient-orders" aria-label="Pedidos">
      <div className="orders-heading">
        <div>
          <span className="eyebrow dark">PEDIDOS</span>
          <h2>{selectedOrder ? "Detalhes do pedido" : "Solicitar entrega"}</h2>
          <p>
            {selectedOrder
              ? "Acompanhe o que já foi confirmado e o próximo passo."
              : "Organize uma retirada autorizada em uma jornada simples."}
          </p>
        </div>
        {selectedOrder && (
          <button
            className="text-button"
            type="button"
            onClick={() => setSelectedOrderId(null)}
          >
            ← Voltar aos pedidos
          </button>
        )}
      </div>
      {notice && (
        <p className={`feedback ${notice.tone}`} role="alert">
          {notice.text}
        </p>
      )}
      {selectedOrder ? (
        <article className="order-detail-card">
          <div className="order-detail-head">
            <div>
              <span className="card-kicker">PEDIDO DE ENTREGA</span>
              <h3>{orderStatusLabel(selectedOrder.status)}</h3>
              <p>
                Solicitado em{" "}
                {new Date(selectedOrder.createdAt).toLocaleDateString("pt-BR")}
              </p>
            </div>
            <span className="order-status-pill">
              {orderStatusLabel(selectedOrder.coverageStatus)}
            </span>
          </div>
          <div className="order-detail-grid">
            <div>
              <span className="card-kicker">PRÓXIMO PASSO</span>
              <strong>{nextAction(selectedOrder, quotes[selectedOrder.id] ?? [])}</strong>
              <p>
                O sistema só confirma rota, tarifa, cobertura e pagamento quando
                as respectivas APIs retornam sucesso.
              </p>
            </div>
            <div>
              <span className="card-kicker">COBERTURA</span>
              <strong>
                {selectedOrder.coverageStatus === "NAO_CONTRATADA"
                  ? "Ainda não contratada"
                  : orderStatusLabel(selectedOrder.coverageStatus)}
              </strong>
              <p>Nenhuma aprovação ou preço é presumido.</p>
            </div>
          </div>
          {activeOrders.current.includes(selectedOrder.id) && (
            <div className="tracking-card">
              <span className="card-kicker">ACOMPANHAMENTO</span>
              <strong>
                {locations[selectedOrder.id]
                  ? `Última atualização em ${new Date(locations[selectedOrder.id]!.capturedAt).toLocaleString("pt-BR")}`
                  : "A localização ainda não está disponível"}
              </strong>
              <p>
                {locations[selectedOrder.id]
                  ? `${locations[selectedOrder.id]!.stale ? "Atualização desatualizada. " : ""}Precisão aproximada de ${locations[selectedOrder.id]!.accuracyMeters} m.`
                  : "Nenhum marcador será exibido sem posição válida."}
              </p>
            </div>
          )}
          {custodies[selectedOrder.id] && (
            <p className="inline-status">
              Custódia: {orderStatusLabel(custodies[selectedOrder.id]!.status)}.
            </p>
          )}
          {(incidents[selectedOrder.id] ?? []).map((incident) => (
            <p className="inline-status" role="status" key={incident.id}>
              Atenção: {orderStatusLabel(incident.status)}.
            </p>
          ))}
          {assignments[selectedOrder.id] && (
            <div className="assignment-status">
              <strong>Entrega designada</strong>
              <span>
                {drivers[selectedOrder.id]?.displayName} ·{" "}
                {drivers[selectedOrder.id]?.model}
              </span>
              <small>
                Identidade operacional necessária para o acompanhamento.
              </small>
            </div>
          )}
          <div className="order-addresses">
            <div className="card-heading">
              <div>
                <span className="card-kicker">ENDEREÇOS</span>
                <h3>Dados usados na rota</h3>
              </div>
              {detailOrigin && !editingAddresses &&
                !["ACEITA", "RETIRADA", "EM_ENTREGA"].includes(selectedOrder.status) && (
                  <button type="button" className="secondary" onClick={() => setEditingAddresses(true)}>
                    Alterar endereço
                  </button>
                )}
            </div>
            {editingAddresses && detailOrigin && detailDestination ? (
              <form onSubmit={(event) => void updateAddresses(selectedOrder, event)}>
                {fields("Origem", detailOrigin, (value) => setDetailOrigin(value), "origin")}
                {fields("Destino", detailDestination, (value) => setDetailDestination(value), "destination")}
                <p className="dependency-note">
                  Ao salvar, o orçamento atual será substituído e uma nova
                  cotação dependerá das verificações da unidade e das integrações
                  de rota, tarifa e cobertura.
                </p>
                <div className="journey-actions">
                  <button type="button" className="secondary" onClick={() => setEditingAddresses(false)}>
                    Cancelar
                  </button>
                  <button type="submit" disabled={busy}>Salvar endereço</button>
                </div>
              </form>
            ) : detailOrigin && detailDestination ? (
              <div className="address-summary">
                <p><strong>Origem:</strong> {detailOrigin.street}, {detailOrigin.number} · {detailOrigin.city}/{detailOrigin.state} · {detailOrigin.postalCode}</p>
                <p><strong>Destino:</strong> {detailDestination.street}, {detailDestination.number} · {detailDestination.city}/{detailDestination.state} · {detailDestination.postalCode}</p>
              </div>
            ) : (
              <p className="empty-state">Os endereços protegidos ainda não puderam ser carregados.</p>
            )}
          </div>
          {(["RETIRADA", "EM_ENTREGA"] as string[]).includes(
            selectedOrder.status,
          ) && (
            <button
              type="button"
              disabled={busy}
              onClick={() => void issueReceiptCode(selectedOrder)}
            >
              Emitir código de recebimento
            </button>
          )}
          {receiptCodes[selectedOrder.id] && (
            <p role="status">
              Código temporário: <code>{receiptCodes[selectedOrder.id].code}</code> · expira em{" "}
              {new Date(
                receiptCodes[selectedOrder.id].expiresAt,
              ).toLocaleString("pt-BR")}
              .
            </p>
          )}
          <div className="order-history">
            <span className="card-kicker">HISTÓRICO DISPONÍVEL</span>
            <ul>
              <li>
                <strong>Solicitação registrada</strong>
                <span>{new Date(selectedOrder.createdAt).toLocaleString("pt-BR")}</span>
              </li>
              {(quotes[selectedOrder.id] ?? []).map((q) => (
                <li key={`history-${q.id}`}>
                  <strong>Orçamento {orderStatusLabel(q.status).toLowerCase()}</strong>
                  <span>{new Date(q.calculatedAt).toLocaleString("pt-BR")}</span>
                </li>
              ))}
              {(incidents[selectedOrder.id] ?? []).map((incident) => (
                <li key={`history-incident-${incident.id}`}>
                  <strong>Ocorrência: {orderStatusLabel(incident.status)}</strong>
                  <span>{new Date(incident.createdAt).toLocaleString("pt-BR")}</span>
                </li>
              ))}
              <li>
                <strong>Situação atual: {orderStatusLabel(selectedOrder.status)}</strong>
                <span>Próximo passo exibido acima</span>
              </li>
            </ul>
          </div>
          <div className="quote-area">
            <div className="card-heading">
              <div>
                <span className="card-kicker">ORÇAMENTO E COBERTURA</span>
                <h3>Condições verificadas</h3>
              </div>
              <button
                type="button"
                disabled={busy || !["EM_VERIFICACAO", "AGUARDANDO_ACEITE"].includes(selectedOrder.status)}
                onClick={() => void quote(selectedOrder)}
              >
                {quotes[selectedOrder.id]?.some((item) => item.status === "EXPIRADO")
                  ? "Solicitar nova cotação"
                  : "Calcular orçamento"}
              </button>
            </div>
            <label>
              Programa de benefício elegível (opcional)
              <select aria-label="Programa de benefício para cobertura" value={coverageProgramId} onChange={(event) => setCoverageProgramId(event.target.value)}>
                <option value="">Sem benefício confirmado</option>
                {benefitPrograms.map((program) => (
                  <option value={program.id} key={`${program.id}-${program.benefitRequestId}`}>
                    {program.institutionName} · {program.name} ({program.code}) · até {program.percentage}%
                  </option>
                ))}
              </select>
            </label>
            {!benefitPrograms.length && <p className="dependency-note">Nenhum programa de benefício elegível foi confirmado para este paciente. O orçamento será calculado sem subsídio.</p>}
            {(quotes[selectedOrder.id] ?? []).length ? (
              (quotes[selectedOrder.id] ?? []).map((q) => (
                <div className="quote-row" key={q.id}>
                  <strong>
                    {q.status === "PROPOSTO"
                      ? "Orçamento disponível"
                      : orderStatusLabel(q.status)}
                  </strong>
                  <span>
                    Frete {formatMoney(q.grossAmount)} · subsídio{" "}
                    {q.subsidyAmount > 0 ? `-${formatMoney(q.subsidyAmount)}` : "nenhum confirmado"}
                  </span>
                  <small>
                    Rota: {q.routeProvider} · tarifa v{q.tariffVersion} · válido até{" "}
                    {new Date(q.expiresAt).toLocaleString("pt-BR")}
                  </small>
                  <div className="quote-composition">
                    <span>Frete <strong>{formatMoney(q.grossAmount)}</strong></span>
                    <span>Desconto/subsídio <strong>{q.subsidyAmount > 0 ? `-${formatMoney(q.subsidyAmount)}` : formatMoney(0)}</strong></span>
                    <span>Total do paciente <strong>{formatMoney(q.patientAmount)}</strong></span>
                  </div>
                  {q.status === "PROPOSTO" && new Date(q.expiresAt).getTime() > Date.now() && (
                    <button
                      type="button"
                      disabled={busy || !q.cancellationPolicyId}
                      onClick={() => void accept(selectedOrder, q)}
                    >
                      Aceitar orçamento
                    </button>
                  )}
                  {q.status === "PROPOSTO" && !q.cancellationPolicyId && (
                    <p className="dependency-note">Aceite bloqueado: a política de cancelamento ainda não está vigente.</p>
                  )}
                  {q.status === "EXPIRADO" && (
                    <p className="dependency-note">Este orçamento não pode ser aceito. Solicite uma nova cotação.</p>
                  )}
                </div>
              ))
            ) : (
              <p className="empty-state">
                Nenhum orçamento foi calculado para este pedido.
              </p>
            )}
          </div>
          {(financialOperations[selectedOrder.id] ?? []).filter((operation) => operation.type === "COBRANCA").map((operation) => (
            <div className="quote-area payment-status-card" key={operation.id}>
              <div className="card-heading"><div><span className="card-kicker">PAGAMENTO PARTICULAR</span><h3>{formatMoney(operation.amount)} · {operation.currency}</h3></div><strong>{operation.status === "CONFIRMADA" ? "Confirmado" : operation.status === "FALHOU" ? "Recusado" : operation.status === "INCERTA" || operation.status === "RECONCILIAR" ? "Resultado incerto" : operation.status === "PROCESSANDO" ? "Em processamento" : "Pendente"}</strong></div>
              <p>Pagamento de teste — sem cobrança real. O retorno do checkout não confirma a operação; a confirmação depende do estado autenticado do fornecedor.</p>
              <div className="journey-actions">
                {!checkout[operation.id] && operation.status !== "CONFIRMADA" && operation.status !== "FALHOU" && <button type="button" disabled={busy} onClick={() => void openCheckout(operation)}>Preparar checkout oficial</button>}
                {checkout[operation.id] && <a className="button" href={checkout[operation.id].url} target="_blank" rel="noreferrer">Abrir checkout oficial</a>}
                {operation.status !== "CONFIRMADA" && <button type="button" className="secondary" disabled={busy} onClick={() => void reconcilePayment(operation)}>Atualizar situação</button>}
              </div>
              {operation.status === "INCERTA" || operation.status === "RECONCILIAR" ? <p className="dependency-note">A confirmação não foi presumida. Consulte novamente a mesma tentativa; não será criada outra cobrança automaticamente.</p> : null}
            </div>
          ))}
        </article>
      ) : (
        <>
          <div className="draft-notice" role="status">
            <div>
              <strong>Rascunho local deste navegador</strong>
              <p>O preenchimento abaixo ainda não foi enviado ao servidor. Ele sobrevive ao recarregar este navegador, não é um pedido e não fica disponível em outra conta.</p>
            </div>
            <button type="button" className="secondary" disabled={!hasDraft} onClick={discardDraft}>
              Descartar rascunho
            </button>
          </div>
          <div className="orders-layout">
            <form className="order-journey" onSubmit={create}>
              <div className="order-stepper" aria-label="Etapas da solicitação">
                {stepNames.map((name, index) => (
                  <button
                    type="button"
                    className={
                      step === index + 1
                        ? "is-current"
                        : step > index + 1
                          ? "is-done"
                          : ""
                    }
                    key={name}
                    onClick={() => setStep(index + 1)}
                  >
                    <span>{index + 1}</span>
                    {name}
                  </button>
                ))}
              </div>
              {step === 1 && (
                <div className="journey-step">
                  <span className="card-kicker">ETAPA 1 DE 6</span>
                  <h3>Quem vai receber o cuidado?</h3>
                  <p>Escolha apenas perfis que sua conta pode representar.</p>
                  <label>
                    Paciente
                    <select
                      aria-label="Paciente representado"
                      required
                      value={patientId}
                      onChange={(e) => setPatientId(e.target.value)}
                    >
                      <option value="" disabled>
                        Selecione um paciente autorizado
                      </option>
                      {patientOptions.map((option) => (
                        <option value={option.id} key={option.id}>
                          {option.label}
                        </option>
                      ))}
                    </select>
                  </label>
                  {!patientOptions.length && (
                    <p className="empty-state">
                      Nenhum perfil de paciente autorizado está disponível para
                      esta conta.
                    </p>
                  )}
                  <button
                    type="button"
                    onClick={nextStep}
                    disabled={!patientId}
                  >
                    Continuar
                  </button>
                </div>
              )}
              {step === 2 && (
                <div className="journey-step">
                  <span className="card-kicker">ETAPA 2 DE 6</span>
                  <h3>De onde será a retirada?</h3>
                  <p>
                    O pedido só pode seguir com uma unidade real e um protocolo
                    de custódia habilitado.
                  </p>
                  <label>
                    Unidade de retirada
                    <select
                      aria-label="Unidade de retirada"
                      required
                      value={unitId}
                      onChange={(e) => setUnitId(e.target.value)}
                      disabled={!unitOptions.length}
                    >
                      <option value="">
                        {unitOptions.length
                          ? "Selecione uma unidade"
                          : "Nenhuma unidade disponível"}
                      </option>
                      {unitOptions.map((option) => (
                        <option value={option.id} key={option.id}>
                          {option.label}
                        </option>
                      ))}
                    </select>
                  </label>
                  <p className="dependency-note">
                    {dependencyMessage} Seus dados preenchidos ficam salvos
                    neste navegador e você pode retomar quando uma unidade
                    estiver configurada.
                  </p>
                  <div className="journey-actions">
                    <button
                      type="button"
                      className="secondary"
                      onClick={previousStep}
                    >
                      Voltar
                    </button>
                    <button type="button" onClick={nextStep} disabled={!unitId}>
                      Continuar
                    </button>
                  </div>
                </div>
              )}
              {step === 3 && (
                <div className="journey-step">
                  <span className="card-kicker">ETAPA 3 DE 6</span>
                  <h3>Quem vai receber?</h3>
                  <p>
                    O destinatário precisa ter autorização vigente para
                    recebimento.
                  </p>
                  <label>
                    Destinatário autorizado
                    <select
                      aria-label="Destinatário autorizado"
                      required
                      value={recipientId}
                      onChange={(e) => setRecipientId(e.target.value)}
                    >
                      <option value="" disabled>
                        Selecione uma pessoa autorizada
                      </option>
                      {recipientOptions.map((option) => (
                        <option value={option.id} key={option.id}>
                          {option.label}
                        </option>
                      ))}
                    </select>
                  </label>
                  {!recipientOptions.length && (
                    <p className="empty-state">
                      Nenhum destinatário com autorização vigente foi
                      encontrado.
                    </p>
                  )}
                  <div className="journey-actions">
                    <button
                      type="button"
                      className="secondary"
                      onClick={previousStep}
                    >
                      Voltar
                    </button>
                    <button
                      type="button"
                      onClick={nextStep}
                      disabled={!recipientId}
                    >
                      Continuar
                    </button>
                  </div>
                </div>
              )}
              {step === 4 && (
                <div className="journey-step">
                  <span className="card-kicker">ETAPA 4 DE 6</span>
                  <h3>Informe os endereços</h3>
                  <p>
                    Esses dados ficam protegidos e são usados somente para
                    calcular a rota autorizada.
                  </p>
                  {fields("Origem", origin, setOrigin, "origin")}
                  {fields("Destino", destination, setDestination, "destination")}
                  <div className="journey-actions">
                    <button
                      type="button"
                      className="secondary"
                      onClick={previousStep}
                    >
                      Voltar
                    </button>
                    <button type="button" onClick={nextStep}>
                      Continuar
                    </button>
                  </div>
                </div>
              )}
              {step === 5 && (
                <div className="journey-step">
                  <span className="card-kicker">ETAPA 5 DE 6</span>
                  <h3>Anexe a autorização</h3>
                  <p>
                    O arquivo é privado e permanece pendente até a verificação
                    da unidade.
                  </p>
                  <label>
                    Evidência de autorização de retirada
                    <input
                      aria-label="Evidência de autorização de retirada"
                      type="file"
                      accept="application/pdf,image/png,image/jpeg"
                      onChange={upload}
                      required={!documentId}
                    />
                  </label>
                  <p
                    className={documentId ? "upload-confirmed" : "empty-state"}
                  >
                    {documentId
                      ? "Documento anexado nesta sessão."
                      : "Nenhum documento anexado ainda."}
                  </p>
                  <div className="journey-actions">
                    <button
                      type="button"
                      className="secondary"
                      onClick={previousStep}
                    >
                      Voltar
                    </button>
                    <button
                      type="button"
                      onClick={nextStep}
                      disabled={!documentId}
                    >
                      Continuar
                    </button>
                  </div>
                </div>
              )}
              {step === 6 && (
                <div className="journey-step">
                  <span className="card-kicker">ETAPA 6 DE 6</span>
                  <h3>Revise antes de solicitar</h3>
                  <div className="review-grid">
                    <span>Paciente</span>
                    <strong>
                      {patientOptions.find((option) => option.id === patientId)
                        ?.label ?? "Não selecionado"}
                    </strong>
                    <span>Destinatário</span>
                    <strong>
                      {recipientOptions.find(
                        (option) => option.id === recipientId,
                      )?.label ?? "Não selecionado"}
                    </strong>
                    <span>Retirada</span>
                    <strong>
                      {unitId ? "Unidade selecionada" : "Aguardando unidade"}
                    </strong>
                    <span>Documento</span>
                    <strong>{documentId ? "Anexado" : "Pendente"}</strong>
                  </div>
                  <p className="dependency-note">
                    A solicitação só será considerada concluída depois que o
                    backend confirmar a persistência. Rota, cobertura, tarifa e
                    política não são presumidas.
                  </p>
                  <div className="journey-actions">
                    <button
                      type="button"
                      className="secondary"
                      onClick={previousStep}
                    >
                      Voltar
                    </button>
                    <button type="submit" disabled={busy || !canSubmit}>
                      {busy ? "Enviando…" : "Solicitar entrega"}
                    </button>
                  </div>
                </div>
              )}
            </form>
            <aside className="order-side-note">
              <span className="card-kicker">COMO FUNCIONA</span>
              <h3>Você decide em cada etapa.</h3>
              <p>
                O pedido é criado somente após a revisão. Enquanto isso, o
                preenchimento fica disponível para retomada neste navegador.
              </p>
              <div>
                <strong>Sem dados fictícios</strong>
                <small>
                  Unidades, valores e aprovações aparecem apenas quando vierem
                  das APIs reais.
                </small>
              </div>
            </aside>
          </div>
          <section
            className="order-history"
            aria-labelledby="order-history-title"
          >
            <div className="card-heading">
              <div>
                <span className="card-kicker">HISTÓRICO</span>
                <h3 id="order-history-title">Pedidos recentes</h3>
              </div>
            </div>
            {orders.length ? (
              orders.map(renderOrderSummary)
            ) : (
              <div className="empty-state">
                <span className="empty-icon" aria-hidden="true">
                  ○
                </span>
                <strong>Você ainda não tem pedidos.</strong>
                <p>
                  Quando uma solicitação for persistida, ela aparecerá aqui com
                  o próximo passo.
                </p>
              </div>
            )}
          </section>
        </>
      )}
    </section>
  );
}

function UnitCatalogPanel({ session }: { session: Tokens }) {
  const [units, setUnits] = useState<CatalogUnit[]>([]);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [name, setName] = useState("");
  const [city, setCity] = useState("");
  const [state, setState] = useState("");
  const [selected, setSelected] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [evidence, setEvidence] = useState("");
  const [partnershipNote, setPartnershipNote] = useState("");
  const [protocolNumber, setProtocolNumber] = useState("1");
  const [pickupInstructions, setPickupInstructions] = useState("");
  const [handoverEvidence, setHandoverEvidence] = useState("");
  const [returnInstructions, setReturnInstructions] = useState("");
  const [returnRequired, setReturnRequired] = useState(true);
  const [coverage, setCoverage] = useState(false);
  async function api(path: string, init: RequestInit = {}) {
    return parse(
      await fetch(`/api/v1${path}`, {
        ...init,
        headers: {
          Authorization: `Bearer ${session.accessToken}`,
          "Content-Type": "application/json",
          ...(init.headers ?? {}),
        },
      }),
    );
  }
  async function load() {
    try {
      const result = await api("/admin/units");
      setUnits(result.result as CatalogUnit[]);
    } catch (error) {
      setNotice({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "Não foi possível carregar o catálogo.",
      });
    }
  }
  useEffect(() => {
    void load();
  }, []);
  async function mutate(action: () => Promise<unknown>, success: string) {
    setBusy(true);
    setNotice(null);
    try {
      await action();
      await load();
      setNotice({ tone: "info", text: success });
    } catch (error) {
      setNotice({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "Não foi possível concluir a alteração.",
      });
    } finally {
      setBusy(false);
    }
  }
  return (
    <section
      className="patient-orders catalog-panel"
      aria-label="Unidades e protocolos"
    >
      <div className="orders-heading">
        <div>
          <span className="eyebrow dark">ADMINISTRAÇÃO OPERACIONAL</span>
          <h2>Unidades e protocolos</h2>
          <p>
            Cadastre o catálogo, registre a comprovação de parceria e habilite a
            operação em etapas separadas.
          </p>
        </div>
      </div>
      <MfaAccess session={session} />
      {notice && (
        <p className={`feedback ${notice.tone}`} role="alert">
          {notice.text}
        </p>
      )}
      <div className="orders-layout">
        <form
          className="order-journey"
          onSubmit={(event) => {
            event.preventDefault();
            void mutate(
              () =>
                api(editingId ? `/admin/units/${editingId}` : "/admin/units", {
                  method: editingId ? "PUT" : "POST",
                  body: JSON.stringify({
                    name,
                    city,
                    state: state.toUpperCase(),
                  }),
                }),
              editingId
                ? "Unidade atualizada."
                : "Unidade cadastrada como CADASTRADA. O cadastro não homologa a parceria.",
            );
          }}
        >
          <span className="card-kicker">{editingId ? "EDITAR CADASTRO" : "NOVO CADASTRO"}</span>
          <h3>{editingId ? "Editar unidade" : "Registrar unidade"}</h3>
          <p>
            Informe somente dados institucionais recebidos. Nenhum hospital ou
            aceite é criado automaticamente.
          </p>
          <label>
            Nome da unidade
            <input
              required
              maxLength={200}
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </label>
          <label>
            Cidade
            <input
              required
              maxLength={120}
              value={city}
              onChange={(e) => setCity(e.target.value)}
            />
          </label>
          <label>
            UF
            <input
              required
              minLength={2}
              maxLength={2}
              value={state}
              onChange={(e) => setState(e.target.value)}
            />
          </label>
          <button disabled={busy}>{editingId ? "Salvar alterações" : "Cadastrar unidade"}</button>
        </form>
        <aside className="order-side-note">
          <span className="card-kicker">REGRA OPERACIONAL</span>
                  <h3>Quatro etapas verificáveis.</h3>
          <p>
            Cadastro identifica a unidade. A parceria guarda a referência da
            comprovação e o aceite explícito. O protocolo só pode ser habilitado
            com unidade ativa, parceria aceita e cobertura de retorno
            confirmada.
          </p>
          <strong>Sem parceiro presumido</strong>
          <small>
            Ative apenas registros cuja comprovação institucional exista fora
            deste sistema.
          </small>
        </aside>
      </div>
      <section className="order-history">
        <div className="card-heading">
          <div>
                  <span className="card-kicker">CATÁLOGO</span>
            <h3>Unidades cadastradas</h3>
          </div>
        </div>
        {units.length ? (
          units.map((unit) => (
            <article className="catalog-unit" key={unit.id}>
              <div className="catalog-unit-head">
                <div>
                  <h3>{unit.name}</h3>
                  <p>
                    {unit.city}/{unit.state}
                  </p>
                </div>
                <span className="order-status-pill">
                  {unit.registrationStatus}
                </span>
              </div>
              <p>
                {unit.partnership
                  ? `Parceria: ${unit.partnership.status}`
                  : "Parceria ainda não registrada"}{" "}
                · {unit.protocols.length} protocolo(s)
              </p>
              <div className="catalog-requirements"><strong>Requisitos para aparecer no seletor do paciente</strong>{unit.pendingRequirements.length ? <ul>{unit.pendingRequirements.map((requirement) => <li key={requirement}>{requirement}</li>)}</ul> : <p className="inline-status">Todos os requisitos operacionais estão atendidos.</p>}</div>
              <div className="catalog-actions">
                <button
                  type="button"
                  disabled={busy}
                  onClick={() =>
                    void mutate(
                      () =>
                        api(
                          `/admin/units/${unit.id}/activation?active=${unit.registrationStatus !== "ATIVA"}`,
                          { method: "POST" },
                        ),
                      unit.registrationStatus === "ATIVA"
                        ? "Unidade desativada."
                        : "Unidade ativada.",
                    )
                  }
                >
                  {unit.registrationStatus === "ATIVA"
                    ? "Desativar unidade"
                    : "Ativar unidade"}
                </button>
                <button
                  type="button"
                  className="secondary"
                  onClick={() => {
                    setEditingId(unit.id);
                    setName(unit.name);
                    setCity(unit.city);
                    setState(unit.state);
                    setSelected(selected === unit.id ? null : unit.id);
                  }}
                >
 Editar cadastro · Gerenciar parceria e protocolo
                </button>
              </div>
              {selected === unit.id && (
                <div className="catalog-editor">
                  <div>
                    <h4>Comprovação de parceria</h4>
                    <p>
                      Estado atual:{" "}
                      {unit.partnership?.status ?? "não registrado"}
                    </p>
                    <label>
                      Referência da comprovação
                      <input
                        value={evidence}
                        onChange={(e) => setEvidence(e.target.value)}
                        placeholder="Protocolo ou referência fornecida"
                      />
                    </label>
                    <label>
                      Observação
                      <textarea
                        value={partnershipNote}
                        onChange={(e) => setPartnershipNote(e.target.value)}
                      />
                    </label>
                    <div className="catalog-actions">
                      <button
                        type="button"
                        disabled={busy || !evidence.trim()}
                        onClick={() =>
                          void mutate(
                            () =>
                              api(`/admin/units/${unit.id}/partnership`, {
                                method: "POST",
                                body: JSON.stringify({
                                  evidenceReference: evidence,
                                  note: partnershipNote || null,
                                  status: "ACEITA",
                                }),
                              }),
                            "Aceite de parceria registrado. Isso não habilita o protocolo sozinho.",
                          )
                        }
                      >
                        Registrar aceite
                      </button>
                      <button
                        type="button"
                        className="secondary"
                        disabled={busy || !evidence.trim()}
                        onClick={() =>
                          void mutate(
                            () =>
                              api(`/admin/units/${unit.id}/partnership`, {
                                method: "POST",
                                body: JSON.stringify({
                                  evidenceReference: evidence,
                                  note: partnershipNote || null,
                                  status: "REVOGADA",
                                }),
                              }),
                            "Parceria revogada.",
                          )
                        }
                      >
                        Revogar
                      </button>
                    </div>
                  </div>
                  <div>
                      <h4>Configuração do protocolo</h4>
                    <label>
                      Número
                      <input
                        type="number"
                        min="1"
                        value={protocolNumber}
                        onChange={(e) => setProtocolNumber(e.target.value)}
                      />
                    </label>
                      <p className="field-help">Esses campos descrevem o procedimento que a unidade aceitou. O formato técnico é montado pelo servidor.</p>
                      <label>Instruções para retirada<textarea required maxLength={1000} value={pickupInstructions} onChange={(e) => setPickupInstructions(e.target.value)} placeholder="O que a pessoa deve apresentar e como a retirada será conferida" /></label>
                      <label>Evidência na entrega<textarea required maxLength={200} value={handoverEvidence} onChange={(e) => setHandoverEvidence(e.target.value)} placeholder="Ex.: assinatura ou comprovante definido pela unidade" /></label>
                      <label>Instruções de retorno<textarea required maxLength={1000} value={returnInstructions} onChange={(e) => setReturnInstructions(e.target.value)} placeholder="Como o retorno será recebido e comprovado" /></label>
                      <label className="checkbox-label"><input type="checkbox" checked={returnRequired} onChange={(e) => setReturnRequired(e.target.checked)} /> Retorno faz parte deste protocolo</label>
                    <label className="checkbox-label">
                      <input
                        type="checkbox"
                        checked={coverage}
                        onChange={(e) => setCoverage(e.target.checked)}
                      />{" "}
                      Cobertura de retorno confirmada
                    </label>
                    <button
                      type="button"
                      disabled={busy}
                      onClick={() =>
                        void mutate(
                          () =>
                            api(`/admin/units/${unit.id}/protocols`, {
                              method: "POST",
                            body: JSON.stringify({
                              number: Number(protocolNumber),
                              pickupInstructions,
                              handoverEvidence,
                              returnInstructions,
                              returnRequired,
                              returnCoverageConfirmed: coverage,
                                startsAt: new Date().toISOString(),
                                endsAt: null,
                              }),
                            }),
                          "Protocolo criado como SUSPENSO.",
                        )
                      }
                    >
                      Cadastrar protocolo
                    </button>
                    <div className="catalog-protocols">
                      {unit.protocols.map((protocol) => (
                        <div key={protocol.id}>
                          <strong>Protocolo {protocol.number}</strong>
                          <span>
                            {protocol.status} ·{" "}
                            {protocol.returnCoverageConfirmed
                              ? "retorno confirmado"
                              : "retorno pendente"}
                          </span>
                          <button
                            type="button"
                            disabled={busy}
                            onClick={() =>
                              void mutate(
                                () =>
                                  api(
                                    `/admin/units/${unit.id}/protocols/${protocol.id}/activation?active=${protocol.status !== "HABILITADO"}`,
                                    { method: "POST" },
                                  ),
                                protocol.status === "HABILITADO"
                                  ? "Protocolo suspenso."
                                  : "Protocolo habilitado.",
                              )
                            }
                          >
                            {protocol.status === "HABILITADO"
                              ? "Suspender"
                              : "Habilitar"}
                          </button>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              )}
            </article>
          ))
        ) : (
          <div className="empty-state">
            <strong>Catálogo sem unidades.</strong>
            <p>
              Cadastre a primeira unidade com autorização administrativa real
              para que ela possa avançar pelas etapas.
            </p>
          </div>
        )}
      </section>
    </section>
  );
}

function MfaAccess({ session }: { session: Tokens }) {
  const [status, setStatus] = useState<{
    enrolled: boolean;
    verifiedForSession: boolean;
  } | null>(null);
  const [secret, setSecret] = useState("");
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  async function api(path: string, init: RequestInit = {}) {
    return parse(
      await fetch(`/api/v1/me/mfa${path}`, {
        ...init,
        headers: {
          Authorization: `Bearer ${session.accessToken}`,
          "Content-Type": "application/json",
          ...(init.headers ?? {}),
        },
      }),
    );
  }
  async function load() {
    setStatus(
      (await api("")).result as {
        enrolled: boolean;
        verifiedForSession: boolean;
      },
    );
  }
  useEffect(() => {
    void load().catch((e) =>
      setNotice({ tone: "error", text: (e as Error).message }),
    );
  }, [session.accessToken]);
  async function act(
    e: FormEvent<HTMLFormElement>,
    kind: "enroll" | "confirm" | "verify",
  ) {
    e.preventDefault();
    const form = e.currentTarget;
    setBusy(true);
    setNotice(null);
    try {
      const data = new FormData(form);
      const response =
        kind === "enroll"
          ? await api("/totp/enrollment", {
              method: "POST",
              body: JSON.stringify({ password: data.get("password") }),
            })
          : await api(
              kind === "confirm" ? "/totp/confirmation" : "/verification",
              {
                method: "POST",
                body: JSON.stringify({ code: data.get("code") }),
              },
            );
      if (kind === "enroll")
        setSecret((response.result as { secret: string }).secret);
      await load();
      if (kind !== "enroll") window.dispatchEvent(new Event("mfa-elevated"));
      setNotice({
        tone: "info",
        text:
          kind === "enroll"
            ? "Fator emitido. Registre-o no autenticador e confirme um código."
            : "MFA validado temporariamente nesta sessão.",
      });
      form.reset();
    } catch (error) {
      setNotice({
        tone: "error",
        text: error instanceof Error ? error.message : "MFA não confirmado.",
      });
    } finally {
      setBusy(false);
    }
  }
  return (
    <section
      className="mfa-strip"
      aria-label="Segurança de ações privilegiadas"
    >
      <div>
        <span className="eyebrow dark">MFA</span>
        <h2>
          {status?.verifiedForSession
            ? "Sessão elevada"
            : "Elevação necessária"}
        </h2>
        <p>
          {status?.enrolled
            ? "O fator está configurado; valide um código antes de decisões privilegiadas."
            : "Configure o fator com sua senha atual. Não há código padrão nem bypass local."}
        </p>
      </div>
      {!status?.enrolled && !secret ? (
        <form onSubmit={(e) => void act(e, "enroll")}>
          <label>
            Senha atual
            <input
              name="password"
              type="password"
              autoComplete="current-password"
              required
            />
          </label>
          <button disabled={busy}>Configurar TOTP</button>
        </form>
      ) : (
        <form
          onSubmit={(e) =>
            void act(e, status?.verifiedForSession ? "verify" : "confirm")
          }
        >
          <label>
            Código TOTP
            <input
              name="code"
              inputMode="numeric"
              pattern="[0-9]{6}"
              autoComplete="one-time-code"
              required
            />
          </label>
          <button disabled={busy}>Validar sessão</button>
        </form>
      )}
      {secret && !status?.verifiedForSession && (
        <p className="secret-once">
          Segredo exibido uma vez: <code>{secret}</code>
        </p>
      )}
      {notice && (
        <p className={`feedback ${notice.tone}`} role="alert">
          {notice.text}
        </p>
      )}
    </section>
  );
}

function DeliveryWorkPanel({ session }: { session: Tokens }) {
  const [offers, setOffers] = useState<Offer[]>([]);
  const [assignments, setAssignments] = useState<TaskAssignment[]>([]);
  const [vehicles, setVehicles] = useState<VehicleLink[]>([]);
  const [documents, setDocuments] = useState<Evidence[]>([]);
  const [addresses, setAddresses] = useState<
    Record<string, { origin: TaskAddress; destination: TaskAddress }>
  >({});
  const [custody, setCustody] = useState<Record<string, Custody>>({});
  const [payouts, setPayouts] = useState<Record<string, Payout>>({});
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  const [deliverySection, setDeliverySection] = useState<"offers" | "active" | "history">("offers");
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);
  const [tracking, setTracking] = useState<BrowserTrackingState>({
    taskId: null,
    status: "idle",
    message: "A localização web não está compartilhada.",
    pending: 0,
    lastPosition: null,
  });
  const trackingWatchRef = useRef<number | null>(null);
  const trackingTaskRef = useRef<TaskAssignment | null>(null);
  const trackingSequenceRef = useRef(1);
  const trackingRequestsRef = useRef(new Set<AbortController>());
  async function api(path: string, init: RequestInit = {}) {
    return parse(
      await fetch(`/api/v1${path}`, {
        ...init,
        headers: {
          Authorization: `Bearer ${session.accessToken}`,
          ...(init.body ? { "Content-Type": "application/json" } : {}),
          ...(init.headers ?? {}),
        },
      }),
    );
  }
  async function load() {
    const [o, a, v, d] = await Promise.all([
      api("/offers").catch((error) => {
        setNotice({ tone: "error", text: error.message });
        return { result: { items: [] as Offer[] } };
      }),
      api("/me/assignments").catch(() => ({ result: [] as TaskAssignment[] })),
      api("/me/vehicle-links").catch(() => ({ result: [] as VehicleLink[] })),
      api("/me/deliverer/documents").catch(() => ({ result: [] as Evidence[] })),
    ]);
    setOffers((o.result as { items: Offer[] }).items);
    const tasks = a.result as TaskAssignment[];
    setAssignments(tasks);
    setVehicles(v.result as VehicleLink[]);
    setDocuments(d.result as Evidence[]);
    const states = await Promise.all(
      tasks.map(
        async (task) =>
          [
            task.orderId,
            (await api(`/orders/${task.orderId}/custody`).catch(() => null))
              ?.result,
            (await api(`/orders/${task.orderId}/payout`).catch(() => null))
              ?.result,
          ] as const,
      ),
    );
    setCustody((current) => ({
      ...current,
      ...Object.fromEntries(
        states.filter(([, value]) => value).map(([id, value]) => [id, value]),
      ),
    }));
    setPayouts(
      Object.fromEntries(
        states
          .filter(([, , value]) => value)
          .map(([id, , value]) => [id, value]),
      ),
    );
  }
  useEffect(() => {
    void load().catch((e) =>
      setNotice({ tone: "error", text: (e as Error).message }),
    );
  }, [session.accessToken]);
  useEffect(() => {
    return () => {
      if (trackingWatchRef.current !== null && "geolocation" in navigator) {
        navigator.geolocation.clearWatch(trackingWatchRef.current);
      }
      trackingWatchRef.current = null;
      trackingTaskRef.current = null;
      trackingRequestsRef.current.forEach((request) => request.abort());
      trackingRequestsRef.current.clear();
    };
  }, []);
  useEffect(() => {
    if (!tracking.taskId) return;
    const task = assignments.find((value) => value.id === tracking.taskId);
    const state = task ? custody[task.orderId] : null;
    if (!task || task.status !== "ATIVA" || state?.status !== "ATIVA") {
      stopBrowserTracking(
        "A autorização operacional deixou de ser válida; a captura foi encerrada e os envios pendentes foram descartados.",
      );
    }
  }, [assignments, custody, tracking.taskId]);
  async function run(action: () => Promise<void>, success: string) {
    setBusy(true);
    setNotice(null);
    try {
      await action();
      await load();
      setNotice({ tone: "info", text: success });
    } catch (error) {
      setNotice({
        tone: "error",
        text:
          error instanceof Error ? error.message : "A ação não foi confirmada.",
      });
    } finally {
      setBusy(false);
    }
  }
  async function accept(offer: Offer) {
    const vehicle = vehicles.find(
      (v) =>
        v.status === "APROVADO" &&
        (!v.validUntil ||
          v.validUntil >= new Date().toISOString().slice(0, 10)),
    );
    if (!vehicle) {
      setNotice({
        tone: "error",
        text: "Nenhum vínculo de veículo aprovado e vigente está disponível. O cadastro continua navegável, mas a oferta não pode ser aceita.",
      });
      return;
    }
    await run(async () => {
      await api(`/orders/${offer.orderId}/assignment`, {
        method: "POST",
        headers: {
          "If-Match": String(offer.orderVersion),
          "Idempotency-Key": crypto.randomUUID(),
        },
        body: JSON.stringify({
          vehicleLinkId: vehicle.id,
          acceptedCancellationPolicyId: offer.cancellationPolicyId,
        }),
      });
    }, "Oferta aceita e tarefa atribuída. Endereços completos agora são restritos a esta designação.");
  }
  async function open(task: TaskAssignment) {
    await run(async () => {
      const response = await api(`/orders/${task.orderId}/addresses`);
      setAddresses((value) => ({
        ...value,
        [task.orderId]: response.result as {
          origin: TaskAddress;
          destination: TaskAddress;
        },
      }));
    }, "Endereços liberados para a tarefa atribuída.");
  }
  async function act(
    task: TaskAssignment,
    action: "pickup" | "start-delivery" | "deliver" | "incidents",
    body?: object,
  ) {
    const version = custody[task.orderId]?.version ?? task.version;
    await run(
      async () => {
        const response = await api(`/orders/${task.orderId}/${action}`, {
          method: "POST",
          headers: {
            "If-Match": String(version),
            "Idempotency-Key": crypto.randomUUID(),
          },
          body: body ? JSON.stringify(body) : undefined,
        });
        if (action !== "incidents" && response.result) {
          setCustody((current) => ({
            ...current,
            [task.orderId]: response.result as Custody,
          }));
        }
        if (action === "deliver") {
          stopBrowserTracking(
            "Entrega encerrada; a captura foi interrompida e os envios pendentes foram descartados.",
          );
        }
      },
      action === "pickup"
        ? "Retirada e custódia registradas."
        : action === "start-delivery"
          ? "Entrega iniciada."
          : action === "deliver"
            ? "Entrega confirmada pelo código do destinatário."
            : "Ocorrência registrada; nenhum desfecho foi presumido.",
    );
  }
  async function trackingPreflight(task: TaskAssignment) {
    const response = await fetch(`/api/v1/orders/${task.orderId}/locations/latest`, {
      headers: { Authorization: `Bearer ${session.accessToken}` },
    });
    if (response.ok || response.status === 404) return;
    const body = await response.json().catch(() => ({})) as { code?: string; message?: string };
    if (body.code === "POLICY_UNDEFINED") {
      throw new Error("A localização web permanece bloqueada até existir uma política de retenção aprovada neste ambiente.");
    }
    throw new Error(body.message ?? "A designação não está autorizada para localização web.");
  }
  function stopBrowserTracking(message = "A localização web foi interrompida.") {
    if (trackingWatchRef.current !== null && "geolocation" in navigator) {
      navigator.geolocation.clearWatch(trackingWatchRef.current);
    }
    trackingWatchRef.current = null;
    trackingTaskRef.current = null;
    trackingRequestsRef.current.forEach((request) => request.abort());
    trackingRequestsRef.current.clear();
    setTracking({ taskId: null, status: "stopped", message, pending: 0, lastPosition: null });
  }
  function trackingError(error: GeolocationPositionError) {
    if (error.code === error.PERMISSION_DENIED) {
      stopBrowserTracking("Permissão de localização negada. Nenhum ponto foi compartilhado.");
      setTracking((current) => ({ ...current, status: "denied" }));
    } else if (error.code === error.POSITION_UNAVAILABLE) {
      setTracking((current) => ({ ...current, status: "error", message: "A posição está indisponível neste navegador ou dispositivo; nenhum ponto novo foi enviado." }));
    } else {
      setTracking((current) => ({ ...current, status: "error", message: "O navegador demorou para obter a posição; tente novamente quando a localização estiver disponível." }));
    }
  }
  async function sendBrowserPosition(task: TaskAssignment, position: GeolocationPosition) {
    if (trackingTaskRef.current?.id !== task.id) return;
    const capturedAt = new Date(position.timestamp).toISOString();
    const accuracyMeters = position.coords.accuracy;
    const lowAccuracy = accuracyMeters > 100;
    const controller = new AbortController();
    trackingRequestsRef.current.add(controller);
    setTracking((current) => ({
      ...current,
      pending: trackingRequestsRef.current.size,
      lastPosition: { capturedAt, accuracyMeters, stale: Date.now() - position.timestamp > 60_000, lowAccuracy },
    }));
    try {
      const response = await fetch(`/api/v1/orders/${task.orderId}/locations`, {
        method: "POST",
        signal: controller.signal,
        headers: { Authorization: `Bearer ${session.accessToken}`, "Content-Type": "application/json" },
        body: JSON.stringify({
          assignmentId: task.id,
          sequence: trackingSequenceRef.current++,
          capturedAt,
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          accuracyMeters,
        }),
      });
      if (!response.ok) {
        const body = await response.json().catch(() => ({})) as { code?: string; message?: string };
        if (["POLICY_UNDEFINED", "TRACKING_INACTIVE", "NOT_FOUND"].includes(body.code ?? "")) {
          stopBrowserTracking("O backend deixou de autorizar esta captura; ela foi encerrada e nenhum envio pendente foi mantido.");
          return;
        }
        setTracking((current) => ({ ...current, status: "error", message: body.message ?? "A posição não foi aceita pelo backend; ela não foi mantida em fila." }));
        return;
      }
      setTracking((current) => ({ ...current, status: "active", message: lowAccuracy ? "Captura ativa, mas a precisão está acima de 100 m." : "Captura ativa; a última posição foi aceita pelo backend." }));
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") return;
      setTracking((current) => ({ ...current, status: "error", message: "Sem conexão com o backend; esta posição não foi enfileirada e será descartada ao interromper." }));
    } finally {
      trackingRequestsRef.current.delete(controller);
      setTracking((current) => ({ ...current, pending: trackingRequestsRef.current.size }));
    }
  }
  async function startBrowserTracking(task: TaskAssignment) {
    const state = custody[task.orderId];
    if (task.status !== "ATIVA" || state?.status !== "ATIVA") {
      setNotice({ tone: "error", text: "A localização só pode começar com designação vigente e custódia ativa." });
      return;
    }
    if (!("geolocation" in navigator)) {
      setTracking({ taskId: null, status: "error", message: "Este navegador não oferece localização em primeiro plano.", pending: 0, lastPosition: null });
      return;
    }
    if (trackingWatchRef.current !== null) stopBrowserTracking("A captura anterior foi interrompida.");
    setTracking({ taskId: task.id, status: "starting", message: "Verificando a autorização da tarefa antes de pedir a permissão do navegador…", pending: 0, lastPosition: null });
    try {
      await trackingPreflight(task);
      trackingTaskRef.current = task;
      trackingSequenceRef.current = 1;
      const watchId = navigator.geolocation.watchPosition(
        (position) => void sendBrowserPosition(task, position),
        trackingError,
        { enableHighAccuracy: true, maximumAge: 15_000, timeout: 15_000 },
      );
      trackingWatchRef.current = watchId;
      setTracking((current) => ({ ...current, status: "active", message: "Captura ativa; aguardando a primeira posição aceita pelo backend." }));
    } catch (error) {
      setTracking({ taskId: null, status: "error", message: error instanceof Error ? error.message : "A captura não foi autorizada.", pending: 0, lastPosition: null });
    }
  }
  return (
    <section className="family" aria-label="Ofertas e entregas">
      <div className="section-heading">
        <span className="eyebrow dark">ENTREGAS</span>
        <h2>Ofertas, custódia e histórico</h2>
        <p>
          A web cobre operações adequadas ao navegador. GPS contínuo permanece
          no aplicativo móvel porque depende de permissão e sensor do
          dispositivo.
        </p>
      </div>
      {notice && (
        <p className={`feedback ${notice.tone}`} role="alert">
          {notice.text}
        </p>
      )}
      <div className="tabs" role="tablist" aria-label="Ofertas e entregas">
        {([["offers", "Ofertas"], ["active", "Entrega atribuída"], ["history", "Histórico"]] as const).map(([value, label]) => (
          <button type="button" className="tab" role="tab" aria-selected={deliverySection === value} key={value} onClick={() => { setDeliverySection(value); setSelectedTaskId(null); }}>
            {label}
          </button>
        ))}
      </div>
      <div className="registers">
        <article hidden={deliverySection !== "offers"}>
          <h3>Ofertas elegíveis</h3>
          {offers.length ? (
            offers.map((offer) => (
              <div className="task-record" key={offer.orderId}>
                <strong>
                  {offer.originCity} → {offer.destinationCity}
                </strong>
                <span>
                  {offer.distanceMeters} m · {offer.currency}{" "}
                  {offer.grossAmount}
                </span>
                <button disabled={busy} onClick={() => void accept(offer)}>
                  Aceitar oferta
                </button>
              </div>
            ))
          ) : (
            <p className="empty">
              Nenhuma oferta elegível. Aprovação profissional, veículo vigente e
              cobertura confirmada são pré-requisitos.
            </p>
          )}
        </article>
        <article hidden={deliverySection === "offers"}>
          <div className="card-heading">
            <div>
              <span className="card-kicker">{deliverySection === "active" ? "ENTREGA ATRIBUÍDA" : "HISTÓRICO"}</span>
              <h3>{selectedTaskId ? "Detalhe da entrega" : deliverySection === "active" ? "Próximas ações" : "Entregas anteriores"}</h3>
            </div>
            {selectedTaskId && <button type="button" className="secondary" onClick={() => setSelectedTaskId(null)}>Voltar à lista</button>}
          </div>
          {assignments.filter((task) => (deliverySection === "active" ? task.status === "ATIVA" : task.status !== "ATIVA") && (!selectedTaskId || task.id === selectedTaskId)).length ? (
            assignments.filter((task) => (deliverySection === "active" ? task.status === "ATIVA" : task.status !== "ATIVA") && (!selectedTaskId || task.id === selectedTaskId)).map((task) => {
              const state = custody[task.orderId];
              const payout = payouts[task.orderId];
              const proof = documents.find(
                (d) =>
                  d.category === "COMPROVANTE" &&
                  d.status === "INSPECAO_APROVADA",
              );
              return (
                <div className="task-record" key={task.id}>
                  <strong>{task.status}</strong>
                  <span>Entrega protegida · situação operacional</span>
                  <span>
                    {state
                      ? `Custódia ${state.status}`
                      : "Retirada não registrada"}
                  </span>
                  <span>
                    {payout
                      ? `Repasse próprio: ${payout.currency} ${payout.amount} · ${payout.status}`
                      : "Valor de repasse ainda não apurado; nenhum zero foi presumido."}
                  </span>
                  <button disabled={busy} onClick={() => void open(task)}>
                    Abrir detalhe e endereços autorizados
                  </button>
                  {!selectedTaskId && <button type="button" className="secondary" onClick={() => setSelectedTaskId(task.id)}>Ver detalhe</button>}
                  {task.status === "ATIVA" && !state && (
                    <button
                      disabled={busy || !proof}
                      onClick={() =>
                        proof &&
                        void act(task, "pickup", {
                          evidenceDocumentId: proof.id,
                        })
                      }
                    >
                      Registrar retirada
                    </button>
                  )}
                  <button
                    disabled={busy || state?.status !== "ATIVA"}
                    onClick={() => void act(task, "start-delivery")}
                  >
                    Iniciar entrega
                  </button>
                  {task.status === "ATIVA" && (
                    <div className="web-tracking-card" data-testid="web-tracking-card" aria-live="polite">
                      <strong>Localização web · primeiro plano</strong>
                      <p>
                        Começa somente por ação sua, após custódia ativa. O navegador pedirá uma permissão contextual;
                        a autorização da designação e a política continuam sendo revalidadas pelo backend.
                      </p>
                      <p className="web-tracking-limitation">
                        Esta captura não continua garantidamente com a aba suspensa, o navegador fechado ou o computador bloqueado.
                        Em homologação, a posição pode ser simulada pelo navegador; isso não comprova GPS físico.
                      </p>
                      {tracking.taskId === task.id && (
                        <>
                          <span className={`web-tracking-state ${tracking.status}`}>
                            {tracking.message}
                          </span>
                          {tracking.lastPosition && (
                            <small>
                              Última posição: {new Date(tracking.lastPosition.capturedAt).toLocaleTimeString("pt-BR")} · precisão aproximada {Math.round(tracking.lastPosition.accuracyMeters)} m
                              {tracking.lastPosition.stale ? " · desatualizada" : ""}
                              {tracking.lastPosition.lowAccuracy ? " · baixa precisão" : ""}
                            </small>
                          )}
                          {tracking.pending > 0 && <small>{tracking.pending} envio pendente em trânsito; não há fila persistente.</small>}
                          <button type="button" className="secondary" onClick={() => stopBrowserTracking("Captura interrompida pelo entregador; envios pendentes foram descartados.")}>
                            Interromper localização
                          </button>
                        </>
                      )}
                      {tracking.taskId !== task.id && (
                        <button
                          type="button"
                          disabled={busy || state?.status !== "ATIVA"}
                          onClick={() => void startBrowserTracking(task)}
                        >
                          Iniciar localização web
                        </button>
                      )}
                      {state?.status !== "ATIVA" && <small>Registre a retirada para habilitar a localização desta entrega.</small>}
                    </div>
                  )}
                  <form
                    onSubmit={(e) => {
                      e.preventDefault();
                      const code = String(
                        new FormData(e.currentTarget).get("code"),
                      );
                      void act(task, "deliver", { code });
                    }}
                  >
                    <label>
                      Código do destinatário
                      <input name="code" type="password" required />
                    </label>
                    <button disabled={busy}>Confirmar entrega</button>
                  </form>
                  <form
                    onSubmit={(e) => {
                      e.preventDefault();
                      const data = new FormData(e.currentTarget);
                      void act(task, "incidents", {
                        type: data.get("type"),
                        description: data.get("description"),
                      });
                    }}
                  >
                    <label>
                      Ocorrência
                      <select name="type">
                        <option>DESTINATARIO_AUSENTE</option>
                        <option>UNIDADE_FECHADA</option>
                        <option>LACRE_COMPROMETIDO</option>
                        <option>OUTRA</option>
                      </select>
                    </label>
                    <label>
                      Descrição
                      <textarea name="description" required />
                    </label>
                    <button disabled={busy}>Registrar ocorrência</button>
                  </form>
                  {addresses[task.orderId] && (
                    <address>
                      Retirada: {addresses[task.orderId].origin.street},{" "}
                      {addresses[task.orderId].origin.number} —{" "}
                      {addresses[task.orderId].origin.city}
                      <br />
                      Entrega: {
                        addresses[task.orderId].destination.street
                      }, {addresses[task.orderId].destination.number} —{" "}
                      {addresses[task.orderId].destination.city}
                    </address>
                  )}
                </div>
              );
            })
          ) : (
            <p className="empty">Nenhuma designação ativa ou histórica.</p>
          )}
        </article>
      </div>
    </section>
  );
}

function OperationalWorkspace({ session }: { session: Tokens }) {
  const [queue, setQueue] = useState<"deliverers" | "vehicles">("deliverers");
  return (
    <>
      <section className="family operational-workspace" aria-label="Área operacional">
        <div className="section-heading">
          <span className="eyebrow dark">OPERAÇÃO</span>
          <h2>Decisões com rastreabilidade</h2>
          <p>Escolha uma fila, consulte o detalhe autorizado e execute apenas a próxima ação permitida.</p>
        </div>
        <div className="tabs" role="tablist" aria-label="Filas operacionais">
          <button type="button" className="tab" role="tab" aria-selected={queue === "deliverers"} onClick={() => setQueue("deliverers")}>Entregadores</button>
          <button type="button" className="tab" role="tab" aria-selected={queue === "vehicles"} onClick={() => setQueue("vehicles")}>Veículos</button>
        </div>
      </section>
      <MfaAccess session={session} />
      <OperationalPolicyPanel session={session} />
      {queue === "deliverers" ? <OperationalReviewPanel session={session} /> : <VehicleReviewPanel session={session} />}
    </>
  );
}

function OperationalPolicyPanel({ session }: { session: Tokens }) {
  const [policies, setPolicies] = useState<Array<{ id: string; type: string; number: number; version: number; scope: string; status: string; startsAt: string; endsAt?: string; used: boolean }>>([]);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  const [form, setForm] = useState({ scope: "GLOBAL", base: "", perKm: "", perMinute: "", minimum: "", rounding: "2", validity: "900", acceptance: "900", execution: "86400", returnAmount: "", returnCoverage: "" });
  const [cancellation, setCancellation] = useState({ scope: "GLOBAL", before: "", after: "", pickup: "", refund: "" });
  const [designation, setDesignation] = useState({ scope: "GLOBAL", simultaneousTasks: "" });
  async function api(path: string, init: RequestInit = {}) {
    return parse(await fetch(`/api/v1${path}`, { ...init, headers: { Authorization: `Bearer ${session.accessToken}`, ...(init.body ? { "Content-Type": "application/json" } : {}), ...(init.headers ?? {}) } }));
  }
  async function load() { setPolicies((await api("/admin/operational-policies")).result as typeof policies); }
  useEffect(() => {
    const refreshAfterMfa = () => {
      void load().catch((e) => setNotice({ tone: "error", text: (e as Error).message }));
    };
    void load().catch((e) => setNotice({ tone: "error", text: (e as Error).message }));
    addEventListener("mfa-elevated", refreshAfterMfa);
    return () => removeEventListener("mfa-elevated", refreshAfterMfa);
  }, [session.accessToken]);
  async function create() {
    setBusy(true); setNotice(null);
    try {
      await api("/admin/operational-policies/tariffs", { method: "POST", body: JSON.stringify({ scope: form.scope, baseAmount: form.base, perKmAmount: form.perKm, perMinuteAmount: form.perMinute, minimumAmount: form.minimum, roundingScale: Number(form.rounding), quoteValiditySeconds: Number(form.validity), acceptanceDeadlineSeconds: Number(form.acceptance), executionDeadlineSeconds: Number(form.execution), returnAmount: form.returnAmount, returnCoverage: form.returnCoverage, startsAt: new Date().toISOString() }) });
      await load(); setNotice({ tone: "info", text: "Rascunho tarifário persistido. Publique-o explicitamente após revisar os prazos e a cobertura." });
    } catch (e) { setNotice({ tone: "error", text: e instanceof Error ? e.message : "Não foi possível salvar a política." }); }
    finally { setBusy(false); }
  }
  async function transition(id: string, action: "publish" | "deactivate") {
    setBusy(true); setNotice(null);
    try { await api(`/admin/operational-policies/tariffs/${id}/${action}`, { method: "POST" }); await load(); setNotice({ tone: "info", text: action === "publish" ? "Política publicada pelo backend." : "Política desativada pelo backend." }); }
    catch (e) { setNotice({ tone: "error", text: e instanceof Error ? e.message : "Transição não confirmada." }); }
    finally { setBusy(false); }
  }
  async function createPolicy(path: string, body: object, label: string) {
    setBusy(true); setNotice(null);
    try { await api(`/admin/operational-policies/${path}`, { method: "POST", body: JSON.stringify({ ...body, startsAt: new Date().toISOString() }) }); await load(); setNotice({ tone: "info", text: `${label} persistida como rascunho.` }); }
    catch (e) { setNotice({ tone: "error", text: e instanceof Error ? e.message : "Não foi possível salvar a política." }); }
    finally { setBusy(false); }
  }
  const field = (key: keyof typeof form, label: string, type = "text") => <label>{label}<input type={type} value={form[key]} onChange={(e) => setForm({ ...form, [key]: e.target.value })} required /></label>;
  return <section className="family review-panel" aria-label="Políticas operacionais">
    <div className="section-heading"><span className="eyebrow dark">POLÍTICAS</span><h2>Tarifa e vigência</h2><p>Rascunhos exigem MFA e publicação explícita. Valores aceitos ficam vinculados à versão e à rota da cotação; não há valores comerciais preenchidos por padrão.</p></div>
    <form className="review-toolbar" onSubmit={(e) => { e.preventDefault(); void create(); }}>
      {field("scope", "Escopo")}{field("base", "Tarifa base", "number")}{field("perKm", "Valor por km", "number")}{field("perMinute", "Valor por minuto", "number")}{field("minimum", "Valor mínimo", "number")}{field("rounding", "Casas de arredondamento", "number")}{field("validity", "Validade da cotação (s)", "number")}{field("acceptance", "Prazo para aceite (s)", "number")}{field("execution", "Prazo para execução (s)", "number")}{field("returnAmount", "Valor de retorno", "number")}{field("returnCoverage", "Cobertura de retorno")}
      <button type="submit" disabled={busy}>Salvar rascunho</button>
    </form>
    <div className="review-toolbar">
      <form onSubmit={(e) => { e.preventDefault(); void createPolicy("cancellations", { scope: cancellation.scope, beforeDesignation: cancellation.before, afterDesignation: cancellation.after, afterPickup: cancellation.pickup, refundHandling: cancellation.refund }, "Política de cancelamento"); }}>
        <h3>Cancelamento e estorno</h3><label>Escopo<input value={cancellation.scope} onChange={(e) => setCancellation({ ...cancellation, scope: e.target.value })} required /></label><label>Antes da designação<input value={cancellation.before} onChange={(e) => setCancellation({ ...cancellation, before: e.target.value })} required /></label><label>Após designação<input value={cancellation.after} onChange={(e) => setCancellation({ ...cancellation, after: e.target.value })} required /></label><label>Após retirada<input value={cancellation.pickup} onChange={(e) => setCancellation({ ...cancellation, pickup: e.target.value })} required /></label><label>Tratamento financeiro<input value={cancellation.refund} onChange={(e) => setCancellation({ ...cancellation, refund: e.target.value })} required /></label><button type="submit" disabled={busy}>Salvar rascunho</button>
      </form>
      <form onSubmit={(e) => { e.preventDefault(); void createPolicy("designations", { scope: designation.scope, simultaneousTasks: Number(designation.simultaneousTasks) }, "Política de designação"); }}>
        <h3>Capacidade de designação</h3><label>Escopo<input value={designation.scope} onChange={(e) => setDesignation({ ...designation, scope: e.target.value })} required /></label><label>Tarefas simultâneas<input type="number" min="1" value={designation.simultaneousTasks} onChange={(e) => setDesignation({ ...designation, simultaneousTasks: e.target.value })} required /></label><button type="submit" disabled={busy}>Salvar rascunho</button>
      </form>
    </div>
    <div className="registers">{policies.filter((p) => p.type === "TARIFA").map((p) => <article key={p.id}><h3>Tarifa {p.number} · v{p.version}</h3><p><strong>{p.status}</strong><span>Escopo {p.scope} · vigência a partir de {new Date(p.startsAt).toLocaleString("pt-BR")}</span></p><div className="journey-actions">{p.status === "RASCUNHO" && <button type="button" disabled={busy} onClick={() => void transition(p.id, "publish")}>Publicar</button>}{p.status === "ATIVA" && <button type="button" disabled={busy} onClick={() => void transition(p.id, "deactivate")}>Desativar</button>}</div></article>)}</div>
    {notice && <p className={`feedback ${notice.tone}`} role="alert">{notice.text}</p>}
  </section>;
}

function OperationalReviewPanel({ session }: { session: Tokens }) {
  const [reviews, setReviews] = useState<DelivererReview[]>([]);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("TODAS");
  const [page, setPage] = useState(0);
  async function api(path: string, init: RequestInit = {}) {
    return parse(
      await fetch(`/api/v1${path}`, {
        ...init,
        headers: {
          Authorization: `Bearer ${session.accessToken}`,
          ...(init.body ? { "Content-Type": "application/json" } : {}),
          ...(init.headers ?? {}),
        },
      }),
    );
  }
  async function load() {
    setReviews((await api("/analyst/reviews")).result as DelivererReview[]);
  }
  useEffect(() => {
    void load().catch((e) =>
      setNotice({ tone: "error", text: (e as Error).message }),
    );
  }, [session.accessToken]);
  async function action(item: DelivererReview, path: string, body?: object) {
    setBusy(true);
    setNotice(null);
    try {
      await api(`/analyst/reviews/${item.id}/${path}`, {
        method: "POST",
        body: body ? JSON.stringify(body) : undefined,
      });
      await load();
      setNotice({
        tone: "info",
        text:
          path === "decision"
            ? "A política disponível foi consultada; o resultado exibido veio do servidor."
            : "Etapa operacional registrada.",
      });
    } catch (error) {
      setNotice({
        tone: "error",
        text: error instanceof Error ? error.message : "Ação não confirmada.",
      });
    } finally {
      setBusy(false);
    }
  }
  const filteredReviews = reviews.filter((item) =>
    (statusFilter === "TODAS" || item.status === statusFilter) &&
    (!query.trim() || `${item.category ?? ""} ${item.documentStatus ?? ""} ${item.status}`.toLowerCase().includes(query.trim().toLowerCase())),
  );
  const visibleReviews = filteredReviews.slice(page * 5, page * 5 + 5);
  const selected = reviews.find((item) => item.id === selectedId) ?? null;
  return (
    <section
      className="family review-panel"
      aria-label="Análise de entregadores"
    >
      <div className="section-heading">
        <span className="eyebrow dark">OPERAÇÃO</span>
        <h2>Fila de entregadores</h2>
        <p>
          Atribuição nominal, MFA e impedimento de autoanálise são verificados
          pelo backend. Inspeção técnica não equivale à aprovação profissional.
        </p>
      </div>
      {selected ? (
        <article className="review-detail-card">
          <div className="card-heading">
            <div><span className="card-kicker">DETALHE DA REVISÃO</span><h3>Cadastro de entregador</h3></div>
            <button type="button" className="secondary" onClick={() => setSelectedId(null)}>Voltar à fila</button>
          </div>
          <div className="review-detail-grid">
            <div><strong>Situação</strong><span>{selected.status}</span><small>O backend mantém a decisão e a permissão.</small></div>
            <div><strong>Documento autorizado</strong><span>{selected.category ?? "Evidência vinculada"}</span><small>{selected.documentStatus ?? "Status indisponível"} · {selected.mime ?? "formato protegido"} · {selected.size ? `${Math.ceil(selected.size / 1024)} KB` : "tamanho não informado"}</small></div>
            <div><strong>Pendência</strong><span>{selected.reason ?? "Nenhum motivo informado pelo servidor."}</span><small>Inspeção estrutural não equivale à aprovação profissional.</small></div>
            <div><strong>Histórico</strong><span>Revisão criada em {new Date(selected.createdAt).toLocaleString("pt-BR")}</span><small>Próxima ação depende de MFA, atribuição nominal e política vigente.</small></div>
          </div>
          <div className="journey-actions">
            {selected.status === "PENDENTE" && <button disabled={busy} onClick={() => void action(selected, "assign")}>Assumir análise</button>}
            {selected.status === "ATRIBUIDA" && <>
              <button disabled={busy} onClick={() => void action(selected, "inspection")}>Inspecionar evidência</button>
              <button disabled={busy} onClick={() => void action(selected, "decision", { decision: "APROVAR", reason: "ANALISE_NOMINAL" })}>Solicitar decisão</button>
            </>}
          </div>
        </article>
      ) : (
        <>
          <div className="review-toolbar">
            <label>Buscar situação ou evidência<input value={query} onChange={(e) => { setQuery(e.target.value); setPage(0); }} placeholder="Ex.: quarentena" /></label>
            <label>Situação<select value={statusFilter} onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}><option>TODAS</option><option>PENDENTE</option><option>ATRIBUIDA</option></select></label>
            <span>{filteredReviews.length} item(ns) na fila</span>
          </div>
          <div className="registers">
            {visibleReviews.length ? visibleReviews.map((item) => (
              <article key={item.id}>
                <h3>Cadastro de entregador</h3>
                <p><strong>{item.status}</strong><span>{item.category ?? "Evidência vinculada"} · {item.documentStatus ?? "status não informado"}</span></p>
                <button type="button" onClick={() => setSelectedId(item.id)}>Abrir detalhe</button>
              </article>
            )) : <article><p className="empty">Nenhuma revisão corresponde aos filtros.</p></article>}
          </div>
          {filteredReviews.length > 5 && <div className="pagination"><button type="button" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Anterior</button><span>Página {page + 1} de {Math.ceil(filteredReviews.length / 5)}</span><button type="button" disabled={(page + 1) * 5 >= filteredReviews.length} onClick={() => setPage((value) => value + 1)}>Próxima</button></div>}
        </>
      )}
      {notice && (
        <p className={`feedback ${notice.tone}`} role="alert">
          {notice.text}
        </p>
      )}
    </section>
  );
}

function BenefitReviewPanel({ session }: { session: Tokens }) {
  const [reviews, setReviews] = useState<BenefitReview[]>([]);
  const [requests, setRequests] = useState<
    Record<string, BenefitRequest & { evidence: BenefitEvidence[] }>
  >({});
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("TODAS");
  const [page, setPage] = useState(0);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  async function api(path: string, init: RequestInit = {}) {
    return parse(
      await fetch(`/api/v1${path}`, {
        ...init,
        headers: {
          Authorization: `Bearer ${session.accessToken}`,
          ...(init.body ? { "Content-Type": "application/json" } : {}),
          ...(init.headers ?? {}),
        },
      }),
    );
  }
  async function load() {
    const list = (await api("/analyst/benefit-reviews"))
      .result as BenefitReview[];
    setReviews(list);
    const detail = await Promise.all(
      list.map(
        async (item) =>
          [
            item.requestId,
            (await api(`/benefit-requests/${item.requestId}`)).result,
          ] as const,
      ),
    );
    setRequests(Object.fromEntries(detail));
  }
  const filteredReviews = reviews.filter((item) => {
    const request = requests[item.requestId];
    const haystack = `${item.type} ${item.status} ${request?.dimensions.join(" ") ?? ""}`.toLowerCase();
    return (statusFilter === "TODAS" || item.status === statusFilter) && haystack.includes(query.toLowerCase());
  });
  const visibleReviews = filteredReviews.slice(page * 5, page * 5 + 5);
  const selected = reviews.find((item) => item.id === selectedId);
  const selectedRequest = selected ? requests[selected.requestId] : undefined;
  const selectedHasUninspected = selectedRequest?.evidence.some(
    (document) => document.current && document.status !== "INSPECAO_APROVADA",
  ) ?? false;
  useEffect(() => {
    void load().catch((e) =>
      setNotice({ tone: "error", text: (e as Error).message }),
    );
  }, [session.accessToken]);
  async function action(path: string, body?: object) {
    setBusy(true);
    setNotice(null);
    try {
      await api(path, {
        method: "POST",
        body: body ? JSON.stringify(body) : undefined,
      });
      await load();
      setNotice({
        tone: "info",
        text: "Etapa de análise persistida. Recursos continuam exigindo outra pessoa elegível.",
      });
    } catch (error) {
      setNotice({
        tone: "error",
        text: error instanceof Error ? error.message : "Ação não confirmada.",
      });
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="family review-panel" aria-label="Análise de benefícios">
      <div className="section-heading">
        <span className="eyebrow dark">BENEFÍCIOS</span>
        <h2>Fila institucional</h2>
        <p>
          Somente solicitações da instituição vinculada aparecem. Autoanálise e
          revisão do próprio recurso são bloqueadas no servidor.
        </p>
      </div>
      {!selected ? <>
      <div className="review-toolbar">
        <label>Buscar na fila<input value={query} onChange={(e) => { setQuery(e.target.value); setPage(0); }} placeholder="Dimensão ou situação" /></label>
        <label>Situação<select value={statusFilter} onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}><option>TODAS</option><option>PENDENTE</option><option>ATRIBUIDA</option></select></label>
        <span>{filteredReviews.length} revisão(ões) no escopo institucional</span>
      </div>
      <div className="registers">
        {reviews.length ? (
          visibleReviews.map((item) => (
            <article key={item.id}>
              <h3>{item.type === "RECURSO" ? "Recurso de benefício" : "Análise inicial de benefício"}</h3>
              <p>
                <strong>{item.status}</strong>
                <span>{requests[item.requestId]?.dimensions.join(" · ") || "Dimensões em carregamento"}</span>
              </p>
              <button type="button" onClick={() => setSelectedId(item.id)}>Abrir detalhe</button>
            </article>
          ))
        ) : (
          <article>
            <p className="empty">
              Nenhuma solicitação na fila desta instituição.
            </p>
          </article>
        )}
      </div>
      {filteredReviews.length > 5 && <div className="pagination"><button type="button" disabled={!page} onClick={() => setPage((p) => p - 1)}>Anterior</button><span>Página {page + 1} de {Math.ceil(filteredReviews.length / 5)}</span><button type="button" disabled={(page + 1) * 5 >= filteredReviews.length} onClick={() => setPage((p) => p + 1)}>Próxima</button></div>}
      </> : <article className="review-detail-card">{selectedRequest && selected && <>
        <div className="card-heading"><div><span className="card-kicker">DETALHE DA REVISÃO</span><h3>{selected.type === "RECURSO" ? "Recurso de benefício" : "Solicitação de benefício"}</h3></div><button type="button" className="secondary" onClick={() => setSelectedId(null)}>Voltar à fila</button></div>
        <div className="review-detail-grid">
          <div><strong>Situação</strong><span>{selected.status}</span><small>Escopo institucional e autoanálise continuam decididos pelo backend.</small></div>
          <div><strong>Dimensões de elegibilidade</strong><span>{selectedRequest.dimensions.join(" · ")}</span><small>Pendentes: {selectedRequest.pendingDimensions.length ? selectedRequest.pendingDimensions.join(" · ") : "nenhuma"}</small></div>
          <div><strong>Política vigente</strong><span>Versão {selectedRequest.policyVersion}</span><small>Limite registrado: {selectedRequest.policyMaximumPercentage}% · não é uma aprovação.</small></div>
          <div><strong>Critérios publicados</strong><pre>{JSON.stringify(selectedRequest.policyCriteria, null, 2)}</pre></div>
        </div>
        <h4>Evidências autorizadas</h4><div className="evidence-list">{selectedRequest.evidence.length ? selectedRequest.evidence.map((document) => <p key={document.id}><strong>{document.purpose}</strong><span>{document.status} · {document.mime} · {Math.ceil(document.size / 1024)} KB</span>{selected.status === "ATRIBUIDA" && document.current && document.status === "QUARENTENA" && <button disabled={busy} onClick={() => void action(`/analyst/benefit-reviews/${selected.id}/documents/${document.id}/inspection`)}>Inspecionar</button>}</p>) : <p className="empty">Nenhuma evidência autorizada vinculada.</p>}</div>
        <h4>Histórico e pendências</h4><div className="timeline">{selectedRequest.reviews.map((review) => <p key={review.id}><strong>{review.type}</strong><span>{review.status} · {new Date(review.createdAt).toLocaleString("pt-BR")}</span><small>{review.reason ?? "Sem justificativa registrada."}</small></p>)}</div>
        {selected.status === "PENDENTE" && <button disabled={busy} onClick={() => void action(`/analyst/benefit-reviews/${selected.id}/assign`)}>Assumir análise</button>}
        {selected.status === "ATRIBUIDA" && <>
          {selectedRequest.pendingDimensions.length > 0 && <p className="blocking-note">Complemento pendente para: {selectedRequest.pendingDimensions.join(" · ")}. A decisão permanece bloqueada.</p>}
          {selectedHasUninspected && <p className="blocking-note">Há evidência sem inspeção aprovada. A inspeção técnica e a decisão humana são etapas distintas.</p>}
          <form onSubmit={(e) => { e.preventDefault(); const data = new FormData(e.currentTarget); void action(`/analyst/benefit-reviews/${selected.id}/complement`, { reason: data.get("reason") }); }}><label>Solicitar complemento<textarea name="reason" required placeholder="Descreva a evidência ou esclarecimento previsto na política vigente." /></label><button className="secondary" disabled={busy}>Solicitar complemento</button></form>
          {selectedRequest.pendingDimensions.length === 0 && <form onSubmit={(e) => { e.preventDefault(); const data = new FormData(e.currentTarget); void action(`/analyst/benefit-reviews/${selected.id}/decision`, { decision: data.get("decision"), percentage: Number(data.get("percentage")), reason: data.get("reason") }); }}><label>Decisão<select name="decision"><option>APROVADA</option><option>REJEITADA</option></select></label><label>Percentual conforme política<input name="percentage" type="number" min="0" max={selectedRequest.policyMaximumPercentage} step="0.01" required /><small>O servidor valida contra a política vigente; nenhum valor é pré-preenchido.</small></label><label>Justificativa<input name="reason" required /></label><button disabled={busy || selectedHasUninspected}>Registrar decisão</button></form>}
        </>}
      </>}</article>}
      {notice && (
        <p className={`feedback ${notice.tone}`} role="alert">
          {notice.text}
        </p>
      )}
    </section>
  );
}

function PrivacyOperationsPanel({ session }: { session: Tokens }) {
  const [items, setItems] = useState<PrivacyRequest[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("TODOS");
  const [page, setPage] = useState(0);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  async function api(path: string, init: RequestInit = {}) {
    return parse(
      await fetch(`/api/v1${path}`, {
        ...init,
        headers: {
          Authorization: `Bearer ${session.accessToken}`,
          ...(init.body ? { "Content-Type": "application/json" } : {}),
          ...(init.headers ?? {}),
        },
      }),
    );
  }
  async function load() {
    setItems((await api("/privacy-requests")).result as PrivacyRequest[]);
  }
  useEffect(() => {
    void load().catch((e) =>
      setNotice({ tone: "error", text: (e as Error).message }),
    );
  }, [session.accessToken]);
  async function action(path: string, body?: object) {
    setBusy(true);
    setNotice(null);
    try {
      await api(path, {
        method: "POST",
        body: body ? JSON.stringify(body) : undefined,
      });
      await load();
      setNotice({
        tone: "info",
        text: "Estado atualizado pelo servidor; ressalvas e bloqueios permanecem visíveis.",
      });
    } catch (error) {
      setNotice({
        tone: "error",
        text: error instanceof Error ? error.message : "Ação não confirmada.",
      });
    } finally {
      setBusy(false);
    }
  }
  const filteredItems = items.filter((item) => (statusFilter === "TODOS" || item.status === statusFilter) && `${item.protocol} ${item.type} ${item.status}`.toLowerCase().includes(query.toLowerCase()));
  const visibleItems = filteredItems.slice(page * 5, page * 5 + 5);
  const selected = items.find((item) => item.id === selectedId);
  return (
    <section
      className="family privacy-panel"
      aria-label="Operação de privacidade"
    >
      <div className="section-heading">
        <span className="eyebrow dark">PRIVACIDADE</span>
        <h2>Fila nominal</h2>
        <p>
          Este papel não concede acesso geral ao produto. Resposta, autorização,
          execução e verificação de expurgo são marcos separados e exigem MFA.
        </p>
      </div>
      {!selected && <>
      <div className="review-toolbar"><label>Buscar na fila<input value={query} onChange={(e) => { setQuery(e.target.value); setPage(0); }} placeholder="Protocolo ou etapa" /></label><label>Etapa<select value={statusFilter} onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}><option>TODOS</option>{[...new Set(items.map((item) => item.status))].map((value) => <option key={value}>{value}</option>)}</select></label><span>{filteredItems.length} solicitação(ões) no escopo</span></div>
      <div className="registers">
        {items.length ? (
          visibleItems.map((item) => (
            <article key={item.id}>
              <h3>{item.type === "EXCLUSAO" ? "Exclusão" : item.type === "CORRECAO" ? "Correção" : "Acesso"}</h3>
              <p>
                <strong>{item.status}</strong>
                <span>
                  {item.responsibleAssigned ? "Responsável atribuído" : "Aguardando responsável"}
                </span>
              </p>
              <button type="button" onClick={() => setSelectedId(item.id)}>Abrir detalhe</button>
            </article>
          ))
        ) : (
          <article>
            <p className="empty">Nenhuma solicitação operacional pendente.</p>
          </article>
        )}
      </div>
      {filteredItems.length > 5 && <div className="pagination"><button type="button" disabled={!page} onClick={() => setPage((p) => p - 1)}>Anterior</button><span>Página {page + 1} de {Math.ceil(filteredItems.length / 5)}</span><button type="button" disabled={(page + 1) * 5 >= filteredItems.length} onClick={() => setPage((p) => p + 1)}>Próxima</button></div>}
      </>}
      {selected && <article className="review-detail-card"><div className="card-heading"><div><span className="card-kicker">DETALHE DA SOLICITAÇÃO</span><h3>{selected.type === "EXCLUSAO" ? "Exclusão" : selected.type === "CORRECAO" ? "Correção" : "Acesso"}</h3></div><button type="button" className="secondary" onClick={() => setSelectedId(null)}>Voltar à fila</button></div><div className="review-detail-grid"><div><strong>Resposta</strong><span>{selected.status === "PENDENTE" || selected.status === "EM_ANALISE" ? "Pendente" : "Registrada"}</span><small>{selected.respondedAt ? new Date(selected.respondedAt).toLocaleString("pt-BR") : "Nenhuma resposta registrada."}</small></div><div><strong>Autorização</strong><span>{selected.purgeStatus ?? "Não aplicável"}</span><small>Resposta ao titular não equivale a exclusão executada.</small></div><div><strong>Execução</strong><span>{selected.status === "EXPURGADA" || selected.status === "VERIFICADA" ? "Executada" : "Pendente"}</span><small>Transformação parcial nunca é apresentada como anonimização integral.</small></div><div><strong>Verificação</strong><span>{selected.status === "VERIFICADA" ? "Verificada" : "Pendente"}</span><small>{selected.procedurePending} etapa(s) pendente(s) · {selected.preserved} registro(s) conservado(s).</small></div></div><h4>Histórico operacional</h4><div className="timeline"><p><strong>Solicitação</strong><span>{new Date(selected.createdAt).toLocaleString("pt-BR")}</span></p>{selected.response && <p><strong>Resposta ao titular</strong><span>Registrada sem declarar execução.</span></p>}{selected.purgeStatus && <p><strong>Expurgo</strong><span>{selected.purgeStatus}</span></p>}<p><strong>Conservação e bloqueios</strong><span>{selected.preserved ? `${selected.preserved} item(ns) preservado(s)` : "Nenhum registro de conservação retornado"}; {selected.procedurePending ? `${selected.procedurePending} procedimento(s) pendente(s)` : "sem procedimentos pendentes"}</span></p><p><strong>Encerramento/anonimização</strong><span>{selected.closureStatus} · {selected.anonymizationStatus}</span></p></div>{["PENDENTE", "EM_ANALISE"].includes(selected.status) && <form onSubmit={(e) => { e.preventDefault(); const response = String(new FormData(e.currentTarget).get("response")); void action(`/privacy-requests/${selected.id}/response`, { status: "RESPONDIDA", response }); }}><label>Resposta ao titular<textarea name="response" required /></label><button disabled={busy}>Registrar resposta</button></form>}{selected.type === "EXCLUSAO" && selected.status === "RESPONDIDA" && <button disabled={busy} onClick={() => void action(`/privacy-requests/${selected.id}/purge`)}>Autorizar expurgo conforme política vigente</button>}{selected.status === "EXPURGO_SOLICITADO" && <button disabled={busy} onClick={() => void action(`/privacy-requests/${selected.id}/purge/execute`)}>Executar alvos controlados</button>}{selected.status === "EXPURGADA" && <button disabled={busy} onClick={() => void action(`/privacy-requests/${selected.id}/purge/verify`)}>Verificar execução</button>}</article>}
      {notice && (
        <p className={`feedback ${notice.tone}`} role="alert">
          {notice.text}
        </p>
      )}
    </section>
  );
}

function PatientDashboard({
  session,
  capabilities,
  go,
}: {
  session: Tokens;
  capabilities: Capabilities;
  go: (route: AppRoute) => void;
}) {
  const [profile, setProfile] = useState<Profile | null>(null);
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    Promise.all([
      fetch("/api/v1/me/profile", {
        headers: { Authorization: `Bearer ${session.accessToken}` },
      }).then(parse),
      fetch("/api/v1/orders", {
        headers: { Authorization: `Bearer ${session.accessToken}` },
      }).then(parse),
    ])
      .then(([profileResult, ordersResult]) => {
        setProfile(profileResult.result as Profile);
        setOrders((ordersResult.result as Order[]) ?? []);
      })
      .catch((cause) =>
        setError(
          cause instanceof Error
            ? cause.message
            : "Não foi possível carregar sua conta.",
        ),
      )
      .finally(() => setLoading(false));
  }, [session.accessToken]);
  const firstName = profile?.name?.split(" ")[0] ?? "seja bem-vindo";
  const statusLabel = profile?.emailVerifiedAt
    ? "E-mail confirmado"
    : "Confirme seu e-mail";
  return (
    <section className="patient-dashboard" aria-labelledby="dashboard-title">
      <div className="dashboard-welcome">
        <div>
          <p className="eyebrow dark">VISÃO GERAL</p>
          <h2 id="dashboard-title">Olá, {firstName}.</h2>
          <p>
            Este é o ponto de partida para cuidar dos seus pedidos e
            recebimentos.
          </p>
        </div>
        <span
          className={`profile-chip ${profile?.emailVerifiedAt ? "is-ok" : "is-pending"}`}
        >
          <i />
          {statusLabel}
        </span>
      </div>
      {error && (
        <p className="feedback error" role="alert">
          {error}
        </p>
      )}
      <div className="dashboard-primary">
        <div>
          <span className="card-kicker">PRÓXIMO PASSO</span>
          <h3>Precisa buscar um resultado?</h3>
          <p>
            Crie um pedido de entrega com as informações da unidade e de quem
            vai receber.
          </p>
          <button onClick={() => go("paciente-pedidos")}>
            Solicitar entrega <span aria-hidden="true">→</span>
          </button>
        </div>
        <div className="primary-mark" aria-hidden="true">
          <span>✦</span>
          <i />
          <i />
          <i />
        </div>
      </div>
      <div className="dashboard-columns">
        <article className="dashboard-card recent-card">
          <div className="card-heading">
            <div>
              <span className="card-kicker">ATIVIDADE</span>
              <h3>Pedidos recentes</h3>
            </div>
            <button className="text-button" onClick={() => go("paciente-pedidos")}>
              Ver pedidos <span aria-hidden="true">↗</span>
            </button>
          </div>
          {loading ? (
            <p className="empty-state">Carregando seus pedidos…</p>
          ) : orders.length ? (
            <div className="real-list">
              {orders.slice(0, 3).map((order) => (
                <button
                  className="real-row"
                  key={order.id}
                  onClick={() => go("paciente-pedidos")}
                >
                  <span className="row-icon">↗</span>
                  <span>
                    <strong>Pedido de retirada</strong>
                    <small>
                      {new Date(order.createdAt).toLocaleDateString("pt-BR")} ·{" "}
                      {order.status.replaceAll("_", " ")}
                    </small>
                  </span>
                  <span aria-hidden="true">→</span>
                </button>
              ))}
            </div>
          ) : (
            <div className="empty-state">
              <span className="empty-icon" aria-hidden="true">
                ○
              </span>
              <strong>Você ainda não tem pedidos.</strong>
              <p>Quando precisar receber um resultado, comece por aqui.</p>
              <button className="text-button" onClick={() => go("paciente-pedidos")}>
                Fazer primeiro pedido →
              </button>
            </div>
          )}
        </article>
        <div className="dashboard-side">
          <article className="dashboard-card mini-card">
            <span className="card-kicker">SEU PERFIL</span>
            <h3>
              {capabilities.patientProfile
                ? "Perfil de paciente ativo"
                : "Complete seu perfil"}
            </h3>
            <p>
              {capabilities.patientProfile
                ? "Suas informações estão prontas para uma nova solicitação."
                : "Adicione seu perfil para solicitar uma entrega."}
            </p>
            <button
              className="text-button"
              onClick={() => go("perfil")}
            >
              {capabilities.patientProfile ? "Revisar perfil" : "Começar na conta"}{" "}
              →
            </button>
          </article>
          <article className="dashboard-card link-card">
            <button onClick={() => go("paciente-beneficios")}>
              <span className="link-symbol">+</span>
              <span>
                <strong>Benefícios</strong>
                <small>Confira apoios disponíveis para seu pedido</small>
              </span>
              <span>→</span>
            </button>
            <button onClick={() => go("familia-autorizacoes")}>
              <span className="link-symbol">⌁</span>
              <span>
                <strong>Família autorizada</strong>
                <small>Gerencie quem pode ajudar</small>
              </span>
              <span>→</span>
            </button>
            <button onClick={() => go("perfil")}>
              <span className="link-symbol">◌</span>
              <span>
                <strong>Configurações</strong>
                <small>Dados e segurança da conta</small>
              </span>
              <span>→</span>
            </button>
          </article>
        </div>
      </div>
    </section>
  );
}

type ActingArea =
  | "paciente"
  | "familiar"
  | "entregador"
  | "operacao"
  | "beneficios"
  | "financeiro"
  | "privacidade-operacional";

const actingAreaLabels: Record<ActingArea, string> = {
  paciente: "Paciente",
  familiar: "Familiar",
  entregador: "Entregador",
  operacao: "Operação",
  beneficios: "Benefícios",
  financeiro: "Financeiro",
  "privacidade-operacional": "Privacidade operacional",
};

function AreaChooser({
  areas,
  go,
}: {
  areas: ActingArea[];
  go: (route: AppRoute) => void;
}) {
  const destinations: Record<ActingArea, AppRoute> = {
    paciente: "paciente",
    familiar: "familia",
    entregador: "entregador",
    operacao: "operacao",
    beneficios: "analise-beneficios",
    financeiro: "financeiro",
    "privacidade-operacional": "operacao-privacidade",
  };
  return (
    <section className="area-chooser" aria-labelledby="area-chooser-title">
      <div className="section-heading">
        <span className="eyebrow dark">CONTEXTO DE ATUAÇÃO</span>
        <h2 id="area-chooser-title">Onde você quer atuar agora?</h2>
        <p>
          Cada área tem seu próprio painel. A escolha só organiza a interface;
          o backend continua revalidando papel, vínculo, escopo e MFA.
        </p>
      </div>
      <div className="area-choice-grid">
        {areas.length ? areas.map((area) => (
          <button
            className="area-choice"
            type="button"
            key={area}
            onClick={() => go(destinations[area])}
          >
            <span className="card-kicker">ATUAR COMO</span>
            <strong>{actingAreaLabels[area]}</strong>
            <span>Entrar no dashboard específico →</span>
          </button>
        )) : (
          <div className="empty-state">
            <strong>Nenhuma área de atuação está habilitada para esta conta.</strong>
            <p>Revise a conta e, se desejar atuar como paciente, crie o perfil explicitamente em Conta e segurança.</p>
            <button type="button" onClick={() => go("perfil")}>Abrir conta e segurança →</button>
          </div>
        )}
      </div>
    </section>
  );
}

function PortfolioApp({
  session,
  onSessionAction,
  busy,
  feedback,
}: {
  session: Tokens;
  onSessionAction: (action: "refresh" | "logout") => Promise<void>;
  busy: boolean;
  feedback: Notice | null;
}) {
  const [capabilities, setCapabilities] = useState<Capabilities | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [route, setRoute] = useState<AppRoute>(appRouteFromPath() ?? "inicio");
  const [activeInstitutionId, setActiveInstitutionId] = useState("");
  useEffect(() => {
    fetch("/api/v1/me/capabilities", { headers: { Authorization: `Bearer ${session.accessToken}` } })
      .then(parse)
      .then((value) => setCapabilities(value.result as Capabilities))
      .catch((error) => setNotice({ tone: "error", text: error instanceof Error ? error.message : "Capacidades indisponíveis." }));
  }, [session.accessToken]);
  useEffect(() => {
    const change = () => setRoute(appRouteFromPath() ?? "inicio");
    addEventListener("popstate", change);
    return () => removeEventListener("popstate", change);
  }, []);
  useEffect(() => {
    if (capabilities && !activeInstitutionId) setActiveInstitutionId(capabilities.institutions[0]?.institutionId ?? "");
  }, [capabilities, activeInstitutionId]);
  const actingAreas: ActingArea[] = capabilities
    ? [
        ...(capabilities.areas.includes("PACIENTE") ? ["paciente" as const] : []),
        ...(capabilities.areas.includes("FAMILIAR") ? ["familiar" as const] : []),
        ...(capabilities.areas.includes("ENTREGADOR") ? ["entregador" as const] : []),
        ...(capabilities.areas.includes("OPERACAO") ? ["operacao" as const] : []),
        ...(capabilities.areas.includes("ANALISE_BENEFICIO") ? ["beneficios" as const] : []),
        ...(capabilities.areas.includes("FINANCEIRO") ? ["financeiro" as const] : []),
        ...(capabilities.areas.includes("OPERACAO_PRIVACIDADE") ? ["privacidade-operacional" as const] : []),
      ]
    : [];
  const areaForRoute = (value: AppRoute): ActingArea | null => {
    if (["paciente", "paciente-pedidos", "paciente-beneficios", "beneficios", "pedidos", "familia-autorizacoes"].includes(value)) return "paciente";
    if (["familia", "familia-pedidos"].includes(value)) return "familiar";
    if (["entregador", "entregador-cadastro", "entregas"].includes(value)) return "entregador";
    if (["operacao", "catalogo"].includes(value)) return "operacao";
    if (["analise-beneficios", "beneficios-fila"].includes(value)) return "beneficios";
    if (value === "financeiro") return "financeiro";
    if (value === "operacao-privacidade") return "privacidade-operacional";
    return null;
  };
  const activeArea = areaForRoute(route);
  const areaRoutes: Record<ActingArea, AppRoute[]> = {
    paciente: ["paciente", "paciente-pedidos", "paciente-beneficios", "familia-autorizacoes"],
    familiar: ["familia", "familia-pedidos"],
    entregador: ["entregador", "entregador-cadastro", "entregas"],
    operacao: ["operacao", "catalogo"],
    beneficios: ["analise-beneficios", "beneficios-fila"],
    financeiro: ["financeiro"],
    "privacidade-operacional": ["operacao-privacidade"],
  };
  const areaDestination: Record<ActingArea, AppRoute> = {
    paciente: "paciente", familiar: "familia", entregador: "entregador", operacao: "operacao",
    beneficios: "analise-beneficios", financeiro: "financeiro", "privacidade-operacional": "operacao-privacidade",
  };
  const allowed = (value: AppRoute) => {
    const area = areaForRoute(value);
    return !area || actingAreas.includes(area);
  };
  useEffect(() => {
    const parsed = appRouteFromPath();
    if (parsed && isLegacyAppPath() && appPath(parsed) !== window.location.pathname) {
      navigate(appPath(parsed), true);
      return;
    }
    if (capabilities && route === "inicio" && actingAreas.length === 1) navigate(appPath(areaDestination[actingAreas[0]]), true);
  }, [capabilities, route, actingAreas.length]);
  const go = (value: AppRoute) => {
    if (!allowed(value)) {
      setNotice({ tone: "error", text: "Esta área não está disponível para a sessão atual." });
      return;
    }
    setRoute(value);
    navigate(appPath(value));
    window.scrollTo({ top: 0, behavior: "smooth" });
  };
  function content() {
    switch (route) {
      case "paciente": return <PatientDashboard session={session} capabilities={capabilities!} go={go} />;
      case "paciente-pedidos": case "pedidos": case "familia-pedidos": return <OrderPanel session={session} />;
      case "paciente-beneficios": case "beneficios": return <BenefitPanel session={session} />;
      case "perfil": return <ProfilePanel session={session} />;
      case "familia": case "familia-autorizacoes": return <FamilyPanel session={session} />;
      case "privacidade": return <PrivacyPanel session={session} />;
      case "entregador": case "entregador-cadastro": return <DelivererPanel session={session} />;
      case "entregas": return <DeliveryWorkPanel session={session} />;
      case "operacao": return <OperationalWorkspace session={session} />;
      case "catalogo": return <UnitCatalogPanel session={session} />;
      case "analise-beneficios": case "beneficios-fila": return <><MfaAccess session={session} /><BenefitReviewPanel session={session} /></>;
      case "financeiro": return <><MfaAccess session={session} />{capabilities && <FinancialWorkspace session={session} capabilities={capabilities} />}</>;
      case "operacao-privacidade": return <><MfaAccess session={session} /><PrivacyOperationsPanel session={session} /></>;
      default: return <AreaChooser areas={actingAreas} go={go} />;
    }
  }
  return (
    <div className="portfolio-shell">
      <aside className="portfolio-sidebar">
        <button className="brand brand-button" onClick={() => go("inicio")}>
          <span>EP</span>
          <strong>Exameperto</strong>
        </button>
        {actingAreas.length > 1 && (
          <label className="workspace-picker">
            Trocar área
            <select
              aria-label="Selecionar área de atuação"
              value={activeArea ?? "inicio"}
              onChange={(event) => {
                const selected = event.target.value as ActingArea | "inicio";
                go(selected === "inicio" ? "inicio" : areaDestination[selected]);
              }}
            >
              <option value="inicio">Escolher área</option>
              {actingAreas.map((area) => (
                <option value={area} key={area}>{actingAreaLabels[area]}</option>
              ))}
            </select>
          </label>
        )}
        <p className="workspace-label">{activeArea ? `ÁREA · ${actingAreaLabels[activeArea].toUpperCase()}` : "ÁREAS DISPONÍVEIS"}</p>
        <nav aria-label="Áreas do produto">
          {(activeArea ? areaRoutes[activeArea] : ["inicio" as AppRoute]).map((value) => (
            <a
              key={value}
              href={appPath(value)}
              onClick={(event) => {
                event.preventDefault();
                go(value);
              }}
              aria-current={route === value ? "page" : undefined}
            >
              {routeLabels[value]}
            </a>
          ))}
        </nav>
        <div className="shared-links" aria-label="Conta e segurança">
          <a href={appPath("perfil")} onClick={(event) => { event.preventDefault(); go("perfil"); }}>Conta e segurança</a>
          <a href={appPath("privacidade")} onClick={(event) => { event.preventDefault(); go("privacidade"); }}>Privacidade da conta</a>
        </div>
        <div className="session-tools">
          <span className="status-dot">Sessão protegida</span>
          <button
            disabled={busy}
            onClick={() => void onSessionAction("refresh")}
          >
            Renovar
          </button>
          <button
            className="secondary"
            disabled={busy}
            onClick={() => void onSessionAction("logout")}
          >
            Sair
          </button>
        </div>
      </aside>
      <main className="portfolio-content">
        <header className="portfolio-topbar">
          <div>
            <span className="eyebrow dark">EXAMEPERTO · {activeArea ? actingAreaLabels[activeArea].toUpperCase() : "SELEÇÃO DE ÁREA"}</span>
            <h1>{routeLabels[route]}</h1>
            {activeArea && <p className="acting-context">Contexto ativo: {actingAreaLabels[activeArea]}</p>}
          </div>
          <div className="role-summary">
            {capabilities?.globalRoles.map((role) => (
              <span key={role}>{role.replaceAll("_", " ")}</span>
            ))}
            {capabilities &&
              !capabilities.globalRoles.length &&
              !capabilities.institutions.length && <span>CONTA BÁSICA</span>}
          </div>
        </header>
        {activeArea && ["operacao", "beneficios", "financeiro", "privacidade-operacional"].includes(activeArea) && (
          <div className="institution-banner" aria-label="Instituição ativa">
            <span>INSTITUIÇÃO ATIVA</span>
            <strong>{capabilities?.institutions.find((item) => item.institutionId === activeInstitutionId)?.institutionName ?? "Vínculo institucional não informado"}</strong>
            <small>O backend revalida vínculo, papel e escopo em cada ação.</small>
          </div>
        )}
        {notice && (
          <p className={`feedback ${notice.tone}`} role="alert">
            {notice.text}
          </p>
        )}
        {feedback && (
          <p className={`feedback ${feedback.tone}`} role="status">
            {feedback.text}
          </p>
        )}
        {capabilities ? (
          allowed(route) ? (
            content()
          ) : (
            <section className="portfolio-home">
              <h1>Acesso não concedido</h1>
              <p>
                Esta área exige um papel nominal ativo. A navegação não altera a
                autorização do backend.
              </p>
            </section>
          )
        ) : (
          <p className="loading-state" role="status">
            Carregando capacidades da conta…
          </p>
        )}
      </main>
    </div>
  );
}

function NotFound() {
  return (
    <main className="not-found">
      <span className="eyebrow">ERRO 404</span>
      <p className="not-found-code" aria-hidden="true">
        404
      </p>
      <h1>Página não encontrada.</h1>
      <p>
        O endereço não corresponde a uma jornada disponível. Nenhuma ação foi
        executada.
      </p>
      <button onClick={() => navigate("/")}>Voltar ao início</button>
    </main>
  );
}

function AccountPage() {
  // Account access is the safe default. Registration is an explicit choice so
  // an ordinary sign-in attempt cannot create a pending account or send mail.
  const [mode, setMode] = useState<Mode>(authRouteFromPath());
  const [busy, setBusy] = useState(false);
  const [session, setSession] = useState<Tokens | null>(null);
  const [feedback, setFeedback] = useState<Notice | null>(null);
  const [emailHint, setEmailHint] = useState(
    () => window.sessionStorage.getItem("exameperto-email") ?? "",
  );
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [expired, setExpired] = useState(false);
  const [restoring, setRestoring] = useState(true);
  const delivererIntent = () =>
    window.sessionStorage.getItem("exameperto-registration-intent") === "entregador";
  useEffect(() => {
    const change = () => setMode(authRouteFromPath());
    addEventListener("popstate", change);
    return () => removeEventListener("popstate", change);
  }, []);
  useEffect(() => setPasswordVisible(false), [mode]);
  useEffect(() => {
    // Public pages must not probe CSRF with a missing refresh cookie. Session
    // restoration is only needed when a protected app URL is opened directly.
    if (!window.location.pathname.startsWith("/app")) {
      setRestoring(false);
      return;
    }
    let active = true;
    (async () => {
      try {
        const csrf = await call("csrf", {});
        const renewed = await call(
          "refresh",
          {},
          { "X-CSRF-Token": csrf.token as string },
        );
        if (active) {
          setSession(renewed as Tokens);
          if (!appRouteFromPath()) navigate("/app", true);
        }
      } catch {
        // No usable refresh cookie is the normal public-page state.
      } finally {
        if (active) setRestoring(false);
      }
    })();
    return () => {
      active = false;
    };
  }, []);
  useEffect(() => {
    if (!session) return;
    setExpired(false);
    const timer = window.setTimeout(
      () => setExpired(true),
      session.expiresIn * 1000,
    );
    return () => window.clearTimeout(timer);
  }, [session]);
  async function call(
    path: string,
    body: object,
    headers: Record<string, string> = {},
  ) {
    return (
      await parse(
        await fetch(`/api/v1/auth/${path}`, {
          method: "POST",
          credentials: "same-origin",
          headers: { "Content-Type": "application/json", ...headers },
          body: JSON.stringify(body),
        }),
      )
    ).result;
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const form = event.currentTarget;
    const fields = Object.fromEntries(new FormData(form).entries());
    setBusy(true);
    setFeedback(null);
    try {
      const result = await call(
        mode,
        mode === "login" ? { ...fields, client: "WEB" } : fields,
      );
      if (mode === "login") {
        setSession(result as Tokens);
        const destination = delivererIntent() ? "/app/entregador/cadastro" : "/app";
        window.sessionStorage.removeItem("exameperto-registration-intent");
        navigate(destination);
        setFeedback({
          tone: "info",
          text: "Sessão iniciada. Seu e-mail está confirmado; identidade e demais verificações continuam independentes.",
        });
      } else if (mode === "verification") {
        setMode("login");
        navigate("/acesso");
        setFeedback({
          tone: "info",
          text: delivererIntent()
            ? "E-mail confirmado. Entre para completar seu cadastro de entregador."
            : "E-mail confirmado. Você já pode entrar.",
        });
      }
      else if (mode === "recovery/complete") {
        setSession(null);
        setFeedback({
          tone: "info",
          text: "Senha alterada. Todas as sessões foram revogadas. Entre novamente.",
        });
      } else if (mode === "register") {
        const email = String(fields.email ?? "");
        setEmailHint(email);
        window.sessionStorage.setItem("exameperto-email", email);
        setMode("verification");
        navigate("/confirmacao");
        setFeedback({
          tone: "info",
          text: "Solicitação recebida. Se o endereço puder iniciar um cadastro, enviaremos as instruções. Se você já possui uma conta, entre ou recupere o acesso.",
        });
      } else if (mode === "recovery") {
        const email = String(fields.email ?? "");
        setEmailHint(email);
        window.sessionStorage.setItem("exameperto-email", email);
        setMode("recovery/complete");
        navigate("/recuperacao/nova-senha");
        setFeedback({
          tone: "info",
          text: "Solicitação recebida. Se a conta atender às condições, confira seu e-mail. Esta resposta não confirma envio ou entrega.",
        });
      }
      form.reset();
    } catch (error) {
      const registrationGuidance =
        mode === "register" &&
        error instanceof Error &&
        error.message.includes("integração necessária")
          ? " O cadastro não foi concluído. Se você já possui uma conta, entre ou recupere o acesso."
          : "";
      setFeedback({
        tone: "error",
        text:
          error instanceof TypeError
            ? "A conexão falhou. O resultado não pôde ser confirmado."
            : error instanceof Error
              ? `${error.message}${registrationGuidance}`
              : "Não foi possível concluir a solicitação.",
      });
    } finally {
      setBusy(false);
    }
  }
  async function sessionAction(action: "refresh" | "logout") {
    if (busy) return;
    setBusy(true);
    setFeedback(null);
    try {
      const csrf = await call("csrf", {});
      let access = session?.accessToken;
      let csrfToken = csrf.token as string;
      if (action === "logout" && expired) {
        const renewed = await call(
          "refresh",
          {},
          { "X-CSRF-Token": csrfToken },
        );
        access = renewed.accessToken;
        setSession(renewed as Tokens);
        csrfToken = (await call("csrf", {})).token as string;
      }
      const result = await call(
        action,
        {},
        {
          "X-CSRF-Token": csrfToken,
          ...(access ? { Authorization: `Bearer ${access}` } : {}),
        },
      );
      if (action === "logout")
        window.sessionStorage.removeItem("exameperto-order-draft");
      setSession(action === "refresh" ? (result as Tokens) : null);
      if (action === "logout") navigate("/acesso", true);
      setFeedback({
        tone: "info",
        text:
          action === "refresh"
            ? "Sessão renovada."
            : "Sessão encerrada no servidor.",
      });
    } catch (error) {
      setSession(null);
      setFeedback({
        tone: "error",
        text:
          error instanceof Error
            ? error.message
            : "O resultado não pôde ser confirmado.",
      });
    } finally {
      setBusy(false);
    }
  }
  const hasEmail = [
    "register",
    "verification/resend",
    "login",
    "recovery",
  ].includes(mode);
  const changeMode = (next: Mode) => {
    setMode(next);
    navigate(authPath(next));
    setFeedback(null);
  };
  if (!isKnownPublicPath() && !appRouteFromPath()) return <NotFound />;
  if (session && !expired)
    return (
      <PortfolioApp
        session={session}
        onSessionAction={sessionAction}
        busy={busy}
        feedback={feedback}
      />
    );
  if (window.location.pathname === "/") return <PublicHome />;
  return (
    <main className="auth-page">
      <header className="auth-header">
        <a
          className="public-brand"
          href="/"
          onClick={(event) => {
            event.preventDefault();
            navigate("/");
          }}
        >
          <span className="brand-mark">EP</span>
          <span>exameperto</span>
        </a>
        <span className="auth-header-note">Acesso pessoal</span>
      </header>
      <div className="auth-layout">
        <section className="auth-intro" aria-labelledby="page-title">
          <span className="eyebrow public-eyebrow">EXAMEPERTO · CONTA</span>
          <h1 id="page-title">
            Tudo perto, <em>com clareza.</em>
          </h1>
          <p>
            Entre para acompanhar seus pedidos, recebimentos e dados da conta em
            um só lugar.
          </p>
          <div className="auth-signal" aria-hidden="true">
            <span />
            <span />
            <span />
          </div>
        </section>
        <section className="auth-card" aria-label="Acesso à conta">
          <nav className="auth-links" aria-label="Navegação de acesso">
            <a
              className={mode === "login" ? "is-current" : ""}
              href={authPath("login")}
              onClick={(event) => {
                event.preventDefault();
                changeMode("login");
              }}
            >
              Entrar
            </a>
            <a
              className={mode === "register" ? "is-current" : ""}
              href={authPath("register")}
              onClick={(event) => {
                event.preventDefault();
                changeMode("register");
              }}
            >
              Criar conta
            </a>
            <a
              className={
                mode === "recovery" || mode === "recovery/complete"
                  ? "is-current"
                  : ""
              }
              href={authPath("recovery")}
              onClick={(event) => {
                event.preventDefault();
                changeMode("recovery");
              }}
            >
              Recuperar acesso
            </a>
          </nav>
          <div className="auth-card-heading">
            <span className="step">SUA CONTA</span>
            <h2>{labels[mode]}</h2>
            <p>
              {mode === "register" &&
                "Crie seu acesso para acompanhar sua jornada."}
              {mode === "login" && "Use seu e-mail e senha para continuar."}
              {mode === "verification" &&
                `Confirme o endereço ${emailHint || "informado no cadastro"}.`}
              {mode === "verification/resend" &&
                "Peça uma nova mensagem de confirmação."}
              {mode === "recovery" &&
                "Enviaremos instruções para recuperar o acesso."}
              {mode === "recovery/complete" &&
                "Defina uma nova senha com o código recebido."}
            </p>
          </div>
          {restoring ? (
            <p className="loading-state" role="status">
              Verificando seu acesso…
            </p>
          ) : (
            <form key={mode} className="auth-form" onSubmit={submit}>
              {mode === "register" && (
                <>
                  <label htmlFor="name">Nome completo</label>
                  <input
                    id="name"
                    name="name"
                    autoComplete="name"
                    required
                    maxLength={160}
                  />
                </>
              )}
              {hasEmail && (
                <>
                  <label htmlFor="email">E-mail</label>
                  <input
                    id="email"
                    name="email"
                    type="email"
                    autoComplete="email"
                    required
                    maxLength={254}
                    defaultValue={emailHint}
                  />
                </>
              )}
              {(mode === "verification" || mode === "recovery/complete") && (
                <>
                  <label htmlFor="token">Código recebido por e-mail</label>
                  <input
                    id="token"
                    name="token"
                    autoComplete="one-time-code"
                    required
                    minLength={16}
                    maxLength={512}
                  />
                </>
              )}
              {(mode === "register" ||
                mode === "login" ||
                mode === "recovery/complete") && (
                <>
                  <label htmlFor="password">
                    {mode === "recovery/complete" ? "Nova senha" : "Senha"}
                  </label>
                  <div className="password-control">
                    <input
                      id="password"
                      name={
                        mode === "recovery/complete"
                          ? "newPassword"
                          : "password"
                      }
                      type={passwordVisible ? "text" : "password"}
                      autoComplete={
                        mode === "login" ? "current-password" : "new-password"
                      }
                      required
                      minLength={mode === "login" ? 1 : 12}
                      maxLength={128}
                    />
                    <button
                      type="button"
                      className="password-toggle"
                      aria-pressed={passwordVisible}
                      onClick={() => setPasswordVisible((visible) => !visible)}
                    >
                      {passwordVisible ? "Ocultar" : "Mostrar"}
                    </button>
                  </div>
                </>
              )}
              <button className="auth-submit" type="submit" disabled={busy}>
                {busy
                  ? "Entrando…"
                  : mode === "register"
                    ? "Criar conta"
                    : labels[mode]}{" "}
                <span aria-hidden="true">→</span>
              </button>
              {feedback && (
                <p className={`feedback ${feedback.tone}`} role="alert">
                  {feedback.text}
                </p>
              )}
            </form>
          )}
          {mode === "login" && (
            <p className="auth-support">
              Ainda não tem acesso?{" "}
              <a
                href={authPath("register")}
                onClick={(event) => {
                  event.preventDefault();
                  changeMode("register");
                }}
              >
                Crie sua conta
              </a>
            </p>
          )}
          {mode === "register" && (
            <p className="auth-support">
              <a
                href={authPath("verification")}
                onClick={(event) => {
                  event.preventDefault();
                  changeMode("verification");
                }}
              >
                Continuar para confirmação
              </a>{" "}
              ·{" "}
              <a
                href={authPath("login")}
                onClick={(event) => {
                  event.preventDefault();
                  changeMode("login");
                }}
              >
                Entrar
              </a>{" "}
              ·{" "}
              <a
                href={authPath("recovery")}
                onClick={(event) => {
                  event.preventDefault();
                  changeMode("recovery");
                }}
              >
                Recuperar acesso
              </a>
            </p>
          )}
          {(mode === "verification" || mode === "verification/resend") && (
            <p className="auth-support">
              <a
                href={authPath("verification/resend")}
                onClick={(event) => {
                  event.preventDefault();
                  changeMode("verification/resend");
                }}
              >
                Reenviar confirmação
              </a>{" "}
              ·{" "}
              <a
                href={authPath("login")}
                onClick={(event) => {
                  event.preventDefault();
                  changeMode("login");
                }}
              >
                Voltar para entrar
              </a>
            </p>
          )}
          {mode === "recovery/complete" && (
            <p className="auth-support">
              <a
                href={authPath("recovery")}
                onClick={(event) => {
                  event.preventDefault();
                  changeMode("recovery");
                }}
              >
                Solicitar outro código
              </a>{" "}
              ·{" "}
              <a
                href={authPath("login")}
                onClick={(event) => {
                  event.preventDefault();
                  changeMode("login");
                }}
              >
                Voltar para entrar
              </a>
            </p>
          )}
          {mode === "recovery" && (
            <p className="auth-support">
              <a
                href={authPath("login")}
                onClick={(event) => {
                  event.preventDefault();
                  changeMode("login");
                }}
              >
                Voltar para entrar
              </a>
            </p>
          )}
        </section>
      </div>
    </main>
  );
}
createRoot(document.getElementById("root")!).render(<AccountPage />);
