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
  await expect(page.getByRole('heading', { name: 'Solicitar cadastro', exact: true })).toBeVisible();
  await page.goto('/endereco-inexistente');
  await expect(page.getByRole('heading', { name: 'Página não encontrada.' })).toBeVisible();
  await page.getByRole('button', { name: 'Voltar ao início' }).click();
  await expect(page).toHaveURL(/\/$/);

  await page.goto('/acesso');
  await page.getByLabel('E-mail', { exact: true }).fill(email);
  await page.getByLabel('Senha', { exact: true }).fill(password);
  await page.locator('button[type=submit]').click();
  await expect(page.getByRole('heading', { name: 'Onde você quer atuar agora?' })).toBeVisible();
  await expect(page.getByText('Sessão protegida', { exact: true })).toBeVisible();

  const nav = page.getByRole('navigation', { name: 'Áreas do produto' });
  const areas = ['Paciente', 'Familiar', 'Entregador', 'Operação', 'Benefícios', 'Financeiro', 'Privacidade operacional'];
  for (const area of areas) {
    await page.getByLabel('Selecionar área de atuação').selectOption({ label: area });
    await expect(page.locator('main.portfolio-content > header.portfolio-topbar')).toContainText(area);
    await expect(nav).toBeVisible();
    if (area === 'Paciente') await page.screenshot({ path: 'test-results/reconstrucao-paciente-sanitizado.png', fullPage: true });
    if (area === 'Familiar') await page.screenshot({ path: 'test-results/reconstrucao-familiar-sanitizado.png', fullPage: true });
    if (area === 'Entregador') await page.screenshot({ path: 'test-results/reconstrucao-entregador-sanitizado.png', fullPage: true });
  }

  await page.getByLabel('Selecionar área de atuação').selectOption({ label: 'Paciente' });
  await nav.getByRole('link', { name: 'Benefícios', exact: true }).click();
  await expect(page).toHaveURL(/\/app\/paciente\/beneficios$/);
  await expect(page.getByLabel('Política vigente')).toBeVisible();
  await expect(page.getByLabel('Política vigente').locator('option').filter({ hasText: 'SYNTHETIC-BENEFIT' })).toHaveCount(1);
  await page.getByLabel('Selecionar área de atuação').selectOption({ label: 'Paciente' });
  await nav.getByRole('link', { name: 'Meus pedidos', exact: true }).click();
  await expect(page).toHaveURL(/\/app\/paciente\/pedidos$/);
  await page.getByLabel('Selecionar área de atuação').selectOption({ label: 'Familiar' });
  await expect(page.getByText('Atuando em nome de')).toBeVisible();
  await nav.getByRole('link', { name: 'Pedidos do representado', exact: true }).click();
  await expect(page).toHaveURL(/\/app\/familiar\/pedidos$/);
  await page.goBack();
  await expect(page).toHaveURL(/\/app\/familiar$/);
  await page.goForward();
  await expect(page).toHaveURL(/\/app\/familiar\/pedidos$/);
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Pedidos do representado', exact: true })).toBeVisible();

  await page.goto('/app/operacao');
  await expect(page.getByRole('heading', { name: 'Área de operação', exact: true })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Segurança de ações privilegiadas' })).toBeVisible();
  await page.goto('/app/financeiro');
  await expect(page.locator('main.portfolio-content > header.portfolio-topbar').getByRole('heading', { name: 'Financeiro', exact: true })).toBeVisible();
  await expect(page.getByText(/Nenhum|indisponível|MFA|required/i).first()).toBeVisible();

  await page.screenshot({ path: 'test-results/reconstrucao-financeiro-sanitizado.png', fullPage: true });
  await page.goto('/app/privacidade/operacao');
  await page.screenshot({ path: 'test-results/reconstrucao-operacao-privacidade-sanitizado.png', fullPage: true });

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
  await expect(page.getByRole('heading', { name: 'Onde você quer atuar agora?' })).toBeVisible();
  await context.clearCookies();
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Entrar', exact: true })).toBeVisible();
});
