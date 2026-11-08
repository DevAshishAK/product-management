package com.pcom.product.entity;

import com.pcom.common.persistence.AuditableEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "category")
public class Category extends AuditableEntity {
    private String name;
    private String description;
}
