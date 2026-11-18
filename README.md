# হিসাব খাতা / Hisab Khata

<p>
  <a href="https://github.com/robinwebc/hisab-khata/releases/download/v1.0.0/HisabKhata.apk"><img alt="Download APK" src="https://img.shields.io/badge/Download-APK-2ea44f?style=for-the-badge&logo=android&logoColor=white"></a>
</p>

**[Download APK](https://github.com/robinwebc/hisab-khata/releases/download/v1.0.0/HisabKhata.apk)** — Hisab Khata v1.0.0 (`arm64-v8a` + `x86_64`)

ছোট দোকানের হিসাব-কিতাব। বাংলা আগে, ইংরেজি টগল আছে। নাম বদলাতে `app/src/main/res/values/strings.xml` ও `values-en/strings.xml`-এর `app_name` বদলান।

A ledger for a small shop in Bangladesh. Bangla is the default language, with an English toggle. To rename the app, change `app_name` in `app/src/main/res/values/strings.xml` and `values-en/strings.xml`.

Package: `bd.robin.hisabkhata`. minSdk 24. Kotlin, Jetpack Compose, Material 3.

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

## অতিথি মোড / Guest mode

Firebase ছাড়াই অ্যাপ চলে। লগইন স্ক্রিনে **অতিথি হিসেবে চালিয়ে যান / Continue offline as guest** চাপুন। হিসাব এই ফোনে Room ডাটাবেসে থাকে।

The app runs without Firebase. On the login screen choose **Continue offline as guest**. The ledger stays on the phone in Room.

## Firebase পরে যোগ করা / Add Firebase later

`app/google-services.json` এখন প্লেসহোল্ডার (`project_id`: `hisab-khata-placeholder`)। `gradle.properties`-এ `hisabkhata.firebase.configured=false`। এই অবস্থায় Gmail সাইন-ইন, OTP ও Firestore সিঙ্ক চালু হয় না। অ্যাপ বোঝায় এবং অতিথি মোডে চলতে থাকে।

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
