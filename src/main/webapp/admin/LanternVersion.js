'use strict';
angular.module('LanternVersion', [])
  .controller('MainCtrl', function ($http, $scope) {
    
    $scope.submit = function () {
      $http.put('rest/LanternVersion/' + $scope.dummyVersion.id, $scope.dummyVersion);
    };
    $scope.dummyVersion = {
      id: '1.0.0-RC1',
      gitSha: 'a1b2c3',
      releaseDate: '2013-10-15T17:58:27.004+0000',
      infoUrl: 'https://github.com/getlantern/lantern/releases/1.0.0-RC1',
      installerUrls: {
        osx: 'https://s3.amazonaws.com/lantern/lantern-1.0.0-RC1.dmg',
        windows: 'https://s3.amazonaws.com/lantern/lantern-1.0.0-RC1.exe',
        ubuntu32: 'https://s3.amazonaws.com/lantern/lantern-1.0.0-RC1-32bit.deb',
        ubuntu64: 'https://s3.amazonaws.com/lantern/lantern-1.0.0-RC1-64bit.deb'
      }
    };
  });