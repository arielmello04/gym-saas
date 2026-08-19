package com.gymsystem.tenant.context;

import com.gymsystem.tenant.Tenant;
import com.gymsystem.tenant.TenantRepository;
import com.gymsystem.tenant.TenantUserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * O filtro decide em qual academia a requisicao pode agir.
 *
 * Estes testes existem por causa de uma falha real: o header X-Tenant-ID era
 * aceito sem conferencia e sobrescrevia o tenant do token, entao qualquer aluno
 * autenticado passava a ler e ESCREVER dentro de outra academia so trocando um
 * cabecalho. O caso "membro de uma academia apontando para outra" e o coracao
 * desta classe.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantResolutionFilterTest {

    private static final String HEADER = "X-Tenant-ID";

    @Mock private TenantRepository tenantRepository;
    @Mock private TenantUserRepository tenantUserRepository;
    @Mock private FilterChain chain;

    @InjectMocks private TenantResolutionFilter filter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    // ── O caso que originou o filtro ──────────────────────────

    @Test
    @DisplayName("membro de outra academia: header apontando para academia alheia é barrado com 403")
    void negaMembroSemVinculo() throws Exception {
        stubTenant("academia-alheia", 99L);
        authenticateAs("aluno@local.test", "ROLE_USER");
        when(tenantUserRepository.hasActiveMembership(99L, "aluno@local.test")).thenReturn(false);

        var response = run(requestWithHeader("academia-alheia"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("sem vinculo com esta academia");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("membro da própria academia: passa e o contexto recebe o tenant")
    void permiteMembroComVinculo() throws Exception {
        stubTenant("academia-fit", 2L);
        authenticateAs("aluno@local.test", "ROLE_USER");
        when(tenantUserRepository.hasActiveMembership(2L, "aluno@local.test")).thenReturn(true);

        var response = run(requestWithHeader("academia-fit"));

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("admin da plataforma administra qualquer academia sem precisar de vínculo")
    void permiteAdminGlobalEmQualquerTenant() throws Exception {
        stubTenant("academia-alheia", 99L);
        authenticateAs("admin@local.test", "ROLE_ADMIN_WEB");

        var response = run(requestWithHeader("academia-alheia"));

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
        // Nem consulta vinculo: o papel global ja responde por si.
        verify(tenantUserRepository, never()).hasActiveMembership(anyLong(), anyString());
    }

    @Test
    @DisplayName("o tenant do token também é conferido, não só o do header")
    void confereTenantVindoDoToken() throws Exception {
        // Sem header: o contexto ja vem preenchido pelo JwtAuthFilter, que roda antes.
        authenticateAs("aluno@local.test", "ROLE_USER");
        TenantContext.set(99L, "academia-alheia");
        when(tenantUserRepository.hasActiveMembership(99L, "aluno@local.test")).thenReturn(false);

        var response = run(new MockHttpServletRequest());

        assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("login não é barrado pelo vínculo: quem troca de conta chega com o token da sessão anterior")
    void naoBarraLogin() throws Exception {
        stubTenant("academia-alheia", 99L);
        // O navegador anexou o token do usuario ANTERIOR na requisicao de login.
        authenticateAs("aluno@local.test", "ROLE_USER");
        when(tenantUserRepository.hasActiveMembership(99L, "aluno@local.test")).thenReturn(false);

        var request = requestWithHeader("academia-alheia");
        request.setRequestURI("/api/v1/auth/login");

        var response = run(request);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("signup e bootstrap também passam livres")
    void naoBarraSignupNemBootstrap() throws Exception {
        stubTenant("academia-alheia", 99L);
        authenticateAs("aluno@local.test", "ROLE_USER");
        when(tenantUserRepository.hasActiveMembership(anyLong(), anyString())).thenReturn(false);

        for (String rota : new String[]{"/api/v1/auth/signup", "/api/v1/auth/bootstrap"}) {
            var request = requestWithHeader("academia-alheia");
            request.setRequestURI(rota);
            assertThat(run(request).getStatus()).as(rota).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("a exceção vale só para as rotas de autenticação, não para o resto da API")
    void excecaoNaoVazaParaOutrasRotas() throws Exception {
        stubTenant("academia-alheia", 99L);
        authenticateAs("aluno@local.test", "ROLE_USER");
        when(tenantUserRepository.hasActiveMembership(99L, "aluno@local.test")).thenReturn(false);

        var request = requestWithHeader("academia-alheia");
        request.setRequestURI("/api/v1/auth/../my/bookings");

        assertThat(run(request).getStatus()).isEqualTo(403);
    }

    // ── Resolução ─────────────────────────────────────────────

    @Test
    @DisplayName("resolve a academia pelo subdomínio quando não há header")
    void resolvePorSubdominio() throws Exception {
        stubTenant("academia-fit", 2L);
        authenticateAs("aluno@local.test", "ROLE_USER");
        when(tenantUserRepository.hasActiveMembership(2L, "aluno@local.test")).thenReturn(true);

        var request = new MockHttpServletRequest();
        request.setServerName("academia-fit.gymsystem.com.br");

        assertThat(run(request).getStatus()).isEqualTo(200);
        verify(tenantRepository).findBySlugAndActiveTrue("academia-fit");
    }

    @Test
    @DisplayName("subdomínios de infraestrutura (www, api, app) não são tratados como academia")
    void ignoraSubdominiosReservados() throws Exception {
        var request = new MockHttpServletRequest();
        request.setServerName("api.gymsystem.com.br");

        assertThat(run(request).getStatus()).isEqualTo(200);
        verify(tenantRepository, never()).findBySlugAndActiveTrue(anyString());
    }

    @Test
    @DisplayName("slug desconhecido ou inativo não entra no contexto")
    void ignoraTenantInexistente() throws Exception {
        when(tenantRepository.findBySlugAndActiveTrue("nao-existe")).thenReturn(Optional.empty());

        var response = run(requestWithHeader("nao-existe"));

        // Segue a requisicao sem tenant; quem barra e o SecurityConfig.
        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("requisição anônima passa: endpoints públicos são por academia e não expõem aluno")
    void permiteAnonimo() throws Exception {
        stubTenant("academia-fit", 2L);
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThat(run(requestWithHeader("academia-fit")).getStatus()).isEqualTo(200);
        verify(tenantUserRepository, never()).hasActiveMembership(anyLong(), anyString());
    }

    // ── Higiene do ThreadLocal ────────────────────────────────

    @Test
    @DisplayName("o contexto é limpo ao fim da requisição, inclusive quando ela é barrada")
    void limpaContextoSempre() throws Exception {
        stubTenant("academia-alheia", 99L);
        authenticateAs("aluno@local.test", "ROLE_USER");
        when(tenantUserRepository.hasActiveMembership(99L, "aluno@local.test")).thenReturn(false);

        run(requestWithHeader("academia-alheia"));

        // Thread de pool reaproveitada nao pode herdar o tenant da requisicao anterior.
        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(TenantContext.getTenantSlug()).isNull();
    }

    @Test
    @DisplayName("o contexto é limpo mesmo se a cadeia estourar")
    void limpaContextoQuandoCadeiaFalha() throws Exception {
        stubTenant("academia-fit", 2L);
        authenticateAs("aluno@local.test", "ROLE_USER");
        when(tenantUserRepository.hasActiveMembership(2L, "aluno@local.test")).thenReturn(true);
        doThrow(new RuntimeException("boom")).when(chain).doFilter(any(), any());

        try {
            run(requestWithHeader("academia-fit"));
        } catch (RuntimeException expected) {
            // a excecao sobe; o que importa e o estado do ThreadLocal
        }

        assertThat(TenantContext.getTenantId()).isNull();
    }

    // ── Helpers ───────────────────────────────────────────────

    private MockHttpServletResponse run(MockHttpServletRequest request) throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private MockHttpServletRequest requestWithHeader(String slug) {
        var request = new MockHttpServletRequest();
        request.addHeader(HEADER, slug);
        return request;
    }

    private void stubTenant(String slug, Long id) {
        var tenant = Tenant.builder().id(id).slug(slug).name(slug).active(true).build();
        when(tenantRepository.findBySlugAndActiveTrue(slug)).thenReturn(Optional.of(tenant));
    }

    private void authenticateAs(String email, String... authorities) {
        var granted = List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, granted));
    }
}
