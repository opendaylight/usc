package org.opendaylight.usc.plugin;

import java.util.List;

import org.opendaylight.usc.manager.cluster.UscChannelIdentifier;
import org.opendaylight.usc.manager.cluster.UscListTable;

public class UscSessionIdManager {
    private UscListTable<UscChannelIdentifier, Integer> sessionIdMap = new UscListTable<UscChannelIdentifier, Integer>();
    private static UscSessionIdManager instance = new UscSessionIdManager();

    private UscSessionIdManager() {

    }

    public static UscSessionIdManager getInstance() {
        return instance;
    }

    public int create(UscChannelIdentifier channelId) {
        int ret = 1;
        List<Integer> idList = sessionIdMap.get(channelId);
        if (idList != null) {
            for (int i = 1; i <= Character.MAX_VALUE; i++) {
                if (!idList.contains(i)) {
                    ret = i;
                    break;
                }
            }
        }
        sessionIdMap.addEntry(channelId, ret);
        return ret;
    }

    public void remove(UscChannelIdentifier channelId, int sessionId) {
        sessionIdMap.removeEntry(channelId, sessionId);
    }

    public boolean exist(UscChannelIdentifier channelId, int sessionId) {
        List<Integer> idList = sessionIdMap.get(channelId);
        if (idList != null) {
            if (idList.contains(sessionId)) {
                return true;
            }
        }
        return false;
    }

    public void clear() {
        sessionIdMap.clear();
    }
}
