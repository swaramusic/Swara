interface TauriInternals {
  invoke: <T = unknown>(cmd: string, args?: Record<string, unknown>) => Promise<T>;
}

function getTauri(): TauriInternals | null {
  if (typeof window === 'undefined') return null;
  const w = window as unknown as { __TAURI_INTERNALS__?: TauriInternals };
  return w.__TAURI_INTERNALS__ ?? null;
}

export function isTauriDesktop(): boolean {
  return getTauri() !== null;
}

export async function tauriInvoke<T = unknown>(
  cmd: string,
  args?: Record<string, unknown>
): Promise<T> {
  const tauri = getTauri();
  if (!tauri) throw new Error('Tauri runtime not available');
  return tauri.invoke<T>(cmd, args);
}

export const tauriFetch: typeof fetch = async (input, init) => {
  const url = typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url;
  const headers: Record<string, string> = {};
  if (init?.headers) {
    new Headers(init.headers).forEach((v, k) => {
      headers[k] = v;
    });
  }
  const body = typeof init?.body === 'string' ? init.body : undefined;
  const res = await tauriInvoke<{ status: number; body: string }>('http_request', {
    url,
    method: init?.method ?? 'GET',
    headers,
    body,
  });
  return new Response(res.body, { status: res.status });
};
