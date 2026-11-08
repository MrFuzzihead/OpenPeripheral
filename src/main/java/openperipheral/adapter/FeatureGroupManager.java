package openperipheral.adapter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.collect.Sets;

import cpw.mods.fml.common.discovery.ASMDataTable;
import cpw.mods.fml.common.discovery.ASMDataTable.ASMData;
import openperipheral.api.architecture.FeatureGroup;
import openperipheral.api.architecture.IFeatureGroupManager;

public class FeatureGroupManager implements IFeatureGroupManager {

    public static final FeatureGroupManager INSTANCE = new FeatureGroupManager();

    private static class FeatureGroupProperties {

        private final Set<String> blacklistedArchitectures = Collections
            .newSetFromMap(new ConcurrentHashMap<String, Boolean>());

        public void disable(String architecture) {
            blacklistedArchitectures.add(architecture);
        }

        public void enable(String architecture) {
            blacklistedArchitectures.remove(architecture);
        }

        public boolean isEnabled(String architecture) {
            return !blacklistedArchitectures.contains(architecture);
        }

        public FeatureGroupProperties copy() {
            FeatureGroupProperties result = new FeatureGroupProperties();
            result.blacklistedArchitectures.addAll(this.blacklistedArchitectures);
            return result;
        }
    }

    private final Map<String, FeatureGroupProperties> featureGroups = new ConcurrentHashMap<String, FeatureGroupProperties>();

    private FeatureGroupProperties getOrCreate(String featureGroup) {
        FeatureGroupProperties result = featureGroups.get(featureGroup);
        if (result == null) {
            final FeatureGroupProperties created = new FeatureGroupProperties();
            result = featureGroups.putIfAbsent(featureGroup, created);
            if (result == null) result = created;
        }

        return result;
    }

    public void ensureExists(String featureGroup) {
        if (!featureGroups.containsKey(featureGroup)) featureGroups.put(featureGroup, new FeatureGroupProperties());
    }

    @Override
    public Set<String> knownFeatureGroups() {
        return Collections.unmodifiableSet(featureGroups.keySet());
    }

    @Override
    public void disable(String featureGroup, String architecture) {
        getOrCreate(featureGroup).disable(architecture);
    }

    @Override
    public void enable(String featureGroup, String architecture) {
        getOrCreate(featureGroup).enable(architecture);
    }

    @Override
    public boolean isEnabled(String featureGroup, String architecture) {
        return getOrCreate(featureGroup).isEnabled(architecture);
    }

    public FeatureGroupManager copy() {
        FeatureGroupManager result = new FeatureGroupManager();

        for (Map.Entry<String, FeatureGroupProperties> e : this.featureGroups.entrySet()) result.featureGroups.put(
            e.getKey(),
            e.getValue()
                .copy());

        return result;
    }

    public String[] saveBlacklist() {
        Set<String> result = Sets.newTreeSet();

        for (Map.Entry<String, FeatureGroupProperties> e : featureGroups.entrySet())
            for (String architecture : e.getValue().blacklistedArchitectures)
                result.add(e.getKey() + ":" + architecture);

        String[] tmp = new String[result.size()];
        return result.toArray(tmp);
    }

    public void loadBlacklist(String[] blacklist) {
        featureGroups.clear();

        if (blacklist == null) return;

        for (String entry : blacklist) {
            final int separator = entry.indexOf(':');
            if (separator <= 0 || separator == entry.length() - 1) {
                openmods.Log.warn("Malformed feature group config entry, skipping: '%s'", entry);
                continue;
            }

            final String featureGroup = entry.substring(0, separator);
            final String architecture = entry.substring(separator + 1);

            disable(featureGroup, architecture);
        }
    }

    public void loadFeatureGroupsFromAnnotations(ASMDataTable asmData) {
        for (ASMData fgAnnotation : asmData.getAll(FeatureGroup.class.getName())) {
            @SuppressWarnings("unchecked")
            final List<String> featureGroups = (List<String>) fgAnnotation.getAnnotationInfo()
                .get("value");
            for (String featureGroup : featureGroups) ensureExists(featureGroup);
        }
    }

}
