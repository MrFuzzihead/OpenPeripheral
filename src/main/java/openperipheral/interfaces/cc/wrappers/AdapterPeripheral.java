package openperipheral.interfaces.cc.wrappers;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.logging.log4j.Level;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IPeripheralTargeted;
import openmods.Log;
import openmods.utils.CachedFactory;
import openperipheral.adapter.AdapterLogicException;
import openperipheral.adapter.IMethodCall;
import openperipheral.adapter.IMethodExecutor;
import openperipheral.adapter.PeripheralTypeProvider;
import openperipheral.adapter.composed.IndexedMethodMap;
import openperipheral.adapter.wrappers.SignallingGlobals;
import openperipheral.api.architecture.IArchitectureAccess;
import openperipheral.api.architecture.IAttachable;
import openperipheral.api.architecture.cc.IComputerCraftAttachable;
import openperipheral.api.peripheral.IOpenPeripheral;
import openperipheral.interfaces.cc.ModuleComputerCraft;
import openperipheral.interfaces.cc.StringMount;
import openperipheral.interfaces.cc.SynchronousExecutor;
import openperipheral.interfaces.cc.UtilsResourceMount;
import openperipheral.util.DocUtils;

public class AdapterPeripheral implements IPeripheral, IOpenPeripheral, IPeripheralTargeted {

    private static final String MOUNT_NAME = "openp";
    private static final IMount MOUNT = new UtilsResourceMount();
    private static final ConcurrentMap<Class<?>, String> DOC_TEXT_CACHE = new ConcurrentHashMap<Class<?>, String>();
    private static final ConcurrentMap<IComputerAccess, Map<String, Integer>> SHARED_MOUNTS = new ConcurrentHashMap<IComputerAccess, Map<String, Integer>>();
    private final Object[] NULL = new Object[0];

    protected final String type;
    protected final Object target;
    private final IMount docMount;

    private final ConcurrentMap<IComputerAccess, Set<String>> ownMounts = new ConcurrentHashMap<IComputerAccess, Set<String>>();

    private final IndexedMethodMap methods;

    private final CachedFactory<IComputerAccess, IArchitectureAccess> accessCache = new CachedFactory<IComputerAccess, IArchitectureAccess>() {

        @Override
        protected IArchitectureAccess create(IComputerAccess computer) {
            return ModuleComputerCraft.ENV.createAccess(computer);
        }
    };

    public AdapterPeripheral(IndexedMethodMap methods, Object target) {
        this.methods = methods;
        this.type = PeripheralTypeProvider.INSTANCE.generateType(target);
        this.target = target;

        // Help text is per adapted class. Building it for every TE instance is pure overhead.
        final Class<?> targetClass = target.getClass();
        String helpText = DOC_TEXT_CACHE.get(targetClass);
        if (helpText == null) {
            helpText = DocUtils.createPeripheralHelpText(targetClass, type, methods);
            final String previous = DOC_TEXT_CACHE.putIfAbsent(targetClass, helpText);
            if (previous != null) helpText = previous;
        }
        this.docMount = new StringMount(helpText);
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Object getTarget() {
        return target;
    }

    @Override
    public String[] getMethodNames() {
        return methods.getMethodNames();
    }

    private IMethodCall prepareCall(IMethodExecutor executor, IComputerAccess computer, ILuaContext context) {
        final IMethodCall call = executor.startCall(target);
        return ModuleComputerCraft.ENV.addPeripheralArgs(call, computer, context);
    }

    private Object[] executeCall(IMethodCall call, int methodIndex, Object[] arguments)
        throws LuaException, InterruptedException {
        try {
            return call.call(arguments);
        } catch (InterruptedException e) {
            throw e;
        } catch (LuaException e) {
            throw e;
        } catch (Error e) {
            // OutOfMemoryError/StackOverflowError must not be hidden from the JVM.
            throw e;
        } catch (Throwable e) {
            String methodName = methods.getMethodName(methodIndex);
            Log.log(
                Level.DEBUG,
                e,
                "Error during method %s(%d) execution on peripheral %s, args: %s",
                methodName,
                methodIndex,
                type,
                Arrays.toString(arguments));
            throw new LuaException(AdapterLogicException.getMessageForThrowable(e));
        }
    }

    private Object[] executeToSignal(int callbackId, int methodIndex, IMethodCall preparedCall, Object[] arguments) {
        try {
            Object[] callResult = executeCall(preparedCall, methodIndex, arguments);
            Object[] fullResult = new Object[callResult.length + 2];
            fullResult[0] = callbackId;
            fullResult[1] = true;
            System.arraycopy(callResult, 0, fullResult, 2, callResult.length);
            return fullResult;
        } catch (InterruptedException e) {
            return new Object[] { callbackId, false };
        } catch (LuaException e) {
            return new Object[] { callbackId, false, e.getMessage() };
        }
    }

    @Override
    public Object[] callMethod(final IComputerAccess computer, final ILuaContext context, final int index,
        final Object[] arguments) throws LuaException, InterruptedException {
        return toLegacyResult(callMethodResult(computer, context, index, arguments));
    }

    private static Object[] toLegacyResult(Object result) {
        if (result instanceof Object[]) return (Object[]) result;
        // Legacy entrypoint: a single MethodResult is understood by the bridge as "suspend"
        return new Object[] { result };
    }

    @Override
    public Object callMethodResult(final IComputerAccess computer, final ILuaContext context, final int index,
        final Object[] arguments) throws LuaException, InterruptedException {
        // this should throw if peripheral isn't attached
        computer.getAttachmentName();

        final IMethodExecutor method = methods.getMethod(index);
        Preconditions.checkNotNull(method, "Invalid method index: %d", index);

        final IMethodCall preparedCall = prepareCall(method, computer, context);

        final Optional<String> returnSignal = method.getReturnSignal();
        if (returnSignal.isPresent()) {
            final int callbackId = SignallingGlobals.instance.nextCallbackId();
            final String returnSignalId = returnSignal.get();
            if (method.isAsynchronous()) {
                SignallingGlobals.instance.scheduleTask(new Runnable() {

                    @Override
                    public void run() {
                        computer
                            .queueEvent(returnSignalId, executeToSignal(callbackId, index, preparedCall, arguments));
                    }
                });
            } else {
                context.issueMainThreadTask(new ILuaTask() {

                    @Override
                    public Object[] execute() {
                        computer
                            .queueEvent(returnSignalId, executeToSignal(callbackId, index, preparedCall, arguments));
                        // this will be used as 'task_complete' result, so we will ignore it
                        return NULL;
                    }
                });
            }
            return new Object[] { callbackId };
        } else {
            if (method.isAsynchronous()) return executeCall(preparedCall, index, arguments);
            else {
                return SynchronousExecutor.executeInMainThread(context, new SynchronousExecutor.Task() {

                    @Override
                    public Object[] execute() throws LuaException, InterruptedException {
                        return executeCall(preparedCall, index, arguments);
                    }
                });
            }
        }
    }

    private void mountShared(IComputerAccess computer, String name, IMount mount) {
        synchronized (SHARED_MOUNTS) {
            Set<String> own = ownMounts.get(computer);
            if (own == null) {
                own = com.google.common.collect.Sets.newHashSet();
                ownMounts.put(computer, own);
            }
            if (!own.add(name)) return;

            Map<String, Integer> counts = SHARED_MOUNTS.get(computer);
            if (counts == null) {
                counts = com.google.common.collect.Maps.newHashMap();
                SHARED_MOUNTS.put(computer, counts);
            }

            final Integer count = counts.get(name);
            if (count != null) {
                counts.put(name, count + 1);
                return;
            }

            try {
                computer.mount(name, mount);
                counts.put(name, 1);
            } catch (Throwable t) {
                own.remove(name);
                if (counts.isEmpty()) SHARED_MOUNTS.remove(computer);
                throw t;
            }
        }
    }

    private void unmountOwned(IComputerAccess computer) {
        synchronized (SHARED_MOUNTS) {
            final Set<String> own = ownMounts.remove(computer);
            if (own == null) return;

            final Map<String, Integer> counts = SHARED_MOUNTS.get(computer);
            if (counts == null) return;

            for (String name : own) {
                final Integer count = counts.get(name);
                if (count == null) continue;

                if (count <= 1) {
                    counts.remove(name);
                    try {
                        computer.unmount(name);
                    } catch (Throwable t) {
                        Log.warn(t, "Failed to unmount peripheral docs at %s", name);
                    }
                } else {
                    counts.put(name, count - 1);
                }
            }

            if (counts.isEmpty()) SHARED_MOUNTS.remove(computer);
        }
    }

    @Override
    public void attach(IComputerAccess computer) {
        mountShared(computer, MOUNT_NAME, AdapterPeripheral.MOUNT);
        mountShared(computer, "rom/help/" + computer.getAttachmentName(), docMount);
        if (target instanceof IAttachable) {
            final IArchitectureAccess access;
            synchronized (accessCache) {
                access = accessCache.getOrCreate(computer);
            }
            ((IAttachable) target).addComputer(access);
        }
        if (target instanceof IComputerCraftAttachable) ((IComputerCraftAttachable) target).addComputer(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        unmountOwned(computer);

        if (target instanceof IAttachable) {
            final IArchitectureAccess access;
            synchronized (accessCache) {
                access = accessCache.remove(computer);
            }
            if (access != null) ((IAttachable) target).removeComputer(access);
        }

        if (target instanceof IComputerCraftAttachable) ((IComputerCraftAttachable) target).removeComputer(computer);
    }

    @Override
    public boolean equals(IPeripheral other) {
        if (other == this) return true;
        if (other instanceof AdapterPeripheral) return ((AdapterPeripheral) other).target == target;
        return false;
    }
}
