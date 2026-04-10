package com.hf.mbcp.core.support;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务同步支持工具。
 * 在 Spring 事务内注册回调，确保缓存失效在事务提交后执行，事务回滚时不操作缓存。
 */
public final class TransactionSynchronizationSupport {

    private TransactionSynchronizationSupport() {}

    /**
     * 若当前在事务中，注册 afterCommit 回调；否则立即执行。
     * @param action 需要在事务提交后执行的操作
     */
    public static void executeAfterCommitOrNow(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
                // afterRollback 默认不做任何操作，保证事务回滚时不清缓存
            });
        } else {
            action.run();
        }
    }

    /**
     * 若当前在事务中，注册 afterCommit + afterRollback 两个回调；否则立即执行 onCommit。
     */
    public static void executeWithRollbackAware(Runnable onCommit, Runnable onRollback) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { onCommit.run(); }
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK && onRollback != null) {
                        onRollback.run();
                    }
                }
            });
        } else {
            onCommit.run();
        }
    }
}
