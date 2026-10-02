import { test, expect } from '@playwright/test';
import { readFile, rm } from 'node:fs/promises';
import { join } from 'node:path';

test.skip(!process.env.TEST_FAMILY_MAILBOX, 'Run through FamilyBrowserFlowTest with PostgreSQL and test-only private-message capture.');

test('navegador → backend → PostgreSQL: perfil, convite, aceite, confirmação e revogação', async ({ page }) => {
  const mailbox = process.env.TEST_FAMILY_MAILBOX!;
  const password = 'Senha-sintetica-123';
  const owner = 'patient-browser@example.test';
  const family = 'family-browser@example.test';
  const choose = async (name: string) => page.getByRole('button', { name, exact: true }).first().click();
  const accountForm = () => page.locator('.card form');
  const register = async (email: string) => {
    await choose('Solicitar cadastro');
    await accountForm().getByLabel('Nome completo').fill('Conta Sintética Navegador');
    await accountForm().getByLabel('E-mail', { exact: true }).fill(email);
    await accountForm().getByLabel('Senha', { exact: true }).fill(password);
    await accountForm().getByRole('button', { name: /Solicitar cadastro/ }).click();
    await expect(page.getByRole('alert')).toContainText('Solicitação recebida');
    await choose('Confirmar e-mail');
    let token=''; await expect.poll(async()=>{token=await readFile(join(mailbox,'confirmation'),'utf8').catch(()=> '');return token.length;}).toBe(43);
    await accountForm().getByLabel('Código recebido por e-mail').fill(token);
    await accountForm().getByRole('button', { name: /Confirmar e-mail/ }).click();
    await expect(page.getByRole('alert')).toContainText('E-mail confirmado'); await rm(join(mailbox,'confirmation'));
  };
  const login = async (email: string) => {
    await choose('Entrar'); await accountForm().getByLabel('E-mail', { exact: true }).fill(email); await accountForm().getByLabel('Senha', { exact: true }).fill(password);
    await accountForm().getByRole('button', { name: /Entrar/ }).click(); await expect(page.getByRole('alert')).toContainText('Sessão iniciada');
  };
  const logout = async () => { await page.getByRole('button',{name:'Sair',exact:true}).click(); await expect(page.getByRole('alert')).toContainText('Sessão encerrada'); };

  await page.goto('/'); await register(owner); await login(owner);
  const profile=page.locator('.work-card').filter({hasText:'Criar perfil de paciente'});
  await profile.getByLabel('CPF').fill('88888888888'); await profile.getByLabel('Data de nascimento').fill('1990-01-01'); await profile.getByRole('button',{name:'Criar perfil'}).click();
  await expect(page.locator('.family .feedback')).toContainText('identidade pendente');
  const invite=page.locator('.work-card').filter({hasText:'Convidar familiar'});
  await invite.getByLabel('E-mail do destinatário').fill(family); await invite.getByLabel('pedidos').check(); await invite.getByRole('button',{name:'Enviar convite privado'}).click();
  await expect(page.locator('.family .feedback')).toContainText('aceite ainda não concede acesso');
  let invitation=''; await expect.poll(async()=>{invitation=await readFile(join(mailbox,'invitation'),'utf8').catch(()=> '');return invitation.includes('Código de uso único');}).toBe(true);
  const match=invitation.match(/Identificador do convite: ([0-9a-f-]{36}) Código de uso único: ([A-Za-z0-9_-]{43})/); expect(match).not.toBeNull();
  await logout(); await register(family); await login(family);
  const acceptance=page.locator('.work-card').filter({hasText:'Aceitar convite'});
  await acceptance.getByLabel('Identificador do convite').fill(match![1]); await acceptance.getByLabel('Código de uso único').fill(match![2]); await acceptance.getByRole('button',{name:'Aceitar sem ativar'}).click();
  await expect(page.locator('.family .feedback')).toContainText('ainda não há acesso'); await expect(page.getByText('Nenhum acesso familiar vigente.')).toBeVisible();
  await logout(); await login(owner);
  const confirmation=page.locator('.work-card').filter({hasText:'Confirmar autorização'});
  await confirmation.getByLabel('Identificador aceito').fill(match![1]); await confirmation.getByLabel('pedidos').check();
  const tomorrow=new Date(Date.now()+24*60*60*1000); const local=new Date(tomorrow.getTime()-tomorrow.getTimezoneOffset()*60000).toISOString().slice(0,16);
  await confirmation.getByLabel('Expira em').fill(local); await confirmation.getByLabel('Senha do paciente').fill(password); await confirmation.getByRole('button',{name:'Confirmar e ativar'}).click();
  await expect(page.locator('.family .feedback')).toContainText('Autorização ativada'); await expect(page.getByText('VIGENTE',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Revogar',exact:true}).click(); await expect(page.locator('.family .feedback')).toContainText('revogada imediatamente'); await expect(page.getByText('REVOGADA',{exact:true})).toBeVisible();
});
