package dev.zyverasystems.utils;

import java.math.BigDecimal;

public class EconomyTotals {

    private boolean baselineImportDone;
    private BigDecimal currentTotalBalance;
    private BigDecimal totalSources;
    private BigDecimal totalSinks;
    private BigDecimal totalTransferVolume;
    private BigDecimal totalNetChange;
    private long updatedAt;

    public EconomyTotals(
            boolean baselineImportDone,
            BigDecimal currentTotalBalance,
            BigDecimal totalSources,
            BigDecimal totalSinks,
            BigDecimal totalTransferVolume,
            BigDecimal totalNetChange,
            long updatedAt
    ) {
        this.baselineImportDone = baselineImportDone;
        this.currentTotalBalance = currentTotalBalance;
        this.totalSources = totalSources;
        this.totalSinks = totalSinks;
        this.totalTransferVolume = totalTransferVolume;
        this.totalNetChange = totalNetChange;
        this.updatedAt = updatedAt;
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