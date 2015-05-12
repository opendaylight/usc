/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
define(['app/usc/usc.module'], function(usc) {
  usc.register.factory('UscRestangular', function(Restangular, ENV) {
    var UscRestangular = Restangular.withConfig(function(RestangularConfig) {
      RestangularConfig.setBaseUrl(ENV.getBaseURL("MD_SAL"));
    });
    return UscRestangular;
  });
  usc.register.factory('UscService', function(UscRestangular) {
    var org = org || {};
    org.opendaylight = org.opendaylight || {};
    org.opendaylight.usc = org.opendaylight.usc || {};
    org.opendaylight.usc.utils = org.opendaylight.usc.utils || {};
    org.opendaylight.usc.utils.get = function(id) {
      return document.getElementById(id);
    };
    org.opendaylight.usc.utils.create = function(tag) {
      return document.createElement(tag);
    };
    org.opendaylight.usc.update = function() {
      jQuery.ajax({
        type: "POST",
        "url": "/restconf/operations/usc-channel:view-channel",
        processData: false,
        contentType: "application/json",
        data: JSON.stringify({"input":{"topology-id":"usc"}}),
        success:function (response) {
          org.opendaylight.usc.process(response);
        }
      });
      return true;
    };
    org.opendaylight.usc.process = function(data) {
      var channelPannelHTML = "";
      channelPannelHTML += '<div style="color:#ffffff;padding-top:15px;padding-bottom:15px;">';
      channelPannelHTML += '<table style="width:100%;" cellspacing="1" cellpadding="1"><tr>';
      channelPannelHTML += '<td style="width:50px;"><div>Alarms</div></td>';
      channelPannelHTML += '<td><div>Description</div></td>';
      channelPannelHTML += '<td style="width:100px;"><div>Bytes In</div></td>';
      channelPannelHTML += '<td style="width:100px;"><div>Bytes Out</div></td>';
      channelPannelHTML += '</tr></table>';
      channelPannelHTML += '</div>';  
      var topologies = data["output"]["topology"];
      for (var i = 0; i < topologies.length; i++) {
        var channels = topologies[i]["channel"];
        if (typeof channels == "undefined" || channels == null) {
          break;
        }
        for (var j = 0; j < channels.length; j++) {
          var channel = channels[j];
          channelPannelHTML += '<div style="color:#ffffff;padding-top:15px;padding-bottom:15px;border-top: 1px solid #888888;">';
          channelPannelHTML += '<table style="width:100%;" cellspacing="1" cellpadding="1"><tr>';
          channelPannelHTML += '<td style="width:50px;vertical-align:top;"><div><div class="' + (channel["channel-alarms"] > 0 ? 'label-danger' : 'label-success') + '" style="background-color:' + (channel["channel-alarms"] > 0 ? '#d9534f' : '#5cb85c') + '; border-radius:35px;width:35px;height:35px;line-height:35px;text-align:center;font-size:10px;">'+channel["channel-alarms"]+'</div></div></td>';
          channelPannelHTML += '<td style="vertical-align:top;"><div>';
          channelPannelHTML += '<div><span>Channel </span><span>'+channel["channel-id"]+'</span></div>';
          channelPannelHTML += '<div><span style="color:#888888;">Controller: </span><span>'+channel["source"]["source-node"]+'</span></div>';
          channelPannelHTML += '<div><span style="color:#888888;">Device: </span><span>'+channel["destination"]["dest-node"]+'</span></div>';
          channelPannelHTML += '<div><span style="color:#888888;">Type: </span><span>'+channel["channel-type"]+'</span></div>';
          channelPannelHTML += '<div><span style="color:#888888;">Call Home: </span><span>'+channel["call-home"]+'</span></div>';
          channelPannelHTML += '<div><span style="color:#888888;">Number of Sessions: </span><span>'+channel["sessions"]+'</span></div>';
          channelPannelHTML += '</div></td>';
          channelPannelHTML += '<td style="width:100px;vertical-align:top;"><div>'+channel["bytes-in"]+' B'+'</div></td>';
          channelPannelHTML += '<td style="width:100px;vertical-align:top;"><div>'+channel["bytes-out"]+' B'+'</div></td>';
          channelPannelHTML += '</tr></table>';
          channelPannelHTML += '</div>';
          var sessions = channel["session"];
          if (typeof sessions != "undefined" && sessions != null) {
            for (var k = 0; k < sessions.length; k++) {
              var session = sessions[k];
              channelPannelHTML += '<div style="color:#ffffff;padding-top:15px;padding-bottom:15px;border-top: 1px solid #888888;">';
              channelPannelHTML += '<table style="width:100%;" cellspacing="1" cellpadding="1"><tr>';
              channelPannelHTML += '<td style="width:100px;vertical-align:top;"><div style="padding-left:50px;"><div class="' + (session["session-alarms"] > 0 ? 'label-danger' : 'label-success') + '" style="background-color:' + (session["session-alarms"] > 0 ? '#d9534f' : '#5cb85c') + '; border-radius:35px;width:35px;height:35px;line-height:35px;text-align:center;font-size:10px;">'+session["session-alarms"]+'</div></div></td>';
              channelPannelHTML += '<td style="vertical-align:top;"><div>';
              channelPannelHTML += '<div><span>Session </span><span>'+session["session-id"]+'</span></div>';
              channelPannelHTML += '<div><span style="color:#888888;">Port: </span><span>'+session["termination-point"]["termination-point-id"]+'</span></div>';
              channelPannelHTML += '</div></td>';
              channelPannelHTML += '<td style="width:100px;vertical-align:top;"><div>'+session["bytes-in"]+' B'+'</div></td>';
              channelPannelHTML += '<td style="width:100px;vertical-align:top;"><div>'+session["bytes-out"]+' B'+'</div></td>';
              channelPannelHTML += '</tr></table>';
              channelPannelHTML += '</div>';
            }
          }
        }
      }  
      var channelPanel = org.opendaylight.usc.utils.get("channelPanel");
      channelPanel.innerHTML = channelPannelHTML;
      return true;
    };
    org.opendaylight.usc.initialize = function() {
      org.opendaylight.usc.update();
    };
    window.org = org;
    var UscService = {
      updateChannels: function() {
        org.opendaylight.usc.initialize();
      }
    };
    return UscService;
  });
});
