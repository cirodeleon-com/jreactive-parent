package com.ciro.jreactive;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.ciro.jreactive.router.RouteProvider;
import com.ciro.jreactive.router.RouteRegistry;
import com.ciro.jreactive.store.CaffeineStateStore;
import com.ciro.jreactive.store.StateStore;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Validator;

/**
 * Autoconfiguración del starter: una aplicación Spring Boot obtiene JReactive con solo
 * agregar la dependencia, sin escanear com.ciro.jreactive.
 * Si la aplicación sí escanea ese paquete, los componentes escaneados tienen prioridad
 * y las importaciones de esta clase no los duplican.
 */
@AutoConfiguration(before = JacksonAutoConfiguration.class)
@Import(JacksonConfig.class)
public class JReactiveAutoConfiguration {
	
	@Autowired ObjectMapper mapper;
	
	@PostConstruct
    public void installEngine() {
        AstComponentEngine.installAsDefault();
    }
	
	@Bean
    @ConditionalOnMissingBean(StateStore.class)
    public StateStore stateStore() {
        return new CaffeineStateStore();
    }

    @Bean
    public CallGuard callGuard(Validator validator, ObjectMapper mapper) {
        return new CallGuard(validator, mapper);
    }
    
    @Bean
    public PageResolver pageResolver(RouteProvider routeProvider, StateStore store) {
        return new PageResolver(routeProvider, store);
    }
    
    @Bean
    public JrxHubManager jrxHubManager(
    		PageResolver pageResolver, 
    		ObjectMapper mapper,
    		@Autowired(required = false) com.ciro.jreactive.spi.JrxMessageBroker broker) {
        return new JrxHubManager(pageResolver, mapper,broker);
    }
    
    @Bean
    @ConditionalOnMissingBean(JrxStateSnapshotController.class)
    public JrxStateSnapshotController jrxStateSnapshotController(
            JrxHubManager hubManager) {
        return new JrxStateSnapshotController(hubManager);
    }

    /**
     * Proveedor de rutas por defecto. Si la aplicación ya tiene uno, escaneado o propio,
     * se usa el de la aplicación.
     */
    @Bean
    @ConditionalOnMissingBean(RouteProvider.class)
    public RouteRegistry routeRegistry(ApplicationContext context) {
        return new RouteRegistry(context);
    }

    /**
     * Piezas que solo tienen sentido con un servidor web servlet: configuración y canal
     * WebSocket, cola serial por sesión y limpieza de sesiones. Si la aplicación escanea
     * com.ciro.jreactive, Spring ya las registró y estas importaciones se ignoran.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @Import({
            WsConfig.class,
            WsResourcesConfig.class,
            JrxRequestQueue.class,
            DelegatingWebSocketHandler.class,
            WebSocketConfig.class,
            SessionCleanupConfig.class
    })
    static class WebEndpoints {
    }

    @Bean
    @ConditionalOnMissingBean(com.ciro.jreactive.spi.JrxMessageBroker.class)
    public com.ciro.jreactive.spi.JrxMessageBroker localMessageBroker() {
        System.out.println("🚀 JReactive: Iniciando LocalMessageBroker (RAM Multijugador)");
        return new com.ciro.jreactive.LocalMessageBroker(); // 👈 Cambio de paquete
    }

}