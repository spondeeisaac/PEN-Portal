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
https://script.google.com/macros/s/AKfycbz4OAIXJVtUWOPUjIXz6WV4DWYLWepJsQMCshp5IKEa4efBcrtrisoXgmRqeLYfIyDicA/exec

Files:
  index.html
  manifest.webmanifest
  service-worker.js
  icons/

Replace the GitHub repository files with the contents of this package.

Do not change the Apps Script project as part of this step.

The service worker deliberately caches only the PWA shell.
The cross-origin Apps Script portal is not cached.

The PWA is responsive for Android, iPhone/iPad, tablets and computers.
