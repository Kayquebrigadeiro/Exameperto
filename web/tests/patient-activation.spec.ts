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
        globalRoles: [], institutions: [], areas: patientCreated ? ['PACIENTE', 'ENTREGADOR'] : ['ENTREGADOR'],
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
