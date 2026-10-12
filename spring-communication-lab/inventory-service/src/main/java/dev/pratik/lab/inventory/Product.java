package dev.pratik.lab.inventory;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product {
  protected Product() {}

  @Id
  @Column(length = 50)
  private String sku;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal price;

  @Column(nullable = false, length = 3)
  private String currency;

  public String getSku() {
    return sku;
  }

  public String getName() {
    return name;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public String getCurrency() {
    return currency;
  }
}
