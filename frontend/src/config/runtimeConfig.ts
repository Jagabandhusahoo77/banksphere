/**
 * Runtime (container-startup) config, as opposed to Vite's build-time
 * `import.meta.env.VITE_*` values. Populated by `public/config.js`
 * (served before the app bundle — see index.html) which the Docker
 * image's docker-entrypoint.d script rewrites from
 * `config.js.template` via `envsubst` on every container start, using
 * the API_BASE_URL env var the GitOps chart injects into the
 * customer-portal Deployment. This is what lets ONE built image be
 * promoted unchanged from DEV to TEST — like the six backend images —
 * instead of needing a separate Vite build per environment.
 */
declare global {
  interface Window {
    __RUNTIME_CONFIG__?: {
      API_BASE_URL?: string;
    };
  }
}

export function getRuntimeApiBaseUrl(): string | undefined {
  const value = window.__RUNTIME_CONFIG__?.API_BASE_URL;
  return value ? value : undefined;
}
