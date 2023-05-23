package link.locutus.discord.db.entities;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.scheduler.TriConsumer;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class AllianceLoanManager {
    private final Map<Long, Set<AllianceLoan>> loansByAccount = new ConcurrentHashMap<>();
    private final Map<Integer, Map<AllianceInvestment.InvestmentType, AllianceInvestment>> investmentsByNation = new ConcurrentHashMap<>();
    private final Map<Long, Map<AllianceInvestment.InvestmentType, AllianceInvestment>> investmentsByAccount = new ConcurrentHashMap<>();
    private final OffshoreInstance offshore;

    public AllianceLoanManager(OffshoreInstance offshore) {
        this.offshore = offshore;
        GuildDB db = offshore.getGuildDB();

        for (AllianceLoan loan : db.getAllianceLoans()) {
            loansByAccount.computeIfAbsent(loan.receiver, k -> new HashSet<>()).add(loan);
        }
        for (AllianceInvestment investment : db.getAllianceInvestments()) {
            if (investment.accountType == 1 && investment.accountId < Integer.MAX_VALUE) {
                // nation
                investmentsByNation.computeIfAbsent((int) investment.accountId, k -> new ConcurrentHashMap<>()).put(investment.type, investment);
            } else if (investment.accountType == 2 && investment.accountId < Integer.MAX_VALUE) {
                // alliance
                investmentsByAccount.computeIfAbsent(investment.accountId, k -> new ConcurrentHashMap<>()).put(investment.type, investment);
            } else if (investment.accountType == 3 && investment.accountId > Integer.MAX_VALUE) {
                // guild
                investmentsByAccount.computeIfAbsent(investment.accountId, k -> new ConcurrentHashMap<>()).put(investment.type, investment);
            } else {
                throw new IllegalStateException("Invalid account type: " + investment.accountId + " | " + investment.accountType);
            }

        }
    }

    public Set<AllianceInvestment> getInvestments() {
        Set<AllianceInvestment> investments = new HashSet<>();
        investmentsByNation.values().forEach(f -> investments.addAll(f.values()));
        investmentsByAccount.values().forEach(f -> investments.addAll(f.values()));
        return investments;
    }

    private Triple<double[], double[], Double> getDeposits(long accountId, int accountType, boolean includeLoansInMaxWithdraw, boolean includeLoansInMaxWithdrawValue, boolean includeInvestments, boolean throwError) {
        double[] deposits;
        double[] maxWithdraw;
        double maxWithdrawValue;

        if (accountType == 2) {
            deposits = PnwUtil.resourcesToArray(offshore.getDepositsAA(Collections.singleton((int) accountId), false));
        } else if (accountType == 3) {
            try {
                deposits = PnwUtil.resourcesToArray(offshore.getDeposits(accountId, false));
            } catch (IllegalArgumentException e) {
                if (throwError) {
                    throw e;
                }
                deposits = ResourceType.getBuffer();
            }
        } else {
            throw new IllegalStateException("Invalid account type: " + accountId + " | " + accountType);
        }

        double[] maxWithdrawInclLoans = deposits.clone();
        if (includeLoansInMaxWithdraw || includeLoansInMaxWithdrawValue) {
            Set<AllianceLoan> loans = loansByAccount.get(accountId);
            if (loans != null) {
                boolean hasLoan = false;
                for (AllianceLoan loan : loans) {
                    if (loan.isClosed()) continue;
                    hasLoan = true;
                    Map.Entry<double[], Double> owed = loan.getAmountOwed();
                    double[] resources = owed.getKey();
                    double money = owed.getValue();
                    for (int i = 0; i < resources.length; i++) {
                        maxWithdrawInclLoans[i] -= resources[i];
                    }
                    maxWithdrawInclLoans[ResourceType.MONEY.ordinal()] -= money;
                }
            }
        }

        maxWithdraw = includeLoansInMaxWithdraw ? maxWithdrawInclLoans.clone() : deposits.clone();

        if (includeInvestments) {
            AllianceInvestment investment = investmentsByAccount.getOrDefault(accountId, Collections.emptyMap()).get(AllianceInvestment.InvestmentType.RESERVE);
            if (investment != null) {
                double[] reserve = investment.resources;

                boolean checkFree = false;

                for (ResourceType type : ResourceType.values) {
                    if (maxWithdrawInclLoans[type.ordinal()] > reserve[type.ordinal()]) {
                        checkFree = true;
                        break;
                    }
                }
                // if !isempty missing
                // get free funds global
                // add only the free ones
            }
        }



        return new AbstractMap.SimpleEntry<>(depo, depoWithLoans);
    }

    private double[] getAmount(AllianceInvestment investment) {
        if (investment.type == AllianceInvestment.InvestmentType.RESERVE) {
            double[] reserve = investment.resources;
            Map.Entry<double[], double[]> depoPair = getDeposits(investment.accountId, investment.accountType, false);
            double[] depo = depoPair.getValue();
            if (ResourceType.isEmpty(depo)) {
                return null;
            }
            for (ResourceType type : ResourceType.values) {
                depo[type.ordinal()] -= reserve[type.ordinal()];
            }
            depo = PnwUtil.normalize(depo);
            if (PnwUtil.convertedTotal(depo) <= 1) {
                return null;
            }
            return depo;
        } else {
            return investment.resources;
        }
    }

    public double[] getFreeFunds(boolean forWithdrawal, boolean allowInterest) {
        double[] amountOnLoan = getAmountOnLoan();
        double[] freeFunds = ResourceType.builder().subtract(amountOnLoan).build();

        Set<AllianceInvestment> investments = getInvestments();

        if (forWithdrawal) {
            for (AllianceInvestment investment : investments) {
                double[] resources = getAmount(investment);
                if (resources == null || ResourceType.isEmpty(resources)) continue;
                for (int i = 0; i < ResourceType.values.length; i++) {
                    freeFunds[i] += resources[i];
                }
            }
        } else {
            Predicate<AllianceInvestment> accept = investment -> {
                switch (investment.type) {
                    case DONATION -> {
                        return true;
                    }
                    case SHARE -> {
                        return allowInterest;
                    }
                    case RESERVE -> {
                        if (investment.accountType == 1) {
                            throw new IllegalStateException("Invalid nation investment type: " + investment.accountId + " | " + investment.accountType + " | " + investment.type);
                        }
                        return allowInterest;
                    }
                    default -> {
                        throw new IllegalArgumentException("Invalid investment type: " + investment.type);
                    }
                }
            };
            investments = investments.stream().filter(accept).collect(Collectors.toSet());

            Map<ResourceType, List<AllianceInvestment>> investmentsByResource = new ConcurrentHashMap<>();
            Map<ResourceType, List<AllianceInvestment>> reservesByResource = new ConcurrentHashMap<>();

            Map<AllianceInvestment, double[]> amounts = new HashMap<>();
            Map<AllianceInvestment, double[]> reserveAbs = new HashMap<>();

            for (AllianceInvestment investment : investments) {
                double[] resources = getAmount(investment);
                if (resources == null || ResourceType.isEmpty(resources)) continue;

                amounts.put(investment, resources);
                for (ResourceType type : ResourceType.values) {
                    investmentsByResource.computeIfAbsent(type, t -> new ArrayList<>()).add(investment);
                }

                if (investment.reserveRatio > 0) {
                    double[] requiredReserve = resources.clone();
                    for (ResourceType type : ResourceType.values) {
                        requiredReserve[type.ordinal()] *= investment.reserveRatio;
                    }

                    reserveAbs.put(investment, requiredReserve);
                    for (ResourceType type : ResourceType.values) {
                        reservesByResource.computeIfAbsent(type, t -> new ArrayList<>()).add(investment);
                    }
                }
            }
            for (Map.Entry<AllianceInvestment, double[]> entry : amounts.entrySet()) {
                freeFunds = PnwUtil.add(freeFunds, entry.getValue());
            }

            Set<AllianceInvestment> excluded = new HashSet<>();

            for (ResourceType type : ResourceType.values) {
                List<AllianceInvestment> reserveInvestments = reservesByResource.get(type);
                if (reserveInvestments == null || reserveInvestments.isEmpty()) continue;
                double amtFree = freeFunds[type.ordinal()];

                while (true) {
                    Iterator<AllianceInvestment> iter = reserveInvestments.iterator();
                    boolean anyRemoved = false;
                    while (iter.hasNext()) {
                        AllianceInvestment investment = iter.next();
                        double[] reserve = reserveAbs.get(investment);
                        if (Math.round(reserve[type.ordinal()] * 100) > Math.round(amtFree * 100)) {
                            anyRemoved = true;
                            iter.remove();

                            double[] amount = amounts.get(investment);
                            freeFunds[type.ordinal()] -= amount[type.ordinal()];

                            excluded.add(investment);
                        }
                    }
                    if (!anyRemoved) {
                        break;
                    }
                    if (reserveInvestments.isEmpty()) break;
                }
            }
        }

        return freeFunds;
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

    public Set<AllianceInvestment> getInvestments(long account, int accountType) {
        switch (accountType) {
            case 1:
                return investmentsByNation.getOrDefault((int) account, Collections.emptySet());
            case 2:
                if (account > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid alliance account id " + account);
                return investmentsByAccount.getOrDefault(account, Collections.emptySet());
            case 3:
                if (account < Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid guild account id " + account);
                return investmentsByAccount.getOrDefault(account, Collections.emptySet());
            default:
                throw new IllegalArgumentException("Invalid account type " + accountType);
        }
    }

    public Set<AllianceLoan> getAllLoans() {
        Set<AllianceLoan> loans = new HashSet<>();
        for (Set<AllianceLoan> set : loansByAccount.values()) {
            loans.addAll(set);
        }
        return loans;
    }

    public void runTurnTasks() {
        Set<AllianceLoan> loans = getAllLoans();
        for (AllianceLoan loan : loans) {
            if (loan.dateClosed > 0) {
                continue;
            }
        }
        // alert for loans about to expire

        // loan interest is divied up when a loan is repaid (and its a loan that pays interest)
    }

    public Set<AllianceLoan> getActiveLoans(long accountId) {
        return loansByAccount.getOrDefault(accountId, Collections.emptySet()).stream().filter(f -> !f.isClosed()).collect(Collectors.toSet());
    }

    public void repayLoan(AllianceLoan loan, double[] amount, Function<ResourceType, Double> resourceValues) {
        if (loan.dateClosed > 0) {
            throw new IllegalStateException("Loan already closed");
        }
        Set<AllianceLoan> loans = getActiveLoans(loan.receiver);
        if (loans.isEmpty()) {
            throw new IllegalArgumentException("You have no loans to repay");
        }
        Map.Entry<double[], Double> owed = loan.getAmountOwed();
        Map.Entry<double[], double[]> accountBalance = getDeposits(loan.receiver, loan.receiver > Integer.MAX_VALUE ? 3 : 2, true);

        double[] balanceAbs = accountBalance.getValue();

        // repaying loans when you are investing
    }
}
