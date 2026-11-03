package tr.com.servisdefteri.common;

/**
 * Kullaniciya gosterilebilir is kurali ihlali. Teknik ayrinti icermez.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
