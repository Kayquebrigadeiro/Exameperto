import { test, expect } from '@playwright/test';

test.skip(!process.env.ASSIGNMENT_EMAIL, 'Run through AssignmentBrowserFlowTest with disposable PostgreSQL.');

test('navegador do paciente → API → PostgreSQL acompanha designação sem dados excessivos', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('link', { name: 'Entrar', exact: true }).first().click();
  await page.getByLabel('E-mail', { exact: true }).fill(process.env.ASSIGNMENT_EMAIL!);
  await page.getByLabel('Senha', { exact: true }).fill('Senha-sintetica-123');
  await page.locator('button[type=submit]').click();
  await expect(page.getByRole('status').filter({ hasText: 'Sessão iniciada' })).toContainText('Sessão iniciada');
  await page.getByRole('navigation', { name: 'Áreas do produto' }).getByRole('link', { name: 'Pedidos', exact: true }).click();
  const panel=page.getByRole('region',{name:'Pedidos', exact:true});
  await panel.locator('.order-history button').first().click();
  await expect(panel).toContainText('Entrega designada');
  await expect(panel).toContainText('Entregador navegador');
  await expect(panel).toContainText('Modelo seguro');
  await expect(panel).not.toContainText('12345678901');
  await expect(panel).not.toContainText('CNH');
});
