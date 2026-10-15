export type ApiNotice = { tone: "error" | "info"; text: string };

export async function parseApiResponse(response: Response) {
  const result =
    response.status === 204 ? {} : await response.json().catch(() => ({}));
  if (!response.ok) {
    const message =
      result.code === "INTEGRATION_UNAVAILABLE"
        ? `Não foi possível concluir a solicitação. A integração necessária está indisponível. ${result.message ?? "Nenhum efeito externo foi confirmado."}`
        : result.code === "INVALID_ROUTE"
          ? "A rota não pôde ser confirmada pelo provedor configurado. Nenhum orçamento foi criado; revise os endereços e tente novamente quando a integração estiver disponível."
          : result.code === "UNIT_NOT_CONFIRMED"
            ? "A unidade ainda não confirmou a retirada. O pedido foi preservado; aguarde essa verificação antes de solicitar o orçamento."
            : result.code === "BENEFIT_UNAVAILABLE"
              ? "Não há cobertura válida confirmada para este paciente e programa. O orçamento não foi criado."
              : result.code === "QUOTE_EXPIRED"
                ? "Este orçamento expirou. Solicite uma nova cotação para receber condições atuais."
        : result.code === "POLICY_UNDEFINED"
                  ? "A operação está bloqueada porque a política de tarifa ou cancelamento ainda não está vigente. Nenhum valor foi confirmado."
                  : (result.message ?? "Não foi possível concluir a solicitação.");
    const error = new Error(message) as Error & { code?: string };
    error.code = result.code;
    throw error;
  }
  return { result, etag: response.headers.get("ETag") };
}

export function uploadWithProgress<T>(
  path: string,
  body: FormData,
  accessToken: string,
  onProgress: (percentage: number) => void,
) {
  return new Promise<T>((resolve, reject) => {
    const request = new XMLHttpRequest();
    request.open("POST", path);
    request.setRequestHeader("Authorization", `Bearer ${accessToken}`);
    request.responseType = "json";
    request.upload.addEventListener("progress", (event) => {
      if (event.lengthComputable)
        onProgress(Math.round((event.loaded / event.total) * 100));
    });
    request.addEventListener("load", () => {
      if (request.status >= 200 && request.status < 300) {
        onProgress(100);
        resolve(request.response as T);
        return;
      }
      reject(new Error(request.response?.message ?? "Upload não concluído."));
    });
    request.addEventListener("error", () =>
      reject(new Error("A conexão do upload falhou.")),
    );
    request.addEventListener("abort", () =>
      reject(new Error("Upload cancelado.")),
    );
    request.send(body);
  });
}
