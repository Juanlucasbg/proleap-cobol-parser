# SaleADS E2E - Mi Negocio Workflow

This folder contains an end-to-end Playwright test for the `saleads_mi_negocio_full_test` workflow.

## What it validates

The spec `tests/saleads-mi-negocio.spec.ts` validates:

1. Login with Google (including optional account selection for `juanlucasbarbiergarzon@gmail.com`).
2. Mi Negocio menu expansion.
3. Agregar Negocio modal content.
4. Administrar Negocios page sections.
5. Informacion General section.
6. Detalles de la Cuenta section.
7. Tus Negocios section.
8. Terminos y Condiciones legal page (same tab or popup).
9. Politica de Privacidad legal page (same tab or popup).
10. Final JSON report with PASS/FAIL per section.

Screenshots are captured at key checkpoints and saved under:

- `e2e/test-results/evidence/<timestamp>/`

The final report is saved as:

- `e2e/test-results/evidence/<timestamp>/final-report.json`

## Usage

Install dependencies:

```bash
cd e2e
npm install
npx playwright install --with-deps chromium
```

Run test:

```bash
SALEADS_LOGIN_URL="https://<current-environment-login-url>" npm run test:mi-negocio
```

Optional headed mode:

```bash
SALEADS_LOGIN_URL="https://<current-environment-login-url>" npm run test:mi-negocio:headed
```

## Environment portability

- The test does **not** hardcode a specific SaleADS domain.
- Provide the current environment login URL via `SALEADS_LOGIN_URL`.
- Element targeting prefers visible text and role-based selectors.
