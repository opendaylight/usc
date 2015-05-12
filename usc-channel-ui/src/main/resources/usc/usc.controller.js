/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
define(['app/usc/usc.module','app/usc/usc.services'], function(topology, service) {
  topology.register.controller('UscController', ['$scope', '$rootScope', 'UscService' ,  function ($scope, $rootScope, UscService) {
    $scope.updateChannels = function() {
      UscService.updateChannels();
    };
    $scope.updateChannels();
  }]);
});
