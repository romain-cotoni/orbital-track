// Fichier VERSIONNE : il ne doit contenir aucun secret en clair.
//
// Prefixe de deploiement, lu a l'execution depuis le <base href> genere par
// --base-href au build. Vaut '/' en local et '/orbital-track/' en prod, sans
// qu'aucune valeur ne soit codee en dur ici.
const basePath = new URL(document.baseURI).pathname;

const wsScheme = window.location.protocol === 'https:' ? 'wss:' : 'ws:';

export const environment = {
  production: true,
  apiUrl: `${basePath}api`,
  wsUrl: `${wsScheme}//${window.location.host}${basePath}api/ws`,
  // Placeholder remplace au build par l'ARG CESIUM_TOKEN du Dockerfile, lui-meme alimente par CESIUM_TOKEN du .env via docker-compose.yml.
  cesiumToken: '__CESIUM_TOKEN__'
};
