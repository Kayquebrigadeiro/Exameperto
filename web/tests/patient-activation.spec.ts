import { test, expect } from '@playwright/test';

async function openPatientActivation(page: import('@playwright/test').Page, createStatus: number) {
  let patientCreated = false;
  let capabilityReads = 0;
  await page.route('**/api/v1/auth/csrf', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify({ token: 'csrf-token' }),
  }));
  await page.route('**/api/v1/auth/refresh', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify({ accessToken: 'patient-access', expiresIn: 900 }),
  }));
  await page.route('**/api/v1/me/capabilities', route => {
    capabilityReads++;
    return route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({
        globalRoles: [], institutions: [], areas: patientCreated ? ['PACIENTE'] : [],
        patientProfile: patientCreated, delivererProfile: false, activeFamilyAuthorization: false,
      }),
    });
  });
  await page.route('**/api/v1/me/profile', route => route.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify({ id: 'user-id', email: 'patient@example.test', name: 'Pessoa', phone: null, status: 'ATIVO', emailVerifiedAt: '2026-01-01T00:00:00Z', version: 1 }),
  }));
  await page.route('**/api/v1/me/patient', route => {
    if (route.request().method() === 'POST') {
      if (createStatus === 201) patientCreated = true;
      return route.fulfill({
        status: createStatus, contentType: 'application/json',
        body: JSON.stringify(createStatus === 201
          ? { id: 'patient-id', identityStatus: 'PENDENTE', version: 0 }
          : { code: 'PATIENT_CONFLICT', message: 'Não foi possível criar o perfil de paciente.' }),
      });
    }
    return route.fulfill(patientCreated
      ? { status: 200, contentType: 'application/json', body: JSON.stringify({ id: 'patient-id', identityStatus: 'PENDENTE', version: 0 }) }
      : { status: 404, contentType: 'application/json', body: JSON.stringify({ code: 'NOT_FOUND', message: 'Perfil ainda não cadastrado.' }) });
  });
  await page.route('**/api/v1/orders', route => route.fulfill({
    status: 200, contentType: 'application/json', body: '[]',
  }));
  await page.goto('/app/paciente');
  await expect(page.getByRole('heading', { name: 'Acesso não concedido' })).toBeVisible();
  await page.getByRole('link', { name: 'Conta e segurança' }).click();
  await expect(page.getByRole('heading', { name: 'Ativar perfil de paciente' })).toBeVisible();
  return () => capabilityReads;
}

test('ativa perfil próprio, atualiza capacidades e abre a área do paciente', async ({ page }) => {
  const capabilityReads = await openPatientActivation(page, 201);
  await page.getByLabel('CPF').fill('12345678901');
  await page.getByLabel('Data de nascimento').fill('1990-01-01');
  await page.getByRole('button', { name: 'Ativar perfil de paciente' }).click();
  await expect(page).toHaveURL(/\/app\/paciente$/);
  await expect(page.getByRole('heading', { name: 'Área do paciente' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Áreas do produto' }).getByRole('link', { name: 'Área do paciente' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Áreas do produto' }).getByRole('link', { name: 'Área do entregador' })).toHaveCount(0);
  await expect(page.getByText('Perfil de paciente ativo')).toBeVisible();
  await expect.poll(capabilityReads).toBe(2);
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Área do paciente' })).toBeVisible();
  await expect(page.getByText('Perfil de paciente ativo')).toBeVisible();
});

test('explica conflito ao tentar duplicar um perfil ou CPF', async ({ page }) => {
  await openPatientActivation(page, 409);
  await page.getByLabel('CPF').fill('12345678901');
  await page.getByLabel('Data de nascimento').fill('1990-01-01');
  await page.getByRole('button', { name: 'Ativar perfil de paciente' }).click();
  await expect(page.getByRole('alert')).toContainText('Já existe um perfil nesta conta ou este CPF já está vinculado');
  await expect(page).toHaveURL(/\/app\/conta$/);
});

test('login sem perfil abre a conta sem exibir área do entregador', async ({ page }) => {
  await page.route('**/api/v1/auth/login', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify({ accessToken: 'account-access', expiresIn: 900 }),
  }));
  await page.route('**/api/v1/me/capabilities', route => route.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify({ globalRoles: [], institutions: [], areas: [], patientProfile: false, delivererProfile: false, activeFamilyAuthorization: false }),
  }));
  await page.route('**/api/v1/me/profile', route => route.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify({ id: 'user-id', email: 'account@example.test', name: 'Pessoa', phone: null, status: 'ATIVO', emailVerifiedAt: '2026-01-01T00:00:00Z', version: 1 }),
  }));
  await page.route('**/api/v1/me/patient', route => route.fulfill({
    status: 404, contentType: 'application/json', body: JSON.stringify({ code: 'NOT_FOUND', message: 'Perfil ainda não cadastrado.' }),
  }));
  await page.goto('/acesso');
  await page.getByLabel('E-mail').fill('account@example.test');
  await page.getByLabel('Senha').fill('Senha-sintetica-123');
  await page.locator('button[type=submit]').click();
  await expect(page).toHaveURL(/\/app\/conta$/);
  await expect(page.getByRole('heading', { name: 'Ativar perfil de paciente' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Áreas do produto' }).getByRole('link', { name: 'Área do entregador' })).toHaveCount(0);
});

test('intenção de entregador permite enviar cadastro e atualiza o menu após o POST', async ({ page }) => {
  let delivererCreated = false;
  let capabilityReads = 0;
  await page.route('**/api/v1/auth/login', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify({ accessToken: 'deliverer-access', expiresIn: 900 }),
  }));
  await page.route('**/api/v1/me/capabilities', route => {
    capabilityReads++;
    return route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({
        globalRoles: [], institutions: [], areas: delivererCreated ? ['ENTREGADOR'] : [],
        patientProfile: false, delivererProfile: delivererCreated, activeFamilyAuthorization: false,
      }),
    });
  });
  await page.route('**/api/v1/me/deliverer', route => {
    if (route.request().method() === 'POST') {
      delivererCreated = true;
      return route.fulfill({
        status: 201, contentType: 'application/json',
        body: JSON.stringify({ id: 'deliverer-id', birthDate: '1990-01-01', status: 'RASCUNHO', version: 0 }),
      });
    }
    return route.fulfill({
      status: 404, contentType: 'application/json', body: JSON.stringify({ code: 'NOT_FOUND', message: 'Cadastro não encontrado.' }),
    });
  });
  await page.goto('/');
  await page.getByRole('link', { name: 'Sou entregador' }).click();
  await page.getByRole('navigation', { name: 'Navegação de acesso' }).getByRole('link', { name: 'Entrar' }).click();
  await page.getByLabel('E-mail').fill('deliverer@example.test');
  await page.getByLabel('Senha').fill('Senha-sintetica-123');
  await page.locator('button[type=submit]').click();
  await expect(page).toHaveURL(/\/app\/entregador\/cadastro$/);
  await expect(page.getByRole('heading', { name: 'Cadastro e comprovações' })).toBeVisible();
  await page.getByRole('tab', { name: 'Cadastro' }).click();
  await page.getByLabel('Data de nascimento').fill('1990-01-01');
  await page.getByRole('button', { name: 'Salvar cadastro' }).click();
  await expect(page.getByRole('navigation', { name: 'Áreas do produto' }).getByRole('link', { name: 'Área do entregador' })).toBeVisible();
  await expect.poll(() => capabilityReads).toBe(2);
});

test('acesso direto à área do entregador sem perfil continua negado', async ({ page }) => {
  await page.route('**/api/v1/auth/csrf', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify({ token: 'csrf-token' }),
  }));
  await page.route('**/api/v1/auth/refresh', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify({ accessToken: 'account-access', expiresIn: 900 }),
  }));
  await page.route('**/api/v1/me/capabilities', route => route.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify({ globalRoles: [], institutions: [], areas: [], patientProfile: false, delivererProfile: false, activeFamilyAuthorization: false }),
  }));
  await page.goto('/app/entregador');
  await expect(page).toHaveURL(/\/app\/entregador$/);
  await expect(page.getByRole('heading', { name: 'Acesso não concedido' })).toBeVisible();
});
