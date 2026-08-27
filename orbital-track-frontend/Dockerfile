# Prefixe public sous lequel l'app est servie. Doit se terminer par un "/".
#   /                -> dev local
#   /orbital-track/  -> prod https://romain-cotoni.fr/orbital-track/
# Declare avant le premier FROM pour etre visible dans les deux stages.
ARG BASE_PATH=/

# Stage 1: Build Angular app
FROM node:22-alpine AS builder
ARG BASE_PATH
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --prefer-offline

COPY . .

# Token Cesium Ion, injecte depuis le .env via docker-compose.yml.
# Il finira dans le bundle JS servi au navigateur (c'est inevitable pour un
# token front) : l'objectif est seulement de le garder hors du depot git.
# Pense a le restreindre par domaine cote Cesium Ion.
ARG CESIUM_TOKEN=""
RUN sed -i "s|__CESIUM_TOKEN__|${CESIUM_TOKEN}|" src/environments/environment.prod.ts

# --base-href fixe le <base href> de index.html et donc la resolution de tous
# les assets et des URLs relatives (dont l'API, cf. environment.prod.ts).
RUN npm run build -- --configuration=production --base-href="${BASE_PATH}"

# Stage 2: Serve with Nginx
FROM nginx:alpine
ARG BASE_PATH

# Les fichiers sont places SOUS le prefixe, ce qui permet a nginx de servir
# avec un simple "root" (voir nginx.conf.template).
COPY --from=builder /app/dist/orbital-tracker/browser /usr/share/nginx/html${BASE_PATH}

# Rendu en /etc/nginx/conf.d/default.conf au demarrage, apres substitution
# de ${BASE_PATH} par l'entrypoint de l'image nginx.
COPY nginx.conf.template /etc/nginx/templates/default.conf.template

EXPOSE 80
