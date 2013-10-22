angular.module('InvitesApp', ['ngResource'])
  .run(function($resource, $rootScope) {
    var invitesResource = $resource('rest/invites/pending');
    
    // For busy indicator
    $rootScope.busy = false;
    
    // Tracks messages for user
    $rootScope.messages = [];
    $rootScope.addMessage = function(text, type) {
      $rootScope.messages.unshift({type: type, text: new Date() + ' -> ' + text});
    };
    
    // For search query
    $rootScope.where = '';
    
    // At the moment, there is no way to change the ordering in the UI
    $rootScope.ordering = 'inviter'
      
    // Checkbox management
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
    
    $rootScope.search = function() {
      // Fetch the invites
      var invites = invitesResource.query({where: $rootScope.where,
                                           ordering: $rootScope.ordering},
                                           function () {
        // Organize them into a tree grouped by inviters
        // Below is some sample data for testing locally
        // invites = [{"id":"lanternfriend@gmail.com\u0001ox@getlantern.org","inviter":{"id":"lanternfriend@gmail.com","degree":2,"hasFallback":false,"countries":["US"],"sponsor":"lanternfriend@gmail.com"},"invitee":{"id":"ox@getlantern.org","degree":null,"hasFallback":null,"countries":null,"sponsor":null}}] 

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
        $rootScope.addMessage(invites.length + " invites found");
      });
    };
  })
  .config(function($provide, $httpProvider) {
    // Set up global loading indicator behavior
    // See http://stackoverflow.com/questions/11956827/angularjs-intercept-all-http-json-responses
    $provide.factory('myHttpInterceptor', function ($q, $rootScope) {
      return {
        request: function(config) {
          $rootScope.busy = true;
          return config || $q.when(config);
        },
        requestError: function(rejection) {
          $rootScope.busy = false;
          return $q.reject(rejection);
        },
        response: function(response) {
          $rootScope.busy = false;
          return response || $q.when(response);
        },
        responseError: function(rejection) {
          $rootScope.addMessage(rejection.data, 'error');
          $rootScope.busy = false;
          return $q.reject(rejection);
        }
      }
    });
    $httpProvider.interceptors.push('myHttpInterceptor');
  });