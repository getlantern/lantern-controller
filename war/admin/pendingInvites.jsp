<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.lantern.data.Dao" %>
<%@ page import="org.lantern.data.Invite" %>
<%@ page import="org.lantern.data.LanternUser" %>
<%@ page import="org.lantern.admin.PendingInvites" %>
<%@ page import="org.lantern.AdminServlet" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>Pending invitations</title>
</head>
<body>

<table>
<tr>
<th>Inviter</th>
<th>degree</th>
<th>countryCodes</th>
<th>sponsor</th>
<th>has installer</th>

<th>Invitee</th>
<th>degree</th>
<th>countryCodes</th>
<th>sponsor</th>
<th>Approve</th>
</tr>
<%
	//this is the cursor used to request this page
	String cursor = request.getParameter("cursor");
	pageContext.setAttribute("cursor", cursor);
    Dao dao = new Dao();
	PendingInvites pendingInvites = dao.getPendingInvites(cursor);
	pageContext.setAttribute("pendingInvites", pendingInvites);
	for (Invite invite : pendingInvites.getInvites()) {
	    LanternUser inviter = dao.getUser(invite.getInviter());
	    LanternUser invitee = dao.getUser(invite.getInvitee());
	    pageContext.setAttribute("inviter", inviter);
	    pageContext.setAttribute("invitee_email", invite.getInvitee());
	    if (invitee != null) {
	    	pageContext.setAttribute("invitee_degree", invitee.getDegree());
	    	pageContext.setAttribute("invitee_country_codes", StringUtils.join(invitee.getCountryCodes(),","));
	    	pageContext.setAttribute("invitee_sponsor", invitee.getSponsor());
	    } else {
	    	pageContext.setAttribute("invitee_degree", "N/A");
	    	pageContext.setAttribute("invitee_country_codes", "N/A");
	    	pageContext.setAttribute("invitee_sponsor", "N/A");
	    }
	    //invitee could be null
	    %>
	    <tr>
	    <td>
	    ${inviter.id}
	    </td>
	    <td>
	    ${inviter.degree}
	    </td>
	    <td>
	    <%= StringUtils.join(inviter.getCountryCodes(),",") %>
	    </td>
	    <td>
	    ${inviter.sponsor}
	    </td>
	    <td>
	    ${inviter.installerLocation != null}
	    </td>
	    <td>
	    ${invitee_email}
	    </td>
	    <td>
	    ${invitee_degree}
	    </td>
	    <td>
	    ${invitee_country_codes}
	    </td>
	    <td>
	    ${inviter_sponsor}
	    </td>
	    <td>
	    <form method="POST" action="/admin/post/approvePendingInvite">
	    <%= AdminServlet.getCsrfTag() %>
	    <input type="hidden" name="cursor" value="${cursor}">
	    <input type="hidden" name="inviter" value="${inviter.id}">
	    <input type="hidden" name="invitee" value="${invitee_email}">
	    <input type="submit" name="Approve" value="Approve">
	    </form>
	    </td>
	    </tr>
	    <%
	}
%>
</table>
<% if (pendingInvites.hasNext()) {
  %><a href="pendingInvitations?cursor=${pendingInvites.cursor}">Next</a>  
<%
}
%>
</body>
</html>