// src/main/java/com/gymsystem/i18n/I18n.java
package com.gymsystem.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Tiny wrapper around MessageSource to resolve codes with current locale.
 */
@Component
@RequiredArgsConstructor
public class I18n {

    private final MessageSource messageSource;

    public String msg(String code, Object... args) {
        return messageSource.getMessage(code, args, code, LocaleContextHolder.getLocale());
    }
}
