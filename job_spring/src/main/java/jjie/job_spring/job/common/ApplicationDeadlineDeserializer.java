package jjie.job_spring.job.common;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

// DATE 只有日历日期，不接受时间戳、时区或 Jackson 默认支持的数组形式。
public class ApplicationDeadlineDeserializer extends StdDeserializer<LocalDate> {
    public static final String ERROR_MESSAGE = "截止日期须为1000到9999年范围内的有效日期（YYYY-MM-DD）";

    public ApplicationDeadlineDeserializer() {
        super(LocalDate.class);
    }

    @Override
    public LocalDate deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.hasToken(JsonToken.VALUE_STRING)) {
            String value = parser.getString().strip();
            if (value.isEmpty()) return null;
            if (value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                try {
                    LocalDate date = LocalDate.parse(value);
                    if (date.getYear() >= 1000) return date;
                } catch (DateTimeParseException exception) {
                    // 例如 2026-02-30，拒绝整次请求，不自动纠正日期。
                }
            }
        }
        return context.reportInputMismatch(LocalDate.class, ERROR_MESSAGE);
    }
}
