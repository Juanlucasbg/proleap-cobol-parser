# SaleADS Mi Negocio E2E

This Playwright suite validates the full **Mi Negocio** workflow, including:

- Login with Google.
- Sidebar navigation checks.
- "Agregar Negocio" modal checks.
- "Administrar Negocios" account view checks.
- Legal links ("Términos y Condiciones", "Política de Privacidad") with new-tab/same-tab handling.
- Checkpoint screenshots and a final PASS/FAIL JSON report.

## Requirements

- Node.js 18+ (Node 22 is recommended).
- A valid SaleADS URL for the current environment (dev/staging/prod).
- Access to Google login account: `juanlucasbarbiergarzon@gmail.com`.

## Install

```bash
cd e2e/saleads-mi-negocio
npm install
npx playwright install
```

## Run

```bash
SALEADS_URL="https://<current-environment-login-url>" npm run test:e2e
```

Optional alternatives for URL input are supported too:

- `TARGET_URL`
- `BASE_URL`

The test will use the first available variable in this order:
`SALEADS_URL`, `TARGET_URL`, `BASE_URL`.
