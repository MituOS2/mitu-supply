export class Router {
  constructor() {
    this.routes = [];
  }

  on(method, pattern, handler) {
    const segments = pattern.split('/').filter(Boolean);
    this.routes.push({
      method: method.toUpperCase(),
      segments,
      handler,
    });
  }

  get(pattern, handler) {
    this.on('GET', pattern, handler);
  }
  post(pattern, handler) {
    this.on('POST', pattern, handler);
  }
  put(pattern, handler) {
    this.on('PUT', pattern, handler);
  }
  patch(pattern, handler) {
    this.on('PATCH', pattern, handler);
  }
  delete(pattern, handler) {
    this.on('DELETE', pattern, handler);
  }

  match(method, pathname) {
    const upper = method.toUpperCase();
    const parts = pathname.split('/').filter(Boolean);

    for (const route of this.routes) {
      if (route.method !== upper) continue;
      if (route.segments.length !== parts.length) continue;

      const params = Object.create(null);
      let matched = true;
      for (let i = 0; i < route.segments.length; i += 1) {
        const expected = route.segments[i];
        const actual = parts[i];
        if (expected.startsWith(':')) {
          const key = expected.slice(1);
          try {
            params[key] = decodeURIComponent(actual);
          } catch {
            matched = false;
            break;
          }
        } else if (expected !== actual) {
          matched = false;
          break;
        }
      }
      if (matched) {
        return { handler: route.handler, params };
      }
    }
    return null;
  }
}
