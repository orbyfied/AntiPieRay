package net.orbyfied.antipieray.command;

import net.orbyfied.antipieray.util.TextUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Base command class for a crude, quick attempt at a simple
 * args array based command system to make writing debug commands
 * faster.
 */
public class BaseCommand extends Subcommand implements TabExecutor {

    public static Subcommand subcommand(String name, String... aliases) {
        return new Subcommand(name, aliases);
    }

    private final Plugin plugin;

    public BaseCommand(Plugin plugin, String name, String... aliases) {
        super(name, aliases);
        this.plugin = plugin;
    }

    /* Configuration */
    private Function<String, String> errorFormatter = s -> ChatColor.RED + TextUtil.translate(s);

    /**
     * Set the error formatter for this base command.
     *
     * @param errorFormatter The error formatter.
     * @return This.
     */
    public BaseCommand setErrorFormatter(Function<String, String> errorFormatter) {
        this.errorFormatter = errorFormatter;
        return this;
    }

    // Builds a pretty string of arguments
    private String buildArgList(List<Argument<?>> arguments) {
        StringBuilder b = new StringBuilder();
        for (int j = 0; j < arguments.size(); j++) {
            if (j != 0) {
                b.append("&c, ");
            }

            Argument<?> arg = arguments.get(j);
            b.append("&e").append(arg.parser().getName()).append(" &f").append(arg.name());
        }

        return b.toString();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        CommandContext context = new CommandContext();
        context.sender = sender;

        Subcommand current = this;
        Executor lastExecutor = null;
        int i = 0, l = args.length;
        while (i < l) {
            String argStr = args[i];

            // find next command
            Subcommand old = current;
            current = current.subcommands.get(argStr);
            if (current == null && old.arguments.isEmpty()) {
                sender.sendMessage(errorFormatter.apply("No subcommand by name &f" + argStr));
                return false;
            }

            if (current == null) {
                current = old;
                break;
            }

            if (current.executor != null) {
                lastExecutor = current.executor;
            }

            i++;
        }

        // check permissions
        if (current.permission != null && !sender.hasPermission(current.permission)) {
            sender.sendMessage(errorFormatter.apply("You do not have permission to execute this command"));
            return false;
        }

        // now try to parse any arguments/flags
        int argIndex = 0;
        for (; i < l; i++) {
            String argStr = args[i];

            /*
                Flags
             */
            if (argStr.startsWith("-")) {
                argStr = argStr.substring(argStr.startsWith("--") ? 2 : 1);

                // find flag
                Argument<?> flag = current.flags.get(argStr);
                if (flag == null) {
                    sender.sendMessage(errorFormatter.apply("No flag by alias &f" + argStr));
                    return false;
                }

                if (flag.isSwitch()) {
                    context.values.put(flag.name(), true);
                } else {
                    // parse value
                    i++;
                    if (i == l) {
                        sender.sendMessage(errorFormatter.apply("Expected value for flag &f" + argStr));
                        return false;
                    }

                    Optional<?> optional = flag.parser().parse(args[i]);
                    if (optional.isEmpty()) {
                        sender.sendMessage(errorFormatter.apply("Expected value of type &f" + flag.parser().getName() + " &cfor flag &f" + argStr));
                        return false;
                    }

                    context.values.put(flag.name(), optional.get());
                }

                continue;
            }

            /*
                Positional Arguments
             */

            if (argIndex >= current.arguments.size()) {
                sender.sendMessage(errorFormatter.apply("Too many positional arguments provided, expected " + buildArgList(current.arguments)));
                return false;
            }

            Argument<?> arg = current.arguments.get(argIndex++);
            Optional<?> optional = arg.parser().parse(args[i]);
            if (optional.isEmpty()) {
                sender.sendMessage(errorFormatter.apply("Expected value of type &e" + arg.parser().getName() + " &cfor argument &f" + argStr));
                return false;
            }

            context.values.put(arg.name(), optional.get());
        }

        // check if all required arguments were parsed
        if (argIndex < current.arguments.size() && current.arguments.get(argIndex).isRequired()) {
            sender.sendMessage(errorFormatter.apply("Missing required arguments " + buildArgList(current.arguments.subList(argIndex, current.arguments.size()))));
            return false;
        }

        // run last executor
        if (lastExecutor == null) {
//            sender.sendMessage(errorFormatter.apply("No command to run wtf"));
            return false;
        }

        try {
            lastExecutor.execute(context);
            return true;
        } catch (CommandContext.CommandError error) {
            String message = error.getMessage();
            if (message == null) {
                message = "An unexpected error occurred";
                if (error.getCause() != null) {
                    message += ": " + error.getCause().getClass().getSimpleName() + ": " + error.getMessage();
                }
            }

            sender.sendMessage(errorFormatter.apply(message));

            if (error.getCause() != null) {
                plugin.getLogger().warning("CommandSystem: error in executor: '" + message + "'");
                error.printStackTrace();
            }

            return false;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> suggestions = new ArrayList<>();

        CommandContext context = new CommandContext();
        context.sender = sender;
        context.isCompleting = true;

        Subcommand current = this;
        int i = 0, l = args.length;
        while (i < l) {
            String argStr = args[i];

            // check permissions
            if (current.permission != null && !sender.hasPermission(current.permission)) {
                return List.of();
            }

            // find next command
            Subcommand old = current;
            current = current.subcommands.get(argStr);
            if (current == null) {
                current = old;
                break;
            }

            i++;
        }

        // suggest literals
        suggestions.addAll(current.subcommands.keySet());

        // now try to parse any arguments/flags
        Argument<?> currentArg = null;
        int argIndex = 0;
        for (; i < l; i++) {
            /*
                Flags
             */
            String argStr = args[i];
            if (argStr.startsWith("--")) {
                argStr = argStr.substring(argStr.startsWith("--") ? 2 : 1);

                // find flag
                Argument<?> flag = current.flags.get(argStr);
                if (flag != null && !flag.isSwitch()) {
                    currentArg = flag;
                }

                continue;
            }

            /*
                Positional Arguments
             */

            if (argIndex < current.arguments.size()) {
                currentArg = current.arguments.get(argIndex++);
            }
        }

        // suggest argument values
        String lastArgStr = args[args.length - 1];
        if (currentArg != null) {
            currentArg.parser().complete(suggestions, lastArgStr);
        }

        // suggest flag names
        for (String alias : current.flags.keySet()) {
            suggestions.add("--" + alias);
        }

        return StringUtil.copyPartialMatches(lastArgStr, suggestions, new ArrayList<>());
    }

}
