package org.lantern.data;

import java.util.Date;
import javax.persistence.Id;

public class PermanentLogEntry {

    @Id
    private Long id;

    private Date date = new Date();

    private String contents;

    public PermanentLogEntry() {
        super();
    }

    public PermanentLogEntry(final String contents) {
        super();
        this.contents = contents;
    }

    public String getContents() {
        return contents;
    }
}
