'use strict';

angular.module('LatestLanternVersion', [])
  // must match the @Path annotation of org.lantern.admin.rest.LatestLanternVersionResource
  .constant('ENDPOINT_LATEST', 'rest/LatestLanternVersion')
   // must match org.lantern.data.LatestLanternVersion.SINGLETON_KEY
  .constant('KEY_LATEST', 'latest')
  .run(function ($http, $rootScope, $timeout, ENDPOINT_LATEST, KEY_LATEST) {
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
      $http.put(ENDPOINT_LATEST, $rootScope.latest).success(function () {
        $rootScope.success = true;
        $timeout(function () { delete $rootScope.success; }, 2000);
      });
    };
  })
  // XXX can we factor this out into a separate module?:
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