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
FROM nginx:1.27-alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
