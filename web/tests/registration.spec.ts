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
  await expect(page.getByRole('alert')).toContainText('recupere o acesso');
});

test('cadastro repetido mantém resposta neutra e oferece login ou recuperação', async ({ page }) => {
  await page.route('**/api/v1/auth/register', route => route.fulfill({
    status: 202, contentType: 'application/json', body: JSON.stringify({ id: 'accepted', status: 'PENDENTE' }),
  }));
  await page.goto('/cadastro');
  await page.getByLabel('Nome completo').fill('Conta já existente');
  await page.getByLabel('E-mail').fill('conta@example.test');
  await page.getByLabel('Senha').fill('Senha-sintetica-123');
  await page.getByRole('button', { name: /Criar conta/ }).click();
  await expect(page.getByRole('alert')).toContainText('Se você já possui uma conta');
  await expect(page.getByRole('link', { name: 'Entrar', exact: true }).last()).toBeVisible();
  await expect(page.getByRole('link', { name: 'Recuperar acesso', exact: true }).last()).toBeVisible();
  await expect(page).toHaveURL(/\/confirmacao$/);
});

test('preserva a intenção de entregador até o dashboard de cadastro após confirmação e login', async ({ page }) => {
  await page.route('**/api/v1/auth/register', route => route.fulfill({
    status: 202, contentType: 'application/json', body: JSON.stringify({ id: 'accepted', status: 'PENDENTE' }),
  }));
  await page.route('**/api/v1/auth/verification', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify({ id: 'verified', status: 'CONCLUIDA' }),
  }));
  await page.route('**/api/v1/auth/login', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify({ accessToken: 'deliverer-access', expiresIn: 900 }),
  }));
  await page.route('**/api/v1/me/capabilities', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify({ globalRoles: [], institutions: [], areas: ['ENTREGADOR'], patientProfile: false, delivererProfile: false, activeFamilyAuthorization: false }),
  }));
  await page.route('**/api/v1/me/deliverer', route => route.fulfill({
    status: 404, contentType: 'application/json', body: JSON.stringify({ code: 'NOT_FOUND', message: 'Perfil ainda não cadastrado.' }),
  }));
  await page.route('**/api/v1/me/deliverer/documents', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify({ result: [] }),
  }));
  await page.route('**/api/v1/me/vehicle-links', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify({ result: [] }),
  }));

  await page.goto('/');
  await page.getByRole('link', { name: 'Sou entregador', exact: true }).click();
  await page.getByLabel('Nome completo').fill('Entregador de Teste');
  await page.getByLabel('E-mail').fill('entregador@example.test');
  await page.getByLabel('Senha').fill('Senha-sintetica-123');
  await page.getByRole('button', { name: /Criar conta/ }).click();
  await expect(page).toHaveURL(/\/confirmacao$/);
  await expect(page.getByRole('heading', { name: 'Confirmar e-mail' })).toBeVisible();

  await page.getByLabel('Código recebido por e-mail').fill('a'.repeat(43));
  await page.getByRole('button', { name: /Confirmar e-mail/ }).click();
  await expect(page).toHaveURL(/\/acesso$/);
  await page.getByLabel('E-mail').fill('entregador@example.test');
  await page.getByLabel('Senha').fill('Senha-sintetica-123');
  await page.getByRole('button', { name: /Entrar/ }).click();
  await expect(page).toHaveURL(/\/app\/entregador\/cadastro$/);
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
  await page.getByRole('link', { name: 'Criar conta', exact: true }).click();
  await expect(page).toHaveURL(/\/cadastro$/);
  await page.goBack();
  await expect(page.getByRole('heading', { name: 'Entrar', exact: true })).toBeVisible();
  await page.goto('/endereco-inexistente');
  await expect(page.getByRole('heading', { name: 'Página não encontrada.' })).toBeVisible();
  const favicon = await page.request.get('/favicon.ico');
  expect(favicon.status()).toBe(200);
});

test('separa acesso público do dashboard e encerra a sessão na página de acesso', async ({ page }) => {
  await page.route('**/api/v1/auth/login', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ accessToken: 'prepared-access', expiresIn: 900 }) }));
  await page.route('**/api/v1/auth/csrf', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ token: 'prepared-csrf' }) }));
  await page.route('**/api/v1/auth/logout', route => route.fulfill({ status: 204 }));
  await page.route('**/api/v1/me/capabilities', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ globalRoles: [], institutions: [], areas: [], patientProfile: false, delivererProfile: false, activeFamilyAuthorization: false }) }));
  await page.route('**/api/v1/me/profile', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ id: 'prepared', email: 'prepared@example.test', name: 'Conta preparada', phone: null, status: 'ATIVO', emailVerifiedAt: '2026-01-01T00:00:00Z', version: 1 }) }));
  await page.route('**/api/v1/orders', route => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
  await page.goto('/');
  await page.getByRole('link', { name: 'Entrar', exact: true }).first().click();
  await expect(page).toHaveURL(/\/acesso$/);
  await expect(page.getByRole('navigation', { name: 'Áreas do produto' })).toHaveCount(0);
  await expect(page.getByText('Como funciona', { exact: true })).toHaveCount(0);
  await page.getByRole('link', { name: 'Criar conta', exact: true }).first().click();
  await expect(page).toHaveURL(/\/cadastro$/);
  await page.getByRole('link', { name: 'Recuperar acesso', exact: true }).first().click();
  await expect(page).toHaveURL(/\/recuperacao$/);
  await page.getByRole('link', { name: 'Voltar para entrar', exact: true }).click();
  await page.getByLabel('E-mail').fill('prepared@example.test');
  await page.getByLabel('Senha').fill('Senha-sintetica-123');
  await page.getByRole('button', { name: /Entrar/ }).click();
  await expect(page).toHaveURL(/\/app$/);
  await expect(page.getByRole('heading', { name: 'Escolher área', exact: true })).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Áreas do produto' })).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.getByRole('button', { name: 'Sair', exact: true }).click();
  await expect(page).toHaveURL(/\/acesso$/);
  await expect(page.getByRole('heading', { name: 'Entrar', exact: true })).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Áreas do produto' })).toHaveCount(0);
});
