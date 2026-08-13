// Verifies Firebase Auth ID tokens (RS256) without the Admin SDK, so this
// can run in a Cloudflare Worker. Ports of the checks the Admin SDK itself
// performs: https://firebase.google.com/docs/auth/admin/verify-id-tokens
const JWKS_URL = 'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com';
const CLOCK_SKEW_SECONDS = 60;

function base64UrlToUint8Array(b64url) {
  const b64 = b64url.replace(/-/g, '+').replace(/_/g, '/');
  const padded = b64 + '='.repeat((4 - (b64.length % 4)) % 4);
  const raw = atob(padded);
  const bytes = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
  return bytes;
}

function base64UrlToJson(b64url) {
  return JSON.parse(new TextDecoder().decode(base64UrlToUint8Array(b64url)));
}

async function fetchJwks() {
  const cache = caches.default;
  const cacheKey = new Request(JWKS_URL);
  let response = await cache.match(cacheKey);
  if (response) return response.json();

  response = await fetch(JWKS_URL);
  if (!response.ok) throw new Error('Failed to fetch Firebase JWKS');
  // Cache respecting the endpoint's own max-age so key rotation is honored.
  await cache.put(cacheKey, response.clone());
  return response.json();
}

async function importRsaPublicKey(jwk) {
  return crypto.subtle.importKey(
    'jwk',
    jwk,
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['verify']
  );
}

class AuthError extends Error {}

// Returns the verified Firebase uid, or throws AuthError.
export async function verifyFirebaseIdToken(request, env) {
  const authHeader = request.headers.get('Authorization') || '';
  const match = authHeader.match(/^Bearer (.+)$/);
  if (!match) throw new AuthError('Missing bearer token');

  const token = match[1];
  const parts = token.split('.');
  if (parts.length !== 3) throw new AuthError('Malformed token');
  const [headerB64, payloadB64, signatureB64] = parts;

  let header, payload;
  try {
    header = base64UrlToJson(headerB64);
    payload = base64UrlToJson(payloadB64);
  } catch (e) {
    throw new AuthError('Malformed token payload');
  }

  // Pin to RS256 explicitly - never trust the header's own claim of "none"
  // or an HMAC alg, which would let a client sign with the public key.
  if (header.alg !== 'RS256') throw new AuthError('Unsupported token algorithm');
  if (!header.kid) throw new AuthError('Missing key id');

  const jwks = await fetchJwks();
  const jwk = (jwks.keys || []).find((k) => k.kid === header.kid);
  if (!jwk) throw new AuthError('Unknown signing key');

  const key = await importRsaPublicKey(jwk);
  const signedData = new TextEncoder().encode(`${headerB64}.${payloadB64}`);
  const signature = base64UrlToUint8Array(signatureB64);
  const valid = await crypto.subtle.verify('RSASSA-PKCS1-v1_5', key, signature, signedData);
  if (!valid) throw new AuthError('Invalid token signature');

  const projectId = env.FIREBASE_PROJECT_ID;
  const now = Math.floor(Date.now() / 1000);

  if (payload.aud !== projectId) throw new AuthError('Token audience mismatch');
  if (payload.iss !== `https://securetoken.google.com/${projectId}`) throw new AuthError('Token issuer mismatch');
  if (typeof payload.exp !== 'number' || payload.exp < now - CLOCK_SKEW_SECONDS) throw new AuthError('Token expired');
  if (typeof payload.iat !== 'number' || payload.iat > now + CLOCK_SKEW_SECONDS) throw new AuthError('Token issued in the future');
  if (typeof payload.sub !== 'string' || payload.sub.length === 0) throw new AuthError('Token missing subject');

  return payload.sub;
}

export { AuthError };
