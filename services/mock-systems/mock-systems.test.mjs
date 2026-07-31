import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import test from 'node:test';

const port = 18081;
const baseUrl = `http://127.0.0.1:${port}`;

async function waitUntilReady() {
  for (let attempt = 0; attempt < 40; attempt += 1) {
    try {
      const response = await fetch(`${baseUrl}/health`);
      if (response.ok) return;
    } catch {
      // The child process may still be starting.
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error('Mock service did not become ready');
}

test('accepts a PII-safe provisioning alert and exposes its count', async () => {
  const child = spawn(process.execPath, ['mock-systems.mjs'], {
    cwd: new URL('.', import.meta.url),
    env: { ...process.env, MOCK_SYSTEMS_PORT: String(port) },
    stdio: 'ignore',
  });

  try {
    await waitUntilReady();
    const response = await fetch(`${baseUrl}/alerts/provisioning`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        eventId: 'evt-test-001',
        correlationId: 'corr-test-001',
        opportunityId: 'opp-test-001',
        state: 'NEEDS_ATTENTION',
        failedStep: 'ZENDESK_CREATE',
        errorCategory: 'DOWNSTREAM_5XX',
        retryCount: 3,
      }),
    });

    assert.equal(response.status, 202);
    assert.deepEqual(await response.json(), {
      accepted: true,
      alertId: 'alert-0001',
      correlationId: 'corr-test-001',
      state: 'NEEDS_ATTENTION',
    });

    const invalidResponse = await fetch(`${baseUrl}/alerts/provisioning`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{}',
    });
    assert.equal(invalidResponse.status, 400);

    const stateResponse = await fetch(`${baseUrl}/state`);
    const state = await stateResponse.json();
    assert.equal(state.alertCalls, 1);
    assert.equal(state.provisioningAlerts, 1);
  } finally {
    child.kill();
  }
});
