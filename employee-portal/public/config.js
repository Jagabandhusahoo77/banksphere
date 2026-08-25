// Placeholder for local dev (npm run dev / vite preview) and as a
// fallback if the container's entrypoint script somehow doesn't run.
// In the built Docker image, this exact path is overwritten at
// container start from config.js.template — see
// docker-entrypoint.d/40-runtime-config.sh.
window.__RUNTIME_CONFIG__ = {
  API_BASE_URL: "",
};
