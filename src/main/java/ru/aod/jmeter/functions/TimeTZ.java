package ru.aod.jmeter.functions;

import java.text.MessageFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
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


public class TimeTZ extends AbstractFunction {
    private static final String KEY = "__timeTZ";

    private static final Pattern DIVISOR_PATTERN = Pattern.compile("/\\d+");

    private static final List<String> desc = new ArrayList<>();

    // Only modified in class init
    private static final Map<String, String> aliases = new HashMap<>();

    private static final Logger log = LoggerFactory.getLogger(TimeTZ.class);

    private static final LoadingCache<String, Supplier<String>> DATE_TIME_FORMATTER_CACHE =
            Caffeine.newBuilder()
                    .maximumSize(1000)
                    .build((key) -> {
                        // Разбираем ключ: формат и часовой пояс
                        String[] parts = key.split("\\|", 2);
                        String fmt = parts[0];
                        ZoneId zoneId = parts.length > 1 ? ZoneId.of(parts[1]) : ZoneId.systemDefault();

                        if (DIVISOR_PATTERN.matcher(fmt).matches()) {
                            long div = Long.parseLong(fmt.substring(1));
                            return () -> Long.toString(System.currentTimeMillis() / div);
                        }

                        DateTimeFormatter df;
                        try {
                            df = DateTimeFormatter
                                    .ofPattern(fmt)
                                    .withZone(zoneId);
                        } catch (IllegalArgumentException e) {
                            throw new IllegalArgumentException("Unable to parse date format " + fmt, e);
                        }

                        if (isPossibleUsageOfUInFormat(df, fmt)) {
                            log.warn(
                                    MessageFormat.format(
                                            JMeterUtils.getResString("time_format_changed"),
                                            fmt));
                        }

                        return () -> df.format(Instant.now());
                    });

    private static boolean isPossibleUsageOfUInFormat(DateTimeFormatter df, String fmt) {
        ZoneId mst = ZoneId.of("-07:00");
        return fmt.contains("u") &&
                df.withZone(mst)
                        .format(ZonedDateTime.of(2006, 1, 2, 15, 4, 5, 6, mst))
                        .contains("2006");
    }

    static {
        desc.add(JMeterUtils.getResString("time_format")); // формат
        desc.add("Timezone (optional, e.g., UTC, GMT+3, Europe/Moscow)"); // часовой пояс
        desc.add(JMeterUtils.getResString("function_name_paropt")); // имя переменной
        aliases.put("YMD",
                JMeterUtils.getPropDefault("time.YMD",
                        "yyyyMMdd"));
        aliases.put("HMS",
                JMeterUtils.getPropDefault("time.HMS",
                        "HHmmss"));
        aliases.put("YMDHMS",
                JMeterUtils.getPropDefault("time.YMDHMS",
                        "yyyyMMdd-HHmmss"));
        aliases.put("USER1",
                JMeterUtils.getPropDefault("time.USER1", ""));
        aliases.put("USER2",
                JMeterUtils.getPropDefault("time.USER2", ""));
    }

    // Ensure that these are set, even if no parameters are provided
    private String format = "";
    private String timezone = "";
    private String variable = "";

    public TimeTZ() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String execute(SampleResult previousResult, Sampler currentSampler) throws InvalidVariableException {
        String datetime;

        if (format.isEmpty()) {// Default to milliseconds

            // Для миллисекунд часовой пояс не важен, но можно добавить сдвиг
            if (!timezone.isEmpty()) {
                ZoneId zoneId = ZoneId.of(timezone);
                datetime = Long.toString(ZonedDateTime.now(zoneId).toInstant().toEpochMilli());
            } else {
                datetime = Long.toString(System.currentTimeMillis());
            }
        } else {
            // Resolve any aliases
            String fmt = aliases.get(format);
            if (fmt == null) {
                fmt = format;// Not found
            }
            // Формируем ключ для кеша: формат|часовой_пояс
            String cacheKey = timezone.isEmpty() ? fmt : fmt + "|" + timezone;
            datetime = DATE_TIME_FORMATTER_CACHE.get(cacheKey).get();
        }

        if (!variable.isEmpty()) {
            JMeterVariables vars = getVariables();
            if (vars != null) {// vars will be null on TestPlan
                vars.put(variable, datetime);
            }
        }
        return datetime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setParameters(Collection<CompoundVariable> parameters) throws InvalidVariableException {

        checkParameterCount(parameters, 0, 3);

        Object[] values = parameters.toArray();
        int count = values.length;

        if (count > 0) {
            format = ((CompoundVariable) values[0]).execute();
        }
        if (count > 1) {
            timezone = ((CompoundVariable) values[1]).execute();
        }

        if (count > 2) {
            variable = ((CompoundVariable) values[2]).execute().trim();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getReferenceKey() {
        return KEY;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getArgumentDesc() {
        return desc;
    }

}