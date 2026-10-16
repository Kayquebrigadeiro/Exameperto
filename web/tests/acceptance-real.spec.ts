import { test, expect } from '@playwright/test';

test.skip(!process.env.ACCEPTANCE_EMAIL, 'Run through AcceptanceBrowserFlowTest with disposable PostgreSQL.');

test('navegador → aceite → API → PostgreSQL mantém pagamento pendente', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('link', { name: 'Entrar', exact: true }).first().click();
  await page.getByLabel('E-mail', { exact: true }).fill(process.env.ACCEPTANCE_EMAIL!);
  await page.getByLabel('Senha', { exact: true }).fill('Senha-sintetica-123');
  await page.locator('button[type=submit]').click();
  await expect(page.getByRole('status').filter({ hasText: 'Sessão iniciada' })).toContainText('Sessão iniciada');
  await page.getByLabel('Selecionar área').selectOption('paciente');
  await page.getByRole('navigation', { name: 'Áreas do produto' }).getByRole('link', { name: 'Meus pedidos', exact: true }).click();
  const panel=page.getByRole('region',{name:'Pedidos', exact:true});
  await panel.getByRole('button',{name:/Pedido de entrega/}).click();
  await panel.getByRole('button',{name:'Aceitar orçamento'}).click();
  await expect(panel.getByRole('alert')).toContainText('aguarda confirmação autenticada');
  await expect(panel).toContainText('pendente pagamento');
});
