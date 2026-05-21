# SaleADS Mi Negocio E2E

This repository now includes an end-to-end Playwright test for the workflow:

- Login with Google
- Open and validate Mi Negocio menu
- Validate Agregar Negocio modal
- Open Administrar Negocios and validate account sections
- Validate legal links (Términos y Condiciones / Política de Privacidad)
- Produce a final PASS/FAIL JSON report by step

## Files

- `playwright.config.ts`
- `tests/saleads-mi-negocio.spec.ts`
- `.env.example`

## Run

1. Install browser once:

```bash
npm run playwright:install
```

2. Configure environment values:

```bash
cp .env.example .env
```

3. Execute the full test:

```bash
npm run test:saleads-mi-negocio
```

Use headed mode for interactive debugging:

```bash
npm run test:saleads-mi-negocio:headed
```

## Notes

- No specific SaleADS domain is hardcoded. Use `SALEADS_LOGIN_URL` or `BASE_URL`.
- The test prefers visible text selectors (`getByRole`/`getByText`).
- Checkpoint screenshots and the final report are attached in Playwright test output.
