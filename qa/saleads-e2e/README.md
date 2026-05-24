# SaleADS Mi Negocio E2E

Playwright suite for validating the full **Mi Negocio** workflow, including:

- Google login continuation
- Sidebar navigation and Mi Negocio submenu
- Agregar Negocio modal assertions
- Administrar Negocios page validations
- Legal links validation (same tab or new tab)
- Checkpoint screenshots and final PASS/FAIL report artifact

## 1) Install

```bash
cd qa/saleads-e2e
npm install
npm run install:browsers
```

## 2) Configure target environment

Create `.env` from `.env.example` and set the login page URL of your current environment:

```bash
cp .env.example .env
```

Supported variables:

- `SALEADS_LOGIN_URL` (recommended)
- `SALEADS_BASE_URL`
- `BASE_URL`

No domain is hardcoded in the test. The environment is selected at runtime via env vars.

## 3) Run

```bash
npm test
```

Optional headed execution:

```bash
HEADED=true npm run test:headed
```

## Artifacts

Playwright stores:

- checkpoint screenshots
- trace/video on failure
- `saleads-mi-negocio-final-report.json` with PASS/FAIL per required step and legal URLs
