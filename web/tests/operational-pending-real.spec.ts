import { expect, Page, test } from '@playwright/test';
import { createHmac } from 'node:crypto';

test.skip(!process.env.PENDING_SCENARIO, 'Run through OperationalPendingBrowserFlowTest.');
test.setTimeout(90_000);
const password = 'Senha-sintetica-123';

function totp(secret: string) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'; let buffer = 0; let bits = 0; const bytes: number[] = [];
  for (const c of secret.replace(/=+$/, '').toUpperCase()) { buffer = (buffer << 5) | alphabet.indexOf(c); bits += 5; if (bits >= 8) { bits -= 8; bytes.push((buffer >> bits) & 255); } }
  const data = Buffer.alloc(8); data.writeBigInt64BE(BigInt(Math.floor(Date.now() / 1000 / 30)));
  const digest = createHmac('sha1', Buffer.from(bytes)).update(data).digest(); const offset = digest[digest.length - 1] & 15;
  const code = ((digest[offset] & 127) << 24) | ((digest[offset + 1] & 255) << 16) | ((digest[offset + 2] & 255) << 8) | (digest[offset + 3] & 255);
  return String(code % 1_000_000).padStart(6, '0');
}

async function login(page: Page, email: string) {
  await page.goto('/acesso'); await page.getByLabel('E-mail', { exact: true }).fill(email); await page.getByLabel('Senha', { exact: true }).fill(password); await page.locator('button[type=submit]').click();
  await expect(page.getByRole('status').filter({ hasText: 'Sessão iniciada' })).toContainText('Sessão iniciada'); await expect(page).toHaveURL(/\/app\//); await expect(page.getByLabel('Selecionar área de atuação')).toBeVisible();
}
async function patientOrders(page: Page, email: string) {
  await login(page, email); await page.getByLabel('Selecionar área de atuação').selectOption('paciente'); await page.getByRole('navigation', { name: 'Áreas do produto' }).getByRole('link', { name: 'Meus pedidos', exact: true }).click();
  return page.getByRole('region', { name: 'Pedidos', exact: true });
}
async function cancel(page: Page, email: string, status: string, feedback: string, phase?: string) {
  const panel = await patientOrders(page, email); const item = panel.locator('button.order-list-item').filter({ hasText: status }).first(); await expect(item).toBeVisible(); await item.click();
  page.once('dialog', (dialog) => dialog.accept()); await panel.getByRole('button', { name: 'Solicitar cancelamento' }).click(); await expect(panel.getByRole('alert')).toContainText(feedback); if (phase) await expect(panel).toContainText(phase);
}

test('cancelamento antes da designação pela tela do paciente', async ({ page }) => { test.skip(process.env.PENDING_SCENARIO === 'resolution'); await cancel(page, process.env.PENDING_PATIENT_EMAIL!, 'Pronto para seguir', 'Pedido cancelado'); });
test('cancelamento após designação antes da retirada pela tela do paciente', async ({ page }) => { test.skip(process.env.PENDING_SCENARIO === 'resolution'); await cancel(page, process.env.PENDING_PATIENT_EMAIL!, 'Aceito', 'Solicitação de cancelamento', 'fase'); });
test('solicitação após retirada preserva custódia e informa apuração', async ({ page }) => { test.skip(process.env.PENDING_SCENARIO === 'resolution'); await cancel(page, process.env.PENDING_PATIENT_EMAIL!, 'Retirado', 'Solicitação de cancelamento', 'fase'); });

async function elevate(page: Page, secret: string) {
  const mfa = page.getByRole('region', { name: 'Segurança de ações privilegiadas' }); await expect(mfa).toBeVisible(); await mfa.getByLabel('Código TOTP').fill(totp(secret)); await mfa.getByRole('button', { name: 'Validar sessão' }).click(); await expect(mfa).toContainText('Sessão elevada');
}
async function resolve(page: Page, decision: 'RETORNO' | 'REENTREGA') {
  await login(page, decision === 'RETORNO' ? process.env.PENDING_RETURN_ADMIN_EMAIL! : process.env.PENDING_REDELIVERY_ADMIN_EMAIL!); await page.getByLabel('Selecionar área de atuação').selectOption('operacao'); await elevate(page, decision === 'RETORNO' ? process.env.PENDING_RETURN_ADMIN_MFA_SECRET! : process.env.PENDING_REDELIVERY_ADMIN_MFA_SECRET!);
  const panel = page.getByRole('region', { name: 'Ocorrências operacionais' }); const card = panel.locator('article').filter({ hasText: decision === 'RETORNO' ? process.env.PENDING_RETURN_ORDER_ID! : process.env.PENDING_REDELIVERY_ORDER_ID! }); await expect(card).toBeVisible(); const form = card.locator('form'); await form.locator('select[name="decision"]').selectOption(decision); await form.locator('select[name="destinationType"]').selectOption(decision === 'RETORNO' ? 'RETORNO' : 'UNIDADE'); const destination = form.locator('select[name="destinationUnitId"]'); await expect(destination.locator('option')).toHaveCount(2); await destination.selectOption({ index: 1 }); await form.getByLabel('Instruções').fill(`Instrução sintética ${decision}`); const submit = form.getByRole('button', { name: 'Registrar resolução com MFA' }); await submit.scrollIntoViewIfNeeded(); await submit.click({ force: true }); await expect(panel.getByRole('alert')).toContainText('Resolução registrada com MFA');
}
async function driverTask(page: Page, action: 'RETORNO' | 'REENTREGA') {
  await login(page, action === 'RETORNO' ? process.env.PENDING_RETURN_DRIVER_EMAIL! : process.env.PENDING_REDELIVERY_DRIVER_EMAIL!); await page.getByLabel('Selecionar área de atuação').selectOption('entregador'); await page.getByRole('navigation', { name: 'Áreas do produto' }).getByRole('link', { name: 'Entregas', exact: true }).click(); const panel = page.getByRole('region', { name: 'Ofertas e entregas' }); await panel.getByRole('tab', { name: 'Entrega atribuída', exact: true }).click(); const task = panel.locator('.task-record').first(); await expect(task).toBeVisible(); await task.getByRole('button', { name: 'Executar resolução' }).click(); await expect(page.getByRole('alert')).toContainText('Execução da resolução registrada'); await task.getByRole('button', { name: 'Iniciar entrega' }).click(); await expect(page.getByRole('alert')).toContainText('Entrega iniciada');
  if (action === 'RETORNO') { await task.getByRole('button', { name: 'Comprovar retorno e encerrar' }).click(); await expect(page.getByRole('alert')).toContainText('Retorno comprovado e custódia encerrada'); return; }
  const recipient = await page.context().newPage(); const orders = await patientOrders(recipient, process.env.PENDING_PATIENT_EMAIL!); const item = orders.locator('button.order-list-item').filter({ hasText: 'Em entrega' }).first(); await item.click(); await orders.getByRole('button', { name: 'Emitir código de recebimento' }).click(); const code = await orders.getByText('Código temporário:').locator('code').textContent(); expect(code).toBeTruthy(); await task.getByLabel('Código do destinatário').fill(code!); await task.getByRole('button', { name: 'Confirmar entrega' }).click(); await expect(page.getByRole('alert')).toContainText('Entrega confirmada pelo código do destinatário'); await recipient.close();
}
test('resolução administrativa, retorno e encerramento', async ({ page, browser }) => { test.skip(process.env.PENDING_SCENARIO !== 'resolution'); await resolve(page, 'RETORNO'); await driverTask(await browser.newPage(), 'RETORNO'); });
test('resolução administrativa, reentrega, recebimento e encerramento', async ({ page, browser }) => { test.skip(process.env.PENDING_SCENARIO !== 'resolution'); await resolve(page, 'REENTREGA'); await driverTask(await browser.newPage(), 'REENTREGA'); });
