import { test, expect } from '@playwright/test';

test.skip(!process.env.ORDER_PATIENT_ID, 'Run through OrderBrowserFlowTest with disposable PostgreSQL.');

async function login(page: import('@playwright/test').Page, email: string) {
  await page.getByRole('button', { name: 'Entrar', exact: true }).first().click();
  await page.getByLabel('E-mail', { exact: true }).fill(email); await page.getByLabel('Senha', { exact: true }).fill('Senha-sintetica-123'); await page.locator('button[type=submit]').click();
  await expect(page.getByRole('alert').filter({ hasText: 'Sessão iniciada' })).toContainText('Sessão iniciada');
}

test('navegador → pedidos → API → PostgreSQL: upload, acompanhamento e bloqueio de cotação', async ({ page }) => {
  await page.goto('/'); await login(page, process.env.ORDER_PATIENT_EMAIL!); await page.getByRole('button', { name: 'Abrir pedidos', exact: true }).click();
  const panel = page.getByRole('region', { name: 'Pedidos e orçamentos' });
  await panel.getByLabel('Paciente').fill(process.env.ORDER_PATIENT_ID!); await panel.getByLabel('Destinatário autorizado').fill(process.env.ORDER_PATIENT_USER_ID!); await panel.getByLabel('Unidade de retirada').fill(process.env.ORDER_UNIT_ID!);
  await panel.getByLabel('Evidência de autorização de retirada').setInputFiles({ name: 'pickup.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4\n%%EOF') });
  for (const fields of [panel.locator('fieldset').nth(0), panel.locator('fieldset').nth(1)]) { await fields.getByLabel('street').fill('Rua sintética'); await fields.getByLabel('number').fill('10'); await fields.getByLabel('district').fill('Centro'); await fields.getByLabel('city').fill('Cidade'); await fields.getByLabel('state').fill('SP'); await fields.getByLabel('postalCode').fill('01001000'); await fields.getByLabel('latitude').fill('-23.55'); await fields.getByLabel('longitude').fill('-46.63'); }
  await panel.getByRole('button', { name: 'Solicitar retirada' }).click(); await expect(panel.getByRole('alert')).toContainText('Pedido criado'); await expect(panel).toContainText('EM_VERIFICACAO');
  await panel.getByRole('button', { name: 'Calcular orçamento' }).click(); await expect(panel.getByRole('alert')).toContainText('unidade');
});
