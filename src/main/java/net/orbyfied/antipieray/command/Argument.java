package net.orbyfied.antipieray.command;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Represents an argument or flag on a subcommand.
 */
public record Argument<T>(boolean isFlag, boolean isSwitch, String name,
                          String[] aliases, Parser<T> parser,
                          boolean isRequired) {
    public interface Parser<T> {
        String getName();                                 // Get the name of this type
        Optional<T> parse(String str);                    // Parse a value from a string
        void complete(List<String> output, String input); // Attempts to complete the given input

        default Parser<T> completes(BiConsumer<List<String>, String> f) {
            return new Parser<T>() {
                @Override
                public String getName() {
                    return Parser.this.getName();
                }

                @Override
                public Optional<T> parse(String str) {
                    return Parser.this.parse(str);
                }

                @Override
                public void complete(List<String> output, String input) {
                    Parser.this.complete(output, input);
                    f.accept(output, input);
                }
            };
        }
    }

    public static <T> Parser<T> namedParser(String name, Function<String, Optional<T>> function) {
        return new Parser<T>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public Optional<T> parse(String str) {
                return function.apply(str);
            }

            @Override
            public void complete(List<String> output, String input) {

            }
        };
    }

    public static <T> Argument<T> arg(String name, Parser<T> parser, String... aliases) {
        return new Argument<>(false, false, name, aliases, parser, true);
    }

    private static final Parser<String> STRING_PARSER = namedParser("string", Optional::of);
    private static final Parser<Long> INTEGER_PARSER = namedParser("integer", s -> {
        try {
            return Optional.of(Long.parseLong(s));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    });
    private static final Parser<Double> NUMBER_PARSER = namedParser("number", s -> {
        try {
            return Optional.of(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    });
    private static final Parser<Boolean> BOOL_PARSER = namedParser("bool", s -> switch (s) {
        case "true", "TRUE", "yes", "y", "1" -> Optional.of(true);
        case "false", "FALSE", "no", "n", "0" -> Optional.of(false);
        default -> Optional.empty();
    }).completes((o, i) -> {
        o.add("true"); o.add("yes"); o.add("y");
        o.add("false"); o.add("no"); o.add("n");
    });
    private static final Parser<Player> ONLINE_PLAYER_PARSER = namedParser("onlinePlayer", s -> {
        // try to find by UUID
        try {
            Player player = Bukkit.getPlayer(UUID.fromString(s));
            return player == null ? Optional.empty() : Optional.of(player);
        } catch (IllegalArgumentException ignored) { }

        // try to find by username
        Player player = Bukkit.getPlayer(s);
        return player == null ? Optional.empty() : Optional.of(player);
    }).completes((o, i) -> {
        for (Player player : Bukkit.getOnlinePlayers()) {
            o.add(player.getName());
        }
    });

    public static Argument<String> string(String name) {
        return arg(name, STRING_PARSER);
    }

    public static Argument<Long> integer(String name) {
        return arg(name, INTEGER_PARSER);
    }

    public static Argument<Double> number(String name) {
        return arg(name, NUMBER_PARSER);
    }

    public static Argument<Boolean> bool(String name) {
        return arg(name, BOOL_PARSER);
    }

    public static Argument<Player> onlinePlayer(String name) {
        return arg(name, ONLINE_PLAYER_PARSER);
    }

    public static Argument<Boolean> boolSwitch(String name, String... aliases) {
        return new Argument<>(true, true, name, aliases, null, true);
    }

    public Argument<T> optional() {
        return new Argument<>(isFlag, isSwitch, name, aliases, parser, false);
    }
}
