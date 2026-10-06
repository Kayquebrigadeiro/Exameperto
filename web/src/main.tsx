import { useEffect, useState } from "react";
import type { ChangeEvent, Dispatch, SetStateAction } from "react";
import type { FormEvent } from "react";
import { createRoot } from "react-dom/client";
import "./styles.css";

type Mode =
  | "register"
  | "verification"
  | "verification/resend"
  | "login"
  | "recovery"
  | "recovery/complete";
type Tokens = { accessToken: string; expiresIn: number };
type Notice = { tone: "error" | "info"; text: string };
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
  id: string; plate: string; make: string; model: string; color: string;
  manufacturingYear: number; modelYear: number; linkType: string;
  validUntil: string | null; status: string; version: number;
  evidenceComplete: boolean; documents: VehicleEvidence[];
};
type VehicleReview = { id: string; linkId: string; linkVersion: number; analystId: string | null; status: string; documents: VehicleEvidence[] };
type BenefitRequest = { id: string; status: string; policyVersion: number; dimensions: string[]; percentage: number | null; reason: string | null; version: number };
type Funding = { id: string; programId: string; amount: number; currency: string; status: string; evidenceDocumentId: string; reconciliationEvidenceId: string | null; version: number };
type Order = { id: string; patientId: string; status: string; coverageStatus: string; createdAt: string; version: number; pickupAuthorizationId: string; pickupUnitId: string };
type Quote = { id: string; orderId: string; status: string; grossAmount: number; patientAmount: number; subsidyAmount: number; tariffVersion: number; routeProvider: string; routeReference: string; distanceMeters: number; durationSeconds: number; trafficIncluded: boolean; calculatedAt: string; expiresAt: string; version: number };
const scopes = ["PEDIDOS", "BENEFICIOS", "RASTREAMENTO", "RECEBIMENTO"];
const labels: Record<Mode, string> = {
  register: "Solicitar cadastro",
  verification: "Confirmar e-mail",
  "verification/resend": "Reenviar confirmação",
  login: "Entrar",
  recovery: "Recuperar acesso",
  "recovery/complete": "Trocar senha",
};

async function parse(response: Response) {
  const result =
    response.status === 204 ? {} : await response.json().catch(() => ({}));
  if (!response.ok) {
    const message =
      result.code === "INTEGRATION_UNAVAILABLE"
        ? "O serviço de conta, e-mail ou canal privado está indisponível. Não foi possível concluir a solicitação."
        : result.code === "POLICY_UNDEFINED"
          ? "A operação aguarda a validação da política aplicável."
          : (result.message ?? "Não foi possível concluir a solicitação.");
    throw new Error(message);
  }
  return { result, etag: response.headers.get("ETag") };
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

function FamilyPanel({ session }: { session: Tokens }) {
  const [patient, setPatient] = useState<Patient | null>(null);
  const [invitations, setInvitations] = useState<Invitation[]>([]);
  const [received, setReceived] = useState<Invitation[]>([]);
  const [grants, setGrants] = useState<Grant[]>([]);
  const [familyGrants, setFamilyGrants] = useState<Grant[]>([]);
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
    setFamilyGrants((familyData.result as { items: Grant[] }).items);
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
  const [dimensions, setDimensions] = useState<string[]>(["IDADE"]);
  const [requests, setRequests] = useState<BenefitRequest[]>([]);
  const [notice, setNotice] = useState<Notice | null>(null);
  async function api(path: string, init: RequestInit = {}) {
    const response = await fetch(path, { ...init, headers: { Authorization: `Bearer ${session.accessToken}`, "Content-Type": "application/json", ...(init.headers ?? {}) } });
    return parse(response);
  }
  async function load(showNotice = true) { try { const { result } = await api("/api/v1/benefit-requests"); setRequests(result); } catch (e) { if (showNotice) setNotice({ tone: "error", text: (e as Error).message }); } }
  useEffect(() => { void load(false); }, [session.accessToken]);
  async function create(event: FormEvent) {
    event.preventDefault(); setNotice(null);
    try { await api("/api/v1/benefit-requests", { method: "POST", body: JSON.stringify({ patientId: (await api("/api/v1/me/patient")).result.id, policyId, dimensions }) }); setNotice({ tone: "info", text: "Solicitação registrada para análise humana." }); await load(); }
    catch (e) { setNotice({ tone: "error", text: (e as Error).message }); }
  }
  return <section className="panel" aria-label="Benefícios"><h2>Solicitação de benefício</h2><p>Dimensões independentes; documentos e critérios permanecem sujeitos à política vigente.</p><form onSubmit={create}><label>UUID da política vigente<input required value={policyId} onChange={e => setPolicyId(e.target.value)} /></label><fieldset><legend>Dimensões</legend>{["IDADE", "DEFICIENCIA", "RENDA"].map(d => <label className="check" key={d}><input type="checkbox" checked={dimensions.includes(d)} onChange={e => setDimensions(e.target.checked ? [...dimensions, d] : dimensions.filter(x => x !== d))} /> {d}</label>)}</fieldset><button type="submit">Solicitar análise</button></form>{notice && <p className={`feedback ${notice.tone}`} role="alert">{notice.text}</p>}<div className="cards">{requests.map(r => <article className="card" key={r.id}><strong>{r.status}</strong><p>Dimensões: {r.dimensions.join(", ")} · política v{r.policyVersion}</p><p>{r.reason ?? "Aguardando análise humana."}</p></article>)}</div></section>;
}

function FundingPanel({ session }: { session: Tokens }) {
  const [programId, setProgramId] = useState(""); const [amount, setAmount] = useState(""); const [sourceReference, setSourceReference] = useState(""); const [evidenceId, setEvidenceId] = useState(""); const [reconciliationId, setReconciliationId] = useState(""); const [reason, setReason] = useState(""); const [funding, setFunding] = useState<Funding[]>([]); const [balance, setBalance] = useState<{ available: number } | null>(null); const [notice, setNotice] = useState<Notice | null>(null); const [mfaSecret, setMfaSecret] = useState("");
  async function api(path: string, init: RequestInit = {}) { const response = await fetch(path, { ...init, headers: { Authorization: `Bearer ${session.accessToken}`, "Content-Type": "application/json", ...(init.headers ?? {}) } }); return parse(response); }
  async function load() { if (!programId) return; try { const [{ result: list }, { result: current }] = await Promise.all([api(`/api/v1/programs/${programId}/funding`), api(`/api/v1/programs/${programId}/balance`)]); setFunding(list); setBalance(current); } catch (e) { setNotice({ tone: "error", text: (e as Error).message }); } }
  async function upload(event: FormEvent<HTMLInputElement>, reconciliation = false) { const file = event.currentTarget.files?.[0]; if (!file) return; const data = new FormData(); data.append("file", file); try { const response = await fetch("/api/v1/me/financial-documents", { method: "POST", headers: { Authorization: `Bearer ${session.accessToken}` }, body: data }); const parsed = await parse(response); if (reconciliation) setReconciliationId(parsed.result.id); else setEvidenceId(parsed.result.id); setNotice({ tone: "info", text: reconciliation ? "Conciliação anexada; inspecione o arquivo antes de revisar." : "Evidência privada anexada; ainda não comprova transferência." }); } catch (e) { setNotice({ tone: "error", text: (e as Error).message }); } }
  async function record(event: FormEvent) { event.preventDefault(); try { await api(`/api/v1/programs/${programId}/funding`, { method: "POST", headers: { "Idempotency-Key": crypto.randomUUID() }, body: JSON.stringify({ amount, currency: "BRL", evidenceDocumentId: evidenceId, sourceReference }) }); setNotice({ tone: "info", text: "Aporte registrado como pendente de conciliação independente." }); await load(); } catch (e) { setNotice({ tone: "error", text: (e as Error).message }); } }
  async function enroll(event: FormEvent) { event.preventDefault(); try { const { result } = await api("/api/v1/me/mfa/totp/enrollment", { method: "POST", body: JSON.stringify({ password: new FormData(event.currentTarget as HTMLFormElement).get("password") }) }); setMfaSecret(result.secret); setNotice({ tone: "info", text: "Segredo MFA emitido; confirme o código para elevar esta sessão." }); } catch (e) { setNotice({ tone: "error", text: (e as Error).message }); } }
  async function confirmMfa(event: FormEvent) { event.preventDefault(); try { await api("/api/v1/me/mfa/totp/confirmation", { method: "POST", body: JSON.stringify({ code: new FormData(event.currentTarget as HTMLFormElement).get("code") }) }); setNotice({ tone: "info", text: "MFA confirmado nesta sessão." }); } catch (e) { setNotice({ tone: "error", text: (e as Error).message }); } }
  async function inspect(item: Funding) { try { await api(`/api/v1/funding/${item.id}/review/evidence/${reconciliationId}/inspection`, { method: "POST" }); setNotice({ tone: "info", text: "Evidência de conciliação inspecionada." }); } catch (e) { setNotice({ tone: "error", text: (e as Error).message }); } }
  async function decide(item: Funding, decision: "CONFIRMADO" | "REJEITADO") { try { await api(`/api/v1/funding/${item.id}/review`, { method: "POST", headers: { "If-Match": String(item.version), "Idempotency-Key": crypto.randomUUID() }, body: JSON.stringify({ decision, reconciliationEvidenceId: reconciliationId, reasonCode: reason || (decision === "CONFIRMADO" ? "CONCILIADO" : "REJEITADO") }) }); setNotice({ tone: "info", text: decision === "CONFIRMADO" ? "Aporte confirmado; disponibilidade atualizada." : "Aporte rejeitado; disponibilidade inalterada." }); await load(); } catch (e) { setNotice({ tone: "error", text: (e as Error).message }); } }
  return <section className="family" aria-label="Aportes institucionais"><div className="section-heading"><span className="eyebrow dark">PAINEL FINANCEIRO</span><h2>Aportes e disponibilidade</h2><p>Exige gestor nominal, MFA e evidência de conciliação. Registro pendente não aumenta saldo.</p></div><div className="work-grid"><form className="work-card" onSubmit={enroll}><h3>Configurar MFA</h3><label>Senha para MFA<input name="password" type="password" required /></label><button type="submit">Emitir segredo MFA</button>{mfaSecret && <small>Segredo temporário: <code>{mfaSecret}</code></small>}</form><form className="work-card" onSubmit={confirmMfa}><h3>Elevar sessão</h3><label>Código MFA<input name="code" inputMode="numeric" pattern="[0-9]{6}" required /></label><button type="submit">Confirmar MFA</button></form></div><form className="work-card" onSubmit={record}><label>UUID do programa<input required value={programId} onChange={e => setProgramId(e.target.value)} /></label><label>Valor em BRL<input required inputMode="decimal" value={amount} onChange={e => setAmount(e.target.value)} /></label><label>Referência de origem<input required value={sourceReference} onChange={e => setSourceReference(e.target.value)} /></label><label>Evidência privada<input required type="file" accept="application/pdf,image/png,image/jpeg" onChange={e => void upload(e)} /></label><p><code>{evidenceId || "Nenhuma evidência anexada"}</code></p><button type="submit" disabled={!evidenceId}>Registrar aporte</button><button type="button" onClick={() => void load()}>Atualizar situação</button></form><div className="work-card"><h3>Conciliação do aporte pendente</h3><label>Evidência de conciliação<input type="file" accept="application/pdf,image/png,image/jpeg" onChange={e => void upload(e, true)} /></label><p><code>{reconciliationId || "Nenhuma conciliação anexada"}</code></p><label>Motivo da revisão<input value={reason} onChange={e => setReason(e.target.value)} /></label></div>{balance && <p className="feedback info">Disponibilidade confirmada: {balance.available} BRL</p>}{notice && <p className={`feedback ${notice.tone}`} role="alert">{notice.text}</p>}<div className="registers">{funding.map(item => <article key={item.id}><strong>{item.status}</strong><p>{item.amount} {item.currency} · versão {item.version}</p>{item.status === "PENDENTE" && <><button type="button" disabled={!reconciliationId} onClick={() => void inspect(item)}>Inspecionar conciliação</button><button type="button" disabled={!reconciliationId} onClick={() => void decide(item, "CONFIRMADO")}>Confirmar aporte</button><button type="button" disabled={!reconciliationId} onClick={() => void decide(item, "REJEITADO")}>Rejeitar aporte</button></>}</article>)}</div></section>;
}

function DelivererPanel({ session }: { session: Tokens }) {
  const [profile, setProfile] = useState<Deliverer | null>(null);
  const [docs, setDocs] = useState<Evidence[]>([]);
  const [birthDate, setBirthDate] = useState("");
  const [category, setCategory] = useState("HABILITACAO");
  const [vehicles, setVehicles] = useState<VehicleLink[]>([]);
  const [selectedVehicle, setSelectedVehicle] = useState<VehicleLink | null>(null);
  const [vehiclePurpose, setVehiclePurpose] = useState("CRLV");
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
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
          setSelectedVehicle(links.find((item) => item.id === selectedVehicle.id) ?? null);
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
    setNotice(null);
    try {
      const r = await fetch("/api/v1/me/deliverer/documents", {
        method: "POST",
        headers: { Authorization: `Bearer ${session.accessToken}` },
        body: data,
      });
      const b = await r.json();
      if (!r.ok) throw new Error(b.message ?? "Upload não concluído.");
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
    }
  }
  async function saveVehicle(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); const form=e.currentTarget; const data=new FormData(form);
    setBusy(true); setNotice(null);
    try {
      const payload={plate:data.get("plate"),make:data.get("make"),model:data.get("model"),color:data.get("color"),manufacturingYear:Number(data.get("manufacturingYear")),modelYear:Number(data.get("modelYear")),linkType:data.get("linkType"),validUntil:data.get("validUntil")||null};
      const path=selectedVehicle?`/api/v1/me/vehicle-links/${selectedVehicle.id}?version=${selectedVehicle.version}`:"/api/v1/me/vehicle-links";
      const r=await fetch(path,{method:selectedVehicle?"PUT":"POST",headers:{"Content-Type":"application/json",Authorization:`Bearer ${session.accessToken}`},body:JSON.stringify(payload)});
      const b=await r.json(); if(!r.ok)throw new Error(b.message??"Vínculo não salvo."); setSelectedVehicle(b); await load();
      setNotice({tone:"info",text:"Vínculo salvo como pendente. Nenhuma consulta oficial foi simulada."});
    } catch(e){setNotice({tone:"error",text:e instanceof Error?e.message:"Resultado não confirmado."});} finally{setBusy(false);}
  }
  async function uploadVehicle(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); if(!selectedVehicle)return; const form=e.currentTarget; const file=(form.elements.namedItem("vehicleFile") as HTMLInputElement).files?.[0]; if(!file)return;
    const data=new FormData(); data.append("version",String(selectedVehicle.version)); data.append("purpose",vehiclePurpose); data.append("file",file); setBusy(true); setNotice(null);
    try{const r=await fetch(`/api/v1/me/vehicle-links/${selectedVehicle.id}/documents`,{method:"POST",headers:{Authorization:`Bearer ${session.accessToken}`},body:data});const b=await r.json();if(!r.ok)throw new Error(b.message??"Upload não concluído.");setSelectedVehicle(b);await load();form.reset();setNotice({tone:"info",text:"Arquivo em quarentena. A substituição abriu uma revisão nova, sem herdar aprovação."});}
    catch(e){setNotice({tone:"error",text:e instanceof Error?e.message:"Resultado não confirmado."});}finally{setBusy(false);}
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
        <p>Propriedade não é requisito universal. Registre veículo próprio, alugado ou autorizado; CRLV, fotos e autorização ficam privados e sujeitos a nova revisão quando substituídos.</p>
      </div>
      <div className="work-grid">
        <form key={selectedVehicle?.id??"new-vehicle"} className="work-card" onSubmit={saveVehicle}>
          <h3>{selectedVehicle?"Alterar veículo selecionado":"Cadastrar veículo"}</h3>
          <label>Placa</label><input name="plate" defaultValue={selectedVehicle?.plate} pattern="[A-Za-z0-9 -]{7,10}" required />
          <label>Marca</label><input name="make" defaultValue={selectedVehicle?.make} maxLength={80} required />
          <label>Modelo</label><input name="model" defaultValue={selectedVehicle?.model} maxLength={80} required />
          <label>Cor</label><input name="color" defaultValue={selectedVehicle?.color} maxLength={40} required />
          <div className="inline-fields"><label>Ano de fabricação<input name="manufacturingYear" type="number" min="1886" defaultValue={selectedVehicle?.manufacturingYear} required /></label><label>Ano do modelo<input name="modelYear" type="number" min="1886" defaultValue={selectedVehicle?.modelYear} required /></label></div>
          <label>Vínculo</label><select name="linkType" defaultValue={selectedVehicle?.linkType??"PROPRIEDADE"}><option value="PROPRIEDADE">Próprio</option><option value="LOCACAO">Alugado</option><option value="AUTORIZACAO">Uso autorizado</option></select>
          <label>Validade, quando aplicável</label><input name="validUntil" type="date" defaultValue={selectedVehicle?.validUntil??""}/>
          <button disabled={busy}>{selectedVehicle?"Salvar alteração e revisar novamente":"Criar vínculo pendente"}</button>
        </form>
        <form className="work-card" onSubmit={uploadVehicle}>
          <h3>Evidência do vínculo</h3><p>Selecione um vínculo abaixo. Cada substituição preserva o histórico e invalida a revisão anterior.</p>
          <label>Finalidade</label><select value={vehiclePurpose} onChange={(e)=>setVehiclePurpose(e.target.value)}><option value="CRLV">CRLV</option><option value="FOTO">Foto do veículo</option>{selectedVehicle?.linkType!=="PROPRIEDADE"&&<option value="USO_AUTORIZADO">Locação ou autorização de uso</option>}</select>
          <label>PDF, JPEG ou PNG (até 10 MiB)</label><input name="vehicleFile" type="file" accept="application/pdf,image/jpeg,image/png" required />
          <button disabled={busy||!selectedVehicle}>Enviar para quarentena</button>
          <small>Inspeção estrutural não comprova autenticidade e não substitui varredura antimalware.</small>
        </form>
      </div>
      <div className="registers vehicle-registers">
        {vehicles.length?vehicles.map((vehicle)=><article key={vehicle.id}><h3>{vehicle.plate} · {vehicle.model}</h3><p><code>{vehicle.id}</code><strong>{vehicle.status}</strong><span>{vehicle.linkType} · versão {vehicle.version} · evidências {vehicle.evidenceComplete?"completas":"incompletas"}</span><button type="button" onClick={()=>setSelectedVehicle(vehicle)}>Selecionar</button></p>{vehicle.documents.map((document)=><p key={`${document.id}-${document.purpose}`}><code>{document.id}</code><strong>{document.status}</strong><span>{document.purpose}{document.current?"":" · substituído"}</span></p>)}</article>):<article><p className="empty">Nenhum vínculo cadastrado.</p></article>}
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
  const [reviews,setReviews]=useState<VehicleReview[]>([]); const [available,setAvailable]=useState(false); const [notice,setNotice]=useState<Notice|null>(null); const [secret,setSecret]=useState(""); const [busy,setBusy]=useState(false);
  async function call(path:string,init:RequestInit={}){const r=await fetch(`/api/v1${path}`,{...init,headers:{...(init.body?{"Content-Type":"application/json"}:{}),Authorization:`Bearer ${session.accessToken}`,...(init.headers??{})}});if(r.status===403&&path==="/analyst/vehicle-reviews"){setAvailable(false);return null;}const b=await r.json().catch(()=>({}));if(!r.ok)throw new Error(b.message??"Ação operacional não concluída.");return b;}
  async function load(){const b=await call("/analyst/vehicle-reviews");if(b){setAvailable(true);setReviews(b as VehicleReview[]);}}
  useEffect(()=>{load().catch(e=>setNotice({tone:"error",text:e.message}));},[session.accessToken]);
  async function run(action:()=>Promise<void>,text:string){setBusy(true);setNotice(null);try{await action();await load();setNotice({tone:"info",text});}catch(e){setNotice({tone:"error",text:e instanceof Error?e.message:"Resultado não confirmado."});}finally{setBusy(false);}}
  async function enroll(e:FormEvent<HTMLFormElement>){e.preventDefault();const password=new FormData(e.currentTarget).get("password");await run(async()=>{const b=await call("/me/mfa/totp/enrollment",{method:"POST",body:JSON.stringify({password})});setSecret(b.secret);},"Segredo emitido. Confirme o código atual para elevar esta sessão.");}
  async function confirm(e:FormEvent<HTMLFormElement>){e.preventDefault();const code=new FormData(e.currentTarget).get("code");await run(async()=>{await call("/me/mfa/totp/confirmation",{method:"POST",body:JSON.stringify({code})});},"MFA confirmado temporariamente nesta sessão.");}
  async function action(review:VehicleReview,path:string,message:string){await run(async()=>{await call(`/analyst/vehicle-reviews/${review.id}${path}`,{method:"POST",body:path==="/decision"?JSON.stringify({decision:"APROVAR",reason:"revisão operacional"}):undefined});},message);}
  async function download(review:VehicleReview,evidence:VehicleEvidence){setBusy(true);try{const r=await fetch(`/api/v1/analyst/vehicle-reviews/${review.id}/documents/${evidence.id}/download`,{headers:{Authorization:`Bearer ${session.accessToken}`}});if(!r.ok){const b=await r.json();throw new Error(b.message??"Download não autorizado.");}const url=URL.createObjectURL(await r.blob());const a=document.createElement("a");a.href=url;a.download=`evidencia-${evidence.id}`;a.click();URL.revokeObjectURL(url);}catch(e){setNotice({tone:"error",text:e instanceof Error?e.message:"Download não confirmado."});}finally{setBusy(false);}}
  if(!available)return null;
  return <section className="family review-panel" aria-labelledby="vehicle-review-title"><div className="section-heading"><span className="eyebrow dark">PAINEL OPERACIONAL</span><h2 id="vehicle-review-title">Revisão atribuída</h2><p>A fila exige atribuição nominal e MFA. O analista não pode revisar o próprio vínculo; inspeção estrutural e aprovação profissional continuam independentes.</p></div><div className="work-grid"><form className="work-card" onSubmit={enroll}><h3>Configurar MFA</h3><label>Senha atual</label><input name="password" type="password" autoComplete="current-password" required/><button disabled={busy}>Emitir segredo TOTP</button>{secret&&<small>Segredo temporário: <code>{secret}</code></small>}</form><form className="work-card" onSubmit={confirm}><h3>Elevar esta sessão</h3><label>Código de seis dígitos</label><input name="code" inputMode="numeric" pattern="[0-9]{6}" required/><button disabled={busy}>Confirmar MFA</button></form></div><div className="registers vehicle-registers">{reviews.length?reviews.map(review=><article key={review.id}><h3>Vínculo <code>{review.linkId}</code></h3><p><code>{review.id}</code><strong>{review.status}</strong><span>snapshot da versão {review.linkVersion}</span>{review.status==="PENDENTE"&&<button type="button" disabled={busy} onClick={()=>void action(review,"/assign","Revisão atribuída.")}>Assumir revisão</button>}</p>{review.documents.map(document=><p key={document.id}><code>{document.id}</code><strong>{document.status}</strong><span>{document.purpose}</span>{review.status==="ATRIBUIDA"&&<><button type="button" disabled={busy||document.status!=="QUARENTENA"} onClick={()=>void action(review,`/documents/${document.id}/inspection`,"Inspeção estrutural registrada.")}>Inspecionar estrutura</button><button type="button" disabled={busy||document.status!=="INSPECAO_APROVADA"} onClick={()=>void download(review,document)}>Download autorizado</button></>}</p>)}{review.status==="ATRIBUIDA"&&<button type="button" className="review-decision" disabled={busy} onClick={()=>void action(review,"/decision","Decisão registrada.")}>Solicitar decisão</button>}</article>):<article><p className="empty">Nenhuma revisão de veículo na fila.</p></article>}</div>{notice&&<p className={`feedback ${notice.tone}`} role="alert">{notice.text}</p>}</section>;
}

function OrderPanel({ session }: { session: Tokens }) {
  const [patientId,setPatientId]=useState(""); const [recipientId,setRecipientId]=useState(""); const [unitId,setUnitId]=useState("");
  const [origin,setOrigin]=useState({street:"",number:"",district:"",city:"",state:"",postalCode:"",latitude:"",longitude:""});
  const [destination,setDestination]=useState({street:"",number:"",district:"",city:"",state:"",postalCode:"",latitude:"",longitude:""});
  const [documentId,setDocumentId]=useState(""); const [orders,setOrders]=useState<Order[]>([]); const [quotes,setQuotes]=useState<Record<string,Quote[]>>({}); const [notice,setNotice]=useState<Notice|null>(null); const [busy,setBusy]=useState(false);
  async function api(path:string,init:RequestInit={}){return parse(await fetch(`/api/v1${path}`,{...init,credentials:"same-origin",headers:{Authorization:`Bearer ${session.accessToken}`,...(init.body instanceof FormData?{}:{"Content-Type":"application/json"}),...(init.headers??{})}}));}
  async function load(){const b=await api("/orders");setOrders(b.result as Order[]);}
  useEffect(()=>{load().catch(e=>setNotice({tone:"error",text:e.message}));},[session.accessToken]);
  async function upload(e:ChangeEvent<HTMLInputElement>){const file=e.target.files?.[0];if(!file)return;setBusy(true);try{const f=new FormData();f.append("file",file);const b=await api("/order-documents/pickup-authorization",{method:"POST",body:f});setDocumentId((b.result as Evidence).id);setNotice({tone:"info",text:"Evidência privada anexada; ela ainda depende da verificação da unidade."});}catch(err){setNotice({tone:"error",text:err instanceof Error?err.message:"Upload não confirmado."});}finally{setBusy(false);}}
  function address(value:typeof origin){return {...value,latitude:Number(value.latitude),longitude:Number(value.longitude)};}
  async function create(e:FormEvent){e.preventDefault();setBusy(true);try{await api("/orders",{method:"POST",headers:{"Idempotency-Key":crypto.randomUUID()},body:JSON.stringify({patientId,recipientUserId:recipientId,pickupUnitId:unitId,pickupAuthorizationDocumentId:documentId,origin:address(origin),destination:address(destination)})});setNotice({tone:"info",text:"Pedido criado para verificação; nenhuma unidade foi presumida como aceita."});await load();}catch(err){setNotice({tone:"error",text:err instanceof Error?err.message:"Pedido não confirmado."});}finally{setBusy(false);}}
  async function quote(order:Order){setBusy(true);try{const b=await api(`/orders/${order.id}/quotes`,{method:"POST",headers:{"If-Match":String(order.version),"Idempotency-Key":crypto.randomUUID()},body:JSON.stringify({})});setQuotes(current=>({...current,[order.id]:[b.result as Quote,...(current[order.id]??[])]}));setNotice({tone:"info",text:"Orçamento calculado com rota e tarifa versionadas."});}catch(err){setNotice({tone:"error",text:err instanceof Error?err.message:"Orçamento não confirmado."});}finally{setBusy(false);}}
  const fields=(label:string,value:typeof origin,setter:Dispatch<SetStateAction<typeof origin>>) => <fieldset><legend>{label}</legend>{(["street","number","district","city","state","postalCode","latitude","longitude"] as const).map(key=><label key={key}>{key}<input required value={value[key]} onChange={e=>setter({...value,[key]:e.target.value})}/></label>)}</fieldset>;
  return <section className="family order-panel" aria-label="Pedidos e orçamentos"><div className="section-heading"><span className="eyebrow dark">PEDIDOS</span><h2>Retirada e orçamento particular</h2><p>Endereços ficam privados. O pedido aguarda verificação da unidade; rota e tarifa precisam estar habilitadas.</p></div><form className="work-card" onSubmit={create}><label>Paciente <input required value={patientId} onChange={e=>setPatientId(e.target.value)}/></label><label>Destinatário autorizado <input required value={recipientId} onChange={e=>setRecipientId(e.target.value)}/></label><label>Unidade de retirada <input required value={unitId} onChange={e=>setUnitId(e.target.value)}/></label><label>Evidência de autorização de retirada<input type="file" accept="application/pdf,image/png,image/jpeg" onChange={upload} required={!documentId}/></label><p><code>{documentId||"Nenhuma evidência anexada"}</code></p>{fields("Origem",origin,setOrigin)}{fields("Destino",destination,setDestination)}<button disabled={busy||!documentId}>Solicitar retirada</button></form><div className="registers">{orders.length?orders.map(order=><article key={order.id}><h3>Pedido <code>{order.id}</code></h3><p><strong>{order.status}</strong><span>versão {order.version}</span><span>autorização {order.pickupAuthorizationId}</span></p><button type="button" disabled={busy||order.status!=="EM_VERIFICACAO"} onClick={()=>void quote(order)}>Calcular orçamento</button>{(quotes[order.id]??[]).map(q=><p key={q.id}><strong>{q.status}</strong><span>R$ {q.grossAmount}</span><span>{q.distanceMeters} m · {q.durationSeconds}s · {q.routeProvider}</span><small>válido até {new Date(q.expiresAt).toLocaleString("pt-BR")}</small></p>)}</article>):<article><p className="empty">Nenhum pedido visível.</p></article>}</div>{notice&&<p className={`feedback ${notice.tone}`} role="alert">{notice.text}</p>}</section>;
}

function AccountPage() {
  const [mode, setMode] = useState<Mode>("register");
  const [busy, setBusy] = useState(false);
  const [session, setSession] = useState<Tokens | null>(null);
  const [feedback, setFeedback] = useState<Notice | null>(null);
  const [showBenefits, setShowBenefits] = useState(false);
  const [showFunding, setShowFunding] = useState(false);
  const [showOrders, setShowOrders] = useState(false);
  const [expired, setExpired] = useState(false);
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
            Confirme sua conta e administre autorizações temporárias com começo,
            escopo e fim visíveis.
          </p>
          <p className="quiet">
            <span className="dot" aria-hidden="true" /> E-mail confirmado não
            comprova identidade, elegibilidade ou aprovação profissional.
          </p>
        </section>
        <section className="card" aria-label="Acesso à conta">
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
                  setFeedback(null);
                }}
              >
                {labels[key]}
              </button>
            ))}
          </nav>
          <div className="card-header">
            <span className="step">SUA CONTA</span>
            <h2>{labels[mode]}</h2>
            <p>
              Conta básica, sem concessão automática de identidade ou
              privilégios.
            </p>
          </div>
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
          <section className="session" aria-label="Sessão">
            <p>
              {session
                ? expired
                  ? "Acesso expirado. Renove para continuar."
                  : "Sessão ativa neste navegador."
                : "Sem sessão ativa em memória."}
            </p>
            <button disabled={busy} onClick={() => sessionAction("refresh")}>
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
      {session && !expired && (
        <>
          <FamilyPanel session={session} />
          <DelivererPanel session={session} />
          <VehicleReviewPanel session={session} />
          <section className="family" aria-label="Benefícios"><button type="button" onClick={() => setShowBenefits(value => !value)}>{showBenefits ? "Ocultar benefícios" : "Abrir benefícios"}</button></section>
          {showBenefits && <BenefitPanel session={session} />}
          <section className="family" aria-label="Aportes"><button type="button" onClick={() => setShowFunding(value => !value)}>{showFunding ? "Ocultar aportes" : "Abrir aportes"}</button></section>
          {showFunding && <FundingPanel session={session} />}
          <section className="family" aria-label="Pedidos"><button type="button" onClick={() => setShowOrders(value => !value)}>{showOrders ? "Ocultar pedidos" : "Abrir pedidos"}</button></section>
          {showOrders && <OrderPanel session={session} />}
        </>
      )}
    </>
  );
}
createRoot(document.getElementById("root")!).render(<AccountPage />);
