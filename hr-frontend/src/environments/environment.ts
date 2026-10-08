export const environment = {
  production: false,
  apiUrl: 'http://localhost:8087/api',
    backendUrl: 'http://localhost:8087',          // ← new: server address, without /api

  wsUrl: 'ws://localhost:8087/ws',
    keycloak: {
    url: 'http://localhost:9090',
    realm: 'hr-realm',
    clientId: 'hr-angular'
  }
};
