package jjie.job_spring.interview.common;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

// 只约束本接口字段，避免 Jackson 将 1.5 截断为 1 或把字符串强制转换为整数。
public class RoundNoDeserializer extends StdDeserializer<Integer> {
    public RoundNoDeserializer() {
        super(Integer.class);
    }

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            return (Integer) context.handleUnexpectedToken(Integer.class, parser);
        }
        return parser.getIntValue();
    }
}
