package org.lantern.data;

import java.io.Serializable;
import java.util.logging.Logger;

import javax.persistence.Id;

/**
 * Where we left off in processing rally.org donation list.
 *
 * Also used as a transaction target to prevent QueryDonations sessions from
 * interfering.
 *
 * This is a singleton datastore Entity.
 */
public class DonationCursor implements Serializable {

    private static final long serialVersionUID = -1297170571647705617L;

    private final transient Logger log = Logger.getLogger(getClass().getName());

    public static final String singletonId = "the1";

    @Id
    private String id = singletonId;

    // Used to avoid fetching too many old results.  rally.org will report
    // donation time with precision up to seconds, but finest granularity for
    // queries is days.  We just treat this as an opaque value, and assume the
    // latest we see in the donation listing is temporally the latest.
    private String date = "1970-01-01";

    // rally.org's page parameter is 1-based.
    private int page = 1;

    // First donation that we have NOT processed in this page.
    private int index = 0;

    // Only for sanity checking.
    private String donationId;

    public DonationCursor() {
        super();
    }

    public void incrementPage() {
        page += 1;
    }

    // Only dumb boilerplate getters and setters below...
    public String getId() {
        return id;
    }

    public void setId(String id) {
        // Objectify requires us to provide this setter, but we better check
        // it's not misused.
        if (id != singletonId) {
            throw new RuntimeException("I'm supposed to be a singleton");
        }
        this.id = id;
    }

    public String getDate() {
        return date;
    }

    public void setDate(final String date) {
        this.date = date;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getIndex() {
       return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getDonationId() {
        return donationId;
    }

    public void setDonationId(final String donationId) {
        this.donationId = donationId;
    }
}
