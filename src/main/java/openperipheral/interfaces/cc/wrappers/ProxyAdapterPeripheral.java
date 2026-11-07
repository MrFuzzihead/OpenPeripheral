package openperipheral.interfaces.cc.wrappers;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import openperipheral.adapter.composed.IndexedMethodMap;

public class ProxyAdapterPeripheral extends AdapterPeripheral implements InvocationHandler {

    public ProxyAdapterPeripheral(IndexedMethodMap methods, Object targetObject) {
        super(methods, targetObject);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            final Class<?> declaringClass = method.getDeclaringClass();
            // Any interface the adapter itself implements (IPeripheral, IPeripheralTargeted, ...) must be served by
            // the wrapper - forwarding it to the target would fail, since the target is unaware of these types.
            if (declaringClass == Object.class || declaringClass.isInstance(this)) return method.invoke(this, args);
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable wrapper = e.getCause();
            throw wrapper != null ? wrapper : e;
        }
    }

}
