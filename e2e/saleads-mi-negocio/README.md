# SaleADS Mi Negocio E2E

Playwright test that validates the complete **Mi Negocio** workflow:

1. Login with Google
2. Open **Mi Negocio** menu
3. Validate **Agregar Negocio** modal
4. Open **Administrar Negocios**
5. Validate **Información General**
6. Validate **Detalles de la Cuenta**
7. Validate **Tus Negocios**
8. Validate **Términos y Condiciones** (including new-tab handling)
9. Validate **Política de Privacidad** (including new-tab handling)
10. Emit final PASS/FAIL report per section

## Why this is environment-agnostic

- The test does not hardcode a domain.
- It can start from the current open login page if the runner already navigated there.
- Or it can navigate using env vars:
  - `SALEADS_LOGIN_URL` (preferred)
  - fallback: `SALEADS_BASE_URL` or `BASE_URL`
- Selectors prioritize visible text and role-based locators.

## Setup

```bash
cd e2e/saleads-mi-negocio
npm install
npx playwright install --with-deps chromium
```

## Run

If your runner already opens the login page, run:

```bash
npm run test:mi-negocio
```

If navigation URL is needed:

```bash
SALEADS_LOGIN_URL="https://<current-saleads-env>/login" npm run test:mi-negocio
```

Optional strict user-name assertion:

```bash
SALEADS_EXPECTED_USER_NAME="Nombre Apellido" npm run test:mi-negocio
```

Optional Google account override:

```bash
GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com" npm run test:mi-negocio
```

## Evidence output

- Checkpoint screenshots are attached through Playwright test attachments.
- A JSON attachment named `final-report.json` is generated with PASS/FAIL results.
- Legal page final URLs are logged as `[EVIDENCE] ... URL: ...`.
