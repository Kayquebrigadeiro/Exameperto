import { useEffect, useRef, useState } from "react";
import type { ChangeEvent, Dispatch, SetStateAction } from "react";
import type { FormEvent } from "react";
import { createRoot } from "react-dom/client";
import { parseApiResponse as parse, uploadWithProgress } from "./api";
import {
  appPath,
  appRouteFromPath,
  appRouteLabels as routeLabels,
  authPath,
  authRouteFromPath,
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
type Deliverer = { id: string; status: string; version: number };
type Evidence = {
  id: string;
  category: string;
  mime: string;
  size: number;
  status: string;
  version: number;
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
    const { result } = await api();
    const value = result as Profile;
    setProfile(value);
    setName(value.name);
    setPhone(value.phone ?? "");
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
      const [{ result }, patient, family] = await Promise.all([
        api("/api/v1/benefit-requests"),
        api("/api/v1/me/patient").catch(() => null),
        api("/api/v1/me/family-authorizations").catch(() => null),
      ]);
      setRequests(result);
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
  }, [session.accessToken]);
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
        <span className="form-step">ETAPA 2 · PROGRAMA</span>
        <label>
          Identificador da política vigente
          <input
            required
            value={policyId}
            onChange={(e) => setPolicyId(e.target.value)}
          />
        </label>
        <p className="dependency-note">
          O catálogo institucional ainda não possui endpoint de consulta. Use
          somente o identificador fornecido por uma instituição real habilitada;
          a ausência desse vínculo bloqueia esta etapa.
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
  async function load() {
    const h = { Authorization: `Bearer ${session.accessToken}` };
    const p = await fetch("/api/v1/me/deliverer", { headers: h });
    if (p.ok) {
      setProfile(await p.json());
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
    } else if (p.status !== 404)
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
      <div className="work-grid">
        <form className="work-card" onSubmit={save}>
          <h3>{profile ? "Atualizar cadastro" : "Criar cadastro"}</h3>
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
          <button disabled={busy}>Enviar para quarentena</button>
        </form>
      </div>
      {uploadProgress !== null && (
        <div className="upload-progress" role="status" aria-live="polite">
          <span>Enviando arquivo privado</span>
          <progress max="100" value={uploadProgress}>
            {uploadProgress}%
          </progress>
          <strong>{uploadProgress}%</strong>
        </div>
      )}
      <article className="registers">
        <h3>Histórico mínimo</h3>
        {docs.length ? (
          docs.map((d) => (
            <p key={d.id}>
              <code>{d.id}</code>
              <strong>{d.status}</strong>
              <span>
                {d.category} · {d.mime} · {Math.round(d.size / 1024)} KiB
              </span>
            </p>
          ))
        ) : (
          <p className="empty">Nenhum arquivo enviado.</p>
        )}
      </article>
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
          <label htmlFor="vehicleEvidenceFile">PDF, JPEG ou PNG (até 10 MiB)</label>
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
                <code>{vehicle.id}</code>
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
                  <code>{document.id}</code>
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
  const [secret, setSecret] = useState("");
  const [busy, setBusy] = useState(false);
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
  async function enroll(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const password = new FormData(e.currentTarget).get("password");
    await run(async () => {
      const b = await call("/me/mfa/totp/enrollment", {
        method: "POST",
        body: JSON.stringify({ password }),
      });
      setSecret(b.secret);
    }, "Segredo emitido. Confirme o código atual para elevar esta sessão.");
  }
  async function confirm(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const code = new FormData(e.currentTarget).get("code");
    await run(async () => {
      await call("/me/mfa/totp/confirmation", {
        method: "POST",
        body: JSON.stringify({ code }),
      });
    }, "MFA confirmado temporariamente nesta sessão.");
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
      <div className="work-grid">
        <form className="work-card" onSubmit={enroll}>
          <h3>Configurar MFA</h3>
          <label>Senha atual</label>
          <input
            name="password"
            type="password"
            autoComplete="current-password"
            required
          />
          <button disabled={busy}>Emitir segredo TOTP</button>
          {secret && (
            <small>
              Segredo temporário: <code>{secret}</code>
            </small>
          )}
        </form>
        <form className="work-card" onSubmit={confirm}>
          <h3>Elevar esta sessão</h3>
          <label>Código de seis dígitos</label>
          <input name="code" inputMode="numeric" pattern="[0-9]{6}" required />
          <button disabled={busy}>Confirmar MFA</button>
        </form>
      </div>
      <div className="registers vehicle-registers">
        {reviews.length ? (
          reviews.map((review) => (
            <article key={review.id}>
              <h3>
                Vínculo <code>{review.linkId}</code>
              </h3>
              <p>
                <code>{review.id}</code>
                <strong>{review.status}</strong>
                <span>snapshot da versão {review.linkVersion}</span>
                {review.status === "PENDENTE" && (
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() =>
                      void action(review, "/assign", "Revisão atribuída.")
                    }
                  >
                    Assumir revisão
                  </button>
                )}
              </p>
              {review.documents.map((document) => (
                <p key={document.id}>
                  <code>{document.id}</code>
                  <strong>{document.status}</strong>
                  <span>{document.purpose}</span>
                  {review.status === "ATRIBUIDA" && (
                    <>
                      <button
                        type="button"
                        disabled={busy || document.status !== "QUARENTENA"}
                        onClick={() =>
                          void action(
                            review,
                            `/documents/${document.id}/inspection`,
                            "Inspeção estrutural registrada.",
                          )
                        }
                      >
                        Inspecionar estrutura
                      </button>
                      <button
                        type="button"
                        disabled={
                          busy || document.status !== "INSPECAO_APROVADA"
                        }
                        onClick={() => void download(review, document)}
                      >
                        Download autorizado
                      </button>
                    </>
                  )}
                </p>
              ))}
              {review.status === "ATRIBUIDA" && (
                <button
                  type="button"
                  className="review-decision"
                  disabled={busy}
                  onClick={() =>
                    void action(review, "/decision", "Decisão registrada.")
                  }
                >
                  Solicitar decisão
                </button>
              )}
            </article>
          ))
        ) : (
          <article>
            <p className="empty">Nenhuma revisão de veículo na fila.</p>
          </article>
        )}
      </div>
      {notice && (
        <p className={`feedback ${notice.tone}`} role="alert">
          {notice.text}
        </p>
      )}
    </section>
  );
}

function OrderPanel({ session }: { session: Tokens }) {
  const [patientId, setPatientId] = useState("");
  const [recipientId, setRecipientId] = useState("");
  const [unitId, setUnitId] = useState("");
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
    latitude: "",
    longitude: "",
  });
  const [destination, setDestination] = useState({
    street: "",
    number: "",
    district: "",
    city: "",
    state: "",
    postalCode: "",
    latitude: "",
    longitude: "",
  });
  const [documentId, setDocumentId] = useState("");
  const [orders, setOrders] = useState<Order[]>([]);
  const [quotes, setQuotes] = useState<Record<string, Quote[]>>({});
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
  const [locations, setLocations] = useState<
    Record<string, LiveLocation | null>
  >({});
  const socket = useRef<WebSocket | null>(null);
  const reconnect = useRef<number | undefined>(undefined);
  const activeOrders = useRef<string[]>([]);
  const activeOrderObjects = useRef<Order[]>([]);
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
    const [b, profile, patient, family] = await Promise.all([
      api("/orders"),
      api("/me/profile").catch(() => null),
      api("/me/patient").catch(() => null),
      api("/me/family-authorizations").catch(() => null),
    ]);
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
      latitude: Number(value.latitude),
      longitude: Number(value.longitude),
    };
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
        text: "Pedido criado para verificação; nenhuma unidade foi presumida como aceita.",
      });
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
        body: JSON.stringify({}),
      });
      setQuotes((current) => ({
        ...current,
        [order.id]: [b.result as Quote, ...(current[order.id] ?? [])],
      }));
      setNotice({
        tone: "info",
        text: "Orçamento calculado com rota e tarifa versionadas.",
      });
    } catch (err) {
      setNotice({
        tone: "error",
        text: err instanceof Error ? err.message : "Orçamento não confirmado.",
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
    setter: Dispatch<SetStateAction<typeof origin>>,
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
          "latitude",
          "longitude",
        ] as const
      ).map((key) => (
        <label key={key}>
          {key}
          <input
            required
            value={value[key]}
            onChange={(e) => setter({ ...value, [key]: e.target.value })}
          />
        </label>
      ))}
    </fieldset>
  );
  return (
    <section className="family order-panel" aria-label="Pedidos e orçamentos">
      <div className="section-heading">
        <span className="eyebrow dark">PEDIDOS</span>
        <h2>Retirada e cobertura</h2>
        <p>
          Endereços ficam privados. Particular e subsidiado têm dependências
          próprias; aceite não confirma pagamento.
        </p>
      </div>
      <form className="work-card staged-form" onSubmit={create}>
        <span className="form-step">ETAPA 1 · PARTICIPANTES</span>
        <label>
          Paciente representado
          <select
            required
            value={patientId}
            onChange={(e) => setPatientId(e.target.value)}
          >
            <option value="" disabled>Selecione um perfil autorizado</option>
            {patientOptions.map((option) => <option value={option.id} key={option.id}>{option.label}</option>)}
          </select>
        </label>
        <p className="acting-context">
          Você está preparando o pedido em nome do paciente indicado. O backend
          exige titularidade ou concessão PEDIDOS vigente.
        </p>
        <label>
          Destinatário autorizado
          <select
            required
            value={recipientId}
            onChange={(e) => setRecipientId(e.target.value)}
          >
            <option value="" disabled>Selecione uma pessoa autorizada</option>
            {recipientOptions.map((option) => <option value={option.id} key={option.id}>{option.label}</option>)}
          </select>
        </label>
        <span className="form-step">ETAPA 2 · RETIRADA</span>
        <label>
          Unidade de retirada{" "}
          <input
            required
            value={unitId}
            onChange={(e) => setUnitId(e.target.value)}
          />
        </label>
        <p className="dependency-note">
          O catálogo de unidades ainda não possui endpoint de consulta. A
          unidade real precisa fornecer este identificador e manter protocolo de
          custódia habilitado; sem isso, não envie o pedido.
        </p>
        <label>
          Evidência de autorização de retirada
          <input
            type="file"
            accept="application/pdf,image/png,image/jpeg"
            onChange={upload}
            required={!documentId}
          />
        </label>
        <p>
          <code>{documentId || "Nenhuma evidência anexada"}</code>
        </p>
        <span className="form-step">ETAPA 3 · ENDEREÇOS</span>
        {fields("Origem", origin, setOrigin)}
        {fields("Destino", destination, setDestination)}
        <div className="review-summary">
          <strong>Revisão</strong>
          <span>
            Participantes selecionados; unidade informada; evidência {documentId ? "anexada" : "pendente"}. Rota, tarifa e cobertura serão verificadas depois.
          </span>
        </div>
        <button disabled={busy || !documentId || !patientId || !recipientId || !unitId}>Solicitar retirada</button>
      </form>
      <div className="registers">
        {orders.length ? (
          orders.map((order) => (
            <article key={order.id}>
              <h3>
                Pedido <code>{order.id}</code>
              </h3>
              <p>
                <strong>{order.status}</strong>
                <span>cobertura {order.coverageStatus}</span>
                <span>versão {order.version}</span>
                <span>autorização {order.pickupAuthorizationId}</span>
              </p>
              {activeOrders.current.includes(order.id) && (
                <p role="status">
                  {locations[order.id] ? (
                    <>
                      Última posição:{" "}
                      {new Date(locations[order.id]!.capturedAt).toLocaleString(
                        "pt-BR",
                      )}{" "}
                      · precisão {locations[order.id]!.accuracyMeters} m
                      {locations[order.id]!.stale ? " · desatualizada" : ""}
                      {locations[order.id]!.lowAccuracy
                        ? " · precisão baixa"
                        : ""}{" "}
                      · {locations[order.id]!.latitude},{" "}
                      {locations[order.id]!.longitude}
                    </>
                  ) : (
                    "Localização indisponível; nenhum marcador é exibido."
                  )}
                </p>
              )}
              {custodies[order.id] && (
                <p>
                  Custódia:{" "}
                  {custodies[order.id]?.status?.toLocaleLowerCase("pt-BR")}
                  {custodies[order.id]?.destinationType
                    ? ` · destino ${custodies[order.id]?.destinationType?.toLocaleLowerCase("pt-BR")}`
                    : ""}
                </p>
              )}
              {(incidents[order.id] ?? []).map((i) => (
                <p key={i.id} role="status">
                  Ocorrência: {i.type.toLocaleLowerCase("pt-BR")} ·{" "}
                  {i.status.toLocaleLowerCase("pt-BR")}
                </p>
              ))}
              {["RETIRADA", "EM_ENTREGA"].includes(order.status) && (
                <div>
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => void issueReceiptCode(order)}
                  >
                    Emitir código de recebimento
                  </button>
                  {receiptCodes[order.id] && (
                    <p role="status">
                      Código temporário:{" "}
                      <code>{receiptCodes[order.id].code}</code> · expira em{" "}
                      {new Date(
                        receiptCodes[order.id].expiresAt,
                      ).toLocaleString("pt-BR")}
                    </p>
                  )}
                </div>
              )}
              {assignments[order.id] && (
                <div className="assignment-status">
                  <p>
                    <strong>Entregador designado</strong>
                    <span>{drivers[order.id]?.displayName}</span>
                    <span>
                      {drivers[order.id]?.model} · {drivers[order.id]?.color} ·
                      placa {drivers[order.id]?.plate}
                    </span>
                    <small>
                      Designação{" "}
                      {assignments[order.id]?.status.toLocaleLowerCase("pt-BR")}{" "}
                      em{" "}
                      {new Date(
                        assignments[order.id]?.acceptedAt,
                      ).toLocaleString("pt-BR")}
                    </small>
                  </p>
                  <p>
                    Somente a identidade operacional necessária é exibida;
                    documentos profissionais e dados de saúde, renda ou
                    benefício permanecem restritos.
                  </p>
                </div>
              )}
              <button
                type="button"
                disabled={busy || order.status !== "EM_VERIFICACAO"}
                onClick={() => void quote(order)}
              >
                Calcular orçamento
              </button>
              {(quotes[order.id] ?? []).map((q) => (
                <div key={q.id}>
                  <p>
                    <strong>{q.status}</strong>
                    <span>
                      R$ {q.grossAmount} · paciente R$ {q.patientAmount} ·
                      subsídio R$ {q.subsidyAmount}
                    </span>
                    <span>
                      {q.distanceMeters} m · {q.durationSeconds}s ·{" "}
                      {q.routeProvider}
                    </span>
                    <small>
                      válido até {new Date(q.expiresAt).toLocaleString("pt-BR")}
                    </small>
                  </p>
                  {q.status === "PROPOSTO" && (
                    <button
                      type="button"
                      disabled={busy || !q.cancellationPolicyId}
                      onClick={() => void accept(order, q)}
                    >
                      Aceitar condições
                    </button>
                  )}
                </div>
              ))}
            </article>
          ))
        ) : (
          <article>
            <p className="empty">Nenhum pedido visível.</p>
          </article>
        )}
      </div>
      {notice && (
        <p className={`feedback ${notice.tone}`} role="alert">
          {notice.text}
        </p>
      )}
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
        <h3>
          {status?.verifiedForSession
            ? "Sessão elevada"
            : "Elevação necessária"}
        </h3>
        <p>
          {status?.enrolled
            ? "O fator está configurado; valide um código antes de decisões privilegiadas."
            : "Configure o fator com sua senha atual. Não há código padrão nem bypass local."}
        </p>
      </div>
      {!status?.enrolled ? (
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
            void act(e, status.verifiedForSession ? "verify" : "confirm")
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
      {secret && (
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
      api("/offers"),
      api("/me/assignments"),
      api("/me/vehicle-links"),
      api("/me/deliverer/documents"),
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
    setCustody(
      Object.fromEntries(
        states.filter(([, value]) => value).map(([id, value]) => [id, value]),
      ),
    );
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
        await api(`/orders/${task.orderId}/${action}`, {
          method: "POST",
          headers: {
            "If-Match": String(version),
            "Idempotency-Key": crypto.randomUUID(),
          },
          body: body ? JSON.stringify(body) : undefined,
        });
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
      <div className="registers">
        <article>
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
        <article>
          <h3>Designações, valores e repasses</h3>
          {assignments.length ? (
            assignments.map((task) => {
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
                  <code>{task.orderId}</code>
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
                    Consultar endereços
                  </button>
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
                        <option>AVARIA</option>
                        <option>RECUSA_RECEBIMENTO</option>
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

function OperationalReviewPanel({ session }: { session: Tokens }) {
  const [reviews, setReviews] = useState<DelivererReview[]>([]);
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
      <div className="registers">
        {reviews.length ? (
          reviews.map((item) => (
            <article key={item.id}>
              <h3>
                Entregador <code>{item.delivererId}</code>
              </h3>
              <p>
                <strong>{item.status}</strong>
                <span>documento {item.documentId}</span>
              </p>
              {item.status === "PENDENTE" && (
                <button
                  disabled={busy}
                  onClick={() => void action(item, "assign")}
                >
                  Assumir análise
                </button>
              )}
              {item.status === "ATRIBUIDA" && (
                <>
                  <button
                    disabled={busy}
                    onClick={() => void action(item, "inspection")}
                  >
                    Inspecionar arquivo
                  </button>
                  <button
                    disabled={busy}
                    onClick={() =>
                      void action(item, "decision", {
                        decision: "APROVAR",
                        reason: "ANALISE_NOMINAL",
                      })
                    }
                  >
                    Submeter decisão à política
                  </button>
                </>
              )}
            </article>
          ))
        ) : (
          <article>
            <p className="empty">Fila operacional vazia.</p>
          </article>
        )}
      </div>
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
    const assigned = list.filter((item) => item.status === "ATRIBUIDA");
    const detail = await Promise.all(
      assigned.map(
        async (item) =>
          [
            item.requestId,
            (await api(`/benefit-requests/${item.requestId}`)).result,
          ] as const,
      ),
    );
    setRequests(Object.fromEntries(detail));
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
      <div className="registers">
        {reviews.length ? (
          reviews.map((item) => (
            <article key={item.id}>
              <h3>
                {item.type} · <code>{item.requestId}</code>
              </h3>
              <p>
                <strong>{item.status}</strong>
                <span>snapshot v{item.requestVersion}</span>
              </p>
              {item.status === "PENDENTE" && (
                <button
                  disabled={busy}
                  onClick={() =>
                    void action(`/analyst/benefit-reviews/${item.id}/assign`)
                  }
                >
                  Assumir análise
                </button>
              )}
              {item.status === "ATRIBUIDA" && (
                <>
                  {(requests[item.requestId]?.evidence ?? []).map(
                    (document) => (
                      <button
                        key={document.id}
                        disabled={busy || document.status !== "QUARENTENA"}
                        onClick={() =>
                          void action(
                            `/analyst/benefit-reviews/${item.id}/documents/${document.id}/inspection`,
                          )
                        }
                      >
                        Inspecionar {document.purpose}
                      </button>
                    ),
                  )}
                  <form
                    onSubmit={(e) => {
                      e.preventDefault();
                      const data = new FormData(e.currentTarget);
                      void action(
                        `/analyst/benefit-reviews/${item.id}/decision`,
                        {
                          decision: data.get("decision"),
                          percentage: Number(data.get("percentage")),
                          reason: data.get("reason"),
                        },
                      );
                    }}
                  >
                    <label>
                      Decisão
                      <select name="decision">
                        <option>APROVADA</option>
                        <option>REJEITADA</option>
                      </select>
                    </label>
                    <label>
                      Percentual
                      <input
                        name="percentage"
                        type="number"
                        min="0"
                        max="100"
                        step="0.01"
                        required
                      />
                    </label>
                    <label>
                      Motivo
                      <input name="reason" required />
                    </label>
                    <button disabled={busy}>Registrar decisão</button>
                  </form>
                </>
              )}
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
      <div className="registers">
        {items.length ? (
          items.map((item) => (
            <article key={item.id}>
              <h3>{item.protocol}</h3>
              <p>
                <strong>{item.status}</strong>
                <span>
                  {item.type} · encerramento {item.closureStatus}
                </span>
              </p>
              {["PENDENTE", "EM_ANALISE"].includes(item.status) && (
                <form
                  onSubmit={(e) => {
                    e.preventDefault();
                    const response = String(
                      new FormData(e.currentTarget).get("response"),
                    );
                    void action(`/privacy-requests/${item.id}/response`, {
                      status: "RESPONDIDA",
                      response,
                    });
                  }}
                >
                  <label>
                    Resposta ao titular
                    <textarea name="response" required />
                  </label>
                  <button disabled={busy}>Registrar resposta</button>
                </form>
              )}
              {item.type === "EXCLUSAO" && item.status === "RESPONDIDA" && (
                <button
                  disabled={busy}
                  onClick={() =>
                    void action(`/privacy-requests/${item.id}/purge`)
                  }
                >
                  Autorizar expurgo conforme política
                </button>
              )}
              {item.status === "EXPURGO_SOLICITADO" && (
                <button
                  disabled={busy}
                  onClick={() =>
                    void action(`/privacy-requests/${item.id}/purge/execute`)
                  }
                >
                  Executar alvos controlados
                </button>
              )}
              {item.status === "EXPURGADA" && (
                <button
                  disabled={busy}
                  onClick={() =>
                    void action(`/privacy-requests/${item.id}/purge/verify`)
                  }
                >
                  Verificar execução
                </button>
              )}
            </article>
          ))
        ) : (
          <article>
            <p className="empty">Nenhuma solicitação operacional pendente.</p>
          </article>
        )}
      </div>
      {notice && (
        <p className={`feedback ${notice.tone}`} role="alert">
          {notice.text}
        </p>
      )}
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
  useEffect(() => {
    fetch("/api/v1/me/capabilities", {
      headers: { Authorization: `Bearer ${session.accessToken}` },
    })
      .then(parse)
      .then((value) => setCapabilities(value.result as Capabilities))
      .catch((error) =>
        setNotice({
          tone: "error",
          text:
            error instanceof Error
              ? error.message
              : "Capacidades indisponíveis.",
        }),
      );
  }, [session.accessToken]);
  useEffect(() => {
    const change = () => setRoute(appRouteFromPath() ?? "inicio");
    addEventListener("popstate", change);
    return () => removeEventListener("popstate", change);
  }, []);
  const allowed = (value: AppRoute) =>
    ![
      "operacao",
      "analise-beneficios",
      "financeiro",
      "operacao-privacidade",
    ].includes(value) ||
    capabilities?.areas.includes(
      value === "operacao"
        ? "OPERACAO"
        : value === "analise-beneficios"
          ? "ANALISE_BENEFICIO"
          : value === "financeiro"
            ? "FINANCEIRO"
            : "OPERACAO_PRIVACIDADE",
    );
  const routes = (Object.keys(routeLabels) as AppRoute[]).filter(allowed);
  const workspaces = [
    { label: "Minha conta", route: "inicio" as AppRoute },
    ...(capabilities?.delivererProfile
      ? [{ label: "Trabalho de entrega", route: "entregador" as AppRoute }]
      : []),
    ...(capabilities?.areas.includes("OPERACAO")
      ? [{ label: "Operação", route: "operacao" as AppRoute }]
      : []),
    ...(capabilities?.areas.includes("ANALISE_BENEFICIO")
      ? [
          {
            label: "Análise de benefícios",
            route: "analise-beneficios" as AppRoute,
          },
        ]
      : []),
    ...(capabilities?.areas.includes("FINANCEIRO")
      ? [{ label: "Financeiro", route: "financeiro" as AppRoute }]
      : []),
    ...(capabilities?.areas.includes("OPERACAO_PRIVACIDADE")
      ? [
          {
            label: "Privacidade operacional",
            route: "operacao-privacidade" as AppRoute,
          },
        ]
      : []),
  ];
  const go = (value: AppRoute) => {
    setRoute(value);
    navigate(appPath(value));
    window.scrollTo({ top: 0, behavior: "smooth" });
  };
  function content() {
    switch (route) {
      case "perfil":
        return <ProfilePanel session={session} />;
      case "familia":
        return <FamilyPanel session={session} />;
      case "beneficios":
        return <BenefitPanel session={session} />;
      case "pedidos":
        return <OrderPanel session={session} />;
      case "privacidade":
        return <PrivacyPanel session={session} />;
      case "entregador":
        return <DelivererPanel session={session} />;
      case "entregas":
        return <DeliveryWorkPanel session={session} />;
      case "operacao":
        return (
          <>
            <MfaAccess session={session} />
            <OperationalReviewPanel session={session} />
            <VehicleReviewPanel session={session} />
          </>
        );
      case "analise-beneficios":
        return (
          <>
            <MfaAccess session={session} />
            <BenefitReviewPanel session={session} />
          </>
        );
      case "financeiro":
        return (
          <>
            <MfaAccess session={session} />
            <FundingPanel session={session} />
            <PayoutPanel session={session} />
          </>
        );
      case "operacao-privacidade":
        return (
          <>
            <MfaAccess session={session} />
            <PrivacyOperationsPanel session={session} />
          </>
        );
      default:
        return (
          <section className="portfolio-home" aria-labelledby="portfolio-title">
            <span className="eyebrow dark">CENTRAL DE OPERAÇÕES CLÍNICAS</span>
            <h1 id="portfolio-title">Olá. O que precisa avançar hoje?</h1>
            <p>
              Escolha uma área. Cada módulo mostra somente ações reais da sua
              conta e explica quando política, outra pessoa ou integração ainda
              são necessárias.
            </p>
            <div className="overview-grid">
              <article>
                <strong>
                  {capabilities?.patientProfile
                    ? "Perfil criado"
                    : "Próximo passo"}
                </strong>
                <h2>Paciente</h2>
                <p>
                  {capabilities?.patientProfile
                    ? "Acompanhe benefícios, pedidos e autorizações."
                    : "Crie seu perfil de paciente antes de solicitar benefícios ou retiradas."}
                </p>
                <a
                  href={appPath("familia")}
                  onClick={(event) => {
                    event.preventDefault();
                    go("familia");
                  }}
                >
                  Abrir perfil e família
                </a>
              </article>
              <article>
                <strong>
                  {capabilities?.delivererProfile
                    ? "Cadastro iniciado"
                    : "Opcional"}
                </strong>
                <h2>Entregador</h2>
                <p>
                  Documentos e veículo permanecem pendentes até análise humana e
                  política aplicável.
                </p>
                <a
                  href={appPath("entregador")}
                  onClick={(event) => {
                    event.preventDefault();
                    go("entregador");
                  }}
                >
                  Abrir cadastro
                </a>
              </article>
              <article>
                <strong>
                  {capabilities?.globalRoles.length ||
                  capabilities?.institutions.length
                    ? "Acesso nominal"
                    : "Sem papel privilegiado"}
                </strong>
                <h2>Áreas de trabalho</h2>
                <p>
                  {capabilities?.globalRoles.length ||
                  capabilities?.institutions.length
                    ? "Use o seletor lateral; o backend revalida papel, instituição e MFA."
                    : "Papéis locais são concedidos apenas pela ferramenta de terminal auditada."}
                </p>
              </article>
              <article className="external-block">
                <strong>Dependências reais</strong>
                <h2>Sem sucesso artificial</h2>
                <p>
                  Rotas, pagamentos, repasses, políticas e instituições ausentes
                  continuam bloqueados com uma explicação específica.
                </p>
              </article>
            </div>
          </section>
        );
    }
  }
  return (
    <div className="portfolio-shell">
      <aside className="portfolio-sidebar">
        <button className="brand brand-button" onClick={() => go("inicio")}>
          <span>EP</span>
          <strong>Exameperto</strong>
        </button>
        {workspaces.length > 1 && (
          <label className="workspace-picker">
            Área de trabalho
            <select
              aria-label="Selecionar área"
              value={
                workspaces.find((item) => item.route === route)?.route ??
                "inicio"
              }
              onChange={(event) => go(event.target.value as AppRoute)}
            >
              {workspaces.map((item) => (
                <option value={item.route} key={item.route}>
                  {item.label}
                </option>
              ))}
            </select>
          </label>
        )}
        <p className="workspace-label">ÁREAS DA CONTA</p>
        <nav aria-label="Áreas do produto">
          {routes.map((value) => (
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
            <span className="eyebrow dark">PORTFÓLIO LOCAL · 127.0.0.1</span>
            <h1>{routeLabels[route]}</h1>
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
  const [expired, setExpired] = useState(false);
  const [restoring, setRestoring] = useState(true);
  useEffect(() => {
    const change = () => setMode(authRouteFromPath());
    addEventListener("popstate", change);
    return () => removeEventListener("popstate", change);
  }, []);
  useEffect(() => {
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
        navigate("/app");
        setFeedback({
          tone: "info",
          text: "Sessão iniciada. Seu e-mail está confirmado; identidade e demais verificações continuam independentes.",
        });
      } else if (mode === "verification")
        setFeedback({
          tone: "info",
          text: "E-mail confirmado. Você já pode entrar.",
        });
      else if (mode === "recovery/complete") {
        setSession(null);
        setFeedback({
          tone: "info",
          text: "Senha alterada. Todas as sessões foram revogadas. Entre novamente.",
        });
      } else
        setFeedback({
          tone: "info",
          text: "Solicitação recebida. Se a conta atender às condições, confira seu e-mail. Esta resposta não confirma envio ou entrega.",
        });
      form.reset();
    } catch (error) {
      setFeedback({
        tone: "error",
        text:
          error instanceof TypeError
            ? "A conexão falhou. O resultado não pôde ser confirmado."
            : error instanceof Error
              ? error.message
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
  return (
    <>
      <main className="shell">
        <section className="intro" aria-labelledby="page-title">
          <span className="eyebrow">EXAME PERTO · ACESSO</span>
          <h1 id="page-title">
            Seu cuidado,
            <br />
            <em>sob seu controle.</em>
          </h1>
          <p className="lede">
            Retirada autorizada e entrega de resultados em envelope fechado, com
            custódia rastreável e acesso sob seu controle.
          </p>
          <ol className="service-steps" aria-label="Como o serviço funciona">
            <li>
              <strong>Solicite</strong>
              <span>Escolha paciente, unidade e destinatário autorizado.</span>
            </li>
            <li>
              <strong>Acompanhe</strong>
              <span>Veja orçamento, custódia e próximos passos reais.</span>
            </li>
            <li>
              <strong>Receba</strong>
              <span>Confirme a entrega sem expor o conteúdo clínico.</span>
            </li>
          </ol>
          <p className="quiet">
            <span className="dot" aria-hidden="true" /> E-mail confirmado não
            comprova identidade, elegibilidade ou aprovação profissional.
          </p>
        </section>
        <section className="card" aria-label="Acesso à conta">
          {!session && (
            <nav aria-label="Opções de acesso">
              {(Object.keys(labels) as Mode[]).map((key) => (
                <button
                  type="button"
                  className="tab"
                  aria-pressed={mode === key}
                  disabled={busy}
                  key={key}
                  onClick={() => {
                    setMode(key);
                    navigate(authPath(key));
                    setFeedback(null);
                  }}
                >
                  {labels[key]}
                </button>
              ))}
            </nav>
          )}
          <div className="card-header">
            <span className="step">SUA CONTA</span>
            <h2>{session ? "Conta conectada" : labels[mode]}</h2>
            <p>
              Conta básica, sem concessão automática de identidade ou
              privilégios.
            </p>
          </div>
          {!session && !restoring && (
            <form key={mode} onSubmit={submit}>
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
                  />
                </>
              )}
              {(mode === "verification" || mode === "recovery/complete") && (
                <>
                  <label htmlFor="token">Código recebido por e-mail</label>
                  <input
                    id="token"
                    name="token"
                    autoComplete="off"
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
                  <input
                    id="password"
                    name={
                      mode === "recovery/complete" ? "newPassword" : "password"
                    }
                    type="password"
                    autoComplete={
                      mode === "login" ? "current-password" : "new-password"
                    }
                    required
                    minLength={mode === "login" ? 1 : 12}
                    maxLength={128}
                  />
                </>
              )}
              <button type="submit" disabled={busy}>
                {busy ? "Aguarde…" : labels[mode]}{" "}
                <span aria-hidden="true">→</span>
              </button>
            </form>
          )}
          <section className="session" aria-label="Sessão">
            <p>
              {restoring
                ? "Verificando uma sessão anterior neste navegador."
                : session
                  ? expired
                    ? "Acesso expirado. Renove para continuar."
                    : "Sessão ativa neste navegador."
                  : "Sem sessão ativa em memória."}
            </p>
            <button
              disabled={busy || restoring}
              onClick={() => sessionAction("refresh")}
            >
              Renovar sessão
            </button>
            {session && (
              <button disabled={busy} onClick={() => sessionAction("logout")}>
                Sair
              </button>
            )}
          </section>
          {feedback && (
            <p className={`feedback ${feedback.tone}`} role="alert">
              {feedback.text}
            </p>
          )}
          <p className="legal">
            Representação legal e menores permanecem fora deste fluxo. Convites
            dependem de canal privado habilitado.
          </p>
        </section>
      </main>
    </>
  );
}
createRoot(document.getElementById("root")!).render(<AccountPage />);
