package com.finance.financeservice.common.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class MultiFormatLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    private static final DateTimeFormatter[] CANDIDATES = new DateTimeFormatter[] {
            // ISO date-time with offset and millis
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"),
            // ISO date-time with offset without millis
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"),
            // ISO local date-time
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            // Space separated without offset
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    };

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String text = p.getText();
        if (text == null || text.isBlank()) return null;

        // Try parse with candidates
        for (DateTimeFormatter f : CANDIDATES) {
            try {
                if (f.toString().contains("XXX")) { // offset present
                    OffsetDateTime odt = OffsetDateTime.parse(text, f);
                    return odt.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
                }
                return LocalDateTime.parse(text, f);
            } catch (DateTimeParseException ignored) { }
        }

        // Fallback to default ISO parsing
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw ctxt.weirdStringException(text, LocalDateTime.class, "Unsupported date-time format");
        }
    }
} 