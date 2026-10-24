package dev.zyverasystems.commands;

import dev.zyverasystems.ServerEconomyTracker;
import dev.zyverasystems.utils.EconomyTotals;
import dev.zyverasystems.utils.EconomyTrackerService;
import dev.zyverasystems.utils.MessagesManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

        // Baseline Command
        if (args[0].equalsIgnoreCase("baseline")) {
            if (!sender.hasPermission("economytracker.baseline")) {
                sender.sendMessage(messages.get("general.no-permission"));
                return true;
            }

            handleBaseline(sender, label, args);
            return true;
        }

        sender.sendMessage(messages.get("general.unknown-subcommand"));
        return true;
    }

    private void handleBaseline(CommandSender sender, String label, String[] args) {
        if (trackerService.getTotals() == null) {
            sender.sendMessage(messages.get("general.totals-not-loaded"));
            return;
        }

        if (args.length == 1) {
            sender.sendMessage(messages.get("baseline.current", MessagesManager.Placeholder.of("value", format(trackerService.getBaseline()))));
            sender.sendMessage(messages.get("baseline.usage", MessagesManager.Placeholder.of("label", label)));
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);

        // A running scan overwrites the total at its end, so changes now would get lost.
        if (trackerService.isScanning()) {
            sender.sendMessage(messages.get("baseline.scan-running"));
            return;
        }

        if (action.equals("recalculate")) {
            sender.sendMessage(messages.get("baseline.recalculate-started"));
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    BigDecimal total = trackerService.recalculateBaseline();
                    sender.sendMessage(messages.get("baseline.recalculate-success",
                            MessagesManager.Placeholder.of("value", format(trackerService.getBaseline())),
                            MessagesManager.Placeholder.of("total", format(total))));
                } catch (Exception e) {
                    plugin.getLogger().severe("Error during baseline recalculation: " + e.getMessage());
                    e.printStackTrace();
                    sender.sendMessage(messages.get("baseline.error"));
                }
            });
            return;
        }

        if (!action.equals("set") && !action.equals("add") && !action.equals("remove")) {
            sender.sendMessage(messages.get("baseline.usage", MessagesManager.Placeholder.of("label", label)));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(messages.get("baseline.usage", MessagesManager.Placeholder.of("label", label)));
            return;
        }

        BigDecimal amount = parseAmount(args[2]);
        if (amount == null || (!action.equals("set") && amount.signum() <= 0)) {
            sender.sendMessage(messages.get("baseline.invalid-amount", MessagesManager.Placeholder.of("input", args[2])));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                switch (action) {
                    case "set" -> trackerService.setBaseline(amount);
                    case "add" -> trackerService.adjustBaseline(amount);
                    case "remove" -> trackerService.adjustBaseline(amount.negate());
                }

                sender.sendMessage(messages.get("baseline.updated",
                        MessagesManager.Placeholder.of("value", format(trackerService.getBaseline())),
                        MessagesManager.Placeholder.of("total", format(trackerService.getTotals().getCurrentTotalBalance()))));
            } catch (Exception e) {
                plugin.getLogger().severe("Error while updating baseline: " + e.getMessage());
                e.printStackTrace();
                sender.sendMessage(messages.get("baseline.error"));
            }
        });
    }

    private BigDecimal parseAmount(String input) {
        try {
            return new BigDecimal(input.replace("_", "")).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
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

            if (sender.hasPermission("economytracker.baseline") && "baseline".startsWith(input)) {
                completions.add("baseline");
            }
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("baseline") && sender.hasPermission("economytracker.baseline")) {
            String input = args[1].toLowerCase(Locale.ROOT);

            for (String option : List.of("set", "add", "remove", "recalculate")) {
                if (option.startsWith(input)) {
                    completions.add(option);
                }
            }
        }

        return completions;
    }

    private String format(BigDecimal value) {
        return decimalFormat.format(value);
    }
}