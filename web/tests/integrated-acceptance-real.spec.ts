import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

test.skip(!process.env.INTEGRATED_ACCEPTANCE_EMAIL, 'Run through IntegratedAcceptanceBrowserFlowTest with disposable PostgreSQL.');

test('navegação integrada pública e autenticada por perfil e área', async ({ page, context }) => {
  test.setTimeout(60_000);
  const email = process.env.INTEGRATED_ACCEPTANCE_EMAIL!;
  const password = 'Senha-sintetica-123';

  await page.goto('/');
  await expect(page.getByRole('heading', { name: /Resultados de exames/ })).toBeVisible();
  await page.goto('/acesso');
  await expect(page.getByRole('heading', { name: 'Entrar', exact: true })).toBeVisible();
  await page.getByRole('link', { name: 'Criar conta', exact: true }).first().click();
  await expect(page).toHaveURL(/\/cadastro$/);
  await page.goBack();
  await expect(page.getByRole('heading', { name: 'Entrar', exact: true })).toBeVisible();
  await page.goForward();
  await expect(page.getByRole('heading', { name: 'Criar conta', exact: true })).toBeVisible();
  await page.goto('/endereco-inexistente');
  await expect(page.getByRole('heading', { name: 'Página não encontrada.' })).toBeVisible();
  await page.getByRole('button', { name: 'Voltar ao início' }).click();
  await expect(page).toHaveURL(/\/$/);

  await page.goto('/acesso');
  await page.getByLabel('E-mail', { exact: true }).fill(email);
  await page.getByLabel('Senha', { exact: true }).fill(password);
  await page.locator('button[type=submit]').click();
  await expect(page.getByRole('heading', { name: /Olá,/ })).toBeVisible();
  await expect(page.getByText('Sessão protegida', { exact: true })).toBeVisible();

  const nav = page.getByRole('navigation', { name: 'Áreas do produto' });
  const areas = [
    'Conta', 'Família', 'Benefícios', 'Pedidos', 'Privacidade',
    'Cadastro de entrega', 'Minhas entregas', 'Operação',
    'Análise de benefícios', 'Financeiro', 'Operação de privacidade',
  ];
  for (const area of areas) {
    await nav.getByRole('link', { name: area, exact: true }).click();
    await expect(page.getByRole('heading', { name: area, exact: true })).toBeVisible();
    await expect(nav.getByRole('link', { name: area, exact: true })).toHaveAttribute('aria-current', 'page');
  }

  await nav.getByRole('link', { name: 'Conta', exact: true }).click();
  await nav.getByRole('link', { name: 'Família', exact: true }).click();
  await page.goBack();
  await expect(page.getByRole('heading', { name: 'Seu perfil', exact: true })).toBeVisible();
  await page.goForward();
  await expect(page.getByRole('heading', { name: 'Família', exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Família', exact: true })).toBeVisible();
  await expect(page.getByText('Nenhum acesso familiar vigente.', { exact: true })).toBeVisible();

  await page.goto('/app/operacao');
  await expect(page.getByRole('heading', { name: 'Operação', exact: true })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Segurança de ações privilegiadas' })).toBeVisible();
  await page.goto('/app/financeiro');
  await expect(page.getByRole('heading', { name: 'Financeiro', exact: true })).toBeVisible();
  await expect(page.getByText(/Nenhum|indisponível|MFA|required/i).first()).toBeVisible();

  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await page.setViewportSize({ width: 1280, height: 900 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);

  await page.getByRole('button', { name: 'Sair', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Entrar', exact: true })).toBeVisible();
  await page.goto('/app/pedidos');
  await expect(page.getByRole('heading', { name: 'Entrar', exact: true })).toBeVisible();

  await page.getByLabel('E-mail', { exact: true }).fill(email);
  await page.getByLabel('Senha', { exact: true }).fill(password);
  await page.locator('button[type=submit]').click();
  await expect(page.getByRole('heading', { name: 'Olá. O que precisa avançar hoje?' })).toBeVisible();
  await context.clearCookies();
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Entrar', exact: true })).toBeVisible();
});
