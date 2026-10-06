import { createHmac } from 'node:crypto';
import { test, expect } from '@playwright/test';

test.skip(!process.env.FUNDING_PROGRAM_ID, 'Run through FundingBrowserFlowTest with disposable PostgreSQL.');

function totp(secret: string) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'; let buffer = 0; let bits = 0; const bytes: number[] = [];
  for (const char of secret.replace(/=+$/, '').toUpperCase()) { buffer = (buffer << 5) | alphabet.indexOf(char); bits += 5; if (bits >= 8) { bits -= 8; bytes.push((buffer >> bits) & 255); } }
  const counter = Math.floor(Date.now() / 1000 / 30); const data = Buffer.alloc(8); data.writeBigInt64BE(BigInt(counter));
  const digest = createHmac('sha1', Buffer.from(bytes)).update(data).digest(); const offset = digest[digest.length - 1] & 15;
  const code = ((digest[offset] & 127) << 24) | ((digest[offset + 1] & 255) << 16) | ((digest[offset + 2] & 255) << 8) | (digest[offset + 3] & 255);
  return String(code % 1_000_000).padStart(6, '0');
}

async function login(page: import('@playwright/test').Page, email: string, password: string) {
  await page.getByRole('button', { name: 'Entrar', exact: true }).first().click();
  await page.getByLabel('E-mail', { exact: true }).fill(email); await page.getByLabel('Senha', { exact: true }).fill(password);
  await page.locator('button[type=submit]').click(); await expect(page.getByRole('alert').filter({ hasText: 'Sessão iniciada' })).toContainText('Sessão iniciada');
  const fundingPanel = page.getByRole('region', { name: 'Aportes institucionais' });
  if (await fundingPanel.count() === 0) {
    await page.getByRole('button', { name: /(?:Abrir|Ocultar) aportes/ }).click();
  }
}

async function elevate(page: import('@playwright/test').Page, password: string) {
  const panel = page.getByRole('region', { name: 'Aportes institucionais' });
  await panel.getByLabel('Senha para MFA').fill(password); await panel.getByRole('button', { name: 'Emitir segredo MFA' }).click();
  const secretText = await panel.getByText(/Segredo temporário:/).textContent(); const secret = secretText!.split(':').pop()!.trim();
  await panel.getByLabel('Código MFA').fill(totp(secret)); await panel.getByRole('button', { name: 'Confirmar MFA' }).click();
  await expect(page.getByRole('alert').filter({ hasText: 'MFA confirmado' })).toContainText('MFA confirmado');
}

test('navegador → painel de financiamento → API → PostgreSQL: aporte e revisão', async ({ page }) => {
  const program = process.env.FUNDING_PROGRAM_ID!; const registrar = process.env.FUNDING_REGISTRAR_EMAIL!; const reviewer = process.env.FUNDING_REVIEWER_EMAIL!; const password = 'Senha-sintetica-123';
  await page.goto('/'); await login(page, registrar, password); const panel = page.getByRole('region', { name: 'Aportes institucionais' });
  await panel.getByLabel('UUID do programa').fill(program); await elevate(page, password);
  await panel.getByLabel('Valor em BRL').fill('100.50'); await panel.getByLabel('Referência de origem').fill('browser-ref-001');
  await panel.getByLabel('Evidência privada').setInputFiles({ name: 'original.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4\n%%EOF') });
  await panel.getByRole('button', { name: 'Registrar aporte' }).click(); await expect(page.getByRole('alert').filter({ hasText: 'pendente' })).toContainText('pendente');
  await panel.getByRole('button', { name: 'Atualizar situação' }).click(); await expect(panel).toContainText('Disponibilidade confirmada: 0'); await expect(panel).toContainText('PENDENTE');
  await page.getByRole('button', { name: 'Sair', exact: true }).click(); await expect(page.getByRole('alert').filter({ hasText: 'Sessão encerrada' })).toContainText('Sessão encerrada');
  await login(page, reviewer, password); const reviewPanel = page.getByRole('region', { name: 'Aportes institucionais' }); await reviewPanel.getByLabel('UUID do programa').fill(program); await elevate(page, password);
  await reviewPanel.getByLabel('Evidência de conciliação').setInputFiles({ name: 'reconciliation.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4\n%%EOF') });
  await reviewPanel.getByRole('button', { name: 'Atualizar situação' }).click(); await expect(reviewPanel).toContainText('PENDENTE');
  await reviewPanel.getByRole('button', { name: 'Inspecionar conciliação' }).first().click(); await expect(page.getByRole('alert').filter({ hasText: 'inspecionada' })).toContainText('inspecionada');
  await reviewPanel.getByRole('button', { name: 'Confirmar aporte' }).first().click(); await expect(page.getByRole('alert').filter({ hasText: 'disponibilidade atualizada' })).toContainText('disponibilidade atualizada');
  await reviewPanel.getByRole('button', { name: 'Atualizar situação' }).click(); await expect(reviewPanel).toContainText('Disponibilidade confirmada: 100.5'); await expect(reviewPanel).toContainText('CONFIRMADO');
});
