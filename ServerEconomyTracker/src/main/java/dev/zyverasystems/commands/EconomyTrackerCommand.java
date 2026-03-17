package dev.zyverasystems.commands;

import dev.zyverasystems.ServerEconomyTracker;
import dev.zyverasystems.utils.EconomyTotals;
import dev.zyverasystems.utils.EconomyTrackerService;
import dev.zyverasystems.utils.MessagesManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EconomyTrackerCommand implements TabExecutor {

    private final ServerEconomyTracker plugin;
    private final EconomyTrackerService trackerService;
    private final MessagesManager messages;
    private final DecimalFormat decimalFormat;

    public EconomyTrackerCommand(ServerEconomyTracker plugin, EconomyTrackerService trackerService, MessagesManager messages) {
        this.plugin = plugin;
        this.trackerService = trackerService;
        this.messages = messages;

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setDecimalSeparator('.');
        symbols.setGroupingSeparator(',');

        this.decimalFormat = new DecimalFormat("#,##0.00", symbols);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(messages.get("general.usage", MessagesManager.Placeholder.of("label", label)));
            return true;
        }

        // Stats Command
        if (args[0].equalsIgnoreCase("stats")) {
            if (!sender.hasPermission("economytracker.stats")) {
                sender.sendMessage(messages.get("general.no-permission"));
                return true;
            }

            EconomyTotals totals = trackerService.getTotals();

            if (totals == null) {
                sender.sendMessage(messages.get("general.totals-not-loaded"));
                return true;
            }

            sender.sendMessage(messages.get("stats.header"));
            sender.sendMessage(messages.get("stats.title"));
            sender.sendMessage(messages.get("stats.current-total-balance", MessagesManager.Placeholder.of("value", format(totals.getCurrentTotalBalance()))));
            sender.sendMessage(messages.get("stats.total-sources", MessagesManager.Placeholder.of("value", format(totals.getTotalSources()))));
            sender.sendMessage(messages.get("stats.total-sinks", MessagesManager.Placeholder.of("value", format(totals.getTotalSinks()))));
            sender.sendMessage(messages.get("stats.total-transfer-volume", MessagesManager.Placeholder.of("value", format(totals.getTotalTransferVolume()))));
            sender.sendMessage(messages.get("stats.total-net-change", MessagesManager.Placeholder.of("value", format(totals.getTotalNetChange()))));
            sender.sendMessage(messages.get("stats.footer"));
            return true;
        }

        // Reload Command
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("economytracker.reload")) {
                sender.sendMessage(messages.get("general.no-permission"));
                return true;
            }

            plugin.reloadPluginFiles();
            sender.sendMessage(messages.get("general.reload-success"));
            return true;
        }

        sender.sendMessage(messages.get("general.unknown-subcommand"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);

            if (sender.hasPermission("economytracker.stats") && "stats".startsWith(input)) {
                completions.add("stats");
            }

            if (sender.hasPermission("economytracker.reload") && "reload".startsWith(input)) {
                completions.add("reload");
            }
        }

        return completions;
    }

    private String format(BigDecimal value) {
        return decimalFormat.format(value);
    }
}