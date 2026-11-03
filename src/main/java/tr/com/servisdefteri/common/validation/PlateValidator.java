package tr.com.servisdefteri.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import tr.com.servisdefteri.common.PlateNormalizer;

public class PlateValidator implements ConstraintValidator<ValidPlate, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return PlateNormalizer.isValid(value);
    }
}
