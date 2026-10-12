package dev.pratik.lab.payment;

import dev.pratik.lab.common.ApiException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class FaultInjector {
  private volatile String mode = "none";
  private volatile int delayMs = 2000;
  private final AtomicInteger remaining = new AtomicInteger();
  private final AtomicInteger calls = new AtomicInteger();

  public synchronized void configure(String mode, int count, int delayMs) {
    if (!Set.of(
                "none",
                "slow",
                "after-commit",
                "400",
                "401",
                "403",
                "404",
                "409",
                "429",
                "500",
                "503")
            .contains(mode)
        || count < -1
        || delayMs < 0
        || delayMs > 5000) throw new ApiException(400, "BAD_FAULT", "Invalid fault parameters.");
    this.mode = mode;
    this.delayMs = delayMs;
    remaining.set(count);
    calls.set(0);
  }

  public String before() {
    calls.incrementAndGet();
    int n = remaining.getAndUpdate(v -> v > 0 ? v - 1 : v);
    String chosen = n == 0 ? "none" : mode;
    if ("slow".equals(chosen)) pause();
    if (chosen.matches("[45][0-9][0-9]"))
      throw new ApiException(Integer.parseInt(chosen), "LAB_FAULT", "Injected payment failure.");
    return chosen;
  }

  public void after(String chosen) {
    if ("after-commit".equals(chosen)) pause();
  }

  private void pause() {
    try {
      Thread.sleep(delayMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(503, "INTERRUPTED", "Interrupted lab delay.");
    }
  }

  public Map<String, Object> state() {
    return Map.of(
        "mode", mode, "remaining", remaining.get(), "delayMs", delayMs, "calls", calls.get());
  }
}
