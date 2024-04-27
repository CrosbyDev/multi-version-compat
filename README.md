# MultiVersion Compat

A simple Minecraft Fabric library for implementing special behaviour based on the server's Minecraft version.

Based on the utility class of the same name in [clientcommands](https://github.com/Earthcomputer/clientcommands/blob/fabric/src/main/java/net/earthcomputer/clientcommands/MultiVersionCompat.java),
originally developed by [Earthcomputer](https://github.com/Earthcomputer), [FlorianMichael](https://github.com/FlorianMichael), and [RaphiMC](https://github.com/RaphiMC).

## Usage

### build.gradle

```groovy
repositories {
    maven {
        name = 'meteor-maven'
        url = 'https://maven.meteordev.org/releases'
    }
}
```

```groovy
dependencies {
    modImplementation 'io.github.racoondog:multi-version-compat:1.0.0'
}
```

### build.gradle.kts

```kotlin
repositories {
    maven("https://maven.meteordev.org/releases") {
        name = "meteor-maven"
    }
}
```

```kotlin
dependencies {
    modImplementation("io.github.racoondog:multi-version-compat:1.0.0")
}
```

### Example usage

```java
import io.github.racoondog.multiversion.MultiVersion;
import net.minecraft.block.Block;
import net.minecraft.item.Item;

public class Example {
    public static void example() {
        MultiVersion multiVersion = MultiVersion.getInstance();
        
        // Get the current server's protocol version
        int protocolVersion = multiVersion.getProtocolVersion();
        
        // Get the human-readable name of the current server's protocol version
        String versionName = multiVersion.getProtocolName();
        
        // Since servers running ViaVersion spoof the protocol version, you can override it manually
        // This doesn't have an effect if ViaFabric or ViaFabricPlus are in use, as they already have user-facing settings
        // You should probably let the user access this through a command or something though
        multiVersion.setProtocolVersion(MultiVersion.V1_16);
        // And you can also reset it to default behaviour
        multiVersion.setProtocolVersion(MultiVersion.RESET);
        
        // Registry diff methods
        // These only work if ViaFabricPlus is present, otherwise they will always return true
        // These are guaranteed to never false negative
        boolean doesItemExist = multiVersion.doesItemExist(Items.NETHERITE_INGOT);
        boolean doesBlockExist = multiVersion.doesBlockExist(Blocks.AMETHYST_BLOCK);
        
        // Some utility methods
        boolean hasModernNether = multiVersion.isAtLeast(MultiVersion.V1_16);
        boolean hasOldCombat = multiVersion.isAtMost(MultiVersion.V1_8);
        boolean hasOldSwimming = multiVersion.isUnder(MultiVersion.V1_13);
        boolean hasDataComponents = multiVersion.isAbove(MultiVersion.V1_20_4);
        
        // The MultiVersion class only has a few constants for major versions
        // For others, make your own constants using https://wiki.vg/Protocol_version_numbers
        int v1_19_4 = 762;
    }
}
```