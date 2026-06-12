package ru.tcynik.klitch.mesh.service;

import ru.tcynik.klitch.mesh.model.DataPacket;
import ru.tcynik.klitch.mesh.model.NodeInfo;
import ru.tcynik.klitch.mesh.model.MeshUser;
import ru.tcynik.klitch.mesh.model.Position;
import ru.tcynik.klitch.mesh.model.MyNodeInfo;

interface IMeshService {
    void subscribeReceiver(String packageName, String receiverName);

    void setOwner(in MeshUser user);

    void setRemoteOwner(in int requestId, in int destNum, in byte []payload);
    void getRemoteOwner(in int requestId, in int destNum);

    String getMyId();

    int getPacketId();

    void send(inout DataPacket packet);

    List<NodeInfo> getNodes();

    byte []getConfig();
    void setConfig(in byte []payload);

    void setRemoteConfig(in int requestId, in int destNum, in byte []payload);
    void getRemoteConfig(in int requestId, in int destNum, in int configTypeValue);

    void setModuleConfig(in int requestId, in int destNum, in byte []payload);
    void getModuleConfig(in int requestId, in int destNum, in int moduleConfigTypeValue);

    void setRingtone(in int destNum, in String ringtone);
    void getRingtone(in int requestId, in int destNum);

    void setCannedMessages(in int destNum, in String messages);
    void getCannedMessages(in int requestId, in int destNum);

    void setChannel(in byte []payload);

    void setRemoteChannel(in int requestId, in int destNum, in byte []payload);
    void getRemoteChannel(in int requestId, in int destNum, in int channelIndex);

    void beginEditSettings(in int destNum);

    void commitEditSettings(in int destNum);

    void removeByNodenum(in int requestID, in int nodeNum);

    void requestPosition(in int destNum, in Position position);

    void setFixedPosition(in int destNum, in Position position);

    void requestTraceroute(in int requestId, in int destNum);

    void requestNeighborInfo(in int requestId, in int destNum);

    void requestShutdown(in int requestId, in int destNum);

    void requestReboot(in int requestId, in int destNum);

    void requestFactoryReset(in int requestId, in int destNum);

    void rebootToDfu(in int destNum);

    void requestNodedbReset(in int requestId, in int destNum, in boolean preserveFavorites);

    byte []getChannelSet();

    String connectionState();

    boolean setDeviceAddress(String deviceAddr);

    MyNodeInfo getMyNodeInfo();

    void startFirmwareUpdate();

    int getUpdateStatus();

    void startProvideLocation();

    void stopProvideLocation();

    void requestUserInfo(in int destNum);

    void getDeviceConnectionStatus(in int requestId, in int destNum);

    void requestTelemetry(in int requestId, in int destNum, in int type);

    void requestRebootOta(in int requestId, in int destNum, in int mode, in byte []hash);
}
