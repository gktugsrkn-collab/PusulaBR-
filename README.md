# pusulaBRÇ 2.0.0

Kotlin + Jetpack Compose ile hazırlanmış, çevrimdışı çalışan kişisel borç ve bütçe yönetimi Android uygulaması.

## Özellikler
- Kredi, taşıt/konut/ihtiyaç kredisi, kredi kartı, KMH/avans hesap ve diğer borç türleri
- Borç ekleme, düzenleme, silme; ödenen taksit sayısını takip etme
- Standart eşit taksitli kredi ödeme tahmini (yıllık nominal faiz / 12)
- İlk ödeme tarihinden itibaren aylık takvim ve kalan taksit listesi
- Gelir/gider kaydı, kategoriler, düzenleme ve silme
- Türkçe ses tanıma ile işlem açıklaması oluşturma (Android cihazın konuşma tanıma hizmetini kullanır)
- Aylık bütçe, borç yükü, net nakit akışı ve borç türü raporları
- Kural tabanlı finans asistanı ve motivasyon önerileri
- Açık/koyu tema, sistem temasını takip etme
- DataStore ile cihazda kalıcı veri saklama
- JSON yedek dışa/içe aktarma
- GitHub Actions ile APK üretimi

## GitHub APK
1. ZIP'i çıkarın ve içeriğini bir GitHub deposunun köküne yükleyin.
2. Actions sekmesinden **Android APK** workflow'unu çalıştırın.
3. Tamamlanan çalışmanın `pusulaBRC-v2-debug-apk` artifact'ini indirin.
4. Bu artifact debug imzalı test APK'sıdır. Google Play dağıtımı için ayrıca release signing yapılandırması gerekir.

Yerel derleme: JDK 17 ve Android SDK kurulu olmalı; `gradle :app:assembleDebug`.

## Hesaplama sınırları
- Kredi taksiti, standart eşit taksitli anüite formülünden tahmin edilir. Banka masrafları, vergi, sigorta, değişken oran ve özel ödeme planları dahil değildir.
- Kredi kartı ve KMH faiz/asgari ödeme kuralları bankaya, döneme ve mevzuata göre değişir. Bu uygulama bunları otomatik olarak kesin hesaplamaz; manuel aylık ödeme alanı kullanılabilir.
- Takvim, girilen ilk ödeme tarihi ve vade üzerinden planlanan taksitleri gösterir; banka ekstresi/otomatik ödeme teyidi değildir.
- Sesli giriş Android'in cihazdaki konuşma tanıma servisine bağlıdır ve cihaz/bağlantı durumuna göre çalışmayabilir.

## Entegrasyonlar
Bu sürüm banka hesabına bağlanmaz, ödeme yapmaz, otomatik banka bildirimi almaz ve gerçek bulut AI sohbeti sunmaz. Bu özellikler için güvenli backend, sağlayıcı sözleşmeleri/API anahtarları, kullanıcı onayı ve güvenlik incelemesi gerekir.

## Gizlilik
Veriler Android DataStore ile uygulamanın özel alanında saklanır. JSON yedek dışa aktarıldığında kullanıcı tarafından seçilen konuma yazılır; yedek dosyasını güvenli saklayın. Uygulamayı kaldırmak yerel veriyi silebilir; kaldırmadan önce yedek alın.
