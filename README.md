# Daily Action Plan

AB's Daily Action Plan: the web page used on the PC (`index.html`, served by GitHub Pages) and the Android app built from the same page.

- `index.html` — the whole DAP. `sw.js` lets it open without internet.
- `android/`, `capacitor.config.json`, `package.json` — the Android app (Capacitor). `.github/workflows/android.yml` builds it on every change and publishes `DAP.apk` under Releases.

No plan data is kept in this repository. The plan lives on the PC (data folder), on the phone, and in a "Daily Action Plan" folder in Google Drive.
