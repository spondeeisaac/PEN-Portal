PEN PORTAL — DIRECT APPS SCRIPT MOBILE PWA

This is the mobile-friendly version requested by the user.

Architecture:
  PEN Portal PWA (GitHub Pages)
          ↓
  DIRECT Apps Script web app
          ↓
  PEN Portal backend / Google Sheet / Drive

It does NOT load Strikingly.

Production Apps Script:
https://script.google.com/macros/s/AKfycbwmduU91w44Y5xmQKTP14GqYM5Eda-59Exo6_gNKeWsT16ktcc0JMZhUnzC1jYTljeF8g/exec

Files:
  index.html
  manifest.webmanifest
  service-worker.js
  icons/

Replace the GitHub repository files with the contents of this package.

Canonical production deployment aligned on 2026-10-29. The Apps Script project URL constant must match this URL.

The service worker deliberately caches only the PWA shell.
The cross-origin Apps Script portal is not cached.

The PWA is responsive for Android, iPhone/iPad, tablets and computers.
