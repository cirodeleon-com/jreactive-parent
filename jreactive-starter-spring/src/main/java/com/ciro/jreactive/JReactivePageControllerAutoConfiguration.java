package com.ciro.jreactive;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

/**
 * Registra el endpoint de páginas y {@code @Call} de JReactive en las aplicaciones web servlet.
 *
 * <p>Las condiciones de encendido viven en {@link JrxPageController}. Si la aplicación ya lo
 * registró al escanear {@code com.ciro.jreactive}, Spring descarta esta importación y queda
 * un solo endpoint.
 */
@AutoConfiguration(after = JReactiveAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import(JrxPageController.class)
public class JReactivePageControllerAutoConfiguration {
}