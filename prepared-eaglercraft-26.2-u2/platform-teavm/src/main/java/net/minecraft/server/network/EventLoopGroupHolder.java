package net.minecraft.server.network;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalIoHandler;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

/**
 * Browser implementation shadowing the desktop EventLoopGroupHolder by FQN.
 * Native epoll and kqueue transports are not on the TeaVM classpath.
 */
public abstract class EventLoopGroupHolder {
	private static final EventLoopGroupHolder NIO = new EventLoopGroupHolder("NIO", NioSocketChannel.class, NioServerSocketChannel.class) {
		@Override
		protected IoHandlerFactory ioHandlerFactory() {
			return NioIoHandler.newFactory();
		}
	};
	private static final EventLoopGroupHolder LOCAL = new EventLoopGroupHolder("Local", LocalChannel.class, LocalServerChannel.class) {
		@Override
		protected IoHandlerFactory ioHandlerFactory() {
			return LocalIoHandler.newFactory();
		}
	};
	private final String type;
	private final Class<? extends Channel> channelCls;
	private final Class<? extends ServerChannel> serverChannelCls;
	private volatile @Nullable EventLoopGroup group;

	public static EventLoopGroupHolder remote(final boolean allowNativeTransport) {
		return NIO;
	}

	public static EventLoopGroupHolder local() {
		return LOCAL;
	}

	private EventLoopGroupHolder(final String type, final Class<? extends Channel> channelCls,
			final Class<? extends ServerChannel> serverChannelCls) {
		this.type = type;
		this.channelCls = channelCls;
		this.serverChannelCls = serverChannelCls;
	}

	private ThreadFactory createThreadFactory() {
		final AtomicInteger threadNumber = new AtomicInteger();
		return task -> {
			final Thread thread = new Thread(task, "Netty " + this.type + " IO #" + threadNumber.getAndIncrement());
			thread.setDaemon(true);
			return thread;
		};
	}

	protected abstract IoHandlerFactory ioHandlerFactory();

	private EventLoopGroup createEventLoopGroup() {
		return new MultiThreadIoEventLoopGroup(this.createThreadFactory(), this.ioHandlerFactory());
	}

	public EventLoopGroup eventLoopGroup() {
		EventLoopGroup result = this.group;
		if (result == null) {
			synchronized (this) {
				result = this.group;
				if (result == null) {
					result = this.createEventLoopGroup();
					this.group = result;
				}
			}
		}

		return result;
	}

	public Class<? extends Channel> channelCls() {
		return this.channelCls;
	}

	public Class<? extends ServerChannel> serverChannelCls() {
		return this.serverChannelCls;
	}
}
