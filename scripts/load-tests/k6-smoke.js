/**
 * Smoke test — 1 VU, 1 iteration.
 * Validates the full happy-path saga end-to-end before running load or stress tests.
 *
 * Usage:
 *   k6 run -e BASE_URL=http://localhost:8080 \
 *           -e KEYCLOAK_URL=http://localhost:8180 \
 *           scripts/load-tests/k6-smoke.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const BASE_URL       = __ENV.BASE_URL      || 'http://localhost:8080';
const KEYCLOAK_URL   = __ENV.KEYCLOAK_URL  || 'http://localhost:8180';
const REALM          = 'payment-platform';
const CLIENT_ID      = 'payment-client';
const KC_USERNAME    = 'testuser';
const KC_PASSWORD    = 'password';

// Fixed product IDs from Flyway seed data
const MOUSE_ID    = 'a1b2c3d4-0000-0000-0000-000000000001';
const MOUSE_PRICE = 799.00;

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    http_req_failed: ['rate==0'],   // zero failures expected in smoke
    http_req_duration: ['p(95)<5000'],
  },
};

const orderConfirmedTime = new Trend('order_confirmed_time_ms');
const ordersConfirmed    = new Counter('orders_confirmed');
const ordersFailed       = new Counter('orders_failed');

function getToken() {
  const res = http.post(
    `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`,
    {
      grant_type: 'password',
      client_id: CLIENT_ID,
      username: KC_USERNAME,
      password: KC_PASSWORD,
    },
    { tags: { name: 'keycloak_token' } }
  );
  check(res, { 'token: 200': (r) => r.status === 200 });
  return res.json('access_token');
}

function topUpWallet(token, customerId, amount) {
  const res = http.post(
    `${BASE_URL}/api/wallets/customer/${customerId}/topup`,
    JSON.stringify({ amount: amount, currency: 'INR' }),
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
      customerEmail: `${customerId}@smoke.test`,
      paymentMethod: 'WALLET',
      shippingAddress: '123 Smoke Test Street, Bengaluru, KA 560001',
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

function pollOrderStatus(token, orderId, maxAttempts = 12, intervalSec = 5) {
  for (let i = 0; i < maxAttempts; i++) {
    const res = http.get(
      `${BASE_URL}/api/orders/${orderId}`,
      {
        headers: { Authorization: `Bearer ${token}` },
        tags: { name: 'poll_order' },
      }
    );
    const status = res.json('data.status');
    if (status === 'CONFIRMED') return 'CONFIRMED';
    if (status === 'FAILED' || status === 'CANCELLED') return status;
    sleep(intervalSec);
  }
  return 'TIMEOUT';
}

export default function () {
  const customerId = 'smoke-customer-001';
  const startMs = Date.now();

  const token = getToken();

  // Top up wallet with enough for 10 orders
  topUpWallet(token, customerId, MOUSE_PRICE * 10);
  sleep(1);

  const orderId = createOrder(token, customerId);
  console.log(`Smoke: created order ${orderId}`);

  const finalStatus = pollOrderStatus(token, orderId);
  const elapsed = Date.now() - startMs;

  console.log(`Smoke: order ${orderId} → ${finalStatus} in ${elapsed}ms`);
  check(null, { 'order reached CONFIRMED': () => finalStatus === 'CONFIRMED' });

  if (finalStatus === 'CONFIRMED') {
    ordersConfirmed.add(1);
    orderConfirmedTime.add(elapsed);
  } else {
    ordersFailed.add(1);
  }
}
