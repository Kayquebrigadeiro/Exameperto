import { test, expect } from '@playwright/test';

test.skip(!process.env.ACCEPTANCE_EMAIL, 'Run through AcceptanceBrowserFlowTest with disposable PostgreSQL.');

test('navegador → aceite → API → PostgreSQL mantém pagamento pendente', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Entrar', exact: true }).first().click();
  await page.getByLabel('E-mail', { exact: true }).fill(process.env.ACCEPTANCE_EMAIL!);
  await page.getByLabel('Senha', { exact: true }).fill('Senha-sintetica-123');
  await page.locator('button[type=submit]').click();
  await expect(page.getByRole('alert').filter({ hasText: 'Sessão iniciada' })).toContainText('Sessão iniciada');
  await page.getByRole('button', { name: 'Abrir pedidos', exact: true }).click();
  const panel=page.getByRole('region',{name:'Pedidos e orçamentos'});
  await expect(panel).toContainText(process.env.ACCEPTANCE_ORDER_ID!);
  await panel.getByRole('button',{name:'Aceitar condições'}).click();
  await expect(panel.getByRole('alert')).toContainText('aguarda confirmação autenticada');
  await expect(panel).toContainText('PENDENTE_PAGAMENTO');
});
