# Kullanıcı Rehberi

## Roller

| Rol | Yetkiler |
|---|---|
| **PATRON (İşletme Sahibi)** | Tüm modüller: müşteri/araç/iş emri, günlük işler, cari hesaplar, tahsilat, raporlar, kullanıcı yönetimi, ayarlar, yedekleme ve denetim kaydı |
| **MUHASEBE** | Tahsilat, cari hesap ve raporlar; müşteri/araç/iş emri kayıtlarını görüntüler, iş emri iptal edebilir; yeni müşteri/araç/iş emri oluşturamaz, durum ilerletemez |
| **SERVIS** | Müşteri, araç, iş emri ve kalem işlemleri, durum geçişleri ve günlük iş panosu; cari/tahsilat/rapor ekranlarına erişemez, iptal yapamaz |

Yetkiler sunucuda kontrol edilir. Menüde görünmeyen bir adrese doğrudan gidilirse erişim reddedilir.

## Giriş ve Çıkış

- Kullanıcı adı ve parola ile giriş yapılır.
- 2 saat işlem yapılmazsa oturum kapanır (`SESSION_TIMEOUT` ile değiştirilebilir).
- Sağ üstteki kullanıcı menüsünden **Parola Değiştir** veya **Çıkış Yap** seçilir.
- Hatalı girişler denetim kaydına yazılır; mesaj kullanıcı adının var olup olmadığını ele vermez.

### Kendi parolanızı değiştirme

Sağ üstteki kullanıcı menüsü → **Parola Değiştir**. Mevcut parola, yeni parola ve tekrarı girilir. Yeni parola en az 10 karakter olmalı; büyük harf, küçük harf ve rakam içermelidir.

## Rol Bazlı Giriş

Uygulama açıldığında rolünüze göre sade bir ekran gelir:

- **PATRON:** İşletme kokpiti (operasyon + finans).
- **MUHASEBE:** **Finans** özeti — bugünkü tahsilat, kalan alacak, borçlu müşteri, açık borçlar ve son tahsilatlar. Kanban, randevu ve bakım kartları bu ekranda gösterilmez.
- **SERVIS:** doğrudan **Bugün** panosuna düşer; finansal kart ve menüler görünmez.

Sol menü role göre kısalır:

- **SERVIS:** Bugün, Randevular, Müşteriler, Araçlar, İşler
- **MUHASEBE:** Özet, İş Emirleri, Cari Hesaplar, Tahsilatlar, Raporlar
- **PATRON:** tüm modüller

Telefonda alt sabit menü vardır; en sık kullanılan ekranlara tek dokunuşla geçilir. Sunum turu ve hızlı komut, sağ üstteki kullanıcı menüsündedir.

## Günlük İşler / Bugün

**Günlük İşler** ekranı, servisin her sabah bakacağı tek listedir. SERVIS rolünde ekranın adı **Bugün** olur.

- **Devam eden işler:** `Bekliyor` ve `İşlemde` durumundaki tüm işler her gün otomatik olarak listelenir. İşin kaç gündür açık olduğu (“Bugün açıldı”, “Dünden devir”, “3 günden beri açık”) gösterilir.
- **Bugün biten işler:** `Tamamlandı` veya `Teslim Edildi` durumuna bugün geçen işler görünür. Ertesi gün bu listenin dışında kalır.
- Bitmemiş iş ertesi gün de listede görünmeye devam eder; iş kapanana kadar devreder.
- İş tamamlandığında veya teslim edildiğinde “bitti” sayılır; iki durum da ertesi gün panodan kalkar.
- Üstteki üç sayaç **Bekleyen / İşlemde / Teslimata hazır** durumunu gösterir; tıklayınca ilgili kolona gidilir.
- Satırdaki **İşleme Al / Tamamla / Teslim Et** düğmeleriyle detaya girmeden durum ilerletilebilir.
- Kartlar sürüklenerek de bir sonraki aşamaya taşınabilir; tablette düğmeler kullanılabilir.
- Üstteki arama alanıyla **plaka, müşteri adı veya iş emri numarası** ile pano filtrelenir.
- Yaş filtresi (**Tümü / Bugün / Dünden / 2+ gün**) tek **Filtre** menüsündedir.
- Telefonda pano **Bekliyor / İşlemde / Tamamlandı / Teslim Edildi** sekmelerine ayrılır; aynı anda tek sütun görünür.
- Tablet ve masaüstünde tüm sütunlar birlikte görünür.

## Randevular

**Randevular** ekranı günlük planı gösterir; önceki/sonraki gün ve **Bugün** düğmeleriyle gezinilir.

- PATRON ve SERVIS yeni randevu oluşturabilir, düzenleyebilir, “Geldi” veya “İptal” olarak işaretleyebilir.
- Randevu için müşteri, araç, tarih-saat ve not girilir.
- Aynı araç için aynı saatte ikinci aktif randevu açılamaz.
- Satırdaki **İş Emri Aç** düğmesi randevuyu otomatik **Geldi** yapar ve plakası ön dolu **Hızlı İş Emri** ekranını açar; müşteri/araç bilgileri otomatik gelir.
- Liste tablet ve telefonda kart görünümüne döner.
- **Haftalık** düğmesiyle 7 günlük takvim görünümüne geçilir; gün başlığındaki **+** ile o güne randevu eklenir.

## Müşteriler

### Yeni müşteri

1. **Müşteriler → Yeni Müşteri**
2. Tür seçin: Bireysel / Firma
3. Ad veya firma unvanı, telefon zorunludur.
4. E-posta, adres, vergi bilgileri ve açıklama isteğe bağlıdır.
5. Kaydedin.

Telefon numarası normalize edilir (boşluk/tire/`+90`/`0` farkları giderilir). **Aynı telefon + aynı isim** ile ikinci kayıt açılamaz. Aynı telefona farklı isimle kayıt (aile bireyleri) mümkündür.

### Müşteri detayı

- İletişim ve vergi bilgileri
- Müşteriye bağlı araçlar
- Son iş emirleri
- PATRON/MUHASEBE için cari bakiye, açık borç, cari ekstre, tahsilat ve manuel borç bağlantıları

### Aktif/pasif ve silme

- Pasif müşteriye yeni araç veya yeni iş emri açılamaz. Mevcut kayıtlar ve cari hareketler korunur.
- Müşteri veya araç detayında **PATRON** için **Pasife Al** düğmesi vardır. İşlem finansal kayıtları yok etmez; kaydı ve bağlı araçları pasife alır. Geçmiş iş emirleri, tahsilatlar ve cari hareketler korunur.
- Pasif kayıt detayında **Geri Al** düğmesiyle müşteri/araç yeniden aktifleştirilir; müşteri geri alınınca araçları da geri alınır.
- Listeler varsayılan olarak yalnızca **aktif** kayıtları gösterir. Pasifleri görmek için **Durum → Tümü** veya **Pasif** seçin.
- Durum değişikliği yalnızca PATRON yetkisindedir. Yeni müşteri/araç kaydı PATRON ve SERVIS tarafından açılabilir; MUHASEBE yalnızca görüntüler.

## Araçlar

### Yeni araç

- Araç sahibi (müşteri) zorunludur.
- Plaka zorunludur; normalize edilerek saklanır ve **benzersizdir** (`34 ABC 123` = `34abc123`).
- Şasi numarası (VIN) girilirse benzersiz olmalıdır.
- Kilometre negatif olamaz ve düzenlemede geriye alınamaz.

### Sahiplik değişikliği

Araç başka müşteriye devredilirse:

- Araç sahibi güncellenir.
- Sahiplik geçmişi kaydı oluşur.
- **Geçmiş iş emirleri eski müşteri kaydında kalır.** Böylece eski müşterinin servis ve cari geçmişi bozulmaz.
- Yeni iş emirleri güncel araç sahibiyle açılır.

### Servis geçmişi

Araç detayında son iş emirleri, işlem yapılan müşteri, durum ve tutar görünür. Son servis tarihi tamamlanmış/teslim edilmiş iş emirlerinden hesaplanır.

## İş Emirleri

### Yeni iş emri (tek ekran)

1. **İş Emirleri → Yeni İş Emri**
2. **Plaka, müşteri veya telefon** alanına plakayı yazın; müşteri adı/telefonu da yazabilirsiniz. Eşleşen araçlar tahmin listesinde çıkar.
3. Kayıtlı plakada müşteri ve araç kartı otomatik gelir. Plaka kayıtlı değilse **ad soyad** ve **telefon** alanları açılır; marka/model opsiyoneldir.
4. **Fiş numarası** alanını girin. Bu alan **zorunludur**; aynı numara ikinci kez kullanılamaz.
5. **Müşteri ne dedi?** alanına şikâyeti yazın.
6. **Yapılacak iş** alanına işlemi yazın veya hazır seçeneklerden birine basın.
7. **Kim ilgileniyor?** alanına sorumlu kişinin adını yazın.
8. **Kaydet** deyin. İş emri `Bekliyor` durumunda açılır.

- **Kilometre** ve **ekip notu** isteğe bağlıdır; **Gelişmiş alanlar** bölümündedir.
- **Düzenle** ekranı da sadeleştirildi: fiş no, sorumlu, şikâyet ve yapılan işlemler ana bölümde; müşteri/araç, açılış tarihi, tespit ve ekip notu **Gelişmiş alanlar** altında.
- “Tespit edilen arıza”, “yapılan işlemler” ve işçilik/parça kalemleri kayıttan sonra detay ekranında girilir.
- “Yapılacak iş” iş emrine hizmet kalemi olarak eklenir; fiyatı detaydan güncellenir.
- Plaka daha önce açılmışsa mevcut müşteri ve araç yeniden kullanılır; mükerrer kayıt açılmaz.

### Hızlı iş emri ve tek tık tamamlama

**İş Emirleri → Hızlı İş** ekranı, işi hemen bitirmek içindir:

1. **Fiş numarasını** girin; zorunludur.
2. **Plaka, müşteri veya telefon** alanına plakayı yazın; eşleşen araçlar tahmin listesinde çıkar.
3. Yeni plakada ad soyad ve telefon alanları açılır; bu iki alan zorunludur.
4. **Kim ilgileniyor?** alanına sorumlu kişinin adını yazın.
5. Hazır işlem düğmelerinden birini seçin veya yapılan işlemi yazın; tutarı girin.
6. **Kaydet** veya **Kaydet ve Tamamla** deyin.

**Kaydet ve Tamamla**, iş emrini oluşturur, işlemi ekler, durumu `Tamamlandı` yapar ve tutar varsa cari borcu otomatik oluşturur. Kayıt sonrası ekranda **Tahsilat Al**, **Teslim Tutanağı**, **Yeni İş Emri** ve **Yazdır** düğmeleri görünür.

### İş emri numarası

Yeni iş emrinde **Fiş numarası** zorunludur ve **otomatik üretilmez**; numarayı Excel’den kopyalayıp yapıştırabilirsiniz. Aynı numara ikinci kez kullanılamaz. Borçlandırılmış iş emrinin numarası değiştirilemez.

> Müşteri ile aracın sahibi aynı olmalıdır. Farklı müşterinin aracıyla iş emri açılamaz.

### Kalem ekleme

- Tip: Hizmet / Parça
- Açıklama, miktar, birim fiyat
- Satır tutarı ve genel toplam sunucuda `BigDecimal` ile hesaplanır.
- Kalem ekleme/düzenleme yalnızca **Bekliyor** veya **İşlemde** durumunda ve borçlandırılmamış iş emrinde yapılır.
- **Hazır kalem** düğmeleri (motor yağı, filtre, fren, klima vb.) açıklama ve tipi forma doldurur; fiyatı siz belirlersiniz.

### Hızlı durum işlemleri

İş emri detayında ve günlük panoda mevcut duruma uygun **İşleme Al**, **Tamamla**, **Teslim Et** ve **İptal Et** düğmeleri gösterilir. Rutin geçişler tek tıkla yapılır; ayrı onay penceresi yoktur. “Tamamla” işlemi tutar varsa cari borcu otomatik oluşturur.

- Tamamlamadan sonra ekranın sağ altında **Geri Al** bildirimi çıkar. Son **10 dakika** içinde tamamlama geri alınabilir; borç varsa ters kayıt oluşturulur, kayıt silinmez.
- **PATRON/MUHASEBE** “Tamamla” yanındaki seçenekten **Cari / Nakit / Kart** seçebilir. Nakit veya kart seçilirse tahsilat aynı işlemde kaydedilir.
- **Teslim Et** tek tıktır; imza gerekmiyorsa doğrudan teslim edilir, teslim tutanağı istenirse sonradan kaydedilir.
- **İşleme Al / Tamamla / Teslim Et:** PATRON ve SERVIS.
- **İptal Et:** PATRON ve MUHASEBE. İptalde onay penceresi vardır.
- MUHASEBE iş emrini görüntüleyebilir ve iptal edebilir; yeni iş emri açamaz, kalem ekleyemez veya durumu ilerletemez.

### Durumlar

| Durum | Anlamı |
|---|---|
| **Bekliyor** | İş emri açıldı, işleme başlanmadı |
| **İşlemde** | Araç üzerinde çalışılıyor |
| **Tamamlandı** | İş bitti; tutar > 0 ise cari borç oluştu |
| **Teslim Edildi** | Araç müşteriye teslim edildi |
| **İptal** | İş emri iptal edildi; borç varsa ters kayıt oluştu |

Geçişler:

```text
Bekliyor → İşlemde → Tamamlandı → Teslim Edildi
    └──────────┴──────────┴──────→ İptal
```

- Tamamlandığında borç otomatik oluşur; aynı iş emri ikinci kez borçlandırılamaz.
- İptal yalnızca PATRON/MUHASEBE tarafından yapılır; SERVIS iptal edemez.
- İş emrine bağlı **aktif tahsilat** varsa önce tahsilat iptal edilmelidir.
- İptal, finansal kaydı silmez; ters kayıt oluşturur.

### Yazdırma

İş emri detayında **Yazdır** ile müşteri/araç bilgileri, kalemler, toplamlar ve imza alanları yazdırılır. Çıktının başlığında Ayarlar’da tanımlanan işletme bilgileri kullanılır. **PDF** düğmesi aynı belgeyi doğrudan `.pdf` olarak indirir; tahsilat makbuzunda da **PDF İndir** bulunur.

**WhatsApp** düğmesi, iş emri numarası, araç, durum ve toplam tutarı içeren hazır bir metni WhatsApp’ta paylaşmak üzere açar. Yerel ağ adresi müşteri tarafında açılamayacağı için bağlantı yerine metin paylaşılır.

### Araç kabul formu

**İş emri detayı → Araç kabul formu**

- Yakıt seviyesi, hasar/mevcut durum notu ve araçta bırakılan eşyalar kaydedilir.
- Kilometre iş emrinden alınır.
- Kaydettikten sonra **Yazdır** veya **PDF** ile ıslak imza alanlı form alınır.
- Tablet veya masaüstünde dijital imza alanına parmak/fare ile imza çizilebilir; imza kayıtla saklanır ve çıktıda görünür.
- PATRON ve SERVIS kaydedebilir; MUHASEBE görüntüleyebilir.

### Teslim tutanağı

**İş emri detayı → Teslim tutanağı**

- Yalnızca iş emri `Tamamlandı` veya `Teslim Edildi` durumundayken kaydedilir.
- Üst bölümde **iş emri tutarı** ve **müşteri kalan bakiyesi** görünür; bakiye varsa **Tahsilat Al** bağlantısı çıkar.
- Teslim alan kişi, kilometre, yakıt seviyesi ve not girilir.
- Tutanakta yapılan işlemler, genel toplam ve imza alanları yer alır.
- **Yazdır** veya **PDF** ile müşteri imzası alınır.
- Dijital imza alanına tablet/masaüstünde imza çizilebilir; imza tutanak çıktısında görünür.

## Cari Hesaplar

### Bakiye mantığı

```text
Bakiye = Borç toplamı − Alacak toplamı
```

- **Borç**: müşterinin işletmeye borcu artar.
- **Alacak**: müşterinin borcu azalır (tahsilat).
- Pozitif bakiye = müşteri borçlu.
- Negatif bakiye = müşteri alacaklı (avans/fazla ödeme).

### Cari ekstre

**Cari Hesaplar → müşteri → Ekstre**

- Tarih aralığı seçilebilir.
- Açılış bakiyesi, dönem borcu, dönem alacağı ve kapanış bakiyesi gösterilir.
- Ters kayıtlar ayrıca işaretlenir.
- **Yazdır**, **Excel** ve **CSV** (UTF-8 BOM, `;` ayırıcı) desteklenir.

### Manuel borç

İş emri dışında oluşan bir alacak için **Manuel Borç** kullanılır. Bu ekran yalnızca PATRON yetkisindedir; MUHASEBE yalnızca tahsilat girer. Gelecek tarihli kayıt oluşturulamaz. Kayıt silinmez; düzeltme ters kayıtla yapılır.

## Tahsilatlar

### Tahsilat oluşturma

1. **Tahsilatlar → Yeni Tahsilat**
2. Müşteri seçin → **Açık Borçları Getir**
3. Tarih, tutar, ödeme yöntemi (Nakit/Banka/Kart), açıklama girin.
4. **Tek açık borç varsa tutar ve dağıtım otomatik doldurulur**; dağıtım tablosu gizlenir, yerine tek satırlık borç özeti gelir. Kontrol edip doğrudan kaydedebilirsiniz. Birden fazla borçta dağıtım tablosu görünür; satırları doldurun veya **Kalanı Dağıt** / **Tüm Açık Borcu Getir** düğmelerini kullanın.
5. Ekranın altındaki özet, dağıtılan tutarı ve varsa avans olarak kalacak tutarı gösterir.
6. Kaydedin.

Boş bırakılan borca dağıtım yapılmaz; dağıtılmayan kısım avans/alacak olarak cari hesaba işlenir.

Kurallar:

- Dağıtım toplamı tahsilat tutarını aşamaz.
- Bir borca kalan tutarından fazla dağıtım yapılamaz.
- Dağıtım yapılmayan kısım avans/alacak olarak cariye işlenir.
- Aynı formun tekrar gönderilmesi tek kullanımlık form belirteciyle engellenir.
- Gelecek tarihli tahsilat kabul edilmez.

### Tahsilat iptali

- Yalnızca PATRON/MUHASEBE.
- Tahsilat `İptal` durumuna alınır; dağıtımlar iptal edilir.
- Cariye ters **borç** kaydı işlenir.
- Orijinal alacak hareketi ve dağıtım satırları silinmez.

### Makbuz

Tahsilat detayında **Makbuz** ile yazdırılabilir. Makbuzda müşteri, tutar, yöntem, dağıtımlar ve imza alanları bulunur.

## Raporlar (PATRON/MUHASEBE)

| Rapor | İçerik |
|---|---|
| Tahsilat raporu | Tarih aralığında gün/yöntem bazında adet ve tutar; CSV + Excel |
| Müşteri bakiyeleri | Borçlu/alacaklı/dengede; CSV + Excel |
| Cari hareket dökümü | Müşteri + tarih aralığı; CSV + Excel |
| İş emri durum raporu | Durum bazında adet/tutar + liste; CSV + Excel |
| Hizmet ve parça tutarları | Dönem kalem toplamları (iptaller hariç); CSV + Excel |
| Cari yaşlandırma | Açık borçların 0-30 / 31-60 / 61+ gün dağılımı; CSV + Excel |
| Bakım hatırlatmaları | 180 gün veya 10.000 km geçen araçlar; CSV + Excel |
| Araç servis geçmişi | Araç + tarih aralığı, son servis tarihi; CSV + Excel |

Raporlar gerçek veritabanı kayıtlarından üretilir; sabit/sahte veri yoktur.

Tarih filtrelerinde **Bugün / Bu Hafta / Bu Ay / Geçen Ay** ön ayarları tek tıkla uygulanır.

## Klavye Kısayolları

- `/` : Üst arama kutusuna git
- `Ctrl+K` (veya `Cmd+K`) : Hızlı komut paleti
- `Alt+N` : Yeni iş emri
- `Alt+B` : Günlük işler panosu

## Sunum Turu

Sağ üstteki **oynat** simgesi 6 adımlık sunum turunu başlatır: kokpit → kanban → plaka ile iş emri → haftalık takvim → bakım hatırlatmaları → özet.

## Dışa Aktarma

- Müşteri, araç, iş emri ve tahsilat listelerinde **Dışa Aktar** menüsünden **Excel (.xlsx)** veya **CSV** indirilebilir.
- İndirme, ekranda uygulanan filtreleri dikkate alır.
- CSV dosyaları UTF-8 BOM ve `;` ayırıcı ile üretilir; Excel'de Türkçe karakterler bozulmaz.
- Excel dosyalarında başlık satırı biçimlendirilir, tutarlar `₺` biçiminde sayısal hücre olarak yer alır.
- Rapor ve ekstre ekranlarında hem **CSV** hem **Excel** düğmesi bulunur.
- İş emri ve tahsilat makbuzu ekranlarında **PDF İndir** ile belge doğrudan PDF olarak kaydedilir.
- Dışa aktarma yetkisi ekranın yetkisiyle aynıdır: tahsilat/rapor dosyaları yalnızca PATRON ve MUHASEBE tarafından indirilebilir.

## Kullanıcılar (PATRON)

- Yeni kullanıcı oluşturma (PATRON/MUHASEBE/SERVIS)
- Ad soyad, rol ve aktiflik güncelleme
- Parola sıfırlama
- Kullanıcı silinmez; pasife alınır
- Kendi hesabınızı pasife alamaz veya kendi PATRON rolünüzü kaldıramazsınız
- Sistemde en az bir aktif PATRON kalmalıdır

## Ayarlar ve Yedekleme (PATRON)

- **İşletme bilgileri:** işletme adı, slogan, telefon, e-posta, adres, vergi dairesi/no ve ana renk. Bu bilgiler giriş ekranı, menü, iş emri, makbuz ve ekstre çıktılarında görünür.
- **Logo:** PNG/JPEG/WebP, en fazla 2 MB. Yüklenen logo giriş ekranı, menü, belgeler ve PDF’lerde kullanılır; kaldırılabilir.
- Yedek klasörü, saklama süresi ve cron bilgisi
- Bilgisayar 02:00'de kapalıysa sonraki açılışta **telafi yedeği** alınır
- **Sunucuda Yedekle** ve **Yedek Al ve İndir** (tarayıcıda istediğiniz klasörü seçerek kaydetme)
- Geçmiş yedekleri **İndir** ile bilgisayara alma
- Son denetim kayıtları
- Tüm denetim kayıtları: işlem türüne göre filtreleme
- **Yazılım Güncellemesi:** GitHub’daki son commit’i kontrol eder; host ajanı çalışıyorsa **Güncelle ve Yeniden Başlat** ile Docker imajı yeniden oluşturulur. Güncelleme sırasında uygulama kısa süre erişilemez ve oturumlar kapanır.

Yedekleme ve geri yükleme adımları için `docs/BACKUP-RESTORE.md`.
Güncelleme ajanı kurulumu için `docs/INSTALLATION.md` → *Yazılım güncelleme ajanı*.

## Denetim Kaydı

Şu işlemler izlenir:

- Giriş/çıkış ve başarısız girişler
- Kullanıcı oluşturma/güncelleme/parola sıfırlama
- Müşteri ve araç değişiklikleri, araç sahibi değişikliği
- İş emri oluşturma, güncelleme, kalem ve durum değişiklikleri
- Borç oluşturma, iş emri borçlandırma/ters kayıt
- Tahsilat oluşturma/iptal
- Yedekleme başlatma/sonuçları

Parola, oturum belirteci ve gereksiz kişisel veri loglanmaz.

## Sık Sorulanlar

**Bir iş emrini sildim, geri gelir mi?**
İş emirleri silinmez. İptal edilir; finansal kayıt varsa ters kayıt oluşur. Yanlış kayıt için PATRON veya MUHASEBE ile iptal sürecini kullanın.

**Tahsilatı düzelttim, bakiye neden değişmedi?**
Tahsilat iptali cariye ters borç kaydı işler. Orijinal alacak silinmediği için ekstrede iki hareket görünür; net bakiye doğrudur.

**Aynı plakayı kaydedemiyorum.**
Plaka normalize edilir ve benzersizdir. Mevcut aracı arayın; gerekirse sahibini değiştirin.

**Müşteriyi sildim, geçmişi kaybolur mu?**
Müşteri silinmez, pasife alınır. Geçmiş iş emirleri ve cari hareketler korunur.

**SERVIS neden cari ekranı göremiyor?**
Finansal veriler PATRON/MUHASEBE ile sınırlıdır. SERVIS müşteri, araç ve iş emri operasyonunu yürütür.

**Dün tamamlanan iş neden bugün panoda yok?**
Tamamlanan veya teslim edilen iş “bitmiş” sayılır ve ertesi gün günlük panodan kalkar. İş emri kaydı İş Emirleri listesinde ve cari geçmişte durmaya devam eder.
