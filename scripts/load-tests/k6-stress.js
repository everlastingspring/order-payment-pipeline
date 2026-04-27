/**
 * Stress test — pushes beyond normal capacity to find the breaking point.
 * Uses a mix of workloads: order creation, wallet balance checks, and inventory reads.
 * This avoids exhausting inventory too fast while still driving high concurrency.
 *
 * Ramps to 100 VUs; watch for circuit breaker trips and Resilience4j fallbacks.
 *
 * Usage:
 *   k6 run -e BASE_URL=http://localhost:8080 \
 *           -e KEYCLOAK_URL=http://localhost:8180 \
 *           scripts/load-tests/k6-stress.js
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
const KEYBOARD_ID = 'a1b2c3d4-0000-0000-0000-000000000002';
const MOUSE_PRICE    = 799.00;
const KEYBOARD_PRICE = 2499.00;
const TOPUP_AMOUNT   = 500000;

export const options = {
  stages: [
    { duration: '2m',  target: 50  },  // ramp up
    { duration: '3m',  target: 50  },  // sustained load
    { duration: '2m',  target: 100 },  // stress — double the load
    { duration: '3m',  target: 100 },  // hold at peak
    { duration: '2m',  target: 0   },  // recovery
  ],
  thresholds: {
    http_req_failed:   ['rate<0.10'],   // allow up to 10% errors under stress
    http_req_duration: ['p(99)<10000'], // 99% of requests under 10s
  },
};

const orderConfirmedTime  = new Trend('order_confirmed_time_ms', true);
const ordersConfirmed     = new Counter('orders_confirmed');
const ordersFailed        = new Counter('orders_failed');
const inventoryCheckTime  = new Trend('inventory_check_time_ms', true);
const walletCheckTime     = new Trend('wallet_check_time_ms', true);
const orderSuccessRate    = new Rate('order_success_rate');

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

function checkInventory(token, productId) {
  const start = Date.now();
  const res = http.get(
    `${BASE_URL}/api/inventory/${productId}`,
    {
      headers: { Authorization: `Bearer ${token}` },
      tags: { name: 'inventory_check' },
    }
  );
  check(res, { 'inventory: 200': (r) => r.status === 200 });
  inventoryCheckTime.add(Date.now() - start);
}

function checkWallet(token, customerId) {
  const start = Date.now();
  const res = http.get(
    `${BASE_URL}/api/wallets/customer/${customerId}`,
    {
      headers: { Authorization: `Bearer ${token}` },
      tags: { name: 'wallet_check' },
    }
  );
  check(res, { 'wallet: 200': (r) => r.status === 200 });
  walletCheckTime.add(Date.now() - start);
}

function createOrder(token, customerId) {
  // Alternate between Mouse and Keyboard to spread inventory pressure
  const useKeyboard = __VU % 3 === 0;
  const productId    = useKeyboard ? KEYBOARD_ID : MOUSE_ID;
  const productName  = useKeyboard ? 'Mechanical Keyboard' : 'Wireless Mouse';
  const sku          = useKeyboard ? 'KB-MECH-001' : 'MOUSE-WL-001';
  const unitPrice    = useKeyboard ? KEYBOARD_PRICE : MOUSE_PRICE;

  const res = http.post(
    `${BASE_URL}/api/orders`,
    JSON.stringify({
      customerId: customerId,
      customerEmail: `${customerId}@stress.test`,
      paymentMethod: 'WALLET',
      shippingAddress: '123 Stress Test Street, Bengaluru, KA 560001',
      items: [{ productId, productName, sku, quantity: 1, unitPrice, currency: 'INR' }],
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
  for (let i = 0; i < 8; i++) {
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
  const customerId = `stress-customer-${__VU}`;
  const token = getToken(customerId);

  if (__ITER === 0) {
    topUpWallet(token, customerId);
    sleep(1);
  }

  // Mix of workloads: every 3rd iteration is read-only (inventory + wallet checks)
  // to simulate realistic traffic patterns with a mix of reads and writes
  if (__ITER % 3 === 2) {
    checkInventory(token, MOUSE_ID);
    sleep(0.5);
    checkWallet(token, customerId);
    sleep(1);
    return;
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

  sleep(1);
}
