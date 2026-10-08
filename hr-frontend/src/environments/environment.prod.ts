// Configuration de production : adresses RELATIVES.
// Le frontend, l'API et Keycloak sont servis par la même adresse (Ingress),
// donc le même build fonctionne sur n'importe quel serveur.
export const environment = {
  production: true,
    backendUrl: '',                                // ← empty: same address as the frontend
  apiUrl: '/api',
  wsUrl: (window.location.protocol === 'https:' ? 'wss://' : 'ws://') + window.location.host + '/ws',
  keycloak: {
    url: window.location.origin + '/auth',
    realm: 'hr-realm',
    clientId: 'hr-angular'
  }
};