package org.lantern.data;

import javax.persistence.Id;


//  For proof-of-concept testing only.
public class UserFallbackConfig {
    @Id
    private String user;
    private String json;

    public UserFallbackConfig() {}

    public UserFallbackConfig(String user, String json) {
        this.user = user;
        this.json = json;
    }

    public String getJson() {
        return json;
    }
}
