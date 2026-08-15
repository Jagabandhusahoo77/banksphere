/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_EMPLOYEE_SERVICE_URL: string;
  readonly VITE_KYC_SERVICE_URL: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
