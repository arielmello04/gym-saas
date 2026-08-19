package com.gymsystem.auth;

import com.gymsystem.auth.dto.LoginRequest;
import com.gymsystem.auth.invite.SignupTokenService;
import com.gymsystem.security.jwt.JwtService;
import com.gymsystem.tenant.Tenant;
import com.gymsystem.tenant.TenantRole;
import com.gymsystem.tenant.TenantUser;
import com.gymsystem.tenant.TenantUserRepository;
import com.gymsystem.tenant.context.TenantContext;
import com.gymsystem.user.User;
import com.gymsystem.user.UserRepository;
import com.gymsystem.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Emissao do JWT no login.
 *
 * O token carimbava a academia que viesse no header X-Tenant-ID, sem conferir
 * vinculo nenhum: bastava pedir o login apontando para a academia alheia para
 * receber um token dizendo pertencer a ela. Agora o tenant so entra no token se
 * houver vinculo ativo - ou se quem entra e admin da plataforma, que atende
 * todas as academias.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceLoginTest {

    private static final Long   TENANT_ID = 2L;
    private static final String SLUG = "academia-fit";
    private static final String EMAIL = "aluno@local.test";

    @Mock private UserRepository userRepository;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private BCryptPasswordEncoder passwordEncoder;
    @Mock private SignupTokenService signupTokenService;
    @Mock private TenantUserRepository tenantUserRepository;

    @InjectMocks private AuthService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID, SLUG);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("com vínculo ativo: o token carrega a academia e o papel dentro dela")
    void tokenComVinculo() {
        comUsuario(UserRole.USER);
        comVinculo(TenantRole.MEMBER);

        service.login(login());

        verify(jwtService).generateToken(EMAIL, "USER", SLUG, "MEMBER");
    }

    @Test
    @DisplayName("sem vínculo: o token sai SEM academia, não carimbado com a alheia")
    void tokenSemVinculoNaoCarimbaAcademia() {
        comUsuario(UserRole.USER);
        semVinculo();

        service.login(login());

        verify(jwtService).generateToken(EMAIL, "USER");
        verify(jwtService, never()).generateToken(any(), any(), any(), any());
    }

    @Test
    @DisplayName("admin da plataforma recebe a academia mesmo sem vínculo: administra todas")
    void adminGlobalRecebeAcademia() {
        comUsuario(UserRole.ADMIN_WEB);
        semVinculo();

        service.login(login());

        verify(jwtService).generateToken(EMAIL, "ADMIN_WEB", SLUG, null);
    }

    @Test
    @DisplayName("sem academia no contexto, o token sai só com o papel global")
    void semTenantNoContexto() {
        TenantContext.clear();
        comUsuario(UserRole.USER);

        service.login(login());

        verify(jwtService).generateToken(EMAIL, "USER");
    }

    @Test
    @DisplayName("a autenticação por senha é executada antes de emitir qualquer token")
    void autenticaAntesDeEmitir() {
        comUsuario(UserRole.USER);
        comVinculo(TenantRole.MEMBER);

        service.login(login());

        verify(authenticationManager).authenticate(any());
    }

    // ── Helpers ───────────────────────────────────────────────

    private LoginRequest login() {
        var req = new LoginRequest();
        req.setEmail(EMAIL);
        req.setPassword("qualquer");
        return req;
    }

    private void comUsuario(UserRole role) {
        var user = User.builder().id(1L).email(EMAIL).role(role).active(true).build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    private void comVinculo(TenantRole role) {
        var tenant = Tenant.builder().id(TENANT_ID).slug(SLUG).name("Academia Fit").active(true).build();
        var vinculo = TenantUser.builder().id(1L).tenant(tenant).role(role).active(true).build();
        when(tenantUserRepository.findByTenantIdAndUserIdAndActiveTrue(eq(TENANT_ID), any()))
                .thenReturn(Optional.of(vinculo));
    }

    private void semVinculo() {
        when(tenantUserRepository.findByTenantIdAndUserIdAndActiveTrue(any(), any()))
                .thenReturn(Optional.empty());
    }
}
