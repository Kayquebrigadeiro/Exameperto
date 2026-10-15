export const appRouteLabels = {
  inicio: "Visão geral",
  perfil: "Conta",
  familia: "Família",
  beneficios: "Benefícios",
  pedidos: "Pedidos",
  privacidade: "Privacidade",
  entregador: "Cadastro de entrega",
  entregas: "Minhas entregas",
  operacao: "Operação",
  catalogo: "Unidades e protocolos",
  "analise-beneficios": "Análise de benefícios",
  financeiro: "Financeiro",
  "operacao-privacidade": "Operação de privacidade",
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
  return route === "inicio" ? "/app" : `/app/${route}`;
}

export function appRouteFromPath(
  pathname = window.location.pathname,
): AppRoute | null {
  if (pathname === "/app" || pathname === "/app/") return "inicio";
  if (!pathname.startsWith("/app/")) return null;
  const candidate = decodeURIComponent(pathname.slice(5)) as AppRoute;
  return candidate in appRouteLabels ? candidate : null;
}

export function isKnownPublicPath(pathname = window.location.pathname) {
  return pathname === "/" || Object.values(authPaths).includes(pathname);
}

export function navigate(path: string, replace = false) {
  window.history[replace ? "replaceState" : "pushState"]({}, "", path);
  window.dispatchEvent(new PopStateEvent("popstate"));
}
