import http from 'node:http';

const port = Number(process.env.MOCK_SYSTEMS_PORT ?? 8081);
const netSuiteCustomers = new Map();
const zendeskOrganizations = new Map();
const zendeskAttempts = new Map();
let netSuiteCalls = 0;
let zendeskCalls = 0;

function send(response, status, body) {
  response.writeHead(status, { 'Content-Type': 'application/json' });
  response.end(JSON.stringify(body));
}

async function readJson(request) {
  const chunks = [];
  for await (const chunk of request) chunks.push(chunk);
  const raw = Buffer.concat(chunks).toString('utf8');
  return raw ? JSON.parse(raw) : {};
}

function requiredHeaders(request, response) {
  const idempotencyKey = request.headers['idempotency-key'];
  const correlationId = request.headers['x-correlation-id'];
  if (!idempotencyKey || !correlationId) {
    send(response, 400, {
      code: 'MISSING_HEADERS',
      message: 'Idempotency-Key and X-Correlation-Id are required',
    });
    return null;
  }
  return { idempotencyKey, correlationId };
}

const server = http.createServer(async (request, response) => {
  try {
    if (request.method === 'GET' && request.url === '/health') {
      send(response, 200, { status: 'UP' });
      return;
    }

    if (request.method === 'POST' && request.url === '/reset') {
      netSuiteCustomers.clear();
      zendeskOrganizations.clear();
      zendeskAttempts.clear();
      netSuiteCalls = 0;
      zendeskCalls = 0;
      send(response, 200, { reset: true });
      return;
    }

    if (request.method === 'GET' && request.url === '/state') {
      send(response, 200, {
        netSuiteCalls,
        netSuiteCustomers: netSuiteCustomers.size,
        zendeskCalls,
        zendeskOrganizations: zendeskOrganizations.size,
        zendeskAttempts: Object.fromEntries(zendeskAttempts),
      });
      return;
    }

    if (request.method === 'POST' && request.url === '/netsuite/customers') {
      const headers = requiredHeaders(request, response);
      if (!headers) return;
      await readJson(request);
      netSuiteCalls += 1;

      const existing = netSuiteCustomers.get(headers.idempotencyKey);
      if (existing) {
        console.log(`system=netsuite correlationId=${headers.correlationId} replayed=true`);
        send(response, 200, { ...existing, replayed: true });
        return;
      }

      const customer = {
        customerId: `ns-${String(netSuiteCustomers.size + 1).padStart(4, '0')}`,
        correlationId: headers.correlationId,
      };
      netSuiteCustomers.set(headers.idempotencyKey, customer);
      console.log(`system=netsuite correlationId=${headers.correlationId} replayed=false`);
      send(response, 201, { ...customer, replayed: false });
      return;
    }

    if (request.method === 'POST' && request.url === '/zendesk/organizations') {
      const headers = requiredHeaders(request, response);
      if (!headers) return;
      const body = await readJson(request);
      zendeskCalls += 1;

      const existing = zendeskOrganizations.get(headers.idempotencyKey);
      if (existing) {
        console.log(`system=zendesk correlationId=${headers.correlationId} replayed=true`);
        send(response, 200, { ...existing, replayed: true });
        return;
      }

      const attempt = (zendeskAttempts.get(headers.idempotencyKey) ?? 0) + 1;
      zendeskAttempts.set(headers.idempotencyKey, attempt);
      if (body.simulateTransientFailure === true && attempt === 1) {
        console.log(`system=zendesk correlationId=${headers.correlationId} attempt=1 status=500`);
        send(response, 500, {
          code: 'SIMULATED_ZENDESK_FAILURE',
          message: 'Transient downstream failure for saga demonstration',
          correlationId: headers.correlationId,
        });
        return;
      }

      const organization = {
        organizationId: `zd-${String(zendeskOrganizations.size + 1).padStart(4, '0')}`,
        netSuiteCustomerId: body.netSuiteCustomerId,
        correlationId: headers.correlationId,
      };
      zendeskOrganizations.set(headers.idempotencyKey, organization);
      console.log(`system=zendesk correlationId=${headers.correlationId} attempt=${attempt} status=201`);
      send(response, 201, { ...organization, replayed: false });
      return;
    }

    send(response, 404, { code: 'NOT_FOUND' });
  } catch (error) {
    send(response, 400, { code: 'INVALID_REQUEST', message: error.message });
  }
});

server.listen(port, '127.0.0.1', () => {
  console.log(`mock_systems_ready url=http://127.0.0.1:${port}`);
});

