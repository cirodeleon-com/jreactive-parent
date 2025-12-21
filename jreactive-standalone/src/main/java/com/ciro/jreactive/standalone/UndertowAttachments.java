/* === File: jreactive-standalone/src/main/java/com/ciro/jreactive/standalone/UndertowAttachments.java === */
package com.ciro.jreactive.standalone;

import io.undertow.util.AttachmentKey;

public final class UndertowAttachments {
    private UndertowAttachments() {}

    public static final AttachmentKey<String> SESSION_ID = AttachmentKey.create(String.class);
}
