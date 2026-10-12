package dev.pratik.lab.inventory;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
  private final InventoryStore store;

  public InventoryController(InventoryStore store) {
    this.store = store;
  }

  @GetMapping("/{sku}")
  @PreAuthorize("hasAuthority('SCOPE_inventory.read')")
  public InventoryStore.ProductView product(@PathVariable String sku) {
    return store.product(sku);
  }

  @GetMapping("/{sku}/sales")
  @PreAuthorize("hasAuthority('SCOPE_inventory.read')")
  public Map<String, Object> sales(@PathVariable String sku) {
    return store.sales(sku);
  }
}
