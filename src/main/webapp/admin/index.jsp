<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="org.lantern.AdminServlet" %>
<%@ page import="org.lantern.data.Dao" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<title>
Lantern Controller Admin
</title>
</head>

<body>
<h1>Lantern Controller Admin</h1>

<form method="POST" action="/admin/post/test">

<input type="submit" name="test" value="test admin interface" />
<%= AdminServlet.getCsrfTag() %>
</form>

<br/>
<br/>

<h2>Pause/unpause invites</h2>
<form method="POST" action="/admin/post/setInvitesPaused">

<% Dao dao = new Dao();
if (dao.areInvitesPaused()) {
%>
Invites are paused.  <button type="submit" name="paused" value="false">Unpause invites</button>
<% } else { %>
Invites are unpaused.  <button type="submit" name="paused" value="true">Pause invites</button><br/>
<% } %>
<%= AdminServlet.getCsrfTag() %>
</form>

<br/>
<br/>

<h2>Set max invites per proxy</h2>
<p>Set the number of invites we'll direct to a proxy before we launch a new one.</p>
<form method="POST" action="/admin/post/setMaxInvitesPerProxy">
<input type="text" name="n" value="100">
<button type="submit" name="set" value="true">Set max invites per proxy</button><br/>
<%= AdminServlet.getCsrfTag() %>
</form>

<br/>
<br/>

<h2>Promote Fallback User</h2>
<p>Promote someone so we'll run fallback proxies as them when they invite someone.</p>
<form method="POST" action="/admin/post/promoteFallbackProxyUser">
<input type="text" name="user" value="user@example.com">
<button type="submit" name="set" value="true">Promote Fallback User</button><br/>
<%= AdminServlet.getCsrfTag() %>
</form>

<br/>
<br/>

<h2>Send version update email</h2>
<p>Send an email to notify users that a new Lantern version is up for download.</p>
<p>The version number will only be used for display purposes.  The invite e-mails always point to the latest Lantern version.</p>
<p>Provide the e-mail address of a Lantern user in the "Send to" textbox if you just want to send a test email.</p>
<p>If you really want to email everyone, enter "<b>EVERYONE, AND I MEAN IT!</b>" in the "Send to" textbox.</p>
<form method="POST" action="/admin/post/sendUpdateEmail">
Version: <input type="text" name="version" value="v1.0.0-beta7">
Send to: <input type="text" name="to" value="test@getlantern.org">
<button type="submit" name="set" value="true">Send version update email</button><br/>
<%= AdminServlet.getCsrfTag() %>
</form>

<h2>Invite to new trust network</h2>
<p>Invite users to new trust network and send an email to notify them that they need to upgrade Lantern.</p>
<p>Provide the e-mail address of the inviter for the newly generated Invites.</p>
<form method="POST" action="/admin/post/inviteToNewTrustNetwork">
Inviter: <input type="text" name="inviter" value="afisk@getlantern.org">
<br/>
<br/>
Invitees:
<br/>
<textarea rows="12" cols="80" name="invitees">
euccastro@yahoo.com
</textarea>
<br/>
<input type="checkbox" name="friend" value="add">Add as a friend too<br/>
<br/>
<button type="submit" name="set" value="true">Invite to new trust network</button><br/>
<%= AdminServlet.getCsrfTag() %>
</form>
<br/>
<br/>

<h2>Export Baseline Stats</h2>
<p>To facilitate migration to statshub for tracking stats, we need to export baseline
statistics from the controller.  This operation is idempotent, so you can run it
as often as you like, but it's not cheap, so you should not run it very often.
Really the only reason to run it again is if the stats in statshub have been
cleared since the last run (e.g. for testing).</p>
<form method="POST" action="/admin/post/exportBaselineStats">
    <%= AdminServlet.getCsrfTag() %>
    <input type="submit" value="Export Baseline Stats" />
</form>
<br/>
<br/>

<h2>Increase Untrusted Invites</h2>
<p>A bunch of users were invited at degrees exceeding 1000, which resulted in
them not having any invites.  Push the button below to find all users at degree
over 1000 and increase their friending quota.  This is done by:</p>

<ol>
<li>Lowering their degree by 999 (e.g. 1001 - 999 = 2)</li>
<li>Recalculating their quota based on their new degree</li>
</ol>

<form method="POST" action="/admin/post/increaseUntrustedInvites">
    <%= AdminServlet.getCsrfTag() %>
    <input type="submit" value="Increase Untrusted Invites" />
</form>
<br/>
<br/>

<h2>Recalculate Friending Quotas</h2>
<p>Push the button below to recalculate the friending quota for all users in the
system based on their current degree and the current formula for calculating
friending quotas.  If the user's existing quota is higher than the newly
calculated value, the existing quota is left alone.</p>

<form method="POST" action="/admin/post/recalculateFriendingQuotas">
    <%= AdminServlet.getCsrfTag() %>
    <input type="submit" value="Recalculate Friending Quotas" />
</form>
<br/>
<br/>

<h2>Change user's fallback</h2>
<p>A new config.json will be uploaded for the user.</p>
<form method="POST" action="/admin/post/changeUsersFallback">
User: <input type="text" name="user" value="_pants@getlantern.org">
<br/>
Fallback Owner: <input type="text" name="fpuid" value="_pants@getlantern.org">
<br/>
Fallback: <input type="text" name="fallback" value="fp-fte3-at-getlantern-dot-org-4853-3-2014-5-10">
<br/>
<button type="submit" name="set" value="true">Change</button><br/>
<%= AdminServlet.getCsrfTag() %>
</form>

<br/>
<br/>

<h2>Trigger maintenance task</h2>
<p>RemoteApi-like thingy.  Don't touch this unless you added it.  Disable it when done.</p>
<form method="POST" action="/admin/post/triggerMaintenanceTask">
<textarea rows="12" cols="80" name="input">
Enter input for your hacks here...
</textarea>
<br/>
<button type="submit" name="set" value="true">Trigger</button><br/>
<%= AdminServlet.getCsrfTag() %>
</form>

<br/>
<br/>

</body>
</html>
