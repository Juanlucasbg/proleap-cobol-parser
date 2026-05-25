# SaleADS E2E tests

## Mi Negocio full workflow

This repository now includes a Playwright test for the requested workflow:

- `e2e/saleads_mi_negocio_full_test.spec.ts`

### Run

1. Install browser binaries:

```bash
npm run playwright:install
```

2. Run the test:

```bash
npm run test:saleads:mi-negocio
```

### Environment handling

- No domain is hardcoded.
- If `SALEADS_LOGIN_URL` is provided, the test will open that URL first.
- If `SALEADS_LOGIN_URL` is not provided, the test assumes the browser is already on the SaleADS login page, as requested.

### Artifacts

The test captures:

- Screenshots at key checkpoints.
- A JSON final report with PASS/FAIL per requested field and captured legal URLs.
