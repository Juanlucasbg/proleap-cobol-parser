# SaleADS Mi Negocio full workflow test

This folder contains an environment-agnostic Playwright test for:

- Google login
- Mi Negocio menu expansion
- Agregar Negocio modal validation
- Administrar Negocios account page validation
- Legal links (Términos y Condiciones, Política de Privacidad)
- Final PASS/FAIL JSON report

## Run

1. Install dependencies:

```bash
npm install
npx playwright install --with-deps chromium
```

2. Run the test:

```bash
npm run test:e2e:mi-negocio
```

## Environment handling

- If the browser session already starts at SaleADS login page, no URL is needed.
- If the test starts from `about:blank`, provide a runtime URL:

```bash
SALEADS_LOGIN_URL="https://<your-saleads-env>/login" npm run test:e2e:mi-negocio
```

No domain is hardcoded in the test itself.
