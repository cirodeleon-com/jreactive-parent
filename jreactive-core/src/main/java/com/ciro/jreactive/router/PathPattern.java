package com.ciro.jreactive.router;

import java.util.*;
import java.util.regex.*;

public final class PathPattern {
    final Pattern regex;
    final List<String> names;

    private PathPattern(Pattern regex, List<String> names) {
        this.regex = regex;
        this.names = names;
    }

    public static PathPattern compile(String template) {
        // "/users/{id}/orders/{oid}" ->  "^/users/([^/]+)/orders/([^/]+)$"
        Matcher m = Pattern.compile("\\{([^/}]+)}").matcher(template);
        StringBuffer sb = new StringBuffer("^");
        List<String> names = new ArrayList<>();
        int last = 0;
        while (m.find()) {
            sb.append(Pattern.quote(template.substring(last, m.start())));
            sb.append("([^/]+)");
            names.add(m.group(1));
            last = m.end();
        }
        sb.append(Pattern.quote(template.substring(last)));
        sb.append("$");
        return new PathPattern(Pattern.compile(sb.toString()), names);
    }

    public Map<String,String> match(String path) {
        Matcher m = regex.matcher(path);
        if (!m.matches()) return null;
        Map<String,String> vals = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            vals.put(names.get(i), m.group(i+1));
        }
        return vals;
    }
}
