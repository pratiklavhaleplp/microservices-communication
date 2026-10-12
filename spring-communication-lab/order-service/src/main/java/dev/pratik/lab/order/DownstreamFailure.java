package dev.pratik.lab.order;

public class DownstreamFailure extends RuntimeException {
  private final int remoteStatus;
  private final boolean retryable;
  private final String retryAfter;

  public DownstreamFailure(int status, boolean retryable, String retryAfter, String message) {
    super(message);
    this.remoteStatus = status;
    this.retryable = retryable;
    this.retryAfter = retryAfter;
  }

  public int remoteStatus() {
    return remoteStatus;
  }

  public boolean retryable() {
    return retryable;
  }

  public String retryAfter() {
    return retryAfter;
  }

  public static DownstreamFailure response(int status, String retryAfter) {
    return new DownstreamFailure(
        status,
        status == 502 || status == 503 || status == 504,
        retryAfter,
        "Payment returned HTTP " + status);
  }

  public static DownstreamFailure transport(Throwable cause) {
    return new DownstreamFailure(0, true, null, "Payment connection failed or timed out.");
  }
}
