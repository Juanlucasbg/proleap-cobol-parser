## SaleADS Mi Negocio E2E

This folder contains the Playwright test for:

- `saleads_mi_negocio_full_test`

### Preconditions

- Browser is already on the SaleADS login page for the current environment (dev/staging/prod).
- Google account is available for login (`juanlucasbarbiergarzon@gmail.com`) when the selector appears.

### Install

```bash
npm install
npm run playwright:install
```

### Run

Headless:

```bash
npm run test:saleads:mi-negocio
```

Headed:

```bash
npm run test:saleads:mi-negocio:headed
```

### Evidence and report

- Checkpoint screenshots are attached to the Playwright test result.
- A final PASS/FAIL report is printed for all required workflow sections.
- Final legal URLs (terms/privacy) are recorded in test output and annotations.
