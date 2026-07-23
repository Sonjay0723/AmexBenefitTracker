export default {
  async fetch(request, env, ctx) {
    // Standard CORS Headers
    const corsHeaders = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type',
    };

    // Handle CORS preflight OPTIONS request
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        status: 204,
        headers: corsHeaders,
      });
    }

    const clientId = env.PLAID_CLIENT_ID;
    const secret = env.PLAID_SECRET;
    const plaidEnv = env.PLAID_ENV || 'sandbox';
    const plaidUrl = `https://${plaidEnv}.plaid.com`;

    if (!clientId || !secret) {
      return new Response(
        JSON.stringify({ error: 'Server configuration error: PLAID_CLIENT_ID or PLAID_SECRET environment variables are missing.' }),
        { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const url = new URL(request.url);
    const path = url.pathname;

    try {
      let reqBody = {};
      if (request.method === 'POST') {
        try {
          reqBody = await request.json();
        } catch (e) {
          reqBody = {};
        }
      }

      if (path === '/create-link-token') {
        const response = await fetch(`${plaidUrl}/link/token/create`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            client_id: clientId,
            secret: secret,
            user: { client_user_id: reqBody.userId || 'amex_tracker_user' },
            client_name: 'Amex Benefit Tracker',
            products: ['transactions'],
            country_codes: ['US'],
            language: 'en',
            android_package_name: 'com.example.amexbenefittracker',
          }),
        });

        const data = await response.json();
        return new Response(JSON.stringify(response.ok ? { link_token: data.link_token } : data), {
          status: response.status,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
      }

      if (path === '/exchange-token') {
        const response = await fetch(`${plaidUrl}/item/public_token/exchange`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            client_id: clientId,
            secret: secret,
            public_token: reqBody.publicToken,
          }),
        });

        const data = await response.json();
        return new Response(
          JSON.stringify(response.ok ? { access_token: data.access_token, item_id: data.item_id } : data),
          {
            status: response.status,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          }
        );
      }

      if (path === '/accounts') {
        const response = await fetch(`${plaidUrl}/accounts/get`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            client_id: clientId,
            secret: secret,
            access_token: reqBody.accessToken,
          }),
        });

        const data = await response.json();
        return new Response(JSON.stringify(response.ok ? { accounts: data.accounts } : data), {
          status: response.status,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
      }

      if (path === '/sync-transactions') {
        const response = await fetch(`${plaidUrl}/transactions/sync`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            client_id: clientId,
            secret: secret,
            access_token: reqBody.accessToken,
            cursor: reqBody.cursor || null,
            count: reqBody.count || 100,
          }),
        });

        const data = await response.json();
        return new Response(JSON.stringify(data), {
          status: response.status,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
      }

      return new Response('Not Found', { status: 404, headers: corsHeaders });
    } catch (error) {
      return new Response(JSON.stringify({ error: error.message }), {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }
  },
};
