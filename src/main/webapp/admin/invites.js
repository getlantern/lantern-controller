angular.module('InvitesApp', [])
  .run(function($http, $rootScope) {
    // For busy indicator
    $rootScope.busy = false;
    
    // Tracks messages for user
    $rootScope.messages = [];
    $rootScope.addMessage = function(text, type) {
      $rootScope.messages.unshift({type: type, text: text, date: new Date()});
    };
    
    // For search query
    $rootScope.where = '';
    
    // Checkbox management
    $rootScope.allSelected = false;
    $rootScope.anySelected = false;
    
    function setSelectionForAllInvites(inviter, selected) {
      _.each(inviter.invites, function (invite) {
        invite.selected = selected;
      });
      inviter.allSelected = selected;
    }
    
    function reevaluateAnyAndAllSelected(inviter) {
      $rootScope.allSelected = _.all($rootScope.inviters, 'allSelected');
      $rootScope.anySelected = _($rootScope.inviters).flatten('invites').any('selected');
    }
    
    $rootScope.toggleInviteSelected = function(invite, inviter) {
      invite.selected = !invite.selected;
      inviter.allSelected = _.all(inviter.invites, 'selected');
      reevaluateAnyAndAllSelected();
    }
    
    function setAllSelected(selected) {
      _.each($rootScope.inviters, function(inviter) {
        setSelectionForAllInvites(inviter, selected);
      });
      $rootScope.allSelected = selected;
      $rootScope.anySelected = selected;
    }
    
    $rootScope.toggleAllSelected = function() {
      setAllSelected(!$rootScope.allSelected);
    };
    
    $rootScope.toggleAllForInviter = function (inviter) {
      setSelectionForAllInvites(inviter, !inviter.allSelected);
      reevaluateAnyAndAllSelected();
    };
    
    $rootScope.search = function() {
      var url = 'rest/invites/pending';
      if ($rootScope.where) {
        url = url + "?where=" + encodeURIComponent($rootScope.where);
      }
      // Fetch the invites
      $http.get(url).success(function(invites) {
        // Organize them into a tree grouped by inviters
        // Below is some sample data for testing locally
//        invites = [
//                   {"id":"lanternfriend@gmail.com\u0001ox@getlantern.org","inviter":{"id":"lanternfriend@gmail.com","degree":2,"hasFallback":false,"countries":["US"],"sponsor":"lanternfriend@gmail.com"},"invitee":{"id":"ox@getlantern.org","degree":null,"hasFallback":null,"countries":null,"sponsor":null}},
//                   {"id":"lanternfriend@gmail.com\u0001ox@getlantern.org","inviter":{"id":"lanternfriend@gmail.com","degree":2,"hasFallback":false,"countries":["US"],"sponsor":"lanternfriend@gmail.com"},"invitee":{"id":"ox2@getlantern.org","degree":null,"hasFallback":null,"countries":null,"sponsor":null}}
//                   ];

        var inviters = {};
        invites = _.sortBy(invites, 'inviter.id');
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
        
        setAllSelected(false);
        $rootScope.addMessage('found ' + invites.length + ' invites');
      }).error(function() {
        $rootScope.inviters = [];
      })
    };
    
    function selectedInviteIds() {
      return _($rootScope.inviters).flatten('invites').filter('selected').pluck('id').value();
    }
    
    function removeSelectedInvites() {
      var retainedInviters = [];
      $rootScope.inviters.forEach(function(inviter) {
        var retainedInvites = [];
        inviter.invites.forEach(function(invite) {
          if (!invite.selected) {
            retainedInvites.push(invite);
          }
        });
        inviter.invites = retainedInvites;
        if (retainedInvites.length > 0) {
          retainedInviters.push(inviter);
        }
      });
      $rootScope.inviters = retainedInviters;
    }
    
    $rootScope.authorizeInvites = function() {
      var ids = selectedInviteIds();
      if (ids.length > 0) {
        $http.post('rest/invites/authorize', ids).success(function(totalAuthorized) {
          removeSelectedInvites();
          setAllSelected(false);
          $rootScope.addMessage('authorized ' + totalAuthorized + ' invites');
        });
      }
    }
    
    $rootScope.deleteInvites = function() {
      var ids = selectedInviteIds();
      if (ids.length > 0) {
        $http.post('rest/invites/delete', ids).success(function(totalDeleted) {
          removeSelectedInvites();
          setAllSelected(false);
          $rootScope.addMessage('deleted ' + totalDeleted + ' invites');
        });
      }
    }
    
    // Search immediately
    $rootScope.search();
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
