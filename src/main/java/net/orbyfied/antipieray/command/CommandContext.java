package net.orbyfied.antipieray.command;

import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Represents the context in which a command runs */
public class CommandContext {

    public static class CommandError extends RuntimeException {
        public CommandError() {

        }

        public CommandError(String message) {
            super(message);
        }

        public CommandError(String message, Throwable cause) {
            super(message, cause);
        }

        public CommandError(Throwable cause) {
            super(cause);
        }
    }

    protected Function<String, String> errorFormatter;
    protected CommandSender sender;
    protected final Map<String, Object> values = new HashMap<>();
    protected boolean isCompleting = false;

    public Function<String, String> getErrorFormatter() {
        return errorFormatter;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public Object fail(String msg) {
        throw new CommandError(msg);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String name) {
        return values.containsKey(name) ? Optional.of((T) values.get(name)) : Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String name, Class<T> tClass) {
        return values.containsKey(name) ? Optional.of((T) values.get(name)) : Optional.empty();
    }

    public CommandSender sender() {
        return sender;
    }

    @SuppressWarnings("unchecked")
    public <T> T sender(Class<T> tClass) {
        if (tClass.isInstance(sender))
            return (T) sender;
        throw new CommandError("Command can only be run by " + tClass.getSimpleName() + " senders");
    }

}
