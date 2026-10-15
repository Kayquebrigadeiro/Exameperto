import { test, expect } from '@playwright/test';

test.skip(!process.env.ADDRESS_PATIENT_ID, 'Run through OrderBrowserFlowTest with disposable PostgreSQL.');

async function login(page: import('@playwright/test').Page) {
  await page.goto('/'); await page.getByRole('link', { name: 'Entrar', exact: true }).first().click();
  await page.getByLabel('E-mail', { exact: true }).fill(process.env.ADDRESS_PATIENT_EMAIL!); await page.getByLabel('Senha', { exact: true }).fill('Senha-sintetica-123'); await page.locator('button[type=submit]').click();
  await expect(page.getByRole('status').filter({ hasText: 'Sessão iniciada' })).toContainText('Sessão iniciada');
}
let candidateScreenshotTaken = false;
async function fillAndConfirm(panel: import('@playwright/test').Locator, index: number) {
  const values = ['Rua pública','10','Centro','São Paulo','SP','01001000']; const fields = ['street','number','district','city','state','postalCode'];
  for (let i=0;i<fields.length;i++) await panel.getByLabel(fields[i]).nth(index).fill(values[i]);
  await panel.getByRole('button', { name: 'Buscar localização' }).nth(index).click();
  await expect(panel.getByText('Rua pública, 10, Centro, São Paulo - SP')).toBeVisible();
  await expect(panel.getByText('Nenhum local foi selecionado automaticamente.')).toBeVisible();
  if (!candidateScreenshotTaken && process.env.ADDRESS_CANDIDATES_SCREENSHOT_PATH) {
    await panel.page().screenshot({ path: process.env.ADDRESS_CANDIDATES_SCREENSHOT_PATH!, fullPage: true });
    candidateScreenshotTaken = true;
  }
  await panel.locator('.candidate-card').first().getByRole('button', { name: 'Confirmar este local' }).click();
  await expect(panel.locator('.candidate-card')).toHaveCount(0);
  await expect(panel.getByText('Local confirmado para cotação.')).toHaveCount(index + 1);
}
test('busca, confirma, cria, recarrega e reconfirma endereço', async ({ page }) => {
  await login(page); await page.getByLabel('Selecionar área de atuação').selectOption('paciente'); await page.getByRole('navigation', { name: 'Áreas do produto' }).getByRole('link', { name: 'Meus pedidos', exact: true }).click();
  const panel=page.getByRole('region',{name:'Pedidos',exact:true}); await panel.getByLabel('Paciente representado').selectOption(process.env.ADDRESS_PATIENT_ID!); await panel.getByRole('button',{name:'Continuar'}).click(); await panel.getByLabel('Unidade de retirada').selectOption({label:process.env.ADDRESS_UNIT_LABEL!}); await panel.getByRole('button',{name:'Continuar'}).click(); await panel.getByRole('button',{name:'Continuar'}).click(); await panel.getByRole('button',{name:'4 Endereço'}).click();
  await fillAndConfirm(panel,0); await fillAndConfirm(panel,1);
  if (process.env.ADDRESS_CONFIRMED_SCREENSHOT_PATH) await page.screenshot({ path: process.env.ADDRESS_CONFIRMED_SCREENSHOT_PATH!, fullPage: true });
  await panel.getByRole('button',{name:'Continuar'}).click();
  await panel.getByLabel('Evidência de autorização de retirada').setInputFiles({name:'authorization.pdf',mimeType:'application/pdf',buffer:Buffer.from('%PDF-1.4\nsynthetic isolated evidence\n%%EOF')}); await expect(panel).toContainText('Documento anexado nesta sessão.'); await panel.getByRole('button',{name:'Continuar'}).click(); await panel.getByRole('button',{name:'Solicitar entrega'}).click();
  await expect(panel).toContainText('Pedido registrado e enviado para verificação'); await page.reload(); const reloaded=page.getByRole('region',{name:'Pedidos',exact:true}); await reloaded.getByRole('button',{name:/Pedido/}).first().click(); await expect(reloaded).toContainText('Rua pública, 10');
  await reloaded.getByRole('button',{name:'Alterar endereço'}).click(); await reloaded.getByLabel('city').nth(0).fill('São Paulo'); await expect(reloaded.getByText('Local confirmado para cotação.')).toHaveCount(0); await fillAndConfirm(reloaded,0); await fillAndConfirm(reloaded,1); await reloaded.getByRole('button',{name:'Salvar endereço'}).click(); await expect(reloaded).toContainText('Endereço atualizado');
  await page.screenshot({path:process.env.ADDRESS_SCREENSHOT_PATH!,fullPage:true});
});
