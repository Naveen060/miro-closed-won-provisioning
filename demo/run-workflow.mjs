import { randomUUID } from 'node:crypto';

const argumentsMap = new Map(
  process.argv.slice(2).map((argument) => {
    const [key, value = 'true'] = argument.replace(/^--/, '').split('=', 2);
    return [key, value];
  }),
);

const scenario = argumentsMap.get('scenario') ?? 'success';
const validationUrl = process.env.VALIDATION_URL ?? 'http://127.0.0.1:8080';
const mockSystemsUrl = process.env.MOCK_SYSTEMS_URL ?? 'http://127.0.0.1:8081';
const apiKey = process.env.VALIDATION_API_KEY ?? 'local-demo-key';
const opportunityId = argumentsMap.get('opportunity-id') ?? `opp-${Date.now()}`;
const correlationId = argumentsMap.get('correlation-id') ?? randomUUID();

if (!['success', 'transient-failure'].includes(scenario)) {
  throw new Error('scenario must be success or transient-failure');
}

const event = {
  opportunityId,
  accountId: 'acct-acme',
  accountName: 'Acme Corp',
  totalAmount: 125000,
  currency: 'USD',
  countryCode: 'US',
};

async function requestJson(url, options = {}) {
  const response = await fetch(url, options);
  const body = await response.json();
  if (!response.ok) {
    const error = new Error(`${response.status} ${body.code ?? 'HTTP_ERROR'}: ${body.message ?? ''}`);
    error.status = response.status;
    error.body = body;
    throw error;
  }
  return { status: response.status, body, headers: response.headers };
}

function headers(step) {
  return {
    'Content-Type': 'application/json',
    'X-Correlation-Id': correlationId,
    'Idempotency-Key': `${opportunityId}:${step}`,
  };
}

async function retryZendesk(operation, maximumAttempts = 3) {
  for (let attempt = 1; attempt <= maximumAttempts; attempt += 1) {
    try {
      return await operation(attempt);
    } catch (error) {
      const retryable = error.status >= 500 && attempt < maximumAttempts;
      console.log(`step=zendesk attempt=${attempt} status=${error.status} retryable=${retryable}`);
      if (!retryable) throw error;
      await new Promise((resolve) => setTimeout(resolve, 150 * attempt));
    }
  }
  throw new Error('unreachable');
}

console.log(`workflow_started scenario=${scenario} correlationId=${correlationId}`);

const validation = await requestJson(`${validationUrl}/api/v1/orders/validate`, {
  method: 'POST',
  headers: { ...headers('validation'), 'X-API-Key': apiKey },
  body: JSON.stringify(event),
});
console.log(`step=validation status=${validation.status} replayed=${validation.headers.get('idempotency-replayed')}`);

const netSuite = await requestJson(`${mockSystemsUrl}/netsuite/customers`, {
  method: 'POST',
  headers: headers('netsuite'),
  body: JSON.stringify({
    accountId: event.accountId,
    validationStatus: validation.body.validationStatus,
  }),
});
console.log(`step=netsuite status=${netSuite.status} customerId=${netSuite.body.customerId}`);

// The retry boundary intentionally contains only Zendesk. NetSuite is never re-entered.
const zendesk = await retryZendesk(async (attempt) => {
  const result = await requestJson(`${mockSystemsUrl}/zendesk/organizations`, {
    method: 'POST',
    headers: headers('zendesk'),
    body: JSON.stringify({
      accountId: event.accountId,
      netSuiteCustomerId: netSuite.body.customerId,
      simulateTransientFailure: scenario === 'transient-failure',
    }),
  });
  console.log(`step=zendesk attempt=${attempt} status=${result.status} organizationId=${result.body.organizationId}`);
  return result;
});

const downstreamState = await requestJson(`${mockSystemsUrl}/state`);
console.log(JSON.stringify({
  status: 'PROVISIONED',
  correlationId,
  opportunityId,
  netSuiteCustomerId: netSuite.body.customerId,
  zendeskOrganizationId: zendesk.body.organizationId,
  proof: downstreamState.body,
}, null, 2));

