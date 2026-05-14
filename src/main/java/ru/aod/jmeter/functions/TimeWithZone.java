package ru.aod.jmeter.functions;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.AbstractFunction;
import org.apache.jmeter.functions.InvalidVariableException;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.threads.JMeterVariables;
import org.apache.jmeter.util.JMeterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import ru.aod.jmeter.functions.util.StringUtilities;

public class TimeWithZone extends AbstractFunction {


    private static final Logger log = LoggerFactory.getLogger(TimeWithZone.class);

    private static final String KEY = "__timeTZ"; //$NON-NLS-1$

    private static final Pattern DIVISOR_PATTERN = Pattern.compile("/\\d+");

    private static final List<String> desc = new ArrayList<>();

    // Алиасы форматов — идентичны оригинальному __time
    private static final Map<String, String> aliases = new HashMap<>();

    static {
        desc.add("Format string (e.g. yyyy-MM-dd HH:mm:ss) or alias (YMD/HMS/YMDHMS/USER1/USER2)");
        desc.add("Time zone ID (e.g. Europe/Moscow, UTC+03:00). Defaults to system time zone.");
        desc.add("Variable name (optional)");

        aliases.put("YMD",    JMeterUtils.getPropDefault("time.YMD",    "yyyyMMdd"));        //$NON-NLS-1$
        aliases.put("HMS",    JMeterUtils.getPropDefault("time.HMS",    "HHmmss"));           //$NON-NLS-1$
        aliases.put("YMDHMS", JMeterUtils.getPropDefault("time.YMDHMS", "yyyyMMdd-HHmmss")); //$NON-NLS-1$
        aliases.put("USER1",  JMeterUtils.getPropDefault("time.USER1",  ""));                 //$NON-NLS-1$
        aliases.put("USER2",  JMeterUtils.getPropDefault("time.USER2",  ""));                 //$NON-NLS-1$
    }

    /**
     * Ключ кэша — пара (format, zoneId).
     */
    private static final class FormatZoneKey {
        private final String format;
        private final String zoneId;

        FormatZoneKey(String format, String zoneId) {
            this.format = format;
            this.zoneId = zoneId;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof FormatZoneKey)) return false;
            FormatZoneKey other = (FormatZoneKey) o;
            return format.equals(other.format) && zoneId.equals(other.zoneId);
        }

        @Override
        public int hashCode() {
            return 31 * format.hashCode() + zoneId.hashCode();
        }
    }

    private static final LoadingCache<FormatZoneKey, DateTimeFormatter> FORMATTER_CACHE =
            Caffeine.newBuilder()
                    .maximumSize(1000)
                    .build(key -> {
                        ZoneId zone;
                        try {
                            zone = StringUtilities.isEmpty(key.zoneId)
                                    ? ZoneId.systemDefault()
                                    : ZoneId.of(key.zoneId);
                        } catch (Exception e) {
                            log.warn("Invalid time zone '{}', falling back to system default", key.zoneId, e);
                            zone = ZoneId.systemDefault();
                        }
                        try {
                            return DateTimeFormatter.ofPattern(key.format).withZone(zone);
                        } catch (IllegalArgumentException e) {
                            throw new IllegalArgumentException(
                                    "Unable to parse date format '" + key.format + "'", e);
                        }
                    });

    // Текущие параметры функции (устанавливаются в setParameters)
    private String format   = ""; //$NON-NLS-1$
    private String timeZone = ""; //$NON-NLS-1$
    private String variable = ""; //$NON-NLS-1$

    public TimeWithZone() {
        super();
    }

    @Override
    public String execute(SampleResult previousResult, Sampler currentSampler)
            throws InvalidVariableException {

        String datetime;

        if (format.isEmpty()) {
            // Поведение по умолчанию — миллисекунды
            datetime = Long.toString(System.currentTimeMillis());
        } else {
            // Разрешаем алиасы
            String fmt = aliases.getOrDefault(format, format);

            if (DIVISOR_PATTERN.matcher(fmt).matches()) {
                // Делитель вида /1000 → unix-секунды, /1 → мс и т.д.
                long div = Long.parseLong(fmt.substring(1));
                datetime = Long.toString(System.currentTimeMillis() / div);
            } else {
                try {
                    DateTimeFormatter df = FORMATTER_CACHE.get(new FormatZoneKey(fmt, timeZone));
                    datetime = df.format(Instant.now());
                } catch (IllegalArgumentException e) {
                    log.error("Invalid format '{}': {}", fmt, e.getMessage());
                    return "";
                }
            }
        }

        if (!variable.isEmpty()) {
            JMeterVariables vars = getVariables();
            if (vars != null) { // vars == null на уровне TestPlan
                vars.put(variable, datetime);
            }
        }

        return datetime;
    }

    @Override
    public void setParameters(Collection<CompoundVariable> parameters)
            throws InvalidVariableException {
        checkParameterCount(parameters, 0, 3);
        Object[] values = parameters.toArray();
        int count = values.length;

        format   = count > 0 ? ((CompoundVariable) values[0]).execute().trim() : "";
        timeZone = count > 1 ? ((CompoundVariable) values[1]).execute().trim() : "";
        variable = count > 2 ? ((CompoundVariable) values[2]).execute().trim() : "";
    }

    @Override
    public String getReferenceKey() {
        return KEY;
    }

    @Override
    public List<String> getArgumentDesc() {
        return desc;
    }
}