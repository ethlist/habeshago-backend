package com.habeshago.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for account retention periods.
 */
@Configuration
@ConfigurationProperties(prefix = "habeshago.retention")
public class RetentionConfig {

    /**
     * Days within which a deleted account can be restored.
     * Default: 90 days
     */
    private int accountRecoveryDays = 90;

    /**
     * Days to block OAuth ID from creating new account after deletion.
     * Default: 365 days (1 year)
     */
    private int oauthBlockDays = 365;

    /**
     * Days to retain deleted account data before permanent deletion.
     * Default: 730 days (2 years)
     */
    private int dataRetentionDays = 730;

    /**
     * Days to retain trip data before anonymization.
     * Default: 365 days (1 year)
     */
    private int tripRetentionDays = 365;

    // Getters and setters
    public int getAccountRecoveryDays() { return accountRecoveryDays; }
    public void setAccountRecoveryDays(int accountRecoveryDays) { this.accountRecoveryDays = accountRecoveryDays; }

    public int getOauthBlockDays() { return oauthBlockDays; }
    public void setOauthBlockDays(int oauthBlockDays) { this.oauthBlockDays = oauthBlockDays; }

    public int getDataRetentionDays() { return dataRetentionDays; }
    public void setDataRetentionDays(int dataRetentionDays) { this.dataRetentionDays = dataRetentionDays; }

    public int getTripRetentionDays() { return tripRetentionDays; }
    public void setTripRetentionDays(int tripRetentionDays) { this.tripRetentionDays = tripRetentionDays; }
}
