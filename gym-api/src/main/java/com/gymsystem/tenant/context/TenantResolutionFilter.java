package com.gymsystem.tenant.context;

import com.gymsystem.tenant.Tenant;
import com.gymsystem.tenant.TenantRepository;
import com.gymsystem.tenant.TenantUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Decide em qual academia a requisicao pode agir, e guarda isso no TenantContext.
 *
 * Resolucao:
 *  1. Header:    X-Tenant-ID: academia-fit
 *  2. Subdominio: academia-fit.gymsystem.com  → slug = "academia-fit"
 *  3. Sem nenhum dos dois, vale o tenant que o JwtAuthFilter tirou do proprio
 *     token.
 *
 * Autorizacao (o ponto principal): o tenant resolvido so vale se o usuario
 * autenticado puder atuar nele. Antes, o header era aceito sem conferencia
 * nenhuma e sobrescrevia o tenant do token - qualquer aluno trocava um header e
 * passava a operar dentro de outra academia, inclusive gravando. A conferencia
 * fica aqui, e nao espalhada pelos services, porque este e o unico ponto por
 * onde toda requisicao passa sabendo ao mesmo tempo quem e o usuario e qual
 * academia ele pediu.
 *
 * Roda depois da cadeia do Spring Security (que registra em -100), entao a
 * autenticacao ja esta no contexto quando este filtro executa.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-ID";

    /**
     * Rotas de autenticacao: nunca sao barradas por vinculo.
     *
     * Quem esta tentando entrar pode chegar carregando o token da sessao
     * anterior - o navegador anexa o Authorization em toda requisicao. Sem esta
     * excecao, o filtro autentica o usuario ANTIGO, confere o vinculo dele
     * contra a academia NOVA e devolve 403: a pessoa fica presa, sem conseguir
     * trocar de conta nem de academia sem limpar o armazenamento local.
     */
    private static final Set<String> AUTH_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/signup",
            "/api/v1/auth/bootstrap"
    );

    /**
     * Papeis globais da plataforma: administram qualquer academia, entao para
     * eles o header e legitimo.
     */
    private static final Set<String> PLATFORM_ROLES = Set.of("ROLE_ADMIN_WEB", "ROLE_ADMIN_APP");

    private final TenantRepository tenantRepository;
    private final TenantUserRepository tenantUserRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String slug = resolveSlug(request);

            if (slug != null && !slug.isBlank()) {
                Optional<Tenant> tenant = tenantRepository.findBySlugAndActiveTrue(slug);
                if (tenant.isPresent()) {
                    TenantContext.set(tenant.get().getId(), tenant.get().getSlug());
                    log.debug("Tenant resolved: {} (id={})", slug, tenant.get().getId());
                } else {
                    log.warn("Unknown or inactive tenant slug: {}", slug);
                    // Context stays null — SecurityConfig will reject protected endpoints
                }
            }

            // Confere o tenant que sobrou no contexto, venha ele do header,
            // do subdominio ou do token.
            if (!isAuthEndpoint(request) && !mayActOnCurrentTenant()) {
                deny(response);
                return;
            }

            chain.doFilter(request, response);

        } finally {
            // Always clear to avoid tenant leaking across requests on reused threads
            TenantContext.clear();
        }
    }

    private boolean isAuthEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && AUTH_PATHS.contains(path);
    }

    /**
     * Se o usuario autenticado pode atuar na academia resolvida.
     *
     * Requisicao anonima passa: os endpoints publicos (catalogo de aulas,
     * calendario) sao por academia e nao expoem nada de aluno. Quem barra o
     * resto e o SecurityConfig.
     */
    private boolean mayActOnCurrentTenant() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) return true;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return true;

        String email = auth.getName();
        if (email == null || "anonymousUser".equals(email)) return true;

        boolean platformAdmin = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(PLATFORM_ROLES::contains);
        if (platformAdmin) return true;

        boolean member = tenantUserRepository.hasActiveMembership(tenantId, email);
        if (!member) {
            log.warn("Acesso negado: {} nao tem vinculo ativo com o tenant {}", email, tenantId);
        }
        return member;
    }

    /**
     * 403 escrito na mao: filtro roda fora do alcance do @ControllerAdvice, mas o
     * corpo segue o mesmo formato de erro do resto da API.
     */
    private void deny(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"timestamp":"%s","status":403,"error":"Forbidden",\
                "message":"Usuario sem vinculo com esta academia"}"""
                .formatted(Instant.now()));
    }

    private String resolveSlug(HttpServletRequest request) {
        // 1. Explicit header (preferred for mobile apps and API clients)
        String header = request.getHeader(TENANT_HEADER);
        if (header != null && !header.isBlank()) {
            return header.trim().toLowerCase();
        }

        // 2. Subdomain: "academia-fit.gymsystem.com" → "academia-fit"
        String host = request.getServerName(); // e.g. academia-fit.gymsystem.com
        if (host != null && host.contains(".")) {
            String subdomain = host.split("\\.")[0];
            // Ignore "www", "api", "app" as they are not tenant slugs
            if (!subdomain.equalsIgnoreCase("www")
                    && !subdomain.equalsIgnoreCase("api")
                    && !subdomain.equalsIgnoreCase("app")
                    && !subdomain.equalsIgnoreCase("localhost")) {
                return subdomain.toLowerCase();
            }
        }

        return null;
    }
}
