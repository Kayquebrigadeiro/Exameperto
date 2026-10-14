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
  await page.getByRole('link', { name: 'Criar conta', exact: true }).first().click();
  await page.getByLabel('Nome completo').fill('Pessoa de Teste');
  await page.getByLabel('E-mail').fill('formulario@example.test');
  await page.getByLabel('Senha').fill('Somente-teste-123');
  await page.locator('button[type=submit]').click();
  await expect(page.getByRole('alert')).toContainText('e-mail');
  await expect(page.getByRole('alert')).toContainText('Não foi possível concluir');
});

test('acesso à conta começa em login e cadastro exige escolha explícita', async ({ page }) => {
  const requests: string[] = [];
  await page.route('**/api/v1/auth/*', async route => {
    requests.push(new URL(route.request().url()).pathname);
    await route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ message: 'Credenciais inválidas ou expiradas.' }) });
  });
  await page.goto('/acesso');
  await expect(page.getByRole('heading', { name: 'Entrar', exact: true })).toBeVisible();
  await page.getByLabel('E-mail').fill('conta@example.test');
  await page.getByLabel('Senha').fill('senha-incorreta');
  await page.locator('button[type=submit]').click();
  await expect.poll(() => requests.filter(path => path.endsWith('/login'))).toEqual(['/api/v1/auth/login']);
});

test('formulário não apresenta violações básicas de acessibilidade', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: /Resultados de exames/ })).toBeVisible();
  const results = await new AxeBuilder({ page }).analyze();
  expect(results.violations).toEqual([]);
});

test('rotas públicas suportam endereço direto, histórico e página não encontrada', async ({ page }) => {
  await page.goto('/acesso');
  await expect(page.getByRole('heading', { name: 'Entrar', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Solicitar cadastro', exact: true }).click();
  await expect(page).toHaveURL(/\/cadastro$/);
  await page.goBack();
  await expect(page.getByRole('heading', { name: 'Entrar', exact: true })).toBeVisible();
  await page.goto('/endereco-inexistente');
  await expect(page.getByRole('heading', { name: 'Página não encontrada.' })).toBeVisible();
  const favicon = await page.request.get('/favicon.ico');
  expect(favicon.status()).toBe(200);
});
