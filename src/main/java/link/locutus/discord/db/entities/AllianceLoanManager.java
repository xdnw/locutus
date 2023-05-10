package link.locutus.discord.db.entities;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.util.scheduler.TriConsumer;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public class AllianceLoanManager {
    private final Map<Long, Set<AllianceLoan>> loansByAccount = new ConcurrentHashMap<>();
    private final Map<Integer, Set<AllianceInvestment>> investmentsByNation = new ConcurrentHashMap<>();
    public AllianceLoanManager(GuildDB db) {
        for (AllianceLoan loan : db.getAllianceLoans()) {
            loansByAccount.computeIfAbsent(loan.receiver, k -> ConcurrentHashMap.newKeySet()).add(loan);
        }
        for (AllianceInvestment investment : db.getAllianceInvestments()) {
            investmentsByNation.computeIfAbsent(investment.nationId, k -> ConcurrentHashMap.newKeySet()).add(investment);
        }

        calculateTotals();
    }

    public double[] getFreeFunds() {
            double[] funds = getFunds();
        double[] amountOnLoan = getAmountOnLoan();
        double[] freeFunds = new double[ResourceType.values.length];
        for (int i = 0; i < ResourceType.values.length; i++) {
            freeFunds[i] = funds[i] - amountOnLoan[i];
        }
        return freeFunds;
    }

    public void calculateTotals() {
        for (Map.Entry<Long, Set<AllianceLoan>> entry : loansByAccount.entrySet()) {
            for (AllianceLoan loan : entry.getValue()) {
                double[] principal = loan.principal;
                double[] interestPaid = loan.interestPaid;

                for (int i = 0; i < ResourceType.values.length; i++) {
                    fundsCents[i] -= Math.round(principal[i] * 100);
                    fundsCents[i] += Math.round(interestPaid[i] * 100);
                }
            }
        }
        // set funds_public to
        for (Map.Entry<Integer, Set<AllianceInvestment>> entry : investmentsByNation.entrySet()) {
            for (AllianceInvestment investment : entry.getValue()) {
                double[] resources = investment.resources;
                for (int i = 0; i < ResourceType.values.length; i++) {
                    fundsCents[i] += Math.round(resources[i] * 100);
                }
            }
        }
    }

    public double[] getFunds(Predicate<AllianceInvestment.InvestmentType> type) {
        // add all the funds that don't require collateral

        // get max
        // get min

        // binary search allowable

        double[] funds = new double[ResourceType.values.length];
        for (int i = 0; i < funds.length; i++) {
            funds[i] = fundsCents[i] / 100d;
        }
        return funds;
    }

    public double[] getAmountOnLoan() {
        double[] amount = ResourceType.getBuffer();
        for (Map.Entry<Long, Set<AllianceLoan>> entry : loansByAccount.entrySet()) {
            for (AllianceLoan loan : entry.getValue()) {
                double[] principal = loan.principal;
                for (int i = 0; i < ResourceType.values.length; i++) {
                    double repaid = Math.max(0, loan.principal[i] - loan.interestPaid[i]);
                    amount[i] += repaid;
                }
            }
        }
        return amount;
    }

    public Map<Long, Set<AllianceLoan>> getLoansByAccount() {
        return loansByAccount;
    }

    public Map<Integer, Set<AllianceInvestment>> getInvestmentsByNation() {
        return investmentsByNation;
    }

    public double[] getInvestorTotal(AllianceInvestment.InvestmentType type) {
        double[] amount = ResourceType.getBuffer();
        for (Map.Entry<Integer, Set<AllianceInvestment>> entry : investmentsByNation.entrySet()) {
            for (AllianceInvestment investment : entry.getValue()) {
                if (investment.type == type) {
                    double[] resources = investment.resources;
                    for (int i = 0; i < ResourceType.values.length; i++) {
                        amount[i] = Math.round(resources[i] * 100 + amount[i] * 100) / 100d;
                    }
                }
            }
        }
        return amount;
    }

    public void runTurnTasks() {

    }
}
