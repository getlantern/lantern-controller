<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="org.lantern.AdminServlet" %>
<%@ page import="java.util.Map" %>
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

<form method="POST" action="/admin/post/addInvites">

<input type="text" name="n" value="0">
<button type="submit" name="add" value="true">Add invites</button><br/>
<%= AdminServlet.getCsrfTag() %>
</form>


<form method="POST" action="/admin/post/setDefaultInvites">

<input type="text" name="n" value="2">
<button type="submit" name="add" value="true">Set default number of invites</button><br/>
<%= AdminServlet.getCsrfTag() %>
</form>

<br>
<h2>Settings</h2>
<% for (Map.Entry<String, String> entry : AdminServlet.getAllSettings().entrySet()) { %>
<form method="POST" action="/admin/post/setSetting">

<strong><%= entry.getKey() %></strong>: 
<input type="hidden" name="name" value="<%= entry.getKey() %>">
<input type="text" name="value" value="<%= entry.getValue() %>">
<button type="submit" name="add" value="true">Set setting</button><br/>
<%= AdminServlet.getCsrfTag() %>
</form>

<% } %>

<form method="POST" action="/admin/post/setSetting">

<strong>[New setting]:</strong><input type="text" name="name" value="">
value = <input type="text" name="value" value="">
<button type="submit" name="add" value="true">Create setting</button><br/>
<%= AdminServlet.getCsrfTag() %>
</form>

</body>
</html>
