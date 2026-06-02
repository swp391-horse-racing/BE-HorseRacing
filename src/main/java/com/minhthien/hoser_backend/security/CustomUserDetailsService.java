package com.minhthien.hoser_backend.security;

import com.minhthien.hoser_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final ConcurrentMap<String, CachedUserDetails> userDetailsCache = new ConcurrentHashMap<>();

    @Value("${app.security.user-cache-ttl-ms:120000}")
    private long userCacheTtlMs;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        String cacheKey = normalizeCacheKey(usernameOrEmail);
        long now = System.currentTimeMillis();
        CachedUserDetails cached = userDetailsCache.get(cacheKey);
        if (cached != null && cached.expiresAtMillis() > now) {
            return cached.userDetails();
        }

        UserDetails userDetails = userRepository.findByEmail(usernameOrEmail)
                .orElseGet(() -> userRepository.findByUsername(usernameOrEmail)
                        .orElseThrow(() -> new UsernameNotFoundException(
                                "User not found with email or username: " + usernameOrEmail)));
        userDetailsCache.put(cacheKey, new CachedUserDetails(userDetails, now + userCacheTtlMs));
        userDetailsCache.put(normalizeCacheKey(userDetails.getUsername()),
                new CachedUserDetails(userDetails, now + userCacheTtlMs));
        return userDetails;
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + id));
    }

    public void evictUser(String usernameOrEmail) {
        if (usernameOrEmail != null) {
            userDetailsCache.remove(normalizeCacheKey(usernameOrEmail));
        }
    }

    public void clearUserCache() {
        userDetailsCache.clear();
    }

    private String normalizeCacheKey(String usernameOrEmail) {
        return usernameOrEmail == null ? "" : usernameOrEmail.trim().toLowerCase(Locale.ROOT);
    }

    private record CachedUserDetails(UserDetails userDetails, long expiresAtMillis) {
    }
}

