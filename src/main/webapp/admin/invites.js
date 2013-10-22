angular.module('InvitesApp', ['ngResource'])
  .run(function($resource, $rootScope) {
    var invitesResource = $resource('rest/invites/pending');
    // Fetch the invites
    var invites = invitesResource.query(function () {
      // Organize them into a tree grouped by inviters
      // Below is some sample data for testing locally
      //invites = [{"id":"lanternfriend@gmail.com\u0001ox@getlantern.org","inviter":{"id":"lanternfriend@gmail.com","degree":2,"hasFallback":false,"countries":["US"],"sponsor":"lanternfriend@gmail.com"},"invitee":{"id":"ox@getlantern.org","degree":null,"hasFallback":null,"countries":null,"sponsor":null}}] 

      var inviters = {};
      invites.forEach(function(invite) {
        var inviter = inviters[invite.inviter.id];
        if (!inviter) {
          inviter = invite.inviter;
          inviter.invites = [];
          inviters[inviter.id] = inviter;
        }
        inviter.invites.push(invite);
      });
      $rootScope.inviters = _.values(inviters);
    });

    $rootScope.where = '';
    $rootScope.allSelected = false;
    
    function setSelectionForAllInvites(inviter, selected) {
      _.each(inviter.invites, function (invite) {
        invite.selected = selected;
      });
      inviter.allSelected = selected;
    }
    
    reevaluateAllSelected = function (inviter) {
      $rootScope.allSelected = _.all($rootScope.inviters, 'allSelected');
    }
    
    $rootScope.toggleInviteSelected = function(invite, inviter) {
      invite.selected = !invite.selected;
      inviter.allSelected = _.all(inviter.invites, 'selected');
      reevaluateAllSelected();
    }
    
    $rootScope.toggleAllSelected = function() {
      _.each($rootScope.inviters, function(inviter) {
        setSelectionForAllInvites(inviter, !$rootScope.allSelected);
      });
      $rootScope.allSelected = !$rootScope.allSelected;
    };
    
    $rootScope.toggleAllForInviter = function (inviter) {
      setSelectionForAllInvites(inviter, !inviter.allSelected);
      reevaluateAllSelected();
    };
  })