# Cafe Bazaar publishing preparation

Prepared from this workspace on 2026-11-09. The Cafe Bazaar developer portal is a client-rendered app; the browser reader exposed only its loading shell in this environment. Treat the dashboard's live fields and limits as authoritative. The official developer channel confirms AAB support and says the annual subscription can be paid after a release is approved, at the release step. It also announced mandatory two-step verification for developer accounts:
- [Cafe Bazaar developer portal](https://developers.cafebazaar.ir/fa)
- [Official Cafe Bazaar Developers announcements: AAB and release workflow](https://t.me/s/CafeBazaarDevelopers?before=272)
- [Official Cafe Bazaar Developers announcement: two-step verification](https://t.me/s/CafeBazaarDevelopers?after=300)

## Project facts

| Item | Current project value |
| --- | --- |
| App title | Luma |
| Application ID | `com.luma.lifeos` |
| Release variant | `productionRelease` |
| Current version | `1.5.8` (`versionCode 13`) |
| Minimum Android | Android 9 / API 28 |
| Target SDK | 35 |
| Current production artifact | `app/production/release/app-production-release.apk` |
| Release minification | Enabled |

Upload the production artifact, not `app/release/app-release.apk`: that other output is older (`1.4.0`, code 8). The current Gradle configuration does not define a release signing config, and no `.jks` or `.keystore` file was found in the workspace. The APK's signature has not been verified here, so do not upload it until it is confirmed as signed with the permanent release key.

The app has a vector launcher icon at `app/src/main/res/drawable/ic_launcher.xml`, but there is no store-ready raster icon or listing screenshot set in app resources. The app declares optional Health Connect step reading, special Usage Access, and notification permission. Its privacy document says records stay on-device, there is no account, ads, analytics or network access, and user-created backup archives are unencrypted: [PRIVACY.md](PRIVACY.md).

## Store listing draft (Persian)

**Title**  
Luma | دفتر زندگی شخصی

**Short description**  
حال‌وهوا، خاطره‌ها، کارها و عادت‌های روزانه را در فضایی خصوصی ثبت کنید.

**Full description**  
Luma فضایی شخصی برای ثبت و مرور زندگی روزمره است.

• حال‌وهوای روزانه و یادداشت‌های ژورنال را ثبت کنید.  
• خاطره‌های بلند را با برچسب و عکس نگه دارید.  
• کارها و عادت‌های روزانه را مدیریت کنید.  
• روزها را در تقویم و خط زمانی ببینید و مرور هفتگی داشته باشید.  
• از ویجت‌های کارها، عادت‌ها و ثبت سریع استفاده کنید.  
• در صورت تمایل، قدم‌ها را از Health Connect و داده‌های استفاده از برنامه‌ها را با فعال‌کردن دسترسی مربوط وارد کنید.

اطلاعات Luma روی دستگاه شما ذخیره می‌شود و برای استفاده معمولی نیازی به حساب کاربری نیست. پشتیبان‌گیری دستی اختیاری است؛ فایل خروجی رمزگذاری نمی‌شود، پس آن را در محل امن نگه دارید.

دسترسی Health Connect فقط برای خواندن تعداد قدم‌ها استفاده می‌شود. دسترسی Usage Access اختیاری است و فقط در صورت فعال‌کردن آن، داده‌های استفاده از برنامه‌ها وارد می‌شوند.

**Suggested category**  
Choose the closest current Bazaar category for productivity / personal organization; confirm the available category names in the dashboard.

**Store art to prepare**
- Export a square, high-resolution PNG based on the existing Luma launcher mark, checking Bazaar's current dimensions and transparency rules in the upload form.
- Capture 4–6 clean screenshots from the current production build: Today, Timeline/Calendar, task or habit flow, weekly review, and widgets. Use sample data only; do not show real personal records.
- Add a Persian support contact and a public privacy-policy URL. The in-app policy exists, but a public URL and support address are not present in this workspace.

## Step-by-step release

1. **Create and secure the developer account.** Register/sign in at the developer portal, complete the requested developer profile and any identity/payment details, and enable two-step verification. Use the legal developer name and contact information you want shown publicly.
2. **Create the app entry.** Start a new app in the dashboard. Keep package name `com.luma.lifeos`; package identity must remain stable for later updates. Select the category and pricing model that match the actual product.
3. **Complete the listing.** Enter the Persian title and description above, add the support contact and public privacy URL, upload the icon and current screenshots, and complete the dashboard's age/content and permissions/data declarations truthfully.
4. **Create a permanently signed release.** In Android Studio use **Build → Generate Signed Bundle / APK**. Select module `app`, release variant `productionRelease`, and use an Android App Bundle if the dashboard accepts it (Bazaar's official channel says AAB uploads are supported). If this is the first release, create a dedicated upload/release keystore; keep secure offline backups and never commit it or its passwords. For an existing Bazaar app, use the exact same signing key that signed its previous release.
5. **Verify before upload.** Install the signed release on a clean Android 9+ device, check onboarding, language switching, reminders/notifications, optional permission flows, backup/export wording, and that no QA/debug application ID was built. Confirm package `com.luma.lifeos`, version code 13, version name 1.5.8, and that the artifact is signed. If the dashboard accepts AAB, upload the signed `.aab`; otherwise upload the signed production APK.
6. **Submit for review.** Upload the artifact, add Persian release notes, finish every required dashboard declaration, then request review. Describe Health Connect as read-only step access and Usage Access as optional special access used for the app-usage feature.
7. **Publish after approval.** Check the release approval status, pay the annual developer subscription when the dashboard requests it at release, then publish. The current official announcement says this payment can happen after approval; check the amount and terms shown in the account.
8. **Check the live listing.** Open the Bazaar page, confirm the correct icon, Persian text, screenshots, install, and first launch. For every later update, keep the same package and signing key and increase `versionCode` above 13.

## Remaining before submission

- [ ] Developer account/profile, public contact, and two-step verification.
- [ ] Public privacy-policy page and its URL.
- [ ] Store icon export and real production screenshots.
- [ ] Permanent signing key created or recovered and safely backed up.
- [ ] Signed release artifact; verify its signature before upload.
- [ ] Current dashboard declarations completed for Health Connect, Usage Access, and notifications.
- [ ] Decide free/paid model and final Bazaar category.

Do not upload either existing APK until its signature is verified. The production artifact metadata is current, but signing is not configured in the project Gradle file.
