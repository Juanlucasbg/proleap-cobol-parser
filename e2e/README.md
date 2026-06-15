# SaleADS Mi Negocio E2E

Playwright test for the full SaleADS.ai "Mi Negocio" workflow:

1. Login with Google
2. Open and validate Mi Negocio menu
3. Validate "Agregar Negocio" modal
4. Open "Administrar Negocios"
5. Validate account sections
6. Validate legal links ("Términos y Condiciones", "Política de Privacidad")
7. Print a PASS/FAIL final report by validation area

## Environment-agnostic setup

The test does not hardcode any SaleADS domain.

- Set `SALEADS_URL` (or `BASE_URL`) to whichever environment should be tested.
- If no URL is provided, the test assumes the page is already at the login screen.

## Run

```bash
cd e2e
npm install
npx playwright install --with-deps
SALEADS_URL="https://your-environment.example.com" npm test
```

## Evidence

The test captures screenshots at these checkpoints:

- Dashboard after login
- Expanded Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios account page
- Términos y Condiciones page
- Política de Privacidad page

Artifacts are stored in Playwright's `test-results` output directory.
