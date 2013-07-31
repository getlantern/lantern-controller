package org.lantern.data;

import java.io.Serializable;
import java.util.logging.Logger;

import javax.persistence.Id;

/**
 * Current balance and select bits about the donation/usage history of a user.
 *
 * We may continue providing access, as funds permit, to users that are behind
 * in funding their servers, but at some point we may need to shut down servers
 * we can no longer afford maintaining.  In deciding which servers to keep,
 * we want to incentivize users to keep funding their servers as long as they
 * can afford.
 *
 * So, besides the credit balance of the user, we maintain some statistics
 * that help inform a policy that generates desirable incentives.
 *
 * We don't keep this in the LanternUser because only a fraction of users will
 * donate, and we don't need to save and index these properties for everyone
 * else.
 *
 * Also, at this moment we give certain meaning to having an entry in the
 * LanternUser table, and we want to register the credit of users that have
 * donated but not enough to launch a server, without necessarily creating a
 * LanternUser entry for them.
 */
public class UserCredit implements Serializable {

    private static final long serialVersionUID = 7391809562916726123L;

    private final transient Logger log = Logger.getLogger(getClass().getName());

    /**
     * This doesn't necessarily correspond to any user in LanternUsers.
     *
     * We may collect donations from users until we have enough to launch a
     * server for them, and we may want to wait until then to create a
     * LanternUser entry for them.
     */
    @Id
    private String userId;

    /**
     * In US dollar cents.
     */
    private int balance = 0;

    /**
     * How many months has this user funded their server, in total.
     *
     * In addition to being interesting on its own, this helps maintain
     * averageStreak.
     */
    private int fundedMonths = 0;

    // Auxiliary stats for use in getAverageStreak.
    private boolean isLastMonthFunded = false;
    private int numStreaks = 0;

    public UserCredit() {
        super();
    }

    public UserCredit(String userId) {
        super();
        setUserId(userId);
    }

    /**
     * How many consecutive months does this user keep paying for their server,
     * on average.
     *
     * Not including the current streak.
     *
     * This encourages continuity of service.  A user that funds their server
     * for six months in a row provides a better experience to their invitees
     * than one that does so for six alternate months in a year.
     *
     * In particular, this gives top donors an incentive to keep funding their
     * servers where otherwise they could expect us to pick them up.
     */
    public float getAverageStreak() {
        if (numStreaks == 0) {
            return 0;
        } else {
            return fundedMonths / numStreaks;
        }
    }

    /**
     * Only call once a month.
     */
    //XXX: move to wherever we have the transaction.
   /* public boolean payMonth(int cost) {
        boolean paid = (balance > cost);
        if (paid) {
            balance -= paid;
        }
        if (funded) {
            monthsFunded++;
        }
        if (funded && !fundedLastMonth) {
            numStreaks++;
        }
        fundedLastMonth = funded;
        funded = false;
        return paid;
    }

    public void addBalance(int cents) {
        balance += cents;
    }*/

    // Only dumb boilerplate getters and setters below...

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getBalance() {
        return balance;
    }

    public void setBalance(int balance) {
        this.balance = balance;
    }

    public int getFundedMonths() {
        return fundedMonths;
    }

    public void setFundedMonths(int fundedMonths) {
        this.fundedMonths = fundedMonths;
    }

    public boolean getLastMothFunded() {
        return isLastMonthFunded;
    }

    public void setLastMonthFunded(boolean isLastMonthFunded) {
        this.isLastMonthFunded = isLastMonthFunded;
    }

    public int getNumStreaks() {
        return numStreaks;
    }

    public void setNumStreaks(int numStreaks) {
        this.numStreaks = numStreaks;
    }
}
