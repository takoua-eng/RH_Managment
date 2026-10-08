# =========================================================
# Étape 1 : compilation Angular avec Node.js
# =========================================================
FROM node:22-alpine AS build
WORKDIR /app

# Les dépendances d'abord : cette couche est réutilisée tant que package*.json ne change pas
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

# Compilation de production (limite mémoire de Node pour la VM outils)
COPY . .
ENV NODE_OPTIONS=--max-old-space-size=1536
RUN npx ng build --configuration production

# Angular 17+ place les fichiers dans dist/<projet>/browser, les versions
# antérieures dans dist/<projet> : on rassemble le résultat dans /out
RUN mkdir -p /out \
    && if ls -d dist/*/browser > /dev/null 2>&1; then cp -r dist/*/browser/. /out/; else cp -r dist/*/. /out/; fi \
    && test -f /out/index.html

# =========================================================
# Étape 2 : Nginx sans privilèges root
# =========================================================
FROM nginxinc/nginx-unprivileged:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /out /usr/share/nginx/html

EXPOSE 8080