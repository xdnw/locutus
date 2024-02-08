package link.locutus.discord.db.entities;

import link.locutus.discord.util.MathMan;
import link.locutus.discord.apiv1.enums.Rank;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Member;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.function.Predicate;

public enum NationMeta {
    INTERVIEW_DEPOSITS,
    INTERVIEW_SPYOP,
    INTERVIEW_LOOT,
    INTERVIEW_SPIES,

    UPDATE_SPIES,
    UPDATE_UUID,
    UPDATE_GRANT,

    INTERVIEW_OPTIMALBUILD,
    INTERVIEW_RAID_BEIGE,
    INTERVIEW_COUNTER,
    INTERVIEW_WAR_ROOM,
    INTERVIEW_CHECKUP,

    DEPRECATED_LAST_BANK_UPDATE(ByteBuffer::getLong),

    INTERVIEW_INDEX,

    LAST_PROJECT_GRANT,
    LAST_WARCHEST,

    UPDATE_GRANT_TURN,


    TEST_SCORE,
    SPY_OPS_DAY(ByteBuffer::getLong),
    SPY_OPS_AMOUNT_DAY,
    SPY_OPS_AMOUNT_TOTAL,

    LAST_CITY_GRANT,

    DISCORD_APPLICANT,

    COUNTER_CARD,

    INTERVIEW_ROI,

    BORGMAS,

    CHECKUPS_PASSED,

    RECRUIT_INVITE_SENT,
    RECRUIT_MAIL_SENT,

    INTERVIEW_TRANSFER_SELF,
    INTERVIEW_INTEL,

    TAX_RATE,

    UPDATE_GRANT_CITY,
    UPDATE_GRANT_PROJECT,

    BANKER_WITHDRAW_LIMIT,

    REFERRER,

    INCENTIVE_REFERRER,

    INCENTIVE_INTERVIEWER,

    IA_CATEGORY_MAX_STAGE,

    INCENTIVE_MENTOR,

    CURRENT_MENTOR,

    BLOCKADED, // 0 = unkown, 1 = true, -1 = false

    ESCROWED, // list of resources to send when blockaded

    BEIGE_ALERT_MODE,

    BEIGE_ALERT_REQUIRED_STATUS,

    BEIGE_ALERT_REQUIRED_LOOT,

    BEIGE_ALERT_SCORE_LEEWAY,

    ALLOWANCE_UP_TO, //

    ALLOWANCE_DISBURSE_DAYS,

    LAST_CHECKED_AUDITS,

    UNBLOCKADE_REASON,

    LAST_LOGIN_DAY,

    LAST_LOGIN_COUNT,

    RECRUIT_AD_COUNT,

    RECRUIT_GOV_MESSAGE,
    LOGIN_NOTIFY,

    GPT_PROVIDER,

    GPT_OPTIONS,

    REPORT_BAN,

    GPT_MODERATED,

    GPT_SOURCES,

    BANK_TRANSFER_REQUIRED_AMOUNT,

    LAST_SENT_CREATION,
    LAST_SENT_LEAVE,
    LAST_SENT_ACTIVE,

    ;

    public static NationMeta[] values = values();

    private final Function<ByteBuffer, Object> parse;

    NationMeta(Function<ByteBuffer, Object> parse) {
        this.parse = parse;
    }

    NationMeta() {
        this(null);
    }

    public String toString(ByteBuffer buf) {
        if (buf == null) return "";
        if (parse != null) return "" + parse.apply(buf);

        byte[] arr = new byte[buf.remaining()];
        buf.get(arr);
        buf = ByteBuffer.wrap(arr);
        switch (arr.length) {
            case 0:
                return "" + (buf.get() & 0xFF);
            case 4:
                return "" + (buf.getInt());
            case 8:
                ByteBuffer buf2 = ByteBuffer.wrap(arr);
                return buf.getLong() + "/" + MathMan.format(buf2.getDouble());
            default:
                return new String(arr, StandardCharsets.ISO_8859_1);
        }
    }

    public enum BeigeAlertRequiredStatus {
        ONLINE(f -> f.getOnlineStatus() == OnlineStatus.ONLINE),
        ONLINE_AWAY(f -> {
            OnlineStatus status = f.getOnlineStatus();
            return status == OnlineStatus.ONLINE || status == OnlineStatus.IDLE;
        }),
        ONLINE_AWAY_DND(f -> {
            OnlineStatus status = f.getOnlineStatus();
            return status == OnlineStatus.ONLINE || status == OnlineStatus.IDLE || status == OnlineStatus.DO_NOT_DISTURB;
        }),
        ANY(f -> true)
        ;

        private final Predicate<Member> applies;

        BeigeAlertRequiredStatus(Predicate<Member> applies) {
            this.applies = applies;
        }

        public Predicate<Member> getApplies() {
            return applies;
        }
    }

    public enum BeigeAlertMode {
        NO_ALERTS(f -> false),
        INACTIVE_NONES(f -> f.active_m() > 10000 && f.getAlliance_id() == 0),
        NONES(f -> f.getAlliance_id() == 0),
        NONES_INACTIVE_APPS(f -> (f.getAlliance_id() == 0 || (f.active_m() > 10000 && f.getPosition() <= Rank.APPLICANT.id))),
        ANYONE_NOT_BLACKLISTED(f -> true)

        ;

        private final Predicate<DBNation> isAllowed;

        BeigeAlertMode(Predicate<DBNation> isAllowed) {
            this.isAllowed = isAllowed;
        }

        public Predicate<DBNation> getIsAllowed() {
            return isAllowed;
        }
    }

}
