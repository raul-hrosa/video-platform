package com.videoplatform.auth;

import com.videoplatform.common.ApiException;
import com.videoplatform.common.GlobalExceptionHandler;
import com.videoplatform.support.TestAuth;
import com.videoplatform.support.WebSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({GlobalExceptionHandler.class, WebSecurityTestConfig.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;
    @MockBean
    private JwtService jwtService;

    private User user() {
        return User.register("Joao", "joao@example.com", "hash");
    }

    @Test
    void registerReturns201WithoutPassword() throws Exception {
        when(authService.register(eq("Joao"), eq("joao@example.com"), any())).thenReturn(user());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Joao\",\"email\":\"joao@example.com\",\"password\":\"supersecret\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("joao@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void registerShortPasswordReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Joao\",\"email\":\"joao@example.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void registerDuplicateEmailReturns409() throws Exception {
        when(authService.register(any(), any(), any()))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email already registered."));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Joao\",\"email\":\"joao@example.com\",\"password\":\"supersecret\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void loginReturnsBearerToken() throws Exception {
        when(authService.authenticate(eq("joao@example.com"), eq("supersecret"))).thenReturn(user());
        when(jwtService.issue(any())).thenReturn(new JwtService.IssuedToken("the.jwt.token", 3600));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"joao@example.com\",\"password\":\"supersecret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("the.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void loginWrongCredentialsReturns401() throws Exception {
        when(authService.authenticate(any(), any()))
                .thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                        "Email or password is incorrect."));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"joao@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void meReturnsAuthenticatedUser() throws Exception {
        when(authService.getById(TestAuth.USER_ID)).thenReturn(user());

        mockMvc.perform(get("/api/v1/auth/me").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("joao@example.com"));
    }
}
