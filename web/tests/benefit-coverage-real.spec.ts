import { expect, test } from '@playwright/test';

test.skip(!process.env.COVERAGE_EMAIL, 'Run through BenefitCoverageBrowserFlowTest with disposable PostgreSQL.');

test('seleciona benefício, aceita orçamento, persiste cobertura e recusa saldo insuficiente', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('link', { name: 'Entrar', exact: true }).first().click();
  await page.getByLabel('E-mail', { exact: true }).fill(process.env.COVERAGE_EMAIL!);
  await page.getByLabel('Senha', { exact: true }).fill('Senha-sintetica-123');
  await page.locator('button[type=submit]').click();
  await expect(page.getByRole('status').filter({ hasText: 'Sessão iniciada' })).toContainText('Sessão iniciada');
  await page.getByLabel('Selecionar área de atuação').selectOption('paciente');
  await page.getByRole('navigation', { name: 'Áreas do produto' }).getByRole('link', { name: 'Meus pedidos', exact: true }).click();

  const panel = page.locator('section[aria-label="Pedidos"]');
  const records = panel.locator('button.order-list-item');
  await expect(records).toHaveCount(2);

  await records.nth(0).click();
  const program = panel.getByRole('combobox', { name: 'Programa de benefício para cobertura' });
  await expect(program).toHaveValue('');
  await expect(program.locator('option').nth(1)).toContainText(process.env.COVERAGE_PROGRAM_LABEL!);
  await program.selectOption(process.env.COVERAGE_PROGRAM_ID!);
  await panel.getByRole('button', { name: 'Calcular orçamento' }).click();
  await expect(panel).toContainText('subsídio -R$ 38,00');
  await expect(panel).toContainText('Total do paciente R$ 0,00');
  await panel.getByRole('button', { name: 'Aceitar orçamento' }).click();
  await expect(panel.getByRole('alert')).toContainText('subsídio reservado');
  await expect(panel).toContainText(/confirmada/i);

  await page.reload();
  const reloaded = page.locator('section[aria-label="Pedidos"]');
  await reloaded.locator('button.order-list-item').nth(0).click();
  await expect(reloaded).toContainText(/confirmada/i);
  await expect(reloaded).toContainText('subsídio -R$ 38,00');

  await reloaded.getByText('← Voltar aos pedidos').click();
  await reloaded.locator('button.order-list-item').nth(1).click();
  await reloaded.getByRole('combobox', { name: 'Programa de benefício para cobertura' }).selectOption(process.env.COVERAGE_PROGRAM_ID!);
  await reloaded.getByRole('button', { name: 'Calcular orçamento' }).click();
  await expect(reloaded).toContainText('subsídio -R$ 38,00');
  await reloaded.getByRole('button', { name: 'Aceitar orçamento' }).click();
  await expect(reloaded.getByRole('alert')).toContainText('cobertura disponível');
  await expect(reloaded).not.toContainText('subsídio reservado');
});
