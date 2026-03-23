# SaleADS Mi Negocio Full Workflow Test

This module contains an environment-agnostic Playwright E2E test for:

- Google login flow
- Mi Negocio menu and modal validations
- Administrar Negocios account page validations
- Legal links (Terms and Privacy) with new-tab or same-tab handling
- Checkpoint screenshots and final PASS/FAIL report

## Usage

```bash
cd automation/saleads-mi-negocio
npm install
npx playwright install chromium
SALEADS_LOGIN_URL="https://<current-environment-login-url>" npm test
```

Notes:

- The test does not hardcode a SaleADS domain.
- If `SALEADS_LOGIN_URL` is not set, the test expects the page to already be on a SaleADS login view.
- The test prefers visible-text selectors and waits after click actions.
