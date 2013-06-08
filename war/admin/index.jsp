<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="org.lantern.AdminServlet" %>
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

<button type="submit" name="paused" value="true">Pause invites</button><br/>
<button type="submit" name="paused" value="false">Unpause invites</button>

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



</body>
</html>
