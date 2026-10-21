package jjie.job_spring.interview.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

// DATETIME 保存本地时间；拒绝携带 Z/偏移量的输入，避免静默丢弃时区含义。
public class InterviewTimeDeserializer extends StdDeserializer<LocalDateTime> {
    public InterviewTimeDeserializer() {
        super(LocalDateTime.class);
    }

    @Override
    public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return (LocalDateTime) context.handleUnexpectedToken(LocalDateTime.class, parser);
        }
        try {
            return LocalDateTime.parse(parser.getString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException exception) {
            return (LocalDateTime) context.handleWeirdStringValue(LocalDateTime.class,
                    parser.getString(), "请使用不带时区的有效本地日期时间");
        }
    }
}
