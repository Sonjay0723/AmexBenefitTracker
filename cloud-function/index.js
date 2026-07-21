const functions = require('@google-cloud/functions-framework');
const axios = require('axios');

// Plaid API proxy broker
functions.http('plaidBroker', async (req, res) => {
  // CORS configuration
  res.set('Access-Control-Allow-Origin', '*');
  res.set('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.set('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') {
    res.status(204).send('');
    return;
  }

  const clientId = process.env.PLAID_CLIENT_ID;
  const secret = process.env.PLAID_SECRET;
  const env = process.env.PLAID_ENV || 'sandbox';
  const plaidUrl = `https://${env}.plaid.com`;

  if (!clientId || !secret) {
    res.status(500).json({ error: 'Server configuration error: PLAID_CLIENT_ID or PLAID_SECRET environment variables are missing.' });
    return;
  }

  try {
    if (req.path === '/create-link-token') {
      const response = await axios.post(`${plaidUrl}/link/token/create`, {
        client_id: clientId,
        secret: secret,
        user: { client_user_id: req.body.userId || 'amex_tracker_user' },
        client_name: 'Amex Benefit Tracker',
        products: ['transactions'],
        country_codes: ['US'],
        language: 'en',
        android_package_name: 'com.example.amexbenefittracker'
      });
      res.json({ link_token: response.data.link_token });
      return;
    }

    if (req.path === '/exchange-token') {
      const response = await axios.post(`${plaidUrl}/item/public_token/exchange`, {
        client_id: clientId,
        secret: secret,
        public_token: req.body.publicToken
      });
      res.json({
        access_token: response.data.access_token,
        item_id: response.data.item_id
      });
      return;
    }

    if (req.path === '/accounts') {
      const response = await axios.post(`${plaidUrl}/accounts/get`, {
        client_id: clientId,
        secret: secret,
        access_token: req.body.accessToken
      });
      res.json({ accounts: response.data.accounts });
      return;
    }

    if (req.path === '/sync-transactions') {
      const response = await axios.post(`${plaidUrl}/transactions/sync`, {
        client_id: clientId,
        secret: secret,
        access_token: req.body.accessToken,
        cursor: req.body.cursor || null,
        count: req.body.count || 100
      });
      res.json(response.data);
      return;
    }

    res.status(404).send('Not Found');
  } catch (error) {
    console.error('Plaid API Error:', error.response ? error.response.data : error.message);
    const statusCode = error.response ? error.response.status : 500;
    const errorData = error.response ? error.response.data : { message: error.message };
    res.status(statusCode).json(errorData);
  }
});
