package com.hawolt.ionhttp.cookies;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class Cookie {

    private final static SimpleDateFormat[] DATE_FORMATS = new SimpleDateFormat[]{
            new SimpleDateFormat("EEE, dd-MMM-yy HH:mm:ss z", Locale.US),
            new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
    };

    static {
        for (SimpleDateFormat format : DATE_FORMATS) {
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
        }
    }

    private final Map<String, String> map = new HashMap<>();
    private final List<String> switches = new ArrayList<>();
    private final String name, value, origin;

    public Cookie(String hostname, String source) {
        this.origin = hostname.split("/", 3)[2].split("/")[0];
        String[] data = source.split(";", 2);
        String[] cookie = data[0].split("=", 2);
        this.name = cookie[0];
        this.value = cookie[1];
        String[] args = data[1].split(";");
        for (String meta : args) {
            if (!meta.contains("=")) switches.add(meta.trim());
            else {
                String[] metadata = meta.trim().split("=");
                map.put(metadata[0], metadata[1]);
            }
        }
        if (map.get("domain") != null) return;
        String[] children = origin.split("\\.");
        map.put("domain", Arrays.stream(origin.split("\\.")).skip(children.length <= 2 ? 0 : 1).collect(Collectors.joining(".")));
    }

    public String get() {
        return String.join("=", name, value);
    }

    public boolean isNotExpired() {
        if (!map.containsKey("expires")) return true;
        for (SimpleDateFormat format : DATE_FORMATS) {
            try {
                Date date = format.parse(map.get("expires"));
                return System.currentTimeMillis() < date.toInstant().toEpochMilli();
            } catch (NumberFormatException | ParseException e) {
                // ignored
            }
        }
        return false;
    }

    public boolean hasValue() {
        return value != null && !value.isEmpty();
    }

    public boolean isValidFor(String hostname) {
        return map.containsKey("domain") && map.get("domain").endsWith(hostname);
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public boolean isSecure() {
        return switches.contains("Secure");
    }

    public boolean isHttpOnly() {
        return switches.contains("HttpOnly");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Cookie cookie = (Cookie) o;
        return Objects.equals(map, cookie.map) && Objects.equals(switches, cookie.switches) && Objects.equals(name, cookie.name) && Objects.equals(value, cookie.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(map, switches, name, value);
    }

    @Override
    public String toString() {
        return "Cookie{" +
                "name='" + name + '\'' +
                ", value='" + value + '\'' +
                ", domain='" + map.get("domain") + '\'' +
                ", origin='" + origin + '\'' +
                '}';
    }
}
