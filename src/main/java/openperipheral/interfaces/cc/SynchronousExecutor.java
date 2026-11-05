package openperipheral.interfaces.cc;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import openperipheral.adapter.AdapterLogicException;

// CC no longer allows API methods to block (no more ILuaContext.pullEvent), since a blocked call
// would also block the task that delivers the event it waits for. A "synchronous" call is now
// expressed as a MethodResult: the task is queued on the main thread and the Lua call is suspended
// until the matching 'task_complete' event arrives.
public class SynchronousExecutor {

    private static final String TASK_COMPLETE_EVENT = "task_complete";

    public interface Task {

        public Object[] execute() throws LuaException, InterruptedException;
    }

    private static class TaskAdapter implements ILuaTask {

        private final Task task;

        public TaskAdapter(Task task) {
            this.task = task;
        }

        @Override
        public Object[] execute() throws LuaException {
            try {
                return task.execute();
            } catch (LuaException e) {
                throw e;
            } catch (Throwable t) {
                // Result types are converted by the Lua bridge, so only failures need rewriting here
                throw new LuaException(AdapterLogicException.getMessageForThrowable(t));
            }
        }
    }

    /**
     * Queues the task on the main thread and returns a result that suspends the current Lua call
     * until the task completes. Task failures are re-raised as {@link LuaException} in Lua.
     */
    public static MethodResult executeInMainThread(ILuaContext context, Task task) throws LuaException {
        final long taskId = context.issueMainThreadTask(new TaskAdapter(task));
        return MethodResult.task(TASK_COMPLETE_EVENT, taskId);
    }
}
