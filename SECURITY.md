# Güvenlik Politikası

## Desteklenen Sürüm

Yalnızca `main` dalındaki güncel sürüm güvenlik düzeltmeleri alır. Üretimde çalışan commit
`updates/deployed-commit` dosyasında tutulur.

## Güvenlik Özellikleri

- Spring Boot 4.2 / Spring Security 7.2; CSRF, güvenli oturum çerezi ve rol bazlı yetki.
- BCrypt strength 12; giriş ve parola kurtarma denemeleri kullanıcı + IP başına sınırlı.
- Setup token yoksa `/setup` yalnızca doğrudan loopback bağlantısından açılır.
- Logo yüklemede gerçek dosya imzası doğrulaması.
- Uygulama container'ı `app` kullanıcısıyla çalışır; `no-new-privileges` açıktır.
- PostgreSQL portu host'a yayınlanmaz; uygulama varsayılan olarak yalnızca `127.0.0.1` dinler.
- Güncelleme ajanı yalnızca `security/allowed_signers` içindeki güvenilen SSH anahtarıyla
  imzalanmış commit'leri uygular.
- Yedekler `pg_restore --list` ile doğrulanır; ikinci/offsite kopya desteklenir.

## Bilinen Artık Riskler (24.11.2026)

| Bileşen | Durum | Not |
|---|---|---|
| Uygulama imajı `servisdefteri:1.0.0` | **0 kritik, 1 yüksek** | Alpine `musl 1.2.6-r3`; düzeltme `1.2.6-r6` henüz Alpine 3.24 deposunda yok |
| PostgreSQL imajı `servisdefteri-db:16` | **0 kritik, 2 yüksek** | `musl 1.2.6-r3` ve `libxml2 2.13.9-r3`; ikinci CVE için üretici düzeltmesi yok |
| Docker Desktop | Lisans | Ticari kullanımda Docker Desktop aboneliği gerekebilir; lisans koşullarını kontrol edin |
| Dış erişim | Kapalı | Uygulama HTTP ve yalnızca `127.0.0.1`; yerel ağa açılırsa TLS/reverse proxy gerekir |
| Yedek şifreleme | Uygulama düzeyinde yok | Disk/BitLocker ve erişim izinleriyle korunmalıdır |

Kalan CVE'ler yerel-only kullanım, yayınlanmayan DB portu, non-root uygulama kullanıcısı ve
`no-new-privileges` ile telafi edilir. Alpine deposunda düzeltme yayımlandığında imajlar
yeniden oluşturulup `docker scout cves` ile doğrulanmalıdır.

## Güvenlik Taraması

```powershell
docker scout cves servisdefteri:1.0.0 --only-severity critical,high
docker scout cves servisdefteri-db:16 --only-severity critical,high
mvn -B clean verify
```

## Commit İmzalama

- Biçim: SSH (`gpg.format=ssh`).
- Güvenilen açık anahtar: `security/allowed_signers`.
- Özel anahtar depoda tutulmaz; geliştirici profilinde (`~/.ssh/toyota_signing`) bulunur.
- Anahtar rotasyonunda yeni açık anahtar `security/allowed_signers` dosyasına eklenir, eski
  anahtar çıkarılır ve değişiklik imzalı bir commit ile yayınlanır.
- GitHub'da commit'lerin **Verified** görünmesi için aynı açık anahtar GitHub hesabına
  *SSH signing key* olarak eklenmelidir. Bu işlem `admin:ssh_signing_key` yetkisi gerektirir.

## Olay Bildirimi

Güvenlik açığı veya şüpheli erişim fark edilirse:

1. Uygulamayı ve gerekirse ağ erişimini durdurun.
2. `backups/` ve `C:\ToyotaServis-Yedekler` klasörlerini değiştirmeden koruyun.
3. `updates/update.log`, `updates/agent.log`, `logs/health-check.log` ve Docker loglarını saklayın.
4. PATRON parolasını `scripts/reset-user-password.ps1 -Username patron` ile sıfırlayın.
5. Gerekirse son doğrulanmış yedeği `docs/BACKUP-RESTORE.md` adımlarıyla geri yükleyin.
