import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

type Mode = 'register' | 'verification' | 'verification/resend' | 'login' | 'recovery' | 'recovery/complete';
type Tokens = { accessToken: string; expiresIn: number };
const labels: Record<Mode, string> = {
  register: 'Solicitar cadastro', verification: 'Confirmar e-mail', 'verification/resend': 'Reenviar confirmação',
  login: 'Entrar', recovery: 'Recuperar acesso', 'recovery/complete': 'Trocar senha',
};

function AccountPage() {
  const [mode, setMode] = useState<Mode>('register');
  const [busy, setBusy] = useState(false);
  const [session, setSession] = useState<Tokens | null>(null);
  const [feedback, setFeedback] = useState<{ tone: 'error' | 'info'; text: string } | null>(null);
  const [expired, setExpired] = useState(false);
  useEffect(() => {
    if (!session) return;
    setExpired(false);
    const timer = window.setTimeout(() => setExpired(true), session.expiresIn * 1000);
    return () => window.clearTimeout(timer);
  }, [session]);

  async function call(path: string, body: object, headers: Record<string, string> = {}) {
    const response = await fetch(`/api/v1/auth/${path}`, {
      method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(body),
    });
    const result = await response.json().catch(() => ({}));
    if (!response.ok) {
      const message = result.code === 'INTEGRATION_UNAVAILABLE'
        ? 'O serviço de conta ou e-mail está indisponível. Não foi possível concluir a solicitação.'
        : result.code === 'POLICY_UNDEFINED' ? 'O cadastro aguarda a validação da política de privacidade.'
        : result.message ?? 'Não foi possível concluir a solicitação.';
      throw new Error(message);
    }
    return result;
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return;
    const form = event.currentTarget;
    const fields = Object.fromEntries(new FormData(form).entries());
    setBusy(true); setFeedback(null);
    try {
      const result = await call(mode, mode === 'login' ? { ...fields, client: 'WEB' } : fields);
      if (mode === 'login') { setSession(result as Tokens); setFeedback({ tone: 'info', text: 'Sessão iniciada. Seu e-mail está confirmado; as demais verificações são independentes.' }); }
      else if (mode === 'verification') setFeedback({ tone: 'info', text: 'E-mail confirmado. Você já pode entrar.' });
      else if (mode === 'recovery/complete') { setSession(null); setFeedback({ tone: 'info', text: 'Senha alterada. Todas as sessões foram revogadas. Entre novamente.' }); }
      else setFeedback({ tone: 'info', text: 'Solicitação recebida. Se a conta atender às condições, confira seu e-mail. Esta resposta não confirma envio ou entrega. Se não receber, tente novamente mais tarde.' });
      form.reset();
    } catch (error) { setFeedback({ tone: 'error', text: error instanceof TypeError ? 'A conexão falhou. O resultado não pôde ser confirmado.' : error instanceof Error ? error.message : 'Não foi possível concluir a solicitação.' }); }
    finally { setBusy(false); }
  }
  async function sessionAction(action: 'refresh' | 'logout') {
    if (busy) return; setBusy(true); setFeedback(null);
    try {
      const csrf = await call('csrf', {});
      // Renew before logout if the short-lived access token has expired.
      let access = session?.accessToken;
      let csrfToken = csrf.token as string;
      if (action === 'logout' && expired) {
        const renewed = await call('refresh', {}, { 'X-CSRF-Token': csrfToken });
        access = renewed.accessToken; setSession(renewed);
        csrfToken = (await call('csrf', {})).token;
      }
      const result = await call(action, {}, { 'X-CSRF-Token': csrfToken, ...(access ? { Authorization: `Bearer ${access}` } : {}) });
      setSession(action === 'refresh' ? result as Tokens : null);
      setFeedback({ tone: 'info', text: action === 'refresh' ? 'Sessão renovada.' : 'Sessão encerrada no servidor.' });
    } catch (error) { setSession(null); setFeedback({ tone: 'error', text: error instanceof Error ? error.message : 'O resultado não pôde ser confirmado.' }); }
    finally { setBusy(false); }
  }
  const hasEmail = ['register', 'verification/resend', 'login', 'recovery'].includes(mode);
  return <main className="shell">
    <section className="intro" aria-labelledby="page-title"><span className="eyebrow">EXAME PERTO · ACESSO</span><h1 id="page-title">Comece pelo seu<br /><em>próprio acesso.</em></h1><p className="lede">Cuide do seu acesso, confirme seu e-mail e acompanhe cada próximo passo com clareza.</p><p className="quiet"><span className="dot" aria-hidden="true" /> E-mail confirmado não comprova identidade, elegibilidade ou aprovação de entregador e veículo.</p></section>
    <section className="card" aria-label="Acesso à conta">
      <nav aria-label="Opções de acesso">{(Object.keys(labels) as Mode[]).map(key => <button type="button" className="tab" aria-pressed={mode === key} disabled={busy} key={key} onClick={() => { setMode(key); setFeedback(null); }}>{labels[key]}</button>)}</nav>
      <div className="card-header"><span className="step">SUA CONTA</span><h2>{labels[mode]}</h2><p>Conta básica, sem concessão de papéis privilegiados.</p></div>
      <form key={mode} onSubmit={submit}>
        {mode === 'register' && <><label htmlFor="name">Nome completo</label><input id="name" name="name" autoComplete="name" required maxLength={160} /></>}
        {hasEmail && <><label htmlFor="email">E-mail</label><input id="email" name="email" type="email" autoComplete="email" required maxLength={254} /></>}
        {(mode === 'verification' || mode === 'recovery/complete') && <><label htmlFor="token">Código recebido por e-mail</label><input id="token" name="token" autoComplete="off" required minLength={16} maxLength={512} /></>}
        {(mode === 'register' || mode === 'login' || mode === 'recovery/complete') && <><label htmlFor="password">{mode === 'recovery/complete' ? 'Nova senha' : 'Senha'}</label><input id="password" name={mode === 'recovery/complete' ? 'newPassword' : 'password'} type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} required minLength={mode === 'login' ? 1 : 12} maxLength={128} /></>}
        <button type="submit" disabled={busy}>{busy ? 'Aguarde…' : labels[mode]} <span aria-hidden="true">→</span></button>
      </form>
      <section className="session" aria-label="Sessão"><p>{session ? expired ? 'Acesso expirado. Renove para continuar.' : 'Sessão ativa neste navegador.' : 'Sem sessão ativa em memória.'}</p><button disabled={busy} onClick={() => sessionAction('refresh')}>Renovar sessão</button>{session && <button disabled={busy} onClick={() => sessionAction('logout')}>Sair</button>}</section>
      {feedback && <p className={`feedback ${feedback.tone}`} role="alert">{feedback.text}</p>}
      <p className="legal">Confirmação e recuperação dependem do serviço de e-mail. Nenhuma aprovação de benefício, identidade ou motorista é concedida por este fluxo.</p>
    </section>
  </main>;
}
createRoot(document.getElementById('root')!).render(<AccountPage />);
