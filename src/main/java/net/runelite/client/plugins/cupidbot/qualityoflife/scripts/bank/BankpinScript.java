package net.runelite.client.plugins.cupidbot.qualityoflife.scripts.bank;

import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.qualityoflife.QoLConfig;
import net.runelite.client.plugins.cupidbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.cupidbot.util.security.Encryption;
import net.runelite.client.plugins.cupidbot.util.security.Login;
import net.runelite.client.plugins.cupidbot.util.security.LoginManager;

import java.util.concurrent.TimeUnit;

public class BankpinScript extends Script {
    public boolean run(QoLConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!CupidBot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!config.useBankPin()) return;
                if ((LoginManager.getActiveProfile().getBankPin() == null || LoginManager.getActiveProfile().getBankPin().isEmpty()) || LoginManager.getActiveProfile().getBankPin().equalsIgnoreCase("**bankpin**")) return;

                Rs2Bank.handleBankPin(Encryption.decrypt(LoginManager.getActiveProfile().getBankPin()));

            } catch(Exception ex) {
                CupidBot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }
}
