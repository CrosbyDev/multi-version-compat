package io.github.racoondog.multiversion;

import net.minecraft.block.Block;
import net.minecraft.item.Item;

/**
 * For versions not in this list, see <a href="https://wiki.vg/Protocol_version_numbers">this list</a>.
 */
@SuppressWarnings("unused")
public sealed interface MultiVersion permits MultiVersionImpl {
    /**
     * Lowest supported version
     */
    int V1_7_2 = 4;
    int V1_8 = 47;
    int V1_9 = 107;
    int V1_10 = 210;
    int V1_11 = 315;
    int V1_12 = 335;
    int V1_13 = 393;
    int V1_14 = 477;
    int V1_15 = 573;
    int V1_16 = 735;
    int V1_17 = 755;
    int V1_18 = 757;
    int V1_19 = 759;
    int V1_20 = 763;
    int V1_20_4 = 765;
    int V1_20_5 = 766;

    /**
     * To reset the protocol version in {@link MultiVersion#setProtocolVersion(int)}.
     */
    int RESET = -1;

    static MultiVersion getInstance() {
        return MultiVersionImpl.INSTANCE;
    }

    int getProtocolVersion();

    /**
     * It's not always possible to detect the server's version, especially when the server is running ViaVersion, so you
     * can use this method if you wish to override the protocol version returned in {@link MultiVersion#getProtocolVersion()},
     * and you can remove the override by passing in {@link MultiVersion#RESET}.
     * This method does nothing if ViaFabric or ViaFabricPlus are installed, as they have their own protocol overriding
     * settings.
     */
    void setProtocolVersion(int protocolVersion);

    String getProtocolName();

    /* Registry diff methods */

    boolean doesItemExist(Item item);

    boolean doesBlockExist(Block block);

    /* Utility methods */

    default boolean isAtLeast(int protocolVersion) {
        return getProtocolVersion() >= protocolVersion;
    }

    default boolean isAbove(int protocolVersion) {
        return getProtocolVersion() > protocolVersion;
    }

    default boolean isAtMost(int protocolVersion) {
        return getProtocolVersion() <= protocolVersion;
    }

    default boolean isUnder(int protocolVersion) {
        return getProtocolVersion() < protocolVersion;
    }
}
