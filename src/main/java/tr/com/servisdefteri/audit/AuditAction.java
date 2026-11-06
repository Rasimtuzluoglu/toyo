package tr.com.servisdefteri.audit;

public enum AuditAction {

    LOGIN_SUCCESS("Giriş"),
    LOGIN_FAILURE("Başarısız giriş"),
    LOGOUT("Çıkış"),
    USER_CREATED("Kullanıcı oluşturma"),
    USER_UPDATED("Kullanıcı güncelleme"),
    USER_PASSWORD_RESET("Parola sıfırlama"),
    CUSTOMER_CREATED("Müşteri oluşturma"),
    CUSTOMER_UPDATED("Müşteri güncelleme"),
    CUSTOMER_DELETED("Müşteri silme"),
    VEHICLE_CREATED("Araç oluşturma"),
    VEHICLE_UPDATED("Araç güncelleme"),
    VEHICLE_DELETED("Araç silme"),
    VEHICLE_OWNER_CHANGED("Araç sahibi değişikliği"),
    WORK_ORDER_CREATED("İş emri oluşturma"),
    WORK_ORDER_UPDATED("İş emri güncelleme"),
    WORK_ORDER_STATUS_CHANGED("İş emri durum değişikliği"),
    WORK_ORDER_ITEM_ADDED("İş emri kalem ekleme"),
    WORK_ORDER_ITEM_UPDATED("İş emri kalem güncelleme"),
    WORK_ORDER_ITEM_REMOVED("İş emri kalem silme"),
    WORK_ORDER_DEBITED("İş emri borçlandırma"),
    WORK_ORDER_DEBT_REVERSED("İş emri borç ters kaydı"),
    VEHICLE_INTAKE_SAVED("Araç kabul formu"),
    VEHICLE_DELIVERY_SAVED("Teslim tutanağı"),
    APPOINTMENT_CREATED("Randevu oluşturma"),
    APPOINTMENT_UPDATED("Randevu güncelleme"),
    APPOINTMENT_CANCELLED("Randevu iptali"),
    DEBT_CREATED("Manuel borç kaydı"),
    PAYMENT_CREATED("Tahsilat oluşturma"),
    PAYMENT_CANCELLED("Tahsilat iptali"),
    PASSWORD_CHANGED("Parola değiştirme"),
    BUSINESS_SETTINGS_UPDATED("İşletme bilgileri güncelleme"),
    APP_UPDATE_REQUESTED("Yazılım güncelleme isteği"),
    APP_UPDATE_COMPLETED("Yazılım güncelleme tamamlandı"),
    BACKUP_TRIGGERED("Yedekleme başlatma"),
    BACKUP_COMPLETED("Yedekleme tamamlandı"),
    BACKUP_FAILED("Yedekleme hatası");

    private final String label;

    AuditAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
