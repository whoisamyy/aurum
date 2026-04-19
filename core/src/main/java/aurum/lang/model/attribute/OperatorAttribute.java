package aurum.lang.model.attribute;

import aurum.lang.model.Attribute;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class OperatorAttribute implements Attribute {
    private final String symbol;
    private final int precedence;
    private final Associativity associativity;
    private final OperatorType type;

    public OperatorAttribute(String symbol, int precedence, Associativity associativity, OperatorType type) {
        this.symbol = symbol;
        this.precedence = precedence;
        this.associativity = associativity;
        this.type = type;
    }

    @Override
    public @NotNull String name() {
        return "Operator";
    }

    @Override
    public @NotNull Map<@NotNull String, Object> values() {
        return Map.of(
                "symbol", symbol,
                "precedence", precedence,
                "associativity", associativity,
                "type", type
        );
    }

    public enum Associativity {
        LEFT_TO_RIGHT, RIGHT_TO_LEFT
    }

    public enum OperatorType {
        BINARY, UNARY
    }
}
