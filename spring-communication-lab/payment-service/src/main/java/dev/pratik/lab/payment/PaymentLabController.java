package dev.pratik.lab.payment;

import dev.pratik.lab.common.ApiException;
import jakarta.persistence.*;
import java.util.*;
import org.hibernate.Session;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("lab")
@RequestMapping("/api/lab")
public class PaymentLabController {
  @PersistenceContext private EntityManager em;
  private final FaultInjector faults;

  public PaymentLabController(FaultInjector faults) {
    this.faults = faults;
  }

  public record Fault(String mode, int remaining, int delayMs) {}

  @PostMapping("/faults")
  public Map<String, Object> set(@RequestBody Fault f) {
    faults.configure(f.mode(), f.remaining(), f.delayMs());
    return faults.state();
  }

  @GetMapping("/faults")
  public Map<String, Object> state() {
    return faults.state();
  }

  @GetMapping("/outbox")
  @Transactional(readOnly = true)
  public List<Map<String, Object>> outbox() {
    return em
        .createQuery("select e from OutboxEvent e order by e.createdAt desc", OutboxEvent.class)
        .setMaxResults(100)
        .getResultList()
        .stream()
        .map(
            e ->
                Map.<String, Object>of(
                    "id", e.getId(), "sent", e.getSent(), "attempts", e.getAttempts()))
        .toList();
  }

  @PostMapping("/outbox/{id}/replay")
  @Transactional
  public Map<String, String> replay(@PathVariable UUID id) {
    int count =
        em.unwrap(Session.class)
            .createMutationQuery("update OutboxEvent e set e.sent=false where e.id=:id")
            .setParameter("id", id)
            .executeUpdate();
    if (count == 0) throw new ApiException(404, "EVENT_NOT_FOUND", "No such event.");
    return Map.of("status", "scheduled", "eventId", id.toString());
  }
}
