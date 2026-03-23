## SaleADS Mi Negocio E2E

Playwright test suite for validating the complete **Mi Negocio** workflow in any SaleADS.ai environment.

### Test included

- `tests/saleads_mi_negocio_full_test.spec.ts`

This test validates:

1. Login with Google (and optional account selection)
2. Mi Negocio menu expansion
3. Agregar Negocio modal content
4. Administrar Negocios view sections
5. Informacion General
6. Detalles de la Cuenta
7. Tus Negocios
8. Terminos y Condiciones legal page + final URL
9. Politica de Privacidad legal page + final URL
10. Final PASS/FAIL report per required field

### Environment agnostic behavior

- No hardcoded SaleADS domain is used.
- If the browser is already on login page, test starts from current page.
- Optionally set `SALEADS_URL` when you want the test to navigate to login itself.
- Selectors primarily use visible text and accessible roles.
- Handles legal links that open in either same tab or a new tab.

### Run

```bash
cd e2e
npm install
npx playwright install --with-deps
npm run test:e2e
```

Optional:

```bash
SALEADS_URL="https://your-saleads-environment/login" npm run test:e2e
HEADLESS=false npm run test:e2e:headed
```

### Evidence

- Checkpoint screenshots are saved under `e2e/screenshots/`.
- A JSON summary is printed as `FINAL_REPORT=...` in the test output.
