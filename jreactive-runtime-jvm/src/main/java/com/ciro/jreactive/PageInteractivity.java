package com.ciro.jreactive;

import com.ciro.jreactive.annotations.Call;
import com.ciro.jreactive.annotations.Client;
import com.ciro.jreactive.annotations.Defer;
import com.ciro.jreactive.annotations.OnEvent;
import com.ciro.jreactive.annotations.Shared;
import com.ciro.jreactive.annotations.Stateful;
import com.ciro.jreactive.annotations.StatefulRam;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Decide si una página ya renderizada necesita su estado en el navegador.
 *
 * <p>Lo necesita si algo puede cambiar después del render: métodos @Call, @Defer u @OnEvent,
 * campos @Bind o @Shared, componentes con estado en servidor o @Client, un ciclo de vida vivo
 * (onMount u onUnmount), un script propio junto a la clase, campos de formulario con name o
 * bloques {{#if}} y {{#each}}, que hoy se resuelven en el navegador. Ante la duda, lo necesita.
 */
final class PageInteractivity {

    /** Campos de formulario que el runtime enlaza al estado por su name. */
    private static final Pattern NAMED_FORM_FIELD =
            Pattern.compile("<(input|textarea|select)\\b[^>]*\\bname\\s*=", Pattern.CASE_INSENSITIVE);

    /** Marcas que el motor deja en el HTML para lo que termina de resolverse en el navegador. */
    private static final List<String> CLIENT_SIDE_MARKERS = List.of(
            "<template data-", "jrx-suspense", "data-jrx-client", "<script data-resource=");

    private static final Map<Class<?>, Boolean> LIVE_CLASSES = new ConcurrentHashMap<>();

    private PageInteractivity() {
    }

    static boolean isInteractive(HtmlComponent page, HtmlComponent layout, String html) {
        return usesClientSideHtml(html) || treeIsLive(page) || treeIsLive(layout);
    }

    private static boolean usesClientSideHtml(String html) {
        return CLIENT_SIDE_MARKERS.stream().anyMatch(html::contains)
                || NAMED_FORM_FIELD.matcher(html).find();
    }

    private static boolean treeIsLive(HtmlComponent component) {
        if (component == null) {
            return false;
        }
        return isLiveClass(component.getClass())
                || component._children().stream().anyMatch(PageInteractivity::treeIsLive);
    }

    static boolean isLiveClass(Class<?> type) {
        return LIVE_CLASSES.computeIfAbsent(type, PageInteractivity::inspect);
    }

    private static boolean inspect(Class<?> type) {
        return hasLiveAnnotation(type)
                || ownHierarchy(type).stream().anyMatch(PageInteractivity::declaresLiveMembers);
    }

    private static boolean hasLiveAnnotation(Class<?> type) {
        return type.isAnnotationPresent(Stateful.class)
                || type.isAnnotationPresent(StatefulRam.class)
                || type.isAnnotationPresent(Client.class);
    }

    /** La clase y sus padres hasta HtmlComponent, sin incluirlo. */
    private static List<Class<?>> ownHierarchy(Class<?> type) {
        List<Class<?>> classes = new ArrayList<>();
        for (Class<?> c = type; c != null && c != HtmlComponent.class; c = c.getSuperclass()) {
            classes.add(c);
        }
        return classes;
    }

    private static boolean declaresLiveMembers(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods()).anyMatch(PageInteractivity::isLiveMethod)
                || Arrays.stream(type.getDeclaredFields()).anyMatch(PageInteractivity::isLiveField);
    }

    private static boolean isLiveMethod(Method method) {
        return method.isAnnotationPresent(Call.class)
                || method.isAnnotationPresent(Defer.class)
                || method.isAnnotationPresent(OnEvent.class)
                || isLiveLifecycle(method);
    }

    /** onMount y onUnmount solo se sobrescriben para algo vivo: timers, suscripciones. */
    private static boolean isLiveLifecycle(Method method) {
        String name = method.getName();
        return method.getParameterCount() == 0 && (name.equals("onMount") || name.equals("onUnmount"));
    }

    private static boolean isLiveField(Field field) {
        return field.isAnnotationPresent(Bind.class) || field.isAnnotationPresent(Shared.class);
    }
}