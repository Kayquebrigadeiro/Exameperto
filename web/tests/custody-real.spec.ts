import { test, expect, type Page } from '@playwright/test';

test.skip(!process.env.CUSTODY_RECIPIENT_EMAIL, 'Run through CustodyBrowserFlowTest with disposable PostgreSQL.');

async function loginApi(page: Page, email: string) {
  return page.evaluate(async ({ email }) => {
    const response = await fetch('/api/v1/auth/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password: 'Senha-sintetica-123', client: 'WEB' }),
    });
    return (await response.json()).accessToken as string;
  }, { email });
}

async function command(page: Page, token: string, path: string, version: number, body?: object) {
  return page.evaluate(async ({ token, path, version, body }) => {
    const response = await fetch(`/api/v1${path}`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`, 'Content-Type': 'application/json',
        'If-Match': String(version), 'Idempotency-Key': crypto.randomUUID(),
      },
      body: body ? JSON.stringify(body) : undefined,
    });
    return { status: response.status, body: await response.json().catch(() => ({})) };
  }, { token, path, version, body });
}

test('navegador → API real → PostgreSQL ensaia custódia e segredo do destinatário', async ({ page, context }) => {
  const driverPage = await context.newPage();
  await driverPage.goto('/');
  const driver = await loginApi(driverPage, process.env.CUSTODY_DRIVER_EMAIL!);
  const delivered = process.env.CUSTODY_DELIVERY_ORDER_ID!;
  const incident = process.env.CUSTODY_INCIDENT_ORDER_ID!;
  const proof = process.env.CUSTODY_EVIDENCE_ID!;

  expect((await command(driverPage, driver, `/orders/${delivered}/pickup`, 3, { evidenceDocumentId: proof })).status).toBe(200);
  expect((await command(driverPage, driver, `/orders/${delivered}/start-delivery`, 4)).status).toBe(200);
  expect((await command(driverPage, driver, `/orders/${incident}/pickup`, 3, { evidenceDocumentId: proof })).status).toBe(200);
  expect((await command(driverPage, driver, `/orders/${incident}/incidents`, 4, { type: 'DESTINATARIO_AUSENTE', description: 'ensaio sintético isolado' })).status).toBe(201);

  const outsiderPage = await context.newPage();
  await outsiderPage.goto('/');
  const outsider = await loginApi(outsiderPage, process.env.CUSTODY_OUTSIDER_EMAIL!);
  expect((await command(outsiderPage, outsider, `/orders/${delivered}/receipt-code`, 5)).status).toBe(404);

  await page.goto('/');
  await page.getByRole('button', { name: 'Entrar', exact: true }).first().click();
  await page.getByLabel('E-mail', { exact: true }).fill(process.env.CUSTODY_RECIPIENT_EMAIL!);
  await page.getByLabel('Senha', { exact: true }).fill('Senha-sintetica-123');
  await page.locator('button[type=submit]').click();
  await expect(page.getByRole('alert').filter({ hasText: 'Sessão iniciada' })).toContainText('Sessão iniciada');
  await page.getByRole('button', { name: 'Abrir pedidos', exact: true }).click();
  const panel = page.getByRole('region', { name: 'Pedidos e orçamentos' });
  const deliveryCard = panel.locator('article', { hasText: delivered });
  const incidentCard = panel.locator('article', { hasText: incident });
  await expect(incidentCard).toContainText('OCORRENCIA');
  await deliveryCard.getByRole('button', { name: 'Emitir código de recebimento' }).click();
  const secretLine = deliveryCard.getByText('Código temporário:');
  await expect(secretLine).toBeVisible();
  const secret = (await secretLine.locator('code').textContent())!;
  expect(secret.length).toBeGreaterThan(20);

  expect((await command(driverPage, driver, `/orders/${delivered}/deliver`, 5, { code: secret })).status).toBe(200);
});
