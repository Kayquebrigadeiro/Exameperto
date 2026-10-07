import { test, expect } from '@playwright/test';

test.skip(!process.env.PAYOUT_MANAGER_EMAIL, 'Run through PayoutFlowTest with disposable PostgreSQL.');

test('navegador → API real → PostgreSQL exibe painel de conciliação', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Entrar', exact: true }).first().click();
  await page.getByLabel('E-mail', { exact: true }).fill(process.env.PAYOUT_MANAGER_EMAIL!);
  await page.getByLabel('Senha', { exact: true }).fill('Senha-sintetica-123');
  await page.locator('button[type=submit]').click();
  await expect(page.getByRole('alert').filter({ hasText: 'Sessão iniciada' })).toContainText('Sessão iniciada');
  const response = await page.evaluate(async (token) => {
    const r = await fetch('/api/v1/financial/payouts', { headers: { Authorization: `Bearer ${token}` } });
    return { status: r.status, body: await r.json() };
  }, process.env.PAYOUT_MANAGER_TOKEN);
  expect(response.status).toBe(200);
  expect(response.body.items.length).toBeGreaterThan(0);
  await page.getByRole('button', { name: 'Abrir repasses', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Livro de obrigações' })).toBeVisible();
});
