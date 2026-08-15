/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_CUSTOMER_SERVICE_URL: string;
  readonly VITE_ACCOUNT_SERVICE_URL: string;
  readonly VITE_TRANSACTION_SERVICE_URL: string;
  readonly VITE_BENEFICIARY_SERVICE_URL: string;
  readonly VITE_KYC_SERVICE_URL: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
