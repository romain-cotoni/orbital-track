// Modele pour le developpement local avec "ng serve" (hors Docker).
//
//   cp src/environments/environment.example.ts src/environments/environment.ts
//
// Puis renseigne ton token Cesium Ion : https://ion.cesium.com
// environment.ts est ignore par git (il contient ton token en clair).
//
// Ce fichier ne sert PAS aux builds Docker : "ng build --configuration=production"
// remplace environment.ts par environment.prod.ts (fileReplacements d'angular.json),
// ou le token est injecte depuis le .env.

export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api',
  wsUrl: 'ws://localhost:8080/api/ws',
  cesiumToken: 'ADD_YOUR_OWN_TOKEN_HERE'
};
