# SaleADS Mi Negocio E2E

This folder contains an automated Playwright workflow runner for validating the full **Mi Negocio** flow in SaleADS.ai, including:

- Login with Google
- Sidebar > Negocio > Mi Negocio expansion
- Agregar Negocio modal validation
- Administrar Negocios page sections
- Información General, Detalles de la Cuenta, Tus Negocios checks
- Términos y Condiciones and Política de Privacidad validation
- Checkpoint screenshots and final report generation

## Prerequisites

- Node.js 20+
- Access to a valid SaleADS login URL for the target environment (dev/staging/prod)
- Browser-accessible Google account session/credentials for:
  - `juanlucasbarbiergarzon@gmail.com` (default, configurable)

## Install

```bash
cd /workspace/e2e
npm install
npm run saleads:install-browser
```

## Run

```bash
cd /workspace/e2e
SALEADS_URL="https://<current-env-login-url>" npm run saleads:mi-negocio
```

Optional environment variables:

- `SALEADS_LOGIN_URL`: alias of `SALEADS_URL`
- `SALEADS_GOOGLE_ACCOUNT`: defaults to `juanlucasbarbiergarzon@gmail.com`
- `HEADLESS`: defaults to `true`; set `false` to run headed
- `BASE_URL` / `APP_URL`: additional URL fallbacks
- `SALEADS_CDP_URL` / `PLAYWRIGHT_CDP_URL`: connect to an already-open browser session/tab (useful when the login page is already open)

## Output

Each run writes artifacts under:

```text
/workspace/e2e/artifacts/saleads-mi-negocio-<timestamp>/
```

Artifacts include:

- checkpoint screenshots (`*.png`)
- machine-readable report (`report.json`)
- human-readable summary (`report.md`)

