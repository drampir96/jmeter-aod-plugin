package ru.aod.jmeter.functions;


import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.AbstractFunction;
import org.apache.jmeter.functions.InvalidVariableException;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.threads.JMeterVariables;
import org.apache.jmeter.util.JMeterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import ru.aod.jmeter.functions.util.LocaleUtils;
import ru.aod.jmeter.functions.util.StringUtilities;

public class TimeShiftWithZone extends AbstractFunction {


private static final Logger log = LoggerFactory.getLogger(TimeShiftWithZone.class);

private static final String KEY = "__timeShiftTZ"; //$NON-NLS-1$

private static final List<String> desc = Arrays.asList(
        "Format string (DateTimeFormatter pattern, or empty for epoch ms)",
        "Date to shift (formatted or epoch ms; empty = now)",
        "Amount to shift (ISO-8601 Duration: P1D, PT2H, -PT30M; empty = no shift)",
        "Locale (e.g. ru_RU; empty = JMeter locale)",
        "Time zone ID (e.g. Europe/Moscow, UTC+03:00; empty = system default)",
        "Variable name (optional)"
);

// Параметры функции (устанавливаются в setParameters)
private String          format             = ""; //$NON-NLS-1$
private CompoundVariable dateToShiftCV;
private CompoundVariable amountToShiftCV;
private Locale          locale             = JMeterUtils.getLocale();
private String          timeZoneId         = ""; //$NON-NLS-1$
private String          variableName       = ""; //$NON-NLS-1$

/** Ключ кэша форматтеров: format + locale + timeZoneId */
private static final class CacheKey {
    final String format;
    final Locale locale;
    final String timeZoneId;

    CacheKey(String format, Locale locale, String timeZoneId) {
        this.format     = format;
        this.locale     = locale;
        this.timeZoneId = timeZoneId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CacheKey)) return false;
        CacheKey k = (CacheKey) o;
        return format.equals(k.format)
                && locale.equals(k.locale)
                && timeZoneId.equals(k.timeZoneId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(format, locale, timeZoneId);
    }
}

private Cache<CacheKey, DateTimeFormatter> formatterCache;

public TimeShiftWithZone() {
    super();
}

// -------------------------------------------------------------------------
// execute
// -------------------------------------------------------------------------

@Override
public String execute(SampleResult previousResult, Sampler currentSampler)
        throws InvalidVariableException {

    String amountToShift = amountToShiftCV.execute().trim();
    String dateToShift   = dateToShiftCV.execute().trim();

    // Определяем часовой пояс
    ZoneId zone = resolveZone(timeZoneId);

    // Начальная точка — "сейчас" в нужном TZ
    ZonedDateTime zdt = ZonedDateTime.now(zone);

    DateTimeFormatter formatter = null;

    // Строим форматтер если задан format
    if (StringUtilities.isNotEmpty(format)) {
        try {
            CacheKey cacheKey = new CacheKey(format, locale, timeZoneId);
            formatter = formatterCache.get(cacheKey, k -> createFormatter(k.format, k.locale, k.timeZoneId));
        } catch (IllegalArgumentException ex) {
            log.error("Format date pattern '{}' is invalid: {}", format, ex.getMessage());
            return "";
        }
    }

    // Парсим входную дату если задана
    if (StringUtilities.isNotEmpty(dateToShift)) {
        try {
            if (formatter != null) {
                zdt = ZonedDateTime.parse(dateToShift, formatter);
            } else {
                // Формат не задан → dateToShift ожидается в миллисекундах
                zdt = ZonedDateTime.ofInstant(
                        Instant.ofEpochMilli(Long.parseLong(dateToShift)), zone);
            }
        } catch (DateTimeParseException | NumberFormatException ex) {
            log.error("Failed to parse date '{}' with formatter '{}': {}",
                    dateToShift, formatter, ex.getMessage());
        }
    }

    // Применяем смещение
    if (StringUtilities.isNotEmpty(amountToShift)) {
        try {
            Duration duration = Duration.parse(amountToShift);
            zdt = zdt.plus(duration);
        } catch (DateTimeParseException ex) {
            log.error("Failed to parse duration '{}' "
                            + "(see https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html#parse-java.lang.CharSequence-): {}",
                    amountToShift, ex.getMessage());
        }
    }

    // Форматируем результат
    String result;
    if (formatter != null) {
        result = zdt.format(formatter);
    } else {
        result = String.valueOf(zdt.toInstant().toEpochMilli());
    }

    // Сохраняем в переменную при необходимости
    if (StringUtilities.isNotEmpty(variableName)) {
        JMeterVariables vars = getVariables();
        if (vars != null) { // vars == null на уровне TestPlan
            vars.put(variableName, result);
        }
    }

    return result;
}

// -------------------------------------------------------------------------
// setParameters
// -------------------------------------------------------------------------

@Override
public void setParameters(Collection<CompoundVariable> parameters)
        throws InvalidVariableException {
    // Минимум 4 (как в __timeShift), максимум 6 (добавляем timeZone + variable)
    checkParameterCount(parameters, 4, 6);
    Object[] values = parameters.toArray();
    int count = values.length;

    format          = ((CompoundVariable) values[0]).execute().trim();
    dateToShiftCV   = (CompoundVariable) values[1];
    amountToShiftCV = (CompoundVariable) values[2];

    // Параметр 4 (индекс 3): locale или variableName (логика оригинала: если 4 аргумента —
    // индекс 3 = variableName; если 5+ — индекс 3 = locale, индекс 4 = variableName)
    if (count == 4) {
        // Нет locale, нет timeZone → variableName
        variableName = ((CompoundVariable) values[3]).execute().trim();
        timeZoneId   = "";
    } else if (count == 5) {
        // locale + timeZone, но нет variableName
        String localeStr = ((CompoundVariable) values[3]).execute().trim();
        if (!localeStr.isEmpty()) {
            locale = LocaleUtils.toLocale(localeStr);
        }
        timeZoneId   = ((CompoundVariable) values[4]).execute().trim();
        variableName = "";
    } else { // count == 6
        String localeStr = ((CompoundVariable) values[3]).execute().trim();
        if (!localeStr.isEmpty()) {
            locale = LocaleUtils.toLocale(localeStr);
        }
        timeZoneId   = ((CompoundVariable) values[4]).execute().trim();
        variableName = ((CompoundVariable) values[5]).execute().trim();
    }

    // Инициализируем кэш форматтеров (если ещё не создан)
    if (formatterCache == null) {
        formatterCache = Caffeine.newBuilder().maximumSize(100).build();
    }
}

// -------------------------------------------------------------------------
// Helpers
// -------------------------------------------------------------------------

/** Разбирает timeZoneId в ZoneId, при ошибке возвращает системный */
private static ZoneId resolveZone(String tzId) {
    if (StringUtilities.isEmpty(tzId)) {
        return ZoneId.systemDefault();
    }
    try {
        return ZoneId.of(tzId);
    } catch (Exception e) {
        log.warn("Invalid time zone '{}', falling back to system default", tzId, e);
        return ZoneId.systemDefault();
    }
}

/**
 * Строит DateTimeFormatter для заданного паттерна, локали и часового пояса.
 * Логика parseDefaulting полностью повторяет оригинальный TimeShift.createFormatter.
 */
private static DateTimeFormatter createFormatter(String pattern, Locale loc, String tzId) {
    ZoneId zone = resolveZone(tzId);
    log.debug("Creating DateTimeFormatter: pattern='{}', locale='{}', zone='{}'",
            pattern, loc, zone);
    return new DateTimeFormatterBuilder()
            .appendPattern(pattern)
            .parseDefaulting(ChronoField.NANO_OF_SECOND,   0)
            .parseDefaulting(ChronoField.MILLI_OF_SECOND,  0)
            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR,   0)
            .parseDefaulting(ChronoField.HOUR_OF_DAY,      0)
            .parseDefaulting(ChronoField.DAY_OF_MONTH,     1)
            .parseDefaulting(ChronoField.MONTH_OF_YEAR,    1)
            .parseDefaulting(ChronoField.YEAR_OF_ERA,      Year.now(zone).getValue())
            .parseDefaulting(ChronoField.OFFSET_SECONDS,   ZonedDateTime.now(zone).getOffset().getTotalSeconds())
            .toFormatter(loc)
            .withZone(zone);
}

// -------------------------------------------------------------------------
// AbstractFunction contract
// -------------------------------------------------------------------------

@Override
public String getReferenceKey() {
    return KEY;
}

@Override
public List<String> getArgumentDesc() {
    return desc;
}
}