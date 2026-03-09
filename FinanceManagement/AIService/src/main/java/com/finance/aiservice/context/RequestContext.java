package com.finance.aiservice.context;

import java.util.List;

/**
 * ThreadLocal storage for authenticated user and group context.
 *
 * PURPOSE:
 * 1. Prevent AI from hallucinating userId values.
 * 2. Pass group context (bankAccountIds) to tools transparently so
 *    they can query group-level financial data from FinanceService.
 *
 * USAGE:
 * 1. InteractiveChatService.processMessage() sets userId (and group context) at entry point
 * 2. AI tools check RequestContext for group mode and route to group APIs when appropriate
 * 3. InteractiveChatService clears context in finally block
 */
public class RequestContext {

    private static final ThreadLocal<String> userId = new ThreadLocal<>();
    private static final ThreadLocal<String> userQuestion = new ThreadLocal<>();

    // ── Group context fields ──
    private static final ThreadLocal<Boolean> groupChat = new ThreadLocal<>();
    private static final ThreadLocal<Long> groupId = new ThreadLocal<>();
    private static final ThreadLocal<String> groupName = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> bankAccountIds = new ThreadLocal<>();

    // ── User context ──

    public static void setUserId(String id) {
        userId.set(id);
    }

    public static String getUserId() {
        return userId.get();
    }

    public static boolean hasUserId() {
        return userId.get() != null;
    }

    public static void setUserQuestion(String question) {
        userQuestion.set(question);
    }

    public static String getUserQuestion() {
        return userQuestion.get();
    }

    // ── Group context ──

    public static void setGroupChat(boolean isGroup) {
        groupChat.set(isGroup);
    }

    public static boolean isGroupChat() {
        Boolean val = groupChat.get();
        return val != null && val;
    }

    public static void setGroupId(Long id) {
        groupId.set(id);
    }

    public static Long getGroupId() {
        return groupId.get();
    }

    public static void setGroupName(String name) {
        groupName.set(name);
    }

    public static String getGroupName() {
        return groupName.get();
    }

    public static void setBankAccountIds(List<String> ids) {
        bankAccountIds.set(ids);
    }

    /**
     * Get bank account IDs for the current group context.
     *
     * @return List of bankAccountId strings, or null if not in group mode
     */
    public static List<String> getBankAccountIds() {
        return bankAccountIds.get();
    }

    /**
     * Check if the current request is in group mode with valid bank account IDs.
     *
     * @return true if group context is active and bankAccountIds are available
     */
    public static boolean hasGroupBankAccounts() {
        List<String> ids = bankAccountIds.get();
        return isGroupChat() && ids != null && !ids.isEmpty();
    }

    /**
     * Clear all context from current request thread.
     * MUST be called in finally block to prevent memory leaks.
     */
    public static void clear() {
        userId.remove();
        userQuestion.remove();
        groupChat.remove();
        groupId.remove();
        groupName.remove();
        bankAccountIds.remove();
    }
}
