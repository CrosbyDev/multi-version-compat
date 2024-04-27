package io.github.racoondog.multiversion;

import com.google.common.collect.Iterables;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.item.Item;
import net.minecraft.network.ClientConnection;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * Adapted from <a href="https://github.com/Earthcomputer/clientcommands/blob/fabric/src/main/java/net/earthcomputer/clientcommands/MultiVersionCompat.java">clientcommands</a>.
 * Originally made by <a href="https://github.com/Earthcomputer">Earthcomputer</a>, <a href="https://github.com/FlorianMichael">FlorianMichael</a>, and <a href="https://github.com/RaphiMC">RaphiMC</a>.
 * Modified by Crosby to replace java reflect with java invoke and add protocol version overriding.
 */
public abstract sealed class MultiVersionImpl implements MultiVersion {
    private static final Logger LOGGER = LoggerFactory.getLogger("MultiVersion");

    @Override
    public void setProtocolVersion(int protocolVersion) {}

    @Override
    public boolean doesItemExist(Item item) {
        return true;
    }

    @Override
    public final boolean doesBlockExist(Block block) {
        return doesItemExist(block.asItem());
    }

    public static final MultiVersionImpl INSTANCE = Util.make(() -> {
        try {
            FabricLoader loader = FabricLoader.getInstance();
            if (loader.isModLoaded("viafabric")) {
                return new ViaFabric(MethodHandles.lookup());
            } else if (loader.isModLoaded("viafabricplus")) {
                return new ViaFabricPlus(MethodHandles.lookup());
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Could not load proper MultiVersionCompat", e);
        }
        return new None();
    });

    private static final class None extends MultiVersionImpl {
        private int protocolVersionOverride = SharedConstants.getProtocolVersion();

        @Override
        public int getProtocolVersion() {
            return protocolVersionOverride;
        }

        @Override
        public void setProtocolVersion(int protocolVersion) {
            if (protocolVersion == MultiVersion.RESET) {
                this.protocolVersionOverride = SharedConstants.getProtocolVersion();
            } else {
                this.protocolVersionOverride = protocolVersion;
            }
        }

        @Override
        public String getProtocolName() {
            return SharedConstants.getGameVersion().getName();
        }
    }

    private static abstract sealed class AbstractViaVersion extends MultiVersionImpl {
        protected final Class<?> protocolVersion;
        private final MethodHandle getVersion;
        private final MethodHandle getIncludedVersions;

        protected AbstractViaVersion(MethodHandles.Lookup lookup) throws ReflectiveOperationException {
            protocolVersion = Class.forName("com.viaversion.viaversion.api.protocol.version.ProtocolVersion");
            getVersion = lookup.unreflect(protocolVersion.getMethod("getVersion")).asType(MethodType.methodType(int.class, Object.class));
            getIncludedVersions = lookup.unreflect(protocolVersion.getMethod("getIncludedVersions")).asType(MethodType.methodType(Set.class, Object.class));
        }

        protected abstract Object getCurrentVersion() throws Throwable;

        @Override
        public int getProtocolVersion() {
            try {
                return (int) getVersion.invokeExact(getCurrentVersion());
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public String getProtocolName() {
            Set<String> includedVersions;
            try {
                includedVersions = (Set<String>) getIncludedVersions.invokeExact(getCurrentVersion());
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
            // the returned set is sorted, so the last version is the latest one
            return Iterables.getLast(includedVersions);
        }
    }

    private static final class ViaFabric extends AbstractViaVersion {
        private final Class<?> fabricDecodeHandler;
        private final VarHandle channel;
        private final MethodHandle getInfo;
        private final MethodHandle getProtocolInfo;
        private final MethodHandle getServerProtocolVersion;
        private final MethodHandle isRegistered;
        private final MethodHandle getProtocol;

        private ViaFabric(MethodHandles.Lookup lookup) throws ReflectiveOperationException {
            super(lookup);
            fabricDecodeHandler = Class.forName("com.viaversion.fabric.common.handler.FabricDecodeHandler");
            Method getInfoMethod = fabricDecodeHandler.getMethod("getInfo");
            getInfo = lookup.unreflect(getInfoMethod).asType(MethodType.methodType(Object.class, ChannelHandler.class));

            Method getProtocolInfoMethod = getInfoMethod.getReturnType().getMethod("getProtocolInfo");
            getProtocolInfo = lookup.unreflect(getProtocolInfoMethod).asType(MethodType.methodType(Object.class, Object.class));
            getServerProtocolVersion = lookup.unreflect(getProtocolInfoMethod.getReturnType().getMethod("getServerProtocolVersion")).asType(MethodType.methodType(int.class, Object.class));

            isRegistered = lookup.unreflect(protocolVersion.getMethod("isRegistered", int.class))
                    .asType(MethodType.methodType(boolean.class, int.class));
            getProtocol = lookup.unreflect(protocolVersion.getMethod("getProtocol", int.class))
                    .asType(MethodType.methodType(Object.class, int.class));

            Field channelField = null;
            for (Field field : ClientConnection.class.getDeclaredFields()) {
                if (field.getType() == Channel.class) {
                    channelField = field;
                    channelField.setAccessible(true);
                    break;
                }
            }
            if (channelField == null) {
                throw new NoSuchFieldException("Could not find channel field in ClientConnection");
            }
            channel = lookup.unreflectVarHandle(channelField);
        }

        @Override
        public int getProtocolVersion() {
            try {
                return doGetProtocolVersion();
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }

        @Override
        protected Object getCurrentVersion() throws Throwable {
            int protocolVersion = doGetProtocolVersion();

            if (!(boolean) isRegistered.invokeExact(protocolVersion)) {
                protocolVersion = SharedConstants.getProtocolVersion();
            }
            return (Object) getProtocol.invokeExact(protocolVersion);
        }

        private int doGetProtocolVersion() throws Throwable {
            int protocolVersion = SharedConstants.getProtocolVersion();

            // https://github.com/ViaVersion/ViaFabric/blob/fda8d39147d46c80698d204538ede790f02589f6/viafabric-mc18/src/main/java/com/viaversion/fabric/mc18/mixin/debug/client/MixinDebugHud.java
            ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
            if (networkHandler != null) {
                Channel channel = (Channel) this.channel.get(networkHandler.getConnection());
                ChannelHandler viaDecoder = channel.pipeline().get("via-decoder");
                if (fabricDecodeHandler.isInstance(viaDecoder)) {
                    Object protocol = getProtocolInfo.invokeExact(getInfo.invokeExact(viaDecoder));
                    if (protocol != null) {
                        protocolVersion = (int) getServerProtocolVersion.invokeExact(protocol);
                    }
                }
            }

            return protocolVersion;
        }
    }

    private static final class ViaFabricPlus extends AbstractViaVersion {
        private final MethodHandle getTargetVersion;
        private final MethodHandle olderThan;
        private final MethodHandle itemRegistryDiffKeepItem;

        private ViaFabricPlus(MethodHandles.Lookup lookup) throws ReflectiveOperationException {
            super(lookup);
            Class<?> protocolTranslator = Class.forName("de.florianmichael.viafabricplus.protocoltranslator.ProtocolTranslator");
            getTargetVersion = lookup.unreflect(protocolTranslator.getMethod("getTargetVersion"))
                    .asType(MethodType.methodType(Object.class));

            Object release1_7_2Version = protocolVersion.getField("v1_7_2").get(null);
            olderThan = MethodHandles.insertArguments(lookup.unreflect(protocolVersion.getMethod("olderThan", protocolVersion))
                    .asType(MethodType.methodType(boolean.class, Object.class, release1_7_2Version.getClass())),
                    1, release1_7_2Version);

            Class<?> itemRegistryDiff = Class.forName("de.florianmichael.viafabricplus.fixes.data.ItemRegistryDiff");
            itemRegistryDiffKeepItem = lookup.unreflect(itemRegistryDiff.getMethod("keepItem", Item.class))
                    .asType(MethodType.methodType(boolean.class, Item.class));
        }

        @Override
        public int getProtocolVersion() {
            try {
                final boolean isOlderThan1_7_2 = (boolean) olderThan.invokeExact(getTargetVersion.invokeExact());
                if (isOlderThan1_7_2) {
                    return MultiVersion.V1_7_2;
                } else {
                    return super.getProtocolVersion();
                }
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }

        @Override
        protected Object getCurrentVersion() throws Throwable {
            return getTargetVersion.invokeExact();
        }

        @Override
        public boolean doesItemExist(Item item) {
            try {
                return (boolean) itemRegistryDiffKeepItem.invokeExact(item);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }
}
