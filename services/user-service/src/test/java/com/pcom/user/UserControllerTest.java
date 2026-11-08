package com.pcom.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pcom.common.core.CoreAutoConfiguration;
import com.pcom.common.security.PcomSecurityAutoConfiguration;
import com.pcom.user.service.RoleService;
import com.pcom.user.service.UserService;
import com.pcom.user.web.UserController;
import com.pcom.user.web.UserDtos.UserResponse;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@Import({PcomSecurityAutoConfiguration.class, CoreAutoConfiguration.class})
@TestPropertySource(properties = "app.security.public-paths=/api/v1/users/register")
class UserControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserService users;
    @MockitoBean RoleService roles;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void registrationIsPublic() throws Exception {
        when(users.register(any())).thenReturn(new UserResponse(UUID.randomUUID(), "a@b.co", "A", null, true, Set.of("CUSTOMER"), Instant.now()));
        mvc.perform(post("/api/v1/users/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.co\",\"password\":\"longenough1\",\"fullName\":\"A\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value("a@b.co"));
    }

    @Test
    void registrationValidatesPayload() throws Exception {
        mvc.perform(post("/api/v1/users/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\",\"fullName\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.violations").isNotEmpty());
    }

    @Test
    void listingUsersNeedsPermission() throws Exception {
        mvc.perform(get("/api/v1/users").with(jwt().authorities(new SimpleGrantedAuthority("order:read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void profileRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }
}
