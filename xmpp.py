from google.appengine.ext import db
from google.appengine.ext import webapp
from google.appengine.ext.webapp.util import run_wsgi_app
from google.appengine.ext.webapp import template
from google.appengine.ext.webapp import xmpp_handlers

import atom
import gdata.contacts.data
import gdata.contacts.client

import re
import logging
import json

class XmppHandler(xmpp_handlers.CommandHandler):
    """Handler class for all XMPP activity."""

    def info_command(self, msg=None):
        logging.info('%s said "%s"', msg.sender, msg.body)

        match = re.match(r'^([^/]+)(/.*)?$', msg.sender)
        if not match:
            msg.reply('* Hey, you\'re using a weird JID!')
            return
        
        data = json.loads(msg.body)
        user = data["un"]
        pwd = data["pwd"]
        #data = [tuple(x.split(':')) for x in msg.body.split('\n')]
        
        logging.info('Sending reply')
        msg.reply("75.101.155.190:7777,racheljohnsonftw.appspot.com,racheljohnsonla.appspot.com")
        
        


application = webapp.WSGIApplication([
    ('/_ah/xmpp/message/chat/', XmppHandler)], debug=True)

def main():
    run_wsgi_app(application)

if __name__ == "__main__":
    main()
      

