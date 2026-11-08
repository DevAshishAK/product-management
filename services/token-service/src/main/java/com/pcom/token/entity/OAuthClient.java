package com.pcom.token.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "oauth_client")
public class OAuthClient {
    @Id
    private String clientId;
    private String secretHash;
    private boolean publicClient;
    private String roles = "";
    private String permissions = "";
    private String grantTypes = "";
    private boolean active = true;
    private Instant createdAt = Instant.now();

    public List<String> roleList() { return split(roles); }
    public List<String> permissionList() { return split(permissions); }
    public boolean supports(String grant) { return split(grantTypes).contains(grant); }

    private static List<String> split(String csv) {
        return csv == null || csv.isBlank() ? List.of() : Arrays.stream(csv.split(",")).map(String::trim).toList();
    }
}
