package ca.mlkem.niagara;

import b.JEUXUvcuL2jZkaA3lVI;
import b.UmorjzOdXluhk2;
import b.e7kYwfpmdAKwqffy3DHfqSmX;
import java.util.concurrent.atomic.AtomicBoolean;

/** Uses Niagara's own relocated coroutine ABI; no second Kotlin runtime. */
public final class NativeContinuation extends JEUXUvcuL2jZkaA3lVI {
    private final NativeBridge.Done done;
    private final AtomicBoolean delivered = new AtomicBoolean();
    public NativeContinuation(NativeBridge.Done callback) throws Exception {
        super((UmorjzOdXluhk2) NativeBridge.terminal(), (e7kYwfpmdAKwqffy3DHfqSmX) NativeBridge.emptyContext());
        done = callback;
    }
    public void deliver(Object result) {
        if (!delivered.compareAndSet(false, true)) return;
        try {
            NativeBridge.callStatic("b.n4PP3LbBEB9lzb", "fu2rtCCigf7Xb3nmMK7yTDWm", result);
        } catch (Throwable error) { done.complete(null, error); return; }
        done.complete(result, null);
    }
    @Override public Object L9Z5UBTfh8XASMAYBb(Object result) {
        deliver(result);
        try { return NativeBridge.unit(); } catch (Exception ignored) { return null; }
    }
}
