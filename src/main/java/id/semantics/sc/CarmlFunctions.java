package id.semantics.sc;

import com.taxonic.carml.engine.function.FnoFunction;
import com.taxonic.carml.engine.function.FnoParam;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class CarmlFunctions {

    public static final String FUNCTION_NS = "http://semantics.id/ns/function#";
    public static final String PARAM_NS = "http://semantics.id/ns/parameter#";

    private static final String SECONDS = "SECONDS";
    private static final String TIME_ZONE = "Europe/Vienna";

    /**
     * Format input time to conform with the XSD datetime format.
     * Special formats: (i) "seconds" to deal with seconds since epoch;
     *
     * @param time
     * @param timeFormat
     * @return String XSDDateTime formatted datetime
     */
    @FnoFunction(FUNCTION_NS + "timeConversion") public String timeConversion(
            @FnoParam(PARAM_NS + "time") String time, @FnoParam(PARAM_NS + "timeFormat") String timeFormat) {

        ZoneId zoneId = ZoneId.of(TIME_ZONE);
        ZoneOffset zoneOffSet = zoneId.getRules().getOffset(LocalDateTime.now());
        LocalDateTime dateTime = LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC);

        if (timeFormat.equalsIgnoreCase(SECONDS)) {
            dateTime = LocalDateTime.ofEpochSecond(Integer.parseInt(time), 0, zoneOffSet);

        } else {

            DateTimeFormatter fromFormatter;
            try {
                fromFormatter = DateTimeFormatter.ofPattern(timeFormat);
                dateTime = LocalDateTime.parse(time, fromFormatter);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (DateTimeParseException e) {
                e.printStackTrace();
            }
        }

        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
    }

}
