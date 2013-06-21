package org.lantern.admin;

import java.util.ArrayList;
import java.util.List;

import org.lantern.data.Invite;

public class PendingInvites {

    private List<Invite> invites = new ArrayList<Invite>();
    private String cursor;

    public void addInvite(Invite invite) {
        getInvites().add(invite);
    }

    public List<Invite> getInvites() {
        return invites;
    }

    public void setInvites(List<Invite> invites) {
        this.invites = invites;
    }

    public String getCursor() {
        return cursor;
    }

    public void setCursor(String cursor) {
        this.cursor = cursor;
    }

    public boolean hasNext() {
        return cursor != null;
    }
}
