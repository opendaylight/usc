package org.opendaylight.usc.test.manager.cluster;

import java.util.ArrayList;
import java.util.List;

public class UscController {
    private String name;
    private List<String> ipList = new ArrayList<String>();
    private List<UscChannel> channelList = new ArrayList<UscChannel>();

    public UscController(String name) {
        super();
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getIpList() {
        return ipList;
    }

    public void setIpList(List<String> ipList) {
        this.ipList = ipList;
    }

    public void addIp(String ip) {
        ipList.add(ip);
    }

    public void addChannel(String ip, String type, boolean remote) {
        UscChannel channel = new UscChannel(ip, type, remote);
        channelList.add(channel);
    }

    public List<UscChannel> getChannelList() {
        return channelList;
    }

    public void setChannelList(List<UscChannel> channelList) {
        this.channelList = channelList;
    }

    public class UscChannel {
        private String ip;
        private String type;
        private boolean remote;
        private List<UscSession> sessionList = new ArrayList<UscSession>();

        public UscChannel(String ip, String type, boolean remote) {
            super();
            this.ip = ip;
            this.type = type;
            this.remote = remote;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isRemote() {
            return remote;
        }

        public void setRemote(boolean remote) {
            this.remote = remote;
        }

        public List<UscSession> getSessionList() {
            return sessionList;
        }

        public void setSessionList(List<UscSession> sessionList) {
            this.sessionList = sessionList;
        }

        public void addSession(short appPort) {
            UscSession s = new UscSession(appPort);
            sessionList.add(s);
        }

        public class UscSession {
            private short appPort;

            public UscSession(short appPort) {
                super();
                this.appPort = appPort;
            }

            public short getAppPort() {
                return appPort;
            }

            public void setAppPort(short appPort) {
                this.appPort = appPort;
            }
        }
    }
}
