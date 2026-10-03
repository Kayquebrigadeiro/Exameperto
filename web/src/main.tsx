import { useEffect, useState } from "react";
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

function DelivererPanel({ session }: { session: Tokens }) {
  const [profile, setProfile] = useState<Deliverer | null>(null);
  const [docs, setDocs] = useState<Evidence[]>([]);
  const [birthDate, setBirthDate] = useState("");
  const [category, setCategory] = useState("HABILITACAO");
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  async function load() {
    const h = { Authorization: `Bearer ${session.accessToken}` };
    const p = await fetch("/api/v1/me/deliverer", { headers: h });
    if (p.ok) {
      setProfile(await p.json());
      const d = await fetch("/api/v1/me/deliverer/documents", { headers: h });
      if (d.ok) setDocs(await d.json());
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
      {notice && (
        <p className={`feedback ${notice.tone}`} role="alert">
          {notice.text}
        </p>
      )}
    </section>
  );
}

function AccountPage() {
  const [mode, setMode] = useState<Mode>("register");
  const [busy, setBusy] = useState(false);
  const [session, setSession] = useState<Tokens | null>(null);
  const [feedback, setFeedback] = useState<Notice | null>(null);
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
        </>
      )}
    </>
  );
}
createRoot(document.getElementById("root")!).render(<AccountPage />);
