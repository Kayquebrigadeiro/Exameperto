import { test, expect } from '@playwright/test';

test.skip(!process.env.ACCEPTANCE_EMAIL, 'Run through PaymentSandboxBrowserFlowTest with disposable PostgreSQL.');

test('paciente prepara checkout, consulta estado autenticado e persiste confirmação', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('link', { name: 'Entrar', exact: true }).first().click();
  await page.getByLabel('E-mail', { exact: true }).fill(process.env.ACCEPTANCE_EMAIL!);
  await page.getByLabel('Senha', { exact: true }).fill('Senha-sintetica-123');
  await page.locator('button[type=submit]').click();
  await expect(page.getByRole('status').filter({ hasText: 'Sessão iniciada' })).toContainText('Sessão iniciada');
  const area = page.getByLabel('Selecionar área de atuação');
  if (await area.isVisible()) await area.selectOption('paciente');
  await page.getByRole('navigation', { name: 'Áreas do produto' }).getByRole('link', { name: 'Meus pedidos', exact: true }).click();
  const panel = page.getByRole('region', { name: 'Pedidos', exact: true });
  await expect(panel.getByRole('button', { name: 'Pedido de entrega' }).first()).toBeVisible();
  await panel.getByRole('button', { name: 'Pedido de entrega' }).first().click();
  await expect(panel).toContainText('Orçamento disponível');
  await panel.getByRole('button', { name: 'Aceitar orçamento' }).click();
  await expect(panel.getByRole('alert')).toContainText('aguarda confirmação autenticada');
  const payment = panel.getByText('PAGAMENTO PARTICULAR').locator('..').locator('..').locator('..');
  await expect(payment).toContainText('Pagamento de teste — sem cobrança real');
  await payment.getByRole('button', { name: 'Preparar checkout oficial' }).click();
  await expect(panel.getByRole('alert')).toContainText('Checkout oficial de teste preparado');
  await expect(payment.getByRole('link', { name: 'Abrir checkout oficial' })).toHaveAttribute('href', /^https:\/\/sandbox\.mercadopago\.test\//);
  await payment.getByRole('button', { name: 'Atualizar situação' }).click();
  await expect(panel.getByRole('alert')).toContainText('Situação consultada');
  await expect(payment).toContainText('Confirmado');
  await page.screenshot({ path: 'target/payment-sandbox-confirmed-sanitized.png', fullPage: true });
  await page.reload();
  await page.getByRole('navigation', { name: 'Áreas do produto' }).getByRole('link', { name: 'Meus pedidos', exact: true }).click();
  const reloaded = page.getByRole('region', { name: 'Pedidos', exact: true });
  await reloaded.getByRole('button', { name: 'Pedido de entrega' }).first().click();
  await expect(reloaded.getByText('PAGAMENTO PARTICULAR').locator('..').locator('..').locator('..')).toContainText('Confirmado');
});
