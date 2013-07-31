package org.lantern.data;

import java.io.Serializable;
import java.util.logging.Logger;

import javax.persistence.Id;

/**
 * Holds the data for the latest donation for which we have successfully
 * enqueued a task.
 *
 * Also used as a transaction target to prevent QueryDonations sessions from
 * interfering.
 *
 * This is a singleton datastore Entity.
 */
public class LatestProcessedDonation implements Serializable {

    private static final long serialVersionUID = 3742472342128505556L;

    private final transient Logger log = Logger.getLogger(getClass().getName());

    public static final String singletonId = "the1";

    @Id
    private String id = singletonId;

    // Used to avoid fetching too many old results.  rally.org will report
    // donation time with precision up to seconds, but finest granularity for
    // queries is days.
    private int year = 1970;
    private int month = 1;
    private int day = 1;

    // rally.org's page parameter is 1-based.
    private int page = 1;

    // -1 means we haven't actually processed any donation in this page.
    private int index = -1;

    // Only for sanity checking.
    private String donationId;

    public LatestProcessedDonation() {
        super();
    }

    public LatestProcessedDonation(int year, int month, int day, int page,
                                   int index, final String donationId) {
        super();
        this.year = year;
        this.month = month;
        this.day = day;
        this.page = page;
        this.index = index;
        this.donationId = donationId;
    }

    public void incrementPage() {
        page += 1;
    }

    // Only dumb boilerplate getters and setters below...
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public int getDay() {
        return day;
    }

    public void setDay(int day) {
        this.day = day;
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
