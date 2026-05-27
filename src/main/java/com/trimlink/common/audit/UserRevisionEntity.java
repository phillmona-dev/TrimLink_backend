package com.trimlink.common.audit;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.DefaultRevisionEntity;
import org.hibernate.envers.RevisionEntity;

import java.util.UUID;

@Entity
@Table(name = "revinfo")
@RevisionEntity(UserRevisionListener.class)
@Getter
@Setter
public class UserRevisionEntity extends DefaultRevisionEntity {
    private UUID userId;
    private String username;
    private String ipAddress;
}
