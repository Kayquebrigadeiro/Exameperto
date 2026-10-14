import { createHmac } from 'node:crypto';
import { test, expect, type Page } from '@playwright/test';

test.skip(!process.env.PRIVACY_OPERATOR_EMAIL, 'Run through PrivilegedOperationsBrowserFlowTest with disposable PostgreSQL.');

const password = 'Senha-sintetica-123';

async function login(page: Page, email: string) {
  await page.goto('/acesso');
  await page.getByLabel('E-mail', { exact: true }).fill(email);
  await page.getByLabel('Senha', { exact: true }).fill(password);
  await page.locator('button[type=submit]').click();
  await expect(page.getByRole('status').filter({ hasText: 'Sessão iniciada' })).toContainText('Sessão iniciada');
}

async function openArea(page: Page, name: string) {
  await page.getByRole('navigation', { name: 'Áreas do produto' }).getByRole('link', { name, exact: true }).click();
}

function totp(secret: string) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'; let buffer = 0; let bits = 0; const bytes: number[] = [];
  for (const char of secret.replace(/=+$/, '').toUpperCase()) { buffer = (buffer << 5) | alphabet.indexOf(char); bits += 5; if (bits >= 8) { bits -= 8; bytes.push((buffer >> bits) & 255); } }
  const counter = Math.floor(Date.now() / 1000 / 30); const data = Buffer.alloc(8); data.writeBigInt64BE(BigInt(counter));
  const digest = createHmac('sha1', Buffer.from(bytes)).update(data).digest(); const offset = digest[digest.length - 1] & 15;
  const code = ((digest[offset] & 127) << 24) | ((digest[offset + 1] & 255) << 16) | ((digest[offset + 2] & 255) << 8) | (digest[offset + 3] & 255);
  return String(code % 1_000_000).padStart(6, '0');
}

async function elevate(page: Page, secret: string) {
  const mfa = page.getByRole('region', { name: 'Segurança de ações privilegiadas' });
  await expect(mfa).toBeVisible();
  if (await mfa.getByLabel('Código TOTP').count() === 0) {
    await mfa.getByLabel('Senha atual').fill(password);
    await mfa.getByRole('button', { name: 'Configurar TOTP' }).click();
    const secretLine = await mfa.getByText(/Segredo exibido uma vez:/).textContent();
    secret = secretLine!.split(':').pop()!.trim();
  }
  await mfa.getByLabel('Código TOTP').fill(totp(secret));
  await mfa.getByRole('button', { name: 'Validar sessão' }).click();
  await expect(mfa).toContainText('Sessão elevada');
}

test('navegador → análise operacional, benefícios e privacidade com MFA e bloqueios', async ({ browser }) => {
  test.setTimeout(90_000);
  const operational = await browser.newPage();
  await login(operational, process.env.OPERATIONAL_ANALYST_EMAIL!);
  await openArea(operational, 'Operação');
  await elevate(operational, process.env.OPERATIONAL_MFA_SECRET!);
  const operationalPanel = operational.getByRole('region', { name: 'Análise de entregadores' });
  const operationalReview = operationalPanel.getByRole('article').filter({ hasText: process.env.OPERATIONAL_DELIVERER_ID! });
  await expect(operationalReview).toContainText('PENDENTE');
  await operationalReview.getByRole('button', { name: 'Assumir análise' }).click();
  await expect(operational.getByRole('alert').filter({ hasText: 'Etapa operacional registrada' })).toBeVisible();
  await operationalReview.getByRole('button', { name: 'Inspecionar arquivo' }).click();
  await expect(operational.getByRole('alert').filter({ hasText: 'Etapa operacional registrada' })).toBeVisible();

  const selfAnalysis = await browser.newPage();
  await login(selfAnalysis, process.env.OPERATIONAL_SELF_EMAIL!);
  await openArea(selfAnalysis, 'Operação');
  await elevate(selfAnalysis, process.env.SELF_MFA_SECRET!);
  const selfPanel = selfAnalysis.getByRole('region', { name: 'Análise de entregadores' });
  const selfReview = selfPanel.getByRole('article').filter({ hasText: process.env.SELF_DELIVERER_ID! });
  await selfReview.getByRole('button', { name: 'Assumir análise' }).click();
  await expect(selfAnalysis.getByRole('alert').filter({ hasText: /autoanálise|não pode|403|proib/i })).toBeVisible();

  const noMfa = await browser.newPage();
  await login(noMfa, process.env.OPERATIONAL_NO_MFA_EMAIL!);
  await openArea(noMfa, 'Operação');
  const noMfaPanel = noMfa.getByRole('region', { name: 'Análise de entregadores' });
  await noMfaPanel.getByRole('button', { name: 'Assumir análise' }).first().click();
  await expect(noMfa.getByRole('alert').filter({ hasText: /MFA|segundo fator/i })).toBeVisible();

  const benefits = await browser.newPage();
  await login(benefits, process.env.BENEFIT_ANALYST_EMAIL!);
  await openArea(benefits, 'Análise de benefícios');
  await elevate(benefits, process.env.BENEFIT_MFA_SECRET!);
  const benefitPanel = benefits.getByRole('region', { name: 'Análise de benefícios' });
  await expect(benefitPanel).toContainText('PENDENTE');
  await benefitPanel.getByRole('button', { name: 'Assumir análise' }).click();
  await expect(benefits.getByRole('alert').filter({ hasText: 'Etapa de análise persistida' })).toBeVisible();
  await benefitPanel.getByRole('button', { name: /Inspecionar/ }).click();
  await expect(benefits.getByRole('alert').filter({ hasText: 'Etapa de análise persistida' })).toBeVisible();
  await benefitPanel.getByLabel('Percentual').fill('50');
  await benefitPanel.getByLabel('Motivo').fill('análise sintética');
  await benefitPanel.getByRole('button', { name: 'Registrar decisão' }).click();
  await expect(benefits.getByRole('alert').filter({ hasText: 'Etapa de análise persistida' })).toBeVisible();

  const privacy = await browser.newPage();
  await login(privacy, process.env.PRIVACY_OPERATOR_EMAIL!);
  await openArea(privacy, 'Operação de privacidade');
  await elevate(privacy, process.env.PRIVACY_MFA_SECRET!);
  const privacyPanel = privacy.getByRole('region', { name: 'Operação de privacidade' });
  await expect(privacyPanel).toContainText('PENDENTE');
  await privacyPanel.getByLabel('Resposta ao titular').fill('Resposta preparada no ensaio sintético.');
  await privacyPanel.getByRole('button', { name: 'Registrar resposta' }).click();
  await expect(privacy.getByRole('alert').filter({ hasText: 'Estado atualizado pelo servidor' })).toBeVisible();
  await privacyPanel.getByRole('button', { name: /Autorizar expurgo/ }).click();
  await expect(privacy.getByRole('alert').filter({ hasText: 'Estado atualizado pelo servidor' })).toBeVisible();
  await privacyPanel.getByRole('button', { name: /Executar alvos/ }).click();
  await expect(privacy.getByRole('alert').filter({ hasText: 'Estado atualizado pelo servidor' })).toBeVisible();
  await privacyPanel.getByRole('button', { name: /Verificar execução/ }).click();
  await expect(privacy.getByRole('alert').last()).toBeVisible();
  await expect(privacy.getByRole('alert').last()).toContainText(/Estado atualizado|bloque|aguarda|obrig|falha|não/i);

  await Promise.all([operational.close(), selfAnalysis.close(), noMfa.close(), benefits.close(), privacy.close()]);
});
