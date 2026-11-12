# sinagtechlabs.com files for Share Parol

Upload these to the website so a "Share Parol" link opens Sinag when it is installed, and the
download page when it is not.

| File | Upload to | Notes |
|---|---|---|
| `smartparol.html` | `https://sinagtechlabs.com/smartparol.html` (and the same on `www.`) | Fill in `APP_STORE_URL` and `ANDROID_PACKAGE` at the top of the script once the apps are published. |
| `.well-known/apple-app-site-association` | `https://sinagtechlabs.com/.well-known/apple-app-site-association` | No file extension. Served as `application/json` over HTTPS, no redirects. Replace `TEAMID.BUNDLEID` with Sinag's Apple Team ID and bundle id. |
| `.well-known/assetlinks.json` | `https://sinagtechlabs.com/.well-known/assetlinks.json` | Replace the package name and the SHA-256 of the **Play app signing** key (Play Console → Setup → App signing). |

Both `.well-known` files must also be served on `www.sinagtechlabs.com` if links use `www.`.

## Link format

`https://sinagtechlabs.com/smartparol.html?a=<lantern address>`, e.g. `?a=192.168.68.60`.
The address only works on the lantern's home WiFi, so the link reveals nothing useful elsewhere.

## Before switching the QR code to this link

1. The three files are live (open the URLs in a browser).
2. iOS: "Associated Domains" capability added for `applinks:sinagtechlabs.com` and
   `applinks:www.sinagtechlabs.com` (needs the paid Apple Developer account).
3. Android: the link filter in the manifest already declares the domain; verification starts once
   `assetlinks.json` is live.
4. Then turn on the web link for the QR code in the apps (`SHARE_QR_USES_WEB_LINK`).
