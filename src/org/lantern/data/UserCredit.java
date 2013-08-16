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
     * Are we running a proxy sponsored by this user?
     *
     * We use this to know whether we should charge the user's account.
     *
     * We can't just use LanternUser.getInstallerLocation() because some users
     * got their proxies through the invite system.
     *
     * We count a proxy as running as soon as we have ordered its launch,
     * because that's pretty much when we start paying for it ourselves.
     */
    // We could encode this info into `runningProxySince` but this is simpler
    // to index and query for in the Datastore.
    private boolean isProxyRunning = false;

    /**
     * Since when has the proxy for this user been running.
     *
     * In milliseconds since the UNIX epoch.
     *
     * Not to unduly charge the full first billing period.
     */
    private long proxyRunningSince;

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

    /**
     * When deciding which unfunded proxies to prune, we keep the ones with
     * the highest value in this field.
     *
     * This is derived from other fields, but we still save it so it can be
     * used as an order in a Datastore query.
     */
    private float creditScore = 0.0f;

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
     * This encourages continuity of service.  A user that funds their server
     * for six months in a row provides a better experience to their invitees
     * than one that does so for six alternate months in a year.
     *
     * In particular, this gives top donors an incentive to keep funding their
     * servers where otherwise they could expect us to pick them up.
     */
    public float getAverageStreak() {
        return fundedMonths / (numStreaks + (isLastMonthFunded ? 1 : 0));
    }

    /**
     * Used to determine which proxies we keep up.
     *
     * We reserve the right to change this anytime.
     */
    public void updateCreditScore() {
        creditScore = getAverageStreak() * 10 + fundedMonths;
    }

    /**
     * Call once per 'billing period'.
     */
    public void pay(int cost) {
        balance -= cost;
        boolean enough = (balance >= 0);
        if (enough) {
            fundedMonths++;
        }
        if (enough && !isLastMonthFunded) {
            numStreaks++;
        }
        isLastMonthFunded = enough;
    }

    public void addBalance(int cents) {
        balance += cents;
    }

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

    public boolean getIsProxyRunning() {
        return isProxyRunning;
    }

    public void setIsProxyRunning(boolean isProxyRunning) {
        this.isProxyRunning = isProxyRunning;
    }

    public long getProxyRunningSince() {
        return proxyRunningSince;
    }

    public void setProxyRunningSince(long proxyRunningSince) {
        this.proxyRunningSince = proxyRunningSince;
    }

    public float getCreditScore() {
        return creditScore;
    }

    public void setCreditScore(float creditScore) {
        this.creditScore = creditScore;
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
