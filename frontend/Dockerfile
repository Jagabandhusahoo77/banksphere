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
FROM nginx:1.27-alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
