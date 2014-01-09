angular.module('FriendingQuotaApp', [])
  .run(function($http, $rootScope) {
    // For busy indicator
    $rootScope.busy = false;
    
    // Tracks messages for user
    $rootScope.messages = [];
    $rootScope.addMessage = function(text, type) {
      $rootScope.messages.unshift({type: type, text: text, date: new Date()});
    };
    
    // For finding by email
    $rootScope.email = '';
    
    function baseUrl() {
      return 'rest/friendingquota/' + encodeURIComponent($rootScope.email);
    }
    
    $rootScope.find = function() {
      var url = baseUrl();
      $http.get(url).success(function(quota) {
        $rootScope.quota = quota;
        if (quota) {
          $rootScope.addMessage("found quota");
        } else {
          $rootScope.addMessage("no matching quota found for " + $rootScope.email, "error");
        }
      }).error(function() {
        $rootScope.quota = null;
      })
    };
    
    $rootScope.setMaxAllowed = function() {
      var url = baseUrl() + "/maxAllowed";
      var maxAllowed = $rootScope.maxAllowed;
      // Note - we need to turng maxAllowed into as tring
      $http.post(url, $rootScope.maxAllowed + "").success(function() {
        $rootScope.quota.maxAllowed = maxAllowed;
        $rootScope.maxAllowed = null;
        $rootScope.addMessage('changed maxAllowed for ' + $rootScope.email + ' to ' + maxAllowed);
      });
    }
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
