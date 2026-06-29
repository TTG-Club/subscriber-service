package club.ttg.subscriber.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Лёгкий принципал, собираемый из claims JWT, выпущенного auth-service.
 * Сервис не хранит пользователей: данные берутся только из токена.
 * <p>
 * Роли превращаются в {@link SimpleGrantedAuthority} **без** префикса `ROLE_`,
 * поскольку доступ ограничивается через `@Secured("ADMIN")` / `@Secured("USER")`.
 */
@Getter
public class AuthUser implements UserDetails {
    private final UUID id;
    private final String username;
    private final String email;
    private final List<String> roles;
    private final Collection<? extends GrantedAuthority> authorities;

    public AuthUser(UUID id, String username, String email, List<String> roles) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.roles = roles == null ? List.of() : List.copyOf(roles);
        this.authorities = this.roles.stream()
                .filter(Objects::nonNull)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
