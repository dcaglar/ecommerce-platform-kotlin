import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { b64decode } from 'k6/encoding';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.4/index.js';

// ---------- endpoints: prefer env, fallback to endpoints.json ----------
let BASE_URL = __ENV.BASE || '';
let HOST_HEADER = __ENV.HOST || '';

if (!BASE_URL || !HOST_HEADER) {
  const endpoints = JSON.parse(open('../infra/endpoints.json'));
  BASE_URL = BASE_URL || endpoints.base_url;          // e.g. http://127.0.0.1
  HOST_HEADER = HOST_HEADER || endpoints.host_header; // e.g. payment.<minikube-ip>.nip.io
}
BASE_URL = BASE_URL.replace(/\/+$/, '');
if (!HOST_HEADER) throw new Error('Missing HOST (env HOST or endpoints.json host_header)');

// ---------- load knobs ----------
const MODE      = (__ENV.MODE || 'constant').toLowerCase(); // 'constant' | 'step'
const VUS       = __ENV.VUS ? parseInt(__ENV.VUS) : undefined; // optional legacy
const DURATION  = __ENV.DURATION || '20m';
const RPS       = __ENV.RPS ? parseInt(__ENV.RPS) : 20;
const CLIENT_TIMEOUT = __ENV.CLIENT_TIMEOUT || '3s';

// Step mode stages: "rate:duration,rate:duration,..."
const STAGES_STR = __ENV.STAGES || '20:1m,60:1m,80:1m,100:15m';

// Expected p95 (ms) used to size VU pool if PRE_VUS not provided
const LAT_MS    = __ENV.LAT_MS ? parseInt(__ENV.LAT_MS) : 60;

// Allow overrides, else auto-calc
const PRE_VUS   = __ENV.PRE_VUS ? parseInt(__ENV.PRE_VUS)
  : Math.ceil(RPS * (LAT_MS / 1000) * 1.5) || 20;   // autosize if constant mode
const MAX_VUS   = __ENV.MAX_VUS ? parseInt(__ENV.MAX_VUS)
  : Math.max(3 * PRE_VUS, 100);

// Optional thresholds (env overrides)
const ERROR_RATE = __ENV.ERROR_RATE || '0';    // e.g. '0.01' for 1%
const P95_MS     = __ENV.P95_MS || '500';

// ---------- scenario builder ----------
function buildOptions() {
  const base = {
    noConnectionReuse: false,           // keep-alives ON for realistic load
    discardResponseBodies: true,        // less memory GC
    thresholds: {
      http_req_failed:   [`rate<=${ERROR_RATE}`],
      http_req_duration: [`p(95)<=${P95_MS}`],
    },
  };

  if (MODE === 'step') {
    const stages = STAGES_STR.split(',')
      .map(s => s.trim())
      .filter(Boolean)
      .map(pair => {
        const [rateStr, dur] = pair.split(':');
        const rate = parseInt(rateStr);
        if (!rate || !dur) throw new Error(`Bad STAGES entry: ${pair}`);
        return { target: rate, duration: dur };
      });

    // choose a VU pool big enough for the largest step
    const peakRate = stages.reduce((m, s) => Math.max(m, s.target), 0);
    const preVUs   = __ENV.PRE_VUS
      ? PRE_VUS
      : Math.ceil(peakRate * (LAT_MS / 1000) * 1.5) || 30;
    const maxVUs   = __ENV.MAX_VUS ? MAX_VUS : Math.max(3 * preVUs, 120);

    return {
      ...base,
      scenarios: {
        stepRate: {
          executor: 'ramping-arrival-rate',
          startRate: 0,
          timeUnit: '1s',
          preAllocatedVUs: preVUs,
          maxVUs: maxVUs,
          stages,
          gracefulStop: '15s',
        },
      },
    };
  }

  // default: constant mode
  return {
    ...base,
    scenarios: {
      constant_request_rate: {
        executor: 'constant-arrival-rate',
        rate: RPS,
        timeUnit: '1s',
        duration: DURATION,
        preAllocatedVUs: PRE_VUS ?? 20,
        maxVUs: MAX_VUS,
        gracefulStop: '15s',
      },
    },
  };
}

// ---------- k6 options ----------
export const options = buildOptions();

// ---------- auth ----------
const ACCESS_TOKEN = open('../keycloak/access.token').replace(/[\r\n]+$/, '');

// ---------- metrics ----------
const statusCount = new Counter('status_count');
const failKinds   = new Counter('fail_kind_count');
const reqs        = new Counter('reqs');

// ---------- helpers ----------
function jwtExpSeconds(token) {
  try {
    const payload = JSON.parse(b64decode(token.split('.')[1], 'rawstd', 'utf-8'));
    return payload.exp;
  } catch (_) { return 0; }
}
function randomId(prefix) { return `${prefix}-${Math.floor(Math.random() * 1e12)}`; }
function randomAmount(min, max) { return parseFloat((Math.random() * (max - min) + min).toFixed(2)); }
function buildPaymentPayload() {
  const paymentOrders = Array.from({ length: 2 }, () => ({
    sellerId: randomId('SELLER'),
    amount: { value: randomAmount(10, 200), currency: 'EUR' },
  }));
  const total = paymentOrders.reduce((s, po) => s + po.amount.value, 0);
  return JSON.stringify({
    orderId: randomId('ORDER'),
    buyerId: randomId('BUYER'),
    totalAmount: { value: parseFloat(total.toFixed(2)), currency: 'EUR' },
    paymentOrders,
  });
}
function truncate(s, n) { return (s && s.length > n) ? s.substring(0, n) + '…' : s; }

export function setup() {
  if (!ACCESS_TOKEN) throw new Error('Missing access token (run get-token.sh)');
  if (!BASE_URL) throw new Error('Missing BASE_URL (env BASE or infra/endpoints.json)');
  const exp = jwtExpSeconds(ACCESS_TOKEN);
  const now = Math.floor(Date.now() / 1000);
  const ttl = exp ? (exp - now) : -1;
  console.log(`🔐 token TTL: ${ttl}s (exp=${exp || 'n/a'})  BASE_URL=${BASE_URL}  HOST=${HOST_HEADER}`);
  console.log(`🧪 mode=${MODE}  rps=${RPS}  duration=${DURATION}  preVUs=${options.scenarios[Object.keys(options.scenarios)[0]].preAllocatedVUs || 'n/a'}  maxVUs=${options.scenarios[Object.keys(options.scenarios)[0]].maxVUs || 'n/a'}`);
  return { authToken: ACCESS_TOKEN, tokenExp: exp };
}

export default function (data) {
  reqs.add(1);

  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${data.authToken}`,
    'Host': HOST_HEADER, // ingress routing key
  };
  const params = { headers, timeout: CLIENT_TIMEOUT };
  const payload = buildPaymentPayload();

  let res;
  try {
    res = http.post(`${BASE_URL}/payments`, payload, params);
  } catch (e) {
    // Network/timeout/DNS/etc. -> count by kind
    failKinds.add(1, { kind: e.name || 'network_error' });
    console.error(`❌ client-fail kind=${e.name || 'network_error'} msg="${e.message}" timeout=${CLIENT_TIMEOUT}`);
    return;
  }

  statusCount.add(1, { status: String(res.status) });
  const ok = check(res, { 'status is 200': (r) => r.status === 200 });
  if (!ok) {
    failKinds.add(1, { kind: `http_${res.status}` });
    console.error(`HTTP ${res.status} dur=${res.timings?.duration}ms body=${truncate(res.body, 200)}`);
  }
}

export function handleSummary(data) {
  const byStatus = aggregateTags(data.metrics['status_count'], 'status');
  const byKind   = aggregateTags(data.metrics['fail_kind_count'], 'kind');

  let extra = '\nStatus histogram:\n';
  for (const k of Object.keys(byStatus).sort()) extra += `  ${k}: ${byStatus[k]}\n`;
  extra += 'Failure kinds:\n';
  for (const k of Object.keys(byKind).sort())   extra += `  ${k}\n`;

  return { stdout: textSummary(data, { indent: ' ', enableColors: true }) + extra };
}

// Robust against k6's summary shape (values is an OBJECT, not an array)
function aggregateTags(metric, tagKey) {
  const out = {};
  if (!metric || !metric.values) return out;
  for (const v of Object.values(metric.values)) {
    if (v.tags && v.tags[tagKey] && typeof v.count === 'number') {
      const key = v.tags[tagKey];
      out[key] = (out[key] || 0) + v.count;
    }
  }
  return out;
}