# ð¡ FileBeam

Telefondan Android TV'ye (ve herhangi iki Android cihaz arasÄ±nda) **yerel aÄ Ã¼zerinden ultra hÄ±zlÄ± dosya aktarÄ±mÄ±**. Kotlin + Jetpack Compose, Material 3.

## Ãzellikler
- â¡ **Ham TCP socket** ile aktarÄ±m â WiFi 5/6 aÄÄ±nda onlarca MB/sn
- ðº **Android TV desteÄi** â LEANBACK launcher, D-pad uyumlu arayÃ¼z
- ð· **QR ile baÄlanma** â TV'deki QR'Ä± telefonla okut
- ð¢ **PIN korumasÄ±** â yanlÄ±Å PIN ile aktarÄ±m reddedilir
- ð¶ **Otomatik cihaz keÅfi** â UDP broadcast ile IP girmeye gerek yok (manuel IP de var)
- ð AlÄ±cÄ± **Ä°ndirilenler** klasÃ¶rÃ¼ne kaydeder (MediaStore)
- ð CanlÄ± ilerleme + **anlÄ±k hÄ±z gÃ¶stergesi**
- ð Koyu/aÃ§Ä±k tema + dinamik renkler

## KullanÄ±m
1. **TV'de:** FileBeam > "Al" â QR + PIN ekranda gÃ¶rÃ¼nÃ¼r
2. **Telefonda:** FileBeam > "GÃ¶nder" â cihazÄ± listeden seÃ§ **veya** QR'Ä± okut **veya** IP+PIN gir
3. DosyalarÄ± seÃ§ â **GÃNDER**

## Ä°mzalÄ± APK (GitHub Actions)
Push sonrasÄ± Actions otomatik derler; Artifacts > `FileBeam-release-apk`. KalÄ±cÄ± imza iÃ§in README'deki 4 secret'Ä± ekleyin:
`SIGNING_KEYSTORE_BASE64`, `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.

## Teknik
- Protokol: UDP 47812 (keÅfi beacon), TCP 47813 (aktarÄ±m). SatÄ±r baÅlÄ±ÄÄ±: `BEAM1|pin|dosyaadÄ±|boyut`
- QR iÃ§eriÄi: `filebeam://<ip>:<port>#<pin>`
- AynÄ± WiFi aÄÄ±nÄ± gerektirir; misafir aÄlarÄ±nda AP izolasyonu Ã§alÄ±Åmayabilir.
