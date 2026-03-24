// src/main/java/com/gymsystem/i18n/MessagesConfig.java
package com.gymsystem.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

/**
 * Configures a MessageSource for i18n using resource bundles named "messages".
 * Spring will pick locale from Accept-Language or LocaleContextHolder.
 */
@Configuration
public class MessagesConfig {

    @Bean
    public MessageSource messageSource() {
        // Reloadable for dev; in prod a simple ResourceBundleMessageSource is fine
        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasename("classpath:messages"); // looks for messages*.properties
        ms.setDefaultEncoding("UTF-8");
        ms.setFallbackToSystemLocale(false);
        return ms;
    }
}
