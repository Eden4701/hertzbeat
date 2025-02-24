package org.dromara.hertzbeat.manager.netty;

import com.google.common.collect.Lists;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.dromara.hertzbeat.common.entity.message.ClusterMsg;
import org.dromara.hertzbeat.common.support.CommonThreadPool;
import org.dromara.hertzbeat.manager.netty.process.CollectCyclicDataResponseProcessor;
import org.dromara.hertzbeat.manager.netty.process.CollectOneTimeDataResponseProcessor;
import org.dromara.hertzbeat.manager.netty.process.CollectorOfflineProcessor;
import org.dromara.hertzbeat.manager.netty.process.CollectorOnlineProcessor;
import org.dromara.hertzbeat.manager.netty.process.HeartbeatProcessor;
import org.dromara.hertzbeat.manager.scheduler.CollectorAndJobScheduler;
import org.dromara.hertzbeat.manager.scheduler.SchedulerProperties;
import org.dromara.hertzbeat.remoting.RemotingServer;
import org.dromara.hertzbeat.remoting.event.NettyEventListener;
import org.dromara.hertzbeat.remoting.netty.NettyHook;
import org.dromara.hertzbeat.remoting.netty.NettyRemotingServer;
import org.dromara.hertzbeat.remoting.netty.NettyServerConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * manage server
 */
@Component
@ConditionalOnProperty(prefix = "scheduler.server",
        name = "enabled", havingValue = "true")
@Slf4j
public class ManageServer {

    private final CollectorAndJobScheduler collectorAndJobScheduler;

    private ScheduledExecutorService channelSchedule;

    private RemotingServer remotingServer;

    private final Map<String, Channel> clientChannelTable = new ConcurrentHashMap<>(16);

    public ManageServer(final SchedulerProperties schedulerProperties,
                        final CollectorAndJobScheduler collectorAndJobScheduler,
                        final CommonThreadPool threadPool) {
        this.collectorAndJobScheduler = collectorAndJobScheduler;
        this.collectorAndJobScheduler.setManageServer(this);

        this.init(schedulerProperties, threadPool);
        this.start();
    }

    private void init(final SchedulerProperties schedulerProperties, final CommonThreadPool threadPool) {
        NettyServerConfig nettyServerConfig = new NettyServerConfig();
        nettyServerConfig.setPort(schedulerProperties.getServer().getPort());
        NettyEventListener nettyEventListener = new ManageNettyEventListener();
        this.remotingServer = new NettyRemotingServer(nettyServerConfig, nettyEventListener, threadPool);

        // register hook
        this.remotingServer.registerHook(Lists.newArrayList(new NettyHook() {
            @Override
            public void doBeforeRequest(ChannelHandlerContext ctx, ClusterMsg.Message message) {
                ManageServer.this.clientChannelTable.put(message.getIdentity(), ctx.channel());
            }
        }));

        // register processor
        this.remotingServer.registerProcessor(ClusterMsg.MessageType.HEARTBEAT, new HeartbeatProcessor(this));
        this.remotingServer.registerProcessor(ClusterMsg.MessageType.GO_ONLINE, new CollectorOnlineProcessor(this));
        this.remotingServer.registerProcessor(ClusterMsg.MessageType.GO_OFFLINE, new CollectorOfflineProcessor(this));
        this.remotingServer.registerProcessor(ClusterMsg.MessageType.RESPONSE_ONE_TIME_TASK_DATA, new CollectOneTimeDataResponseProcessor(this));
        this.remotingServer.registerProcessor(ClusterMsg.MessageType.RESPONSE_CYCLIC_TASK_DATA, new CollectCyclicDataResponseProcessor());

        this.channelSchedule = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        this.remotingServer.start();

        this.channelSchedule.scheduleAtFixedRate(() -> {
            ManageServer.this.clientChannelTable.forEach((collector, channel) -> {
                if (!channel.isActive()) {
                    channel.closeFuture();
                    ManageServer.this.clientChannelTable.remove(collector);
                    ManageServer.this.collectorAndJobScheduler.collectorGoOffline(collector);
                }
            });
        }, 10, 3, TimeUnit.SECONDS);
    }

    public void shutdown() {
        this.remotingServer.shutdown();

        this.channelSchedule.shutdownNow();
    }

    public CollectorAndJobScheduler getCollectorAndJobScheduler() {
        return collectorAndJobScheduler;
    }

    public Channel getChannel(final String identity) {
        Channel channel = this.clientChannelTable.get(identity);
        if (channel == null || !channel.isActive()) {
            this.clientChannelTable.remove(identity);
            log.error("client {} offline now", identity);
        }
        return channel;
    }

    public void closeChannel(final String identity) {
        Channel channel = this.getChannel(identity);
        if (channel != null) {
            this.collectorAndJobScheduler.collectorGoOffline(identity);
            ClusterMsg.Message message = ClusterMsg.Message.newBuilder().setType(ClusterMsg.MessageType.GO_CLOSE).build();
            this.remotingServer.sendMsg(channel, message);
            this.clientChannelTable.remove(identity);
            log.info("close collect client success, identity: {}", identity);
        }
    }

    public boolean isChannelExist(final String identity) {
        Channel channel = this.clientChannelTable.get(identity);
        return channel != null && channel.isActive();
    }

    public boolean sendMsg(final String identityId, final ClusterMsg.Message message) {
        Channel channel = this.getChannel(identityId);
        if (channel != null) {
            this.remotingServer.sendMsg(channel, message);
            return true;
        }
        return false;
    }

    public ClusterMsg.Message sendMsgSync(final String identityId, final ClusterMsg.Message message) {
        Channel channel = this.getChannel(identityId);
        if (channel != null) {
            return this.remotingServer.sendMsgSync(channel, message, 3000);
        }
        return null;
    }

    public class ManageNettyEventListener implements NettyEventListener {

        @Override
        public void onChannelIdle(Channel channel) {
            String identity = null;
            for (Map.Entry<String, Channel> entry : ManageServer.this.clientChannelTable.entrySet()) {
                if (entry.getValue().equals(channel)) {
                    identity = entry.getKey();
                    break;
                }
            }
            if (identity != null) {
                ManageServer.this.clientChannelTable.remove(identity);
                ManageServer.this.collectorAndJobScheduler.collectorGoOffline(identity);
                log.info("handle idle event triggered. the client {} is going offline.", identity);
            }
        }
    }
}
