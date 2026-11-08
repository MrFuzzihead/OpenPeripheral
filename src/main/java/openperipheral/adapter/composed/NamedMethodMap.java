package openperipheral.adapter.composed;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import openperipheral.adapter.IMethodExecutor;

public class NamedMethodMap implements IMethodMap {

    private final Map<String, IMethodExecutor> methods;

    private final String[] methodNames;

    public NamedMethodMap(Map<String, IMethodExecutor> methods) {
        this.methods = ImmutableMap.copyOf(methods);

        final List<String> sortedNames = Lists.newArrayList(methods.keySet());
        Collections.sort(sortedNames);
        this.methodNames = sortedNames.toArray(new String[sortedNames.size()]);
    }

    public String[] getMethodNames() {
        return methodNames;
    }

    public IMethodExecutor getMethod(String name) {
        return methods.get(name);
    }

    @Override
    public boolean isEmpty() {
        return methods.isEmpty();
    }

    @Override
    public int size() {
        return methods.size();
    }

    @Override
    public void visitMethods(IMethodVisitor visitor) {
        for (Map.Entry<String, IMethodExecutor> e : methods.entrySet()) visitor.visit(e.getKey(), e.getValue());
    }
}
