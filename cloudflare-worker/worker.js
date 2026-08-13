import { verifyFirebaseIdToken, AuthError } from './auth.js';

// Origins allowed to call the authenticated /plaid/* routes with
// Authorization headers. Electron's production build loads over file://
// and sends Origin: null for cross-origin fetches, so 'null' is intentional
// here, not a wildcard fallback. Native Android (OkHttp) sends no Origin
// header at all, so it is unaffected by CORS either way.
const DEFAULT_ALLOWED_ORIGINS = ['http://localhost:5173', 'null'];
const MAX_TRANSACTION_COUNT = 250;
const KV_PREFIX = 'plaid:';

function allowedOrigins(env) {
  if (!env.ALLOWED_ORIGINS) return DEFAULT_ALLOWED_ORIGINS;
  return env.ALLOWED_ORIGINS.split(',').map((o) => o.trim()).filter(Boolean);
}

// Strict CORS for the new authenticated routes: only echoes back an origin
// that is on the allowlist, and never combines a wildcard with credentials.
function strictCorsHeaders(request, env) {
  const origin = request.headers.get('Origin');
  const headers = {
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    Vary: 'Origin',
  };
  if (origin && allowedOrigins(env).includes(origin)) {
    headers['Access-Control-Allow-Origin'] = origin;
  }
  return headers;
}

// Unchanged CORS for the legacy, unauthenticated routes kept only for the
// migration window - see the block comment above LEGACY_ROUTES below.
const legacyCorsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type',
};

function json(data, status, extraHeaders) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...extraHeaders, 'Content-Type': 'application/json' },
  });
}

async function readJsonBody(request) {
  if (request.method !== 'POST') return {};
  try {
    return await request.json();
  } catch (e) {
    return {};
  }
}

async function plaidCall(plaidUrl, clientId, secret, endpoint, body) {
  const response = await fetch(`${plaidUrl}${endpoint}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ client_id: clientId, secret, ...body }),
  });
  const data = await response.json();
  return { ok: response.ok, status: response.status, data };
}

// Never forward Plaid's raw error body to the client - it can include
// request_id/display_message and, on our own thrown errors, internal
// messages. Log server-side (picked up by `observability`) and return a
// generic shape instead.
function plaidError(result, context) {
  console.error('Plaid API error', context, result.status, result.data);
  return { error: 'Upstream request failed', context };
}

function kvKey(uid) {
  return `${KV_PREFIX}${uid}`;
}

async function getRecord(env, uid) {
  const raw = await env.PLAID_KV.get(kvKey(uid));
  return raw ? JSON.parse(raw) : null;
}

async function putRecord(env, uid, record) {
  await env.PLAID_KV.put(kvKey(uid), JSON.stringify(record));
}

async function requireUid(request, env) {
  try {
    return await verifyFirebaseIdToken(request, env);
  } catch (e) {
    if (e instanceof AuthError) throw e;
    throw new AuthError('Token verification failed');
  }
}

function sanitizedPlaidEnv(env) {
  const value = env.PLAID_ENV || 'sandbox';
  if (!['sandbox', 'development', 'production'].includes(value)) {
    throw new Error(`Invalid PLAID_ENV: ${value}`);
  }
  return value;
}

// --- Authenticated /plaid/* handlers -------------------------------------

async function handleLinkToken(request, env, ctx) {
  const uid = await requireUid(request, env);
  const result = await plaidCall(ctx.plaidUrl, ctx.clientId, ctx.secret, '/link/token/create', {
    user: { client_user_id: uid },
    client_name: 'Amex Benefit Tracker',
    products: ['transactions'],
    country_codes: ['US'],
    language: 'en',
    android_package_name: 'com.example.amexbenefittracker',
  });
  if (!result.ok) return json(plaidError(result, 'link-token'), 502, ctx.cors);
  return json({ link_token: result.data.link_token }, 200, ctx.cors);
}

async function handleExchange(request, env, ctx) {
  const uid = await requireUid(request, env);
  const body = await readJsonBody(request);
  if (!body.publicToken) return json({ error: 'publicToken is required' }, 400, ctx.cors);

  const result = await plaidCall(ctx.plaidUrl, ctx.clientId, ctx.secret, '/item/public_token/exchange', {
    public_token: body.publicToken,
  });
  if (!result.ok) return json(plaidError(result, 'exchange'), 502, ctx.cors);

  const now = Date.now();
  await putRecord(env, uid, {
    access_token: result.data.access_token,
    item_id: result.data.item_id,
    cursor: null,
    card_mappings: {},
    created_at: now,
    updated_at: now,
  });
  return json({ connected: true }, 200, ctx.cors);
}

async function handleStatus(request, env, ctx) {
  const uid = await requireUid(request, env);
  const record = await getRecord(env, uid);
  if (!record) return json({ connected: false }, 200, ctx.cors);

  const result = await plaidCall(ctx.plaidUrl, ctx.clientId, ctx.secret, '/accounts/get', {
    access_token: record.access_token,
  });
  if (!result.ok) return json(plaidError(result, 'status'), 502, ctx.cors);

  return json(
    {
      connected: true,
      accounts: result.data.accounts,
      card_mappings: record.card_mappings || {},
    },
    200,
    ctx.cors
  );
}

async function handleAccounts(request, env, ctx) {
  const uid = await requireUid(request, env);
  const record = await getRecord(env, uid);
  if (!record) return json({ error: 'Not connected to Plaid' }, 409, ctx.cors);

  const result = await plaidCall(ctx.plaidUrl, ctx.clientId, ctx.secret, '/accounts/get', {
    access_token: record.access_token,
  });
  if (!result.ok) return json(plaidError(result, 'accounts'), 502, ctx.cors);
  return json({ accounts: result.data.accounts }, 200, ctx.cors);
}

// Pages through Plaid's cursor internally until has_more is false, so
// clients never see pagination. The cursor is NOT written back to KV here -
// see handleCommitCursor for why.
async function handleSync(request, env, ctx) {
  const uid = await requireUid(request, env);
  const record = await getRecord(env, uid);
  if (!record) return json({ error: 'Not connected to Plaid' }, 409, ctx.cors);

  const fromCursor = record.cursor || null;
  let cursor = fromCursor;
  let hasMore = true;
  const added = [];

  while (hasMore) {
    const result = await plaidCall(ctx.plaidUrl, ctx.clientId, ctx.secret, '/transactions/sync', {
      access_token: record.access_token,
      cursor,
      count: MAX_TRANSACTION_COUNT,
    });
    if (!result.ok) return json(plaidError(result, 'sync'), 502, ctx.cors);

    added.push(...(result.data.added || []));
    cursor = result.data.next_cursor;
    hasMore = !!result.data.has_more;
  }

  return json({ added, from_cursor: fromCursor, next_cursor: cursor }, 200, ctx.cors);
}

// Plaid's sync cursor is destructive-on-advance: once committed, transactions
// before it can no longer be re-fetched. If the worker advanced it
// automatically inside handleSync and the client then failed to persist the
// resulting claims, those transactions would be lost forever. So the client
// commits explicitly, only after its own write succeeds - and only if
// nothing else has committed a newer cursor in the meantime (compare-and-swap
// against from_cursor), so two devices racing a sync can't silently skip
// each other's transactions.
async function handleCommitCursor(request, env, ctx) {
  const uid = await requireUid(request, env);
  const body = await readJsonBody(request);
  if (typeof body.cursor !== 'string') return json({ error: 'cursor is required' }, 400, ctx.cors);

  const record = await getRecord(env, uid);
  if (!record) return json({ error: 'Not connected to Plaid' }, 409, ctx.cors);

  const currentCursor = record.cursor || null;
  const fromCursor = body.fromCursor || null;
  if (currentCursor !== fromCursor) {
    return json(
      { error: 'Cursor was advanced by another device; re-sync before committing', current_cursor: currentCursor },
      409,
      ctx.cors
    );
  }

  record.cursor = body.cursor;
  record.updated_at = Date.now();
  await putRecord(env, uid, record);
  return json({ ok: true }, 200, ctx.cors);
}

async function handleMappings(request, env, ctx) {
  const uid = await requireUid(request, env);
  const body = await readJsonBody(request);
  if (!body.card_mappings || typeof body.card_mappings !== 'object') {
    return json({ error: 'card_mappings is required' }, 400, ctx.cors);
  }

  const record = await getRecord(env, uid);
  if (!record) return json({ error: 'Not connected to Plaid' }, 409, ctx.cors);

  record.card_mappings = { ...record.card_mappings, ...body.card_mappings };
  record.updated_at = Date.now();
  await putRecord(env, uid, record);
  return json({ card_mappings: record.card_mappings }, 200, ctx.cors);
}

async function handleDisconnect(request, env, ctx) {
  const uid = await requireUid(request, env);
  await env.PLAID_KV.delete(kvKey(uid));
  return json({ ok: true }, 200, ctx.cors);
}

// One-time import of a token a legacy client still holds client-side
// (Firestore's plaid_tokens field, or Android's plaid_prefs). Strictly
// insert-only: if this uid is already connected, it is a silent no-op so a
// stolen ID token can never be used to repoint an existing connection at an
// attacker-supplied Plaid item. Delete this route once the migration window
// closes (see cloudflare-worker note in the repo root CLAUDE.md).
async function handleMigrate(request, env, ctx) {
  const uid = await requireUid(request, env);
  const body = await readJsonBody(request);
  if (!body.accessToken) return json({ error: 'accessToken is required' }, 400, ctx.cors);

  const existing = await getRecord(env, uid);
  if (existing) return json({ migrated: false, reason: 'already_connected' }, 200, ctx.cors);

  const result = await plaidCall(ctx.plaidUrl, ctx.clientId, ctx.secret, '/item/get', {
    access_token: body.accessToken,
  });
  if (!result.ok) return json(plaidError(result, 'migrate'), 502, ctx.cors);

  const now = Date.now();
  await putRecord(env, uid, {
    access_token: body.accessToken,
    item_id: result.data.item.item_id,
    cursor: null,
    card_mappings: body.card_mappings && typeof body.card_mappings === 'object' ? body.card_mappings : {},
    created_at: now,
    updated_at: now,
  });
  return json({ migrated: true }, 200, ctx.cors);
}

const AUTHED_ROUTES = {
  '/plaid/link-token': { method: 'POST', handler: handleLinkToken },
  '/plaid/exchange': { method: 'POST', handler: handleExchange },
  '/plaid/status': { method: 'GET', handler: handleStatus },
  '/plaid/accounts': { method: 'POST', handler: handleAccounts },
  '/plaid/sync': { method: 'POST', handler: handleSync },
  '/plaid/cursor': { method: 'POST', handler: handleCommitCursor },
  '/plaid/mappings': { method: 'POST', handler: handleMappings },
  '/plaid/disconnect': { method: 'POST', handler: handleDisconnect },
  '/plaid/migrate': { method: 'POST', handler: handleMigrate },
};

// --- Legacy, unauthenticated routes ---------------------------------------
// TEMPORARY: kept only so already-shipped clients keep working until they've
// migrated to the /plaid/* routes above (see plan Phase "Rollout order").
// These predate caller verification entirely and accept a client-supplied
// accessToken - delete this whole block, and legacyCorsHeaders, once the
// migration window closes.
async function legacyCreateLinkToken(request, env, cors) {
  const body = await readJsonBody(request);
  const result = await plaidCall(plaidBaseUrl(env), env.PLAID_CLIENT_ID, env.PLAID_SECRET, '/link/token/create', {
    user: { client_user_id: body.userId || 'amex_tracker_user' },
    client_name: 'Amex Benefit Tracker',
    products: ['transactions'],
    country_codes: ['US'],
    language: 'en',
    android_package_name: 'com.example.amexbenefittracker',
  });
  return json(result.ok ? { link_token: result.data.link_token } : plaidError(result, 'legacy/create-link-token'), result.ok ? 200 : 502, cors);
}

async function legacyExchangeToken(request, env, cors) {
  const body = await readJsonBody(request);
  const result = await plaidCall(plaidBaseUrl(env), env.PLAID_CLIENT_ID, env.PLAID_SECRET, '/item/public_token/exchange', {
    public_token: body.publicToken,
  });
  return json(
    result.ok ? { access_token: result.data.access_token, item_id: result.data.item_id } : plaidError(result, 'legacy/exchange-token'),
    result.ok ? 200 : 502,
    cors
  );
}

async function legacyAccounts(request, env, cors) {
  const body = await readJsonBody(request);
  const result = await plaidCall(plaidBaseUrl(env), env.PLAID_CLIENT_ID, env.PLAID_SECRET, '/accounts/get', {
    access_token: body.accessToken,
  });
  return json(result.ok ? { accounts: result.data.accounts } : plaidError(result, 'legacy/accounts'), result.ok ? 200 : 502, cors);
}

async function legacySyncTransactions(request, env, cors) {
  const body = await readJsonBody(request);
  const result = await plaidCall(plaidBaseUrl(env), env.PLAID_CLIENT_ID, env.PLAID_SECRET, '/transactions/sync', {
    access_token: body.accessToken,
    cursor: body.cursor || null,
    count: Math.min(body.count || 100, MAX_TRANSACTION_COUNT),
  });
  return json(result.ok ? result.data : plaidError(result, 'legacy/sync-transactions'), result.ok ? 200 : 502, cors);
}

const LEGACY_ROUTES = {
  '/create-link-token': { method: 'POST', handler: legacyCreateLinkToken },
  '/exchange-token': { method: 'POST', handler: legacyExchangeToken },
  '/accounts': { method: 'POST', handler: legacyAccounts },
  '/sync-transactions': { method: 'POST', handler: legacySyncTransactions },
};

function plaidBaseUrl(env) {
  return `https://${sanitizedPlaidEnv(env)}.plaid.com`;
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const path = url.pathname;

    if (request.method === 'OPTIONS') {
      const isAuthedRoute = path in AUTHED_ROUTES;
      return new Response(null, {
        status: 204,
        headers: isAuthedRoute ? strictCorsHeaders(request, env) : legacyCorsHeaders,
      });
    }

    if (!env.PLAID_CLIENT_ID || !env.PLAID_SECRET) {
      return json(
        { error: 'Server configuration error: PLAID_CLIENT_ID or PLAID_SECRET environment variables are missing.' },
        500,
        legacyCorsHeaders
      );
    }
    if (!env.FIREBASE_PROJECT_ID) {
      return json({ error: 'Server configuration error: FIREBASE_PROJECT_ID is missing.' }, 500, legacyCorsHeaders);
    }

    if (path in LEGACY_ROUTES) {
      const route = LEGACY_ROUTES[path];
      if (request.method !== route.method) return json({ error: 'Method not allowed' }, 405, legacyCorsHeaders);
      try {
        return await route.handler(request, env, legacyCorsHeaders);
      } catch (error) {
        return json({ error: error.message }, 500, legacyCorsHeaders);
      }
    }

    if (path in AUTHED_ROUTES) {
      const cors = strictCorsHeaders(request, env);
      const route = AUTHED_ROUTES[path];
      if (request.method !== route.method) return json({ error: 'Method not allowed' }, 405, cors);

      let plaidUrl;
      try {
        plaidUrl = plaidBaseUrl(env);
      } catch (error) {
        return json({ error: error.message }, 500, cors);
      }

      try {
        return await route.handler(request, env, {
          cors,
          plaidUrl,
          clientId: env.PLAID_CLIENT_ID,
          secret: env.PLAID_SECRET,
        });
      } catch (error) {
        if (error instanceof AuthError) return json({ error: error.message }, 401, cors);
        return json({ error: error.message }, 500, cors);
      }
    }

    return new Response('Not Found', { status: 404, headers: legacyCorsHeaders });
  },
};
