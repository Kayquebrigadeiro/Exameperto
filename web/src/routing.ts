export const appRouteLabels = {
  inicio: "Escolher área",
  paciente: "Área do paciente",
  "paciente-pedidos": "Meus pedidos",
  "paciente-beneficios": "Benefícios",
  pedidos: "Meus pedidos",
  beneficios: "Benefícios",
  perfil: "Conta",
  familia: "Área familiar",
  "familia-pedidos": "Pedidos do representado",
  "familia-autorizacoes": "Autorizações",
  entregador: "Área do entregador",
  "entregador-cadastro": "Cadastro e documentos",
  entregas: "Entregas",
  operacao: "Área de operação",
  catalogo: "Catálogo de unidades",
  "analise-beneficios": "Área de benefícios",
  "beneficios-fila": "Fila de benefícios",
  financeiro: "Financeiro",
  privacidade: "Privacidade da conta",
  "operacao-privacidade": "Privacidade operacional",
} as const;

export type AppRoute = keyof typeof appRouteLabels;
export type AuthRoute =
  | "register"
  | "verification"
  | "verification/resend"
  | "login"
  | "recovery"
  | "recovery/complete";

const authPaths: Record<AuthRoute, string> = {
  login: "/acesso",
  register: "/cadastro",
  verification: "/confirmacao",
  "verification/resend": "/confirmacao/reenviar",
  recovery: "/recuperacao",
  "recovery/complete": "/recuperacao/nova-senha",
};

export function authPath(route: AuthRoute) {
  return authPaths[route];
}

export function authRouteFromPath(
  pathname = window.location.pathname,
): AuthRoute {
  return (Object.entries(authPaths).find(
    ([, path]) => path === pathname,
  )?.[0] ?? "login") as AuthRoute;
}

export function appPath(route: AppRoute) {
  const paths: Record<AppRoute, string> = {
    inicio: "/app",
    paciente: "/app/paciente",
    "paciente-pedidos": "/app/paciente/pedidos",
    "paciente-beneficios": "/app/paciente/beneficios",
    pedidos: "/app/paciente/pedidos",
    beneficios: "/app/paciente/beneficios",
    perfil: "/app/conta",
    familia: "/app/familiar",
    "familia-pedidos": "/app/familiar/pedidos",
    "familia-autorizacoes": "/app/familiar/autorizacoes",
    entregador: "/app/entregador",
    "entregador-cadastro": "/app/entregador/cadastro",
    entregas: "/app/entregador/entregas",
    operacao: "/app/operacao",
    catalogo: "/app/operacao/catalogo",
    "analise-beneficios": "/app/beneficios",
    "beneficios-fila": "/app/beneficios/fila",
    financeiro: "/app/financeiro",
    privacidade: "/app/privacidade",
    "operacao-privacidade": "/app/privacidade/operacao",
  };
  return paths[route];
}

export function appRouteFromPath(
  pathname = window.location.pathname,
): AppRoute | null {
  if (pathname === "/app" || pathname === "/app/") return "inicio";
  const paths = Object.entries(appRouteLabels).map(([route]) => [route, appPath(route as AppRoute)] as const);
  const canonical = paths.find(([, path]) => path === pathname)?.[0] as AppRoute | undefined;
  if (canonical) return canonical;
  const aliases: Record<string, AppRoute> = {
    "/app/pedidos": "paciente-pedidos",
    "/app/beneficios": "paciente-beneficios",
    "/app/familia": "familia",
    "/app/entregas": "entregas",
    "/app/analise-beneficios": "beneficios-fila",
    "/app/operacao-privacidade": "operacao-privacidade",
    "/app/conta": "perfil",
    "/app/privacidade": "privacidade",
  };
  return aliases[pathname] ?? null;
}

export function isLegacyAppPath(pathname = window.location.pathname) {
  return [
    "/app/pedidos",
    "/app/beneficios",
    "/app/familia",
    "/app/entregas",
    "/app/analise-beneficios",
    "/app/operacao-privacidade",
  ].includes(pathname);
}

export function isKnownPublicPath(pathname = window.location.pathname) {
  return pathname === "/" || Object.values(authPaths).includes(pathname);
}

export function navigate(path: string, replace = false) {
  window.history[replace ? "replaceState" : "pushState"]({}, "", path);
  window.dispatchEvent(new PopStateEvent("popstate"));
}
