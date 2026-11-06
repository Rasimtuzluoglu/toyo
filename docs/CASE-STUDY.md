# Case Study: Toyota Plaza Öztoprak Servis Yönetimi

## Özet

Toyota Plaza Öztoprak için geliştirilen bu sistem; müşteri, araç, iş emri, cari hesap,
tahsilat, randevu ve belge süreçlerini tek bir yerel ağ uygulamasında toplar.
İnternete açılmaz; veri işletmenin kendi bilgisayarında kalır.

## Problem

- Müşteri ve araç bilgileri Excel, kâğıt defter ve farklı kişilerin hafızasında dağınıktı.
- İş emri açmak, kalem eklemek ve tahsilatı ayrı ayrı takip etmek zaman kaybettiriyordu.
- Günlük açık işlerin, teslim edilecek araçların ve borçlu müşterilerin tek ekranda görünürlüğü yoktu.
- Yazdırılan belgelerde standart bir düzen yoktu.
- Verilerin buluta çıkması istenmiyordu.

## Çözüm

| Alan | Çözüm |
|---|---|
| Operasyon | Plaka ile hızlı iş emri, zorunlu manuel fiş numarası, tek tıkla tamamlama |
| Günlük akış | Kanban iş panosu, tek tık durum geçişi, sürükle-bırak |
| Finans | İş emrinden otomatik cari borç, tahsilat dağıtımı, cari ekstre, yaşlandırma |
| Randevu | Günlük ve haftalık takvim, randevudan iş emri açma |
| Belgeler | İş emri, makbuz, araç kabul ve teslim tutanağı; PDF ve ıslak/dijital imza |
| Müşteri ilişkisi | Bakım hatırlatmaları (180 gün / 10.000 km), WhatsApp metin paylaşımı |
| Güvenlik | Rol bazlı yetki, CSRF, audit log, yedekleme ve geri yükleme |
| Kurulum | Docker Compose, yerel ağ, isteğe bağlı demo ortamı |

## Teknik Yapı

- Java 21, Spring Boot 3.5, Thymeleaf, PostgreSQL 16
- Flyway migration (V16), Spring Security, Bean Validation
- Apache POI (Excel), OpenPDF (PDF), Noto Sans (Türkçe karakter)
- Docker / Docker Compose, yerel ağda çalışır
- 104 otomatik test (Testcontainers ile gerçek PostgreSQL)
- Modüler monolit; katmanlı servis/repository yapısı
- White-label: işletme adı, slogan, ana renk ve logo ayarlanabilir

## Ölçülecek Pilot Metrikleri

> Aşağıdaki değerler gerçek kullanım sonrası doldurulmalıdır; varsayımsal değer yazılmamalıdır.

| Metrik | Önce | Hedef / Sonra |
|---|---|---|
| İş emri açma süresi | ___ dk | ___ sn |
| Günlük açık iş görünürlüğü | Yok / dağınık | Tek pano |
| Tahsilat takibi | Elle / gecikmeli | Anlık cari bakiye |
| Kayıp/eksik iş emri | ___ | ___ |
| Müşteriye dönüş süresi | ___ | ___ |
| Günlük kullanıcı sayısı | ___ | ___ |

## Ekran Görüntüleri

- [Ana Panel](screenshots/dashboard.jpg)
- [Günlük İşler / Kanban](screenshots/board.jpg)
- [Giriş](screenshots/login.jpg)

## Sonuç

Sistem, Toyota Plaza Öztoprak'ta yerel ağda çalışacak şekilde kuruldu.
Kod tabanı test edilebilir, yedeklenebilir ve yeni bayi kurulumları için marka ayarlarıyla
tekrar kullanılabilir durumda.

## Referans İzni

Müşteri adı ve logosunun portföyde kullanımı için işletme sahibinden yazılı izin alınmalıdır.
İzin yoksa bu doküman "yetkili bir otomotiv servisi" olarak anonimleştirilmelidir.
