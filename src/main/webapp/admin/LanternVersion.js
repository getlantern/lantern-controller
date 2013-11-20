'use strict';

angular.module('LanternVersion', [])
  .constant('ENDPOINT_LATEST', 'rest/LanternVersion/latest')
  .constant('KEY_LATEST', 'latest') // must match data.LanternVersion.SINGLETON_KEY XXX DRY
  .run(function ($http, $rootScope, ENDPOINT_LATEST, KEY_LATEST) {
    $rootScope.latest = {};

    $http.get(ENDPOINT_LATEST).then(
      function onSuccess(response) {
        $rootScope.latest = response.data;
        var latest = $rootScope.latest;
        if (latest.releaseDate && latest.releaseDate.length > 10) {
          // trim off the time
          latest.releaseDate = latest.releaseDate.substring(0, 10);
        }
      },
      function onFailure(data) {
        $rootScope.error = 'Initial fetch of current latest version failed. ' +
          'If the latest version has not yet been populated, this is expected; just ' +
          'submit this form to populate it and this won\'t happen again. (Check the ' +
          'js console to view the actual error response.)';
        console.error('Initial fetch of current latest version failed:', data);
      }
    );
    $rootScope.submit = function () {
      $rootScope.latest.key = KEY_LATEST;
      $http.put(ENDPOINT_LATEST, $rootScope.latest);
    };
  })
  .config(function($provide, $httpProvider) {
    // Set up global loading indicator behavior
    // See http://stackoverflow.com/questions/11956827/angularjs-intercept-all-http-json-responses
    $provide.factory('myHttpInterceptor', function ($q, $rootScope) {
      return {
        request: function(config) {
          $rootScope.error = null;
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
          $rootScope.error = rejection.data;
          $rootScope.busy = false;
          return $q.reject(rejection);
        }
      }
    });
    $httpProvider.interceptors.push('myHttpInterceptor');
  });