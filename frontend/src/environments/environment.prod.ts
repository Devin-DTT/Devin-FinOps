export const environment = {
  production: true,
  // In production (Docker/AWS), the frontend is served by nginx which
  // reverse-proxies /ws/ to the backend container. Use a relative URL
  // so it works behind any domain or load balancer.
  wsUrl: (typeof window !== 'undefined' && (window as Record<string, unknown>)['__WS_URL__'])
    ? String((window as Record<string, unknown>)['__WS_URL__'])
    : 'ws://' + (typeof window !== 'undefined' ? window.location.host : 'localhost:8080') + '/ws/devin-data'
};
