import { createHmac } from 'node:crypto';
import { test, expect } from '@playwright/test';

test.skip(!process.env.PAYOUT_MANAGER_EMAIL, 'Run through PayoutFlowTest with disposable PostgreSQL.');

function totp(secret: string) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'; let buffer = 0; let bits = 0; const bytes: number[] = [];
  for (const char of secret.replace(/=+$/, '').toUpperCase()) { buffer = (buffer << 5) | alphabet.indexOf(char); bits += 5; if (bits >= 8) { bits -= 8; bytes.push((buffer >> bits) & 255); } }
  const counter = Math.floor(Date.now() / 1000 / 30); const data = Buffer.alloc(8); data.writeBigInt64BE(BigInt(counter));
  const digest = createHmac('sha1', Buffer.from(bytes)).update(data).digest(); const offset = digest[digest.length - 1] & 15;
  const code = ((digest[offset] & 127) << 24) | ((digest[offset + 1] & 255) << 16) | ((digest[offset + 2] & 255) << 8) | (digest[offset + 3] & 255); return String(code % 1_000_000).padStart(6, '0');
}

test('navegador → API real → PostgreSQL exibe painel de conciliação', async ({ page }) => {
  await page.goto('/acesso');
  await page.getByLabel('E-mail', { exact: true }).fill(process.env.PAYOUT_MANAGER_EMAIL!);
  await page.getByLabel('Senha', { exact: true }).fill('Senha-sintetica-123');
  await page.locator('button[type=submit]').click();
  await expect(page.getByRole('status').filter({ hasText: 'Sessão iniciada' })).toContainText('Sessão iniciada');
  await page.getByRole('navigation', { name: 'Áreas do produto' }).getByRole('link', { name: 'Financeiro', exact: true }).click();
  const mfa = page.getByRole('region', { name: 'Segurança de ações privilegiadas' });
  if (await mfa.getByLabel('Senha atual').count()) {
    await mfa.getByLabel('Senha atual').fill('Senha-sintetica-123');
    await mfa.getByRole('button', { name: 'Configurar TOTP' }).click();
  }
  const secretLine = await mfa.getByText(/Segredo exibido uma vez:/).textContent().catch(() => null);
  const secret = secretLine ? secretLine.split(':').pop()!.trim() : process.env.PAYOUT_MFA_SECRET!;
  await mfa.getByLabel('Código TOTP').fill(totp(secret));
  await mfa.getByRole('button', { name: 'Validar sessão' }).click();
  await expect(mfa).toContainText('Sessão elevada');
  await expect(page.getByRole('heading', { name: 'Recursos com rastreabilidade' })).toBeVisible();
  await page.getByRole('button', { name: 'Obrigações e repasses', exact: true }).click();
  const ledger = page.getByRole('region', { name: 'Operação financeira' });
  await expect(ledger).toContainText(process.env.PAYOUT_ORDER_ID!.slice(0, 8));
  await ledger.getByRole('button', { name: /Pedido .*abrir detalhe/ }).click();
  await page.getByRole('button', { name: 'Solicitar repasse' }).click();
  await expect(page.getByRole('alert').filter({ hasText: 'Obrigação enviada ao provedor' })).toBeVisible();
  await expect(page.getByRole('complementary', { name: 'Detalhe da obrigação' })).toContainText('CONFIRMADO');
  await expect(page.getByText('HISTÓRICO AUDITÁVEL')).toBeVisible();
  await page.screenshot({ path: 'test-results/financeiro-obrigacoes-sanitizado.png', fullPage: true });
  await page.getByRole('button', { name: 'Programas e aportes', exact: true }).click();
  await page.screenshot({ path: 'test-results/financeiro-programas-sanitizado.png', fullPage: true });
});
