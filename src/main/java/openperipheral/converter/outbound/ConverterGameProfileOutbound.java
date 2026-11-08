package openperipheral.converter.outbound;

import java.util.Map;

import com.google.common.collect.Maps;
import com.mojang.authlib.GameProfile;

import openperipheral.api.converter.IConverter;
import openperipheral.api.helpers.SimpleOutboundConverter;

public class ConverterGameProfileOutbound extends SimpleOutboundConverter<GameProfile> {

    @Override
    public Object convert(IConverter registry, GameProfile profile) {

        Map<String, Object> map = Maps.newHashMap();
        map.put("name", profile.getName());
        // Offline-mode GameProfiles have no id. Converting them must not throw.
        final java.util.UUID id = profile.getId();
        map.put("uuid", id != null ? id.toString() : null);
        return map;
    }

}
