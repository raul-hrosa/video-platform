# Build context = repo root (video-platform/). See docker-compose.yml.

# ---- build ----
FROM node:22-alpine AS build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm install
COPY frontend/ .
ARG VITE_LIVEKIT_URL
ARG VITE_API_URL=/api
ARG VITE_MEDIA_PROVIDER=livekit
ENV VITE_LIVEKIT_URL=$VITE_LIVEKIT_URL
ENV VITE_API_URL=$VITE_API_URL
ENV VITE_MEDIA_PROVIDER=$VITE_MEDIA_PROVIDER
RUN npm run build

# ---- runtime ----
FROM nginx:1.27-alpine
COPY infra/nginx/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
