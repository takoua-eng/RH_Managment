import Keycloak from 'keycloak-js';

const keycloak = new Keycloak({
  url: 'http://localhost:9090',
  realm: 'hr-realm',
  clientId: 'hr-angular'
});

export function initializeKeycloak(): () => Promise<boolean> {
  return () =>
    keycloak.init({
      onLoad: 'check-sso',
      silentCheckSsoRedirectUri: window.location.origin + '/assets/silent-check-sso.html',
      pkceMethod: 'S256'
    });
}

export default keycloak;