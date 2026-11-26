import crypto from 'node:crypto';

const HOST_HEADER_RE = /^[a-zA-Z0-9.-]+(:\d{1,5})?$/;

export function isSameOriginRequest(req, config) {
  const fetchSite = req.headers['sec-fetch-site'];
  if (fetchSite && fetchSite !== 'same-origin' && fetchSite !== 'none') {
    return false;
  }

  const origin = req.headers.origin;
  const host = String(req.headers.host || '').trim();
  const forwardedHost = String(req.headers['x-forwarded-host'] || '')
    .split(',')[0]
    .trim();

  const matchesHost = (candidateHost) => {
    const clean = candidateHost.toLowerCase();
    if (host && HOST_HEADER_RE.test(host) && clean === host.toLowerCase()) {
      return true;
    }
    if (forwardedHost && HOST_HEADER_RE.test(forwardedHost) && clean === forwardedHost.toLowerCase()) {
      return true;
    }
    return false;
  };

  if (origin) {
    let parsedOrigin;
    try {
      parsedOrigin = new URL(origin);
    } catch {
      return false;
    }
    if (config.allowedOrigins.includes(parsedOrigin.origin)) {
      return true;
    }
    return matchesHost(parsedOrigin.host);
  }

  // Allow non-browser test clients only when they carry no cross-site origin; browsers always send Origin on unsafe fetch.
  const referer = req.headers.referer;
  if (referer) {
    try {
      const parsedRef = new URL(referer);
      if (config.allowedOrigins.includes(parsedRef.origin)) return true;
      return matchesHost(parsedRef.host);
    } catch {
      return false;
    }
  }

  return true;
}

export function verifyCsrfToken(req, session) {
  if (!session || !session.csrfToken) return false;
  const headerToken = req.headers['x-csrf-token'];
  if (typeof headerToken !== 'string' || headerToken.length < 16 || headerToken.length > 256) {
    return false;
  }
  const actual = Buffer.from(headerToken, 'utf8');
  const expected = Buffer.from(session.csrfToken, 'utf8');
  if (actual.length !== expected.length) return false;
  return crypto.timingSafeEqual(actual, expected);
}
