package link.locutus.discord.db.entities;

import link.locutus.discord.db.NationDB;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.scheduler.ThrowingBiConsumer;
import link.locutus.discord.util.scheduler.ThrowingConsumer;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class LoanManager {
    private final NationDB db;

    public LoanManager(NationDB db) {
        this.db = db;

        createLoanTable();
    }

    public void createLoanTable() {
        String create = "CREATE TABLE IF NOT EXISTS `LOANS` (" +
                "loan_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "loaner_guild_or_aa BIGINT NOT NULL, " +
                "loaner_nation INT NOT NULL, " +
                "receiver BIGINT NOT NULL, " +
                "principal BLOB NOT NULL, " +
                "paid BLOB NOT NULL, " +
                "remaining BLOB NOT NULL, " +
                "status INT NOT NULL, " +
                "due_date BIGINT NOT NULL, " +
                "loan_date BIGINT NOT NULL, " +
                "date_submitted BIGINT NOT NULL" +
                ")";
        db.executeStmt(create);

        // add index loaner_guild_or_aa
        db.executeStmt("CREATE INDEX IF NOT EXISTS loaner_guild_or_aa_idx ON LOANS (loaner_guild_or_aa)");
        // add index receiver
        db.executeStmt("CREATE INDEX IF NOT EXISTS receiver_idx ON LOANS (receiver)");
        // add index status
        db.executeStmt("CREATE INDEX IF NOT EXISTS status_idx ON LOANS (status)");
    }



    public void addLoans(List<DBLoan> loans) {
        String query = "INSERT INTO LOANS (loaner_guild_or_aa, loaner_nation, receiver, principal, paid, remaining, status, due_date, loan_date, date_submitted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        db.executeBatch(loans, query, new ThrowingBiConsumer<DBLoan, PreparedStatement>() {
            @Override
            public void acceptThrows(DBLoan loan, PreparedStatement stmt) throws Exception {
                stmt.setLong(1, loan.loanerGuildOrAA);
                stmt.setInt(2, loan.loanerNation);
                stmt.setLong(3, getId(loan.nationOrAllianceId, loan.isAlliance));
                stmt.setBytes(4, ArrayUtil.toByteArray(loan.principal));
                stmt.setBytes(5, ArrayUtil.toByteArray(loan.paid));
                stmt.setBytes(6, ArrayUtil.toByteArray(loan.remaining));
                stmt.setInt(7, loan.status.ordinal());
                stmt.setLong(8, loan.dueDate);
                stmt.setLong(9, loan.loanDate);
                stmt.setLong(10, loan.date_submitted);
            }
        });
    }

    public void setLoans(long loaner_guild_or_aa, List<DBLoan> loans) {
        if (loans.isEmpty()) {
            throw new IllegalArgumentException("Loans cannot be empty");
        }
        db.executeStmt("DELETE FROM LOANS WHERE loaner_guild_or_aa = " + loaner_guild_or_aa);
        addLoans(loans);
    }

    private long getId(int id, boolean isAA) {
        return isAA ? -id : id;
    }

    public List<DBLoan> getLoansByAlliance(int allianceId) {
        long id = getId(allianceId, true);
        return getLoansById(id);
    }

    public List<DBLoan> getLoansByNation(int nationId) {
        long id = getId(nationId, false);
        return getLoansById(id);
    }

    public List<DBLoan> getLoansByNations(Set<Integer> nationIds, Set<DBLoan.Status> loanStatus) {
        Set<Long> ids = nationIds.stream().map(id -> getId(id, false)).collect(Collectors.toSet());
        return getLoansByIds(ids, loanStatus);
    }

    public List<DBLoan> getLoansByStatus(Set<DBLoan.Status> requiredStatusOrNull) {
        return getLoansByIds(null, requiredStatusOrNull);
    }

    public List<DBLoan> getLoansByIds(Set<Long> ids, Set<DBLoan.Status> requiredStatusOrNull) {
        List<DBLoan> loans = new ArrayList<>();

        String query = "select * FROM LOANS where";
        if (requiredStatusOrNull != null) {
            if (requiredStatusOrNull.size() == 1) {
                query += " status = " + requiredStatusOrNull.iterator().next().ordinal();
            } else {
                Set<Integer> statusIds = requiredStatusOrNull.stream().map(Enum::ordinal).collect(Collectors.toSet());
                query += " status in " + StringMan.getString(statusIds);
            }
        }
        if (ids != null) {
            if (requiredStatusOrNull != null) {
                query += " and ";
            }
            query += " receiver in " + StringMan.getString(ids);
        }
        try (PreparedStatement stmt = db.prepareQuery(query)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    loans.add(new DBLoan(rs));
                }
            }
            return loans;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private List<DBLoan> getLoansById(long id) {
        List<DBLoan> loans = new ArrayList<>();
        try (PreparedStatement stmt = db.prepareQuery("select * FROM LOANS where receiver = ?")) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    loans.add(new DBLoan(rs));
                }
            }
            return loans;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<DBLoan> getLoansByGuildOrAlliance(long id) {
        List<DBLoan> loans = new ArrayList<>();
        try (PreparedStatement stmt = db.prepareQuery("select * FROM LOANS where loaner_guild_or_aa = ?")) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    loans.add(new DBLoan(rs));
                }
            }
            return loans;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public DBLoan getLoanById(int loanId) {
        List<DBLoan> loans = new ArrayList<>();
        try (PreparedStatement stmt = db.prepareQuery("select * FROM LOANS where loan_id = ?")) {
            stmt.setInt(1, loanId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    return new DBLoan(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void deleteLoans(List<DBLoan> loans) {
        String query = "DELETE FROM LOANS WHERE loan_id = ?";
        db.executeBatch(loans, query, (ThrowingBiConsumer<DBLoan, PreparedStatement>) (loan, stmt) -> stmt.setInt(1, loan.loanId));
    }
}
