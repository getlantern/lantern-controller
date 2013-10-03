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

<form method="POST" action="/admin/post/setMaxInvitesPerProxy">
<input type="text" name="n" value="100">
<button type="submit" name="set" value="true">Set max invites per proxy</button><br/>
<%= AdminServlet.getCsrfTag() %>
</form>

</body>
</html>
