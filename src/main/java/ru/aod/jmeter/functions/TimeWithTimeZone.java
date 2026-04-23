package ru.aod.jmeter.functions;



import org.apache.jmeter.engine.util.CompoundVariable;
import org.apache.jmeter.functions.AbstractFunction;
import org.apache.jmeter.functions.InvalidVariableException;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.threads.JMeterVariables;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

    public class TimeWithTimeZone extends AbstractFunction {
        private static final List<String> DESC = new ArrayList<>();
        private static final String KEY = "__timeTZ";

        private CompoundVariable format;
        private CompoundVariable variableName;
        private CompoundVariable timeZone;
        private CompoundVariable locale;

        static {
            DESC.add("Формат даты (опционально, по умолчанию - миллисекунды)");
            DESC.add("Имя переменной для сохранения результата (опционально)");
            DESC.add("Часовой пояс (например, 'UTC', 'Europe/Moscow', '+03:00')");
            DESC.add("Локаль (опционально, например, 'en_US', 'ru_RU')");
        }

        @Override
        public String execute(SampleResult previousResult, Sampler currentSampler) throws InvalidVariableException {
            String formatStr = format != null ? format.execute().trim() : "";
            String tzStr = timeZone != null ? timeZone.execute().trim() : "UTC";
            String localeStr = locale != null ? locale.execute().trim() : "";

            try {
                ZoneId zoneId;
                if (tzStr.startsWith("+") || tzStr.startsWith("-")) {
                    zoneId = ZoneId.of("GMT" + tzStr);
                } else {
                    zoneId = ZoneId.of(tzStr);
                }

                ZonedDateTime now = ZonedDateTime.now(zoneId);
                String result;

                if (formatStr.isEmpty()) {
                    result = String.valueOf(now.toInstant().toEpochMilli());
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
                    result = formatter.format(now);
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
            checkParameterCount(parameters, 0, 4);
            Object[] values = parameters.toArray();

            if (values.length > 0) {
                format = (CompoundVariable) values[0];
            }
            if (values.length > 1) {
                variableName = (CompoundVariable) values[1];
            }
            if (values.length > 2) {
                timeZone = (CompoundVariable) values[2];
            }
            if (values.length > 3) {
                locale = (CompoundVariable) values[3];
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