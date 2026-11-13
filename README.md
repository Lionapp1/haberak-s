# 📰 Haber Akışı

Modern, Material 3 temelli Android haber okuyucu uygulaması. Kotlin + Jetpack Compose ile geliştirildi.

## Özellikler
- 📡 Türkçe haber kaynakları (NTV, BBC Türkçe) — RSS üzerinden
- 🌗 Koyu / açık tema + sistem teması takibi (Android 12+ dinamik renkler)
- 🔍 Haberlerde arama
- 🔖 Yer imi (kaydedilen haberler, cihazda saklanır)
- 🖼️ Haber görselleri, tam ekran okuma (WebView)
- ⚡ R8 minify ile optimize release build

## İmzalı APK alma (GitHub Actions)
1. Bu repoyu GitHub'a push'la.
2. Kendi imza anahtarını oluştur:
   ```bash
   keytool -genkey -v -keystore release.keystore -alias haberakisi \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
3. Repo → Settings → Secrets and variables → Actions altına 4 secret ekle:
   - `SIGNING_KEYSTORE_BASE64` → `base64 -w 0 release.keystore` çıktısı
   - `SIGNING_KEYSTORE_PASSWORD` → keystore şifresi
   - `SIGNING_KEY_ALIAS` → `haberakisi`
   - `SIGNING_KEY_PASSWORD` → key şifresi
4. Actions sekmesinde build çalışır; **Artifacts** bölümünden `HaberAkisi-release-apk` dosyasını indir.
   Release oluşturursan APK otomatik release'e eklenir.

> ⚠️ `keystore.properties` ve `*.keystore` dosyaları `.gitignore`'dadır — anahtarını repoya yükleme. Aynı anahtarla imzaladığın sürece uygulama güncellemeleri sorunsuz kurulur.

## Yerelde derleme
```bash
./gradlew assembleRelease   # veya: gradle assembleRelease
```

## Kaynak ekleme
`app/src/main/java/com/lion/haberakisi/data/NewsRepository.kt` içindeki `sources` listesine yeni RSS adresi eklemen yeterli.
