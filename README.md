# 📡 FileBeam

Telefondan Android TV'ye (ve herhangi iki Android cihaz arasında) **yerel ağ üzerinden ultra hızlı dosya aktarımı**. Kotlin + Jetpack Compose, Material 3.

## Özellikler
- ⚡ **Ham TCP socket** ile aktarım — WiFi 5/6 ağında onlarca MB/sn
- 📺 **Android TV desteği** — LEANBACK launcher, D-pad uyumlu arayüz
- 📷 **QR ile bağlanma** — TV'deki QR'ı telefonla okut
- 🔢 **PIN koruması** — yanlış PIN ile aktarım reddedilir
- 📶 **Otomatik cihaz keşfi** — UDP broadcast ile IP girmeye gerek yok (manuel IP de var)
- 📂 Alıcı **İndirilenler** klasörüne kaydeder (MediaStore)
- 📊 Canlı ilerleme + **anlık hız göstergesi**
- 🌗 Koyu/açık tema + dinamik renkler

## Kullanım
1. **TV'de:** FileBeam > "Al" → QR + PIN ekranda görünür
2. **Telefonda:** FileBeam > "Gönder" → cihazı listeden seç **veya** QR'ı okut **veya** IP+PIN gir
3. Dosyaları seç → **GÖNDER**

## İmzalı APK (GitHub Actions)
Push sonrası Actions otomatik derler; Artifacts > `FileBeam-release-apk`. Kalıcı imza için 4 secret ekleyin:
`SIGNING_KEYSTORE_BASE64`, `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.

## Teknik
- Protokol: UDP 47812 (keşif beacon), TCP 47813 (aktarım). Satır başlığı: `BEAM1|pin|dosyaadı|boyut`
- QR içeriği: `filebeam://<ip>:<port>#<pin>`
- Aynı WiFi ağını gerektirir; misafir ağlarında AP izolasyonu çalışmayabilir.
