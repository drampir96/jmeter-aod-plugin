package ru.aod.jmeter.functions;

import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.AbstractFunction;
import org.apache.jmeter.functions.InvalidVariableException;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.threads.JMeterVariables;

import java.time.format.DateTimeFormatter;
import java.time.*;
import java.util.*;

    public class TimeShiftWithTimeZone extends AbstractFunction {
        private static final List<String> DESC = new ArrayList<>();
        private static final String KEY = "__timeShiftTZ";

        private CompoundVariable format;
        private CompoundVariable date;
        private CompoundVariable shift;
        private CompoundVariable locale;
        private CompoundVariable timeZone;
        private CompoundVariable variableName;
        private CompoundVariable resultFormat;

        static {
            DESC.add("Формат даты (опционально)");
            DESC.add("Начальная дата (опционально, по умолчанию - сейчас)");
            DESC.add("Сдвиг (например, 'P1D', '-PT1H', '+P1Y2M3D')");
            DESC.add("Локаль (опционально)");
            DESC.add("Часовой пояс (например, 'UTC', 'Europe/Moscow')");
            DESC.add("Имя переменной для сохранения результата (опционально)");
            DESC.add("Формат результата (опционально)");
        }

        @Override
        public String execute(SampleResult previousResult, Sampler currentSampler) throws InvalidVariableException {
            String formatStr = format != null ? format.execute().trim() : "";
            String dateStr = date != null ? date.execute().trim() : "";
            String shiftStr = shift != null ? shift.execute().trim() : "";
            String localeStr = locale != null ? locale.execute().trim() : "";
            String tzStr = timeZone != null ? timeZone.execute().trim() : "UTC";
            String resultFormatStr = resultFormat != null ? resultFormat.execute().trim() : "";

            try {
                ZoneId zoneId;
                if (tzStr.startsWith("+") || tzStr.startsWith("-")) {
                    zoneId = ZoneId.of("GMT" + tzStr);
                } else {
                    zoneId = ZoneId.of(tzStr);
                }

                ZonedDateTime dateTime;

                if (dateStr.isEmpty()) {
                    dateTime = ZonedDateTime.now(zoneId);
                } else {
                    if (formatStr.isEmpty()) {
                        // Если формат не указан, предполагаем что это timestamp
                        long timestamp = Long.parseLong(dateStr);
                        dateTime = ZonedDateTime.ofInstant(
                                Instant.ofEpochMilli(timestamp),
                                zoneId
                        );
                    } else {
                        DateTimeFormatter parser;
                        if (!localeStr.isEmpty()) {
                            String[] parts = localeStr.split("_");
                            Locale loc = parts.length == 2 ?
                                    new Locale(parts[0], parts[1]) :
                                    new Locale(parts[0]);
                            parser = DateTimeFormatter.ofPattern(formatStr, loc);
                        } else {
                            parser = DateTimeFormatter.ofPattern(formatStr);
                        }

                        LocalDateTime localDateTime = LocalDateTime.parse(dateStr, parser);
                        dateTime = localDateTime.atZone(zoneId);
                    }
                }

                // Применяем сдвиг
                if (!shiftStr.isEmpty()) {
                    boolean negative = shiftStr.startsWith("-");
                    String shiftValue = shiftStr.replace("+", "").replace("-", "");

                    // Парсим сдвиг
                    Period period = Period.ZERO;
                    Duration duration = Duration.ZERO;

                    String[] parts = shiftValue.split("T");
                    if (parts.length > 0 && !parts[0].isEmpty()) {
                        period = Period.parse("P" + parts[0]);
                    }
                    if (parts.length > 1 && !parts[1].isEmpty()) {
                        duration = Duration.parse("PT" + parts[1]);
                    }

                    if (negative) {
                        dateTime = dateTime.minus(period).minus(duration);
                    } else {
                        dateTime = dateTime.plus(period).plus(duration);
                    }
                }

                // Форматируем результат
                String result;
                if (!resultFormatStr.isEmpty()) {
                    DateTimeFormatter formatter;
                    if (!localeStr.isEmpty()) {
                        String[] parts = localeStr.split("_");
                        Locale loc = parts.length == 2 ?
                                new Locale(parts[0], parts[1]) :
                                new Locale(parts[0]);
                        formatter = DateTimeFormatter.ofPattern(resultFormatStr, loc);
                    } else {
                        formatter = DateTimeFormatter.ofPattern(resultFormatStr);
                    }
                    result = formatter.format(dateTime);
                } else if (formatStr.isEmpty()) {
                    result = String.valueOf(dateTime.toInstant().toEpochMilli());
                } else {
                    DateTimeFormatter formatter;
                    if (!localeStr.isEmpty()) {
                        String[] parts = localeStr.split("_");
                        Locale loc = parts.length == 2 ?
                                new Locale(parts[0], parts[1]) :
                                new Locale(parts[0]);
                        formatter = DateTimeFormatter.ofPattern(formatStr, loc);
                    } else {
                        formatter = DateTimeFormatter.ofPattern(formatStr);
                    }
                    result = formatter.format(dateTime);
                }

                if (variableName != null) {
                    JMeterVariables vars = getVariables();
                    vars.put(variableName.execute().trim(), result);
                }

                return result;
            } catch (Exception e) {
                throw new InvalidVariableException("Ошибка в функции " + KEY + ": " + e.getMessage(), e);
            }
        }

        @Override
        public void setParameters(Collection<CompoundVariable> parameters) throws InvalidVariableException {
            checkParameterCount(parameters, 0, 7);
            Object[] values = parameters.toArray();

            if (values.length > 0) {
                format = (CompoundVariable) values[0];
            }
            if (values.length > 1) {
                date = (CompoundVariable) values[1];
            }
            if (values.length > 2) {
                shift = (CompoundVariable) values[2];
            }
            if (values.length > 3) {
                locale = (CompoundVariable) values[3];
            }
            if (values.length > 4) {
                timeZone = (CompoundVariable) values[4];
            }
            if (values.length > 5) {
                variableName = (CompoundVariable) values[5];
            }
            if (values.length > 6) {
                resultFormat = (CompoundVariable) values[6];
            }
        }

        @Override
        public String getReferenceKey() {
            return KEY;
        }

        @Override
        public List<String> getArgumentDesc() {
            return DESC;
        }
    }