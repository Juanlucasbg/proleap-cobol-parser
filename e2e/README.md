# SaleADS Mi Negocio - E2E workflow

This Playwright suite validates the full **Mi Negocio** workflow, including:

1. Login with Google
2. Sidebar navigation to Mi Negocio
3. "Agregar Negocio" modal validation
4. "Administrar Negocios" page validation
5. Legal links validation (Términos y Condiciones / Política de Privacidad)
6. PASS/FAIL final report by requested section

## Requirements

- Node.js 20+ (tested with Node 22)
- Access to a SaleADS.ai environment

## Install

```bash
cd e2e
npm install
npx playwright install --with-deps chromium
```

## Run

The suite is environment-agnostic and does not hardcode any domain.
Provide the target login URL through environment variables:

```bash
cd e2e
SALEADS_LOGIN_URL="https://<your-env>/login" npm test
```

Alternative variable accepted:

```bash
SALEADS_BASE_URL="https://<your-env>/login" npm test
```

If your runner already opens the login page before the test starts, skip explicit navigation:

```bash
SALEADS_SKIP_NAVIGATION=true npm test
```

For headed debugging:

```bash
HEADED=true SALEADS_LOGIN_URL="https://<your-env>/login" npm run test:headed
```

## Evidence and report

- Checkpoint screenshots are attached in Playwright output.
- A `final-report.json` attachment is generated per test execution.
- Final URLs for legal pages are attached as text artifacts.
