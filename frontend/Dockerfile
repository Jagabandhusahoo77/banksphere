# --- Build stage ---
FROM node:20-alpine AS build
WORKDIR /build

COPY package.json package-lock.json* ./
RUN npm install

COPY . .
ARG VITE_CUSTOMER_SERVICE_URL=http://localhost:8081
ARG VITE_ACCOUNT_SERVICE_URL=http://localhost:8082
ARG VITE_TRANSACTION_SERVICE_URL=http://localhost:8083
ARG VITE_BENEFICIARY_SERVICE_URL=http://localhost:8084
ARG VITE_KYC_SERVICE_URL=http://localhost:8086
ENV VITE_CUSTOMER_SERVICE_URL=$VITE_CUSTOMER_SERVICE_URL
ENV VITE_ACCOUNT_SERVICE_URL=$VITE_ACCOUNT_SERVICE_URL
ENV VITE_TRANSACTION_SERVICE_URL=$VITE_TRANSACTION_SERVICE_URL
ENV VITE_BENEFICIARY_SERVICE_URL=$VITE_BENEFICIARY_SERVICE_URL
ENV VITE_KYC_SERVICE_URL=$VITE_KYC_SERVICE_URL
RUN npm run build

# --- Run stage ---
# Pinned (not the floating "alpine" tag) — Alpine 3.24.1, which carries
# patched libxml2/musl/nghttp2/zlib versions that 1.27-alpine (Alpine
# 3.21.3) did not. See docs/deployment or the CVE remediation report for
# the exact CVEs this addresses (CVE-2025-32414/32415/49794/49795/49796,
# CVE-2026-6732, CVE-2026-40200, CVE-2026-27135, CVE-2026-22184).
FROM nginx:1.31.4-alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY docker-entrypoint.d/40-runtime-config.sh /docker-entrypoint.d/40-runtime-config.sh
RUN chmod +x /docker-entrypoint.d/40-runtime-config.sh

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
