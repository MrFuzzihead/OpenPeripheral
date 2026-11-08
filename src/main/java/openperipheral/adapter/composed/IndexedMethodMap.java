package openperipheral.adapter.composed;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;

import openperipheral.adapter.IMethodExecutor;

public class IndexedMethodMap implements IMethodMap {

    protected final String[] names;
    protected final IMethodExecutor[] methods;

    public IndexedMethodMap(Map<String, IMethodExecutor> methods) {
        final int methodCount = methods.size();
        this.names = new String[methodCount];
        this.methods = new IMethodExecutor[methodCount];

        // Script-visible method order must be stable across JVM runs: expose a sorted name list.
        final List<String> sortedNames = Lists.newArrayList(methods.keySet());
        Collections.sort(sortedNames);

        int i = 0;
        for (String name : sortedNames) {
            this.names[i] = name;
            this.methods[i] = methods.get(name);
            i++;
        }
    }

    public IMethodExecutor[] getMethods() {
        return methods;
    }

    public String[] getMethodNames() {
        return names;
    }

    public IMethodExecutor getMethod(int index) {
        return index >= 0 && index < methods.length ? methods[index] : null;
    }

    public String getMethodName(int index) {
        return index >= 0 && index < names.length ? names[index] : null;
    }

    @Override
    public int size() {
        return names.length;
    }

    @Override
    public boolean isEmpty() {
        return names.length == 0;
    }

    @Override
    public void visitMethods(IMethodVisitor visitor) {
        for (int i = 0; i < names.length; i++) visitor.visit(names[i], methods[i]);
    }
}
