import { expect, test } from '@playwright/test';

test.skip(!process.env.DELIVERER_EMAIL, 'Execute por DelivererBrowserFlowTest com PostgreSQL descartável.');

test('entregador acompanha cadastro, documentos, veículo e bloqueio de ofertas', async ({ page }) => {
  await page.goto('/acesso');
  await page.getByLabel('E-mail', { exact: true }).fill(process.env.DELIVERER_EMAIL!);
  await page.getByLabel('Senha', { exact: true }).fill('Senha-sintetica-123');
  await page.locator('button[type=submit]').click();
  await expect(page.getByRole('status').filter({ hasText: 'Sessão iniciada' })).toBeVisible();

  const navigation = page.getByRole('navigation', { name: 'Áreas do produto' });
  await navigation.getByRole('link', { name: 'Cadastro de entrega', exact: true }).click();
  await expect(page).toHaveURL(/\/app\/entregador$/);
  const panel = page.getByRole('region', { name: 'Cadastro e comprovações' });
  await panel.getByLabel('Data de nascimento').fill('1990-01-15');
  await panel.getByRole('button', { name: 'Salvar cadastro' }).click();
  await expect(panel.getByRole('alert')).toContainText('Isto não aprova');

  const pdf = { name: 'habilitacao.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4\n%%EOF') };
  await panel.getByLabel('Categoria').selectOption('HABILITACAO');
  await panel.getByLabel(/Arquivo PDF/).setInputFiles(pdf);
  await panel.getByRole('button', { name: 'Enviar para quarentena' }).first().click();
  await expect(panel.getByRole('alert')).toContainText('quarentena');
  await expect(panel.locator('.registers').getByText('HABILITACAO', { exact: false })).toBeVisible();

  await panel.getByLabel('Placa').fill('TST1A23');
  await panel.getByLabel('Marca').fill('Marca de teste');
  await panel.getByLabel('Modelo', { exact: true }).fill('Modelo de teste');
  await panel.getByLabel('Cor').fill('Azul');
  await panel.getByLabel('Ano de fabricação').fill('2024');
  await panel.getByLabel('Ano do modelo').fill('2025');
  await panel.getByLabel('Vínculo').selectOption('PROPRIEDADE');
  await panel.getByRole('button', { name: 'Criar vínculo pendente' }).click();
  await expect(panel.getByRole('alert')).toContainText('Nenhuma consulta oficial');
  await expect(panel.getByText('TST1A23', { exact: false })).toBeVisible();

  await panel.getByLabel('Finalidade').selectOption('CRLV');
  const vehicleFile = panel.getByLabel('PDF, JPEG ou PNG (até 10 MiB)', { exact: true });
  await vehicleFile.setInputFiles({ ...pdf, name: 'crlv-1.pdf' });
  await panel.getByRole('button', { name: 'Enviar para quarentena' }).last().click();
  await expect(panel.getByRole('alert')).toContainText('revisão nova');
  await vehicleFile.setInputFiles({ ...pdf, name: 'crlv-2.pdf' });
  await panel.getByRole('button', { name: 'Enviar para quarentena' }).last().click();
  await expect(panel.getByText('substituído', { exact: false })).toBeVisible();

  await navigation.getByRole('link', { name: 'Minhas entregas', exact: true }).click();
  await expect(page).toHaveURL(/\/app\/entregas$/);
  const work = page.getByRole('region', { name: 'Ofertas e entregas' });
  await expect(work).toContainText('Nenhuma oferta elegível');
  await expect(work).toContainText('Aprovação profissional');
  await expect(work).toContainText('Nenhuma designação ativa ou histórica');
});
