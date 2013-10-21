angular.module('InvitesApp', ['ngResource'])
  .run(function($resource, $rootScope) {
    var invitesRsrc = $resource('rest/invites/pending');
    var invites = invitesRsrc.query(function () {
      var foo = invites;
      debugger;
    });

    $rootScope.where = '';

    $rootScope.inviters = [
      {
        "id": "ox@getlantern.org",
        "degree": 2,
        "hasFallback": true,
        "countries":["US", "DE"],
        "sponsor": "_pants@getlantern.org",
        "invitees": [
          {
            id: "foo@bar.com"
          },
          {
            id: "baz@fleem.com"
          }
          ]
      },
      {
        "id": "_pants@getlantern.org",
        "degree": 1,
        "hasFallback": true,
        "countries": ["US"],
        "sponsor": "admin@getlantern.org",
        "invitees": [
          {
            id: "foo@bar.com"
          },
          {
            id: "baz@fleem.com"
          }
          ]
      }
    ];

    $rootScope.allSelected = false;
    
    function setSelectionForAllInvitees(inviter, selected) {
      _.each(inviter.invitees, function (invitee) {
        invitee.selected = selected;
      });
      inviter.allSelected = selected;
    }
    
    reevaluateAllSelected = function (inviter) {
      $rootScope.allSelected = _.all($rootScope.inviters, 'allSelected');
    }
    
    $rootScope.toggleInviteeSelected = function(invitee, inviter) {
      invitee.selected = !invitee.selected;
      inviter.allSelected = _.all(inviter.invitees, 'selected');
      reevaluateAllSelected();
    }
    
    $rootScope.toggleAllSelected = function() {
      _.each($rootScope.inviters, function(inviter) {
        setSelectionForAllInvitees(inviter, !$rootScope.allSelected);
      });
      $rootScope.allSelected = !$rootScope.allSelected;
    };
    
    $rootScope.toggleAllForInviter = function (inviter) {
      setSelectionForAllInvitees(inviter, !inviter.allSelected);
      reevaluateAllSelected();
    };
  })