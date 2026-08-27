# --- Build stage ---
FROM node:20-alpine AS build
WORKDIR /build

COPY package.json package-lock.json* ./
RUN npm install

COPY . .
ARG VITE_EMPLOYEE_SERVICE_URL=http://localhost:8085
ENV VITE_EMPLOYEE_SERVICE_URL=$VITE_EMPLOYEE_SERVICE_URL
ARG VITE_KYC_SERVICE_URL=http://localhost:8086
ENV VITE_KYC_SERVICE_URL=$VITE_KYC_SERVICE_URL
RUN npm run build

# --- Run stage ---
# Pinned (not the floating "alpine" tag) — Alpine 3.24.1, which carries
# patched libxml2/musl/nghttp2/zlib versions that 1.27-alpine (Alpine
# 3.21.3) did not. See docs/deployment or the CVE remediation report for
# the exact CVEs this addresses (CVE-2025-32414/32415/49794/49795/49796,
# CVE-2026-6732, CVE-2026-40200, CVE-2026-27135, CVE-2026-22184).
FROM nginx:1.31.4-alpine
# CVE-2026-14456 (OpenSSL DoS via unbounded memory growth in QUIC server)
# — fixed in libssl3/libcrypto3 3.5.8-r0, which Alpine's 3.24 package
# repo has published but no nginx:*-alpine tag has been rebuilt with yet
# (confirmed: nginx:1.31.4-alpine, the floating nginx:alpine tag, and
# even alpine:edge are all still on 3.5.7-r0 as of this build — there is
# currently no base-image upgrade path for this one). A targeted upgrade
# of exactly these two packages, not a blanket `apk upgrade`, pulls the
# fix from the SAME Alpine 3.24 branch already in use — no version jump,
# no untested packages. Revisit/remove once a published tag ships this
# baked in.
RUN apk add --no-cache --upgrade libssl3 libcrypto3
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY docker-entrypoint.d/40-runtime-config.sh /docker-entrypoint.d/40-runtime-config.sh
RUN chmod +x /docker-entrypoint.d/40-runtime-config.sh

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
