# SaleADS Mi Negocio full workflow test

This Playwright script automates the workflow requested in `saleads_mi_negocio_full_test`:

1. Login with Google.
2. Open sidebar `Negocio` -> `Mi Negocio`.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate `Información General`.
6. Validate `Detalles de la Cuenta`.
7. Validate `Tus Negocios`.
8. Validate `Términos y Condiciones` (same tab or new tab).
9. Validate `Política de Privacidad` (same tab or new tab).
10. Emit final PASS/FAIL summary report.

The script is domain-agnostic: it does not hardcode SaleADS URLs.

## Setup

```bash
cd /workspace/e2e/saleads-mi-negocio
npm install
npx playwright install chromium
```

## Run

```bash
SALEADS_LOGIN_URL="https://<current-environment-login-url>" npm run run
```

If you pass the environment homepage instead of a direct login route, the script first tries to click a visible `Inicia sesión` / `Sign in` entry before continuing with `Sign in with Google`.

Optional environment variables:

- `GOOGLE_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADLESS` (`true` by default, set `false` for headed mode)
- `ACTION_TIMEOUT_MS` (default: `30000`)

## Artifacts

Each run creates:

- `artifacts/<run-id>/screenshots/*.png`
- `artifacts/<run-id>/report.json`

The report includes:

- Per-step checks and status (`PASS` / `FAIL`)
- Captured evidence (screenshots and legal-page URLs)
- Final summary for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
