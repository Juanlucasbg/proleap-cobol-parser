# SaleADS Mi Negocio E2E

Playwright test that validates the complete `Mi Negocio` workflow:

1. Login with Google.
2. Open `Mi Negocio` submenu.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate account sections and legal links.
6. Capture screenshots at key checkpoints.
7. Produce a final PASS/FAIL report by section.

## Requirements

- Node.js 18+
- Playwright browsers installed

## Setup

```bash
cd e2e
npm install
npx playwright install
```

## Run

Use any SaleADS environment URL via environment variable (no hardcoded domain):

```bash
cd e2e
SALEADS_URL="https://<your-saleads-env>/login" npm run test:saleads-mi-negocio
```

You can also use:

- `SALEADS_START_URL` (alternative env var name)

## Artifacts

- Checkpoint screenshots are saved in Playwright test output.
- A JSON attachment (`saleads-mi-negocio-report`) includes:
  - PASS/FAIL per required section.
  - Final URL for `Términos y Condiciones`.
  - Final URL for `Política de Privacidad`.
