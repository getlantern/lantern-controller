package org.lantern.data;

import java.util.Date;
import javax.persistence.Id;

public class PermanentLogEntry {

    @Id
    private String id;

    private Date date = new Date();

    private String contents;

    public PermanentLogEntry() {
        super();
    }

    public PermanentLogEntry(String id, String contents) {
        super();
        this.id = id;
        this.contents = contents;
    }

    public String getContents() {
        return contents;
    }
}
