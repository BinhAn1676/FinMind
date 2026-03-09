package com.finance.aiservice.context;

import java.util.HashMap;
import java.util.Map;

/**
 * ThreadLocal guard to prevent AI from calling tools excessively.
 *
 * PURPOSE: Prevent infinite loops when AI repeatedly calls same function.
 *
 * PROBLEM:
 * - Some AI models ignore "call once" instructions in prompts
 * - Models may call same function 2, 3, or more times
 * - This causes infinite loops and poor UX
 *
 * SOLUTION - 3-Strike Approach:
 * - Call 1: Execute normally, cache result
 * - Call 2: Return cached result (give AI one more chance to use data)
 * - Call 3+: Return ERROR to force AI to stop
 *
 * USAGE:
 * 1. Tool checks call count at start of apply()
 * 2. Tool caches result after successful execution
 * 3. InteractiveChatService clears guard in finally block
 */
public class ToolCallGuard {

    /**
     * Tracks call count and cached result for each tool.
     */
    private static class CallTracker {
        Object cachedResult;
        int callCount = 0;
    }

    private static final ThreadLocal<Map<String, CallTracker>> toolCalls =
        ThreadLocal.withInitial(HashMap::new);

    /**
     * Increment call count for a tool and return current count.
     *
     * @param toolName Name of the tool being called
     * @return Current call count (1 for first call, 2 for second, etc.)
     */
    public static int incrementAndGetCallCount(String toolName) {
        CallTracker tracker = toolCalls.get()
            .computeIfAbsent(toolName, k -> new CallTracker());
        tracker.callCount++;
        return tracker.callCount;
    }

    /**
     * Cache the result from a tool call for potential reuse.
     *
     * @param toolName Name of the tool
     * @param result Result to cache
     */
    public static void cacheResult(String toolName, Object result) {
        CallTracker tracker = toolCalls.get()
            .computeIfAbsent(toolName, k -> new CallTracker());
        tracker.cachedResult = result;
    }

    /**
     * Get cached result from a previous tool call.
     *
     * @param toolName Name of the tool
     * @param resultType Expected result class
     * @return Cached result, or null if not found or wrong type
     */
    @SuppressWarnings("unchecked")
    public static <T> T getCachedResult(String toolName, Class<T> resultType) {
        CallTracker tracker = toolCalls.get().get(toolName);
        if (tracker != null && tracker.cachedResult != null
                && resultType.isInstance(tracker.cachedResult)) {
            return (T) tracker.cachedResult;
        }
        return null;
    }

    /**
     * Clear all tool call tracking for current request.
     * MUST be called in finally block to prevent memory leaks.
     */
    public static void clear() {
        toolCalls.remove();
    }
}
