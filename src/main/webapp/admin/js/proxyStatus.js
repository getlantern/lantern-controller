angular.module('ProxyStatus', [ 'googlechart' ]).controller(
    'ProxyStatusCtrl',
    function($scope, $http) {
      $scope.$watch("proxies", function(proxies) {
        console.debug("Setting up charts for each proxy");
        if (proxies) {
          proxies
              .forEach(function(proxy) {
                proxy.loadAverageSummaryChart = {
                  type : "ColumnChart",
                  displayed : true,
                  data : proxy.loadAverages,
                  hAxis : {
                    minValue : new Date(new Date().getTime() - 25 * 60 * 60
                        * 1000),
                    maxValue : new Date(new Date().getTime() + 60 * 60 * 1000)
                  }
                };
              });
        }
      }, false);

      //$scope.proxies = [{"loadAverages":{"cols":[{"id":"period","label":"Period","type":"string"},{"id":"loadAverage","label":"Load Average","type":"number"}],"rows":[{"c":[{"v":"Wed Sep 18 20:31:40 UTC 2013"},{"v":0}]},{"c":[{"v":"Wed Sep 18 21:00:33 UTC 2013"},{"v":0}]},{"c":[{"v":"Wed Sep 18 22:00:39 UTC 2013"},{"v":0}]},{"c":[{"v":"Wed Sep 18 23:00:14 UTC 2013"},{"v":0}]},{"c":[{"v":"Thu Sep 19 00:00:30 UTC 2013"},{"v":0}]},{"c":[{"v":"Thu Sep 19 01:00:11 UTC 2013"},{"v":0}]},{"c":[{"v":"Thu Sep 19 02:00:00 UTC 2013"},{"v":0}]},{"c":[{"v":"Thu Sep 19 03:00:11 UTC 2013"},{"v":0}]},{"c":[{"v":"Thu Sep 19 04:00:52 UTC 2013"},{"v":0}]},{"c":[{"v":"Thu Sep 19 05:00:13 UTC 2013"},{"v":0}]},{"c":[{"v":"Thu Sep 19 06:00:01 UTC 2013"},{"v":0}]},{"c":[{"v":"Thu Sep 19 07:00:02 UTC 2013"},{"v":0}]},{"c":[{"v":"Thu Sep 19 08:00:03 UTC 2013"},{"v":0}]},{"c":[{"v":"Thu Sep 19 09:00:06 UTC 2013"},{"v":0}]},{"c":[{"v":"Thu Sep 19 10:00:17 UTC 2013"},{"v":0}]},{"c":[{"v":"Thu Sep 19 11:00:18 UTC 2013"},{"v":0}]},{"c":[{"v":"Thu Sep 19 12:00:03 UTC 2013"},{"v":0}]},{"c":[{"v":"Thu Sep 19 13:00:00 UTC 2013"},{"v":0}]},{"c":[{"v":"Thu Sep 19 14:00:13 UTC 2013"},{"v":0}]},{"c":[{"v":"Thu Sep 19 15:00:19 UTC 2013"},{"v":0}]},{"c":[{"v":"Thu Sep 19 16:00:13 UTC 2013"},{"v":0}]}]},"latestLoadAverage":0,"userId":"ox@getlantern.org","instanceId":"407fedd66125a603d3152a2bd168d2e5"},{"loadAverages":{"cols":[{"id":"period","label":"Period","type":"string"},{"id":"loadAverage","label":"Load Average","type":"number"}],"rows":[{"c":[{"v":"Thu Sep 19 15:36:44 UTC 2013"},{"v":1.99462890625}]},{"c":[{"v":"Thu Sep 19 16:00:16 UTC 2013"},{"v":1.09033203125}]}]},"latestLoadAverage":1.09033203125,"userId":"ox@getlantern.org","instanceId":"787a8dc2e695ba3dff51ce89e87b2499"},{"loadAverages":{"cols":[{"id":"period","label":"Period","type":"string"},{"id":"loadAverage","label":"Load Average","type":"number"}],"rows":[{"c":[{"v":"Thu Sep 19 01:17:25 UTC 2013"},{"v":0}]},{"c":[{"v":"Thu Sep 19 01:17:25 UTC 2013"},{"v":0}]},{"c":[{"v":"Thu Sep 19 01:17:25 UTC 2013"},{"v":0}]},{"c":[{"v":"Thu Sep 19 01:17:25 UTC 2013"},{"v":0}]},{"c":[{"v":"Thu Sep 19 01:17:25 UTC 2013"},{"v":1.0546875}]}]},"latestLoadAverage":1.0546875,"userId":"ox@getlantern.org","instanceId":"b01759d5a80c5962160fa0c643896e0f"}];

      $http.get('/proxyStatus').success(function(data) {
        console.log(data);
        console.log(JSON.stringify(data));
        $scope.proxies = data;
      });
    });