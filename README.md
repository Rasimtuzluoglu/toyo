# Toyota Plaza Ã–ztoprak Â· Servis YÃ¶netimi

Toyota Plaza Ã–ztoprak iÃ§in geliÅŸtirilen bu uygulama; mÃ¼ÅŸteri, araÃ§, iÅŸ emri, cari hesap,
tahsilat ve randevu sÃ¼reÃ§lerini **tek bir yerel aÄŸ uygulamasÄ±nda** toplar. Veri iÅŸletmenin
kendi bilgisayarÄ±nda kalÄ±r; bulut hesabÄ±, dÄ±ÅŸ servis veya port yÃ¶nlendirme gerekmez.

> GÃ¼ncel durum: Ã¼retim ve demo ortamÄ± Ã§alÄ±ÅŸÄ±yor Â· **87 otomatik test** Â· yerel CI + Docker imaj doÄŸrulamasÄ±

## Ã–ne Ã‡Ä±kanlar

**Operasyon**
- GÃ¼nlÃ¼k iÅŸ panosu: bitmemiÅŸ iÅŸler her gÃ¼n devreder, bitenler ertesi gÃ¼n listeden kalkar.
- Kanban Ã¼zerinde sÃ¼rÃ¼kle-bÄ±rak veya tek tÄ±kla durum ilerletme; plaka/mÃ¼ÅŸteri aramasÄ±.
- YoÄŸun gÃ¼nler iÃ§in kompakt kart, sabit yÃ¼kseklikte kayan kolonlar, yaÅŸ filtreleri ve katlanabilir biten kolonlarÄ±.
- Telefonda **Bekliyor / Ä°ÅŸlemde / TamamlandÄ± / Teslim Edildi** sekmeleri.
- Plaka odaklÄ± hÄ±zlÄ± iÅŸ emri: kayÄ±tlÄ± plakada mÃ¼ÅŸteri/araÃ§ otomatik gelir.
- HazÄ±r iÅŸlem dÃ¼ÄŸmeleri, tek ekranda **Kaydet ve Tamamla**.
- Randevuyu otomatik â€œGeldiâ€ yapÄ±p plakasÄ± Ã¶n dolu hÄ±zlÄ± iÅŸ emrini aÃ§ma.
- GÃ¼nlÃ¼k ve haftalÄ±k randevu takvimi; bakÄ±m hatÄ±rlatmalarÄ± (180 gÃ¼n / 10.000 km).
- Ctrl+K komut paleti, 6 adÄ±mlÄ±k sunum turu, manuel/otomatik iÅŸ emri no ve hazÄ±r kalem ÅŸablonlarÄ±.

**Finans**
- Ä°ÅŸ emri tamamlanÄ±nca otomatik cari borÃ§; iptalde ters kayÄ±t (silme yok).
- Tamamla + tahsilat: **Cari / Nakit / Kart**.
- TahsilatlarÄ± birden fazla borca daÄŸÄ±tma, tahsilat iptali, cari ekstre ve yaÅŸlandÄ±rma.
- Excel (.xlsx), CSV ve PDF Ã§Ä±ktÄ±larÄ±; dashboard ve raporlar.

**Belgeler ve marka**
- AraÃ§ kabul formu, teslim tutanaÄŸÄ±, Ä±slak imza Ã§Ä±ktÄ±sÄ± ve dijital imza.
- White-label: iÅŸletme adÄ±, slogan, ana renk ve logo; giriÅŸ, menÃ¼, belgeler ve PDFâ€™lerde geÃ§erli.
- Ä°ÅŸ emri ve tahsilat makbuzu iÃ§in doÄŸrudan PDF indirme.

**GÃ¼venlik, veri ve bakÄ±m**
- PATRON / MUHASEBE / SERVIS rolleri; yetkiler sunucu tarafÄ±nda uygulanÄ±r.
- CSRF, BCrypt (strength 12), oturum sabitleme, 5 hatalÄ± giriÅŸte 15 dakika kilit.
- Ä°ÅŸlem denetim kaydÄ±: kullanÄ±cÄ±, zaman, IP ve detay.
- Tek seferlik kurulum; kullanÄ±cÄ±lar silinse bile `/setup` yeniden aÃ§Ä±lmaz.
- GÃ¼nlÃ¼k otomatik yedek + gÃ¼ncelleme Ã¶ncesi yedek; yedek indirme ve geri yÃ¼kleme tatbikatÄ±.
- Ayarlar â†’ GitHub son commit kontrolÃ¼ ve host ajanÄ±yla Docker Ã¼zerinden gÃ¼ncelleme.

Kapsam dÄ±ÅŸÄ±: e-fatura, banka/kart Ã¶deme kuruluÅŸu entegrasyonu, otomatik SMS/WhatsApp,
bulut dÄ±ÅŸ eriÅŸim, Ã§ok iÅŸletmeli SaaS, tam stok/depo, native mobil uygulama, tam muhasebe sistemi.

## Ekran GÃ¶rÃ¼ntÃ¼leri

| Ana Panel | GÃ¼nlÃ¼k Ä°ÅŸler |
|---|---|
| ![Ana Panel](docs/screenshots/dashboard.jpg) | ![GÃ¼nlÃ¼k Ä°ÅŸler](docs/screenshots/board.jpg) |

![GiriÅŸ](docs/screenshots/login.jpg)

## HÄ±zlÄ± BaÅŸlangÄ±Ã§ (Docker)

1. Ortam dosyasÄ±nÄ± oluÅŸturun:

   ```bash
   cp .env.example .env
   ```

   Windows PowerShell: `Copy-Item .env.example .env`

2. `.env` iÃ§inde en az `POSTGRES_PASSWORD` deÄŸerini deÄŸiÅŸtirin. Ä°lk iÅŸletme sahibini
   ortam deÄŸiÅŸkeninden oluÅŸturmak isterseniz `INITIAL_ADMIN_USERNAME`,
   `INITIAL_ADMIN_PASSWORD` ve `INITIAL_ADMIN_FULL_NAME` deÄŸerlerini de doldurun.
   **VarsayÄ±lan parola yoktur.**

3. Servisleri baÅŸlatÄ±n:

   ```bash
   docker compose up -d --build
   docker compose ps
   docker compose logs -f app
   ```

4. TarayÄ±cÄ±dan `http://localhost:18080` adresini aÃ§Ä±n. Ä°lk kurulumda `/setup` ekranÄ±ndan
   PATRON (iÅŸletme sahibi) hesabÄ±nÄ± oluÅŸturun. Bu ekran **yalnÄ±zca bir kez** aÃ§Ä±lÄ±r.

5. Durdurma:

   ```bash
   docker compose down
   docker compose restart app
   ```

   > `docker compose down -v` **volumeâ€™larÄ± siler** ve veritabanÄ± kayÄ±tlarÄ±nÄ± geri
   > dÃ¶ndÃ¼rÃ¼lemez biÃ§imde kaybettirir. Normal kullanÄ±mda `-v` kullanmayÄ±n.

PostgreSQL portu ana compose dosyasÄ±nda bilerek yayÄ±nlanmaz. Yerel geliÅŸtirmede veritabanÄ±na
host Ã¼zerinden eriÅŸmek iÃ§in:

```bash
docker compose -f docker-compose.yml -f compose.dev.yml up -d db
```

Bu komut portu yalnÄ±zca `127.0.0.1:5432` Ã¼zerinde aÃ§ar.

## Demo / Sunum OrtamÄ±

GerÃ§ek veriyi kirletmeden sistemi gÃ¶stermek iÃ§in ayrÄ± veritabanÄ± ve port kullanÄ±lÄ±r.

```bash
docker compose up -d --build
docker compose -f docker-compose.demo.yml up -d
```

- Adres: `http://localhost:18081`
- KullanÄ±cÄ±: `demo` Â· Parola: `DemoPass1234`
- Veri: 12 mÃ¼ÅŸteri, 12 Toyota araÃ§, 12 iÅŸ emri, 5 tahsilat, 4 randevu.
- Demo verisi yalnÄ±zca `demo` profilinde ve boÅŸ veritabanÄ±nda oluÅŸturulur; Ã¼retim verisine dokunmaz.
- SÄ±fÄ±rlama: `docker compose -f docker-compose.demo.yml down -v` â†’ `... up -d`

### 5 dakikalÄ±k sunum akÄ±ÅŸÄ±

1. **GiriÅŸ** â†’ `demo / DemoPass1234`
2. **Ana Panel** â†’ aÃ§Ä±k iÅŸ, bugÃ¼nkÃ¼ tahsilat, alacak, randevu ve teslimata hazÄ±r araÃ§lar.
3. **GÃ¼nlÃ¼k Ä°ÅŸler** â†’ sekmeler/arama; bir iÅŸte **Cari/Nakit/Kart** seÃ§ip **Tamamla**; **Geri Al**.
4. **HÄ±zlÄ± Ä°ÅŸ Emri** â†’ `34 TP 1001` yazÄ±n; mÃ¼ÅŸteri/araÃ§ otomatik gelsin; **Kaydet ve Tamamla**.
5. **Randevular** â†’ **Ä°ÅŸ Emri AÃ§**; randevu â€œGeldiâ€ olur, plaka dolu hÄ±zlÄ± form aÃ§Ä±lÄ±r.
6. **Teslimata hazÄ±r** â†’ **Teslim Et**; teslim ekranÄ±nda bakiye ve **Tahsilat Al**.
7. **Tahsilat/Raporlar** â†’ makbuz PDFâ€™i, cari ekstre ve Excel/CSV indirme.
8. **Ayarlar** â†’ yedekleme ve **GÃ¼ncelleme** kartÄ±.

## Roller ve GÃ¼nlÃ¼k AkÄ±ÅŸ

| Rol | Kapsam |
|---|---|
| **PATRON** | TÃ¼m iÅŸlemler: operasyon, finans, raporlar, kullanÄ±cÄ±lar, ayarlar, yedekleme, gÃ¼ncelleme |
| **MUHASEBE** | Tahsilat, cari hesap, raporlar ve dashboard; mÃ¼ÅŸteri/araÃ§/iÅŸ emrini gÃ¶rÃ¼ntÃ¼ler, iÅŸ emrini iptal edebilir |
| **SERVIS** | MÃ¼ÅŸteri, araÃ§, iÅŸ emri ve gÃ¼nlÃ¼k akÄ±ÅŸ; finans ekranlarÄ±na eriÅŸemez, tahsilat alamaz, iptal edemez |

Ä°ÅŸ emri akÄ±ÅŸÄ±:

```text
Bekliyor â†’ Ä°ÅŸlemde â†’ TamamlandÄ± â†’ Teslim Edildi
    â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”´â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”´â”€â”€â”€â”€â”€â”€â†’ Ä°ptal (ters kayÄ±t)
```

- Tamamlamadan sonra 10 dakika iÃ§inde **Geri Al**; borÃ§ varsa ters kayÄ±t oluÅŸur.
- Tahsilat, iptal ve ters kayÄ±tlar denetim kaydÄ±na yazÄ±lÄ±r.

## Teknolojiler

| Katman | Teknoloji |
|---|---|
| Dil | Java 21 LTS |
| Ã‡atÄ± | Spring Boot 3.5.16 |
| Web | Spring MVC + Thymeleaf |
| Veri | Spring Data JPA / Hibernate, PostgreSQL 16 |
| DÄ±ÅŸa aktarma | Apache POI 5.5.1 (Excel .xlsx), UTF-8 BOM CSV |
| PDF | OpenPDF 2.2.2, Noto Sans (gÃ¶mÃ¼lÃ¼ TÃ¼rkÃ§e karakter desteÄŸi) |
| Migration | Flyway (V16) |
| GÃ¼venlik | Spring Security, BCrypt (strength 12), CSRF |
| UI | Bootstrap 5.3.3, Bootstrap Icons, Ã¶zel CSS |
| Test | JUnit 5, Spring Boot Test, MockMvc, Testcontainers (PostgreSQL 16) |
| Paketleme | Maven, Docker, Docker Compose |

## Yerel GeliÅŸtirme

```bash
# 1) PostgreSQL 16
docker compose -f docker-compose.yml -f compose.dev.yml up -d db

# 2) Uygulama
set DB_URL=jdbc:postgresql://localhost:5432/servisdefteri
set DB_USERNAME=servisdefteri
set DB_PASSWORD=<.env icindeki POSTGRES_PASSWORD>
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

PowerShell:

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/servisdefteri"
$env:DB_USERNAME="servisdefteri"
$env:DB_PASSWORD="..."
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

VarsayÄ±lan port `18080`â€™dir. Port doluysa `SERVER_PORT` (uygulama) veya `APP_PORT` (Docker)
ile deÄŸiÅŸtirin.

## Ortam DeÄŸiÅŸkenleri

| DeÄŸiÅŸken | AÃ§Ä±klama | VarsayÄ±lan |
|---|---|---|
| `POSTGRES_DB` | VeritabanÄ± adÄ± | `servisdefteri` |
| `POSTGRES_USER` | VeritabanÄ± kullanÄ±cÄ±sÄ± | `servisdefteri` |
| `POSTGRES_PASSWORD` | VeritabanÄ± parolasÄ± | Zorunlu |
| `APP_PORT` | Dockerâ€™da dÄ±ÅŸ port | `18080` |
| `APP_BIND_ADDRESS` | Dinlenecek aÄŸ arayÃ¼zÃ¼; `127.0.0.1` = yalnÄ±zca bu bilgisayar, `0.0.0.0` = yerel aÄŸ | `0.0.0.0` |
| `SERVER_PORT` | Uygulama portu | `18080` |
| `SESSION_TIMEOUT` | Oturum sÃ¼resi | `2h` |
| `DB_URL` | JDBC adresi | `jdbc:postgresql://localhost:5432/servisdefteri` |
| `DB_USERNAME` / `DB_PASSWORD` | Uygulama DB kullanÄ±cÄ±sÄ± | `servisdefteri` / zorunlu |
| `INITIAL_ADMIN_USERNAME` | Ä°lk PATRON kullanÄ±cÄ± adÄ± | BoÅŸ |
| `INITIAL_ADMIN_PASSWORD` | Ä°lk PATRON parolasÄ± | BoÅŸ |
| `INITIAL_ADMIN_FULL_NAME` | Ä°lk PATRON ad soyad | `Ä°ÅŸletme Sahibi` |
| `BACKUP_ENABLED` | ZamanlanmÄ±ÅŸ yedekleme | `true` |
| `BACKUP_DIR` | Yedek dizini | `./backups` (Docker: `/app/backups`) |
| `BACKUP_RETENTION_DAYS` | Yedek saklama sÃ¼resi | `14` |
| `BACKUP_CRON` | Yedekleme cron ifadesi | `0 0 2 * * *` |
| `PG_DUMP_PATH` | pg_dump yolu | `pg_dump` |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | Yedekleme baÄŸlantÄ±sÄ± | `localhost` / `5432` / `servisdefteri` |
| `APP_UPDATE_ENABLED` | Ayarlarâ€™daki gÃ¼ncelleme Ã¶zelliÄŸi | `true` |
| `APP_UPDATE_DIR` | Host ajanÄ±yla paylaÅŸÄ±lan klasÃ¶r | `./updates` (Docker: `/app/updates`) |
| `APP_UPDATE_REPO` | Ä°zlenecek GitHub deposu | `Rasimtuzluoglu/toyo` |
| `APP_UPDATE_GITHUB_TOKEN` | Ã–zel depo iÃ§in GitHub token | BoÅŸ |
| `GIT_COMMIT` | Docker imajÄ±na yazÄ±lan sÃ¼rÃ¼m etiketi | `unknown` |

`.env` dosyasÄ± Gitâ€™e eklenmez. Ã–rnek deÄŸerler `.env.example` iÃ§indedir.

## Yedekleme ve GÃ¼ncelleme

**Otomatik yedek**
- VarsayÄ±lan her gÃ¼n **02:00** (`BACKUP_CRON`), `pg_dump` custom format.
- 14 gÃ¼n saklanÄ±r (`BACKUP_RETENTION_DAYS`); Dockerâ€™da `servisdefteri-backups` volumeâ€™Ã¼nde.
- **Her yazÄ±lÄ±m gÃ¼ncellemesinden Ã¶nce** ayrÄ±ca `pre-update-...dump` alÄ±nÄ±r; yedek alÄ±namazsa gÃ¼ncelleme durur.
- Manuel **Sunucuda Yedekle** ve **Yedek Al ve Ä°ndir** (PATRON).
- Yedek aynÄ± bilgisayarda durur; disk arÄ±zasÄ±na karÅŸÄ± harici disk/NASâ€™a kopyalayÄ±n.
- AyrÄ±ntÄ±lar: [Yedekleme ve Geri YÃ¼kleme](docs/BACKUP-RESTORE.md) Â· tatbikat: `scripts/restore-drill.ps1`

**Ayarlar â†’ YazÄ±lÄ±m GÃ¼ncellemesi**
- **Kontrol Et**: GitHubâ€™daki son commitâ€™i gÃ¶sterir.
- **GÃ¼ncelle ve Yeniden BaÅŸlat**: host ajanÄ±na istek bÄ±rakÄ±r; ajan yedek alÄ±r,
  `git pull --ff-only origin main` yapar, `docker compose up -d --build` ile yeniden kurar
  ve saÄŸlÄ±k kontrolÃ¼ yapar.
- Ajan kurulumu (tek seferlik):

  ```powershell
  powershell -ExecutionPolicy Bypass -File .\scripts\install-update-agent.ps1
  ```

- GÃ¼ncelleme sÄ±rasÄ±nda uygulama 1â€“3 dakika eriÅŸilemez; oturumlar kapanÄ±r.
- Otomatik yakalama iÃ§in `UPDATE_AUTO=true` ortam deÄŸiÅŸkeniyle ajanÄ± baÅŸlatÄ±n.
- GÃ¼nlÃ¼kler: `updates/agent.log`, `updates/update.log`.

## Testler ve Yerel CI

```bash
mvn test
```

Testler gerÃ§ek PostgreSQL 16 Ã¼zerinde Testcontainers ile Ã§alÄ±ÅŸÄ±r; Docker eriÅŸimi gerekir.
Kapsam: cari bakiye/tutar hesaplarÄ±, durum geÃ§iÅŸleri, mÃ¼ÅŸteri-araÃ§-iÅŸ emri-tahsilat akÄ±ÅŸÄ±,
mÃ¼kerrer borÃ§/plaka/tahsilat kontrolleri, rol yetkileri, panonun devir/bitiÅŸ davranÄ±ÅŸÄ±,
plaka aramasÄ±, Flyway migration ve kÄ±sÄ±tlar, CSRF/form belirteci, tÃ¼m ana sayfalarÄ±n renderâ€™Ä±,
PDF/Excel/CSV Ã§Ä±ktÄ±larÄ±, araÃ§ kabul/teslim ve randevu akÄ±ÅŸÄ±.

Son Ã§alÄ±ÅŸtÄ±rma: **87 test, 0 hata** (`mvn clean package`). Rapor: `target/surefire-reports/`.

### Yerel CI

```powershell
# Testler + Docker imajÄ±
powershell -ExecutionPolicy Bypass -File .\scripts\ci-local.ps1

# Ä°steÄŸe baÄŸlÄ±: her push Ã¶ncesi otomatik Ã§alÄ±ÅŸsÄ±n
powershell -ExecutionPolicy Bypass -File .\scripts\install-ci-hook.ps1
```

- Docker Desktop aÃ§Ä±k olmalÄ±dÄ±r.
- Log: `logs/ci-local.log`.
- Pre-push hook, test veya Docker build baÅŸarÄ±sÄ±zsa pushâ€™u durdurur.
- KaldÄ±rmak iÃ§in `.git/hooks/pre-push` dosyasÄ±nÄ± silin.

## Proje YapÄ±sÄ±

```text
src/main/java/tr/com/servisdefteri/
  account/      cari hareketler, bakiye, ters kayÄ±t
  appointment/  randevular
  audit/        denetim kaydÄ±
  backup/       pg_dump yedekleme
  common/       ortak hata, doÄŸrulama, CSV, format
  config/       gÃ¼venlik, web, ilk iÅŸletme sahibi
  customer/     mÃ¼ÅŸteri modÃ¼lÃ¼
  dashboard/    ana panel
  payment/      tahsilat ve daÄŸÄ±tÄ±m
  report/       raporlar ve CSV
  search/       global arama
  security/     kullanÄ±cÄ±, rol, oturum, tek seferlik kurulum
  settings/     ayarlar, marka ve audit ekranÄ±
  update/       GitHub sÃ¼rÃ¼m kontrolÃ¼ ve host ajanÄ± isteÄŸi
  vehicle/      araÃ§ modÃ¼lÃ¼
  workorder/    iÅŸ emri, kalem ve belgeler
src/main/resources/
  db/migration/ Flyway migration dosyalarÄ±
  templates/    Thymeleaf ÅŸablonlarÄ±
  static/       CSS, JavaScript ve varsayÄ±lan logo
scripts/        baÅŸlatma, yedek tatbikatÄ±, CI, gÃ¼ncelleme ajanÄ±
updates/        uygulama â†” host ajanÄ± Ã§alÄ±ÅŸma dosyalarÄ±
docs/           mimari, veritabanÄ±, kurulum, yedekleme, kullanÄ±m
```

## DokÃ¼mantasyon

- [Mimari](docs/ARCHITECTURE.md)
- [VeritabanÄ±](docs/DATABASE.md)
- [Kurulum](docs/INSTALLATION.md)
- [Yedekleme ve Geri YÃ¼kleme](docs/BACKUP-RESTORE.md)
- [KullanÄ±cÄ± Rehberi](docs/USER-GUIDE.md)
- [Case Study](docs/CASE-STUDY.md)
- [Demo Video Senaryosu](docs/DEMO-VIDEO-SCRIPT.md)

## GÃ¼venlik ve Yerel AÄŸ NotlarÄ±

- Uygulama yerel aÄŸ iÃ§indir; internete aÃ§mayÄ±n, modemde port yÃ¶nlendirmeyin.
- Tablet/telefon aynÄ± Wi-Fiâ€™den `http://<sunucu-ip>:18080` ile baÄŸlanÄ±r.
- Windows GÃ¼venlik DuvarÄ±â€™nda yalnÄ±zca **Ã¶zel aÄŸ** profiline izin verin.
- Yerel aÄŸda HTTPS yoktur; gÃ¼venilir ve parola korumalÄ± bir Wi-Fi kullanÄ±n.
- TÃ¼m yetki kontrolleri sunucu tarafÄ±ndadÄ±r; menÃ¼/buton gizleme tek baÅŸÄ±na gÃ¼venlik sayÄ±lmaz.
- CSRF aÃ§Ä±ktÄ±r; formlar POST/Redirect/GET dÃ¼zenindedir.
- Finansal iptaller PATRON/MUHASEBE ile sÄ±nÄ±rlÄ±dÄ±r; iÅŸ emri durum geÃ§iÅŸleri PATRON/SERVIS yetkisindedir.
- Parola, oturum belirteci ve gereksiz kiÅŸisel veri loglanmaz.
- Ãœretimde `spring.jpa.hibernate.ddl-auto=validate`; ÅŸema yalnÄ±zca Flyway ile deÄŸiÅŸir.
- Yedek dosyalarÄ± web kÃ¶kÃ¼nde deÄŸildir; indirme yalnÄ±zca PATRON oturumuyla yapÄ±lÄ±r.

## Lisans / KullanÄ±m

Bu proje Toyota Plaza Ã–ztoprak iÃ§in yerel iÅŸletme kullanÄ±mÄ± amacÄ±yla geliÅŸtirilmiÅŸtir.
GerÃ§ek mÃ¼ÅŸteri verileriyle kullanmadan Ã¶nce kendi yedekleme ve eriÅŸim politikanÄ±zÄ± uygulayÄ±n.

---

<sub>powered by [RasimTuzluoÄŸlu](https://www.linkedin.com/in/rasimtuzluoglu311/)</sub>
