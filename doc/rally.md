# Rally.org Integration

Lantern users will be able to sponsor fallback proxies by donating to a [rally.org](https://rally.org) rally we will set up for this purpose (at this moment we only have a test rally in place).  This document describes the mechanisms by which the Lantern Controller supports this functionality.

## Conventions

When talking about classes the `org.lantern` package prefix is assumed.  For example, `data.DonationCursor` refers to the class `org.lantern.data.DonationCursor` which can be found at `<project-root>/src/org/lantern/data/DonationCursor.java`.

## Overview

### Detecting donations

`QueryDonations` checks rally.org periodically through its [HTTP API](https://rally.org/corp/dev) for any new donations.

If any are detected, we enqueue the relevant details for further processing by using a [Task Queue](https://developers.google.com/appengine/docs/java/taskqueue/#Java_Tasks_within_transactions) inside a transaction.  GAE enqueues such tasks iff the transaction succeeds, and in that case they are retried until we return status 200 OK in the HTTP request that handles the task.  This helps make sure that each donation is handled exactly once.

### Effects of a payment

Donation-triggered tasks are handled in `ProcessPayment`.  A payment increases the credit associated with the sponsor's e-mail address.  We store such credit balances, and other related stats, in a `data.UserCredit` datastore 'table'.

If the new credit is enough to pay a proxy for a full month,
- if there is no LanternUser with the sponsor's e-mail, we create one,
- if there is no server running as this user, we launch one.

We request a fallback proxy to be spawned by sending a SQS message to a queue called `<controller-id>_request` in the `ap-southeast-1` (Singapore) AWS region (see `InvitedServerLauncher.orderServerLaunch` and `SQSUtil`).  We receive various notifications from proxies by reading another SQS queue called `notify_<controller-id>` (see `SQSChecker`).

Whether the donation triggered the launching of a server, or it didn't because of insufficient funds, or because a server was already launched for this user, the user will receive an e-mail with templates `proxy-launching`, `insufficient-balance`, or `payment-received`, respectively.

### Getting a refresh token

If we don't have a refresh token for a donor, their proxy will initially run as `lanterndonors@gmail.com`.  In this case, when the proxy starts up (see `InvitedServerLauncher.onFallbackProxyUp`) we send an e-mail to the donor (template `token-request`) with installer wrappers pointing to that proxy, so they can log in to Lantern.

The next time the controller gets an Available XMPP presence notification from that user, it will ask the client for their refresh token.

As soon as we have both a refresh token and a proxy ready to accept it (note that these can happen in either order), we send the token to the proxy, which will reconfigure itself, restart Lantern, and ping back when done.

### Reporting completion

When the proxy is fully configured, we get a SQS notification and send an e-mail to the user (template `proxy-ready`) and any invitees (template `invite-notification`.)  See `InvitedServerLauncher.onFallbackProxyUp`.

### Deducting credit, shutting down unpaid for servers

This is done in `ChargeForProxies`, which runs on a monthly basis.  It's actually triggered by a daily cron job, but it knows to only do anything in specific days of the month ([aranhoide] later I learned that the cron schedule syntax probably allows this to be expressed more directly; I'll look into that in a later pass).

In a first pass (see `SendCreditWarning`) we send a warning email (template `credit-warning`) to users whose balance is not enough to pay for their proxy next month.

Three days later, we run a second pass (see `ChargeProxy`) that charges all accounts by the amount due that month, notifying the corresponding users via email (template `proxy-charged`).  In this pass we also assign each user account a score that is based on their credit history and determines whose proxies will be shut down if they stop funding them and we can't afford to pay for them all.  Doing it like this means we can use the datastore index to sort the candidates, rather than trying to load them into instance memory.

One day later we shut down all candidates selected by the previous step (see `ShutdownProxy`).  We abort the shutdown of instances for which we receive funds at the last minute.  We email the sponsors of shut down proxies (template `proxy-shutdown`) but currently their invitees don't get any notification.  We can't do much for them currently because we don't know which users are assigned to which proxies.  We need to fix that for this and other use cases (e.g. proxies being blocked).  In time..
