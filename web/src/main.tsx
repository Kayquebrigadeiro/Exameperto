import { useState } from 'react';
import type { FormEvent } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

type ApiError = { code?: string; message?: string };

function RegistrationPage() {
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ tone: 'error' | 'info'; text: string } | null>(null);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return; setFeedback(null);
    const form = new FormData(event.currentTarget);
    const payload = { name: String(form.get('name') ?? ''), email: String(form.get('email') ?? ''), password: String(form.get('password') ?? '') };
    setBusy(true);
    try {
      const response = await fetch('/api/v1/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
      const body = await response.json().catch(() => ({} as ApiError)) as ApiError;
      if (!response.ok) {
        const message = body.code === 'INTEGRATION_UNAVAILABLE' ? 'O serviço de e-mail ainda não está disponível. Nenhuma conta foi criada.' : body.code === 'POLICY_UNDEFINED' ? 'O cadastro aguarda a validação da política de privacidade.' : body.message ?? 'Não foi possível concluir o cadastro.';
        setFeedback({ tone: 'error', text: message }); return;
      }
      setFeedback({ tone: 'info', text: 'Solicitação recebida. Confira seu e-mail para continuar.' });
    } catch { setFeedback({ tone: 'error', text: 'Não foi possível alcançar o serviço. Nenhuma conta foi criada.' }); }
    finally { setBusy(false); }
  }
  return <main className="shell">
    <section className="intro" aria-labelledby="page-title"><span className="eyebrow">EXAME PERTO · ACESSO</span><h1 id="page-title">Comece pelo seu<br /><em>próprio acesso.</em></h1><p className="lede">Um cadastro simples para acompanhar suas entregas com clareza, desde o primeiro passo.</p><p className="quiet"><span className="dot" aria-hidden="true" /> Seus dados ficam protegidos e não concedem papéis administrativos.</p></section>
    <section className="card" aria-label="Formulário de cadastro"><div className="card-header"><span className="step">01 / 01</span><h2>Criar conta básica</h2><p>Você poderá confirmar o e-mail quando o serviço estiver disponível.</p></div>
      <form onSubmit={submit} noValidate><label htmlFor="name">Nome completo<span aria-hidden="true">*</span></label><input id="name" name="name" autoComplete="name" required maxLength={160} placeholder="Como podemos chamar você?" /><label htmlFor="email">E-mail<span aria-hidden="true">*</span></label><input id="email" name="email" type="email" autoComplete="email" required maxLength={254} placeholder="voce@exemplo.com" /><label htmlFor="password">Senha<span aria-hidden="true">*</span></label><input id="password" name="password" type="password" autoComplete="new-password" required minLength={12} maxLength={128} placeholder="Mínimo de 12 caracteres" /><button type="submit" disabled={busy}>{busy ? 'Verificando disponibilidade…' : 'Solicitar cadastro'} <span aria-hidden="true">→</span></button></form>
      {feedback && <p className={`feedback ${feedback.tone}`} role="alert">{feedback.text}</p>}<p className="legal">Ao continuar, você inicia apenas um cadastro básico. Nenhuma aprovação de benefício ou papel privilegiado é concedida.</p>
    </section></main>;
}
createRoot(document.getElementById('root')!).render(<RegistrationPage />);
