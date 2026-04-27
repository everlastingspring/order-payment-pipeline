/**
 * Load test — ramps to 50 VUs over ~8 minutes.
 * Each VU uses a unique customer ID (customer-<VU>) so wallets don't contend.
 * Tests the happy-path saga (CONFIRMED) and exercises saga compensation when
 * inventory runs out (stock=100 for Mouse, so orders fail after ~100 confirmations).
 *
 * Usage:
 *   k6 run -e BASE_URL=http://localhost:8080 \
 *           -e KEYCLOAK_URL=http://localhost:8180 \
 *           scripts/load-tests/k6-load.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';

const BASE_URL      = __ENV.BASE_URL     || 'http://localhost:8080';
const KEYCLOAK_URL  = __ENV.KEYCLOAK_URL || 'http://localhost:8180';
const REALM         = 'payment-platform';
const CLIENT_ID     = 'payment-client';
const KC_USERNAME   = 'testuser';
const KC_PASSWORD   = 'password';

const MOUSE_ID    = 'a1b2c3d4-0000-0000-0000-000000000001';
const MOUSE_PRICE = 799.00;
const TOPUP_AMOUNT = 100000; // 100,000 INR — enough for ~125 mouse orders per VU

export const options = {
  stages: [
    { duration: '1m',  target: 10 },  // ramp up
    { duration: '2m',  target: 10 },  // steady at 10 VUs
    { duration: '1m',  target: 30 },  // ramp to 30
    { duration: '2m',  target: 30 },  // steady at 30 VUs
    { duration: '1m',  target: 50 },  // ramp to 50
    { duration: '2m',  target: 50 },  // steady at 50 VUs — peak
    { duration: '1m',  target: 0  },  // ramp down
  ],
  thresholds: {
    http_req_failed:   ['rate<0.05'],    // <5% HTTP errors
    http_req_duration: ['p(95)<3000'],   // 95% of requests under 3s
    'order_confirmed_time_ms': ['p(90)<30000'],  // 90% of orders confirm within 30s
    'order_success_rate':      ['rate>0.70'],    // >70% of orders reach CONFIRMED
  },
};

const orderConfirmedTime = new Trend('order_confirmed_time_ms', true);
const ordersConfirmed    = new Counter('orders_confirmed');
const ordersFailed       = new Counter('orders_failed');
const orderSuccessRate   = new Rate('order_success_rate');

// Token cache per VU — refresh once per VU lifetime
const tokenCache = {};

function getToken(customerId) {
  if (tokenCache[customerId]) return tokenCache[customerId];
  const res = http.post(
    `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`,
    { grant_type: 'password', client_id: CLIENT_ID, username: KC_USERNAME, password: KC_PASSWORD },
    { tags: { name: 'keycloak_token' } }
  );
  check(res, { 'token: 200': (r) => r.status === 200 });
  tokenCache[customerId] = res.json('access_token');
  return tokenCache[customerId];
}

function topUpWallet(token, customerId) {
  const res = http.post(
    `${BASE_URL}/api/wallets/customer/${customerId}/topup`,
    JSON.stringify({ amount: TOPUP_AMOUNT, currency: 'INR' }),
    {
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      tags: { name: 'wallet_topup' },
    }
  );
  check(res, { 'topup: 2xx': (r) => r.status >= 200 && r.status < 300 });
}

function createOrder(token, customerId) {
  const res = http.post(
    `${BASE_URL}/api/orders`,
    JSON.stringify({
      customerId: customerId,
      customerEmail: `${customerId}@load.test`,
      paymentMethod: 'WALLET',
      shippingAddress: '123 Load Test Street, Bengaluru, KA 560001',
      items: [
        {
          productId: MOUSE_ID,
          productName: 'Wireless Mouse',
          sku: 'MOUSE-WL-001',
          quantity: 1,
          unitPrice: MOUSE_PRICE,
          currency: 'INR',
        },
      ],
    }),
    {
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      tags: { name: 'create_order' },
    }
  );
  check(res, { 'create_order: 201': (r) => r.status === 201 });
  return res.json('data.id');
}

function pollOrderStatus(token, orderId) {
  for (let i = 0; i < 10; i++) {
    sleep(3);
    const res = http.get(
      `${BASE_URL}/api/orders/${orderId}`,
      {
        headers: { Authorization: `Bearer ${token}` },
        tags: { name: 'poll_order' },
      }
    );
    const status = res.json('data.status');
    if (status === 'CONFIRMED' || status === 'FAILED' || status === 'CANCELLED') return status;
  }
  return 'TIMEOUT';
}

export default function () {
  const customerId = `load-customer-${__VU}`;
  const token = getToken(customerId);

  // Top up wallet on first iteration only
  if (__ITER === 0) {
    topUpWallet(token, customerId);
    sleep(1);
  }

  const startMs = Date.now();
  const orderId = createOrder(token, customerId);
  if (!orderId) {
    ordersFailed.add(1);
    orderSuccessRate.add(false);
    return;
  }

  const finalStatus = pollOrderStatus(token, orderId);
  const elapsed = Date.now() - startMs;

  if (finalStatus === 'CONFIRMED') {
    ordersConfirmed.add(1);
    orderConfirmedTime.add(elapsed);
    orderSuccessRate.add(true);
  } else {
    ordersFailed.add(1);
    orderSuccessRate.add(false);
  }

  // Small pause between iterations to avoid hammering the outbox poller
  sleep(2);
}
