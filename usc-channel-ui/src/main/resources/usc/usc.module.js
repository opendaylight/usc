/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
define(['angularAMD', 'app/routingConfig', 'app/core/core.services','Restangular', 'common/config/env.module'], function(ng) {

  var usc = angular.module('app.usc', ['ui.router.state','app.core','restangular', 'config']);

  usc.config(function($stateProvider, $controllerProvider, $compileProvider, $provide, $translateProvider, NavHelperProvider) {

  usc.register = {
      controller : $controllerProvider.register,
      directive : $compileProvider.directive,
      service : $provide.service,
      factory : $provide.factory
    };

    $translateProvider.useStaticFilesLoader({
      prefix: 'assets/data/locale-',
      suffix: '.json'
    });

    NavHelperProvider.addControllerUrl('app/usc/usc.controller');
    NavHelperProvider.addToMenu('usc', {
      "link": "#/usc",
      "title": "USC",
      "active": "main.usc",
      "icon": "icon-link",
      "page": {
        "title": "USC",
        "description": "USC"
      }
    });

    var access = routingConfig.accessLevels;
    $stateProvider.state('main.usc', {
      url: 'usc',
      access: access.public,
      views : {
        'content' : {
          templateUrl: 'src/app/usc/usc.tpl.html',
          controller: 'UscController'
        }
      }
    });

  });

  return usc;

});
