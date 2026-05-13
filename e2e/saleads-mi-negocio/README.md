# SaleADS Mi Negocio Full Workflow Test

This Playwright script validates the full Mi Negocio module workflow end-to-end:

1. Login with Google.
2. Open and validate the Mi Negocio menu.
3. Validate the Agregar Negocio modal.
4. Open Administrar Negocios and validate all account sections.
5. Validate legal links (Términos y Condiciones, Política de Privacidad), including new-tab handling.
6. Generate screenshots and a final PASS/FAIL report.

## Key Design Choices

- **Environment agnostic**: no hardcoded SaleADS domain.
- **Text-first selectors**: prioritizes visible text and roles.
- **UI wait strategy**: waits after every click/navigation.
- **Evidence capture**: screenshots at major checkpoints + final report JSON.

## Usage

From this directory:

```bash
npm install
npm run test:mi-negocio
```

### Optional environment variables

- `SALEADS_LOGIN_URL` (or `SALEADS_URL`): login page URL for any environment.
- `HEADLESS=false`: run with visible browser.
- `PLAYWRIGHT_USER_DATA_DIR`: custom persistent browser profile path.

## Outputs

- `artifacts/<timestamp>/screenshots/*.png`
- `artifacts/<timestamp>/final-report.json`

The report contains:

- PASS/FAIL for each requested validation area.
- Captured legal-page URLs.
- Screenshot file paths.
- Any per-step errors.
