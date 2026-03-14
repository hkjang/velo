package io.velo.was.transport.netty;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public final class NativeTransportSelector {

    private NativeTransportSelector() {
    }

    public static EventLoopGroup createBossGroup(int threads) {
        if (Epoll.isAvailable()) {
            return new EpollEventLoopGroup(threads);
        }
        if (KQueue.isAvailable()) {
            return new KQueueEventLoopGroup(threads);
        }
        return new NioEventLoopGroup(threads);
    }

    public static EventLoopGroup createWorkerGroup(int threads) {
        if (Epoll.isAvailable()) {
            return new EpollEventLoopGroup(threads);
        }
        if (KQueue.isAvailable()) {
            return new KQueueEventLoopGroup(threads);
        }
        return new NioEventLoopGroup(threads);
    }

    public static Class<? extends ServerChannel> serverChannelClass() {
        if (Epoll.isAvailable()) {
            return EpollServerSocketChannel.class;
        }
        if (KQueue.isAvailable()) {
            return KQueueServerSocketChannel.class;
        }
        return NioServerSocketChannel.class;
    }

    public static String transportName() {
        if (Epoll.isAvailable()) {
            return "epoll";
        }
        if (KQueue.isAvailable()) {
            return "kqueue";
        }
        return "nio";
    }
}

