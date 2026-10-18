import { createHmac } from "node:crypto";
import { test, expect } from "@playwright/test";

test.skip(
  !process.env.ASSESSMENT_RESPONSIBLE_EMAIL,
  "Run through ServiceAssessmentBrowserFlowTest with disposable PostgreSQL.",
);
function totp(secret: string, stepOffset = 0) {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  let buffer = 0,
    bits = 0;
  const bytes: number[] = [];
  for (const c of secret.replace(/=+$/, "").toUpperCase()) {
    buffer = (buffer << 5) | alphabet.indexOf(c);
    bits += 5;
    if (bits >= 8) {
      bits -= 8;
      bytes.push((buffer >> bits) & 255);
    }
  }
  const data = Buffer.alloc(8);
  data.writeBigInt64BE(BigInt(Math.floor(Date.now() / 1000 / 30) + stepOffset));
  const digest = createHmac("sha1", Buffer.from(bytes)).update(data).digest();
  const o = digest[digest.length - 1] & 15;
  return String(
    (((digest[o] & 127) << 24) |
      ((digest[o + 1] & 255) << 16) |
      ((digest[o + 2] & 255) << 8) |
      (digest[o + 3] & 255)) %
      1000000,
  ).padStart(6, "0");
}
async function elevate(page: any, secret: string) {
  const mfa = page.getByRole("region", {
    name: "Segurança de ações privilegiadas",
  });
  page.on("response", (r) => {
    if (/mfa|assessment/.test(r.url()))
      console.log(`HTTP ${r.status()} ${new URL(r.url()).pathname}`);
  });
  await mfa.getByLabel("Código TOTP").fill(totp(secret, 1));
  await mfa.getByRole("button", { name: "Validar sessão" }).click();
  await expect(mfa).toContainText("Sessão elevada");
}
async function login(page: any, email: string, secret: string) {
  await page.goto("/acesso");
  await page.getByLabel("E-mail", { exact: true }).fill(email);
  await page.getByLabel("Senha", { exact: true }).fill("Senha-sintetica-123");
  await page.locator("button[type=submit]").click();
  await expect(
    page.getByRole("status").filter({ hasText: "Sessão iniciada" }),
  ).toContainText("Sessão iniciada");
  const choose = page.getByRole("button", { name: /ATUAR COMO Financeiro/ });
  if (await choose.count()) await choose.click();
  else {
    const picker = page.getByLabel("Selecionar área de atuação");
    if (await picker.count()) await picker.selectOption("financeiro");
    else if (
      !(await page
        .getByRole("heading", { name: "Financeiro", exact: true })
        .count())
    )
      await page
        .getByRole("navigation", { name: "Áreas do produto" })
        .getByRole("link", { name: "Financeiro", exact: true })
        .click();
  }
  await expect(page.locator("h1")).toHaveText("Financeiro");
  await elevate(page, secret);
}
test("proposta e revisão de serviço parcial usam áreas financeiras reais", async ({
  browser,
}) => {
  const responsible = await browser.newPage();
  await login(
    responsible,
    process.env.ASSESSMENT_RESPONSIBLE_EMAIL!,
    process.env.ASSESSMENT_RESPONSIBLE_MFA_SECRET!,
  );
  await responsible
    .getByRole("button", { name: "Pendências de apuração" })
    .click();
  const queue = responsible.getByRole("region", {
    name: "Pendências de apuração",
  });
  await queue.getByRole("button", { name: /Pedido/ }).click();
  await responsible.getByLabel("Valor devido ao entregador").fill("12.00");
  await responsible.getByLabel("Parcela do paciente").fill("10.00");
  await responsible.getByLabel("Parcela institucional").fill("2.00");
  await responsible.getByLabel("Motivo").fill("Deslocamento comprovado");
  await responsible
    .getByLabel("Evidências, uma referência por linha")
    .fill("evidencia:deslocamento");
  await responsible.getByRole("button", { name: "Registrar proposta" }).click();
  await expect(
    responsible.getByRole("alert").filter({ hasText: "Proposta registrada" }),
  ).toBeVisible();
  await responsible.close();
  const reviewer = await browser.newPage();
  await login(
    reviewer,
    process.env.ASSESSMENT_REVIEWER_EMAIL!,
    process.env.ASSESSMENT_REVIEWER_MFA_SECRET!,
  );
  await reviewer
    .getByRole("button", { name: "Pendências de apuração" })
    .click();
  await reviewer.getByRole("button", { name: /Pedido/ }).click();
  reviewer.once("dialog", (d) =>
    d.accept("Revisão independente com evidência suficiente"),
  );
  await reviewer.getByRole("button", { name: "Aprovar com MFA" }).click();
  await expect(
    reviewer.getByRole("alert").filter({ hasText: "Apuração aprovada" }),
  ).toBeVisible();
  await reviewer.close();
});
