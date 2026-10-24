package dev.zyverasystems.utils;

import java.math.BigDecimal;

public class EconomyTotals {

    private boolean baselineImportDone;
    private BigDecimal currentTotalBalance;
    private BigDecimal totalSources;
    private BigDecimal totalSinks;
    private BigDecimal totalTransferVolume;
    private BigDecimal totalNetChange;
    // Money the scan cannot see (e.g. a bank plugin without Vault support), set by an admin.
    // Part of the baseline and kept when the baseline is recalculated.
    private BigDecimal manualCorrection;
    private long updatedAt;

    public EconomyTotals(
            boolean baselineImportDone,
            BigDecimal currentTotalBalance,
            BigDecimal totalSources,
            BigDecimal totalSinks,
            BigDecimal totalTransferVolume,
            BigDecimal totalNetChange,
            BigDecimal manualCorrection,
            long updatedAt
    ) {
        this.baselineImportDone = baselineImportDone;
        this.currentTotalBalance = currentTotalBalance;
        this.totalSources = totalSources;
        this.totalSinks = totalSinks;
        this.totalTransferVolume = totalTransferVolume;
        this.totalNetChange = totalNetChange;
        this.manualCorrection = manualCorrection;
        this.updatedAt = updatedAt;
    }

    public EconomyTotals copy() {
        return new EconomyTotals(
                baselineImportDone, currentTotalBalance, totalSources, totalSinks,
                totalTransferVolume, totalNetChange, manualCorrection, updatedAt
        );
    }

    public BigDecimal getManualCorrection() {
        return manualCorrection;
    }

    public void setManualCorrection(BigDecimal manualCorrection) {
        this.manualCorrection = manualCorrection;
    }

    public boolean isBaselineImportDone() {
        return baselineImportDone;
    }

    public void setBaselineImportDone(boolean baselineImportDone) {
        this.baselineImportDone = baselineImportDone;
    }

    public BigDecimal getCurrentTotalBalance() {
        return currentTotalBalance;
    }

    public void setCurrentTotalBalance(BigDecimal currentTotalBalance) {
        this.currentTotalBalance = currentTotalBalance;
    }

    public BigDecimal getTotalSources() {
        return totalSources;
    }

    public void setTotalSources(BigDecimal totalSources) {
        this.totalSources = totalSources;
    }

    public BigDecimal getTotalSinks() {
        return totalSinks;
    }

    public void setTotalSinks(BigDecimal totalSinks) {
        this.totalSinks = totalSinks;
    }

    public BigDecimal getTotalTransferVolume() {
        return totalTransferVolume;
    }

    public void setTotalTransferVolume(BigDecimal totalTransferVolume) {
        this.totalTransferVolume = totalTransferVolume;
    }

    public BigDecimal getTotalNetChange() {
        return totalNetChange;
    }

    public void setTotalNetChange(BigDecimal totalNetChange) {
        this.totalNetChange = totalNetChange;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}