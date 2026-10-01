import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

test('mostra indisponibilidade da API sem declarar cadastro ou envio concluído', async ({ page }) => {
  await page.route('**/api/v1/auth/register', route => route.fulfill({
    status: 503, contentType: 'application/json', body: JSON.stringify({
      code: 'INTEGRATION_UNAVAILABLE', message: 'O serviço de e-mail não está disponível.',
      correlationId: 'd2a6e8f8-4d64-4ba8-97a0-90af124d1361', retryable: true,
    }),
  }));
  await page.goto('/');
  await page.getByLabel('Nome completo').fill('Pessoa de Teste');
  await page.getByLabel('E-mail').fill('formulario@example.test');
  await page.getByLabel('Senha').fill('Somente-teste-123');
  await page.getByRole('button', { name: 'Solicitar cadastro' }).click();
  await expect(page.getByRole('alert')).toContainText('serviço de e-mail');
  await expect(page.getByRole('alert')).toContainText('Nenhuma conta foi criada');
});

test('formulário não apresenta violações básicas de acessibilidade', async ({ page }) => {
  await page.goto('/');
  const results = await new AxeBuilder({ page }).analyze();
  expect(results.violations).toEqual([]);
});
