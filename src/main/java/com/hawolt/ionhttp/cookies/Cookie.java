package com.hawolt.ionhttp.cookies;

import com.hawolt.logger.Logger;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class Cookie {

    private final static DateTimeFormatter[] DATE_FORMATTERS = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern("EEE, dd-MMM-yy HH:mm:ss z", Locale.US),
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.US),
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_ZONED_DATE_TIME
    };

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
        String expiry = map.get("expires").trim();
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                ZonedDateTime dateTime = ZonedDateTime.parse(expiry, formatter);
                return System.currentTimeMillis() < dateTime.toInstant().toEpochMilli();
            } catch (Exception e) {
                // ignored
            }
        }
        Logger.debug("Failed to parse: {}", expiry);
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
