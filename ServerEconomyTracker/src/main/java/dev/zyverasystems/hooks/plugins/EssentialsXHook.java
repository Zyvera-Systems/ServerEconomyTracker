package dev.zyverasystems.hooks.plugins;

import com.earth2me.essentials.User;
import dev.zyverasystems.ServerEconomyTracker;
import dev.zyverasystems.hooks.EconomyWealthHook;
import net.ess3.api.IEssentials;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.util.UUID;

// EssentialsX stores bank/town accounts of other plugins (e.g. Towny) as NPC users.
public class EssentialsXHook implements EconomyWealthHook {

    private final ServerEconomyTracker plugin;

    public EssentialsXHook(ServerEconomyTracker plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "EssentialsX";
    }

    @Override
    public boolean isAvailable() {
        return plugin.getServer().getPluginManager().getPlugin("Essentials") instanceof IEssentials;
    }

    // Loads every Essentials user from disk, so only call this async (baseline / recalculate).
    @Override
    public BigDecimal getAccountWealth() {
        Plugin essentialsPlugin = plugin.getServer().getPluginManager().getPlugin("Essentials");
        if (!(essentialsPlugin instanceof IEssentials essentials)) return BigDecimal.ZERO;

        BigDecimal total = BigDecimal.ZERO;

        for (UUID uuid : essentials.getUsers().getAllUserUUIDs()) {
            User user = essentials.getUsers().loadUncachedUser(uuid);
            if (user == null || !user.isNPC()) continue;

            BigDecimal money = user.getMoney();
            if (money != null) total = total.add(money);
        }

        return total;
    }
}
