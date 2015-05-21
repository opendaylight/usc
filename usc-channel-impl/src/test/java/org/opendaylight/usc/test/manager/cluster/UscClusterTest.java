/*
 * Copyright (c) 2015 Huawei, Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.usc.test.manager.cluster;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import org.apache.commons.codec.binary.Base64;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.usc.agent.UscAgentTcp;
import org.opendaylight.usc.manager.UscTopologyService;
import org.opendaylight.usc.plugin.model.UscChannel.ChannelType;
import org.opendaylight.usc.test.AbstractTest;
import org.opendaylight.usc.test.manager.cluster.UscController.UscChannel;
import org.opendaylight.usc.test.plugin.EchoServerTcp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.test.rev150101.AddChannelInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.test.rev150101.AddChannelInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.usc.test.rev150101.add.channel.input.ChannelBuilder;

public class UscClusterTest extends AbstractTest {
    private List<String> serverList = new ArrayList<String>();
    private List<UscController> controllerList = new ArrayList<UscController>();

    // private int nodeNumber = 2;
    // private Gson gson = new Gson();

    @Before
    public void setUp() {
        init();
    }

    public AutoCloseable startEchoServer(boolean enableEncryption) {
        EchoServerTcp echoServer = new EchoServerTcp(enableEncryption);
        Executors.newSingleThreadExecutor().submit(echoServer);
        return echoServer;
    }

    public AutoCloseable startAgent(boolean callHome) throws IOException, InterruptedException {
        UscAgentTcp agent = new UscAgentTcp(callHome);
        if (!callHome) {
            Executors.newSingleThreadExecutor().submit(agent);
        }
        return agent;
    }

    private void init() {
        String channelType = ChannelType.TLS.name();
        short appPort = 2007;
        serverList.add("philo-workstation");
        serverList.add("node2");
        UscController con1 = new UscController(serverList.get(0));
        String ip1 = "192.168.56.1";
        con1.addIp(ip1);
        con1.addChannel(ip1, channelType, false);
        con1.getChannelList().get(0).addSession(appPort);
        controllerList.add(con1);

        UscController con2 = new UscController(serverList.get(1));
        String ip2 = "192.168.56.102";
        con2.addIp(ip2);
        con2.addChannel(ip2, channelType, true);
        con2.getChannelList().get(0).addSession(appPort);
        controllerList.add(con2);
    }

    private String getAddChannelInput(String hostname, int port, boolean remote) {
        // {"input":{"channel":{"hostname":"192.168.56.1","port":2007,"remote":false}}}
        return "{\"input\":{\"channel\":{\"hostname\":\"" + hostname + "\",\"port\":" + port + ",\"remote\":" + remote
                + "}}}";
    }
    
//  @Test
    public void cluster() {
        AutoCloseable appServer = startEchoServer(false);
        AutoCloseable javaAgent = null;
        try {
            javaAgent = startAgent(false);
        } catch (IOException | InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        try {
            Thread.sleep(500);
        } catch (InterruptedException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        addChannel("192.168.56.1", "philo-workstation", "192.168.56.1", (short) 2007, false);
        addChannel("192.168.56.102", "node2", "192.168.56.1", (short) 2007, true);
        sendContentToChannel("192.168.56.102", "node2", "192.168.56.1", (short) 2007, "this is a test from junit.");
        try {

            appServer.close();
            if (javaAgent != null) {
                javaAgent.close();
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

//    @Test
    public void singleNode() {
        String node = "philo-workstation";
        String ip = "192.168.56.1";
        short port = (short) 2007;
        AutoCloseable appServer = startEchoServer(false);
        AutoCloseable javaAgent = null;
        try {
            javaAgent = startAgent(false);
        } catch (IOException | InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        try {
            Thread.sleep(500);
        } catch (InterruptedException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        addChannel(ip, node, ip, port, false);
        sendContentToChannel(ip, node, ip, port, "this is a test from junit.");
        try {

            appServer.close();
            if (javaAgent != null) {
                javaAgent.close();
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private String sendTopologyPost(String serverIp) {
        // building request body for inserting a static route
        String baseURL = "http://" + serverIp + ":8181/restconf/operations/usc-channel:view-channel";
        String requestBody = "{\"input\":{\"topology-id\":\"usc\"}}";
        return sendPost(baseURL, requestBody);
    }

    public void addChannel(UscController contoller, int channelIndex, int sessionIndex) {
        String url = getRootUrl(contoller) + "restconf/operations/usc-test:add-channel";
        UscChannel channel = contoller.getChannelList().get(channelIndex);
        AddChannelInputBuilder inputBuilder = new AddChannelInputBuilder();
        ChannelBuilder channelBuilder = new ChannelBuilder();
        AddChannelInput input = inputBuilder.setChannel(
                channelBuilder.setHostname(channel.getIp())
                        .setPort((Short) (channel.getSessionList().get(sessionIndex).getAppPort()))
                        .setRemote(channel.isRemote()).build()).build();
        String body = getAddChannelInput(input.getChannel().getHostname(), input.getChannel().getPort(), input
                .getChannel().isRemote());
        // System.out.println("body is " + body);
        String result = sendPost(url, body);
        try {
            JSONObject json = new JSONObject(result);
            JSONObject output = json.getJSONObject("output");
            Assert.assertTrue(output.getString("result").contains("Succeed"));
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private String getSendNetconfRequest(String hostname, int port, String content) {
        // {"input":{"channel":{"hostname":"192.168.56.1","port":2007,"content":"This is a test for route request."}}}
        return "{\"input\":{\"channel\":{\"hostname\":\"" + hostname + "\",\"port\":" + port + ",\"content\":\""
                + content + "\"}}}";
    }

    public void sendContentToChannel(String serverIp, String serverName, String deviceIp, short appPort, String content) {
        String url = getRootUrl(serverIp) + "restconf/operations/usc-test:netconf-request";
        String body = getSendNetconfRequest(deviceIp, appPort, content);
        System.out.println("body is " + body);
        String result = sendPost(url, body);
        if (result == null) {
            Assert.assertTrue(false);
        }
        try {
            JSONObject json = new JSONObject(result);
            JSONObject output = json.getJSONObject("output");
            Assert.assertTrue(output.getString("result").contains("Succeed"));
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            Assert.assertTrue(false);
        }
        result = sendTopologyPost(serverIp);
        if (result == null) {
            Assert.assertTrue(false);
        }
        try {
            JSONObject json = new JSONObject(result);
            JSONObject output = json.getJSONObject("output");
            JSONArray topologyList = output.getJSONArray("topology");
            JSONObject topology = topologyList.getJSONObject(0);
            JSONObject tmp = null;
            JSONArray tmpArray = null;
            boolean ret = false;

            // check channel
            JSONArray channelList = topology.getJSONArray("channel");
            JSONObject channel = null;
            String channelId = null;
            JSONObject session = null;
            boolean findPort = false;
            for (int i = 0; i < channelList.length(); i++) {
                channel = (JSONObject) channelList.get(i);
                channelId = channel.getString("channel-id");
                if (channelId.contains(serverName) && channelId.contains(deviceIp)) {
                    Assert.assertTrue(channelId.contains(serverName) && channelId.contains(deviceIp));
                    tmp = (JSONObject) channel.get("source");
                    Assert.assertTrue(tmp.getString("source-node").equalsIgnoreCase(serverName));
                    tmp = (JSONObject) channel.get("destination");
                    Assert.assertTrue(tmp.getString("dest-node").equalsIgnoreCase(deviceIp));
                    // Assert.assertSame(channel.getInt("bytes-in"),
                    // content.length());
                    Assert.assertSame(channel.getInt("bytes-out"), content.length());
                    tmpArray = channel.getJSONArray("session");
                    for (int j = 0; j < tmpArray.length(); j++) {
                        session = (JSONObject) tmpArray.get(j);
                        tmp = session.getJSONObject("termination-point");
                        if (tmp.getString("termination-point-id").equalsIgnoreCase(appPort + "")) {
                            findPort = true;
                            // Assert.assertSame(session.getInt("bytes-in"),
                            // content.length());
                            Assert.assertSame(session.getInt("bytes-out"), content.length());
                        }
                    }
                    ret = true;
                }
            }
            Assert.assertTrue(findPort);
            // check node
            if (ret) {
                JSONArray nodeList = topology.getJSONArray("node");
                JSONObject node = null;
                boolean findServer = false;
                boolean findDevice = false;
                for (int i = 0; i < nodeList.length(); i++) {
                    node = (JSONObject) nodeList.get(i);
                    if (node.getString("node-id").equalsIgnoreCase(serverName)) {
                        Assert.assertTrue(node.getString("node-type").equalsIgnoreCase(
                                UscTopologyService.NODE_TYPE_CONTROLLER));
                        findServer = true;
                    }
                    if (node.getString("node-id").equalsIgnoreCase(deviceIp)) {
                        Assert.assertTrue(node.getString("node-type").equalsIgnoreCase(
                                UscTopologyService.NODE_TYPE_NETWORK_DEVICE));
                        findDevice = true;
                    }
                    if (findServer && findDevice) {
                        break;
                    }
                }
                Assert.assertTrue(findServer && findDevice);
            }
            Assert.assertTrue(ret);
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            Assert.assertTrue(false);
        }
    }

    public void addChannel(String serverIp, String serverName, String deviceIp, short appPort, boolean isRemote) {
        String url = getRootUrl(serverIp) + "restconf/operations/usc-test:add-channel";
        String body = getAddChannelInput(deviceIp, appPort, isRemote);
        System.out.println("body is " + body);
        String result = sendPost(url, body);
        if (result == null) {
            Assert.assertTrue(false);
        }
        System.out.println("result is " + result);
        try {
            JSONObject json = new JSONObject(result);
            JSONObject output = json.getJSONObject("output");
            Assert.assertTrue(output.getString("result").contains("Succeed"));
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            Assert.assertTrue(false);
        }
        result = sendTopologyPost(serverIp);
        if (result == null) {
            Assert.assertTrue(false);
        }
        try {
            JSONObject json = new JSONObject(result);
            JSONObject output = json.getJSONObject("output");
            JSONArray topologyList = output.getJSONArray("topology");
            JSONObject topology = topologyList.getJSONObject(0);
            JSONObject tmp = null;
            boolean ret = false;

            // check channel
            JSONArray channelList = topology.getJSONArray("channel");
            JSONObject channel = null;
            String channelId = null;
            String channelType = null;
            for (int i = 0; i < channelList.length(); i++) {
                channel = (JSONObject) channelList.get(i);
                channelId = channel.getString("channel-id");
                if (channelId.contains(serverName) && channelId.contains(deviceIp)) {
                    Assert.assertTrue(channelId.contains(serverName) && channelId.contains(deviceIp));
                    tmp = (JSONObject) channel.get("source");
                    Assert.assertTrue(tmp.getString("source-node").equalsIgnoreCase(serverName));
                    tmp = (JSONObject) channel.get("destination");
                    Assert.assertTrue(tmp.getString("dest-node").equalsIgnoreCase(deviceIp));
                    if (isRemote) {
                        channelType = channel.getString("channel-type");
                        Assert.assertTrue(channelType.contains("Remote"));
                    }
                    ret = true;
                }
            }
            // check node
            if (ret) {
                JSONArray nodeList = topology.getJSONArray("node");
                JSONObject node = null;
                boolean findServer = false;
                boolean findDevice = false;
                for (int i = 0; i < nodeList.length(); i++) {
                    node = (JSONObject) nodeList.get(i);
                    if (node.getString("node-id").equalsIgnoreCase(serverName)) {
                        Assert.assertTrue(node.getString("node-type").equalsIgnoreCase(
                                UscTopologyService.NODE_TYPE_CONTROLLER));
                        findServer = true;
                    }
                    if (node.getString("node-id").equalsIgnoreCase(deviceIp)) {
                        Assert.assertTrue(node.getString("node-type").equalsIgnoreCase(
                                UscTopologyService.NODE_TYPE_NETWORK_DEVICE));
                        findDevice = true;
                    }
                    if (findServer && findDevice) {
                        break;
                    }
                }
                Assert.assertTrue(findServer && findDevice);
            }
            Assert.assertTrue(ret);
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            Assert.assertTrue(false);
        }
    }

    private String getRootUrl(UscController controller) {
        return getRootUrl(controller.getIpList().get(0));
    }

    private String getRootUrl(String hostip) {
        return "http://" + hostip + ":8181/";
    }

    // private boolean existNode(String nodeId, String type, List<Node>
    // nodeList) {
    // for (Node node : nodeList) {
    // if (node.getNodeId().getValue().equals(nodeId) &&
    // type.equals(node.getNodeType())) {
    // return true;
    // }
    // }
    // return false;
    // }

    public String sendPost(String baseURL, String requestBody) {
        // attaching authentication information
        String authString = "admin:admin";
        byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
        String authStringEnc = new String(authEncBytes);
        URL url;
        StringBuilder sb = new StringBuilder();

        try {
            url = new URL(baseURL);
        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return e.getMessage();
        }
        // creating the URLConnection
        HttpURLConnection connection;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Basic " + authStringEnc);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Content-Length", requestBody.length() + "");
            connection.setRequestProperty("Accept", "application/json");
            // now add the request body
            connection.setDoOutput(true);
            OutputStreamWriter wr = new OutputStreamWriter(connection.getOutputStream());
            wr.write(requestBody);
            wr.flush();
            connection.connect();

            // getting the result, first check response code
            Integer httpResponseCode = connection.getResponseCode();
            if (httpResponseCode > 299) {
                System.out.println("ResponseCode = " + httpResponseCode);
                return null;
            }
            // get the result string from the inputstream.
            InputStream is = connection.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));

            int cp;
            while ((cp = rd.read()) != -1) {
                sb.append((char) cp);
            }
            System.out.println("Content = " + sb.toString());
            is.close();
            connection.disconnect();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            // e.printStackTrace();
            return e.getMessage();
        }
        return sb.toString();
    }
}
