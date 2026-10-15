import { test, expect } from '@playwright/test';

test.skip(!process.env.ORDER_PATIENT_ID, 'Run through OrderBrowserFlowTest with disposable PostgreSQL.');

async function login(page: import('@playwright/test').Page, email: string) {
  await page.getByRole('link', { name: 'Entrar', exact: true }).first().click();
  await page.getByLabel('E-mail', { exact: true }).fill(email); await page.getByLabel('Senha', { exact: true }).fill('Senha-sintetica-123'); await page.locator('button[type=submit]').click();
  await expect(page.getByRole('status').filter({ hasText: 'Sessão iniciada' })).toContainText('Sessão iniciada');
}

test('navegador → API → PostgreSQL: jornada preserva o rascunho quando falta unidade real', async ({ page }) => {
  await page.goto('/'); await login(page, process.env.ORDER_PATIENT_EMAIL!); await page.getByRole('navigation', { name: 'Áreas do produto' }).getByRole('link', { name: 'Pedidos', exact: true }).click();
  const panel = page.getByRole('region', { name: 'Pedidos', exact: true });
  await expect(panel.getByLabel('Paciente representado')).toHaveValue(process.env.ORDER_PATIENT_ID!);
  await panel.getByRole('button', { name: 'Continuar' }).click();
  await expect(panel.getByLabel('Unidade de retirada')).toBeDisabled();
  await expect(panel).toContainText('Ainda não há uma unidade de retirada disponível para seleção');
  await expect(panel).toContainText('nenhum identificador foi inventado');
});
