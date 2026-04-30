# saleads_mi_negocio_full_test

Automated Playwright test for validating the SaleADS.ai Mi Negocio workflow after Google login.

## What it validates

1. Login with Google and dashboard/sidebar availability.
2. Mi Negocio menu expansion and submenu visibility.
3. "Agregar Negocio" modal fields and actions.
4. "Administrar Negocios" page sections.
5. Informacion General details.
6. Detalles de la Cuenta details.
7. Tus Negocios details.
8. Terminos y Condiciones page (same tab or new tab).
9. Politica de Privacidad page (same tab or new tab).
10. Final PASS/FAIL JSON report.

## Environment-agnostic behavior

- The test does not hardcode any SaleADS domain.
- It assumes the browser is already on the SaleADS login page, or you can pass `SALEADS_LOGIN_URL`.
- It supports `dev`, `staging`, and `production` environments.

## Setup

```bash
cd /workspace/automation/saleads-mi-negocio
npm install
npx playwright install chromium
```

## Run

Headless with explicit login URL:

```bash
SALEADS_LOGIN_URL="https://your-saleads-environment/login" \
GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com" \
npx playwright test tests/saleads_mi_negocio_full_test.spec.js
```

Headed:

```bash
HEADED=true \
SALEADS_LOGIN_URL="https://your-saleads-environment/login" \
GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com" \
npx playwright test tests/saleads_mi_negocio_full_test.spec.js
```

## Output artifacts

- Checkpoint screenshots are written under `screenshots/`.
- A final report JSON is generated at:

`screenshots/saleads_mi_negocio_final_report.json`

## Notes

- If your CI/login setup already opens the SaleADS login page before test start, leave `SALEADS_LOGIN_URL` unset.
- If Google account chooser does not appear, the test continues and validates dashboard visibility.
