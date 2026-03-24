# SaleADS Mi Negocio Full Workflow Test

This folder contains a standalone Playwright E2E test:

- `tests/saleads_mi_negocio_full_test.spec.ts`

The test is intentionally environment-agnostic:

- It does **not** hardcode a SaleADS domain.
- It assumes the browser starts on the current environment login page.
- It prefers visible-text selectors and resilient fallbacks.
- It validates both same-tab and new-tab legal-link behavior.
- It captures screenshots at key checkpoints.

## Setup

```bash
cd /workspace/e2e
npm install
npx playwright install --with-deps chromium
```

## Run

Headed:

```bash
npm run test:headed
```

Headless:

```bash
npm test
```

## Credentials / Session

For Google login, use Playwright project `chromium-auth` and provide a storage state file:

```bash
PLAYWRIGHT_AUTH_FILE=/absolute/path/to/storageState.json npm test
```

If no storage state is provided, the test attempts interactive login by clicking a button containing:

- `Sign in with Google`
- `Google`
- `Iniciar con Google`

When Google account selection appears, it selects:

- `juanlucasbarbiergarzon@gmail.com`

## Artifacts

- Screenshots and report are written under: `artifacts/saleads_mi_negocio_full_test/`
