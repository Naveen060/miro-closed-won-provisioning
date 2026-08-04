import http from 'node:http';

// This dependency-free HTTP server emulates the external systems used by the
// provisioning workflow. Keeping the mocks in one process makes local demos
// deterministic while still exercising real HTTP, headers, status codes, and
// JSON serialization at each integration boundary.
const port = Number(process.env.PORT ?? process.env.MOCK_SYSTEMS_PORT ?? 8081);
const bindAddress = process.env.BIND_ADDRESS ?? '0.0.0.0';

// Maps model durable records created by each downstream system. Their keys are
// idempotency keys so repeated workflow attempts can return the original record
// instead of manufacturing duplicate customers or organizations.
const netSuiteCustomers = new Map();
const zendeskOrganizations = new Map();
const zendeskAttempts = new Map();
const provisioningAlerts = new Map();

// Call counters are intentionally separate from record counts: a replay or a
// failed attempt is still an outbound call and should remain visible in /state.
let netSuiteCalls = 0;
let zendeskCalls = 0;
let alertCalls = 0;

function send(response, status, body) {
  // All endpoints use the same small JSON response contract, including errors.
  response.writeHead(status, { 'Content-Type': 'application/json' });
  response.end(JSON.stringify(body));
}

async function readJson(request) {
  // Node request streams may deliver a body in multiple chunks. Buffer the
  // complete payload before parsing so split packets behave exactly like one.
  const chunks = [];
  for await (const chunk of request) chunks.push(chunk);
  const raw = Buffer.concat(chunks).toString('utf8');
  return raw ? JSON.parse(raw) : {};
}

function requiredHeaders(request, response) {
  // Provisioning writes require both deduplication and end-to-end traceability.
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
    // Liveness probe used by Docker and the integration test readiness loop.
    if (request.method === 'GET' && request.url === '/health') {
      send(response, 200, { status: 'UP' });
      return;
    }

    if (request.method === 'POST' && request.url === '/reset') {
      // Reset every piece of mutable state to make repeated demos independent.
      netSuiteCustomers.clear();
      zendeskOrganizations.clear();
      zendeskAttempts.clear();
      provisioningAlerts.clear();
      netSuiteCalls = 0;
      zendeskCalls = 0;
      alertCalls = 0;
      send(response, 200, { reset: true });
      return;
    }

    if (request.method === 'GET' && request.url === '/state') {
      // Expose operational counters only; no customer payloads or PII leave the mocks.
      send(response, 200, {
        netSuiteCalls,
        netSuiteCustomers: netSuiteCustomers.size,
        zendeskCalls,
        zendeskOrganizations: zendeskOrganizations.size,
        zendeskAttempts: Object.fromEntries(zendeskAttempts),
        alertCalls,
        provisioningAlerts: provisioningAlerts.size,
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
        // A replay returns the original identifier with 200 rather than creating
        // another customer. The explicit flag lets tests prove deduplication.
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
      // Fail only the first matching attempt so the workflow can demonstrate
      // retry recovery without requiring a nondeterministic external outage.
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

    if (request.method === 'POST' && request.url === '/alerts/provisioning') {
      const body = await readJson(request);
      const requiredFields = [
        'eventId',
        'correlationId',
        'opportunityId',
        'state',
        'failedStep',
        'errorCategory',
      ];
      const missingFields = requiredFields.filter(
        (field) => body[field] === undefined || body[field] === null || body[field] === '',
      );

      if (missingFields.length > 0) {
        send(response, 400, {
          code: 'MISSING_FIELDS',
          message: `Missing required fields: ${missingFields.join(', ')}`,
        });
        return;
      }

      alertCalls += 1;
      // Alerts use their own monotonically increasing ID because unlike the
      // provisioning writes they are accepted events, not idempotent resources.
      const alertId = `alert-${String(alertCalls).padStart(4, '0')}`;
      const alert = {
        alertId,
        eventId: body.eventId,
        correlationId: body.correlationId,
        opportunityId: body.opportunityId,
        state: body.state,
        failedStep: body.failedStep,
        errorCategory: body.errorCategory,
        retryCount: Number(body.retryCount ?? 0),
        acceptedAt: new Date().toISOString(),
      };
      provisioningAlerts.set(alertId, alert);

      // Log operational identifiers only. Do not include customer names, emails, or other PII.
      console.log(
        `system=provisioning-alert alertId=${alertId}`
          + ` correlationId=${body.correlationId}`
          + ` opportunityId=${body.opportunityId}`
          + ` state=${body.state}`
          + ` failedStep=${body.failedStep}`,
      );

      send(response, 202, {
        accepted: true,
        alertId,
        correlationId: body.correlationId,
        state: body.state,
      });
      return;
    }

    send(response, 404, { code: 'NOT_FOUND' });
  } catch (error) {
    // Malformed JSON and other request-level failures become a stable client
    // error instead of terminating the long-running mock server process.
    send(response, 400, { code: 'INVALID_REQUEST', message: error.message });
  }
});

server.listen(port, bindAddress, () => {
  console.log(`mock_systems_ready url=http://${bindAddress}:${port}`);
});
