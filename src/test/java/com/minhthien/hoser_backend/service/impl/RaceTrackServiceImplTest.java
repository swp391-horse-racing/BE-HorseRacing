package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.RaceTrackRequest;
import com.minhthien.hoser_backend.entity.RaceTrack;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.repository.RaceTrackRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaceTrackServiceImplTest {
    @Mock
    private RaceTrackRepository raceTrackRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    void adminCreatesRaceTrackWithNormalizedLocationKey() {
        RaceTrackServiceImpl service = service();
        when(userRepository.findById(9L)).thenReturn(Optional.of(admin()));
        when(raceTrackRepository.save(any(RaceTrack.class))).thenAnswer(invocation -> {
            RaceTrack track = invocation.getArgument(0);
            track.setId(30L);
            return track;
        });

        var response = service.createRaceTrack(9L, request("hcm", true));

        assertThat(response.getId()).isEqualTo(30L);
        assertThat(response.getLocationKey()).isEqualTo("HCM");
        assertThat(response.getActive()).isTrue();
    }

    @Test
    void adminListsRaceTracksByLocationAndActiveStatus() {
        RaceTrackServiceImpl service = service();
        RaceTrack track = track("HCM", true);

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin()));
        when(raceTrackRepository.findByLocationKeyAndActiveOrderByNameAsc("HCM", true))
                .thenReturn(List.of(track));

        var response = service.getRaceTracks(9L, "hcm", true);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getLocationKey()).isEqualTo("HCM");
        verify(raceTrackRepository).findByLocationKeyAndActiveOrderByNameAsc("HCM", true);
    }

    @Test
    void adminUpdatesRaceTrackActiveStatus() {
        RaceTrackServiceImpl service = service();
        RaceTrack track = track("HCM", true);

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin()));
        when(raceTrackRepository.findById(30L)).thenReturn(Optional.of(track));
        when(raceTrackRepository.save(track)).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateRaceTrackActive(9L, 30L, false);

        assertThat(response.getActive()).isFalse();
        assertThat(track.getActive()).isFalse();
    }

    private RaceTrackServiceImpl service() {
        return new RaceTrackServiceImpl(raceTrackRepository, userRepository);
    }

    private RaceTrackRequest request(String locationKey, boolean active) {
        RaceTrackRequest request = new RaceTrackRequest();
        request.setName("Main Track");
        request.setLocationKey(locationKey);
        request.setLocationName("Ho Chi Minh City");
        request.setAddress("District 1");
        request.setTrackType("TURF");
        request.setDistance("1200m");
        request.setActive(active);
        return request;
    }

    private RaceTrack track(String locationKey, boolean active) {
        return RaceTrack.builder()
                .id(30L)
                .name("Main Track")
                .locationKey(locationKey)
                .locationName("Ho Chi Minh City")
                .address("District 1")
                .trackType("TURF")
                .distance("1200m")
                .active(active)
                .build();
    }

    private User admin() {
        return User.builder()
                .id(9L)
                .username("admin")
                .email("admin@example.com")
                .role(UserRole.ADMIN)
                .build();
    }
}
