# 📡 FileBeam

Android telefonlar ve Android TV arasında aynı yerel Wi-Fi ağı üzerinden dosya aktarımı. Kotlin + Jetpack Compose + TCP/UDP.

## Özellikler
- Ham TCP üzerinden doğrudan cihazdan cihaza dosya aktarımı
- Android TV / Leanback başlatıcısı ve uzaktan kumanda desteği
- QR kodu ve 6 haneli PIN ile eşleştirme
- UDP broadcast ile yerel ağdaki alıcı cihazları keşfetme
- İlerleme, aktarım hızı ve tamamlanma bildirimi
- Android 10+ cihazlarda İndirilenler/MediaStore kaydı
- Koyu/açık tema

## Kullanım
1. TV'de FileBeam'i açıp **Al** ekranını başlatın.
2. Telefonda **Gönder** ekranında TV'yi seçin veya QR kodunu okutun.
3. Dosyaları seçip gönderin. Her iki cihaz da aynı yerel ağda olmalıdır.

## Kalıcı imzalı APK (önemli)
GitHub Actions geçici imza anahtarı üretmez. Geçici anahtar her derlemede değişebileceği için APK'nın önceki kurulumun üzerine güncellenmesini garanti etmez.

Repository Settings → Secrets and variables → Actions bölümüne aşağıdaki dört secret eklenmelidir:
- `SIGNING_KEYSTORE_BASE64`: aynı release keystore dosyasının Base64 içeriği
- `SIGNING_KEYSTORE_PASSWORD`: keystore parolası
- `SIGNING_KEY_ALIAS`: anahtar takma adı
- `SIGNING_KEY_PASSWORD`: anahtar parolası

**Mevcut kullanıcıların cihazlarında kurulu APK'nın imzasıyla aynı keystore kullanılmalıdır.** Eski keystore kaybolduysa farklı imzalı APK, Android tarafından mevcut uygulamanın üzerine güncelleme olarak kurulamaz; eski uygulamayı kaldırmak veri kaybına yol açabilir.

Actions başarılı olduğunda `FileBeam-release-apk` artifact'i içinde APK ve SHA-256 checksum bulunur. Workflow ayrıca APK imzasını doğrular.

## Teknik protokol
- UDP 47812: cihaz keşif beacon'ı
- TCP 47813: dosya aktarımı
- Aktarım başlığı: `BEAM2|pin|size|base64url(filename)`
- `size=-1`: sağlayıcı dosya boyutunu bildirmediğinde akış EOF'a kadar aktarılır.
- Başlık, ikili dosya içeriğini önceden okumamak için tamponlamasız okunur.
- Sabit uzunluklu dosyalarda eksik aktarım algılanır; başarısız aktarımın geçici kaydı silinir.

## Ağ notları
Misafir Wi-Fi ağları veya router üzerindeki AP/client isolation, cihazların birbirini görmesini engelleyebilir. UDP keşfi çalışmazsa QR veya manuel IP bağlantısı denenebilir.
