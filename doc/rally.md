# Rally.org Integration

Lantern users will be able to sponsor fallback proxies by donating to a [rally.org](https://rally.org) rally we will set up for this purpose (at this moment we only have a test rally in place).  This document describes the mechanisms by which the Lantern Controller supports this functionality.

## Conventions

When talking about classes the `org.lantern` package prefix is assumed.  For example, `data.DonationCursor` refers to the class `org.lantern.data.DonationCursor` which can be found at `<project-root>/src/org/lantern/data/DonationCursor.java`.

## Overview

### Detecting donations

`QueryDonations` checks rally.org periodically through its [HTTP API](https://rally.org/corp/dev) for any new donations.

If any are detected, we enqueue the relevant details for further processing by using a [Task Queue](https://developers.google.com/appengine/docs/java/taskqueue/#Java_Tasks_within_transactions) inside a transaction.  GAE enqueues such tasks iff the transaction succeeds, and in that case they are retried until we return status 200 OK in the HTTP request that handles the task.  This helps make sure that each donation is handled exactly once.

### Effects of a donation

Donation-triggered tasks are handled in `ProcessDonation`.  A donation increases the credit associated with the donor's e-mail address.  We store such credit balances, and other related stats, in a `data.UserCredit` datastore 'table'.

If the new credit is enough to pay a proxy for a full month,
- if there is no LanternUser with the sponsor's e-mail, we create one,
- if there is no server running as this user, we launch one.

We request a fallback proxy to be spawned by sending a SQS message to a queue (see `InvitedServerLauncher.orderServerLaunch` and `SQSUtil`).  We receive various notifications from proxies by reading another SQS queue (see `SQSChecker`).

### Getting a refresh token

If we don't have a refresh token for a donor, their proxy will initially run as `lanterndonors@gmail.com`.  In this case, when the proxy starts up (see `InvitedServerLauncher.onFallbackProxyUp`) we send an e-mail to the donor with installer wrappers pointing to that proxy, so they can log in to Lantern.

The next time the controller gets an Available XMPP presence notification from that user, it will ask the client for their refresh token.

As soon as we have both a refresh token and a proxy ready to accept it (note that these can happen in either order), we send the token to the proxy, which will reconfigure itself, restart Lantern, and ping back when done.

### Reporting completion

When the proxy is fully configured, we get a SQS notification and send an e-mail to the user and any invitees (see `InvitedServerLauncher.onFallbackProxyUp`).

### Deducting credit, shutting down unpaid for servers

This will be done in a monthly cron job that is not implemented yet.  Most of the fields in `data.UserCredit` hint at how that will work; you can ignore them for the time being.
