package com.williambl.timeout.mixin;

import com.google.gson.Gson;
import net.minecraft.server.ServerConfigEntry;
import net.minecraft.server.ServerConfigList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(ServerConfigList.class)
public interface ServerConfigListAccessor<K, V extends ServerConfigEntry<K>> {
    @Accessor("map")
    Map<String, V> getMap();

    @Accessor
    public static Gson getGSON() {
        throw new UnsupportedOperationException("mixin");
    }
}
