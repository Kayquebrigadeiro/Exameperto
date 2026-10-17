import { test, expect, type Browser, type Page } from '@playwright/test';

test.skip(!process.env.CUSTODY_RECIPIENT_EMAIL, 'Run through CustodyBrowserFlowTest with disposable PostgreSQL.');

async function loginUi(page: Page, email: string) {
  await page.goto('/acesso');
  await page.getByLabel('E-mail', { exact: true }).fill(email);
  await page.getByLabel('Senha', { exact: true }).fill('Senha-sintetica-123');
  await page.locator('button[type=submit]').click();
  await expect(page.getByRole('status').filter({ hasText: 'Sessão iniciada' })).toContainText('Sessão iniciada');
}

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
      method: 'POST', headers: {
        Authorization: `Bearer ${token}`, 'Content-Type': 'application/json',
        'If-Match': String(version), 'Idempotency-Key': crypto.randomUUID(),
      }, body: body ? JSON.stringify(body) : undefined,
    });
    return { status: response.status, body: await response.json().catch(() => ({})) };
  }, { token, path, version, body });
}

test('navegador → API real → PostgreSQL ensaia custódia e segredo do destinatário', async ({ page, browser }) => {
  const driverContext = await browser.newContext({
    permissions: ['geolocation'],
    geolocation: { latitude: -23.5505, longitude: -46.6333 },
  });
  const driverPage = await driverContext.newPage();
  const delivered = process.env.CUSTODY_DELIVERY_ORDER_ID!;
  const incident = process.env.CUSTODY_INCIDENT_ORDER_ID!;
  await loginUi(driverPage, process.env.CUSTODY_DRIVER_EMAIL!);
  const driverNavigation = driverPage.getByRole('navigation', { name: 'Áreas do produto' });
  await expect(driverNavigation).toBeVisible();
  await driverNavigation.getByRole('link', { name: 'Entregas', exact: true }).click();
  const deliveryPanel = driverPage.getByRole('region', { name: 'Ofertas e entregas' });
  const offers = deliveryPanel.getByRole('button', { name: 'Aceitar oferta', exact: true });
  await expect(offers).toHaveCount(2);
  await offers.first().click();
  await expect(deliveryPanel.getByText('Oferta aceita e tarefa atribuída.')).toBeVisible();
  await deliveryPanel.getByRole('tab', { name: 'Ofertas', exact: true }).click();
  await deliveryPanel.getByRole('button', { name: 'Aceitar oferta', exact: true }).click();
  await deliveryPanel.getByRole('tab', { name: 'Entrega atribuída', exact: true }).click();
  const deliveryTask = deliveryPanel.locator('.task-record').nth(0);
  const incidentTask = deliveryPanel.locator('.task-record').nth(1);
  await deliveryTask.getByRole('button', { name: 'Registrar retirada' }).click();
  await expect(driverPage.getByRole('alert')).toContainText('Retirada e custódia registradas');
  await expect(deliveryTask).toContainText('Custódia ATIVA');
  await deliveryTask.getByRole('button', { name: 'Iniciar entrega' }).click();
  await expect(driverPage.getByRole('alert')).toContainText('Entrega iniciada');
  await incidentTask.getByRole('button', { name: 'Registrar retirada' }).click();
  await expect(driverPage.getByRole('alert')).toContainText('Retirada e custódia registradas');
  const trackingCard = incidentTask.getByTestId('web-tracking-card');
  await trackingCard.getByRole('button', { name: 'Iniciar localização web' }).click();
  await expect(trackingCard).toContainText('Captura ativa');
  await expect(trackingCard).toContainText('Última posição');
  await driverPage.goto('/app/entregas');
  const refreshedPanel = driverPage.getByRole('region', { name: 'Ofertas e entregas' });
  await refreshedPanel.getByRole('tab', { name: 'Entrega atribuída', exact: true }).click();
  const refreshedIncidentTask = refreshedPanel.locator('.task-record').nth(1);
  await refreshedIncidentTask.getByLabel('Descrição').fill('ensaio sintético isolado');
  await refreshedIncidentTask.getByRole('button', { name: 'Registrar ocorrência' }).click();
  await expect(driverPage.getByRole('alert')).toContainText('Ocorrência registrada');

  const outsiderContext = await browser.newContext();
  const outsiderPage = await outsiderContext.newPage();
  await outsiderPage.goto('/');
  const outsider = await loginApi(outsiderPage, process.env.CUSTODY_OUTSIDER_EMAIL!);
  expect((await command(outsiderPage, outsider, `/orders/${delivered}/receipt-code`, 5)).status).toBe(404);

  await loginUi(page, process.env.CUSTODY_RECIPIENT_EMAIL!);
  await page.getByLabel('Selecionar área de atuação').selectOption('paciente');
  await page.getByRole('navigation', { name: 'Áreas do produto' }).getByRole('link', { name: 'Meus pedidos', exact: true }).click();
  const panel = page.getByRole('region', { name: 'Pedidos', exact: true });
  const orderItems = panel.locator('button.order-list-item');
  await expect(orderItems).toHaveCount(2);
  await orderItems.nth(1).click();
  await expect(panel).toContainText('Ocorrência');
  await panel.getByRole('button', { name: '← Voltar aos pedidos' }).click();
  await orderItems.nth(0).click();
  const deliveryCard = panel.locator('.order-detail-card');
  await deliveryCard.getByRole('button', { name: 'Emitir código de recebimento' }).click();
  const secretLine = deliveryCard.getByText('Código temporário:');
  await expect(secretLine).toBeVisible();
  const secret = (await secretLine.locator('code').textContent())!;
  expect(secret.length).toBeGreaterThan(20);

  await expect(deliveryTask).toContainText('Custódia ATIVA');
  await deliveryTask.getByLabel('Código do destinatário').fill(secret);
  await deliveryTask.getByRole('button', { name: 'Confirmar entrega' }).click();
  await expect(driverPage.getByRole('alert')).toContainText('Entrega confirmada pelo código do destinatário');
  await driverContext.close();
  await outsiderContext.close();
});
