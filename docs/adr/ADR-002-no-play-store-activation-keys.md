# ADR-002: No Play Store. Website APK + AWS OTA + activation keys

- **Status:** Accepted (10 Nov 2026). Replaces the "two flavors, Play for public" plan in Delivery Plan v1.0 section 3.2.
- **Reference:** `devxjitin/ultron-android` (`mcp/license`, `mcp/license-admin`, `ultronapp/`, `docs/ARCHITECTURE.md` sections 21-22). We copy that model.

## Decision

Khojo AI Play Store par nahi jayega. Sabke liye ek hi raasta:

1. **Install:** website se APK download (`GET /v1/releases/download`, public, throttled). Pehli baar user "Install unknown apps" allow karta hai.
2. **Updates:** sabke liye AWS OTA (jo Sprint 2 mein bana: S3 + `khojo-updates` Lambda + in-app updater + SHA-256 verify).
3. **Unlock:** app tab tak locked jab tak valid **activation key** na daali jaaye. Key = plan + validity. Kharidari app ke bahar hoti hai (website store / payment link / manually), app sirf key check karti hai.

## Ultron se kya copy kar rahe hain

| Cheez | Ultron | Khojo |
|---|---|---|
| Key format | `ULTR-XXXX-XXXX-XXXX-XXXX` (Crockford base32, 80 bit) | `KHOJ-XXXX-XXXX-XXXX-XXXX`, same normalize rules (O→0, I/L→1, spaces/dashes ignore) |
| Storage | DynamoDB `ultron-licenses`, primary key = SHA-256 of key (table mein asli key kabhi nahi) | DynamoDB `khojo-licenses`, same |
| Routes | `POST /v1/license/activate`, `POST /v1/license/status` (Firebase JWT) | Same routes, `lambdas/license` |
| Device binding | Ek key ek phone (Firebase anonymous uid) par ek waqt; naya phone activate kare to key move, purana agle status check par lock; din mein max 1 move | Same |
| Expiry | `durationDays` pehli activation se; `0` = kabhi expire nahi | Same |
| Offline | Aakhri server jawab 7 din tak valid, expiry ke baad nahi | Same |
| Admin | `ultronapp/` static web app (Google sign-in, `ADMIN_EMAILS`), `mcp/license-admin` Lambda: create / edit / revoke / reissue / allow-move, CSV export | `admin/` web app + `lambdas/license-admin`, same features |
| Key email | Resend (`no-reply@<domain>`), API key SSM SecureString mein | Same |
| Identity | Firebase Anonymous Auth + App Check (Play Integrity sirf device integrity, `PLAY_RECOGNIZED`/`LICENSED` off) | Same (Cognito plan hata diya) |

## Ultron se kya alag hai (aur kyu)

- **Har cloud call par server license check karta hai.** Ultron ke routes sirf JWT dekhte hain; Khojo ke cloud calls (embeddings, jobs, transcription) GPU paise khaate hain. Isliye control Lambda uid → license (GSI `uid`) dekhta hai: active? plan ki quota bachi? Client ke "unlocked" flag par bharosa nahi.
- **Plan = quota.** Key ka `plan` (`free`, `trial`, `pilot`, `plus`, `seller_pro`, `media_pro`) blueprint section 15 ki limits se map hota hai (`lambdas/control` mein ek jagah). App bina key ke locked hai, isliye Free plan bhi ek ₹0 `free` key hai (website par email se, lifetime).
- **Updates license ke bina bhi milte hain.** `/v1/releases/latest` sirf JWT maangta hai, taaki expired user bhi bug-fix update le sake (Ultron jaisa).
- **Expire hone par local data delete nahi hota.** Phone ka index rehta hai; app "key expire ho gayi, renew karo" dikhati hai aur cloud calls band.
- Audit report (Ultron, 10 Nov 2026) ke 2 bugs pehle din se avoid: reissue running key ki bachi hui expiry rakhe (poora term restart nahi), aur reissue ko revenue mein do baar/zero na gine.

## Kya hata rahe hain

- `play` flavor, `play-release.yml`, Play Console, Play Billing, RTDN/Pub/Sub, `/v1/billing/verify`, Play In-App Updates, Play Feature Delivery, Play Data safety form, Play developer fee.
- Ek hi build: `applicationId = com.khojoai.app`. Abhi tak koi APK kisi phone par install nahi hua, isliye id badalna ab free hai (baad mein badla to sabko reinstall).
- `BETA_KEY` (APK ke andar shared secret) sirf tab tak, jab tak Firebase JWT + activation nahi aate (Sprint 4B).

## Consequences

- ✅ Play ka 15% fee nahi; payment gateway ~2% + GST. Release Play review ke bina, har merge ke 20-30 min mein.
- ✅ Ultron ka tested code/infra reuse.
- ⚠️ Install friction: "unknown sources" permission + Play Protect warning. Website par screenshots wala install guide, ek hi signing key hamesha.
- ⚠️ **Android developer verification:** Google sideload apps ke liye developer registration la raha hai (30 Sep 2026 se Brazil, Indonesia, Singapore, Thailand; 2027 mein global, India ki date abhi announce nahi). Iske baad unregistered developer ka APK certified phones par install nahi hoga. Action: Android Developer Console mein register karo aur `com.khojoai.app` + OTA signing key register karo, India ki date track karo. Public launch se pehle ye done hona chahiye.
- ⚠️ Key share/abuse: device binding + 1 move/day + server-side quota. Key leak ho to admin revoke/reissue.
- ⚠️ Payment, invoices, refunds, GST ab hamari zimmedari (Play nahi karta). Website store aane tak admin manually key banata hai.
- ⚠️ Signing key kho gayi = har user ko reinstall. Offline backup 2 jagah (pehle se rule).
