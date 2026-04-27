# SaleADS Mi Negocio Full Workflow (Playwright)

This suite validates the end-to-end "Mi Negocio" workflow requested in the automation payload:

1. Login with Google.
2. Open "Negocio" -> "Mi Negocio".
3. Validate "Agregar Negocio" modal.
4. Open "Administrar Negocios".
5. Validate "Informacion General".
6. Validate "Detalles de la Cuenta".
7. Validate "Tus Negocios".
8. Validate "Terminos y Condiciones" (handles same-tab or new-tab navigation).
9. Validate "Politica de Privacidad" (handles same-tab or new-tab navigation).
10. Produce PASS/FAIL report.

## Why this is environment-agnostic

- It does **not** hardcode a specific SaleADS domain.
- It receives the login URL through `SALEADS_BASE_URL`.
- It uses visible-text selectors with tolerant accent-insensitive regex.

## Usage

```bash
cd e2e/saleads-mi-negocio
npm install
npm run test
```

Optional env vars:

- `SALEADS_BASE_URL` (required in CI if not already on login page)
- `GOOGLE_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `GOOGLE_ACCOUNT_NAME` (optional strict user-name assertion)
- `HEADLESS` (`true` by default)

Example:

```bash
SALEADS_BASE_URL="https://<your-saleads-env>/login" \
GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com" \
npm run test
```

## Evidence output

- Checkpoint screenshots and JSON test output: `artifacts/`
- Final PASS/FAIL report attachment: `artifacts/final-report-*.json`
- HTML report: `playwright-report/`
