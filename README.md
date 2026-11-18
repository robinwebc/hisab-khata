# nSedo

<table>
  <tr>
    <td valign="middle">
      <a href="https://github.com/robinwebc/hisab-khata/releases/latest"><img alt="Download APK" src="https://img.shields.io/badge/Download-APK-0F6B56?style=for-the-badge&logo=android&logoColor=white"></a>
      <br><br>
      <strong><a href="https://github.com/robinwebc/hisab-khata/releases/latest">Download APK</a></strong> — latest nSedo release (<code>arm64-v8a</code> + <code>x86_64</code>)
    </td>
    <td width="28"></td>
    <td valign="middle">
      <img src="branding/download-qr.png" alt="Scan to download nSedo" width="220">
    </td>
  </tr>
</table>

দোকানের হিসাব। বাংলা আগে, ইংরেজি টগল আছে। Gmail দিয়ে ঢুকতে হয়। খদ্দের, বাকি, ক্যাশ, মাল ও দোকানের সেটিংস `users/{uid}/...` এ সেভ হয়। মুখের ছাপ ও এনআইডির ছবি শুধু ফোনে থাকে।

A shop ledger. Bangla first, with an English toggle. Gmail sign-in is required. Customers, entries, cash, products and shop settings sync under `users/{uid}/...`. Face prints and NID photos stay on the device. Package name stays `bd.robin.hisabkhata`.

Package: `bd.robin.hisabkhata`. minSdk 24. Kotlin, Jetpack Compose, Material 3.

1.3.0 uses a red header, khata list, cashbox, quick-sell QR and stock tabs. বাকি / পাবো is red and জমা / দেবো is green, and those colours can still be changed in settings. Quick sell, the app passkey, Firestore sync and encrypted Drive backup stay. nSedo does not collect payments.

## চালানো / Run

Android SDK লাগবে (`ANDROID_HOME`), JDK 17+।

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleRelease
```

ডিবাগ বিল্ড: `./gradlew assembleDebug`

রিলিজ APK: `app/build/outputs/apk/release/app-release.apk`

এই APK-তে `arm64-v8a` (সাম্প্রতিক ফোন) ও `x86_64` (এমুলেটর) আছে। পুরনো ৩২-বিট ফোনের জন্য `app/build.gradle.kts`-এ `abiFilters`-এ `armeabi-v7a` যোগ করুন।

The release APK includes `arm64-v8a` and `x86_64`. Add `armeabi-v7a` to `abiFilters` in `app/build.gradle.kts` if you still need 32-bit phones.

## Gmail ও সিঙ্ক / Gmail and sync

অতিথি মোড নেই। প্রথমবার **Gmail দিয়ে প্রবেশ**। তারপর অ্যাপ খুললে **পাসকি / ফিঙ্গারপ্রিন্ট** (ফোনের পিনও চলে)। সেটিংসে বন্ধ করা যায়। **অন্য Gmail দিয়ে ঢুকুন** আলাদা অ্যাকাউন্টের হিসাব আনে।

ক্লাউড পাথ: `users/{uid}/parties|entries|cash|products|sales` এবং `users/{uid}/profile/shop`। মুছে ফেলা নরম (`deleted: true`) — ডকুমেন্ট হার্ড ডিলিট হয় না। নতুন ফোনে সাইন-ইন করলে ক্লাউড থেকে ফিরে আসে। অফলাইন ক্যাশ চালু। বদলালে ও অ্যাপ খুললে WorkManager আবার চেষ্টা করে।

There is no guest mode. The first open is **Continue with Gmail**. Later opens use device biometrics or the phone PIN. Cloud data lives under `users/{uid}/...` with soft-deletes. Rules are in `firestore.rules`.

## Firebase

প্রজেক্ট `hisab-khata-7114a`। `app/google-services.json` আসল কনফিগ। `hisabkhata.firebase.configured=true`।

1. [Firebase console](https://console.firebase.google.com)-এ প্রজেক্ট খুলুন।
2. Android অ্যাপ যোগ করুন, প্যাকেজ `bd.robin.hisabkhata`।
3. ডাউনলোড করা `google-services.json` দিয়ে `app/google-services.json` প্রতিস্থাপন করুন।
4. Authentication-এ Google ও Phone চালু করুন। Firestore চালু করুন।
5. নিচের কীস্টোরের SHA-1 Firebase-এ যোগ করুন (Google সাইন-ইনের জন্য)।
6. Web client ID (Firebase-এর OAuth web client) বসান `gradle.properties`-এ:
   ```
   hisabkhata.firebase.configured=true
   hisabkhata.webClientId=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
   ```
7. `./gradlew assembleRelease`

Firestore নিয়ম (শুধু নিজের ডেটা):

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

সিঙ্ক মুখের এমবেডিং, মুখের ছবি, এনআইডি নম্বর বা এনআইডি ছবি পাঠায় না। খদ্দেরের OTP যাচাই আলাদা Firebase অ্যাপে হয়, যাতে দোকানদারের Gmail সেশন বদলে না যায়।

Sync never uploads face embeddings, face photos, NID numbers, or NID photos. Customer OTP uses a secondary Firebase app so the shop Gmail session stays signed in.

## কীস্টোর / Signing

ডেমো রিলিজ কী রিপোতে আছে, যাতে `assembleRelease` সই করা APK বানায়। প্লে স্টোরে দেওয়ার আগে নিজের কী দিয়ে বদলান।

| | |
|---|---|
| File | `keystore/hisab-khata-release.jks` |
| Properties | `keystore/keystore.properties` |
| Alias | `hisabkhata` |
| Store password | `hisabkhata-demo` |
| Key password | `hisabkhata-demo` |

SHA-1:

```bash
keytool -list -v -alias hisabkhata -keystore keystore/hisab-khata-release.jks -storepass hisabkhata-demo
```

This is a demo key checked in so the release build is reproducible. Replace it before publishing.

## কী কী আছে / What is inside

- খদ্দের ও সরবরাহকারী, বাকি/জমা, চলতি ব্যালেন্স, খোঁজ।
- ক্যাশবক্স (আয়/খরচ) ও খাত।
- দৈনিক/মাসিক রিপোর্ট ও বার চার্ট।
- খদ্দেরের পিডিএফ স্টেটমেন্ট শেয়ার। এসএমএস ও WhatsApp-এ লেখা বসিয়ে অ্যাপ খোলে। অ্যাপ নিজে মেসেজ পাঠায় না।
- JSON ব্যাকআপ/রিস্টোর। মুখ ও এনআইডি ডিফল্টে ফাইলে থাকে না। টিক দিলে শুধু সেই ফাইলে যোগ হয়।
- বাকি সীমা: ডিফল্ট, খদ্দেরপ্রতি, দৈনিক। সতর্ক বা আটকানো।
- স্লিপ স্ক্যান: ক্যামেরা বা গ্যালারি, ML Kit টেক্সট রিকগনিশন, তারপর আপনি মিলিয়ে সেভ করেন। নমুনা স্লিপ দিয়ে এমুলেটরেও দেখা যায়। ল্যাটিন মডেল ইংরেজি ও ইংরেজি অঙ্ক ভালো পড়ে। বাংলা অক্ষর নির্ভরযোগ্য নাও হতে পারে। বাংলা অঙ্ক পার্সার বোঝে।
- বারকোড দিয়ে মাল যোগ, খোঁজা ও বিক্রি। স্টক কমে।
- মুখ: ML Kit ফেস ডিটেকশন + MobileFaceNet (TFLite, ১১২×১১২ → ১৯২-মাত্রার এমবেডিং)। এমবেডিং শুধু `vault.db`-তে, অটো ব্যাকআপ ও সিঙ্ক থেকে বাদ। এনরোলের আগে সম্মতি। মুছে ফেলা যায়। মডেল না থাকলে লোকাল গ্রিড এমবেডিং।
- ফোন যাচাই: Firebase Phone Auth সেট থাকলে OTP, নাহলে কল করে নিশ্চিত। ব্যাজ আলাদা।
- এনআইডি স্ক্যান স্থানীয়। অটো ব্যাকআপে যায় না।
- অ্যাপ পিন ও বায়োমেট্রিক।

Customers and suppliers, credit (বাকি) and payment (জমা), running balance, search, cash box, daily and monthly charts, PDF statement, SMS and WhatsApp drafts (the app never sends by itself), JSON backup, credit limits, slip OCR, barcode stock, on-device face match, phone verification, local NID scan, and a PIN or biometric lock.

## মুখের মডেল / Face model

`app/src/main/assets/mobile_face_net.tflite` comes from [syaringan357/Android-MobileFaceNet-MTCNN-FaceAntiSpoofing](https://github.com/syaringan357/Android-MobileFaceNet-MTCNN-FaceAntiSpoofing) (MIT). See `third_party/mobilefacenet/LICENSE`. Input is a batch of 2 images, 112×112, normalized with `(pixel - 127.5) / 128`. Output is a 192-d embedding, then L2-normalized. Cosine threshold is 0.62. The same photo is fed twice because this file’s input batch is 2.

বাংলা ফন্ট Noto Sans Bengali, SIL Open Font License।

## টেস্ট / Tests

```bash
./gradlew testDebugUnitTest
```

সীমা, স্লিপ পার্সার, ফোন, মুখের মিল, ক্লাউড ম্যাপ (মুখ/এনআইডি বাদ), ব্যাকআপ ও পিন।
