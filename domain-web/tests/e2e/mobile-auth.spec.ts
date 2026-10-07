import { test, expect } from '@playwright/test';
/** Exercises Expo AuthSession's actual browser popup, PKCE callback, device BFF and profile screen. */
test('Expo web preview completes real mobile OAuth and profile/logout screens', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 }); await page.goto('http://localhost:8082');
  const popupPromise = page.waitForEvent('popup');
  await page.getByRole('button', { name: 'Нэвтрэх', exact: true }).click();
  const popup = await popupPromise; await popup.getByLabel('Имэйл').fill(process.env.DEV_AGENT_EMAIL!); await popup.getByLabel('Нууц үг').fill(process.env.DEV_AGENT_PASSWORD!);
  await popup.getByRole('button', { name: /^Нэвтрэх/ }).click();
  await expect(page.getByRole('button', { name: 'Нэвтрэх', exact: true })).toHaveCount(0);
  const account = page.getByRole('tab', { name: 'Бүртгэл', exact: true }); await account.scrollIntoViewIfNeeded(); await account.click();
  await expect(page.getByText(process.env.DEV_AGENT_EMAIL!, { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Гарах', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Нэвтрэх', exact: true })).toBeVisible();
});
